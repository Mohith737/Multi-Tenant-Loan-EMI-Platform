package com.loanplatform.loan_platform.domain.borrower.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerAddress {

    @Column(name = "address_line1", nullable = false, length = 255)
    private String line1;

    @Column(name = "address_city", nullable = false, length = 100)
    private String city;

    @Column(name = "address_state", nullable = false, length = 100)
    private String state;

    @Column(name = "address_pincode", nullable = false, length = 15)
    private String pincode;
}
