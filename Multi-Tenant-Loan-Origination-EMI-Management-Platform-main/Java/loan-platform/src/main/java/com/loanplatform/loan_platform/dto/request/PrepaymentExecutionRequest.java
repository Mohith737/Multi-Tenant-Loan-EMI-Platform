package com.loanplatform.loan_platform.dto.request;

import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepaymentExecutionRequest {

    @NotNull(message = "prepaymentType is required")
    private PrepaymentType prepaymentType;

    private PrepaymentOption prepaymentOption;

    @NotNull(message = "requestedAmount is required")
    @DecimalMin(value = "0.01", message = "requestedAmount must be greater than zero")
    private BigDecimal requestedAmount;

    @NotNull(message = "quoteId is required")
    private UUID quoteId;

    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 180, message = "idempotencyKey must not exceed 180 characters")
    private String idempotencyKey;

    private LocalDate paymentDate;
}
