package com.loanplatform.loan_platform.smoke;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.service.NpaService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Flow8NpaApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private NpaService npaService;
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
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MambuPort mambuPort;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private UUID tenantId;
    private String loanAccountId;

    @BeforeEach
    void setUp() {
        DatabaseCleanupHelper.clearDomainTables(jdbcTemplate);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(mambuPort.patchLoanState(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> MambuLoanAccountResponse.builder()
                        .id(invocation.getArgument(0, String.class))
                        .accountState(invocation.getArgument(1, String.class))
                        .build());

        tenantId = createTenant();
        UUID loanProductId = createLoanProduct(tenantId);
        UUID borrowerId = createBorrower(tenantId);
        loanAccountId = "LN_FLOW8_SMOKE_001";
        UUID applicationId = createLoanApplication(tenantId, borrowerId, loanProductId, loanAccountId);
        createInstallment(tenantId, borrowerId, applicationId, loanAccountId, LocalDate.now().minusDays(100));

        npaService.flagNpaLoans(LocalDate.now());
    }

    @Test
    void adminNpaEndpoints_smoke() throws Exception {
        String token = jwtTokenService.generateToken("tenant_admin_smoke", List.of("TENANT_ADMIN"), tenantId).token();

        mockMvc.perform(get("/api/v1/admin/npa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.npaloans[0].loanAccountId").value(loanAccountId));

        mockMvc.perform(post("/api/v1/admin/npa/{loanAccountId}/override", loanAccountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrideReason": "Manual settlement",
                                  "adminNote": "Smoke test override"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountId").value(loanAccountId))
                .andExpect(jsonPath("$.npaLockCleared").value(true));
    }

    private UUID createTenant() {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Tenant Flow8 Smoke")
                .domain("tenant-flow8-smoke.test")
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_flow8_smoke")
                .mambuBranchEncodedKey("branch_key_flow8_smoke")
                .stripeAccountId("acct_flow8_smoke")
                .planTier("STANDARD")
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private UUID createLoanProduct(UUID tenantId) {
        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_FLOW8_SMOKE")
                .mambuProductKey("LP_FLOW8_SMOKE")
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Flow8 Smoke Product")
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

    private UUID createBorrower(UUID tenantId) {
        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Smoke")
                .lastName("Borrower")
                .email("borrower.flow8.smoke@loanos.dev")
                .passwordHash("unused")
                .mobile("+919999999991")
                .dateOfBirth(LocalDate.of(1991, 5, 10))
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
                .build();
        return borrowerRepository.save(borrower).getId();
    }

    private UUID createLoanApplication(UUID tenantId, UUID borrowerId, UUID loanProductId, String mambuLoanId) {
        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(loanProductId)
                .loanProductKey("LP_FLOW8_SMOKE")
                .requestedAmount(new BigDecimal("500000"))
                .requestedTenureMonths(36)
                .loanPurpose("WORKING_CAPITAL")
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(745)
                .dtiRatioSnapshot(new BigDecimal("23.00"))
                .monthlyIncomeSnapshot(new BigDecimal("120000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("600000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now())
                .mambuLoanId(mambuLoanId)
                .build();
        return loanApplicationRepository.save(application).getId();
    }

    private UUID createInstallment(UUID tenantId, UUID borrowerId, UUID applicationId, String mambuLoanId, LocalDate dueDate) {
        LoanInstallment installment = LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId(mambuLoanId)
                .installmentNumber(1)
                .dueDate(dueDate)
                .principalAmount(new BigDecimal("9000.00"))
                .interestAmount(new BigDecimal("1200.00"))
                .totalDue(new BigDecimal("10200.00"))
                .status(LoanInstallmentStatus.OVERDUE)
                .retryCount(0)
                .mambuSyncPending(false)
                .waived(false)
                .build();
        return loanInstallmentRepository.save(installment).getId();
    }
}
