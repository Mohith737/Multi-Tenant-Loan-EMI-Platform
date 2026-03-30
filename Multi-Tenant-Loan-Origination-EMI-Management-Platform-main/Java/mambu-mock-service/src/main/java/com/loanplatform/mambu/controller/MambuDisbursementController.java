package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuInstallment;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.model.repayment.MambuTransaction;
import com.loanplatform.mambu.repository.MambuInstallmentRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.repository.MambuTransactionRepository;
import com.loanplatform.mambu.service.EmiCalculatorService;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Tag(name = "Mambu Disbursement", description = "Mambu Disbursement API Mock")
public class MambuDisbursementController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuLoanProductRepository productRepository;
    private final MambuInstallmentRepository installmentRepository;
    private final MambuTransactionRepository transactionRepository;
    private final EmiCalculatorService emiCalculator;
    private final MambuIdGenerator idGenerator;

    @PostMapping("/{loanId}/disbursement")
    @Operation(summary = "Disburse a Mambu Loan and generate repayment schedule")
    public ResponseEntity<Map<String, Object>> disburseLoan(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/disbursement", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        String externalId = (String) request.get("externalId");
        if (externalId != null && transactionRepository.existsByExternalId(externalId)) {
            MambuTransaction existing = transactionRepository.findByExternalId(externalId).orElse(null);
            if (existing != null
                    && "DISBURSEMENT".equals(existing.getType())
                    && loanId.equals(existing.getParentAccountId())) {
                log.warn("Mambu Mock — Duplicate disbursement detected externalId={}, returning existing txn", externalId);
                return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(existing, loan, BigDecimal.ZERO));
            }
        }

        if (!"APPROVED".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot disburse loan in state: " + loan.getAccountState() + ". Expected: APPROVED");
        }

        String disbDateStr = (String) request.get("disbursementDate");
        LocalDate disbDate = disbDateStr != null ? LocalDate.parse(disbDateStr) : LocalDate.now();

        String firstRepayStr = (String) request.get("firstRepaymentDate");
        LocalDate firstRepayment = firstRepayStr != null
                ? LocalDate.parse(firstRepayStr)
                : disbDate.plusMonths(1).withDayOfMonth(1);

        // Fetch product config for processing fee
        var product = productRepository.findById(loan.getProductTypeKey()).orElse(null);
        BigDecimal feePercent = product != null ? product.getProcessingFeePercent() : BigDecimal.ZERO;
        BigDecimal processingFee = loan.getLoanAmount()
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netDisbursed = loan.getLoanAmount().subtract(processingFee);

        // Update loan state
        loan.setAccountState("ACTIVE");
        loan.setDisbursementDate(disbDate);
        loan.setFirstRepaymentDate(firstRepayment);
        loan.setLastRepaymentDate(firstRepayment.plusMonths(loan.getRepaymentInstallments() - 1));
        loan.setPrincipalBalance(loan.getLoanAmount());
        loan.setExternalId((String) request.get("externalId"));
        loanRepository.save(loan);

        // Generate installment schedule
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                emiCalculator.generateSchedule(loan.getLoanAmount(), loan.getInterestRate(),
                        loan.getRepaymentInstallments(), firstRepayment);

        List<MambuInstallment> installments = new ArrayList<>();
        for (EmiCalculatorService.InstallmentBreakdown b : schedule) {
            installments.add(MambuInstallment.builder()
                    .loanEncodedKey(loan.getEncodedKey())
                    .installmentNumber(b.number())
                    .dueDate(b.dueDate())
                    .state("PENDING")
                    .principalAmount(b.principalAmount())
                    .principalDue(b.principalAmount())
                    .interestAmount(b.interestAmount())
                    .interestDue(b.interestAmount())
                    .totalDue(b.totalDue())
                    .build());
        }
        installmentRepository.saveAll(installments);

        // Create disbursement transaction
        String txnId = idGenerator.generateTransactionId();
        String txnEncodedKey = idGenerator.generateEncodedKey();
        MambuTransaction txn = MambuTransaction.builder()
                .encodedKey(txnEncodedKey)
                .transactionId(txnId)
                .type("DISBURSEMENT")
                .amount(netDisbursed)
                .parentAccountKey(loan.getEncodedKey())
                .parentAccountId(loan.getLoanId())
                .externalId((String) request.get("externalId"))
                .notes((String) request.getOrDefault("notes", "Loan disbursed"))
                .channelId("BANK_TRANSFER")
                .valueDate(disbDate)
                .build();
        transactionRepository.save(txn);

        log.info("Mambu Mock — Loan disbursed: loanId={}, amount={}, txnId={}", loanId, netDisbursed, txnId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(txn, loan, processingFee));
    }

    private Map<String, Object> toTransactionResponse(MambuTransaction txn, MambuLoanAccount loan, BigDecimal processingFee) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("encodedKey", txn.getEncodedKey());
        response.put("id", txn.getTransactionId());
        response.put("type", txn.getType());
        response.put("amount", txn.getAmount());
        response.put("fees", Map.of("amount", processingFee));
        response.put("notes", txn.getNotes());
        response.put("externalId", txn.getExternalId());
        response.put("creationDate", txn.getCreationDate() + "+05:30");
        response.put("valueDate", txn.getValueDate().toString());
        response.put("parentAccountKey", loan.getEncodedKey());
        response.put("parentAccountId", loan.getLoanId());
        return response;
    }
}
