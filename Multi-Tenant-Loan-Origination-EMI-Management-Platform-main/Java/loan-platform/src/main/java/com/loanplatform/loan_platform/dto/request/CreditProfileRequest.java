package com.loanplatform.loan_platform.dto.request;

import com.loanplatform.loan_platform.domain.borrower.model.CreditBureau;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditProfileRequest {

    @NotNull
    @Min(300)
    @Max(900)
    private Integer creditScore;

    @NotNull
    private CreditBureau creditBureau;

    @NotNull
    @DecimalMin(value = "0.01", message = "monthlyIncome must be greater than zero")
    private BigDecimal monthlyIncome;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal existingEmiObligations;

    @NotBlank
    @Size(max = 40)
    private String employmentType;

    @Size(max = 180)
    private String employerName;

    @NotNull
    @Min(0)
    private Integer employmentMonths;
}
