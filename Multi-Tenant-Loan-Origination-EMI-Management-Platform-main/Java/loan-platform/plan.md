# PLAN.md — Production Architecture & Folder Structure

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Virtual Threads enabled) |
| Framework | Spring Boot 3.3.x |
| Security | Spring Security 6 + JWT + Role-Based |
| State Machine | Spring State Machine 3.x |
| AI | Spring AI 1.x + OpenAI GPT-4o |
| ORM | Spring Data JPA + Hibernate 6 |
| DB | PostgreSQL 16 (multi-tenant via schema-per-tenant) |
| Cache | Redis 7 (Lettuce client) |
| Messaging | Apache Kafka 3.7 |
| HTTP Client | Feign (OpenFeign) |
| Migration | Flyway 10 |
| Mapping | MapStruct 1.5 |
| Validation | Jakarta Bean Validation 3 |
| Testing | JUnit 5 + Mockito + AssertJ + Testcontainers + WireMock |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9 (multi-module) |
| Containerization | Docker + Docker Compose |
| Payment | Stripe Java SDK 24.x |

---

## Multi-Module Project Structure
```
loanplatform/
├── pom.xml                          # Parent POM
├── platform-api/                    # Main Spring Boot application
├── mambu-mock-service/              # Standalone fake Mambu service (port 8081)
├── payment-gateway-service/         # Stripe wrapper + mock fallback (port 8082)
├── shared-kernel/                   # Shared DTOs, exceptions, constants, utils
├── infrastructure/
│   ├── docker-compose.yml
│   ├── kafka/
│   ├── redis/
│   └── postgres/
│       └── init-schemas.sql
└── docs/
    ├── plans/
    └── sessions/
```

---

