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
public class BorrowerInstallmentScheduleResponse {

    private UUID applicationId;
    private UUID borrowerId;
    private String mambuLoanId;
    private Integer totalInstallments;
    private List<InstallmentItem> installments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentItem {
        private UUID installmentId;
        private Integer installmentNumber;
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal totalDue;
        private String paymentStatus;
        private String stripePaymentStatus;
        private String stripePaymentIntentId;
        private LocalDate paidDate;
        private Integer retryCount;
    }
}
