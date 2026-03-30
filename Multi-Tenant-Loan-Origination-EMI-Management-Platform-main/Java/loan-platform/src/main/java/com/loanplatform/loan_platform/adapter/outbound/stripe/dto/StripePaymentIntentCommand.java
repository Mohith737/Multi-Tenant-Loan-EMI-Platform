package com.loanplatform.loan_platform.adapter.outbound.stripe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentIntentCommand {

    private BigDecimal amount;
    private String currency;
    private String customerId;
    private String paymentMethodId;
    private String description;
    private String idempotencyKey;
    private Map<String, String> metadata;
}
