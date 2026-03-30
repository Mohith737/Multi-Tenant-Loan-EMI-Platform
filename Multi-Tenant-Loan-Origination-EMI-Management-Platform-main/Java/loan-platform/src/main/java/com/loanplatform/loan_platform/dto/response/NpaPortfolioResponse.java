package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpaPortfolioResponse {

    private List<NpaLoanItem> npaloans;
    private Long total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NpaLoanItem {

        private String loanAccountId;
        private String borrowerName;
        private BigDecimal outstandingAmount;
        private Integer daysPassedDue;
        private Instant npaFlaggedAt;
        private String recoveryStage;
        private String mambuState;
    }
}
