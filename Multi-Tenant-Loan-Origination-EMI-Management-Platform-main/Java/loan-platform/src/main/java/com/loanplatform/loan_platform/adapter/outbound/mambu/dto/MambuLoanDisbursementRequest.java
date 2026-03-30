package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanDisbursementRequest {

    private String notes;
    private String firstRepaymentDate;
    private String disbursementDate;
    private String externalId;
    private TransactionDetails transactionDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetails {
        private String transactionChannelId;
    }
}
