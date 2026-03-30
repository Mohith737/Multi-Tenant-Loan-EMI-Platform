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
@Schema(description = "Mambu client creation request")
public class MambuClientCreateRequest {
    @Schema(example = "Rahul")
    private String firstName;
    @Schema(example = "Sharma")
    private String lastName;
    @Schema(example = "rahul.sharma@email.com")
    private String emailAddress;
    @Schema(example = "+919876543210")
    private String mobilePhone;
    @Schema(example = "1990-05-15")
    private String birthDate;
    @Schema(example = "MALE")
    private String gender;
    @Schema(example = "br_abcfinance_main")
    private String assignedBranchKey;
    @Schema(example = "ACTIVE")
    private String state;
    @Schema(description = "Identity documents for client verification")
    private List<IdDocument> idDocuments;
    @Schema(description = "Address list for the client")
    private List<Address> addresses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Mambu client ID document")
    public static class IdDocument {
        @Schema(example = "PAN")
        private String documentType;
        @Schema(example = "ABCDE1234F")
        private String documentId;
        @Schema(example = "INCOME_TAX_DEPARTMENT")
        private String issuingAuthority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Mambu client address")
    public static class Address {
        @Schema(example = "123 MG Road")
        private String line1;
        @Schema(example = "Bengaluru")
        private String city;
        @Schema(example = "Karnataka")
        private String region;
        @Schema(example = "560001")
        private String postcode;
        @Schema(example = "IN")
        private String country;
        @Schema(example = "0")
        private Integer indexInList;
    }
}
