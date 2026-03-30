package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.config.MambuMockConfig;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loanproduct.MambuLoanProduct;
import com.loanplatform.mambu.repository.MambuBranchRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/loanproducts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Loan Products", description = "Mambu Loan Product API Mock")
public class MambuLoanProductController {

    private final MambuLoanProductRepository productRepository;
    private final MambuBranchRepository branchRepository;
    private final MambuIdGenerator idGenerator;
    private final MambuMockConfig mockConfig;

    @PostMapping
    @Operation(summary = "Create a Loan Product")
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> request) {
        delay();

        String productId = (String) request.getOrDefault(
                "id",
                "LP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase()
        );

        if (productRepository.existsByProductId(productId)) {
            throw new IllegalArgumentException("Loan product with id '" + productId + "' already exists");
        }

        String forBranchKey = (String) request.get("forBranchKey");
        if (!branchRepository.existsById(forBranchKey)) {
            throw new MambuResourceNotFoundException("Branch not found with encodedKey: " + forBranchKey);
        }

        Map<String, Object> amount = asMap(request.get("loanAmountSettings"));
        Map<String, Object> minAmount = asMap(amount.get("minAmount"));
        Map<String, Object> maxAmount = asMap(amount.get("maxAmount"));
        Map<String, Object> defaultAmount = asMap(amount.get("defaultAmount"));

        Map<String, Object> interest = asMap(request.get("interestSettings"));
        Map<String, Object> defaultInterestRate = asMap(interest.get("defaultInterestRate"));

        Map<String, Object> schedule = asMap(request.get("scheduleSettings"));
        Map<String, Object> currency = asMap(request.get("currency"));

        MambuLoanProduct product = MambuLoanProduct.builder()
                .encodedKey(idGenerator.generateEncodedKey())
                .productId(productId)
                .name((String) request.get("name"))
                .state((String) request.getOrDefault("state", "ACTIVE"))
                .type((String) request.getOrDefault("type", "FIXED_TERM_LOAN"))
                .forBranchKey(forBranchKey)
                .currencyCode((String) currency.getOrDefault("code", "INR"))
                .minLoanAmount(toBigDecimal(minAmount.get("value"), new BigDecimal("10000")))
                .maxLoanAmount(toBigDecimal(maxAmount.get("value"), new BigDecimal("1000000")))
                .defaultLoanAmount(toBigDecimal(defaultAmount.get("value"), new BigDecimal("100000")))
                .defaultInterestRate(toBigDecimal(defaultInterestRate.get("value"), new BigDecimal("14.5")))
                .interestCalculationMethod((String) interest.getOrDefault("interestCalculationMethod", "DECLINING_BALANCE"))
                .interestChargeFrequency((String) interest.getOrDefault("interestChargeFrequency", "ANNUALIZED"))
                .defaultRepaymentPeriodCount(toInteger(schedule.get("defaultRepaymentPeriodCount"), 12))
                .defaultRepaymentPeriodUnit((String) schedule.getOrDefault("defaultRepaymentPeriodUnit", "MONTHS"))
                .repaymentScheduleMethod((String) schedule.getOrDefault("repaymentScheduleMethod", "STANDARD"))
                .build();

        MambuLoanProduct saved = productRepository.save(product);
        log.info("Mambu Mock — Loan Product created: encodedKey={}, id={}", saved.getEncodedKey(), saved.getProductId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get Loan Product by ID")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String productId) {
        MambuLoanProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan product not found: " + productId));
        return ResponseEntity.ok(toResponse(product));
    }

    private Map<String, Object> toResponse(MambuLoanProduct p) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("encodedKey", p.getEncodedKey());
        response.put("id", p.getProductId());
        response.put("name", p.getName());
        response.put("state", p.getState());
        response.put("type", p.getType());
        response.put("creationDate", p.getCreationDate() + "+05:30");
        response.put("lastModifiedDate", p.getLastModifiedDate() + "+05:30");
        response.put("currency", Map.of("code", p.getCurrencyCode(), "name", "Indian Rupee"));
        response.put("loanAmountSettings", Map.of(
                "minAmount", Map.of("value", p.getMinLoanAmount()),
                "maxAmount", Map.of("value", p.getMaxLoanAmount()),
                "defaultAmount", Map.of("value", p.getDefaultLoanAmount())
        ));
        response.put("interestSettings", Map.of(
                "defaultInterestRate", Map.of("value", p.getDefaultInterestRate()),
                "interestCalculationMethod", p.getInterestCalculationMethod(),
                "interestChargeFrequency", p.getInterestChargeFrequency()
        ));
        response.put("scheduleSettings", Map.of(
                "defaultRepaymentPeriodCount", p.getDefaultRepaymentPeriodCount(),
                "defaultRepaymentPeriodUnit", p.getDefaultRepaymentPeriodUnit()
        ));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private Integer toInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private void delay() {
        try {
            Thread.sleep(Math.max(mockConfig.getSimulateDelayMs(), 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
