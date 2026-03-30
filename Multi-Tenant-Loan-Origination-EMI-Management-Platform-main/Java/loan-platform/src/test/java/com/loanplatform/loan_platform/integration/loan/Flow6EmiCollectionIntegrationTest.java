package com.loanplatform.loan_platform.integration.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentResult;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.ReconciliationReportRepository;
import com.loanplatform.loan_platform.domain.loan.service.EmiCollectionService;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.security.JwtTokenService;
import com.loanplatform.loan_platform.testsupport.DatabaseCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.stripe.webhook-secret=whsec_flow6_test_secret",
        "app.flow6.scheduler.collection-cron=0 0 9 * * *",
        "app.flow6.scheduler.retry-cron=0 0 18 * * *"
})
class Flow6EmiCollectionIntegrationTest {

    private static final String STRIPE_WEBHOOK_SECRET = "whsec_flow6_test_secret";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private EmiCollectionService emiCollectionService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired
    private EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    @Autowired
    private ReconciliationReportRepository reconciliationReportRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StripeGatewayService stripeGatewayService;

    @MockBean
    private MambuPort mambuPort;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private UUID tenantId;
    private UUID borrowerA;
    private UUID borrowerB;
    private UUID applicationId;
    private UUID installmentId;

    @BeforeEach
    void setUp() {
        resetDatabaseState();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        tenantId = createTenant("tenant-flow6.test");
        UUID loanProductId = createLoanProduct(tenantId, "LP_FLOW6");
        borrowerA = createBorrower(tenantId, "borrower.a@flow6.dev", "+919111111111", "cus_A", "pm_A");
        borrowerB = createBorrower(tenantId, "borrower.b@flow6.dev", "+919222222222", "cus_B", "pm_B");
        applicationId = createLoanApplication(tenantId, borrowerA, loanProductId, "LN_FLOW6_001");
        installmentId = createInstallment(tenantId, borrowerA, applicationId, "LN_FLOW6_001", 1, LoanInstallmentStatus.PENDING);
    }

    @Test
    void emiDispatchPlusWebhookSuccess_happyPath_marksInstallmentPaid() throws Exception {
        when(stripeGatewayService.createEmiPaymentIntent(eq(tenantId), eq(installmentId), any()))
                .thenReturn(StripePaymentIntentResult.builder()
                        .paymentIntentId("pi_flow6_success")
                        .status("requires_confirmation")
                        .build());

        when(mambuPort.postRepayment(eq("LN_FLOW6_001"), any())).thenReturn(MambuRepaymentResponse.builder()
                .id("txn_flow6_1")
                .encodedKey("enc_txn_flow6_1")
                .amount(new BigDecimal("1000.00"))
                .build());

        emiCollectionService.dispatchDueInstallments(LocalDate.now(), false);

        LoanInstallment afterDispatch = loanInstallmentRepository.findById(installmentId).orElseThrow();
        assertThat(afterDispatch.getStatus()).isEqualTo(LoanInstallmentStatus.PROCESSING);
        assertThat(afterDispatch.getStripePaymentIntentId()).isEqualTo("pi_flow6_success");

        String webhookPayload = """
                {
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "pi_flow6_success",
                      "metadata": {
                        "tenantId": "%s",
                        "installmentId": "%s"
                      }
                    }
                  }
                }
                """.formatted(tenantId, installmentId);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", stripeSignatureHeader(webhookPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        waitUntilPaid(installmentId, Duration.ofSeconds(15), Duration.ofMillis(200));
        LoanInstallment afterWebhook = loanInstallmentRepository.findById(installmentId).orElseThrow();
        assertThat(afterWebhook.getStatus()).isEqualTo(LoanInstallmentStatus.PAID);
        assertThat(afterWebhook.getPaidDate()).isEqualTo(LocalDate.now());
        assertThat(afterWebhook.isMambuSyncPending()).isFalse();
    }

    @Test
    void webhookInvalidSignature_returns400() throws Exception {
        String webhookPayload = """
                {
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "pi_invalid_signature",
                      "metadata": {
                        "tenantId": "%s",
                        "installmentId": "%s"
                      }
                    }
                  }
                }
                """.formatted(tenantId, installmentId);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.received").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));
    }

