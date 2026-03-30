package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {
    private UUID applicationId;
    private UUID borrowerId;
    private String status;
    private String loanState;
    private EligibilityResult eligibilityResult;
    private List<LoanOfferResponse> offers;
    private Instant submittedAt;
    private Instant offersExpiresAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityResult {
        private boolean eligible;
        private String riskCategory;
        private BigDecimal maxApprovedAmount;
    }
}
