package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.ActivateTenantRequest;
import com.loanplatform.loan_platform.dto.request.SuspendTenantRequest;
import com.loanplatform.loan_platform.dto.request.TenantRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.TenantActivationResponse;
import com.loanplatform.loan_platform.dto.response.TenantRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.TenantResponse;
import com.loanplatform.loan_platform.dto.response.TenantSuspensionResponse;
import com.loanplatform.loan_platform.port.inbound.TenantUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tenant Onboarding", description = "Flow 1 tenant onboarding APIs")
@SecurityRequirements(value = {
        @SecurityRequirement(name = "basicAuth"),
        @SecurityRequirement(name = "bearerAuth")
})
public class TenantController {

    private final TenantUseCase tenantUseCase;

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Register tenant", description = "Registers tenant, provisions Mambu branch, then activates tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant onboarded"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Tenant domain already exists"),
            @ApiResponse(responseCode = "502", description = "Mambu integration error")
    })
    public ResponseEntity<TenantRegistrationResponse> registerTenant(@Valid @RequestBody TenantRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantUseCase.registerTenant(request));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get tenant by ID", description = "Fetches tenant details by tenant identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant fetched"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantResponse> getTenant(
            @Parameter(description = "Tenant identifier", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID tenantId
    ) {
        return ResponseEntity.ok(tenantUseCase.getTenant(tenantId));
    }

    @PutMapping("/{tenantId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Activate tenant", description = "Activates a tenant currently in PENDING status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant activated"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<TenantActivationResponse> activateTenant(
            @Parameter(description = "Tenant identifier", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID tenantId
    ) {
        return ResponseEntity.ok(tenantUseCase.activateTenant(tenantId, new ActivateTenantRequest()));
    }

    @PutMapping("/{tenantId}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspend tenant", description = "Suspends an active tenant with a mandatory reason")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant suspended"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<TenantSuspensionResponse> suspendTenant(
            @Parameter(description = "Tenant identifier", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID tenantId,
            @Valid @RequestBody SuspendTenantRequest request
    ) {
        return ResponseEntity.ok(tenantUseCase.suspendTenant(tenantId, request));
    }
}
