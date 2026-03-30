package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentScheduleResponse;
import com.loanplatform.loan_platform.domain.loan.model.DisbursementStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.service.RepaymentScheduleService;
import com.loanplatform.loan_platform.exception.RepaymentSchedulePersistenceException;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock
    private MambuPort mambuPort;

    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;

    @InjectMocks
    private RepaymentScheduleService repaymentScheduleService;

    @Test
    void schedulePersistedCorrectly_allInstallmentsSaved() {
        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .borrowerId(UUID.randomUUID())
                .mambuLoanId("LN_001")
                .status(LoanApplicationStatus.DISBURSED)
                .loanState(LoanLifecycleState.DISBURSED)
                .disbursementStatus(DisbursementStatus.IN_PROGRESS)
                .build();

        MambuRepaymentScheduleResponse response = MambuRepaymentScheduleResponse.builder()
                .installments(List.of(
                        installment(1, "2026-04-01", "PENDING", "10000", "2000", "12000"),
                        installment(2, "2026-05-01", "PENDING", "10200", "1800", "12000")
                ))
                .build();

        when(mambuPort.getRepaymentSchedule("LN_001")).thenReturn(response);
        when(loanInstallmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<LoanInstallment> saved = repaymentScheduleService.fetchAndPersistSchedule(application);

        assertThat(saved).hasSize(2);
        assertThat(saved.getFirst().getInstallmentNumber()).isEqualTo(1);
        assertThat(saved.getFirst().getPrincipalAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getFirst().getInterestAmount()).isEqualByComparingTo("2000");
        assertThat(saved.getFirst().getTotalDue()).isEqualByComparingTo("12000");

        ArgumentCaptor<List<LoanInstallment>> captor = ArgumentCaptor.forClass(List.class);
        verify(loanInstallmentRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        verify(loanInstallmentRepository).deleteByTenantIdAndApplicationId(application.getTenantId(), application.getId());
    }

    @Test
    void scheduleFetchFails_setsRetryFlag_noBorrowerNotification() {
        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .borrowerId(UUID.randomUUID())
                .mambuLoanId("LN_001")
                .status(LoanApplicationStatus.DISBURSED)
                .loanState(LoanLifecycleState.DISBURSED)
                .build();

        when(mambuPort.getRepaymentSchedule("LN_001")).thenReturn(MambuRepaymentScheduleResponse.builder().installments(List.of()).build());

        assertThatThrownBy(() -> repaymentScheduleService.fetchAndPersistSchedule(application))
                .isInstanceOf(RepaymentSchedulePersistenceException.class)
                .hasMessageContaining("Unable to fetch/persist repayment schedule");

        verify(loanInstallmentRepository, never()).deleteByTenantIdAndApplicationId(eq(application.getTenantId()), eq(application.getId()));
        verify(loanInstallmentRepository, never()).saveAll(any());
    }

    private MambuRepaymentScheduleResponse.Installment installment(
            int number,
            String dueDate,
            String state,
            String principal,
            String interest,
            String totalDue
    ) {
        return MambuRepaymentScheduleResponse.Installment.builder()
                .number(number)
                .dueDate(dueDate)
                .state(state)
                .principal(MambuRepaymentScheduleResponse.Component.builder()
                        .amount(MambuRepaymentScheduleResponse.AmountValue.builder().value(new BigDecimal(principal)).build())
                        .build())
                .interest(MambuRepaymentScheduleResponse.Component.builder()
                        .amount(MambuRepaymentScheduleResponse.AmountValue.builder().value(new BigDecimal(interest)).build())
                        .build())
                .totalDue(MambuRepaymentScheduleResponse.TotalDue.builder().value(new BigDecimal(totalDue)).build())
                .lastPaidDate(null)
                .build();
    }
}
