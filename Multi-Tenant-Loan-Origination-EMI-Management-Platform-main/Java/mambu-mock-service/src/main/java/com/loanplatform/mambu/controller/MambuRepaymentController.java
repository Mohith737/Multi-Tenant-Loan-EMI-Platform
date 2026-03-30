package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuInstallment;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.model.repayment.MambuTransaction;
import com.loanplatform.mambu.repository.MambuInstallmentRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuTransactionRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Repayments", description = "Mambu Repayment & Schedule API Mock")
public class MambuRepaymentController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuInstallmentRepository installmentRepository;
    private final MambuTransactionRepository transactionRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping("/{loanId}/repayments")
    @Operation(summary = "Post a repayment to a Mambu Loan")
    public ResponseEntity<Map<String, Object>> postRepayment(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/repayments", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        String externalId = (String) request.get("externalId");

        // Idempotency: if same externalId exists, return same transaction
        if (externalId != null && transactionRepository.existsByExternalId(externalId)) {
            MambuTransaction existing = transactionRepository.findByExternalId(externalId).get();
            log.warn("Mambu Mock — Duplicate repayment detected externalId={}, returning existing txn", externalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(toTxnResponse(existing));
        }

        if (!"ACTIVE".equals(loan.getAccountState()) && !"NON_PERFORMING".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot post repayment to loan in state: " + loan.getAccountState());
        }

        BigDecimal amount = parseBigDecimal(request.get("amount"), "0");
        String dateStr = (String) request.get("date");
        LocalDate paymentDate = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();

        // Find earliest PENDING installment and mark it paid
        List<MambuInstallment> pending = installmentRepository
                .findByLoanEncodedKeyOrderByInstallmentNumberAsc(loan.getEncodedKey())
                .stream()
                .filter(i -> "PENDING".equals(i.getState()) || "OVERDUE".equals(i.getState()))
                .toList();

        if (!pending.isEmpty()) {
            MambuInstallment installment = pending.get(0);
            installment.setState("PAID");
            installment.setPrincipalPaid(installment.getPrincipalAmount());
            installment.setPrincipalDue(BigDecimal.ZERO);
            installment.setInterestPaid(installment.getInterestAmount());
            installment.setInterestDue(BigDecimal.ZERO);
            installment.setLastPaidDate(paymentDate);
            installmentRepository.save(installment);

            // Update principal balance
            loan.setPrincipalBalance(loan.getPrincipalBalance().subtract(installment.getPrincipalAmount()));
            if (loan.getPrincipalBalance().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setAccountState("CLOSED_OBLIGATIONS_MET");
                loan.setClosedDate(LocalDateTime.now());
            }
            loanRepository.save(loan);
        }

        // Create repayment transaction
        String txnEncodedKey = idGenerator.generateEncodedKey();
        String txnId = idGenerator.generateTransactionId();
        Map<String, Object> channelDetails = asMap(request.get("transactionDetails"));

        MambuTransaction txn = MambuTransaction.builder()
                .encodedKey(txnEncodedKey)
                .transactionId(txnId)
                .type("REPAYMENT")
                .amount(amount)
                .parentAccountKey(loan.getEncodedKey())
                .parentAccountId(loan.getLoanId())
                .externalId(externalId)
                .notes((String) request.get("notes"))
                .channelId((String) channelDetails.getOrDefault("transactionChannelId", "ONLINE_PAYMENT"))
                .valueDate(paymentDate)
                .build();
        transactionRepository.save(txn);

        log.info("Mambu Mock — Repayment posted: loanId={}, amount={}, txnId={}", loanId, amount, txnId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTxnResponse(txn));
    }

    @GetMapping("/{loanId}/schedule")
    @Operation(summary = "Get repayment schedule for a Mambu Loan")
    public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String loanId) {
        log.info("Mambu Mock — GET /api/v2/loans/{}/schedule", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        List<MambuInstallment> installments = installmentRepository
                .findByLoanEncodedKeyOrderByInstallmentNumberAsc(loan.getEncodedKey());

        List<Map<String, Object>> installmentList = new ArrayList<>();
        for (MambuInstallment inst : installments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("number", inst.getInstallmentNumber());
            item.put("dueDate", inst.getDueDate().toString());
            item.put("lastPaidDate", inst.getLastPaidDate() != null ? inst.getLastPaidDate().toString() : null);
            item.put("state", inst.getState());
            item.put("principal", Map.of(
                    "amount", Map.of("value", inst.getPrincipalAmount()),
                    "paid", Map.of("value", inst.getPrincipalPaid()),
                    "due", Map.of("value", inst.getPrincipalDue())
            ));
            item.put("interest", Map.of(
                    "amount", Map.of("value", inst.getInterestAmount()),
                    "paid", Map.of("value", inst.getInterestPaid()),
                    "due", Map.of("value", inst.getInterestDue())
            ));
            item.put("fee", Map.of(
                    "amount", Map.of("value", inst.getFeeAmount()),
                    "paid", Map.of("value", inst.getFeePaid()),
                    "due", Map.of("value", inst.getFeeDue())
            ));
            item.put("penalty", Map.of(
                    "amount", Map.of("value", inst.getPenaltyAmount()),
                    "paid", Map.of("value", inst.getPenaltyPaid()),
                    "due", Map.of("value", inst.getPenaltyDue())
            ));
            installmentList.add(item);
        }

        return ResponseEntity.ok(Map.of("installments", installmentList));
    }

    @GetMapping("/{loanId}/preview-early-repayment")
    @Operation(summary = "Preview early repayment / foreclosure amount")
    public ResponseEntity<Map<String, Object>> previewEarlyRepayment(
            @PathVariable String loanId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String paymentDate) {

        log.info("Mambu Mock — GET /api/v2/loans/{}/preview-early-repayment", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        BigDecimal remaining = loan.getPrincipalBalance();
        BigDecimal accruedInterest = remaining.multiply(loan.getInterestRate())
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal prepaymentPenalty = remaining.multiply(BigDecimal.valueOf(0.02))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalSettlement = remaining.add(accruedInterest).add(prepaymentPenalty);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loanId", loanId);
        response.put("remainingPrincipal", Map.of("value", remaining));
        response.put("accruedInterest", Map.of("value", accruedInterest));
        response.put("prepaymentPenalty", Map.of("value", prepaymentPenalty));
        response.put("feesBalance", Map.of("value", BigDecimal.ZERO));
        response.put("totalSettlementAmount", Map.of("value", totalSettlement));
        response.put("rebate", Map.of("value", BigDecimal.ZERO));
        response.put("earlyRepaymentDate", paymentDate != null ? paymentDate : LocalDate.now().toString());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toTxnResponse(MambuTransaction t) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", t.getEncodedKey());
        res.put("id", t.getTransactionId());
        res.put("type", t.getType());
        res.put("amount", t.getAmount());
        res.put("fees", Map.of("amount", BigDecimal.ZERO));
        res.put("notes", t.getNotes());
        res.put("externalId", t.getExternalId());
        res.put("creationDate", t.getCreationDate() + "+05:30");
        res.put("valueDate", t.getValueDate().toString());
        res.put("parentAccountKey", t.getParentAccountKey());
        res.put("parentAccountId", t.getParentAccountId());
        return res;
    }

    private BigDecimal parseBigDecimal(Object val, String def) {
        if (val == null) return new BigDecimal(def);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object val) {
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
