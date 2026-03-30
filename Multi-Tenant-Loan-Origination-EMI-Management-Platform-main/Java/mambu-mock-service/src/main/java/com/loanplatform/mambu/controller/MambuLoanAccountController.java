package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.repository.MambuClientRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Loan Accounts", description = "Mambu Loan Account API Mock")
public class MambuLoanAccountController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuClientRepository clientRepository;
    private final MambuLoanProductRepository productRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a Mambu Loan Account")
    public ResponseEntity<Map<String, Object>> createLoan(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/loans, clientKey={}", request.get("clientKey"));

        String clientKey = (String) request.get("clientKey");
        String productTypeKey = (String) request.get("productTypeKey");

        if (!clientRepository.existsById(clientKey)) {
            throw new MambuResourceNotFoundException("Client not found: " + clientKey);
        }
        if (!productRepository.existsById(productTypeKey)) {
            throw new MambuResourceNotFoundException("Loan product not found: " + productTypeKey);
        }

        Map<?, ?> amtMap = (Map<?, ?>) request.getOrDefault("loanAmount", Map.of());
        Map<?, ?> rateMap = (Map<?, ?>) request.getOrDefault("interestRate", Map.of());
        Map<?, ?> disbDetails = (Map<?, ?>) request.getOrDefault("disbursementDetails", Map.of());

        String encodedKey = idGenerator.generateEncodedKey();
        String loanId = idGenerator.generateLoanId();

        MambuLoanAccount loan = MambuLoanAccount.builder()
                .encodedKey(encodedKey)
                .loanId(loanId)
                .accountState("PENDING_APPROVAL")
                .clientKey(clientKey)
                .productTypeKey(productTypeKey)
                .assignedBranchKey((String) request.getOrDefault("assignedBranchKey", ""))
                .loanAmount(parseBigDecimal(amtMap.get("value"), "0"))
                .interestRate(parseBigDecimal(rateMap.get("value"), "14.5"))
                .repaymentInstallments(parseInteger(request.get("repaymentInstallments"), 12))
                .principalBalance(parseBigDecimal(amtMap.get("value"), "0"))
                .build();

        loanRepository.save(loan);
        log.info("Mambu Mock — Loan account created: encodedKey={}, id={}", encodedKey, loanId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(loan));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get Mambu Loan Account by ID")
    public ResponseEntity<Map<String, Object>> getLoan(@PathVariable String loanId) {
        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));
        return ResponseEntity.ok(toResponse(loan));
    }

    @PostMapping("/{loanId}/approve")
    @Operation(summary = "Approve a Mambu Loan Account")
    public ResponseEntity<Map<String, Object>> approveLoan(
            @PathVariable String loanId,
            @RequestBody(required = false) Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/approve", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        if (!"PENDING_APPROVAL".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot approve loan in state: " + loan.getAccountState() + ". Expected: PENDING_APPROVAL");
        }

        loan.setAccountState("APPROVED");
        loan.setApprovedDate(LocalDateTime.now());
        loanRepository.save(loan);

        log.info("Mambu Mock — Loan approved: {}", loanId);
        return ResponseEntity.ok(toResponse(loan));
    }

    @PatchMapping("/{loanId}")
    @Operation(summary = "Patch Mambu Loan (NPA flagging, state changes)")
    public ResponseEntity<Map<String, Object>> patchLoan(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {
        return updateLoanState(loanId, request, "PATCH");
    }

    @PutMapping("/{loanId}")
    @Operation(summary = "Update Mambu Loan (compatibility alias for PATCH)")
    public ResponseEntity<Map<String, Object>> putLoan(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {
        return updateLoanState(loanId, request, "PUT");
    }

    private ResponseEntity<Map<String, Object>> updateLoanState(
            String loanId,
            Map<String, Object> request,
            String method
    ) {
        log.info("Mambu Mock — {} /api/v2/loans/{}: {}", method, loanId, request);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        if (request.containsKey("loanState")) {
            loan.setAccountState((String) request.get("loanState"));
        }
        if (request.containsKey("notes")) {
            loan.setNotes((String) request.get("notes"));
        }

        loanRepository.save(loan);
        return ResponseEntity.ok(toResponse(loan));
    }

    @GetMapping
    @Operation(summary = "Query Mambu Loan Accounts (portfolio reporting)")
    public ResponseEntity<Map<String, Object>> queryLoans(
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String loanState,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<MambuLoanAccount> loans;
        if (branchId != null && loanState != null) {
            loans = loanRepository.findByAssignedBranchKeyAndAccountState(branchId, loanState);
        } else if (branchId != null) {
            loans = loanRepository.findByAssignedBranchKey(branchId);
        } else {
            loans = loanRepository.findAll();
        }

        int total = loans.size();
        List<MambuLoanAccount> page = loans.stream().skip(offset).limit(limit).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loans", page.stream().map(this::toResponse).toList());
        response.put("pagingDetails", Map.of(
                "totalCount", total,
                "pageSize", limit,
                "pageNum", offset / Math.max(limit, 1)
        ));
        return ResponseEntity.ok(response);
    }

    public Map<String, Object> toResponse(MambuLoanAccount l) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", l.getEncodedKey());
        res.put("id", l.getLoanId());
        res.put("accountState", l.getAccountState());
        res.put("clientKey", l.getClientKey());
        res.put("productTypeKey", l.getProductTypeKey());
        res.put("assignedBranchKey", l.getAssignedBranchKey());
        res.put("loanAmount", Map.of("value", l.getLoanAmount()));
        res.put("interestRate", Map.of("value", l.getInterestRate()));
        res.put("repaymentInstallments", l.getRepaymentInstallments());
        res.put("principalBalance", Map.of("value", l.getPrincipalBalance()));
        res.put("interestBalance", Map.of("value", l.getInterestBalance()));
        res.put("feesBalance", Map.of("value", l.getFeesBalance()));
        res.put("penaltyBalance", Map.of("value", l.getPenaltyBalance()));
        res.put("disbursementDate", l.getDisbursementDate());
        res.put("firstRepaymentDate", l.getFirstRepaymentDate());
        res.put("approvedDate", l.getApprovedDate() != null ? l.getApprovedDate() + "+05:30" : null);
        res.put("closedDate", l.getClosedDate() != null ? l.getClosedDate() + "+05:30" : null);
        res.put("externalId", l.getExternalId());
        res.put("creationDate", l.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", l.getLastModifiedDate() + "+05:30");
        return res;
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
