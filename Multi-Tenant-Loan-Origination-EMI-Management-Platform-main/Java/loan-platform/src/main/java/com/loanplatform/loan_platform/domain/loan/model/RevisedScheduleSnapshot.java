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
@Table(name = "revised_schedule_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisedScheduleSnapshot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "prepayment_request_id", nullable = false, updatable = false)
    private UUID prepaymentRequestId;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "snapshot_version", nullable = false)
    private Integer snapshotVersion;

    @Column(name = "schedule_payload_json", nullable = false, columnDefinition = "TEXT")
    private String schedulePayloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.snapshotVersion == null) {
            this.snapshotVersion = 1;
        }
    }
}
