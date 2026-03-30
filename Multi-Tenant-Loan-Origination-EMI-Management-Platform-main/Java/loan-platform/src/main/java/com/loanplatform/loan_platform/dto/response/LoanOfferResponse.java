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
public class LoanOfferResponse {
    private UUID offerId;
    private Integer tenureMonths;
    private BigDecimal requestedAmount;
    private BigDecimal emiAmount;
    private BigDecimal totalInterest;
    private BigDecimal effectiveAnnualRate;
    private BigDecimal processingFee;
    private BigDecimal totalPayable;
    private String status;
    private Instant expiresAt;
}
