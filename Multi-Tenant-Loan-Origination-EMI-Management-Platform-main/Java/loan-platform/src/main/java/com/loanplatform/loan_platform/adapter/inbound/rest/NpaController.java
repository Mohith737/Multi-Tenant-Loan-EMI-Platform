package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.NpaOverrideRequest;
import com.loanplatform.loan_platform.dto.response.NpaOverrideResponse;
import com.loanplatform.loan_platform.dto.response.NpaPortfolioResponse;
import com.loanplatform.loan_platform.port.inbound.NpaUseCase;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/npa")
@RequiredArgsConstructor
@Validated
@Tag(name = "NPA", description = "Flow 8 NPA flagging, recovery, and legal escalation APIs")
@SecurityRequirement(name = "bearerAuth")
public class NpaController {

    private final NpaUseCase npaUseCase;
    private final TenantContextResolver tenantContextResolver;

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List tenant NPA portfolio", description = "Returns active NPA records with current recovery stages")
    @ApiResponse(responseCode = "200", description = "NPA portfolio fetched")
    public ResponseEntity<NpaPortfolioResponse> getNpaPortfolio(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(npaUseCase.getNpaPortfolio(tenantId, page, size));
    }

    @PostMapping("/{loanAccountId}/override")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Override NPA lock", description = "Clears active NPA lock for a loan with admin reason and note")
    @ApiResponse(responseCode = "200", description = "NPA lock cleared")
    public ResponseEntity<NpaOverrideResponse> overrideNpa(
            @PathVariable String loanAccountId,
            @Valid @RequestBody NpaOverrideRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID actorId = tenantContextResolver.currentActorId();
        return ResponseEntity.ok(npaUseCase.overrideNpaLoan(tenantId, actorId, loanAccountId, request));
    }
}
