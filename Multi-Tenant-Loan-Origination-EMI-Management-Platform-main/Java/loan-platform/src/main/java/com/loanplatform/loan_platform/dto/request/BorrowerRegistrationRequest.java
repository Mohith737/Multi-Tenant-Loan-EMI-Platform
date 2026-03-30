package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Borrower registration request")
public class BorrowerRegistrationRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(example = "Rahul")
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Schema(example = "Sharma")
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 180)
    @Schema(example = "rahul.sharma@email.com")
    private String email;

    @NotBlank
    @Schema(example = "S3cureP@ssword")
    private String password;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^\\+?[1-9][0-9]{9,14}$", message = "Invalid mobile number")
    @Schema(example = "+919876543210")
    private String mobile;

    @NotNull
    @Past
    @Schema(example = "1990-05-15")
    private LocalDate dateOfBirth;

    @NotBlank
    @Size(max = 20)
    @Schema(example = "MALE")
    private String gender;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN must match standard format")
    @Schema(example = "ABCDE1234F")
    private String panNumber;

    @NotBlank
    @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar must be 12 digits")
    @Schema(example = "987654321234")
    private String aadhaarNumber;

    @NotNull
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;

    @NotNull
    @Valid
    private BorrowerAddressRequest address;
}
