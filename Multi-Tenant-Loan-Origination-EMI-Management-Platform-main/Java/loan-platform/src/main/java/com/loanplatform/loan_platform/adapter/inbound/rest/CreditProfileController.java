package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.borrower.service.CreditProfileService;
import com.loanplatform.loan_platform.dto.request.CreditProfileRequest;
import com.loanplatform.loan_platform.dto.response.CreditProfileResponse;
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
@RequestMapping("/api/v1/credit-profile")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
public class CreditProfileController {

    private final CreditProfileService creditProfileService;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<CreditProfileResponse> setupCreditProfile(@Valid @RequestBody CreditProfileRequest request) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.status(HttpStatus.CREATED).body(creditProfileService.setup(tenantId, borrowerId, request));
    }
}
