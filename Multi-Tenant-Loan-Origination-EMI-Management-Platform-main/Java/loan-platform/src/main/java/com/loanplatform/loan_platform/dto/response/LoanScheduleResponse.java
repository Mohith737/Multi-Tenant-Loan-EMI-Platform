package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanScheduleResponse {

    private UUID applicationId;
    private String mambuLoanId;
    private Integer totalInstallments;
    private LocalDate firstDueDate;
    private LocalDate lastDueDate;
    private List<InstallmentItem> installments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentItem {
        private Integer installmentNumber;
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal totalDue;
        private String status;
        private String mambuInstallmentState;
    }
}
