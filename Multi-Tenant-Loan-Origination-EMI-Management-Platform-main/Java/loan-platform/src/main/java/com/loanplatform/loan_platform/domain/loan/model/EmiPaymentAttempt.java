package com.loanplatform.loan_platform.domain.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emi_payment_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmiPaymentAttempt {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "installment_id", nullable = false, updatable = false)
    private UUID installmentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private UUID borrowerId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(name = "stripe_payment_intent_id", length = 120)
    private String stripePaymentIntentId;

    @Column(name = "stripe_status", length = 40)
    private String stripeStatus;

    @Column(name = "mambu_transaction_id", length = 120)
    private String mambuTransactionId;

    @Column(name = "mambu_transaction_key", length = 120)
    private String mambuTransactionKey;

    @Column(name = "mambu_posted", nullable = false)
    private boolean mambuPosted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private EmiPaymentAttemptStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }
}
