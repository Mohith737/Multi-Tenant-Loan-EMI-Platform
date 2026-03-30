package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.domain.loan.service.DisbursementService;
import com.loanplatform.loan_platform.domain.loan.service.EmiWebhookProcessingService;
import com.loanplatform.loan_platform.domain.loan.service.PrepaymentWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final DisbursementService disbursementService;
    private final EmiWebhookProcessingService emiWebhookProcessingService;
    private final PrepaymentWebhookService prepaymentWebhookService;
    private final ObjectMapper objectMapper;

    @Value("${app.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<java.util.Map<String, Object>> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String stripeSignature,
            @RequestBody String payload
    ) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.error("Stripe webhook secret is not configured; rejecting webhook");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("received", false, "error", "WEBHOOK_SECRET_NOT_CONFIGURED"));
        }

        Event event;
        JsonNode root;
        try {
            event = Webhook.constructEvent(payload, stripeSignature, stripeWebhookSecret);
            root = objectMapper.readTree(payload);
        } catch (SignatureVerificationException ex) {
            log.warn("Rejected Stripe webhook due to invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("received", false, "error", "INVALID_SIGNATURE"));
        } catch (Exception ex) {
            log.warn("Rejected Stripe webhook due to malformed payload", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("received", false, "error", "INVALID_PAYLOAD"));
        }

        String eventType = event.getType();
        if ("payment_intent.succeeded".equals(eventType)
                || "payment_intent.payment_failed".equals(eventType)
                || "payment_intent.requires_action".equals(eventType)
                || "payment_intent.canceled".equals(eventType)) {
            JsonNode metadata = root.path("data").path("object").path("metadata");
            String prepaymentRequestId = metadata.path("prepaymentRequestId").asText(null);
            CompletableFuture.runAsync(() -> {
                try {
                    if (prepaymentRequestId != null && !prepaymentRequestId.isBlank()) {
                        prepaymentWebhookService.processEvent(eventType, root);
                    } else {
                        emiWebhookProcessingService.processEvent(eventType, root);
                    }
                } catch (Exception ex) {
                    log.error("Stripe webhook async processing failed eventType={}", eventType, ex);
                }
            });
            return ResponseEntity.ok(java.util.Map.of("received", true));
        }

        if ("transfer.failed".equals(eventType) || "payout.failed".equals(eventType)) {
            JsonNode objectNode = root.path("data").path("object");
            String transferId = objectNode.path("id").asText(null);
            String failureReason = objectNode.path("failure_message").asText("Transfer failed");
            boolean handledByTransferId = transferId != null && !transferId.isBlank();

            if (handledByTransferId) {
                disbursementService.handleTransferFailedByTransferId(transferId, failureReason);
            } else {
                JsonNode metadata = objectNode.path("metadata");
                String tenantIdRaw = metadata.path("tenantId").asText(null);
                String applicationIdRaw = metadata.path("applicationId").asText(null);
                if (tenantIdRaw != null && applicationIdRaw != null) {
                    try {
                        UUID tenantId = UUID.fromString(tenantIdRaw);
                        UUID applicationId = UUID.fromString(applicationIdRaw);
                        disbursementService.handleTransferFailed(tenantId, applicationId, failureReason);
                    } catch (IllegalArgumentException ex) {
                        log.warn("Ignoring stripe webhook due to invalid metadata tenantId={} applicationId={}",
                                tenantIdRaw, applicationIdRaw);
                    }
                }
            }
        }

        return ResponseEntity.ok(java.util.Map.of("received", true));
    }
}
