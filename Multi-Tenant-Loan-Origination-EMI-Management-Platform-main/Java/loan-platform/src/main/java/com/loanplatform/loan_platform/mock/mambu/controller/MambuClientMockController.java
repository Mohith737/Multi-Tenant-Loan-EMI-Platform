package com.loanplatform.loan_platform.mock.mambu.controller;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientResponse;
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
@Tag(name = "Mambu Mock - Clients", description = "Mock Mambu client APIs")
public class MambuClientMockController {

    private final InMemoryMambuStore store;

    @PostMapping("/clients")
    @Operation(summary = "Create Mambu client", description = "Mock endpoint to create borrower client in Mambu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mambu client created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<MambuClientResponse> createClient(
            @RequestBody(description = "Mambu client payload", required = true)
            @org.springframework.web.bind.annotation.RequestBody MambuClientCreateRequest request
    )
            throws InterruptedException {
        Thread.sleep(200);

        String generatedId = generateClientId(request.getFirstName(), request.getLastName());
        String now = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        MambuClientResponse response = MambuClientResponse.builder()
                .encodedKey(UUID.randomUUID().toString().replace("-", ""))
                .id(generatedId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .fullName(request.getFirstName() + " " + request.getLastName())
                .emailAddress(request.getEmailAddress())
                .mobilePhone(request.getMobilePhone())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .state(request.getState() == null ? "ACTIVE" : request.getState())
                .clientRole(MambuClientResponse.ClientRole.builder()
                        .encodedKey("8a818e8d00000001")
                        .build())
                .creationDate(now)
                .lastModifiedDate(now)
                .assignedBranchKey(request.getAssignedBranchKey())
                .idDocuments(request.getIdDocuments())
                .addresses(request.getAddresses())
                .build();

        store.saveClient(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String generateClientId(String firstName, String lastName) {
        String first = firstName == null ? "client" : firstName.toLowerCase(Locale.ROOT);
        String last = lastName == null ? "user" : lastName.toLowerCase(Locale.ROOT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return "cli_" + first + "_" + last + "_" + suffix;
    }
}
