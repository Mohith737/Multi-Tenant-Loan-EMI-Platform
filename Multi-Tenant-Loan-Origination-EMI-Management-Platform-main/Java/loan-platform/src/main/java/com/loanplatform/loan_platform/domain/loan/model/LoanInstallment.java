package com.loanplatform.loan_platform.domain.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_installments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanInstallment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private UUID borrowerId;

    @Column(name = "mambu_loan_id", nullable = false, length = 120)
    private String mambuLoanId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanInstallmentStatus status;

    @Column(name = "stripe_payment_intent_id", length = 120)
    private String stripePaymentIntentId;

    @Column(name = "stripe_payment_status", length = 40)
    private String stripePaymentStatus;

    @Column(name = "mambu_installment_state", length = 30)
    private String mambuInstallmentState;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "payment_failure_reason", length = 500)
    private String paymentFailureReason;

    @Column(name = "mambu_sync_pending", nullable = false)
    private boolean mambuSyncPending;

    @Column(name = "waived", nullable = false)
    private boolean waived;

    @Column(name = "waived_reason", length = 500)
    private String waivedReason;

    @Column(name = "waived_by")
    private UUID waivedBy;

    @Column(name = "waived_at")
    private Instant waivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = LoanInstallmentStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
