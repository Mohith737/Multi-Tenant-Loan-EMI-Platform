package com.loanplatform.loan_platform.dto.response;

import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentType;
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
public class PrepaymentSimulationResponse {

    private UUID quoteId;
    private UUID applicationId;
    private String loanAccountId;
    private PrepaymentType prepaymentType;
    private BigDecimal principalOutstanding;
    private BigDecimal accruedInterest;
    private BigDecimal prepaymentPenalty;
    private BigDecimal totalPayable;
    private Instant quoteGeneratedAt;
    private Instant quoteExpiresAt;
    private List<OptionPreview> options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionPreview {
        private PrepaymentOption option;
        private BigDecimal revisedOutstanding;
        private BigDecimal estimatedEmi;
        private Integer estimatedTenureMonths;
        private String note;
    }
}
