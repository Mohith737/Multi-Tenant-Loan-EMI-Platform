package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuEarlyRepaymentPreviewResponse {

    private String loanId;
    private AmountValue remainingPrincipal;
    private AmountValue accruedInterest;
    private AmountValue prepaymentPenalty;
    private AmountValue feesBalance;
    private AmountValue totalSettlementAmount;
    private AmountValue rebate;
    private String earlyRepaymentDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValue {
        private java.math.BigDecimal value;
    }
}
