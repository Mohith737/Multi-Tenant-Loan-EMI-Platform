package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.response.LoanProductResponse;
import com.loanplatform.loan_platform.port.inbound.LoanProductUseCase;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Loan Product Configuration", description = "Flow 1 loan product APIs")
@SecurityRequirement(name = "bearerAuth")
public class LoanProductController {

    private final LoanProductUseCase loanProductUseCase;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create loan product", description = "Creates a loan product in Mambu and maps it to current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Loan product created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "502", description = "Mambu integration error")
    })
    public ResponseEntity<LoanProductResponse> createLoanProduct(
            @Valid @RequestBody LoanProductConfigRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(loanProductUseCase.createLoanProduct(tenantId, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List loan products", description = "Lists loan products mapped for current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan products fetched"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<List<LoanProductResponse>> listLoanProducts() {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(loanProductUseCase.listLoanProducts(tenantId));
    }
}
