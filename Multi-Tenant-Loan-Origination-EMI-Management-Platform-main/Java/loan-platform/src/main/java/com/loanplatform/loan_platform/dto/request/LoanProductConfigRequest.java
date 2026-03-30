package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loan product configuration payload")
public class LoanProductConfigRequest {

    @NotBlank
    @Schema(example = "Personal Loan - ABC")
    private String productName;

    @NotNull
    @DecimalMin("0.0")
    @Schema(example = "10000")
    private BigDecimal minLoanAmount;

    @NotNull
    @DecimalMin("0.0")
    @Schema(example = "1000000")
    private BigDecimal maxLoanAmount;

    @NotNull
    @Min(1)
    @Schema(example = "6")
    private Integer minTenureMonths;

    @NotNull
    @Min(1)
    @Schema(example = "60")
    private Integer maxTenureMonths;

    @NotNull
    @DecimalMin("0.0")
    @Schema(example = "14.5")
    private BigDecimal annualInterestRate;

    @NotNull
    @DecimalMin("0.0")
    @Schema(example = "1.5")
    private BigDecimal processingFeePercent;

    @NotNull
    @DecimalMin("0.0")
    @Schema(example = "2.0")
    private BigDecimal prepaymentPenaltyPercent;

    @NotBlank
    @Schema(example = "INR")
    private String currency;
}
