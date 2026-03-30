package com.loanplatform.loan_platform.domain.loan.service;

import com.fasterxml.jackson.databind.JsonNode;
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
public class PrepaymentWebhookService {

    private final PrepaymentService prepaymentService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.flow7.webhook-dedupe-ttl-seconds:86400}")
    private long webhookDedupeTtlSeconds;

    @Transactional(propagation = Propagation.REQUIRED)
    public void processEvent(String eventType, JsonNode payload) {
        JsonNode objectNode = payload.path("data").path("object");
        JsonNode metadata = objectNode.path("metadata");

        String paymentIntentId = objectNode.path("id").asText(null);
        String tenantIdRaw = metadata.path("tenantId").asText(null);
        String prepaymentRequestIdRaw = metadata.path("prepaymentRequestId").asText(null);

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new StripeWebhookValidationException("Stripe webhook payment intent id is missing");
        }
        if (tenantIdRaw == null || tenantIdRaw.isBlank() || prepaymentRequestIdRaw == null || prepaymentRequestIdRaw.isBlank()) {
            throw new StripeWebhookValidationException("Stripe webhook metadata missing tenantId/prepaymentRequestId");
        }

        UUID tenantId;
        UUID prepaymentRequestId;
        try {
            tenantId = UUID.fromString(tenantIdRaw);
            prepaymentRequestId = UUID.fromString(prepaymentRequestIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new StripeWebhookValidationException("Stripe webhook metadata IDs are invalid", ex);
        }

        String dedupeKey = "webhook:stripe:prepayment:" + paymentIntentId + ":" + eventType;
        Boolean firstDelivery = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", Duration.ofSeconds(webhookDedupeTtlSeconds));
        if (!Boolean.TRUE.equals(firstDelivery)) {
            log.debug("Skipping duplicate prepayment webhook eventType={} paymentIntentId={}", eventType, paymentIntentId);
            return;
        }

        switch (eventType) {
            case "payment_intent.succeeded" ->
                    prepaymentService.handlePaymentSucceeded(tenantId, prepaymentRequestId, paymentIntentId);
            case "payment_intent.payment_failed" ->
                    prepaymentService.markPaymentFailed(
                            tenantId,
                            prepaymentRequestId,
                            paymentIntentId,
                            "payment_failed",
                            extractFailureReason(objectNode, "Prepayment payment failed")
                    );
            case "payment_intent.canceled" ->
                    prepaymentService.markPaymentFailed(
                            tenantId,
                            prepaymentRequestId,
                            paymentIntentId,
                            "canceled",
                            extractFailureReason(objectNode, "Prepayment payment canceled")
                    );
            case "payment_intent.requires_action" ->
                    prepaymentService.markPaymentFailed(
                            tenantId,
                            prepaymentRequestId,
                            paymentIntentId,
                            "requires_action",
                            "Further customer authentication required"
                    );
            default -> log.debug("Ignoring non-Flow7 webhook eventType={}", eventType);
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
