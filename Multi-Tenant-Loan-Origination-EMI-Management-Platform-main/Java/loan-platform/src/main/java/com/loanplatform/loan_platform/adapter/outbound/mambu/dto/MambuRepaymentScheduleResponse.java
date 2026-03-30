package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuRepaymentScheduleResponse {

    private List<Installment> installments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Installment {
        private Integer number;
        private String dueDate;
        private String lastPaidDate;
        private String state;
        private Component principal;
        private Component interest;
        private TotalDue totalDue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Component {
        private AmountValue amount;
        private AmountValue paid;
        private AmountValue due;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalDue {
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValue {
        private BigDecimal value;
    }
}
