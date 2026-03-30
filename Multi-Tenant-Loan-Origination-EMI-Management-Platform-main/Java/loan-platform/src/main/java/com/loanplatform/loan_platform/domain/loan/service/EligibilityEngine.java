package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.loan.model.EligibilityDecision;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.exception.DuplicateActiveLoanException;
import com.loanplatform.loan_platform.exception.LoanEligibilityFailedException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EligibilityEngine {

    private static final int MIN_CREDIT_SCORE = 650;
    private static final BigDecimal MAX_DTI_RATIO = BigDecimal.valueOf(60);
    private static final BigDecimal MIN_MONTHLY_INCOME = BigDecimal.valueOf(25000);
    private static final int MIN_EMPLOYMENT_MONTHS = 6;

    private final CreditProfileRepository creditProfileRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public EligibilityDecision evaluate(
            UUID tenantId,
            UUID borrowerId,
            LoanProduct loanProduct,
            BigDecimal requestedAmount,
            Integer requestedTenureMonths
    ) {
        log.debug("Eligibility evaluation entry tenantId={} borrowerId={} requestedAmount={} requestedTenureMonths={}",
                tenantId, borrowerId, requestedAmount, requestedTenureMonths);

        boolean hasActiveLoan = loanApplicationRepository.existsByTenantIdAndBorrowerIdAndStatusIn(
                tenantId,
                borrowerId,
                LoanApplicationStatus.activeStatuses()
        );
        if (hasActiveLoan) {
            throw new DuplicateActiveLoanException("Borrower already has an active loan application/loan");
        }

        CreditProfile creditProfile = creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new LoanEligibilityFailedException("Credit profile not found for borrower"));

        LoanProductConfigSnapshot config = loanProduct.getLoanProductConfig();
        if (config == null) {
            throw new LoanEligibilityFailedException("Loan product configuration is missing");
        }

        if (creditProfile.getCreditScore() < MIN_CREDIT_SCORE) {
            return decision(false, "LOW_CREDIT_SCORE", creditProfile, config, requestedAmount);
        }

        if (creditProfile.getDtiRatio().compareTo(MAX_DTI_RATIO) > 0) {
            return decision(false, "HIGH_DTI", creditProfile, config, requestedAmount);
        }

        if (creditProfile.getMonthlyIncome().compareTo(MIN_MONTHLY_INCOME) < 0) {
            return decision(false, "LOW_INCOME", creditProfile, config, requestedAmount);
        }

        if (creditProfile.getEmploymentMonths() < MIN_EMPLOYMENT_MONTHS) {
            return decision(false, "INSUFFICIENT_EMPLOYMENT_HISTORY", creditProfile, config, requestedAmount);
        }

        if (creditProfile.getEmploymentType() == null || creditProfile.getEmploymentType().isBlank()) {
            return decision(false, "EMPLOYMENT_TYPE_MISSING", creditProfile, config, requestedAmount);
        }

        if (config.getMinLoanAmount() != null && requestedAmount.compareTo(config.getMinLoanAmount()) < 0) {
            return decision(false, "AMOUNT_BELOW_PRODUCT_MIN", creditProfile, config, requestedAmount);
        }

        if (config.getMaxLoanAmount() != null && requestedAmount.compareTo(config.getMaxLoanAmount()) > 0) {
            return decision(false, "AMOUNT_ABOVE_PRODUCT_MAX", creditProfile, config, requestedAmount);
        }

        if (config.getMinTenureMonths() != null && requestedTenureMonths < config.getMinTenureMonths()) {
            return decision(false, "TENURE_BELOW_PRODUCT_MIN", creditProfile, config, requestedAmount);
        }

        if (config.getMaxTenureMonths() != null && requestedTenureMonths > config.getMaxTenureMonths()) {
            return decision(false, "TENURE_ABOVE_PRODUCT_MAX", creditProfile, config, requestedAmount);
        }

        EligibilityDecision decision = decision(true, null, creditProfile, config, requestedAmount);
        log.debug("Eligibility evaluation exit tenantId={} borrowerId={} eligible={} riskCategory={}",
                tenantId, borrowerId, decision.isEligible(), decision.getRiskCategory());
        return decision;
    }

    private EligibilityDecision decision(
            boolean eligible,
            String reasonCode,
            CreditProfile creditProfile,
            LoanProductConfigSnapshot config,
            BigDecimal requestedAmount
    ) {
        BigDecimal productCap = config.getMaxLoanAmount() == null ? requestedAmount : config.getMaxLoanAmount();
        BigDecimal incomeCap = creditProfile.getMonthlyIncome().multiply(BigDecimal.valueOf(24));
        BigDecimal maxApprovedAmount = incomeCap.min(productCap);

        return EligibilityDecision.builder()
                .eligible(eligible)
                .reasonCode(reasonCode)
                .riskCategory(resolveRiskCategory(creditProfile.getCreditScore(), creditProfile.getDtiRatio()))
                .creditScore(creditProfile.getCreditScore())
                .dtiRatio(creditProfile.getDtiRatio())
                .monthlyIncome(creditProfile.getMonthlyIncome())
                .employmentType(creditProfile.getEmploymentType())
                .maxApprovedAmount(maxApprovedAmount)
                .build();
    }

    private String resolveRiskCategory(int creditScore, BigDecimal dtiRatio) {
        if (creditScore >= 780 && dtiRatio.compareTo(BigDecimal.valueOf(35)) <= 0) {
            return "LOW";
        }
        if (creditScore >= 700 && dtiRatio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
