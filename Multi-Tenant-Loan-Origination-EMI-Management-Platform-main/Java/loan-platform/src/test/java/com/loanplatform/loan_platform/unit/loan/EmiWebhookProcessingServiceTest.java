package com.loanplatform.loan_platform.unit.loan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.service.EmiWebhookProcessingService;
import com.loanplatform.loan_platform.domain.loan.service.LedgerUpdateService;
import com.loanplatform.loan_platform.exception.StripeWebhookValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmiWebhookProcessingServiceTest {

    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;

    @Mock
    private LedgerUpdateService ledgerUpdateService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmiWebhookProcessingService emiWebhookProcessingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emiWebhookProcessingService, "maxRetryCount", 3);
        ReflectionTestUtils.setField(emiWebhookProcessingService, "webhookDedupeTtlSeconds", 86400L);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void processEvent_missingPaymentIntentId_throwsValidationException() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "data": {
                    "object": {
                      "metadata": {
                        "tenantId": "8a3b0f4e-9e6a-4c5c-9b4f-7fef6108c781",
                        "installmentId": "7ae125dd-f102-4683-abda-6525d31ab86c"
                      }
                    }
                  }
                }
                """);
        assertThatThrownBy(() -> emiWebhookProcessingService.processEvent("payment_intent.succeeded", payload))
                .isInstanceOf(StripeWebhookValidationException.class)
                .hasMessageContaining("payment intent id is missing");

        verify(ledgerUpdateService, never()).handleSucceededPayment(any(), any(), anyString());
    }

    @Test
    void processEvent_duplicateWebhookDelivery_skipsProcessing() throws Exception {
        JsonNode payload = objectMapper.readTree(basePayload("payment_intent_id", UUID.randomUUID(), UUID.randomUUID(), null));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        emiWebhookProcessingService.processEvent("payment_intent.succeeded", payload);

        verify(loanInstallmentRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
        verify(ledgerUpdateService, never()).handleSucceededPayment(any(), any(), anyString());
    }

    @Test
    void processEvent_succeededEvent_callsLedgerUpdateService() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        JsonNode payload = objectMapper.readTree(basePayload("pi_success_1", tenantId, installmentId, null));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId))
                .thenReturn(Optional.of(LoanInstallment.builder()
                        .id(installmentId)
                        .tenantId(tenantId)
                        .stripePaymentIntentId("pi_success_1")
                        .build()));

        emiWebhookProcessingService.processEvent("payment_intent.succeeded", payload);

        verify(ledgerUpdateService).handleSucceededPayment(tenantId, installmentId, "pi_success_1");
    }

    @Test
    void processEvent_paymentFailedEvent_marksRetryScheduled() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        JsonNode payload = objectMapper.readTree(basePayload("pi_fail_1", tenantId, installmentId, "Card declined"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId))
                .thenReturn(Optional.of(LoanInstallment.builder()
                        .id(installmentId)
                        .tenantId(tenantId)
                        .stripePaymentIntentId("pi_fail_1")
                        .build()));

        emiWebhookProcessingService.processEvent("payment_intent.payment_failed", payload);

        verify(ledgerUpdateService).markPaymentFailed(
                tenantId,
                installmentId,
                "pi_fail_1",
                "payment_failed",
                "Card declined",
                3
        );
    }

    @Test
    void processEvent_requiresActionEvent_marksRequiresAction() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        JsonNode payload = objectMapper.readTree(basePayload("pi_action_1", tenantId, installmentId, null));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId))
                .thenReturn(Optional.of(LoanInstallment.builder()
                        .id(installmentId)
                        .tenantId(tenantId)
                        .stripePaymentIntentId("pi_action_1")
                        .build()));

        emiWebhookProcessingService.processEvent("payment_intent.requires_action", payload);

        verify(ledgerUpdateService).markPaymentFailed(
                tenantId,
                installmentId,
                "pi_action_1",
                "requires_action",
                "3DS authentication required",
                3
        );
    }

    @Test
    void processEvent_canceledEvent_marksFailed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        JsonNode payload = objectMapper.readTree(basePayload("pi_cancel_1", tenantId, installmentId, "Cancelled by user"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId))
                .thenReturn(Optional.of(LoanInstallment.builder()
                        .id(installmentId)
                        .tenantId(tenantId)
                        .stripePaymentIntentId("pi_cancel_1")
                        .build()));

        emiWebhookProcessingService.processEvent("payment_intent.canceled", payload);

        verify(ledgerUpdateService).markPaymentFailed(
                tenantId,
                installmentId,
                "pi_cancel_1",
                "canceled",
                "Cancelled by user",
                3
        );
    }

    @Test
    void processEvent_missingMetadata_throwsValidationException() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "data": {
                    "object": {
                      "id": "pi_missing_metadata"
                    }
                  }
                }
                """);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> emiWebhookProcessingService.processEvent("payment_intent.succeeded", payload))
                .isInstanceOf(StripeWebhookValidationException.class)
                .hasMessageContaining("metadata is missing");
    }

    @Test
    void processEvent_invalidMetadataUuid_throwsValidationException() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "data": {
                    "object": {
                      "id": "pi_bad_uuid",
                      "metadata": {
                        "tenantId": "bad-tenant-id",
                        "installmentId": "bad-installment-id"
                      }
                    }
                  }
                }
                """);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> emiWebhookProcessingService.processEvent("payment_intent.succeeded", payload))
                .isInstanceOf(StripeWebhookValidationException.class)
                .hasMessageContaining("metadata IDs are invalid");
    }

    @Test
    void processEvent_unhandledEventType_doesNothing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();

        JsonNode payload = objectMapper.readTree(basePayload("pi_ignore", tenantId, installmentId, null));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId))
                .thenReturn(Optional.of(LoanInstallment.builder()
                        .id(installmentId)
                        .tenantId(tenantId)
                        .stripePaymentIntentId("pi_ignore")
                        .build()));

        emiWebhookProcessingService.processEvent("payment_intent.processing", payload);

        verify(ledgerUpdateService, never()).handleSucceededPayment(any(), any(), anyString());
        verify(ledgerUpdateService, never()).markPaymentFailed(any(), any(), anyString(), anyString(), anyString(), anyInt());
    }

    private String basePayload(String paymentIntentId, UUID tenantId, UUID installmentId, String failureMessage) {
        String errorNode = failureMessage == null
                ? ""
                : "\"last_payment_error\": {\"message\": \"" + failureMessage + "\"},";

        return """
                {
                  "data": {
                    "object": {
                      "id": "%s",
                      %s
                      "metadata": {
                        "tenantId": "%s",
                        "installmentId": "%s"
                      }
                    }
                  }
                }
                """.formatted(paymentIntentId, errorNode, tenantId, installmentId);
    }
}
