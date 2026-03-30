package com.loanplatform.loan_platform.adapter.outbound.stripe;

import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentCommand;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentResult;
import com.loanplatform.loan_platform.exception.StripeTransferException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class StripeGatewayService {

    @Value("${app.stripe.enabled:false}")
    private boolean stripeEnabled;

    @Value("${app.stripe.api-key:}")
    private String stripeApiKey;

    @Value("${app.stripe.currency:inr}")
    private String stripeCurrency;

    public StripePaymentIntentResult createEmiPaymentIntent(UUID tenantId, UUID installmentId, StripePaymentIntentCommand command) {
        return createPaymentIntentInternal(tenantId, installmentId, command, "installment");
    }

    public StripePaymentIntentResult createPrepaymentPaymentIntent(UUID tenantId, UUID prepaymentRequestId, StripePaymentIntentCommand command) {
        return createPaymentIntentInternal(tenantId, prepaymentRequestId, command, "prepaymentRequest");
    }

    private StripePaymentIntentResult createPaymentIntentInternal(
            UUID tenantId,
            UUID entityId,
            StripePaymentIntentCommand command,
            String entityType
    ) {
        if (!stripeEnabled) {
            throw new StripeTransferException("Stripe payment intent creation is disabled by configuration");
        }
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new StripeTransferException("Stripe API key is missing for EMI collection");
        }
        if (command == null) {
            throw new StripeTransferException("Stripe payment intent command cannot be null");
        }
        if (command.getCustomerId() == null || command.getCustomerId().isBlank()) {
            throw new StripeTransferException("Stripe customer id is missing");
        }
        if (command.getPaymentMethodId() == null || command.getPaymentMethodId().isBlank()) {
            throw new StripeTransferException("Stripe payment method id is missing");
        }
        if (command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StripeTransferException("Stripe payment intent amount is invalid");
        }
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()) {
            throw new StripeTransferException("Stripe payment intent idempotency key is missing");
        }

        Stripe.apiKey = stripeApiKey;
        long amountInMinorUnits = command.getAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(command.getCurrency() == null || command.getCurrency().isBlank() ? stripeCurrency : command.getCurrency())
                .setCustomer(command.getCustomerId())
                .setPaymentMethod(command.getPaymentMethodId())
                .setConfirm(true)
                .setOffSession(true);

        if (command.getDescription() != null && !command.getDescription().isBlank()) {
            paramsBuilder.setDescription(command.getDescription());
        }
        if (command.getMetadata() != null) {
            for (Map.Entry<String, String> entry : command.getMetadata().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    paramsBuilder.putMetadata(entry.getKey(), entry.getValue());
                }
            }
        }

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(command.getIdempotencyKey())
                .build();

        try {
            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), requestOptions);
            return StripePaymentIntentResult.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .status(paymentIntent.getStatus())
                    .clientSecret(paymentIntent.getClientSecret())
                    .build();
        } catch (StripeException ex) {
            log.error("Stripe PaymentIntent creation failed tenantId={} {}Id={}", tenantId, entityType, entityId, ex);
            throw new StripeTransferException("Stripe PaymentIntent creation failed for " + entityType + " " + entityId, ex);
        }
    }

    public String createDisbursementTransfer(
            UUID tenantId,
            UUID applicationId,
            BigDecimal disbursementAmount,
            String destinationAccountId
    ) {
        if (!stripeEnabled) {
            throw new StripeTransferException("Stripe disbursement transfer is disabled by configuration");
        }
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new StripeTransferException("Stripe API key is missing for disbursement transfer");
        }
        if (destinationAccountId == null || destinationAccountId.isBlank()) {
            throw new StripeTransferException("Stripe destination account is missing");
        }
        if (disbursementAmount == null || disbursementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new StripeTransferException("Stripe transfer amount is invalid");
        }

        Stripe.apiKey = stripeApiKey;

        long amountInMinorUnits = disbursementAmount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(stripeCurrency)
                .setDestination(destinationAccountId)
                .setDescription("Flow5 disbursement for application " + applicationId)
                .putMetadata("tenantId", tenantId.toString())
                .putMetadata("applicationId", applicationId.toString())
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("DISB:" + applicationId + ":" + tenantId)
                .build();

        try {
            Transfer transfer = Transfer.create(params, requestOptions);
            return transfer.getId();
        } catch (StripeException ex) {
            log.error("Stripe transfer creation failed tenantId={} applicationId={}", tenantId, applicationId, ex);
            throw new StripeTransferException("Stripe transfer creation failed for application " + applicationId, ex);
        }
    }
}
