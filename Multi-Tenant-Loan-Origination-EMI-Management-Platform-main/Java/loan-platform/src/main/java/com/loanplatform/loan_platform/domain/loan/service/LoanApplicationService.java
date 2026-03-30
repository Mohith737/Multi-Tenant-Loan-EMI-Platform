package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.EligibilityDecision;
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
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.dto.request.LoanApplicationRequest;
import com.loanplatform.loan_platform.dto.request.LoanOfferSelectionRequest;
import com.loanplatform.loan_platform.dto.response.LoanApplicationResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferSelectionResponse;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.LoanEligibilityFailedException;
import com.loanplatform.loan_platform.exception.MambuSimulationException;
import com.loanplatform.loan_platform.mapper.LoanApplicationMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.LoanApplicationUseCase;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.statemachine.actions.SubmitApplicationAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationService implements LoanApplicationUseCase {

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final LoanStateHistoryRepository loanStateHistoryRepository;
    private final EligibilityEngine eligibilityEngine;
    private final LoanOfferSelectionService loanOfferSelectionService;
    private final MambuPort mambuPort;
    private final LoanApplicationMapper loanApplicationMapper;
    private final LoanStateMachinePort loanStateMachinePort;
    private final KafkaEventPort kafkaEventPort;
    private final SubmitApplicationAction submitApplicationAction;

    @Value("${app.loan.offer-validity-hours:48}")
    private long offerValidityHours;

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public LoanApplicationResponse submitApplication(UUID tenantId, UUID borrowerId, LoanApplicationRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.debug("Submit loan application entry tenantId={} borrowerId={} requestId={} requestedAmount={} requestedTenure={}",
                tenantId, borrowerId, requestId, request.getRequestedAmount(), request.getRequestedTenureMonths());

        if (request.getBorrowerId() != null && !request.getBorrowerId().equals(borrowerId)) {
            throw new IllegalArgumentException("borrowerId in request does not match authenticated borrower");
        }

        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + borrowerId));

        if (borrower.getMambuClientKey() == null || borrower.getMambuClientKey().isBlank()) {
            throw new LoanEligibilityFailedException("Borrower is not synced with Mambu client profile");
        }

        LoanProduct loanProduct = loanProductRepository.findByTenantIdAndMambuProductKey(tenantId, request.getLoanProductKey())
                .orElseThrow(() -> new LoanEligibilityFailedException("Loan product not found for key: " + request.getLoanProductKey()));

        EligibilityDecision eligibilityDecision = eligibilityEngine.evaluate(
                tenantId,
                borrowerId,
                loanProduct,
                request.getRequestedAmount(),
                request.getRequestedTenureMonths()
        );

        if (!eligibilityDecision.isEligible()) {
            throw new LoanEligibilityFailedException("Loan eligibility failed: " + eligibilityDecision.getReasonCode());
        }

        LoanProductConfigSnapshot productConfig = loanProduct.getLoanProductConfig();
        int minTenure = productConfig.getMinTenureMonths() == null ? 1 : productConfig.getMinTenureMonths();
        int maxTenure = productConfig.getMaxTenureMonths() == null ? 600 : productConfig.getMaxTenureMonths();
        List<Integer> tenures = resolveTenureOptions(request.getRequestedTenureMonths(), minTenure, maxTenure);

        List<MambuLoanSimulationResponse> simulations = new ArrayList<>();
        for (Integer tenure : tenures) {
            simulations.add(simulateOffer(
                    tenantId,
                    borrower,
                    request,
                    loanProduct,
                    tenure,
                    requestId
            ));
        }

        Instant now = Instant.now();
        Instant offerExpiry = now.plusSeconds(offerValidityHours * 3600);

        LoanLifecycleState nextState = loanStateMachinePort.transition(LoanLifecycleState.APPLICATION_DRAFT, LoanLifecycleEvent.SUBMIT);

        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(loanProduct.getId())
                .loanProductKey(loanProduct.getMambuProductKey())
                .requestedAmount(request.getRequestedAmount())
                .requestedTenureMonths(request.getRequestedTenureMonths())
                .loanPurpose(request.getLoanPurpose())
                .requestedDisbursementDate(request.getRequestedDisbursementDate())
                .status(LoanApplicationStatus.APPLICATION_SUBMITTED)
                .loanState(nextState)
                .eligibilityPassed(eligibilityDecision.isEligible())
                .eligibilityReasonCode(eligibilityDecision.getReasonCode())
                .riskCategory(eligibilityDecision.getRiskCategory())
                .creditScoreSnapshot(eligibilityDecision.getCreditScore())
                .dtiRatioSnapshot(eligibilityDecision.getDtiRatio())
                .monthlyIncomeSnapshot(eligibilityDecision.getMonthlyIncome())
                .employmentTypeSnapshot(eligibilityDecision.getEmploymentType())
                .maxApprovedAmountSnapshot(eligibilityDecision.getMaxApprovedAmount())
                .offersExpiresAt(offerExpiry)
                .submittedAt(now)
                .build();

        LoanApplication savedApplication = loanApplicationRepository.save(application);

        BigDecimal processingFee = calculateProcessingFee(productConfig, request.getRequestedAmount());
        List<LoanOffer> offers = new ArrayList<>();
        for (int i = 0; i < simulations.size(); i++) {
            MambuLoanSimulationResponse simulation = simulations.get(i);
            LoanOffer offer = LoanOffer.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .applicationId(savedApplication.getId())
                    .offerRank((short) (i + 1))
                    .tenureMonths(tenures.get(i))
                    .principalAmount(request.getRequestedAmount())
                    .emiAmount(simulation.getPeriodicPayment())
                    .totalInterest(simulation.getTotalInterestCharged())
                    .effectiveAnnualRate(simulation.getAnnualPercentageRate())
                    .processingFeeAmount(processingFee)
                    .totalPayableAmount(simulation.getTotalAmountRepaid())
                    .expiresAt(offerExpiry)
                    .status(LoanOfferStatus.ACTIVE)
                    .mambuSimulationRef(requestId + ":" + tenures.get(i))
                    .build();
            offers.add(offer);
        }

        List<LoanOffer> savedOffers = loanOfferRepository.saveAll(offers);

        saveStateHistory(
                savedApplication,
                LoanLifecycleState.APPLICATION_DRAFT,
                nextState,
                LoanLifecycleEvent.SUBMIT,
                "borrower:" + borrowerId,
                requestId,
                null
        );

        submitApplicationAction.execute(tenantId, savedApplication.getId(), requestId);
        kafkaEventPort.publish("loan.application.submitted", tenantId, savedApplication.getId(), "offers=" + savedOffers.size());

        LoanApplicationResponse response = loanApplicationMapper.toLoanApplicationResponse(savedApplication);
        response.setOffers(loanApplicationMapper.toLoanOfferResponses(savedOffers));

        log.debug("Submit loan application exit tenantId={} borrowerId={} applicationId={} requestId={}",
                tenantId, borrowerId, savedApplication.getId(), requestId);
        return response;
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public List<LoanOfferResponse> getOffers(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        if (application.getStatus() == LoanApplicationStatus.APPLICATION_SUBMITTED && application.getOffersExpiresAt().isBefore(Instant.now())) {
            loanOfferSelectionService.expireApplicationIfRequired(application, UUID.randomUUID().toString(), "system");
        }

        return loanApplicationMapper.toLoanOfferResponses(
                loanOfferRepository.findByApplicationIdAndTenantIdOrderByOfferRankAsc(applicationId, tenantId)
        );
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public LoanOfferSelectionResponse selectOffer(UUID tenantId, UUID borrowerId, UUID applicationId, LoanOfferSelectionRequest request) {
        return loanOfferSelectionService.selectOffer(tenantId, borrowerId, applicationId, request, UUID.randomUUID().toString());
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanApplicationResponse getApplication(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        return toResponseWithOffers(application);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanApplicationResponse getApplicationForTenant(UUID tenantId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        return toResponseWithOffers(application);
    }

    @Scheduled(fixedDelayString = "${app.loan.offer-expiry-scan-ms:60000}")
    @Transactional(propagation = Propagation.REQUIRED)
    public void expireStaleOffers() {
        List<LoanApplication> expiredApps = loanApplicationRepository.findByStatusAndOffersExpiresAtBefore(
                LoanApplicationStatus.APPLICATION_SUBMITTED,
                Instant.now()
        );
        for (LoanApplication application : expiredApps) {
            try {
                loanOfferSelectionService.expireApplicationIfRequired(application, UUID.randomUUID().toString(), "system");
            } catch (Exception ex) {
                log.error("Offer expiry job failed tenantId={} applicationId={}",
                        application.getTenantId(), application.getId(), ex);
            }
        }
    }

    private LoanApplicationResponse toResponseWithOffers(LoanApplication application) {
        LoanApplicationResponse response = loanApplicationMapper.toLoanApplicationResponse(application);
        response.setOffers(loanApplicationMapper.toLoanOfferResponses(
                loanOfferRepository.findByApplicationIdAndTenantIdOrderByOfferRankAsc(application.getId(), application.getTenantId())
        ));
        return response;
    }

    private MambuLoanSimulationResponse simulateOffer(
            UUID tenantId,
            Borrower borrower,
            LoanApplicationRequest request,
            LoanProduct loanProduct,
            int tenure,
            String requestId
    ) {
        try {
            MambuLoanSimulationRequest simulationRequest = MambuLoanSimulationRequest.builder()
                    .loanAmount(new MambuLoanSimulationRequest.AmountValue(request.getRequestedAmount()))
                    .interestRate(new MambuLoanSimulationRequest.AmountValue(loanProduct.getLoanProductConfig().getAnnualInterestRate()))
                    .repaymentInstallments(tenure)
                    .loanProductTypeKey(loanProduct.getMambuProductKey())
                    .clientKey(borrower.getMambuClientKey())
                    .disbursementDetails(MambuLoanSimulationRequest.DisbursementDetails.builder()
                            .expectedDisbursementDate(resolveDisbursementDate(request.getRequestedDisbursementDate()).toString())
                            .build())
                    .repaymentScheduleMethod("STANDARD")
                    .interestCalculationMethod("DECLINING_BALANCE")
                    .build();

            MambuLoanSimulationResponse response = mambuPort.simulateLoan(simulationRequest);
            if (response == null || response.getPeriodicPayment() == null || response.getTotalInterestCharged() == null
                    || response.getTotalAmountRepaid() == null || response.getAnnualPercentageRate() == null) {
                throw new IllegalStateException("Mambu simulation response missing required fields");
            }
            return response;
        } catch (Exception ex) {
            log.error("Mambu simulation failed tenantId={} borrowerId={} tenure={} requestId={}",
                    tenantId, borrower.getId(), tenure, requestId, ex);
            throw new MambuSimulationException("Mambu loan simulation failed for tenure " + tenure, ex);
        }
    }

    private List<Integer> resolveTenureOptions(int requestedTenure, int minTenure, int maxTenure) {
        Set<Integer> options = new LinkedHashSet<>();
        options.add(clamp(requestedTenure, minTenure, maxTenure));
        options.add(clamp(requestedTenure - 12, minTenure, maxTenure));
        options.add(clamp(requestedTenure + 12, minTenure, maxTenure));

        for (int delta = 1; options.size() < 3 && delta <= (maxTenure - minTenure); delta++) {
            options.add(clamp(requestedTenure - delta, minTenure, maxTenure));
            if (options.size() == 3) {
                break;
            }
            options.add(clamp(requestedTenure + delta, minTenure, maxTenure));
        }

        return options.stream().limit(3).toList();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private LocalDate resolveDisbursementDate(LocalDate requestedDisbursementDate) {
        if (requestedDisbursementDate == null) {
            return LocalDate.now();
        }
        return requestedDisbursementDate;
    }

    private BigDecimal calculateProcessingFee(LoanProductConfigSnapshot config, BigDecimal requestedAmount) {
        BigDecimal feePercent = config.getProcessingFeePercent();
        if (feePercent == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return requestedAmount.multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
                .reason(reason)
                .requestId(requestId)
                .changedAt(Instant.now())
                .build());
    }
}
