package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanSimulationResponse {
    private MambuLoanSimulationRequest.AmountValue loanAmount;
    private Integer repaymentInstallments;
    private MambuLoanSimulationRequest.AmountValue interestRate;
    private BigDecimal annualPercentageRate;
    private BigDecimal periodicPayment;
    private BigDecimal totalInterestCharged;
    private BigDecimal totalAmountRepaid;
}
