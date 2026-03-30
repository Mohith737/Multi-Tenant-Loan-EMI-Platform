package com.loanplatform.loan_platform.mock.mambu.controller;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.mock.mambu.store.InMemoryMambuStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Tag(name = "Mambu Mock - Loan Products", description = "Mock Mambu loan product APIs")
public class MambuLoanProductMockController {

    private final InMemoryMambuStore store;

    @PostMapping("/loanproducts")
    @Operation(summary = "Create Mambu loan product", description = "Mock endpoint to create tenant loan product in Mambu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mambu loan product created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<MambuLoanProductResponse> createLoanProduct(
            @RequestBody(description = "Mambu loan product payload", required = true)
            @org.springframework.web.bind.annotation.RequestBody MambuLoanProductCreateRequest request
    ) throws InterruptedException {
        Thread.sleep(200);

        String now = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        MambuLoanProductResponse response = MambuLoanProductResponse.builder()
                .encodedKey(UUID.randomUUID().toString().replace("-", ""))
                .id(resolveId(request.getId()))
                .name(request.getName())
                .state(request.getState() == null ? "ACTIVE" : request.getState())
                .type(request.getType() == null ? "FIXED_TERM_LOAN" : request.getType())
                .creationDate(now)
                .lastModifiedDate(now)
                .currency(request.getCurrency())
                .loanAmountSettings(request.getLoanAmountSettings())
                .interestSettings(request.getInterestSettings())
                .scheduleSettings(request.getScheduleSettings())
                .build();

        store.saveLoanProduct(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String resolveId(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return "LP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
