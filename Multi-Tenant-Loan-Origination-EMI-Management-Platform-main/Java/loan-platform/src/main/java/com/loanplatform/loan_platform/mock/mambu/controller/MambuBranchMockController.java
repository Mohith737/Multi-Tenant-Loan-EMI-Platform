package com.loanplatform.loan_platform.mock.mambu.controller;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
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
@Tag(name = "Mambu Mock - Branches", description = "Mock Mambu branch APIs")
public class MambuBranchMockController {

    private final InMemoryMambuStore store;

    @PostMapping("/branches")
    @Operation(summary = "Create Mambu branch", description = "Mock endpoint to create tenant branch in Mambu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mambu branch created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<MambuBranchResponse> createBranch(
            @RequestBody(description = "Mambu branch payload", required = true)
            @org.springframework.web.bind.annotation.RequestBody MambuBranchCreateRequest request
    ) throws InterruptedException {
        Thread.sleep(200);

        String now = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        MambuBranchResponse response = MambuBranchResponse.builder()
                .encodedKey(UUID.randomUUID().toString().replace("-", ""))
                .id(resolveId(request.getId()))
                .name(request.getName())
                .state(request.getState() == null ? "ACTIVE" : request.getState())
                .creationDate(now)
                .lastModifiedDate(now)
                .address(MambuBranchResponse.Address.builder()
                        .country(request.getAddress() == null ? "IN" : request.getAddress().getCountry())
                        .city(request.getAddress() == null ? "Bengaluru" : request.getAddress().getCity())
                        .build())
                .emailAddress(request.getEmailAddress())
                .phoneNumber(request.getPhoneNumber())
                .build();

        store.saveBranch(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String resolveId(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return "branch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }
}
