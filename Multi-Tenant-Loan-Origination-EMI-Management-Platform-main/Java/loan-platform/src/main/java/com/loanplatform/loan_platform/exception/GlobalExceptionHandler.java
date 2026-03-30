package com.loanplatform.loan_platform.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotFound(TenantNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BorrowerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBorrowerNotFound(BorrowerNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "BORROWER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(LoanApplicationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleLoanApplicationNotFound(LoanApplicationNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "LOAN_APPLICATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(KycDocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleKycNotFound(KycDocumentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "KYC_DOCUMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePanException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePan(DuplicatePanException ex) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_PAN", ex.getMessage());
    }

    @ExceptionHandler({BorrowerConflictException.class, TenantConflictException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DuplicateActiveLoanException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateActiveLoan(DuplicateActiveLoanException ex) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_ACTIVE_LOAN", ex.getMessage());
    }

    @ExceptionHandler(KycNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleKycNotVerified(KycNotVerifiedException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "KYC_NOT_VERIFIED", ex.getMessage());
    }

    @ExceptionHandler(LoanEligibilityFailedException.class)
    public ResponseEntity<Map<String, Object>> handleLoanEligibilityFailed(LoanEligibilityFailedException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "LOAN_ELIGIBILITY_FAILED", ex.getMessage());
    }

    @ExceptionHandler(LoanOfferExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleLoanOfferExpired(LoanOfferExpiredException ex) {
        return build(HttpStatus.CONFLICT, "LOAN_OFFER_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(PrepaymentQuoteExpiredException.class)
    public ResponseEntity<Map<String, Object>> handlePrepaymentQuoteExpired(PrepaymentQuoteExpiredException ex) {
        return build(HttpStatus.CONFLICT, "PREPAYMENT_QUOTE_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(InvalidPrepaymentOptionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPrepaymentOption(InvalidPrepaymentOptionException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PREPAYMENT_OPTION", ex.getMessage());
    }

    @ExceptionHandler(ForeclosureCalculationException.class)
    public ResponseEntity<Map<String, Object>> handleForeclosureCalculation(ForeclosureCalculationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "FORECLOSURE_CALCULATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(UnderwriterDecisionConflictException.class)
    public ResponseEntity<Map<String, Object>> handleUnderwriterConflict(UnderwriterDecisionConflictException ex) {
        return build(HttpStatus.CONFLICT, "UNDERWRITER_DECISION_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(UnderwriterDecisionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUnderwriterNotFound(UnderwriterDecisionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "UNDERWRITER_DECISION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DisbursementPreconditionFailedException.class)
    public ResponseEntity<Map<String, Object>> handleDisbursementPreconditions(DisbursementPreconditionFailedException ex) {
        Map<String, Object> body = baseBody(HttpStatus.UNPROCESSABLE_ENTITY, "DISBURSEMENT_PRECONDITION_FAILED", ex.getMessage());
        body.put("failedConditions", ex.getFailedConditions());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(DisbursementInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleDisbursementInProgress(DisbursementInProgressException ex) {
        return build(HttpStatus.CONFLICT, "DISBURSEMENT_IN_PROGRESS", ex.getMessage());
    }

    @ExceptionHandler(EmiCollectionPreconditionFailedException.class)
    public ResponseEntity<Map<String, Object>> handleEmiCollectionPreconditions(EmiCollectionPreconditionFailedException ex) {
        Map<String, Object> body = baseBody(HttpStatus.UNPROCESSABLE_ENTITY, "EMI_PRECONDITION_FAILED", ex.getMessage());
        body.put("failedConditions", ex.getFailedConditions());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(DuplicateEmiProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmi(DuplicateEmiProcessingException ex) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_EMI_PROCESSING", ex.getMessage());
    }

    @ExceptionHandler(StripeWebhookValidationException.class)
    public ResponseEntity<Map<String, Object>> handleStripeWebhookValidation(StripeWebhookValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_WEBHOOK_SIGNATURE", ex.getMessage());
    }

    @ExceptionHandler(LedgerSyncException.class)
    public ResponseEntity<Map<String, Object>> handleLedgerSync(LedgerSyncException ex) {
        return build(HttpStatus.BAD_GATEWAY, "LEDGER_SYNC_FAILED", ex.getMessage());
    }

    @ExceptionHandler(RepaymentSchedulePersistenceException.class)
    public ResponseEntity<Map<String, Object>> handleSchedulePersistence(RepaymentSchedulePersistenceException ex) {
        return build(HttpStatus.BAD_GATEWAY, "SCHEDULE_PERSISTENCE_ERROR", ex.getMessage());
    }

    @ExceptionHandler(TenantIsolationViolationException.class)
    public ResponseEntity<Map<String, Object>> handleTenantIsolation(TenantIsolationViolationException ex) {
        return build(HttpStatus.FORBIDDEN, "TENANT_ISOLATION_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(NpaOverrideNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleNpaOverrideNotAllowed(NpaOverrideNotAllowedException ex) {
        return build(HttpStatus.CONFLICT, "NPA_OVERRIDE_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(MambuIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleMambu(MambuIntegrationException ex) {
        return build(HttpStatus.BAD_GATEWAY, "MAMBU_INTEGRATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(StripeTransferException.class)
    public ResponseEntity<Map<String, Object>> handleStripeTransfer(StripeTransferException ex) {
        return build(HttpStatus.BAD_GATEWAY, "STRIPE_TRANSFER_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MambuSimulationException.class)
    public ResponseEntity<Map<String, Object>> handleMambuSimulation(MambuSimulationException ex) {
        return build(HttpStatus.BAD_GATEWAY, "MAMBU_SIMULATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "Duplicate or invalid data");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = baseBody(status, code, message);
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> baseBody(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("errorCode", code);
        body.put("message", message);
        body.put("status", status.value());
        return body;
    }
}
