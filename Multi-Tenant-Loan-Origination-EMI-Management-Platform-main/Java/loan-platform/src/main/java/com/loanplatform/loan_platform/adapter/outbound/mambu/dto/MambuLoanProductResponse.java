package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanProductResponse {
    private String encodedKey;
    private String id;
    private String name;
    private String state;
    private String type;
    private String creationDate;
    private String lastModifiedDate;
    private Object currency;
    private Object loanAmountSettings;
    private Object interestSettings;
    private Object scheduleSettings;
}
