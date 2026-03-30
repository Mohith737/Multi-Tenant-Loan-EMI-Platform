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
@Table(name = "loan_applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplication {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private UUID borrowerId;

    @Column(name = "loan_product_id", nullable = false, updatable = false)
    private UUID loanProductId;

    @Column(name = "loan_product_key", nullable = false, length = 100)
    private String loanProductKey;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_tenure_months", nullable = false)
    private Integer requestedTenureMonths;

    @Column(name = "loan_purpose", length = 120)
    private String loanPurpose;

    @Column(name = "requested_disbursement_date")
    private LocalDate requestedDisbursementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LoanApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_state", nullable = false, length = 40)
    private LoanLifecycleState loanState;

    @Column(name = "eligibility_passed", nullable = false)
    private boolean eligibilityPassed;

    @Column(name = "eligibility_reason_code", length = 80)
    private String eligibilityReasonCode;

    @Column(name = "risk_category", length = 30)
    private String riskCategory;

    @Column(name = "credit_score_snapshot", nullable = false)
    private Integer creditScoreSnapshot;

    @Column(name = "dti_ratio_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal dtiRatioSnapshot;

    @Column(name = "monthly_income_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncomeSnapshot;

    @Column(name = "employment_type_snapshot", nullable = false, length = 40)
    private String employmentTypeSnapshot;

    @Column(name = "max_approved_amount_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxApprovedAmountSnapshot;

    @Column(name = "offers_expires_at", nullable = false)
    private Instant offersExpiresAt;

    @Column(name = "selected_offer_id")
    private UUID selectedOfferId;

    @Column(name = "mambu_loan_id", length = 120)
    private String mambuLoanId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "underwriter_id")
    private UUID underwriterId;

    @Column(name = "underwriter_remarks", length = 1000)
    private String underwriterRemarks;

    @Column(name = "mambu_sync_failed", nullable = false)
    private boolean mambuSyncFailed;

    @Enumerated(EnumType.STRING)
    @Column(name = "disbursement_status", nullable = false, length = 30)
    private DisbursementStatus disbursementStatus;

    @Column(name = "mambu_disbursement_txn_id", length = 120)
    private String mambuDisbursementTxnId;

    @Column(name = "stripe_transfer_id", length = 120)
    private String stripeTransferId;

    @Column(name = "net_disbursed_amount", precision = 19, scale = 2)
    private BigDecimal netDisbursedAmount;

    @Column(name = "processing_fee_charged", precision = 19, scale = 2)
    private BigDecimal processingFeeCharged;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "first_repayment_date")
    private LocalDate firstRepaymentDate;

    @Column(name = "schedule_fetch_failed", nullable = false)
    private boolean scheduleFetchFailed;

    @Column(name = "mambu_disburse_sync_failed", nullable = false)
    private boolean mambuDisburseSyncFailed;

    @Column(name = "disbursement_failure_reason", length = 500)
    private String disbursementFailureReason;

    @Column(name = "schedule_persisted_at")
    private Instant schedulePersistedAt;

    @Column(name = "last_prepayment_at")
    private Instant lastPrepaymentAt;

    @Column(name = "foreclosed_at")
    private Instant foreclosedAt;

    @Column(name = "foreclosure_amount", precision = 19, scale = 2)
    private BigDecimal foreclosureAmount;

    @Column(name = "closure_reason", length = 100)
    private String closureReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.submittedAt == null) {
            this.submittedAt = now;
        }
        if (this.disbursementStatus == null) {
            this.disbursementStatus = DisbursementStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
