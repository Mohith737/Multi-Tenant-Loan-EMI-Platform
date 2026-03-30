package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.borrower.service.KycService;
import com.loanplatform.loan_platform.dto.request.KycSubmissionRequest;
import com.loanplatform.loan_platform.dto.response.KycSubmissionResponse;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping("/submit")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<KycSubmissionResponse> submitKyc(@Valid @RequestBody KycSubmissionRequest request) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.submit(tenantId, borrowerId, request));
    }
}
