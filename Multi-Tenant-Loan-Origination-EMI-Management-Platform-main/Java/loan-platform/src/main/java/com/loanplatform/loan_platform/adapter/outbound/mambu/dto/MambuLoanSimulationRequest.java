package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanSimulationRequest {
    private AmountValue loanAmount;
    private AmountValue interestRate;
    private Integer repaymentInstallments;
    private String loanProductTypeKey;
    private String clientKey;
    private DisbursementDetails disbursementDetails;
    private String repaymentScheduleMethod;
    private String interestCalculationMethod;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValue {
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisbursementDetails {
        private String expectedDisbursementDate;
    }
}
