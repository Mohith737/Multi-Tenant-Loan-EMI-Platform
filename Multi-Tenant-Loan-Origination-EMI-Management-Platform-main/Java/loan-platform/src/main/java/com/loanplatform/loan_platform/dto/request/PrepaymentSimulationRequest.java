package com.loanplatform.loan_platform.dto.request;

import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepaymentSimulationRequest {

    @NotNull(message = "prepaymentType is required")
    private PrepaymentType prepaymentType;

    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    private PrepaymentOption prepaymentOption;

    private LocalDate requestedDate;
}
