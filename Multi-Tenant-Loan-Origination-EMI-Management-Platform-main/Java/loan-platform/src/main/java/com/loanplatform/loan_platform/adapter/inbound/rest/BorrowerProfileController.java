package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.borrower.service.BorrowerService;
import com.loanplatform.loan_platform.dto.response.BorrowerSyncStatusResponse;
import com.loanplatform.loan_platform.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrowers/me")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
public class BorrowerProfileController {

    private final BorrowerService borrowerService;
    private final TenantContextResolver tenantContextResolver;

    @GetMapping("/sync-status")
    @PreAuthorize("hasRole('BORROWER')")
    public ResponseEntity<BorrowerSyncStatusResponse> syncStatus() {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(borrowerService.getSyncStatus(tenantId, borrowerId));
    }
}
