package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.service.EmiCalculatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Simulation", description = "Mambu Loan Simulation API Mock")
public class MambuSimulationController {

    private final EmiCalculatorService emiCalculator;

    /**
     * Mambu uses POST /api/v2/loans:simulate (colon notation, not /simulate)
     * Spring handles this via @PostMapping with the literal colon in the path
     */
    @PostMapping("/loans:simulate")
    @Operation(summary = "Simulate a Loan — calculate EMI, schedule, total interest")
    public ResponseEntity<Map<String, Object>> simulateLoan(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/loans:simulate");

        Map<?, ?> amtMap = (Map<?, ?>) request.getOrDefault("loanAmount", Map.of());
        Map<?, ?> rateMap = (Map<?, ?>) request.getOrDefault("interestRate", Map.of());
        Map<?, ?> disbDetails = (Map<?, ?>) request.getOrDefault("disbursementDetails", Map.of());

        BigDecimal principal = parseBigDecimal(amtMap.get("value"), "100000");
        BigDecimal annualRate = parseBigDecimal(rateMap.get("value"), "14.5");
        int tenureMonths = parseInteger(request.get("repaymentInstallments"), 12);

        String disbDateStr = (String) disbDetails.get("expectedDisbursementDate");
        LocalDate disbDate = disbDateStr != null ? LocalDate.parse(disbDateStr) : LocalDate.now();
        LocalDate firstRepaymentDate = disbDate.plusMonths(1).withDayOfMonth(1);

        BigDecimal emi = emiCalculator.calculateEmi(principal, annualRate, tenureMonths);
        BigDecimal totalInterest = emiCalculator.calculateTotalInterest(principal, annualRate, tenureMonths);
        BigDecimal totalPayable = principal.add(totalInterest).setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualPercentageRate = annualRate;

        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                emiCalculator.generateSchedule(principal, annualRate, tenureMonths, firstRepaymentDate);

        // Build installments in Mambu response format
        List<Map<String, Object>> installments = new ArrayList<>();
        for (EmiCalculatorService.InstallmentBreakdown inst : schedule) {
            Map<String, Object> installment = new LinkedHashMap<>();
            installment.put("number", inst.number());
            installment.put("dueDate", inst.dueDate().toString());
            installment.put("principal", Map.of(
                    "amount", Map.of("value", inst.principalAmount()),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("interest", Map.of(
                    "amount", Map.of("value", inst.interestAmount()),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("fee", Map.of(
                    "amount", Map.of("value", BigDecimal.ZERO),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("penalty", Map.of(
                    "amount", Map.of("value", BigDecimal.ZERO),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("totalDue", Map.of("value", inst.totalDue()));
            installments.add(installment);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loanAmount", Map.of("value", principal));
        response.put("repaymentInstallments", tenureMonths);
        response.put("interestRate", Map.of("value", annualRate));
        response.put("annualPercentageRate", annualPercentageRate);
        response.put("periodicPayment", emi);
        response.put("totalInterestCharged", totalInterest);
        response.put("totalAmountRepaid", totalPayable);
        response.put("repaymentSchedule", Map.of("installments", installments));

        log.info("Mambu Mock — Simulation complete: principal={}, tenure={}, emi={}", principal, tenureMonths, emi);
        return ResponseEntity.ok(response);
    }

    private BigDecimal parseBigDecimal(Object val, String def) {
        if (val == null) return new BigDecimal(def);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    private int parseInteger(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
