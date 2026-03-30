package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanApproveRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanCreateRequest;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.LoanStateHistory;
import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecision;
import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecisionType;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.repository.UnderwriterDecisionRepository;
import com.loanplatform.loan_platform.dto.request.UnderwriterDecisionRequest;
import com.loanplatform.loan_platform.dto.response.UnderwriterDecisionResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterQueueItemResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterQueueResponse;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionConflictException;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionNotFoundException;
import com.loanplatform.loan_platform.mapper.LoanDocumentMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnderwriterService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final UnderwriterDecisionRepository underwriterDecisionRepository;
    private final LoanDocumentService loanDocumentService;
    private final LoanDocumentMapper loanDocumentMapper;
    private final LoanStateMachinePort loanStateMachinePort;
    private final LoanStateHistoryRepository loanStateHistoryRepository;
    private final KafkaEventPort kafkaEventPort;
    private final BorrowerNotificationPort borrowerNotificationPort;
    private final MambuPort mambuPort;

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public UnderwriterQueueResponse getQueue(UUID tenantId) {
        List<LoanApplication> applications = loanApplicationRepository.findByTenantIdAndStatusOrderBySubmittedAtAsc(
                tenantId,
                LoanApplicationStatus.UNDER_VERIFICATION
        );

        List<UnderwriterQueueItemResponse> queue = applications.stream()
                .map(application -> loanDocumentMapper.toUnderwriterQueueItemResponse(
                        application,
                        (int) loanDocumentService.countDocuments(tenantId, application.getId())
                ))
                .toList();

        return UnderwriterQueueResponse.builder()
                .queue(queue)
                .total(queue.size())
                .build();
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public UnderwriterDecisionResponse decide(
            UUID tenantId,
            UUID underwriterId,
            UUID applicationId,
            UnderwriterDecisionRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        log.debug("Underwriter decision entry tenantId={} underwriterId={} applicationId={} decision={} requestId={}",
                tenantId, underwriterId, applicationId, request.getDecision(), requestId);

        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        if (application.getStatus() != LoanApplicationStatus.UNDER_VERIFICATION) {
            throw new UnderwriterDecisionConflictException("Application is not in UNDER_VERIFICATION state");
        }
        loanDocumentService.validateRequiredDocumentsForDecision(tenantId, application);
        if (underwriterDecisionRepository.existsByApplicationId(applicationId)) {
            throw new UnderwriterDecisionConflictException("Underwriter decision already recorded for application");
        }

        UnderwriterDecision decision = UnderwriterDecision.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .underwriterId(underwriterId)
                .decision(request.getDecision())
                .remarks(request.getRemarks())
                .decidedAt(Instant.now())
                .build();
        underwriterDecisionRepository.save(decision);

        application.setUnderwriterId(underwriterId);
        application.setUnderwriterRemarks(request.getRemarks());
        application.setDecisionAt(decision.getDecidedAt());

        if (request.getDecision() == UnderwriterDecisionType.REJECTED) {
            rejectApplication(application, requestId, request.getRemarks());
        } else {
            approveApplication(application, requestId);
        }

        log.debug("Underwriter decision exit tenantId={} underwriterId={} applicationId={} status={} state={} requestId={}",
                tenantId, underwriterId, applicationId, application.getStatus(), application.getLoanState(), requestId);
        return loanDocumentMapper.toUnderwriterDecisionResponse(application, decision);
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public UnderwriterDecisionResponse getDecision(UUID tenantId, UUID borrowerId, UUID applicationId, boolean tenantAdmin) {
        LoanApplication application = tenantAdmin
                ? loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId))
                : loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        UnderwriterDecision decision = underwriterDecisionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new UnderwriterDecisionNotFoundException("Underwriter decision not found for application"));

        return loanDocumentMapper.toUnderwriterDecisionResponse(application, decision);
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void retryMambuSync(LoanApplication application) {
        if (!application.isMambuSyncFailed() || application.getStatus() != LoanApplicationStatus.UNDER_VERIFICATION) {
            return;
        }

        boolean mambuApproved = attemptMambuApproval(application);
        if (!mambuApproved) {
            return;
        }

        LoanLifecycleState previousState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.APPROVE);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.APPROVED);
        application.setMambuSyncFailed(false);
        loanApplicationRepository.save(application);

        saveStateHistory(
                application,
                previousState,
                nextState,
                LoanLifecycleEvent.APPROVE,
                "system:mambuSyncScheduler",
                UUID.randomUUID().toString(),
                "Mambu approval retry succeeded"
        );

        kafkaEventPort.publish("loan.application.approved", application.getTenantId(), application.getId(),
                "mambuLoanId=" + application.getMambuLoanId());
        borrowerNotificationPort.onLoanApproved(application.getBorrowerId(), application.getTenantId(), application.getId());
    }

    private void rejectApplication(LoanApplication application, String requestId, String reason) {
        LoanLifecycleState previousState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.REJECT);

        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.REJECTED);
        application.setRejectionReason(reason);
        application.setMambuSyncFailed(false);
        loanApplicationRepository.save(application);

        saveStateHistory(
                application,
                previousState,
                nextState,
                LoanLifecycleEvent.REJECT,
                "underwriter:" + application.getUnderwriterId(),
                requestId,
                reason
        );

        kafkaEventPort.publish("loan.application.rejected", application.getTenantId(), application.getId(), "reason=" + reason);
        borrowerNotificationPort.onLoanRejected(application.getBorrowerId(), application.getTenantId(), application.getId(), reason);
    }

    private void approveApplication(LoanApplication application, String requestId) {
        boolean mambuApproved = attemptMambuApproval(application);
        if (!mambuApproved) {
            application.setMambuSyncFailed(true);
            loanApplicationRepository.save(application);
            kafkaEventPort.publish("loan.underwriter.decision.failed", application.getTenantId(), application.getId(),
                    "mambuSyncFailed=true");
            return;
        }

        LoanLifecycleState previousState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.APPROVE);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.APPROVED);
        application.setMambuSyncFailed(false);
        loanApplicationRepository.save(application);

        saveStateHistory(
                application,
                previousState,
                nextState,
                LoanLifecycleEvent.APPROVE,
                "underwriter:" + application.getUnderwriterId(),
                requestId,
                null
        );

        kafkaEventPort.publish("loan.application.approved", application.getTenantId(), application.getId(),
                "mambuLoanId=" + application.getMambuLoanId());
        borrowerNotificationPort.onLoanApproved(application.getBorrowerId(), application.getTenantId(), application.getId());
    }

    private boolean attemptMambuApproval(LoanApplication application) {
        try {
            Borrower borrower = borrowerRepository.findByIdAndTenantId(application.getBorrowerId(), application.getTenantId())
                    .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + application.getBorrowerId()));
            if (borrower.getMambuClientKey() == null || borrower.getMambuClientKey().isBlank()) {
                throw new IllegalStateException("Borrower mambuClientKey is missing");
            }
            if (application.getSelectedOfferId() == null) {
                throw new IllegalStateException("selectedOfferId is required for Mambu loan account creation");
            }

            LoanOffer selectedOffer = loanOfferRepository.findByIdAndApplicationIdAndTenantId(
                            application.getSelectedOfferId(),
                            application.getId(),
                            application.getTenantId()
                    )
                    .orElseThrow(() -> new IllegalStateException("Selected offer not found for application"));

            MambuLoanCreateRequest createRequest = MambuLoanCreateRequest.builder()
                    .clientKey(borrower.getMambuClientKey())
                    .productTypeKey(application.getLoanProductKey())
                    .loanAmount(MambuLoanCreateRequest.AmountValue.builder()
                            .value(selectedOffer.getPrincipalAmount())
                            .build())
                    .interestRate(MambuLoanCreateRequest.AmountValue.builder()
                            .value(selectedOffer.getEffectiveAnnualRate())
                            .build())
                    .repaymentInstallments(selectedOffer.getTenureMonths())
                    .disbursementDetails(MambuLoanCreateRequest.DisbursementDetails.builder()
                            .expectedDisbursementDate((application.getRequestedDisbursementDate() == null
                                    ? LocalDate.now()
                                    : application.getRequestedDisbursementDate()).toString())
                            .build())
                    .build();

            MambuLoanAccountResponse createdLoan = mambuPort.createLoanAccount(createRequest);
            if (createdLoan == null || createdLoan.getId() == null || createdLoan.getId().isBlank()) {
                throw new IllegalStateException("Mambu create loan response missing id");
            }
            application.setMambuLoanId(createdLoan.getId());

            MambuLoanAccountResponse approvedLoan = mambuPort.approveLoanAccount(
                    createdLoan.getId(),
                    MambuLoanApproveRequest.builder()
                            .notes("Approved by underwriter")
                            .date(LocalDate.now().toString())
                            .build()
            );

            return approvedLoan != null && "APPROVED".equalsIgnoreCase(approvedLoan.getAccountState());
        } catch (Exception ex) {
            log.error("Mambu approval failed tenantId={} applicationId={}", application.getTenantId(), application.getId(), ex);
            return false;
        }
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
