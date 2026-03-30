package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.dto.request.LoanApplicationRequest;
import com.loanplatform.loan_platform.dto.request.LoanOfferSelectionRequest;
import com.loanplatform.loan_platform.dto.response.LoanApplicationResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferSelectionResponse;
import com.loanplatform.loan_platform.port.inbound.LoanApplicationUseCase;
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
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans/applications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Loan Application", description = "Flow 3 loan application and offer APIs")
@SecurityRequirement(name = "bearerAuth")
public class LoanApplicationController {

    private final LoanApplicationUseCase loanApplicationUseCase;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Submit loan application", description = "Runs eligibility, simulates three offers, stores APPLICATION_SUBMITTED")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application submitted and offers generated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Duplicate active loan/application"),
            @ApiResponse(responseCode = "422", description = "Eligibility failed"),
            @ApiResponse(responseCode = "502", description = "Mambu simulation failure")
    })
    public ResponseEntity<LoanApplicationResponse> submitApplication(@Valid @RequestBody LoanApplicationRequest request) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanApplicationUseCase.submitApplication(tenantId, borrowerId, request));
    }

    @GetMapping("/{applicationId}/offers")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Get offers", description = "Returns generated offers for borrower application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offers fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<List<LoanOfferResponse>> getOffers(@PathVariable UUID applicationId) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(loanApplicationUseCase.getOffers(tenantId, borrowerId, applicationId));
    }

    @PostMapping("/{applicationId}/offers/select")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Select offer", description = "Selects one active offer and moves application to OFFER_SELECTED")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Offer selected"),
            @ApiResponse(responseCode = "404", description = "Application/offer not found"),
            @ApiResponse(responseCode = "409", description = "Offer expired or state invalid")
    })
    public ResponseEntity<LoanOfferSelectionResponse> selectOffer(
            @PathVariable UUID applicationId,
            @Valid @RequestBody LoanOfferSelectionRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(loanApplicationUseCase.selectOffer(tenantId, borrowerId, applicationId, request));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "Get application", description = "Fetches application summary and associated offers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<LoanApplicationResponse> getApplication(@PathVariable UUID applicationId, Authentication authentication) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));

        if (tenantAdmin) {
            return ResponseEntity.ok(loanApplicationUseCase.getApplicationForTenant(tenantId, applicationId));
        }

        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(loanApplicationUseCase.getApplication(tenantId, borrowerId, applicationId));
    }
}
