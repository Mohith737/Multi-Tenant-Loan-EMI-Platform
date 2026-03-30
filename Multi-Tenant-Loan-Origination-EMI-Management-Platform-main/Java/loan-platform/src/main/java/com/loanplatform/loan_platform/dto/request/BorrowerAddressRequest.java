package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Borrower postal address")
public class BorrowerAddressRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(example = "123 MG Road")
    private String line1;

    @NotBlank
    @Size(max = 100)
    @Schema(example = "Bengaluru")
    private String city;

    @NotBlank
    @Size(max = 100)
    @Schema(example = "Karnataka")
    private String state;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,10}$", message = "pincode must be numeric")
    @Schema(example = "560001")
    private String pincode;
}
