package com.loanplatform.loan_platform.domain.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "foreclosure_quotes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeclosureQuote {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "principal_outstanding", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalOutstanding;

    @Column(name = "accrued_interest", nullable = false, precision = 19, scale = 2)
    private BigDecimal accruedInterest;

    @Column(name = "penalty_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "total_payable", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPayable;

    @Column(name = "quote_generated_at", nullable = false)
    private Instant quoteGeneratedAt;

    @Column(name = "quote_expires_at", nullable = false)
    private Instant quoteExpiresAt;

    @Column(name = "mambu_reference", length = 120)
    private String mambuReference;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (quoteGeneratedAt == null) {
            quoteGeneratedAt = now;
        }
        if (quoteExpiresAt == null) {
            quoteExpiresAt = now.plusSeconds(900);
        }
    }

    @PreUpdate
    void onUpdate() {
        // no-op for deterministic update behavior
    }
}
