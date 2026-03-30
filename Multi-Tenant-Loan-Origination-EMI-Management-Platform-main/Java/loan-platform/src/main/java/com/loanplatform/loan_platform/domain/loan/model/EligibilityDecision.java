package com.loanplatform.loan_platform.domain.loan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityDecision {
    private boolean eligible;
    private String reasonCode;
    private String riskCategory;
    private Integer creditScore;
    private BigDecimal dtiRatio;
    private BigDecimal monthlyIncome;
    private String employmentType;
    private BigDecimal maxApprovedAmount;
}
