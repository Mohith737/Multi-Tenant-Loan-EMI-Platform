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
public class MambuLoanDisbursementResponse {

    private String encodedKey;
    private String id;
    private String type;
    private BigDecimal amount;
    private Fee fees;
    private String notes;
    private String externalId;
    private String creationDate;
    private String valueDate;
    private String parentAccountKey;
    private String parentAccountId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fee {
        private BigDecimal amount;
    }
}
