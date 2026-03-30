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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepaymentExecutionResponse {

    private UUID requestId;
    private UUID applicationId;
    private String loanAccountId;
    private PrepaymentType prepaymentType;
    private PrepaymentOption prepaymentOption;
    private BigDecimal requestedAmount;
    private String stripePaymentIntentId;
    private String stripeStatus;
    private String mambuTransactionId;
    private PrepaymentStatus status;
    private String loanState;
    private boolean nocAvailable;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;
}
