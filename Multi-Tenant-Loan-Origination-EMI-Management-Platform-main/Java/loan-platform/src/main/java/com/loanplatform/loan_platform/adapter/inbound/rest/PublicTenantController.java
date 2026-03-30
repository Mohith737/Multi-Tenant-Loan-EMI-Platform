package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.response.PublicTenantBrowseResponse;
import com.loanplatform.loan_platform.port.inbound.TenantUseCase;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants/public")
@RequiredArgsConstructor
@Validated
public class PublicTenantController {

    private final TenantUseCase tenantUseCase;

    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<PublicTenantBrowseResponse>> listActiveTenants() {
        List<@NotNull PublicTenantBrowseResponse> response = tenantUseCase.listActiveTenantsForPublicBrowse();
        return ResponseEntity.ok(response);
    }
}
