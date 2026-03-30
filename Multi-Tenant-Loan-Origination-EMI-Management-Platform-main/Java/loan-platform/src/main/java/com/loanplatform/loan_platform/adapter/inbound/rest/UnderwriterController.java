package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.loan.service.UnderwriterService;
import com.loanplatform.loan_platform.dto.request.UnderwriterDecisionRequest;
import com.loanplatform.loan_platform.dto.response.UnderwriterDecisionResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterQueueResponse;
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
@RequiredArgsConstructor
@Validated
@Tag(name = "Underwriter", description = "Flow 4 verification queue and decision APIs")
@SecurityRequirement(name = "bearerAuth")
public class UnderwriterController {

    private final UnderwriterService underwriterService;
    private final TenantContextResolver tenantContextResolver;

    @GetMapping("/api/v1/underwriter/queue")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Underwriter queue", description = "Returns tenant-scoped applications in UNDER_VERIFICATION state")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Queue fetched")
    })
    public ResponseEntity<UnderwriterQueueResponse> getQueue() {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(underwriterService.getQueue(tenantId));
    }

    @PostMapping("/api/v1/loans/{applicationId}/decision")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Underwriter decision", description = "Records APPROVED/REJECTED decision and triggers Flow 4 transitions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Decision recorded"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "409", description = "Decision conflict")
    })
    public ResponseEntity<UnderwriterDecisionResponse> decide(
            @PathVariable UUID applicationId,
            @Valid @RequestBody UnderwriterDecisionRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID underwriterId = tenantContextResolver.currentActorId();
        return ResponseEntity.ok(underwriterService.decide(tenantId, underwriterId, applicationId, request));
    }

    @GetMapping("/api/v1/loans/{applicationId}/decision")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "Get decision", description = "Returns final decision for loan application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Decision fetched"),
            @ApiResponse(responseCode = "404", description = "Application or decision not found")
    })
    public ResponseEntity<UnderwriterDecisionResponse> getDecision(
            @PathVariable UUID applicationId,
            Authentication authentication
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));
        UUID borrowerId = tenantAdmin ? null : tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(underwriterService.getDecision(tenantId, borrowerId, applicationId, tenantAdmin));
    }
}
