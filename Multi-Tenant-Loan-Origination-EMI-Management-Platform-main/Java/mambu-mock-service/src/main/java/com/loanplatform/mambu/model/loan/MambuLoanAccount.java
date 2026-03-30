package com.loanplatform.mambu.model.loan;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mambu_loan_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanAccount {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "loan_id", nullable = false, unique = true, length = 100)
    private String loanId;

    @Column(name = "account_state", nullable = false, length = 100)
    @Builder.Default
    private String accountState = "PENDING_APPROVAL";

    @Column(name = "client_key", nullable = false, length = 64)
    private String clientKey;

    @Column(name = "product_type_key", nullable = false, length = 64)
    private String productTypeKey;

    @Column(name = "assigned_branch_key", nullable = false, length = 64)
    private String assignedBranchKey;

    @Column(name = "loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "repayment_installments", nullable = false)
    private Integer repaymentInstallments;

    @Column(name = "principal_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalBalance = BigDecimal.ZERO;

    @Column(name = "interest_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestBalance = BigDecimal.ZERO;

    @Column(name = "fees_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feesBalance = BigDecimal.ZERO;

    @Column(name = "penalty_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyBalance = BigDecimal.ZERO;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "first_repayment_date")
    private LocalDate firstRepaymentDate;

    @Column(name = "last_repayment_date")
    private LocalDate lastRepaymentDate;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "loanEncodedKey", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    @Builder.Default
    private List<MambuInstallment> installments = new ArrayList<>();

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

