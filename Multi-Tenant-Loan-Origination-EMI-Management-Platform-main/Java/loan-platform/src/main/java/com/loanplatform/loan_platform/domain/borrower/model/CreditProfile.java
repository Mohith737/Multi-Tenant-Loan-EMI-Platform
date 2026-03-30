package com.loanplatform.loan_platform.domain.borrower.model;

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
@Table(name = "credit_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditProfile {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "credit_bureau", nullable = false, length = 30)
    private String creditBureau;

    @Column(name = "monthly_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "existing_emi_obligations", nullable = false, precision = 19, scale = 2)
    private BigDecimal existingEmiObligations;

    @Column(name = "employment_type", nullable = false, length = 40)
    private String employmentType;

    @Column(name = "employer_name", length = 180)
    private String employerName;

    @Column(name = "employment_months", nullable = false)
    private Integer employmentMonths;

    @Column(name = "dti_ratio", nullable = false, precision = 10, scale = 2)
    private BigDecimal dtiRatio;

    @Column(name = "eligibility_category", nullable = false, length = 30)
    private String eligibilityCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
