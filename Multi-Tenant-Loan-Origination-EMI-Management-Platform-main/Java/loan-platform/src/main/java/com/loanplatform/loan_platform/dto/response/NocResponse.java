package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NocResponse {

    private UUID applicationId;
    private String loanAccountId;
    private String borrowerName;
    private BigDecimal foreclosureAmount;
    private Instant foreclosedAt;
    private String accountState;
    private String certificateNumber;
    private Instant generatedAt;
}
