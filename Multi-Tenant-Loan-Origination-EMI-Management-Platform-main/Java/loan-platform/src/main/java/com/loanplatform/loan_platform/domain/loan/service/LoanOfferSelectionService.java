package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.LoanOfferStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanStateHistory;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.dto.request.LoanOfferSelectionRequest;
import com.loanplatform.loan_platform.dto.response.LoanOfferSelectionResponse;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.LoanOfferExpiredException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanOfferSelectionService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final LoanStateHistoryRepository loanStateHistoryRepository;
    private final LoanStateMachinePort loanStateMachinePort;
    private final KafkaEventPort kafkaEventPort;

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public LoanOfferSelectionResponse selectOffer(
            UUID tenantId,
            UUID borrowerId,
            UUID applicationId,
            LoanOfferSelectionRequest request,
            String requestId
    ) {
        log.debug("Loan offer selection entry tenantId={} borrowerId={} applicationId={} offerId={}",
                tenantId, borrowerId, applicationId, request.getOfferId());

        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        expireApplicationIfRequired(application, requestId, "system");

        if (application.getStatus() != LoanApplicationStatus.APPLICATION_SUBMITTED) {
            throw new IllegalStateException("Loan application is not in APPLICATION_SUBMITTED state");
        }

        LoanOffer selectedOffer = loanOfferRepository.findByIdAndApplicationIdAndTenantId(
                        request.getOfferId(),
                        applicationId,
                        tenantId
                )
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan offer not found: " + request.getOfferId()));

        Instant now = Instant.now();
        if (selectedOffer.getStatus() != LoanOfferStatus.ACTIVE || selectedOffer.getExpiresAt().isBefore(now)) {
            throw new LoanOfferExpiredException("Loan offer is no longer active");
        }

        LoanLifecycleState previousState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.SELECT_OFFER);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.OFFER_SELECTED);
        application.setSelectedOfferId(selectedOffer.getId());

        selectedOffer.setStatus(LoanOfferStatus.SELECTED);
        selectedOffer.setSelectedAt(now);

        List<LoanOffer> offers = loanOfferRepository.findByApplicationIdAndTenantId(applicationId, tenantId);
        for (LoanOffer offer : offers) {
            if (!offer.getId().equals(selectedOffer.getId())) {
                offer.setStatus(LoanOfferStatus.EXPIRED);
            }
        }

        loanOfferRepository.saveAll(offers);
        loanApplicationRepository.save(application);

        saveStateHistory(application, previousState, nextState, LoanLifecycleEvent.SELECT_OFFER, "borrower:" + borrowerId, requestId, null);

        kafkaEventPort.publish(
                "loan.offer.selected",
                tenantId,
                applicationId,
                "selectedOfferId=" + selectedOffer.getId()
        );

        log.debug("Loan offer selection exit tenantId={} borrowerId={} applicationId={} nextState={}",
                tenantId, borrowerId, applicationId, nextState);

        return LoanOfferSelectionResponse.builder()
                .applicationId(applicationId)
                .selectedOfferId(selectedOffer.getId())
                .status(application.getStatus().name())
                .loanState(application.getLoanState().name())
                .selectedAt(selectedOffer.getSelectedAt())
                .build();
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void expireApplicationIfRequired(LoanApplication application, String requestId, String changedBy) {
        if (application.getStatus() != LoanApplicationStatus.APPLICATION_SUBMITTED) {
            return;
        }
        if (!application.getOffersExpiresAt().isBefore(Instant.now())) {
            return;
        }

        LoanLifecycleState previousState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.EXPIRE_OFFERS);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.OFFER_EXPIRED);
        loanApplicationRepository.save(application);

        List<LoanOffer> offers = loanOfferRepository.findByApplicationIdAndTenantId(application.getId(), application.getTenantId());
        offers.forEach(offer -> offer.setStatus(LoanOfferStatus.EXPIRED));
        loanOfferRepository.saveAll(offers);

        saveStateHistory(application, previousState, nextState, LoanLifecycleEvent.EXPIRE_OFFERS, changedBy, requestId, "Offer validity window elapsed");

        kafkaEventPort.publish(
                "loan.offer.expired",
                application.getTenantId(),
                application.getId(),
                "offersExpired=true"
        );

        log.debug("Expired loan offers tenantId={} applicationId={} requestId={}",
                application.getTenantId(), application.getId(), requestId);
    }

    private void saveStateHistory(
            LoanApplication application,
            LoanLifecycleState fromState,
            LoanLifecycleState toState,
            LoanLifecycleEvent event,
            String changedBy,
            String requestId,
            String reason
    ) {
        loanStateHistoryRepository.save(LoanStateHistory.builder()
                .id(UUID.randomUUID())
                .tenantId(application.getTenantId())
                .applicationId(application.getId())
                .fromState(fromState == null ? null : fromState.name())
                .toState(toState.name())
                .event(event.name())
                .changedBy(changedBy)
                .requestId(requestId)
                .reason(reason)
                .changedAt(Instant.now())
                .build());
    }
}
