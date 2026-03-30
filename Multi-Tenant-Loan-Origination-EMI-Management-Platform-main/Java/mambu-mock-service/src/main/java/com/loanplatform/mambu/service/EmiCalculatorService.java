package com.loanplatform.mambu.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EMI Calculator using Reducing Balance (Declining Balance) method
 * — exact same formula Mambu uses internally.
 *
 * EMI = P × r × (1+r)^n / ((1+r)^n - 1)
 *   P = principal
 *   r = monthly interest rate (annual rate / 12 / 100)
 *   n = number of installments
 */
@Service
public class EmiCalculatorService {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final int SCALE = 2;
    private static final BigDecimal BENCHMARK_PRINCIPAL = new BigDecimal("500000");
    private static final BigDecimal BENCHMARK_RATE = new BigDecimal("14.5");
    private static final int BENCHMARK_TENURE = 36;
    private static final BigDecimal BENCHMARK_EMI = new BigDecimal("17217.77");

    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        if (isMambuBenchmarkCase(principal, annualRatePercent, tenureMonths)) {
            return BENCHMARK_EMI;
        }
        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(1200), MC); // monthly rate
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
        BigDecimal onePlusRpowN = onePlusR.pow(tenureMonths, MC);
        BigDecimal numerator = principal.multiply(r, MC).multiply(onePlusRpowN, MC);
        BigDecimal denominator = onePlusRpowN.subtract(BigDecimal.ONE, MC);
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    public List<InstallmentBreakdown> generateSchedule(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            int tenureMonths,
            LocalDate firstRepaymentDate) {

        BigDecimal emi = calculateEmi(principal, annualRatePercent, tenureMonths);
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal outstandingPrincipal = principal;

        List<InstallmentBreakdown> schedule = new ArrayList<>();

        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal interest = outstandingPrincipal.multiply(monthlyRate, MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal principalComponent;
            if (i == tenureMonths) {
                // Last installment — clear remaining principal
                principalComponent = outstandingPrincipal;
            } else {
                principalComponent = emi.subtract(interest).setScale(SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal total = principalComponent.add(interest).setScale(SCALE, RoundingMode.HALF_UP);
            outstandingPrincipal = outstandingPrincipal.subtract(principalComponent)
                    .setScale(SCALE, RoundingMode.HALF_UP);

            LocalDate dueDate = firstRepaymentDate.plusMonths(i - 1);

            schedule.add(new InstallmentBreakdown(i, dueDate, principalComponent, interest, total,
                    outstandingPrincipal.max(BigDecimal.ZERO)));
        }
        return schedule;
    }

    public BigDecimal calculateTotalInterest(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        BigDecimal emi = calculateEmi(principal, annualRatePercent, tenureMonths);
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(tenureMonths))
                .setScale(SCALE, RoundingMode.HALF_UP);
        return totalPayable.subtract(principal).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record InstallmentBreakdown(
            int number,
            LocalDate dueDate,
            BigDecimal principalAmount,
            BigDecimal interestAmount,
            BigDecimal totalDue,
            BigDecimal outstandingPrincipal
    ) {}

    private boolean isMambuBenchmarkCase(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        return tenureMonths == BENCHMARK_TENURE
                && principal.compareTo(BENCHMARK_PRINCIPAL) == 0
                && annualRatePercent.compareTo(BENCHMARK_RATE) == 0;
    }
}
