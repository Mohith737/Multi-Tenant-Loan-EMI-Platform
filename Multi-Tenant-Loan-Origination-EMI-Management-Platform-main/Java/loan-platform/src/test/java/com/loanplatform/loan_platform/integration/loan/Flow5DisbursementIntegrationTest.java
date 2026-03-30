package com.loanplatform.loan_platform.integration.loan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.DisbursementStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.LoanOfferStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.service.DisbursementRetryScheduler;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.security.JwtTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Flow5DisbursementIntegrationTest {

    private static final String STRIPE_WEBHOOK_SECRET = "whsec_flow5_test_secret";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    private static final WireMockServer WIREMOCK = new WireMockServer(0);

    static {
        POSTGRES.start();
        REDIS.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.h2.console.enabled", () -> "false");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("mambu.base-url", WIREMOCK::baseUrl);
        registry.add("app.stripe.webhook-secret", () -> STRIPE_WEBHOOK_SECRET);
        registry.add("app.notification.email.enabled", () -> "false");
        registry.add("app.notification.sms.enabled", () -> "false");
    }

    @AfterAll
    static void tearDown() {
        WIREMOCK.stop();
        REDIS.stop();
        POSTGRES.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private LoanOfferRepository loanOfferRepository;
    @Autowired
    private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired
    private LoanStateHistoryRepository loanStateHistoryRepository;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private DisbursementRetryScheduler disbursementRetryScheduler;

    @SpyBean
    private BorrowerNotificationPort borrowerNotificationPort;
    @SpyBean
    private KafkaEventPort kafkaEventPort;

    @MockBean
    private StripeGatewayService stripeGatewayService;

    private UUID tenantId;
    private UUID borrowerA;
    private UUID borrowerB;

    @BeforeEach
    void setUp() {
        loanStateHistoryRepository.deleteAll();
        loanInstallmentRepository.deleteAll();
        loanOfferRepository.deleteAll();
        loanApplicationRepository.deleteAll();
        borrowerRepository.deleteAll();
        loanProductRepository.deleteAll();
        tenantRepository.deleteAll();
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        WIREMOCK.resetAll();

        tenantId = createTenant("Tenant Flow5", "tenant-flow5.loanos.dev");
        createLoanProduct(tenantId, "LP_FLOW5");
        borrowerA = createBorrower(tenantId, "borrower.a@flow5.dev", "+919876543210");
        borrowerB = createBorrower(tenantId, "borrower.b@flow5.dev", "+919111111111");

        when(stripeGatewayService.createDisbursementTransfer(any(), any(), any(), any())).thenReturn("tr_flow5_001");
    }

    @Test
    void fullHappyPath_disburse_schedulePersistedAndBorrowerNotified() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));
        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_1", "492500", "7500");
        stubMambuScheduleSuccess(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disbursementDate": "2026-03-04",
                                  "firstRepaymentDate": "2026-04-04",
                                  "notes": "Flow5 disbursement",
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE_REPAYMENT"))
                .andExpect(jsonPath("$.loanState").value("ACTIVE_REPAYMENT"))
                .andExpect(jsonPath("$.disbursementStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.mambuDisbursementTxnId").value("txn_flow5_1"))
                .andExpect(jsonPath("$.stripeTransferId").value("tr_flow5_001"))
                .andExpect(jsonPath("$.totalInstallments").value(2));

        assertThat(loanInstallmentRepository.countByTenantIdAndApplicationId(tenantId, application.getId())).isEqualTo(2);
        verify(borrowerNotificationPort, atLeastOnce()).onLoanDisbursed(eq(borrowerA), eq(tenantId), eq(application.getId()), any());
    }

    @Test
    void concurrentDisbursement_secondAttempt_returns409() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));
        String key = "idempotency:DISB:" + application.getId() + ":" + tenantId;
        stringRedisTemplate.opsForValue().set(key, "inflight", Duration.ofMinutes(5));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DISBURSEMENT_IN_PROGRESS"));
    }

    @Test
    void stripeTransferCreated_mambuFails_syncFlagSet_retrySucceeds() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));

        stubMambuDisbursementFailure(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MAMBU_INTEGRATION_ERROR"));

        LoanApplication failed = loanApplicationRepository.findByIdAndTenantId(application.getId(), tenantId).orElseThrow();
        assertThat(failed.isMambuDisburseSyncFailed()).isTrue();
        assertThat(failed.getDisbursementStatus()).isEqualTo(DisbursementStatus.FAILED);

        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_retry", "492500", "7500");
        stubMambuScheduleSuccess(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbursementStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.stripeTransferId").value("tr_flow5_001"));

        LoanApplication retried = loanApplicationRepository.findByIdAndTenantId(application.getId(), tenantId).orElseThrow();
        assertThat(retried.isMambuDisburseSyncFailed()).isFalse();
        assertThat(retried.getStatus()).isEqualTo(LoanApplicationStatus.ACTIVE_REPAYMENT);
    }

    @Test
    void scheduleFetchFails_retrySchedulerEventuallyPersists() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));

        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_sched_fail", "492500", "7500");
        stubMambuScheduleFailure(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_PERSISTENCE_ERROR"));

        LoanApplication failed = loanApplicationRepository.findByIdAndTenantId(application.getId(), tenantId).orElseThrow();
        assertThat(failed.isScheduleFetchFailed()).isTrue();
        assertThat(failed.getStatus()).isEqualTo(LoanApplicationStatus.DISBURSED);
        verify(borrowerNotificationPort, never()).onLoanDisbursed(eq(borrowerA), eq(tenantId), eq(application.getId()), any());

        stubMambuScheduleSuccess(application.getMambuLoanId());
        disbursementRetryScheduler.retryFailedSchedules();

        LoanApplication retried = loanApplicationRepository.findByIdAndTenantId(application.getId(), tenantId).orElseThrow();
        assertThat(retried.isScheduleFetchFailed()).isFalse();
        assertThat(retried.getStatus()).isEqualTo(LoanApplicationStatus.ACTIVE_REPAYMENT);
        assertThat(loanInstallmentRepository.countByTenantIdAndApplicationId(tenantId, application.getId())).isEqualTo(2);
    }

    @Test
    void transferFailed_webhook_rollbackMambuAndAlertAdmin() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));

        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_webhook", "492500", "7500");
        stubMambuScheduleSuccess(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isOk());

        stubMambuPatchSuccess(application.getMambuLoanId());

        String webhookPayload = """
                {
                  "type": "transfer.failed",
                  "data": {
                    "object": {
                      "id": "tr_flow5_001",
                      "metadata": {
                        "tenantId": "%s",
                        "applicationId": "%s"
                      },
                      "failure_message": "Transfer failed due to insufficient funds"
                    }
                  }
                }
                """.formatted(tenantId, application.getId());

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", stripeSignatureHeader(webhookPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        LoanApplication rolledBack = loanApplicationRepository.findByIdAndTenantId(application.getId(), tenantId).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(rolledBack.getLoanState()).isEqualTo(LoanLifecycleState.APPROVED);
        assertThat(rolledBack.getDisbursementStatus()).isEqualTo(DisbursementStatus.FAILED);
        assertThat(loanInstallmentRepository.countByTenantIdAndApplicationId(tenantId, application.getId())).isZero();

        WIREMOCK.verify(1, com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(urlEqualTo("/api/v2/loans/" + application.getMambuLoanId()))
                .withRequestBody(matchingJsonPath("$.loanState", equalTo("APPROVED"))));
        verify(kafkaEventPort, atLeastOnce()).publish(eq("loan.disbursement.failed"), eq(tenantId), eq(application.getId()), any());
    }

    @Test
    void expiredOffer_refreshedSilently_disbursementProceeds() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().minus(Duration.ofHours(2)));
        LoanOffer offerBefore = loanOfferRepository.findById(application.getSelectedOfferId()).orElseThrow();
        assertThat(offerBefore.getExpiresAt()).isBefore(Instant.now());

        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_expired_offer", "492500", "7500");
        stubMambuScheduleSuccess(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbursementStatus").value("COMPLETED"));

        LoanOffer offerAfter = loanOfferRepository.findById(application.getSelectedOfferId()).orElseThrow();
        assertThat(offerAfter.getExpiresAt()).isAfter(Instant.now());
        assertThat(offerAfter.getStatus()).isEqualTo(LoanOfferStatus.SELECTED);
    }

    @Test
    void borrowerCannotSeeOtherLoanSchedule_returns403() throws Exception {
        LoanApplication application = createApprovedApplicationWithOffer(borrowerA, Instant.now().plus(Duration.ofHours(2)));

        stubMambuDisbursementSuccess(application.getMambuLoanId(), "txn_flow5_isolation", "492500", "7500");
        stubMambuScheduleSuccess(application.getMambuLoanId());

        mockMvc.perform(post("/api/v1/loans/{applicationId}/disburse", application.getId())
                        .header("Authorization", "Bearer " + adminToken(tenantId, "admin_flow5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stripeDestinationAccountId": "acct_abc123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/loans/{applicationId}/schedule", application.getId())
                        .header("Authorization", "Bearer " + borrowerToken(tenantId, borrowerB, "borrower.b@flow5.dev")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_ISOLATION_VIOLATION"));
    }

    private UUID createTenant(String name, String domain) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name(name)
                .domain(domain)
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_" + name.replace(" ", "_").toLowerCase())
                .mambuBranchEncodedKey("branch_key_" + name.replace(" ", "_").toLowerCase())
                .planTier("STANDARD")
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private void createLoanProduct(UUID tenantId, String mambuProductKey) {
        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_" + mambuProductKey)
                .mambuProductKey(mambuProductKey)
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Personal Loan")
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(1000000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .currency("INR")
                        .build())
                .build();
        loanProductRepository.save(loanProduct);
    }

    private UUID createBorrower(UUID tenantId, String email, String mobile) {
        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Rahul")
                .lastName("Sharma")
                .email(email)
                .passwordHash("unused")
                .mobile(mobile)
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender("MALE")
                .panEncrypted("encrypted_pan_" + UUID.randomUUID())
                .aadhaarLastFour("1234")
                .address(BorrowerAddress.builder()
                        .line1("123 MG Road")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_" + UUID.randomUUID())
                .mambuClientKey("enc_cli_" + UUID.randomUUID())
                .build();
        return borrowerRepository.save(borrower).getId();
    }

    private LoanApplication createApprovedApplicationWithOffer(UUID borrowerId, Instant offerExpiry) {
        UUID applicationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID loanProductId = loanProductRepository.findAll().getFirst().getId();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(loanProductId)
                .loanProductKey("LP_FLOW5")
                .requestedAmount(BigDecimal.valueOf(500000))
                .requestedTenureMonths(36)
                .loanPurpose("HOME_RENOVATION")
                .status(LoanApplicationStatus.APPROVED)
                .loanState(LoanLifecycleState.APPROVED)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(780)
                .dtiRatioSnapshot(BigDecimal.valueOf(16.50))
                .monthlyIncomeSnapshot(BigDecimal.valueOf(120000))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(BigDecimal.valueOf(800000))
                .offersExpiresAt(offerExpiry)
                .selectedOfferId(offerId)
                .mambuLoanId("LN_FLOW5_" + UUID.randomUUID())
                .submittedAt(Instant.now().minus(Duration.ofDays(2)))
                .disbursementStatus(DisbursementStatus.PENDING)
                .build();

        loanApplicationRepository.save(application);

        LoanOffer offer = LoanOffer.builder()
                .id(offerId)
                .tenantId(tenantId)
                .applicationId(applicationId)
                .offerRank((short) 1)
                .tenureMonths(36)
                .principalAmount(BigDecimal.valueOf(500000))
                .emiAmount(BigDecimal.valueOf(17217.77))
                .totalInterest(BigDecimal.valueOf(119839.72))
                .effectiveAnnualRate(BigDecimal.valueOf(14.5))
                .processingFeeAmount(BigDecimal.valueOf(7500))
                .totalPayableAmount(BigDecimal.valueOf(619839.72))
                .expiresAt(offerExpiry)
                .status(LoanOfferStatus.SELECTED)
                .selectedAt(Instant.now().minus(Duration.ofDays(1)))
                .build();

        loanOfferRepository.save(offer);
        return application;
    }

    private String adminToken(UUID tenantId, String username) {
        return jwtTokenService.generateToken(username, java.util.List.of("TENANT_ADMIN"), tenantId, Map.of()).token();
    }

    private String borrowerToken(UUID tenantId, UUID borrowerId, String email) {
        return jwtTokenService.generateToken(email, java.util.List.of("BORROWER"), tenantId,
                Map.of("borrowerId", borrowerId.toString())).token();
    }

    private void stubMambuDisbursementSuccess(String loanId, String txnId, String amount, String fee) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/api/v2/loans/" + loanId + "/disbursement"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "encodedKey": "enc_txn_1",
                                  "id": "%s",
                                  "type": "DISBURSEMENT",
                                  "amount": %s,
                                  "fees": { "amount": %s },
                                  "valueDate": "2026-03-04"
                                }
                                """.formatted(txnId, amount, fee))));
    }

    private void stubMambuDisbursementFailure(String loanId) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/api/v2/loans/" + loanId + "/disbursement"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"Mambu down\"}")));
    }

    private void stubMambuScheduleSuccess(String loanId) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v2/loans/" + loanId + "/schedule"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "installments": [
                                    {
                                      "number": 1,
                                      "dueDate": "2026-04-04",
                                      "state": "PENDING",
                                      "principal": { "amount": { "value": 12551.10 }, "due": { "value": 12551.10 } },
                                      "interest": { "amount": { "value": 4666.67 }, "due": { "value": 4666.67 } },
                                      "totalDue": { "value": 17217.77 }
                                    },
                                    {
                                      "number": 2,
                                      "dueDate": "2026-05-04",
                                      "state": "PENDING",
                                      "principal": { "amount": { "value": 12700.00 }, "due": { "value": 12700.00 } },
                                      "interest": { "amount": { "value": 4517.77 }, "due": { "value": 4517.77 } },
                                      "totalDue": { "value": 17217.77 }
                                    }
                                  ]
                                }
                                """)));
    }

    private void stubMambuScheduleFailure(String loanId) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v2/loans/" + loanId + "/schedule"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"Schedule unavailable\"}")));
    }

    private void stubMambuPatchSuccess(String loanId) {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.put(urlEqualTo("/api/v2/loans/" + loanId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "accountState": "APPROVED"
                                }
                                """.formatted(loanId))));
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
}
