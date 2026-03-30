package com.loanplatform.loan_platform.domain.tenant.model;

import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProductConfigSnapshot {

    @Column(name = "loan_product_name", length = 150)
    private String productName;

    @Column(name = "min_loan_amount", precision = 19, scale = 2)
    private BigDecimal minLoanAmount;

    @Column(name = "max_loan_amount", precision = 19, scale = 2)
    private BigDecimal maxLoanAmount;

    @Column(name = "min_tenure_months")
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months")
    private Integer maxTenureMonths;

    @Column(name = "annual_interest_rate", precision = 10, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "processing_fee_percent", precision = 10, scale = 4)
    private BigDecimal processingFeePercent;

    @Column(name = "prepayment_penalty_percent", precision = 10, scale = 4)
    private BigDecimal prepaymentPenaltyPercent;

    @Column(name = "loan_currency", length = 10)
    private String currency;

    public static LoanProductConfigSnapshot fromRequest(LoanProductConfigRequest request) {
        if (request == null) {
            return null;
        }
        return LoanProductConfigSnapshot.builder()
                .productName(request.getProductName())
                .minLoanAmount(request.getMinLoanAmount())
                .maxLoanAmount(request.getMaxLoanAmount())
                .minTenureMonths(request.getMinTenureMonths())
                .maxTenureMonths(request.getMaxTenureMonths())
                .annualInterestRate(request.getAnnualInterestRate())
                .processingFeePercent(request.getProcessingFeePercent())
                .prepaymentPenaltyPercent(request.getPrepaymentPenaltyPercent())
                .currency(request.getCurrency())
                .build();
    }
}
