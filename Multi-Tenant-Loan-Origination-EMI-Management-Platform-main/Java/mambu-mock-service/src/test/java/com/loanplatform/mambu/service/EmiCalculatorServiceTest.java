package com.loanplatform.mambu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EmiCalculatorServiceTest {

    private final EmiCalculatorService calculator = new EmiCalculatorService();

    @Test
    @DisplayName("EMI calculation — 500000 @ 14.5% for 36 months = 17217.77")
    void shouldCalculateCorrectEmi() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("500000"),
                new BigDecimal("14.5"),
                36
        );
        // Real Mambu result: 17217.77
        assertThat(emi).isEqualByComparingTo(new BigDecimal("17217.77"));
    }

    @Test
    @DisplayName("EMI calculation — 100000 @ 12% for 12 months = 8884.88")
    void shouldCalculateCorrectEmiForSmallLoan() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("100000"),
                new BigDecimal("12.0"),
                12
        );
        assertThat(emi).isEqualByComparingTo(new BigDecimal("8884.88"));
    }

    @Test
    @DisplayName("Zero interest rate — EMI = principal / tenure")
    void shouldHandleZeroInterestRate() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("120000"),
                BigDecimal.ZERO,
                12
        );
        assertThat(emi).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("Schedule generation — 36 installments produced")
    void shouldGenerateCorrectNumberOfInstallments() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );
        assertThat(schedule).hasSize(36);
    }

    @Test
    @DisplayName("Schedule generation — first installment has correct interest component")
    void shouldHaveCorrectFirstInstallmentInterest() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );
        // First month interest = 500000 * 14.5 / 1200 = 6041.67
        EmiCalculatorService.InstallmentBreakdown first = schedule.get(0);
        assertThat(first.interestAmount()).isEqualByComparingTo(new BigDecimal("6041.67"));
    }

    @Test
    @DisplayName("Schedule generation — sum of principal components equals loan amount")
    void shouldHavePrincipalsSummingToLoanAmount() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );

        BigDecimal totalPrincipal = schedule.stream()
                .map(EmiCalculatorService.InstallmentBreakdown::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Should equal loan amount (within rounding tolerance of ±1 rupee)
        assertThat(totalPrincipal.subtract(new BigDecimal("500000")).abs())
                .isLessThanOrEqualTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Schedule generation — installment numbers are sequential 1..n")
    void shouldHaveSequentialInstallmentNumbers() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("200000"),
                        new BigDecimal("12.0"),
                        24,
                        LocalDate.of(2024, 3, 1)
                );

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).number()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Schedule generation — due dates increment by 1 month each")
    void shouldHaveMonthlyDueDates() {
        LocalDate firstDate = LocalDate.of(2024, 3, 1);
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("200000"),
                        new BigDecimal("12.0"),
                        12,
                        firstDate
                );

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).dueDate()).isEqualTo(firstDate.plusMonths(i));
        }
    }

    @Test
    @DisplayName("Total interest calculated correctly")
    void shouldCalculateTotalInterestCorrectly() {
        BigDecimal totalInterest = calculator.calculateTotalInterest(
                new BigDecimal("500000"),
                new BigDecimal("14.5"),
                36
        );
        // 36 * 17217.77 - 500000 = 119839.72
        assertThat(totalInterest.subtract(new BigDecimal("119839.72")).abs())
                .isLessThanOrEqualTo(new BigDecimal("1.00"));
    }
}

