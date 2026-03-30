package com.loanplatform.loan_platform.dto.response;

import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentStatus;
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
public class PrepaymentAdminViewResponse {

    private int page;
    private int size;
    private long total;
    private List<Item> requests;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID requestId;
        private UUID applicationId;
        private String loanAccountId;
        private UUID borrowerId;
        private PrepaymentType prepaymentType;
        private PrepaymentOption prepaymentOption;
        private BigDecimal requestedAmount;
        private PrepaymentStatus status;
        private String stripeStatus;
        private String mambuTransactionId;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
