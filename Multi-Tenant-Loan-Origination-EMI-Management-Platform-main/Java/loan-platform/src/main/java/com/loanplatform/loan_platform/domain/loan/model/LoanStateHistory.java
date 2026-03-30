package com.loanplatform.loan_platform.domain.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "loan_state_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanStateHistory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "from_state", length = 40)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 40)
    private String toState;

    @Column(nullable = false, length = 40)
    private String event;

    @Column(name = "changed_by", nullable = false, length = 120)
    private String changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.changedAt == null) {
            this.changedAt = now;
        }
    }
}