## platform-api Full Package Structure
```
platform-api/
└── src/
    ├── main/
    │   ├── java/com/loanplatform/
    │   │   ├── PlatformApiApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   ├── KafkaConfig.java
    │   │   │   ├── FeignConfig.java
    │   │   │   ├── FlywayConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── TenantContextConfig.java
    │   │   │   └── StateMachineConfig.java
    │   │   │
    │   │   ├── multitenancy/
    │   │   │   ├── TenantContext.java           # ThreadLocal tenant holder
    │   │   │   ├── TenantInterceptor.java       # HTTP header extractor
    │   │   │   ├── TenantAwareAspect.java       # AOP for @TenantAware
    │   │   │   ├── TenantAware.java             # Custom annotation
    │   │   │   └── TenantSchemaRouter.java      # Routes to tenant DB schema
    │   │   │
    │   │   ├── domain/
    │   │   │   │
    │   │   │   ├── tenant/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── Tenant.java
    │   │   │   │   │   ├── TenantStatus.java
    │   │   │   │   │   └── LoanProduct.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   ├── TenantRepository.java
    │   │   │   │   │   └── LoanProductRepository.java
    │   │   │   │   └── service/
    │   │   │   │       └── TenantService.java
    │   │   │   │
    │   │   │   ├── borrower/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── Borrower.java
    │   │   │   │   │   ├── KycDocument.java
    │   │   │   │   │   ├── KycStatus.java
    │   │   │   │   │   └── CreditProfile.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   ├── BorrowerRepository.java
    │   │   │   │   │   └── CreditProfileRepository.java
    │   │   │   │   └── service/
    │   │   │   │       └── BorrowerService.java
    │   │   │   │
    │   │   │   ├── loan/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── LoanApplication.java
    │   │   │   │   │   ├── LoanAccount.java
    │   │   │   │   │   ├── LoanState.java       # Enum: all FSM states
    │   │   │   │   │   ├── LoanEvent.java       # Enum: all FSM events
    │   │   │   │   │   ├── RepaymentSchedule.java
    │   │   │   │   │   ├── EmiInstallment.java
    │   │   │   │   │   └── LoanOffer.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   ├── LoanApplicationRepository.java
    │   │   │   │   │   ├── LoanAccountRepository.java
    │   │   │   │   │   └── RepaymentScheduleRepository.java
    │   │   │   │   └── service/
    │   │   │   │       ├── LoanApplicationService.java
    │   │   │   │       ├── EligibilityEngine.java
    │   │   │   │       ├── LoanAccountService.java
    │   │   │   │       └── RepaymentService.java
    │   │   │   │
    │   │   │   ├── document/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── LoanDocument.java
    │   │   │   │   │   └── DocumentStatus.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── LoanDocumentRepository.java
    │   │   │   │   └── service/
    │   │   │   │       └── DocumentService.java
    │   │   │   │
    │   │   │   ├── disbursement/
    │   │   │   │   ├── model/
    │   │   │   │   │   └── DisbursementRecord.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── DisbursementRepository.java
    │   │   │   │   └── service/
    │   │   │   │       └── DisbursementService.java
    │   │   │   │
    │   │   │   ├── payment/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── EmiPayment.java
    │   │   │   │   │   ├── PaymentStatus.java
    │   │   │   │   │   └── PrepaymentRecord.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   ├── EmiPaymentRepository.java
    │   │   │   │   │   └── PrepaymentRepository.java
    │   │   │   │   └── service/
    │   │   │   │       ├── EmiCollectionService.java
    │   │   │   │       └── PrepaymentService.java
    │   │   │   │
    │   │   │   ├── npa/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── NpaRecord.java
    │   │   │   │   │   └── RecoveryAction.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── NpaRepository.java
    │   │   │   │   └── service/
    │   │   │   │       ├── NpaFlaggingService.java
    │   │   │   │       └── RecoveryWorkflowService.java
    │   │   │   │
    │   │   │   └── analytics/
    │   │   │       ├── model/
    │   │   │       │   └── PortfolioMetrics.java
    │   │   │       └── service/
    │   │   │           └── AnalyticsService.java
    │   │   │
    │   │   ├── port/
    │   │   │   ├── inbound/
    │   │   │   │   ├── TenantUseCase.java
    │   │   │   │   ├── BorrowerUseCase.java
    │   │   │   │   ├── LoanApplicationUseCase.java
    │   │   │   │   ├── DocumentUseCase.java
    │   │   │   │   ├── DisbursementUseCase.java
    │   │   │   │   ├── PaymentUseCase.java
    │   │   │   │   ├── PrepaymentUseCase.java
    │   │   │   │   ├── NpaUseCase.java
    │   │   │   │   └── AnalyticsUseCase.java
    │   │   │   └── outbound/
    │   │   │       ├── MambuPort.java
    │   │   │       ├── StripePort.java
    │   │   │       ├── KafkaEventPort.java
    │   │   │       └── NotificationPort.java
    │   │   │
    │   │   ├── adapter/
    │   │   │   ├── inbound/
    │   │   │   │   ├── rest/
    │   │   │   │   │   ├── TenantController.java
    │   │   │   │   │   ├── BorrowerController.java
    │   │   │   │   │   ├── LoanApplicationController.java
    │   │   │   │   │   ├── DocumentController.java
    │   │   │   │   │   ├── DisbursementController.java
    │   │   │   │   │   ├── PaymentController.java
    │   │   │   │   │   ├── PrepaymentController.java
    │   │   │   │   │   ├── NpaController.java
    │   │   │   │   │   ├── AnalyticsController.java
    │   │   │   │   │   └── ChatbotController.java
    │   │   │   │   ├── webhook/
    │   │   │   │   │   └── StripeWebhookController.java
    │   │   │   │   └── kafka/
    │   │   │   │       ├── EmiCollectionListener.java
    │   │   │   │       └── NpaFlaggingListener.java
    │   │   │   └── outbound/
    │   │   │       ├── mambu/
    │   │   │       │   ├── MambuClient.java           # Feign client
    │   │   │       │   ├── MambuAdapter.java          # Implements MambuPort
    │   │   │       │   └── dto/                       # Mambu request/response DTOs
    │   │   │       ├── stripe/
    │   │   │       │   ├── StripeGatewayService.java
    │   │   │       │   └── StripeAdapter.java
    │   │   │       ├── kafka/
    │   │   │       │   └── KafkaEventAdapter.java
    │   │   │       └── notification/
    │   │   │           └── NotificationAdapter.java
    │   │   │
    │   │   ├── statemachine/
    │   │   │   ├── LoanStateMachineConfig.java
    │   │   │   ├── LoanStateMachineService.java
    │   │   │   ├── actions/
    │   │   │   │   ├── SubmitApplicationAction.java
    │   │   │   │   ├── ApproveAction.java
    │   │   │   │   ├── RejectAction.java
    │   │   │   │   ├── DisburseAction.java
    │   │   │   │   ├── PrepayAction.java
    │   │   │   │   ├── ForeCloseAction.java
    │   │   │   │   ├── OverdueAction.java
    │   │   │   │   ├── NpaAction.java
    │   │   │   │   └── SettleAction.java
    │   │   │   └── guards/
    │   │   │       ├── EligibilityGuard.java
    │   │   │       └── IdempotencyGuard.java
    │   │   │
    │   │   ├── ai/
    │   │   │   ├── ChatbotService.java
    │   │   │   ├── RagDocumentLoader.java
    │   │   │   ├── tools/
    │   │   │   │   ├── GetLoanStatusTool.java
    │   │   │   │   ├── GetRepaymentScheduleTool.java
    │   │   │   │   ├── SimulatePrepaymentTool.java
    │   │   │   │   ├── GetOverdueStatusTool.java
    │   │   │   │   └── GetNpaPortfolioTool.java
    │   │   │   └── prompt/
    │   │   │       ├── BorrowerSystemPrompt.java
    │   │   │       └── AdminSystemPrompt.java
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   └── response/
    │   │   │
    │   │   ├── mapper/
    │   │   │   └── (MapStruct mappers per domain)
    │   │   │
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── LoanPlatformException.java
    │   │   │   ├── TenantNotFoundException.java
    │   │   │   ├── BorrowerNotFoundException.java
    │   │   │   ├── LoanNotFoundException.java
    │   │   │   ├── DuplicatePaymentException.java
    │   │   │   └── MambuIntegrationException.java
    │   │   │
    │   │   └── util/
    │   │       ├── IdempotencyUtil.java
    │   │       ├── JwtUtil.java
    │   │       └── DateUtil.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           ├── V1__create_tenant_tables.sql
    │           ├── V2__create_borrower_tables.sql
    │           ├── V3__create_loan_tables.sql
    │           ├── V4__create_document_tables.sql
    │           ├── V5__create_payment_tables.sql
    │           ├── V6__create_npa_tables.sql
    │           └── V7__create_analytics_tables.sql
    │
    └── test/
        └── java/com/loanplatform/
            ├── unit/          # Pure unit tests
            ├── integration/   # @SpringBootTest + Testcontainers
            └── smoke/         # API smoke tests
```

