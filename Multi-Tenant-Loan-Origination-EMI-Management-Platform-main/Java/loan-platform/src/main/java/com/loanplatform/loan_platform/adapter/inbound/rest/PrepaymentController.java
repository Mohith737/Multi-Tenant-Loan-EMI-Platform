package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.PrepaymentExecutionRequest;
import com.loanplatform.loan_platform.dto.request.PrepaymentSimulationRequest;
import com.loanplatform.loan_platform.dto.response.NocResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentAdminViewResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentExecutionResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentSimulationResponse;
import com.loanplatform.loan_platform.port.inbound.PrepaymentUseCase;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Prepayment", description = "Flow 7 prepayment and foreclosure APIs")
@SecurityRequirement(name = "bearerAuth")
public class PrepaymentController {

    private final PrepaymentUseCase prepaymentUseCase;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping("/loans/{loanAccountId}/prepayment/simulate")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Simulate prepayment/foreclosure", description = "Returns payable amount and revised schedule preview")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Simulation generated"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "422", description = "Business precondition failure")
    })
    public ResponseEntity<PrepaymentSimulationResponse> simulate(
            @PathVariable String loanAccountId,
            @Valid @RequestBody PrepaymentSimulationRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(prepaymentUseCase.simulatePrepayment(tenantId, borrowerId, loanAccountId, request));
    }

    @PostMapping("/loans/{loanAccountId}/prepayment/execute")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Execute prepayment/foreclosure", description = "Creates Stripe payment intent for borrower-approved quote")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Execution initiated"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "409", description = "Duplicate/in-progress request")
    })
    public ResponseEntity<PrepaymentExecutionResponse> execute(
            @PathVariable String loanAccountId,
            @Valid @RequestBody PrepaymentExecutionRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(prepaymentUseCase.executePrepayment(tenantId, borrowerId, loanAccountId, request));
    }

    @GetMapping("/loans/{loanAccountId}/prepayment/{requestId}")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Get prepayment request status", description = "Returns current payment and processing status for borrower request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request status fetched"),
            @ApiResponse(responseCode = "404", description = "Request not found")
    })
    public ResponseEntity<PrepaymentExecutionResponse> getRequestStatus(
            @PathVariable String loanAccountId,
            @PathVariable UUID requestId
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(prepaymentUseCase.getPrepaymentRequest(tenantId, borrowerId, loanAccountId, requestId));
    }

    @GetMapping("/admin/prepayments")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get tenant prepayment activity", description = "Read-only dashboard API for tenant admin")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Prepayment activity fetched")
    })
    public ResponseEntity<PrepaymentAdminViewResponse> getAdminPrepayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(prepaymentUseCase.getAdminPrepayments(tenantId, page, size));
    }

    @GetMapping("/loans/{loanAccountId}/noc")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "Get NOC for foreclosed loan", description = "Returns No Objection Certificate metadata after foreclosure")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "NOC fetched"),
            @ApiResponse(responseCode = "422", description = "Loan is not foreclosed")
    })
    public ResponseEntity<NocResponse> getNoc(@PathVariable String loanAccountId, Authentication authentication) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));
        if (tenantAdmin) {
            return ResponseEntity.ok(prepaymentUseCase.getNocForTenant(tenantId, loanAccountId));
        }
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(prepaymentUseCase.getNocForBorrower(tenantId, borrowerId, loanAccountId));
    }
}
