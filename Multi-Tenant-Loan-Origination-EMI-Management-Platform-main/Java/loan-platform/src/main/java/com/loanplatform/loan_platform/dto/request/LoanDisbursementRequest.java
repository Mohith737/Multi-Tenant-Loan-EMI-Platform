package com.loanplatform.loan_platform.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDisbursementRequest {

    private LocalDate disbursementDate;

    private LocalDate firstRepaymentDate;

    @Size(max = 500, message = "notes must not exceed 500 characters")
    private String notes;

    @Size(max = 120, message = "stripeDestinationAccountId must not exceed 120 characters")
    private String stripeDestinationAccountId;
}
