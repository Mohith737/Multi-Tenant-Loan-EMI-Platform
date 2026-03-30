package com.loanplatform.loan_platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Borrower response")
public class BorrowerResponse {
    @Schema(example = "b550e840-e29b-41d4-a716-446655440001")
    private UUID borrowerId;
    @Schema(example = "Rahul")
    private String firstName;
    @Schema(example = "Sharma")
    private String lastName;
    @Schema(example = "ABCDE1234F")
    private String panNumber;
    @Schema(example = "1234", description = "Last 4 digits of Aadhaar")
    private String aadhaarLastFour;
    @Schema(example = "rahul.sharma@email.com")
    private String email;
    @Schema(example = "+919876543210")
    private String mobileNumber;
    @Schema(example = "PENDING")
    private String kycStatus;
    @Schema(example = "cli_rahul_001")
    private String mambuClientId;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;
    @Schema(example = "2024-01-15T11:00:00Z")
    private Instant createdAt;
    @Schema(example = "2024-01-15T11:00:00Z")
    private Instant updatedAt;
}
