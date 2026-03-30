package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanCreateRequest {

    private String clientKey;
    private String productTypeKey;
    private AmountValue loanAmount;
    private AmountValue interestRate;
    private Integer repaymentInstallments;
    private String assignedBranchKey;
    private DisbursementDetails disbursementDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValue {
        private Object value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisbursementDetails {
        private String expectedDisbursementDate;
    }
}
