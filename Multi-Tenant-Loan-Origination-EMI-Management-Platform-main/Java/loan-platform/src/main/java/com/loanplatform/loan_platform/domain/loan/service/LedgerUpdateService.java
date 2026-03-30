package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttemptStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.exception.LedgerSyncException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerUpdateService {

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    private final MambuPort mambuPort;
    private final KafkaEventPort kafkaEventPort;
    private final LoanStateMachinePort loanStateMachinePort;
    private final StringRedisTemplate stringRedisTemplate;

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void handleSucceededPayment(UUID tenantId, UUID installmentId, String stripePaymentIntentId) {
        LoanInstallment installment = loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));

        if (emiPaymentAttemptRepository.existsByStripePaymentIntentIdAndMambuPostedTrue(stripePaymentIntentId)) {
            clearIdempotencyKey(installment);
            return;
        }

        String idempotencyKey = buildIdempotencyKey(installment);
        try {
            MambuRepaymentResponse repaymentResponse = mambuPort.postRepayment(
                    installment.getMambuLoanId(),
                    MambuRepaymentRequest.builder()
                            .amount(installment.getTotalDue())
                            .date(LocalDate.now().toString())
                            .notes("EMI " + installment.getInstallmentNumber() + " collection")
                            .externalId(idempotencyKey)
                            .transactionDetails(MambuRepaymentRequest.TransactionDetails.builder()
                                    .transactionChannelId("ONLINE_PAYMENT")
                                    .build())
                            .build()
            );

            if (repaymentResponse.getAmount() != null
                    && repaymentResponse.getAmount().compareTo(installment.getTotalDue()) != 0) {
                log.error("EMI amount mismatch tenantId={} installmentId={} local={} mambu={}",
                        tenantId, installmentId, installment.getTotalDue(), repaymentResponse.getAmount());
            }

            installment.setStatus(LoanInstallmentStatus.PAID);
            installment.setStripePaymentStatus("succeeded");
            installment.setPaidDate(LocalDate.now());
            installment.setLastPaidDate(LocalDate.now());
            installment.setMambuSyncPending(false);
            installment.setPaymentFailureReason(null);
            loanInstallmentRepository.save(installment);

            EmiPaymentAttempt attempt = latestOrNewAttempt(installment, idempotencyKey, stripePaymentIntentId);
            attempt.setStripeStatus("succeeded");
            attempt.setMambuPosted(true);
            attempt.setMambuTransactionId(repaymentResponse.getId());
            attempt.setMambuTransactionKey(repaymentResponse.getEncodedKey());
            attempt.setStatus(EmiPaymentAttemptStatus.MAMBU_POSTED);
            attempt.setFailureReason(null);
            emiPaymentAttemptRepository.save(attempt);

            loanApplicationRepository.findByIdAndTenantId(installment.getApplicationId(), tenantId)
                    .ifPresent(application -> loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.EMI_COLLECTED));

            kafkaEventPort.publish("emi.collected", tenantId, installment.getApplicationId(),
                    "installmentId=" + installmentId + ",paymentIntentId=" + stripePaymentIntentId + ",mambuTxn=" + repaymentResponse.getId());

            clearIdempotencyKey(installment);
        } catch (Exception ex) {
            installment.setMambuSyncPending(true);
            installment.setStripePaymentStatus("succeeded");
            installment.setPaymentFailureReason(ex.getMessage());
            loanInstallmentRepository.save(installment);

            EmiPaymentAttempt attempt = latestOrNewAttempt(installment, idempotencyKey, stripePaymentIntentId);
            attempt.setStripeStatus("succeeded");
            attempt.setMambuPosted(false);
            attempt.setStatus(EmiPaymentAttemptStatus.MAMBU_POST_FAILED);
            attempt.setFailureReason(ex.getMessage());
            emiPaymentAttemptRepository.save(attempt);

            throw new LedgerSyncException("Mambu repayment posting failed for installment " + installmentId, ex);
        }
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void markPaymentFailed(UUID tenantId,
                                  UUID installmentId,
                                  String stripePaymentIntentId,
                                  String stripeStatus,
                                  String failureReason,
                                  int maxRetryCount) {
        LoanInstallment installment = loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));

        installment.setStripePaymentStatus(stripeStatus);
        installment.setPaymentFailureReason(failureReason);
        installment.setLastRetryAt(Instant.now());

        if ("requires_action".equals(stripeStatus)) {
            installment.setStatus(LoanInstallmentStatus.REQUIRES_ACTION);
        } else if ("canceled".equals(stripeStatus)) {
            installment.setStatus(LoanInstallmentStatus.FAILED);
        } else {
            installment.setRetryCount(installment.getRetryCount() + 1);
            if (installment.getRetryCount() >= maxRetryCount) {
                installment.setStatus(LoanInstallmentStatus.OVERDUE);
                kafkaEventPort.publish("emi.overdue", tenantId, installment.getApplicationId(),
                        "installmentId=" + installment.getId() + ",retryCount=" + installment.getRetryCount());
            } else {
                installment.setStatus(LoanInstallmentStatus.RETRY_SCHEDULED);
            }
        }
        loanInstallmentRepository.save(installment);

        String idempotencyKey = buildIdempotencyKey(installment);
        EmiPaymentAttempt attempt = latestOrNewAttempt(installment, idempotencyKey, stripePaymentIntentId);
        attempt.setStripeStatus(stripeStatus);
        attempt.setMambuPosted(false);
        attempt.setStatus(EmiPaymentAttemptStatus.STRIPE_FAILED);
        attempt.setFailureReason(failureReason);
        emiPaymentAttemptRepository.save(attempt);

        kafkaEventPort.publish("emi.failed", tenantId, installment.getApplicationId(),
                "installmentId=" + installment.getId() + ",status=" + installment.getStatus());

        clearIdempotencyKey(installment);
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void retryPendingMambuSync(LoanInstallment installment) {
        if (!installment.isMambuSyncPending()) {
            return;
        }
        handleSucceededPayment(installment.getTenantId(), installment.getId(), installment.getStripePaymentIntentId());
    }

    private EmiPaymentAttempt latestOrNewAttempt(LoanInstallment installment, String idempotencyKey, String stripePaymentIntentId) {
        Optional<EmiPaymentAttempt> existing = emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installment.getId());
        if (existing.isPresent()) {
            EmiPaymentAttempt attempt = existing.get();
            if (stripePaymentIntentId != null && !stripePaymentIntentId.isBlank()) {
                attempt.setStripePaymentIntentId(stripePaymentIntentId);
            }
            return attempt;
        }

        return EmiPaymentAttempt.builder()
                .id(UUID.randomUUID())
                .tenantId(installment.getTenantId())
                .installmentId(installment.getId())
                .applicationId(installment.getApplicationId())
                .borrowerId(installment.getBorrowerId())
                .attemptNumber(Math.max(1, installment.getRetryCount()))
                .idempotencyKey(idempotencyKey)
                .stripePaymentIntentId(stripePaymentIntentId)
                .status(EmiPaymentAttemptStatus.INITIATED)
                .mambuPosted(false)
                .build();
    }

    private String buildIdempotencyKey(LoanInstallment installment) {
        return "idempotency:EMI:" + installment.getMambuLoanId() + ":" + installment.getInstallmentNumber() + ":" + installment.getTenantId();
    }

    private void clearIdempotencyKey(LoanInstallment installment) {
        String key = buildIdempotencyKey(installment);
        stringRedisTemplate.delete(key);
    }
}
