package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {

    private UUID applicationId;
    private UUID borrowerId;
    private List<PaymentItem> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentItem {
        private UUID installmentId;
        private Integer installmentNumber;
        private BigDecimal paidAmount;
        private LocalDate paidDate;
        private String stripePaymentIntentId;
        private String stripeStatus;
        private String mambuTransactionId;
        private String mambuTransactionKey;
        private String status;
        private Instant attemptedAt;
    }
}
