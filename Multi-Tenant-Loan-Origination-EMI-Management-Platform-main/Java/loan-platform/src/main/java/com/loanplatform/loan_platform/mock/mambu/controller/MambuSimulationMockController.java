package com.loanplatform.loan_platform.mock.mambu.controller;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "Mambu Mock - Simulation", description = "Mock Mambu simulation API")
public class MambuSimulationMockController {

    @PostMapping("/loans:simulate")
    @Operation(summary = "Simulate loan", description = "Mock Mambu endpoint for EMI and interest simulation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Simulation completed"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<MambuLoanSimulationResponse> simulateLoan(
            @RequestBody MambuLoanSimulationRequest request
    ) throws InterruptedException {
        Thread.sleep(200);

        BigDecimal principal = request.getLoanAmount() == null || request.getLoanAmount().getValue() == null
                ? BigDecimal.ZERO
                : request.getLoanAmount().getValue();
        BigDecimal annualRate = request.getInterestRate() == null || request.getInterestRate().getValue() == null
                ? BigDecimal.ZERO
                : request.getInterestRate().getValue();
        int tenureMonths = request.getRepaymentInstallments() == null ? 0 : request.getRepaymentInstallments();

        BigDecimal emi = calculateEmi(principal, annualRate, tenureMonths);
        BigDecimal totalAmountRepaid = emi.multiply(BigDecimal.valueOf(tenureMonths)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = totalAmountRepaid.subtract(principal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        MambuLoanSimulationResponse response = MambuLoanSimulationResponse.builder()
                .loanAmount(new MambuLoanSimulationRequest.AmountValue(principal))
                .interestRate(new MambuLoanSimulationRequest.AmountValue(annualRate))
                .repaymentInstallments(tenureMonths)
                .annualPercentageRate(annualRate.setScale(4, RoundingMode.HALF_UP))
                .periodicPayment(emi)
                .totalInterestCharged(totalInterest)
                .totalAmountRepaid(totalAmountRepaid)
                .build();

        return ResponseEntity.ok(response);
    }

    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0 || tenureMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate = annualRatePercent
                .divide(BigDecimal.valueOf(12 * 100), 12, RoundingMode.HALF_UP);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        MathContext mathContext = new MathContext(16, RoundingMode.HALF_UP);
        BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyRate).pow(tenureMonths, mathContext);
        BigDecimal numerator = principal.multiply(monthlyRate, mathContext).multiply(onePlusRPowerN, mathContext);
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE, mathContext);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
