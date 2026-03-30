package com.loanplatform.mambu.model.loan;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "mambu_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_encoded_key", nullable = false, length = 64)
    private String loanEncodedKey;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String state = "PENDING";

    // Principal
    @Column(name = "principal_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalAmount = BigDecimal.ZERO;

    @Column(name = "principal_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "principal_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalDue = BigDecimal.ZERO;

    // Interest
    @Column(name = "interest_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "interest_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "interest_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestDue = BigDecimal.ZERO;

    // Fee
    @Column(name = "fee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "fee_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feePaid = BigDecimal.ZERO;

    @Column(name = "fee_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feeDue = BigDecimal.ZERO;

    // Penalty
    @Column(name = "penalty_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "penalty_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Column(name = "penalty_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "total_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDue = BigDecimal.ZERO;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;
}

