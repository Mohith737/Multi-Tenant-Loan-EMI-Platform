package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.loan.model.EligibilityDecision;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.service.EligibilityEngine;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.exception.DuplicateActiveLoanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityEngineTest {

    @Mock
    private CreditProfileRepository creditProfileRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @InjectMocks
    private EligibilityEngine eligibilityEngine;

    @Test
    void evaluate_activeLoanExists_throwsDuplicateActiveLoanException() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        when(loanApplicationRepository.existsByTenantIdAndBorrowerIdAndStatusIn(
                tenantId,
                borrowerId,
                LoanApplicationStatus.activeStatuses()
        )).thenReturn(true);

        assertThatThrownBy(() -> eligibilityEngine.evaluate(
                tenantId,
                borrowerId,
                buildLoanProduct(),
                BigDecimal.valueOf(250000),
                24
        )).isInstanceOf(DuplicateActiveLoanException.class);
    }

    @Test
    void evaluate_creditScoreLow_returnsIneligibleDecision() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        when(loanApplicationRepository.existsByTenantIdAndBorrowerIdAndStatusIn(
                tenantId,
                borrowerId,
                LoanApplicationStatus.activeStatuses()
        )).thenReturn(false);

        when(creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId))
                .thenReturn(Optional.of(buildCreditProfile(620, BigDecimal.valueOf(25), BigDecimal.valueOf(90000), 24)));

        EligibilityDecision decision = eligibilityEngine.evaluate(
                tenantId,
                borrowerId,
                buildLoanProduct(),
                BigDecimal.valueOf(250000),
                24
        );

        assertThat(decision.isEligible()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("LOW_CREDIT_SCORE");
    }

    @Test
    void evaluate_allRulesPass_returnsEligibleDecision() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        when(loanApplicationRepository.existsByTenantIdAndBorrowerIdAndStatusIn(
                tenantId,
                borrowerId,
                LoanApplicationStatus.activeStatuses()
        )).thenReturn(false);

        when(creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId))
                .thenReturn(Optional.of(buildCreditProfile(780, BigDecimal.valueOf(32), BigDecimal.valueOf(120000), 48)));

        EligibilityDecision decision = eligibilityEngine.evaluate(
                tenantId,
                borrowerId,
                buildLoanProduct(),
                BigDecimal.valueOf(300000),
                36
        );

        assertThat(decision.isEligible()).isTrue();
        assertThat(decision.getReasonCode()).isNull();
        assertThat(decision.getRiskCategory()).isEqualTo("LOW");
        assertThat(decision.getMaxApprovedAmount()).isEqualByComparingTo("800000.00");
    }

    private LoanProduct buildLoanProduct() {
        return LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .mambuProductId("LP_TEST")
                .mambuProductKey("key_lp_test")
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(800000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .build())
                .build();
    }

    private CreditProfile buildCreditProfile(int creditScore, BigDecimal dtiRatio, BigDecimal monthlyIncome, int employmentMonths) {
        return CreditProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .borrowerId(UUID.randomUUID())
                .creditScore(creditScore)
                .dtiRatio(dtiRatio)
                .monthlyIncome(monthlyIncome)
                .employmentType("SALARIED")
                .employmentMonths(employmentMonths)
                .existingEmiObligations(BigDecimal.valueOf(5000))
                .creditBureau("CIBIL")
                .eligibilityCategory("PRIME")
                .build();
    }
}
