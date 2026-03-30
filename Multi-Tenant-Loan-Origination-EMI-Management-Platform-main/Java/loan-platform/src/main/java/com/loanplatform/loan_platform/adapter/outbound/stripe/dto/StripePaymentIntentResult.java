package com.loanplatform.loan_platform.adapter.outbound.stripe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentIntentResult {

    private String paymentIntentId;
    private String status;
    private String clientSecret;
}
