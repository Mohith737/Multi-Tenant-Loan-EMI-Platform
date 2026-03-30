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
import java.util.UUID;

@Entity
@Table(name = "npa_loan_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpaLoanRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private UUID borrowerId;

    @Column(name = "mambu_loan_id", nullable = false, length = 120)
    private String mambuLoanId;

    @Column(name = "max_dpd", nullable = false)
    private Integer maxDpd;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "npa_flagged_at", nullable = false)
    private Instant npaFlaggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_stage", nullable = false, length = 50)
    private NpaRecoveryStage recoveryStage;

    @Column(name = "mambu_state", nullable = false, length = 50)
    private String mambuState;

    @Column(name = "legal_escalated_at")
    private Instant legalEscalatedAt;

    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "overridden_by")
    private UUID overriddenBy;

    @Column(name = "overridden_at")
    private Instant overriddenAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (npaFlaggedAt == null) {
            npaFlaggedAt = now;
        }
        if (recoveryStage == null) {
            recoveryStage = NpaRecoveryStage.DAY_1_REMINDER;
        }
        if (mambuState == null || mambuState.isBlank()) {
            mambuState = "NON_PERFORMING";
        }
        if (outstandingAmount == null) {
            outstandingAmount = BigDecimal.ZERO;
        }
        if (maxDpd == null) {
            maxDpd = 0;
        }
        active = overriddenAt == null;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
