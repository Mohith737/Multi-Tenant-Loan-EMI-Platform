package com.loanplatform.loan_platform.adapter.outbound.mambu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuBranchResponse {
    private String encodedKey;
    private String id;
    private String name;
    private String state;
    private String creationDate;
    private String lastModifiedDate;
    private Address address;
    private String emailAddress;
    private String phoneNumber;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String country;
        private String city;
    }
}
