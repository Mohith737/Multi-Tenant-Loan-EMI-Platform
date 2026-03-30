package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.ReconciliationReportRepository;
import com.loanplatform.loan_platform.domain.loan.service.ReconciliationJob;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;
    @Mock
    private EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    @Mock
    private ReconciliationReportRepository reconciliationReportRepository;
    @Mock
    private KafkaEventPort kafkaEventPort;

    @InjectMocks
    @Spy
    private ReconciliationJob reconciliationJob;

    @Test
    void generateForTenant_whenNoDiscrepancy_marksReportReconciledTrue() {
        UUID tenantId = UUID.randomUUID();
        LocalDate reportDate = LocalDate.now();

        when(loanInstallmentRepository.countDueByTenantAndDate(tenantId, reportDate)).thenReturn(10L);
        when(loanInstallmentRepository.countPaidByTenantAndDate(tenantId, reportDate)).thenReturn(8L);
        when(loanInstallmentRepository.countFailedByTenantAndDate(tenantId, reportDate)).thenReturn(2L);
        when(loanInstallmentRepository.sumTotalDueByTenantAndDate(tenantId, reportDate)).thenReturn(new BigDecimal("10000"));
        when(loanInstallmentRepository.sumPaidAmountByTenantAndDate(tenantId, reportDate)).thenReturn(new BigDecimal("8000"));
        when(emiPaymentAttemptRepository.sumMambuPostedAmountForTenantBetween(eq(tenantId), any(), any())).thenReturn(new BigDecimal("8000"));
        when(reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, reportDate)).thenReturn(Optional.empty());
        when(reconciliationReportRepository.save(any(ReconciliationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationJob.generateForTenant(tenantId, reportDate);

        ArgumentCaptor<ReconciliationReport> captor = ArgumentCaptor.forClass(ReconciliationReport.class);
        verify(reconciliationReportRepository).save(captor.capture());
        assertThat(captor.getValue().isReconciled()).isTrue();
        assertThat(captor.getValue().getDiscrepancyAmount()).isEqualByComparingTo("0");
        verify(kafkaEventPort, never()).publish(eq("emi.reconciliation.discrepancy"), any(), any(), any());
    }

    @Test
    void generateForTenant_whenDiscrepancyExists_publishesDiscrepancyEvent() {
        UUID tenantId = UUID.randomUUID();
        LocalDate reportDate = LocalDate.now();

        when(loanInstallmentRepository.countDueByTenantAndDate(tenantId, reportDate)).thenReturn(10L);
        when(loanInstallmentRepository.countPaidByTenantAndDate(tenantId, reportDate)).thenReturn(9L);
        when(loanInstallmentRepository.countFailedByTenantAndDate(tenantId, reportDate)).thenReturn(1L);
        when(loanInstallmentRepository.sumTotalDueByTenantAndDate(tenantId, reportDate)).thenReturn(new BigDecimal("10000"));
        when(loanInstallmentRepository.sumPaidAmountByTenantAndDate(tenantId, reportDate)).thenReturn(new BigDecimal("9000"));
        when(emiPaymentAttemptRepository.sumMambuPostedAmountForTenantBetween(eq(tenantId), any(), any())).thenReturn(new BigDecimal("8500"));
        when(reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, reportDate)).thenReturn(Optional.empty());
        when(reconciliationReportRepository.save(any(ReconciliationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationJob.generateForTenant(tenantId, reportDate);

        verify(kafkaEventPort).publish(eq("emi.reconciliation.discrepancy"), eq(tenantId), any(), any());
    }

    @Test
    void runNightlyReconciliation_activeTenantsPresent_generatesForEachTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Tenant t1 = Tenant.builder().id(tenantA).status(TenantStatus.ACTIVE).stripeAccountId("acct_a").name("A").domain("a.test").planTier("STD").build();
        Tenant t2 = Tenant.builder().id(tenantB).status(TenantStatus.ACTIVE).stripeAccountId("acct_b").name("B").domain("b.test").planTier("STD").build();

        when(tenantRepository.findByStatusAndStripeAccountIdIsNotNullOrderByCreatedAtDesc(TenantStatus.ACTIVE))
                .thenReturn(List.of(t1, t2));

        doNothing().when(reconciliationJob).generateForTenant(eq(tenantA), any());
        doNothing().when(reconciliationJob).generateForTenant(eq(tenantB), any());

        reconciliationJob.runNightlyReconciliation();

        verify(reconciliationJob).generateForTenant(eq(tenantA), any());
        verify(reconciliationJob).generateForTenant(eq(tenantB), any());
    }
}
