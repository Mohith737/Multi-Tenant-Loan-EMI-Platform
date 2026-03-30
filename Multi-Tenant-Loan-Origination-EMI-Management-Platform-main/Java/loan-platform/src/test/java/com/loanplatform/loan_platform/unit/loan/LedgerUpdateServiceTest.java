package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.service.LedgerUpdateService;
import com.loanplatform.loan_platform.exception.LedgerSyncException;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerUpdateServiceTest {

    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private EmiPaymentAttemptRepository emiPaymentAttemptRepository;

    @Mock
    private MambuPort mambuPort;

    @Mock
    private KafkaEventPort kafkaEventPort;

    @Mock
    private LoanStateMachinePort loanStateMachinePort;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private LedgerUpdateService ledgerUpdateService;

    private UUID tenantId;
    private UUID installmentId;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        installmentId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
    }

    @Test
    void handleSucceededPayment_duplicateWebhookAlreadyPosted_skipsMambuAndClearsKey() {
        LoanInstallment installment = baseInstallment();
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.existsByStripePaymentIntentIdAndMambuPostedTrue("pi_dup_1")).thenReturn(true);

        ledgerUpdateService.handleSucceededPayment(tenantId, installmentId, "pi_dup_1");

        verify(mambuPort, never()).postRepayment(any(), any());
        verify(stringRedisTemplate).delete("idempotency:EMI:LN_001:1:" + tenantId);
    }

    @Test
    void handleSucceededPayment_validSucceededPayment_marksPaidAndPublishesEvent() {
        LoanInstallment installment = baseInstallment();
        EmiPaymentAttempt attempt = EmiPaymentAttempt.builder().id(UUID.randomUUID()).installmentId(installmentId).build();
        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .build();

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.existsByStripePaymentIntentIdAndMambuPostedTrue("pi_success_1")).thenReturn(false);
        when(mambuPort.postRepayment(eq("LN_001"), any())).thenReturn(MambuRepaymentResponse.builder()
                .id("txn_1")
                .encodedKey("enc_txn_1")
                .amount(new BigDecimal("1000.00"))
                .build());
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.of(attempt));
        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));

        ledgerUpdateService.handleSucceededPayment(tenantId, installmentId, "pi_success_1");

        ArgumentCaptor<LoanInstallment> installmentCaptor = ArgumentCaptor.forClass(LoanInstallment.class);
        verify(loanInstallmentRepository).save(installmentCaptor.capture());
        assertThat(installmentCaptor.getValue().getStatus()).isEqualTo(LoanInstallmentStatus.PAID);
        assertThat(installmentCaptor.getValue().getPaidDate()).isEqualTo(LocalDate.now());
        assertThat(installmentCaptor.getValue().isMambuSyncPending()).isFalse();

        verify(kafkaEventPort).publish(eq("emi.collected"), eq(tenantId), eq(applicationId), any());
        verify(stringRedisTemplate).delete("idempotency:EMI:LN_001:1:" + tenantId);
    }

    @Test
    void handleSucceededPayment_mambuPostingFails_setsSyncPendingAndThrows() {
        LoanInstallment installment = baseInstallment();
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.existsByStripePaymentIntentIdAndMambuPostedTrue("pi_fail_1")).thenReturn(false);
        when(mambuPort.postRepayment(eq("LN_001"), any())).thenThrow(new RuntimeException("mambu down"));
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerUpdateService.handleSucceededPayment(tenantId, installmentId, "pi_fail_1"))
                .isInstanceOf(LedgerSyncException.class)
                .hasMessageContaining("Mambu repayment posting failed");

        ArgumentCaptor<LoanInstallment> installmentCaptor = ArgumentCaptor.forClass(LoanInstallment.class);
        verify(loanInstallmentRepository).save(installmentCaptor.capture());
        assertThat(installmentCaptor.getValue().isMambuSyncPending()).isTrue();
        assertThat(installmentCaptor.getValue().getStripePaymentStatus()).isEqualTo("succeeded");
    }

    @Test
    void markPaymentFailed_paymentFailedBelowRetryLimit_setsRetryScheduled() {
        LoanInstallment installment = baseInstallment();
        installment.setRetryCount(0);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.empty());

        ledgerUpdateService.markPaymentFailed(tenantId, installmentId, "pi_retry_1", "payment_failed", "bank declined", 3);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.RETRY_SCHEDULED);
        assertThat(installment.getRetryCount()).isEqualTo(1);
        verify(kafkaEventPort).publish(eq("emi.failed"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void markPaymentFailed_requiresAction_setsRequiresAction() {
        LoanInstallment installment = baseInstallment();
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.empty());

        ledgerUpdateService.markPaymentFailed(tenantId, installmentId, "pi_action_1", "requires_action", "3DS", 3);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.REQUIRES_ACTION);
        assertThat(installment.getRetryCount()).isEqualTo(0);
    }

    @Test
    void markPaymentFailed_canceled_setsFailedPermanently() {
        LoanInstallment installment = baseInstallment();
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.empty());

        ledgerUpdateService.markPaymentFailed(tenantId, installmentId, "pi_cancel_1", "canceled", "canceled", 3);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.FAILED);
        assertThat(installment.getRetryCount()).isEqualTo(0);
    }

    @Test
    void markPaymentFailed_retryExhausted_setsOverdueAndPublishesOverdueEvent() {
        LoanInstallment installment = baseInstallment();
        installment.setRetryCount(2);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installmentId)).thenReturn(Optional.empty());

        ledgerUpdateService.markPaymentFailed(tenantId, installmentId, "pi_overdue_1", "payment_failed", "failed", 3);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.OVERDUE);
        assertThat(installment.getRetryCount()).isEqualTo(3);
        verify(kafkaEventPort).publish(eq("emi.overdue"), eq(tenantId), eq(applicationId), any());
    }

    private LoanInstallment baseInstallment() {
        return LoanInstallment.builder()
                .id(installmentId)
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(UUID.randomUUID())
                .mambuLoanId("LN_001")
                .installmentNumber(1)
                .dueDate(LocalDate.now())
                .totalDue(new BigDecimal("1000.00"))
                .principalAmount(new BigDecimal("900.00"))
                .interestAmount(new BigDecimal("100.00"))
                .status(LoanInstallmentStatus.PROCESSING)
                .retryCount(0)
                .build();
    }
}
