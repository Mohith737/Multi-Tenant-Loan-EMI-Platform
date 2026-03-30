package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.borrower.service.KycService;
import com.loanplatform.loan_platform.dto.request.KycVerificationRequest;
import com.loanplatform.loan_platform.dto.response.KycDocumentResponse;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/kyc")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
public class AdminKycController {

    private final KycService kycService;
    private final TenantContextResolver tenantContextResolver;

    @PutMapping("/{kycDocumentId}/verify")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<KycDocumentResponse> verifyKyc(
            @PathVariable UUID kycDocumentId,
            @Valid @RequestBody KycVerificationRequest request,
            Principal principal
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        String verifiedBy = principal != null ? principal.getName() : "TENANT_ADMIN";
        return ResponseEntity.ok(kycService.verify(tenantId, kycDocumentId, request, verifiedBy));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<KycDocumentResponse>> pendingQueue() {
        UUID tenantId = tenantContextResolver.currentTenantId();
        return ResponseEntity.ok(kycService.listPending(tenantId));
    }
}
