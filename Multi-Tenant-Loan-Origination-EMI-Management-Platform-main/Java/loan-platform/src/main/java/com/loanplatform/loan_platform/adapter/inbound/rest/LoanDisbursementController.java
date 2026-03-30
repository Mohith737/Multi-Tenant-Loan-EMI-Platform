package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.LoanDisbursementRequest;
import com.loanplatform.loan_platform.dto.response.DisbursementStatusResponse;
import com.loanplatform.loan_platform.dto.response.LoanDisbursementResponse;
import com.loanplatform.loan_platform.dto.response.LoanScheduleResponse;
import com.loanplatform.loan_platform.port.inbound.DisbursementUseCase;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Validated
@Tag(name = "Loan Disbursement", description = "Flow 5 disbursement and schedule APIs")
@SecurityRequirement(name = "bearerAuth")
public class LoanDisbursementController {

    private final DisbursementUseCase disbursementUseCase;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping("/{applicationId}/disburse")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Disburse approved loan", description = "Triggers Mambu disbursement, persists schedule, and notifies borrower")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disbursement completed"),
            @ApiResponse(responseCode = "409", description = "Disbursement in progress or state conflict"),
            @ApiResponse(responseCode = "422", description = "Precondition failed")
    })
    public ResponseEntity<LoanDisbursementResponse> disburse(
            @PathVariable UUID applicationId,
            @Valid @RequestBody LoanDisbursementRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID actorId = tenantContextResolver.currentActorId();
        return ResponseEntity.ok(disbursementUseCase.disburse(tenantId, actorId, applicationId, request));
    }

    @GetMapping("/{applicationId}/disbursement-status")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get disbursement status", description = "Returns current Flow 5 disbursement status for tenant admins")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<DisbursementStatusResponse> getDisbursementStatus(@PathVariable UUID applicationId) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(disbursementUseCase.getDisbursementStatus(tenantId, applicationId));
    }

    @GetMapping("/{applicationId}/schedule")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "Get repayment schedule", description = "Returns locally persisted repayment schedule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Schedule fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<LoanScheduleResponse> getSchedule(
            @PathVariable UUID applicationId,
            Authentication authentication
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));

        if (tenantAdmin) {
            return ResponseEntity.ok(disbursementUseCase.getScheduleForTenant(tenantId, applicationId));
        }

        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(disbursementUseCase.getScheduleForBorrower(tenantId, borrowerId, applicationId));
    }

    @GetMapping("/{applicationId}/disbursement-details")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "Get disbursement details", description = "Returns disbursement summary for borrower/admin")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Details fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<LoanDisbursementResponse> getDisbursementDetails(
            @PathVariable UUID applicationId,
            Authentication authentication
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));

        if (tenantAdmin) {
            return ResponseEntity.ok(disbursementUseCase.getDisbursementDetailsForTenant(tenantId, applicationId));
        }

        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(disbursementUseCase.getDisbursementDetailsForBorrower(tenantId, borrowerId, applicationId));
    }
}
