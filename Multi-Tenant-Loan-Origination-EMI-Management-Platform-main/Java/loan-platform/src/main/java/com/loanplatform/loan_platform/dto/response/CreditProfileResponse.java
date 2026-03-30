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
public class CreditProfileResponse {
    private UUID creditProfileId;
    private UUID borrowerId;
    private Integer creditScore;
    private String creditBureau;
    private BigDecimal dtiRatio;
    private String eligibilityCategory;
    private Instant createdAt;
    private Instant updatedAt;
}
