package com.loanplatform.mambu.store;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryMambuStore {

    private final Map<String, BranchRecord> branchesById = new ConcurrentHashMap<>();
    private final Map<String, BranchRecord> branchesByEncodedKey = new ConcurrentHashMap<>();

    private final Map<String, LoanProductRecord> productsById = new ConcurrentHashMap<>();
    private final Map<String, LoanProductRecord> productsByEncodedKey = new ConcurrentHashMap<>();

    public boolean branchIdExists(String branchId) {
        return branchesById.containsKey(branchId);
    }

    public boolean branchEncodedKeyExists(String encodedKey) {
        return branchesByEncodedKey.containsKey(encodedKey);
    }

    public void putBranch(BranchRecord branch) {
        branchesById.put(branch.id(), branch);
        branchesByEncodedKey.put(branch.encodedKey(), branch);
    }

    public Optional<BranchRecord> getBranchById(String branchId) {
        return Optional.ofNullable(branchesById.get(branchId));
    }

    public List<BranchRecord> getAllBranches() {
        return new ArrayList<>(branchesById.values());
    }

    public boolean productIdExists(String productId) {
        return productsById.containsKey(productId);
    }

    public boolean productEncodedKeyExists(String encodedKey) {
        return productsByEncodedKey.containsKey(encodedKey);
    }

    public void putProduct(LoanProductRecord product) {
        productsById.put(product.id(), product);
        productsByEncodedKey.put(product.encodedKey(), product);
    }

    public Optional<LoanProductRecord> getProductById(String productId) {
        return Optional.ofNullable(productsById.get(productId));
    }

    public record BranchRecord(
            String encodedKey,
            String id,
            String name,
            String state,
            String country,
            String city,
            String emailAddress,
            String phoneNumber,
            String notes,
            LocalDateTime creationDate,
            LocalDateTime lastModifiedDate
    ) {
    }

    public record LoanProductRecord(
            String encodedKey,
            String id,
            String name,
            String state,
            String type,
            String forBranchKey,
            String currencyCode,
            java.math.BigDecimal minAmount,
            java.math.BigDecimal maxAmount,
            java.math.BigDecimal defaultAmount,
            java.math.BigDecimal defaultInterestRate,
            String interestCalculationMethod,
            String interestChargeFrequency,
            Integer defaultRepaymentPeriodCount,
            String defaultRepaymentPeriodUnit,
            String repaymentScheduleMethod,
            LocalDateTime creationDate,
            LocalDateTime lastModifiedDate
    ) {
    }
}
