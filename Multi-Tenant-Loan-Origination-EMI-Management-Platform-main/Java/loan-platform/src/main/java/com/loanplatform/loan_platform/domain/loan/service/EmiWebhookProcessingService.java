package com.loanplatform.loan_platform.domain.loan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.exception.StripeWebhookValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiWebhookProcessingService {

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LedgerUpdateService ledgerUpdateService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.flow6.max-retry-count:3}")
    private int maxRetryCount;

    @Value("${app.flow6.webhook-dedupe-ttl-seconds:86400}")
    private long webhookDedupeTtlSeconds;

    @Transactional(propagation = Propagation.REQUIRED)
    public void processEvent(String eventType, JsonNode payload) {
        JsonNode objectNode = payload.path("data").path("object");
        String paymentIntentId = objectNode.path("id").asText(null);
        JsonNode metadata = objectNode.path("metadata");

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new StripeWebhookValidationException("Stripe webhook payment intent id is missing");
        }

        String dedupeKey = "emi:webhook:processed:" + paymentIntentId + ":" + eventType;
        Boolean firstDelivery = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", Duration.ofSeconds(webhookDedupeTtlSeconds));
        if (!Boolean.TRUE.equals(firstDelivery)) {
            log.debug("Skipping duplicate webhook delivery eventType={} paymentIntentId={}", eventType, paymentIntentId);
            return;
        }

        String tenantIdRaw = metadata.path("tenantId").asText(null);
        String installmentIdRaw = metadata.path("installmentId").asText(null);

        if (tenantIdRaw == null || installmentIdRaw == null) {
            throw new StripeWebhookValidationException("Stripe webhook metadata is missing tenantId/installmentId");
        }

        UUID tenantId;
        UUID installmentId;
        try {
            tenantId = UUID.fromString(tenantIdRaw);
            installmentId = UUID.fromString(installmentIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new StripeWebhookValidationException("Stripe webhook metadata IDs are invalid", ex);
        }

        LoanInstallment installment = loanInstallmentRepository
                .findByIdAndTenantIdForUpdate(installmentId, tenantId)
                .orElseThrow(() -> new StripeWebhookValidationException("Installment not found for webhook metadata"));

        if (!paymentIntentId.equals(installment.getStripePaymentIntentId())) {
            log.warn("Webhook payment intent mismatch tenantId={} installmentId={} payloadPI={} dbPI={}",
                    tenantId, installmentId, paymentIntentId, installment.getStripePaymentIntentId());
        }

        switch (eventType) {
            case "payment_intent.succeeded" ->
                    ledgerUpdateService.handleSucceededPayment(tenantId, installmentId, paymentIntentId);
            case "payment_intent.payment_failed" ->
                    ledgerUpdateService.markPaymentFailed(
                            tenantId,
                            installmentId,
                            paymentIntentId,
                            "payment_failed",
                            extractFailureReason(objectNode, "Payment failed"),
                            maxRetryCount
                    );
            case "payment_intent.requires_action" ->
                    ledgerUpdateService.markPaymentFailed(
                            tenantId,
                            installmentId,
                            paymentIntentId,
                            "requires_action",
                            "3DS authentication required",
                            maxRetryCount
                    );
            case "payment_intent.canceled" ->
                    ledgerUpdateService.markPaymentFailed(
                            tenantId,
                            installmentId,
                            paymentIntentId,
                            "canceled",
                            extractFailureReason(objectNode, "Payment intent canceled"),
                            maxRetryCount
                    );
            default -> log.debug("Ignoring non-Flow6 Stripe webhook eventType={}", eventType);
        }
    }

    private String extractFailureReason(JsonNode objectNode, String fallback) {
        String message = objectNode.path("last_payment_error").path("message").asText(null);
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}
