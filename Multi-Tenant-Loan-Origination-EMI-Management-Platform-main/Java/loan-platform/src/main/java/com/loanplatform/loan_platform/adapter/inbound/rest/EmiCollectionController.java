package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.EmiWaiveRequest;
import com.loanplatform.loan_platform.dto.response.AdminDueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminFailedInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminOverdueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.BorrowerInstallmentScheduleResponse;
import com.loanplatform.loan_platform.dto.response.PaymentHistoryResponse;
import com.loanplatform.loan_platform.dto.response.ReconciliationReportResponse;
import com.loanplatform.loan_platform.port.inbound.EmiCollectionUseCase;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "EMI Collection", description = "Flow 6 EMI collection and reconciliation APIs")
@SecurityRequirement(name = "bearerAuth")
public class EmiCollectionController {

    private final EmiCollectionUseCase emiCollectionUseCase;
    private final TenantContextResolver tenantContextResolver;

    @GetMapping("/loans/{applicationId}/installments")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Get borrower installment schedule", description = "Returns installment-level payment statuses")
    @ApiResponse(responseCode = "200", description = "Installments fetched")
    public ResponseEntity<BorrowerInstallmentScheduleResponse> getBorrowerInstallments(@PathVariable UUID applicationId) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(emiCollectionUseCase.getInstallmentsForBorrower(tenantId, borrowerId, applicationId));
    }

    @GetMapping("/loans/{applicationId}/payment-history")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Get borrower payment history", description = "Returns paid installment history")
    @ApiResponse(responseCode = "200", description = "Payment history fetched")
    public ResponseEntity<PaymentHistoryResponse> getPaymentHistory(@PathVariable UUID applicationId) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(emiCollectionUseCase.getPaymentHistoryForBorrower(tenantId, borrowerId, applicationId));
    }

    @GetMapping("/admin/emi/due-today")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get due EMIs", description = "Returns due installments for current tenant")
    @ApiResponse(responseCode = "200", description = "Due installments fetched")
    public ResponseEntity<AdminDueInstallmentsResponse> getDueToday(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(emiCollectionUseCase.getDueTodayForAdmin(tenantId, paymentStatus, page, size));
    }

    @GetMapping("/admin/emi/failed")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get failed EMIs", description = "Returns installments marked for retry")
    @ApiResponse(responseCode = "200", description = "Failed installments fetched")
    public ResponseEntity<AdminFailedInstallmentsResponse> getFailed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(emiCollectionUseCase.getFailedForAdmin(tenantId, page, size));
    }

    @GetMapping("/admin/emi/overdue")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get overdue EMIs", description = "Returns overdue installments")
    @ApiResponse(responseCode = "200", description = "Overdue installments fetched")
    public ResponseEntity<AdminOverdueInstallmentsResponse> getOverdue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(emiCollectionUseCase.getOverdueForAdmin(tenantId, page, size));
    }

    @PostMapping("/admin/emi/{installmentId}/waive")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Waive EMI installment", description = "Marks installment as waived with reason")
    @ApiResponse(responseCode = "200", description = "Installment waived")
    public ResponseEntity<Void> waiveInstallment(@PathVariable UUID installmentId, @Valid @RequestBody EmiWaiveRequest request) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID actorId = tenantContextResolver.currentActorId();
        emiCollectionUseCase.waiveInstallment(tenantId, actorId, installmentId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/reconciliation/{date}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get reconciliation report", description = "Returns reconciliation report for tenant and date")
    @ApiResponse(responseCode = "200", description = "Reconciliation fetched")
    public ResponseEntity<ReconciliationReportResponse> getReconciliation(@PathVariable String date) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(emiCollectionUseCase.getReconciliationReport(tenantId, LocalDate.parse(date)));
    }
}
