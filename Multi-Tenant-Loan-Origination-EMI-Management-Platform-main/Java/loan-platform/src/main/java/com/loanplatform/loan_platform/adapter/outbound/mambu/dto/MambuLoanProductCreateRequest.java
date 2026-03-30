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
public class MambuLoanProductCreateRequest {
    private String name;
    private String id;
    private String type;
    private LoanAmountSettings loanAmountSettings;
    private ScheduleSettings scheduleSettings;
    private InterestSettings interestSettings;
    private Currency currency;
    private String forBranchKey;
    private String state;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanAmountSettings {
        private AmountValue defaultAmount;
        private AmountValue minAmount;
        private AmountValue maxAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleSettings {
        private Integer defaultRepaymentPeriodCount;
        private String defaultRepaymentPeriodUnit;
        private String repaymentScheduleMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestSettings {
        private AmountValue defaultInterestRate;
        private String interestChargeFrequency;
        private String interestCalculationMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValue {
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Currency {
        private String code;
    }
}
