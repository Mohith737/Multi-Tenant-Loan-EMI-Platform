package com.loanplatform.mambu.model.loanproduct;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_loan_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanProduct {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "product_id", nullable = false, unique = true, length = 100)
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String state = "ACTIVE";

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String type = "FIXED_TERM_LOAN";

    @Column(name = "currency_code", nullable = false, length = 10)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "for_branch_key", nullable = false, length = 64)
    private String forBranchKey;

    // Interest Settings
    @Column(name = "default_interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal defaultInterestRate;

    @Column(name = "interest_calculation_method", nullable = false)
    @Builder.Default
    private String interestCalculationMethod = "DECLINING_BALANCE";

    @Column(name = "interest_charge_frequency", nullable = false)
    @Builder.Default
    private String interestChargeFrequency = "ANNUALIZED";

    // Amount Settings
    @Column(name = "min_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minLoanAmount;

    @Column(name = "max_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxLoanAmount;

    @Column(name = "default_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal defaultLoanAmount;

    // Schedule Settings
    @Column(name = "default_repayment_period_count", nullable = false)
    @Builder.Default
    private Integer defaultRepaymentPeriodCount = 12;

    @Column(name = "default_repayment_period_unit", nullable = false)
    @Builder.Default
    private String defaultRepaymentPeriodUnit = "MONTHS";

    @Column(name = "repayment_schedule_method", nullable = false)
    @Builder.Default
    private String repaymentScheduleMethod = "STANDARD";

    // Fees
    @Column(name = "processing_fee_percent", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal processingFeePercent = BigDecimal.ZERO;

    @Column(name = "prepayment_penalty_percent", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal prepaymentPenaltyPercent = BigDecimal.ZERO;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

