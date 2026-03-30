package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.loan.service.LoanDocumentService;
import com.loanplatform.loan_platform.dto.request.LoanDocumentUploadRequest;
import com.loanplatform.loan_platform.dto.response.LoanDocumentResponse;
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
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Validated
@Tag(name = "Loan Documents", description = "Flow 4 document upload and retrieval APIs")
@SecurityRequirement(name = "bearerAuth")
public class LoanDocumentController {

    private final LoanDocumentService loanDocumentService;
    private final TenantContextResolver tenantContextResolver;

    @PostMapping("/{applicationId}/documents")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "Upload loan document", description = "Uploads borrower document for selected loan offer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<LoanDocumentResponse> uploadDocument(
            @PathVariable UUID applicationId,
            @Valid @RequestBody LoanDocumentUploadRequest request
    ) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        UUID borrowerId = tenantContextResolver.currentBorrowerId();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request));
    }

    @GetMapping("/{applicationId}/documents")
    @PreAuthorize("hasAnyRole('BORROWER','TENANT_ADMIN')")
    @Operation(summary = "List application documents", description = "Lists uploaded documents for borrower or underwriter review")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Documents fetched"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<List<LoanDocumentResponse>> getDocuments(@PathVariable UUID applicationId, Authentication authentication) {
        UUID tenantId = tenantContextResolver.currentTenantId();
        boolean tenantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TENANT_ADMIN".equals(a.getAuthority()));

        if (tenantAdmin) {
            return ResponseEntity.ok(loanDocumentService.getDocumentsForTenant(tenantId, applicationId));
        }

        UUID borrowerId = tenantContextResolver.currentBorrowerId();
        return ResponseEntity.ok(loanDocumentService.getDocumentsForBorrower(tenantId, borrowerId, applicationId));
    }
}
