package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mambu client response")
public class MambuClientResponse {
    @Schema(example = "8a818f4d95ecf8d00195eef84f4a0010")
    private String encodedKey;
    @Schema(example = "cli_rahul_sharma_6f72ae")
    private String id;
    @Schema(example = "Rahul")
    private String firstName;
    @Schema(example = "Sharma")
    private String lastName;
    @Schema(example = "Rahul Sharma")
    private String fullName;
    @Schema(example = "rahul.sharma@email.com")
    private String emailAddress;
    @Schema(example = "+919876543210")
    private String mobilePhone;
    @Schema(example = "1990-05-15")
    private String birthDate;
    @Schema(example = "MALE")
    private String gender;
    @Schema(example = "ACTIVE")
    private String state;
    private ClientRole clientRole;
    @Schema(example = "2026-03-03T11:45:10+05:30")
    private String creationDate;
    @Schema(example = "2026-03-03T11:45:10+05:30")
    private String lastModifiedDate;
    @Schema(example = "br_abcfinance_main")
    private String assignedBranchKey;
    private List<MambuClientCreateRequest.IdDocument> idDocuments;
    private List<MambuClientCreateRequest.Address> addresses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Mambu client role metadata")
    public static class ClientRole {
        @Schema(example = "8a818e8d00000001")
        private String encodedKey;
    }
}