    @Test
    void borrowerInstallments_otherBorrowerAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/loans/{applicationId}/installments", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken(tenantId, borrowerB, "borrower.b@flow6.dev")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_ISOLATION_VIOLATION"));
    }

    @Test
    void waiveInstallment_adminRequest_marksInstallmentWaived() throws Exception {
        LoanInstallment installment = loanInstallmentRepository.findById(installmentId).orElseThrow();
        installment.setStatus(LoanInstallmentStatus.RETRY_SCHEDULED);
        installment.setRetryCount(2);
        loanInstallmentRepository.save(installment);

        mockMvc.perform(post("/api/v1/admin/emi/{installmentId}/waive", installmentId)
                        .header("Authorization", "Bearer " + adminToken(tenantId, "tenant_admin_flow6"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Approved waiver for hardship"
                                }
                                """))
                .andExpect(status().isOk());

        LoanInstallment updated = loanInstallmentRepository.findById(installmentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LoanInstallmentStatus.WAIVED);
        assertThat(updated.isWaived()).isTrue();
        assertThat(updated.getWaivedReason()).isEqualTo("Approved waiver for hardship");
    }

    private UUID createTenant(String domain) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Tenant Flow6")
                .domain(domain)
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_flow6")
                .mambuBranchEncodedKey("branch_key_flow6")
                .stripeAccountId("acct_flow6")
                .planTier("STANDARD")
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private UUID createLoanProduct(UUID tenantId, String productKey) {
        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_" + productKey)
                .mambuProductKey(productKey)
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Flow6 Product")
                        .minLoanAmount(new BigDecimal("10000"))
                        .maxLoanAmount(new BigDecimal("2000000"))
                        .minTenureMonths(6)
                        .maxTenureMonths(84)
                        .annualInterestRate(new BigDecimal("14.5"))
                        .processingFeePercent(new BigDecimal("1.5"))
                        .currency("INR")
                        .build())
                .build();
        return loanProductRepository.save(loanProduct).getId();
    }

    private UUID createBorrower(UUID tenantId,
                                String email,
                                String mobile,
                                String stripeCustomerId,
                                String stripePaymentMethodId) {
        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Flow")
                .lastName("Borrower")
                .email(email)
                .passwordHash("unused")
                .mobile(mobile)
                .dateOfBirth(LocalDate.of(1991, 1, 10))
                .gender("MALE")
                .panEncrypted("enc_pan_" + UUID.randomUUID())
                .aadhaarLastFour("1234")
                .address(BorrowerAddress.builder()
                        .line1("123 Main St")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_" + UUID.randomUUID())
                .mambuClientKey("enc_cli_" + UUID.randomUUID())
                .stripeCustomerId(stripeCustomerId)
                .stripePaymentMethodId(stripePaymentMethodId)
                .build();
        return borrowerRepository.save(borrower).getId();
    }

    private UUID createLoanApplication(UUID tenantId, UUID borrowerId, UUID loanProductId, String mambuLoanId) {
        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(loanProductId)
                .loanProductKey("LP_FLOW6")
                .requestedAmount(new BigDecimal("500000"))
                .requestedTenureMonths(36)
                .loanPurpose("WORKING_CAPITAL")
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(760)
                .dtiRatioSnapshot(new BigDecimal("24.50"))
                .monthlyIncomeSnapshot(new BigDecimal("120000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("800000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now().minusSeconds(3600))
                .mambuLoanId(mambuLoanId)
                .build();
        return loanApplicationRepository.save(application).getId();
    }

    private UUID createInstallment(UUID tenantId,
                                   UUID borrowerId,
                                   UUID applicationId,
                                   String mambuLoanId,
                                   int installmentNumber,
                                   LoanInstallmentStatus status) {
        LoanInstallment installment = LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId(mambuLoanId)
                .installmentNumber(installmentNumber)
                .dueDate(LocalDate.now())
                .principalAmount(new BigDecimal("900.00"))
                .interestAmount(new BigDecimal("100.00"))
                .totalDue(new BigDecimal("1000.00"))
                .status(status)
                .retryCount(0)
                .build();
        return loanInstallmentRepository.save(installment).getId();
    }

    private String adminToken(UUID tenantId, String username) {
        return jwtTokenService.generateToken(username, java.util.List.of("TENANT_ADMIN"), tenantId, Map.of()).token();
    }

    private String borrowerToken(UUID tenantId, UUID borrowerId, String email) {
        return jwtTokenService.generateToken(email, java.util.List.of("BORROWER"), tenantId,
                Map.of("borrowerId", borrowerId.toString())).token();
    }

    private String stripeSignatureHeader(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;

        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                STRIPE_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        hmac.init(secretKeySpec);
        String signature = HexFormat.of().formatHex(hmac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }

    private void waitUntilPaid(UUID installmentId, Duration timeout, Duration pollInterval) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            LoanInstallment installment = loanInstallmentRepository.findById(installmentId).orElseThrow();
            if (installment.getStatus() == LoanInstallmentStatus.PAID) {
                return;
            }
            Thread.sleep(pollInterval.toMillis());
        }
        LoanInstallment latest = loanInstallmentRepository.findById(installmentId).orElseThrow();
        assertThat(latest.getStatus()).isEqualTo(LoanInstallmentStatus.PAID);
    }

    private void resetDatabaseState() {
        DatabaseCleanupHelper.clearDomainTables(jdbcTemplate);
    }
}
