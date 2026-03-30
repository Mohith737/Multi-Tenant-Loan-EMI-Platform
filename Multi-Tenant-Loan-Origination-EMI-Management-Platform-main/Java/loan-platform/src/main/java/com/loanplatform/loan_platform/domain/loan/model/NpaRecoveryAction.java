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
@Table(name = "npa_recovery_actions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpaRecoveryAction {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "npa_loan_record_id", nullable = false, updatable = false)
    private UUID npaLoanRecordId;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 60)
    private NpaRecoveryActionType actionType;

    @Column(name = "action_note", length = 1000)
    private String actionNote;

    @Column(name = "triggered_by", nullable = false, length = 40)
    private String triggeredBy;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @PrePersist
    void onCreate() {
        if (triggeredAt == null) {
            triggeredAt = Instant.now();
        }
        if (triggeredBy == null || triggeredBy.isBlank()) {
            triggeredBy = "SYSTEM";
        }
    }
}
