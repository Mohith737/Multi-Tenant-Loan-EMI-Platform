# Gate 1 Plan - Flow 6: EMI Collection -> Payment Reconciliation -> Ledger Update

## 1. Feature Summary
Flow 6 automates daily EMI collection for active repayment loans by creating Stripe PaymentIntents off-session, then posting only successful collections to Mambu repayment APIs for ledger correctness. The flow is asynchronous and event-driven: scheduler dispatches attempts, webhook confirms payment outcome, and reconciliation verifies Stripe-vs-Mambu financial parity nightly. End-to-end idempotency is enforced with one shared key across Redis, Stripe idempotency header, and Mambu `externalId`.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.domain.tenant.model`
- `com.loanplatform.loan_platform.domain.tenant.repository`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.stripe`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.mambu.dto`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.mambu.controller` (mock contract + 200ms delay parity)
- `com.loanplatform.mambu.repository` (reconciliation query support)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V10__create_flow6_emi_collection_reconciliation_tables.sql` | Flow 6 schema changes: extend `loan_installments`, add `emi_payment_attempts`, add `reconciliation_reports`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/EmiPaymentAttempt.java` | Append-only audit entity for every EMI payment attempt/webhook outcome/Mambu posting state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/ReconciliationReport.java` | Tenant/day reconciliation aggregate entity for Stripe-vs-Mambu vs local totals. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/EmiPaymentAttemptStatus.java` | Enum for attempt lifecycle (`INITIATED`, `STRIPE_SUCCEEDED`, `STRIPE_FAILED`, `MAMBU_POSTED`, `MAMBU_POST_FAILED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/EmiPaymentAttemptRepository.java` | Repository for attempt dedupe checks, retry picks, and payment history queries. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/ReconciliationReportRepository.java` | Repository for reconciliation report storage and admin retrieval. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/EmiSchedulerJob.java` | 9AM scheduler to dispatch due EMIs across tenants with precondition and idempotency checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/EmiCollectionService.java` | Core Flow 6 orchestration: preconditions, PROCESSING mark, Stripe PaymentIntent creation, async dispatch. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LedgerUpdateService.java` | Handles confirmed success path: post repayment to Mambu, persist txn IDs, mark installment PAID. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/EmiWebhookProcessingService.java` | Async processing for Stripe webhook events and installment status transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/RetryJob.java` | 6PM scheduler for `RETRY_SCHEDULED` installments (`retryCount < 3`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/ReconciliationJob.java` | 11PM scheduler computing per-tenant discrepancy and creating reconciliation reports. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/MambuRepaymentSyncRetryJob.java` | Every 5-minute retry for Stripe-success but Mambu-post-failed (`mambuSyncPending = true`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/EmiCollectionUseCase.java` | Inbound contract for borrower/admin EMI and reconciliation APIs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/EmiCollectionController.java` | Borrower + tenant-admin Flow 6 APIs (installments, history, due/failed/overdue, waive, reconciliation). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/EmiWaiveRequest.java` | Waive API request DTO with mandatory reason and validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/BorrowerInstallmentScheduleResponse.java` | Borrower schedule view with Flow 6 status fields (`paidDate`, Stripe status). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PaymentHistoryResponse.java` | Borrower payment history response for PAID installments and audit references. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/AdminDueInstallmentsResponse.java` | Tenant admin due-today list response with filtering/pagination metadata. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/AdminFailedInstallmentsResponse.java` | Tenant admin failed/retry list response with retry counters and reasons. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/AdminOverdueInstallmentsResponse.java` | Tenant admin overdue list response with overdue metrics. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/ReconciliationReportResponse.java` | Reconciliation report API response DTO. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/EmiCollectionMapper.java` | MapStruct mapper between Flow 6 entities and response DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuRepaymentRequest.java` | DTO for `POST /api/v2/loans/{loanId}/repayments` payload (with `externalId`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuRepaymentResponse.java` | DTO for Mambu repayment response fields (`id`, `encodedKey`, `amount`, `externalId`, `valueDate`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/stripe/dto/StripePaymentIntentCommand.java` | Internal command model for PaymentIntent creation inputs + metadata. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/stripe/dto/StripePaymentIntentResult.java` | Internal result model for PaymentIntent creation outputs and state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/EmiCollectionPreconditionFailedException.java` | Structured exception for installment precondition failures. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/DuplicateEmiProcessingException.java` | Exception for duplicate/in-flight EMI collection attempts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/StripeWebhookValidationException.java` | Exception for invalid Stripe signature/payload handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/LedgerSyncException.java` | Exception for Mambu ledger posting failures after Stripe success. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/EmiCollectionServiceTest.java` | Unit tests for precondition checks, idempotency, and PaymentIntent dispatch behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/LedgerUpdateServiceTest.java` | Unit tests for Stripe-success -> Mambu-post -> PAID transitions and failure handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/EmiWebhookProcessingServiceTest.java` | Unit tests for webhook event matrix (`succeeded`, `payment_failed`, `requires_action`, `canceled`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/ReconciliationJobTest.java` | Unit tests for discrepancy calculations and alert trigger decisions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/statemachine/LoanFlow6StateMachineTest.java` | State machine unit tests for Flow 6 self-transitions/invalid transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow6EmiCollectionIntegrationTest.java` | End-to-end integration tests for due EMI collection, webhook, Mambu posting, retries, and tenant isolation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow6EmiApiSmokeTest.java` | Smoke tests for new Flow 6 borrower/admin/webhook APIs. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuRepaymentContractTest.java` | Contract tests for repayment schema shape and idempotency via `externalId`. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanInstallment.java` | Add Flow 6 columns/fields: `stripePaymentIntentId`, `stripePaymentStatus`, `paidDate`, `retryCount`, `lastRetryAt`, `paymentFailureReason`, `mambuSyncPending`, `waived`, `waivedReason`, `waivedAt`, `waivedBy`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanInstallmentStatus.java` | Extend enum with `PROCESSING`, `RETRY_SCHEDULED`, `REQUIRES_ACTION`, `FAILED`, `WAIVED` while retaining current values. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/Borrower.java` | Add `stripeCustomerId` and `stripePaymentMethodId` for off-session EMI charging preconditions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/model/Tenant.java` | Add `stripeAccountId` for tenant-level payment capability precondition. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanInstallmentRepository.java` | Add tenant-scoped due/retry/reconciliation queries and row-lock (`FOR UPDATE`) methods used by schedulers/webhook. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanApplicationRepository.java` | Add loan-state-filtered join query for active repayment precondition checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/repository/BorrowerRepository.java` | Add query projections for Stripe customer/payment method precondition checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/repository/TenantRepository.java` | Add query for active tenants with Stripe account configured. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/stripe/StripeGatewayService.java` | Add Flow 6 method to create PaymentIntent (`confirm=true`, `off_session=true`) with idempotency key and metadata for installment resolution. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/StripeWebhookController.java` | Extend to verify signature and accept `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.requires_action`, `payment_intent.canceled`; acknowledge `200` quickly and delegate async processing. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add `postRepayment(...)` and `getRepaymentsForDate(...)` contracts for ledger sync and reconciliation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign mappings for `POST /api/v2/loans/{loanId}/repayments` and transactions query endpoint used by reconciliation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Implement repayment and reconciliation adapter mappings for new Mambu DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kafka/KafkaEventAdapter.java` | Ensure Flow 6 events (`emi.collected`, `emi.failed`, `emi.overdue`) are published with structured payload logging. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleEvent.java` | Add Flow 6 internal lifecycle events (`EMI_COLLECTED`, `EMI_RETRY_EXHAUSTED`) for state-machine-governed audit transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Add `ACTIVE_REPAYMENT` self-transitions for Flow 6 events; reject invalid events outside repayment states. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Ensure Flow 6 events are resolved and invalid transition errors remain deterministic. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/config/SecurityConfig.java` | Keep webhook endpoint public but tighten handling notes; ensure new Flow 6 admin/borrower endpoints remain authenticated. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Map new Flow 6 exceptions with stable API error codes (`EMI_PRECONDITION_FAILED`, `DUPLICATE_EMI_PROCESSING`, `LEDGER_SYNC_FAILED`, `INVALID_STRIPE_WEBHOOK_SIGNATURE`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add Flow 6 scheduler/retry/redis/kafka topic settings and Stripe webhook/payment properties with env-variable backing. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Replace current minimal Flow 6 section with full borrower/admin/webhook request-response contracts and event semantics. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 6 status progression (`NOT STARTED` -> `IN PROGRESS` -> `COMPLETE`) at Gate 2/3 completion. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuRepaymentController.java` | Enforce exact repayment response schema consumed by platform and add deterministic 200ms delay on repayment endpoints. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/repository/MambuTransactionRepository.java` | Add date/range query methods for reconciliation total retrieval. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuMockFullLifecycleTest.java` | Extend lifecycle assertions for Flow 6 repayment idempotency and duplicate webhook safety behavior. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V10__create_flow6_emi_collection_reconciliation_tables.sql`

### Table: `loan_installments` (ALTER)
| Column | Type | Constraints / Notes |
|---|---|---|
| `stripe_payment_intent_id` | `VARCHAR(120)` | Nullable, latest PaymentIntent for installment |
| `stripe_payment_status` | `VARCHAR(40)` | Nullable, latest Stripe status/event snapshot |
| `paid_date` | `DATE` | Nullable, set only after Stripe success + Mambu post success |
| `retry_count` | `INTEGER` | `NOT NULL DEFAULT 0` |
| `last_retry_at` | `TIMESTAMP` | Nullable |
| `payment_failure_reason` | `VARCHAR(500)` | Nullable |
| `mambu_sync_pending` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `waived` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `waived_reason` | `VARCHAR(500)` | Nullable |
| `waived_by` | `UUID` | Nullable (tenant admin actor) |
| `waived_at` | `TIMESTAMP` | Nullable |

Indexes:
- `idx_loan_installments_tenant_due_status_retry` on (`tenant_id`, `due_date`, `status`, `retry_count`)
- `idx_loan_installments_tenant_status_due` on (`tenant_id`, `status`, `due_date`)
- `idx_loan_installments_stripe_pi` on (`stripe_payment_intent_id`)
- `idx_loan_installments_mambu_sync_pending` on (`mambu_sync_pending`, `status`)

### Table: `emi_payment_attempts` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `installment_id` | `UUID` | `NOT NULL`, FK -> `loan_installments(id)` |
| `application_id` | `UUID` | `NOT NULL` |
| `borrower_id` | `UUID` | `NOT NULL` |
| `attempt_number` | `INTEGER` | `NOT NULL` |
| `idempotency_key` | `VARCHAR(180)` | `NOT NULL` |
| `stripe_payment_intent_id` | `VARCHAR(120)` | Nullable |
| `stripe_status` | `VARCHAR(40)` | Nullable |
| `mambu_transaction_id` | `VARCHAR(120)` | Nullable |
| `mambu_transaction_key` | `VARCHAR(120)` | Nullable |
| `mambu_posted` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `status` | `VARCHAR(40)` | `NOT NULL` |
| `failure_reason` | `VARCHAR(500)` | Nullable |
| `attempted_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes and constraints:
- Unique index `uk_emi_attempt_tenant_installment_attempt` on (`tenant_id`, `installment_id`, `attempt_number`)
- Unique index `uk_emi_attempt_stripe_pi` on (`stripe_payment_intent_id`) where not null
- Index `idx_emi_attempt_tenant_attempted_at` on (`tenant_id`, `attempted_at`)
- Index `idx_emi_attempt_installment_mambu_posted` on (`installment_id`, `mambu_posted`)

### Table: `reconciliation_reports` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `report_date` | `DATE` | `NOT NULL` |
| `total_due_count` | `INTEGER` | `NOT NULL DEFAULT 0` |
| `total_collected_count` | `INTEGER` | `NOT NULL DEFAULT 0` |
| `total_failed_count` | `INTEGER` | `NOT NULL DEFAULT 0` |
| `total_amount_due` | `NUMERIC(19,2)` | `NOT NULL DEFAULT 0` |
| `total_amount_collected` | `NUMERIC(19,2)` | `NOT NULL DEFAULT 0` |
| `mambu_total_posted` | `NUMERIC(19,2)` | `NOT NULL DEFAULT 0` |
| `discrepancy_amount` | `NUMERIC(19,2)` | `NOT NULL DEFAULT 0` |
| `reconciled` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `reviewed_by` | `UUID` | Nullable |
| `reviewed_at` | `TIMESTAMP` | Nullable |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes and constraints:
- Unique index `uk_recon_tenant_report_date` on (`tenant_id`, `report_date`)
- Index `idx_recon_discrepancy` on (`reconciled`, `discrepancy_amount`)

## 6. API Endpoints (Method + Path + Purpose + Role)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `GET` | `/api/v1/loans/{applicationId}/installments` | Borrower loan schedule with payment status and Stripe/Mambu payment fields. | `BORROWER` (own loan only) |
| `GET` | `/api/v1/loans/{applicationId}/payment-history` | Borrower payment history from PAID installments and attempts. | `BORROWER` (own loan only) |
| `GET` | `/api/v1/admin/emi/due-today` | Tenant admin view of due installments with status filters and pagination. | `TENANT_ADMIN` |
| `GET` | `/api/v1/admin/emi/failed` | Tenant admin list of `RETRY_SCHEDULED` installments with retry metadata. | `TENANT_ADMIN` |
| `GET` | `/api/v1/admin/emi/overdue` | Tenant admin list of `OVERDUE` installments and overdue metrics. | `TENANT_ADMIN` |
| `POST` | `/api/v1/admin/emi/{installmentId}/waive` | Tenant admin waiver with mandatory reason and Mambu waiver synchronization. | `TENANT_ADMIN` |
| `GET` | `/api/v1/admin/reconciliation/{date}` | Fetch reconciliation report by date for current tenant. | `TENANT_ADMIN` |
| `POST` | `/api/v1/webhooks/stripe` | Accept Stripe events for EMI processing (signature verified; immediate ack). | Public endpoint (Stripe signature required) |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu Calls (Flow 6)
- `POST /api/v2/loans/{mambuLoanId}/repayments` (ledger posting after Stripe success)
- `GET /api/v2/loans/{mambuLoanId}/schedule` (optional verification/drift checks)
- `GET /api/v2/transactions?type=REPAYMENT&fromDate={date}&toDate={date}&loanId={mambuLoanId}` (reconciliation aggregation; if unavailable, add equivalent mock endpoint)

### Repayment Request Mapping
- `amount` <- `loan_installments.total_due`
- `date` <- webhook-confirmed payment date
- `notes` <- `EMI {installmentNumber} collection`
- `externalId` <- `idempotency:EMI:{mambuLoanId}:{emiNumber}:{tenantId}`
- `transactionDetails.transactionChannelId` <- `ONLINE_PAYMENT`

### Response Fields Persisted
- `id` -> `emi_payment_attempts.mambu_transaction_id`
- `encodedKey` -> `emi_payment_attempts.mambu_transaction_key`
- `amount` -> amount verification against local installment
- `externalId` -> idempotency audit

### Mambu Mock Service Changes
- Keep repayment idempotency by `externalId` and return existing txn on duplicate requests.
- Add deterministic `200ms` delay for repayment/reconciliation endpoints.
- Add/extend contract tests to lock response shape consumed by `MambuRepaymentResponse`.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `emi.collected` | Produce | Installment moved to `PAID` after Mambu success; triggers notifications/analytics/internal ledger. |
| `emi.failed` | Produce | Stripe payment failed or canceled; drives borrower/admin alerts. |
| `emi.overdue` | Produce | Retry exhausted (`retryCount >= 3`) and installment marked `OVERDUE`; Flow 8 NPA intake signal. |
| `emi.collection.dispatch` | Consume (optional internal) | Optional async work queue for webhook decoupling and scheduler fan-out. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `idempotency:EMI:{mambuLoanId}:{emiNumber}:{tenantId}` | `300s` | Primary idempotency guard before PaymentIntent creation and Mambu posting. |
| `emi:processing:lock:{tenantId}:{installmentId}` | `300s` | Prevent concurrent scheduler/retry processing for same installment row. |
| `emi:webhook:processed:{stripePaymentIntentId}:{eventType}` | `86400s` | Duplicate webhook event suppression. |
| `emi:mambu-sync-retry:{tenantId}:{installmentId}` | `3600s` | Retry backoff state for Stripe-success but Mambu-post-failed cases. |

## 10. Stripe Integration Points
- Use `PaymentIntent` for off-session recurring EMI collection.
- On attempt dispatch, call Stripe via `StripeGatewayService` with:
  - amount in minor units (`paise`)
  - `customer` = `borrower.stripeCustomerId`
  - `payment_method` = `borrower.stripePaymentMethodId`
  - `confirm=true`, `off_session=true`
  - metadata: `tenantId`, `applicationId`, `installmentId`, `emiNumber`, `mambuLoanId`, `idempotencyKey`
  - `Idempotency-Key` header = same Redis key
- Webhook event handling matrix:
  - `payment_intent.succeeded` -> async ledger update (Mambu repayment), then mark PAID
  - `payment_intent.payment_failed` -> mark `RETRY_SCHEDULED`, increment `retryCount`, publish `emi.failed`
  - `payment_intent.requires_action` -> mark `REQUIRES_ACTION`, notify borrower, no automatic retry
  - `payment_intent.canceled` -> mark `FAILED`, alert tenant admin
- Signature verification is mandatory; invalid signature returns `400` and payload is ignored.
- Webhook controller returns `200` quickly after verification; heavy processing is asynchronous.

## 11. State Machine Transitions Triggered
### Planned Flow 6 Transition Rules
- Normal EMI success/failure does not change macro loan lifecycle from `ACTIVE_REPAYMENT`.
- Add explicit self-transitions for audit and invariants:
  - `ACTIVE_REPAYMENT` --`EMI_COLLECTED`--> `ACTIVE_REPAYMENT`
  - `ACTIVE_REPAYMENT` --`EMI_RETRY_EXHAUSTED`--> `ACTIVE_REPAYMENT`
- `emi.overdue` event is emitted to Flow 8; any future NPA lifecycle transition will happen via state machine there, not by direct DB mutation.

## 12. Security (Roles, Tenant Isolation)
- Borrower APIs require JWT + `ROLE_BORROWER` + strict ownership check (`tenantId`, `borrowerId`, `applicationId`).
- Tenant admin APIs require JWT + `ROLE_TENANT_ADMIN`; all queries scoped to JWT `tenantId`.
- Webhook endpoint remains unauthenticated for JWT but must verify `Stripe-Signature` before processing.
- Service methods handling Flow 6 business logic are `@TenantAware` and log `tenantId`, `loanId/applicationId`, `requestId` at entry/exit.
- Waive endpoint writes auditable actor references (`waivedBy`, `waivedAt`, reason) and blocks cross-tenant installment access.

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Duplicate charging from retries/restarts | Financial and compliance risk due to duplicate borrower debits. | Redis idempotency + Stripe idempotency key + installment `PROCESSING` lock + webhook dedupe key. |
| Stripe success but Mambu post failure | Cash collected but ledger unsynced (critical accounting drift). | `mambu_sync_pending=true`, append attempt row, retry job every 5 min, admin alert after threshold. |
| Webhook replay/out-of-order delivery | Can cause duplicate posting or wrong status transitions. | Signature verification + processed-event Redis key + PaymentIntent-based idempotent attempt resolution. |
| Tenant isolation breach in admin queries | Cross-tenant financial data exposure. | Tenant-scoped repository methods, method-level authorization, integration tests proving tenant A/B separation. |
| Reconciliation false positives due to timezone cutoffs | Incorrect discrepancy alerts at day boundary. | Use tenant-aware timezone boundaries (IST default), explicit report date windowing, deterministic date conversion tests. |
| Contract drift between platform and mambu mock | Runtime mapping failures on repayment sync/reconciliation. | Dedicated DTOs + contract tests + mandatory 200ms delay simulation parity. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-6-emi-collection-payment-reconciliation-ledger-update.md`
- [x] All required Gate 1 sections filled (a-m)
- [ ] Human approval pending