---

## mambu-mock-service Package Structure
```
mambu-mock-service/
└── src/main/java/com/loanplatform/mambu/
    ├── MambuMockApplication.java
    ├── config/
    │   └── MambuMockConfig.java
    ├── controller/
    │   ├── MambuBranchController.java
    │   ├── MambuLoanProductController.java
    │   ├── MambuClientController.java
    │   ├── MambuLoanAccountController.java
    │   ├── MambuDisbursementController.java
    │   ├── MambuRepaymentController.java
    │   ├── MambuSimulationController.java
    │   ├── MambuNpaController.java
    │   └── MambuReportingController.java
    ├── store/
    │   └── InMemoryMambuStore.java     # ConcurrentHashMap-based store
    ├── model/
    │   ├── MambuBranch.java
    │   ├── MambuLoanProduct.java
    │   ├── MambuClient.java
    │   ├── MambuLoanAccount.java
    │   ├── MambuInstallment.java
    │   └── MambuRepayment.java
    └── util/
        └── MambuIdGenerator.java
```

---

## payment-gateway-service Package Structure
```
payment-gateway-service/
└── src/main/java/com/loanplatform/payment/
    ├── PaymentGatewayApplication.java
    ├── controller/
    │   ├── PaymentIntentController.java
    │   └── WebhookSimulatorController.java
    ├── service/
    │   └── StripePaymentService.java
    └── model/
        ├── PaymentIntentRequest.java
        └── PaymentIntentResponse.java
```

---

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `loan.application.submitted` | LoanApplicationService | DocumentService | Trigger doc collection |
| `loan.approved` | DocumentService | DisbursementService | Trigger disbursement prep |
| `loan.disbursed` | DisbursementService | NotificationAdapter | Notify borrower |
| `emi.due` | Scheduled batch | EmiCollectionListener | Trigger EMI collection |
| `emi.collected` | EmiCollectionService | ReconciliationService | Reconcile payment |
| `loan.overdue` | Batch job | NpaFlaggingListener | 90+ DPD check |
| `npa.flagged` | NpaFlaggingService | RecoveryWorkflowService | Start recovery |

---

## Redis Key Patterns

| Key | TTL | Purpose |
|-----|-----|---------|
| `idempotency:EMI:{loanId}:{emiNum}:{tenantId}` | 300s | Prevent duplicate EMI posting |
| `idempotency:DISB:{loanId}:{tenantId}` | 300s | Prevent duplicate disbursement |
| `npa:lock:{loanId}` | until admin override | Block NPA modification |
| `chat:session:{userId}:{sessionId}` | 1800s | AI chat history |
| `tenant:config:{tenantId}` | 3600s | Tenant config cache |
| `jwt:blacklist:{jti}` | token TTL | Invalidated tokens |

---

## Database Schema Strategy

- **Global schema:** `public` — tenants, users, audit_log
- **Tenant schema:** `tenant_{tenantId}` — all loan/borrower/payment tables
- Flyway manages migrations per schema
- `TenantSchemaRouter` sets schema on each request via `SET search_path`

---

## Flow Summary

| Flow | Module(s) | Key Integration |
|------|-----------|----------------|
| 1 | tenant, config | Mambu Branch + LoanProduct API |
| 2 | borrower, kyc | Mambu Client API |
| 3 | loan.application, eligibility | Mambu Simulation API |
| 4 | document, underwriting | Mambu Loan Approval API |
| 5 | disbursement | Mambu Disbursement API |
| 6 | payment, reconciliation | Stripe + Mambu Repayment API |
| 7 | payment.prepayment | Mambu Prepayment API |
| 8 | npa, recovery | Mambu NPA Flag API |
| 9 | analytics | Mambu Reporting API |
| 10 | statemachine | All Mambu APIs |
| 11 | ai, chatbot | Spring AI + OpenAI + all domain APIs |