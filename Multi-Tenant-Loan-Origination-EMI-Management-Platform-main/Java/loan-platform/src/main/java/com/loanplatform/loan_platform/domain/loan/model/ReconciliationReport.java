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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "total_due_count", nullable = false)
    private Integer totalDueCount;

    @Column(name = "total_collected_count", nullable = false)
    private Integer totalCollectedCount;

    @Column(name = "total_failed_count", nullable = false)
    private Integer totalFailedCount;

    @Column(name = "total_amount_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmountDue;

    @Column(name = "total_amount_collected", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmountCollected;

    @Column(name = "mambu_total_posted", nullable = false, precision = 19, scale = 2)
    private BigDecimal mambuTotalPosted;

    @Column(name = "discrepancy_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discrepancyAmount;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (totalDueCount == null) {
            totalDueCount = 0;
        }
        if (totalCollectedCount == null) {
            totalCollectedCount = 0;
        }
        if (totalFailedCount == null) {
            totalFailedCount = 0;
        }
        if (totalAmountDue == null) {
            totalAmountDue = BigDecimal.ZERO;
        }
        if (totalAmountCollected == null) {
            totalAmountCollected = BigDecimal.ZERO;
        }
        if (mambuTotalPosted == null) {
            mambuTotalPosted = BigDecimal.ZERO;
        }
        if (discrepancyAmount == null) {
            discrepancyAmount = BigDecimal.ZERO;
        }
    }
}
