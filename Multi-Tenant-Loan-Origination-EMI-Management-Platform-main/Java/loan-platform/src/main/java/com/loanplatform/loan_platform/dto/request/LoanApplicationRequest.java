package com.loanplatform.loan_platform.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
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
public class LoanApplicationRequest {

    private UUID borrowerId;

    @NotBlank
    @Size(max = 100)
    private String loanProductKey;

    @NotNull
    @DecimalMin(value = "0.01", message = "requestedAmount must be greater than zero")
    private BigDecimal requestedAmount;

    @NotNull
    @Min(value = 1, message = "requestedTenureMonths must be at least 1")
    private Integer requestedTenureMonths;

    @NotBlank
    @Size(max = 120)
    private String loanPurpose;

    @FutureOrPresent(message = "requestedDisbursementDate must be today or a future date")
    private LocalDate requestedDisbursementDate;
}
