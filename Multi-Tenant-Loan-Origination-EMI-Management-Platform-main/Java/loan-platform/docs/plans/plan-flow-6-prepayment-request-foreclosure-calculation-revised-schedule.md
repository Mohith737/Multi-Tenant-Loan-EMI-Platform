# Gate 1 Plan - Flow 6: Prepayment Request -> Foreclosure Calculation -> Revised Schedule

## 1. Feature Summary
This flow enables borrower-driven lump-sum prepayment in two modes: partial prepayment (loan remains active with revised schedule) and full foreclosure (loan closes after final settlement). The platform must calculate charges, collect payment via Stripe, call Mambu early repayment/prepayment APIs, and persist a full audit trail including revised schedule snapshots.

Note: In `AGENTS.md`, this business capability is listed under Flow 7 (Prepayment & Foreclosure). This document is labeled as Flow 6 to match the current request and planned implementation batch.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.inbound.webhook`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.mambu.dto`
- `com.loanplatform.loan_platform.adapter.outbound.stripe`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.mambu.controller` (mock parity for early repayment contract)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V11__create_flow6_prepayment_foreclosure_tables.sql` | Add Flow 6 prepayment/foreclosure persistence tables and indexes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentRequest.java` | Aggregate for borrower prepayment intent, quote snapshot, selected strategy, and final status. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentType.java` | Enum for `PARTIAL` and `FULL`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentOption.java` | Enum for partial strategy: `REDUCE_TENURE_KEEP_EMI`, `REDUCE_EMI_KEEP_TENURE`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentStatus.java` | Lifecycle enum (`QUOTE_GENERATED`, `PAYMENT_PENDING`, `PAYMENT_CONFIRMED`, `MAMBU_UPDATED`, `COMPLETED`, `FAILED`, `EXPIRED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/ForeclosureQuote.java` | Persisted quote fields (principal, accrued interest, penalty, total payable, validity). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/RevisedScheduleSnapshot.java` | Snapshot entity for Mambu returned revised schedule after successful partial prepayment. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/PrepaymentRequestRepository.java` | Tenant-scoped repository for borrower and admin prepayment lookups. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/ForeclosureQuoteRepository.java` | Repository for quote retrieval and expiry validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/RevisedScheduleSnapshotRepository.java` | Repository for latest revised schedule snapshot per request/loan. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/PrepaymentService.java` | Core orchestration for simulate/execute flows, idempotency checks, Stripe + Mambu coordination. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/ForeclosureService.java` | Full foreclosure calculation validation, closure update handling, NOC trigger. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/PrepaymentWebhookService.java` | Async handling for Stripe success/failure webhook outcomes for prepayment intents. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/PrepaymentUseCase.java` | Inbound contract for borrower prepayment APIs and admin visibility APIs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/PrepaymentSimulationRequest.java` | Borrower request DTO for preview (`amount`, `type`, `option`, `requestedDate`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/PrepaymentExecutionRequest.java` | Borrower execution DTO including quote/reference and idempotency key. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentSimulationResponse.java` | Response with revised schedule options for partial and payoff breakdown for full. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentExecutionResponse.java` | Response for accepted/processed execution with transaction IDs and resulting state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentAdminViewResponse.java` | Tenant admin read-only prepayment/foreclosure activity payload. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/PrepaymentMapper.java` | MapStruct mapper for request/response and entity projections. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/PrepaymentQuoteExpiredException.java` | Exception for stale quote execution attempts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/InvalidPrepaymentOptionException.java` | Exception when borrower selection is incompatible with prepayment type. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/ForeclosureCalculationException.java` | Exception wrapper for quote mismatch or invalid closure conditions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/PrepaymentServiceTest.java` | Unit tests for simulate/execute idempotent prepayment behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/ForeclosureServiceTest.java` | Unit tests for foreclosure amount composition and closure transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow6PrepaymentIntegrationTest.java` | Integration tests for borrower partial/full prepayment happy and error paths, tenant isolation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow6PrepaymentApiSmokeTest.java` | API smoke tests for all new Flow 6 endpoints. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/PrepaymentController.java` | Implement/extend borrower simulate + execute endpoints with validation and response contracts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/AnalyticsController.java` | Add tenant admin read-only prepayment activity endpoint for dashboard aggregation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/StripeWebhookController.java` | Handle prepayment webhook outcomes alongside existing events with fast ack + async processing. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign mappings for early repayment preview and revised schedule retrieval if missing. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Map prepayment preview/execute and closure-related responses into domain models. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add outbound contracts for preview early repayment, post prepayment, and fetch revised schedule. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/stripe/StripeGatewayService.java` | Add prepayment-specific PaymentIntent creation using borrower idempotency key and metadata. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kafka/KafkaEventAdapter.java` | Publish prepayment completion/foreclosure closure/NOC events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplication.java` | Store prepayment/foreclosure summary fields (`foreclosedAt`, `foreclosureAmount`, `lastPrepaymentAt`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Add transitions for partial prepayment and foreclosure closure. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Route new flow events through state machine only (no direct state mutation). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add stable error mapping for quote expiry, invalid option, and foreclosure validation errors. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add prepayment quote TTL, retry config, and webhook feature toggles via env-backed settings. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add/update full Flow 6 prepayment + foreclosure request/response contracts and error scenarios. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Track requested flow progress state updates after Gate 2 and Gate 3 completion. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuLoanAccountController.java` | Ensure preview early repayment and closure-related contract fields exactly match platform DTO expectations. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuRepaymentController.java` | Support idempotent lump-sum posting behavior for prepayment and foreclosure with deterministic 200ms delay. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuMockFullLifecycleTest.java` | Extend lifecycle contract tests for partial prepayment schedule revision and foreclosure closure state. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V11__create_flow6_prepayment_foreclosure_tables.sql`

### Table: `prepayment_requests` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `borrower_id` | `UUID` | `NOT NULL` |
| `prepayment_type` | `VARCHAR(20)` | `NOT NULL` (`PARTIAL`, `FULL`) |
| `prepayment_option` | `VARCHAR(40)` | Nullable for `FULL`; required for `PARTIAL` |
| `requested_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `quote_id` | `UUID` | `NOT NULL` |
| `idempotency_key` | `VARCHAR(180)` | `NOT NULL` |
| `stripe_payment_intent_id` | `VARCHAR(120)` | Nullable |
| `stripe_status` | `VARCHAR(40)` | Nullable |
| `mambu_transaction_id` | `VARCHAR(120)` | Nullable |
| `status` | `VARCHAR(40)` | `NOT NULL` |
| `failure_reason` | `VARCHAR(500)` | Nullable |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes and constraints:
- Unique index `uk_prepayment_idempotency` on (`tenant_id`, `idempotency_key`)
- Index `idx_prepayment_tenant_loan_created` on (`tenant_id`, `loan_application_id`, `created_at`)
- Index `idx_prepayment_tenant_status` on (`tenant_id`, `status`)

### Table: `foreclosure_quotes` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `principal_outstanding` | `NUMERIC(19,2)` | `NOT NULL` |
| `accrued_interest` | `NUMERIC(19,2)` | `NOT NULL` |
| `penalty_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `total_payable` | `NUMERIC(19,2)` | `NOT NULL` |
| `quote_generated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `quote_expires_at` | `TIMESTAMP` | `NOT NULL` |
| `mambu_reference` | `VARCHAR(120)` | Nullable |

Indexes and constraints:
- Index `idx_foreclosure_quote_tenant_loan_expiry` on (`tenant_id`, `loan_application_id`, `quote_expires_at`)

### Table: `revised_schedule_snapshots` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `prepayment_request_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `snapshot_version` | `INTEGER` | `NOT NULL DEFAULT 1` |
| `schedule_payload_json` | `TEXT` | `NOT NULL` (sanitized Mambu schedule snapshot) |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes and constraints:
- Index `idx_revised_schedule_tenant_request` on (`tenant_id`, `prepayment_request_id`)
- Unique index `uk_revised_schedule_request_version` on (`prepayment_request_id`, `snapshot_version`)

### Table: `loan_applications` (ALTER)
Columns:
- `last_prepayment_at` (`TIMESTAMP`, nullable)
- `foreclosed_at` (`TIMESTAMP`, nullable)
- `foreclosure_amount` (`NUMERIC(19,2)`, nullable)
- `closure_reason` (`VARCHAR(100)`, nullable)

Indexes:
- Index `idx_loan_app_foreclosed_at` on (`tenant_id`, `foreclosed_at`)

## 6. API Endpoints (Method + Path + Purpose + Role)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/v1/loans/{loanAccountId}/prepayment/simulate` | Generate prepayment/foreclosure quote and revised schedule preview from Mambu. | `BORROWER` (owner only) |
| `POST` | `/api/v1/loans/{loanAccountId}/prepayment/execute` | Execute borrower-selected partial prepayment or full foreclosure with Stripe collection. | `BORROWER` (owner only) |
| `GET` | `/api/v1/loans/{loanAccountId}/prepayment/{requestId}` | Borrower reads current request status, payment status, and resulting schedule/closure details. | `BORROWER` (owner only) |
| `GET` | `/api/v1/admin/prepayments` | Tenant admin read-only portfolio view of prepayment/foreclosure activity. | `TENANT_ADMIN` |
| `GET` | `/api/v1/loans/{loanAccountId}/noc` | Download/generate NOC metadata for completed foreclosure. | `BORROWER` (owner only), `TENANT_ADMIN` (same tenant read) |
| `POST` | `/api/v1/webhooks/stripe` | Process prepayment payment outcomes (`succeeded`, `payment_failed`, `canceled`) asynchronously. | Public endpoint with Stripe signature verification |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu Calls
- `GET /api/v2/loans/{loanId}/preview-early-repayment` for quote/revised schedule preview.
- `POST /api/v2/loans/{loanId}/repayments` to post partial/full prepayment ledger transaction.
- `GET /api/v2/loans/{loanId}/schedule` to fetch revised schedule after partial prepayment.
- `GET /api/v2/loans/{loanId}` to verify final account status (`CLOSED_OBLIGATIONS_MET`) after full foreclosure.

### Request/Response Mapping
- Foreclosure amount composition:
  - `principal_outstanding` + `accrued_interest` + `prepayment_penalty` = `total_payable`
- Partial prepayment options:
  - `REDUCE_TENURE_KEEP_EMI`
  - `REDUCE_EMI_KEEP_TENURE`
- Persisted response fields:
  - `transactionId`, `encodedKey`, `valueDate`, `newSchedule`, `accountState`

### Mambu Mock Service Changes
- Ensure preview response includes both partial options and full-closure quote details.
- Enforce idempotency by `externalId` for prepayment repayment posting.
- Add deterministic 200ms delay on early repayment preview and repayment posting endpoints.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.prepayment.requested` | Produce | Audit and downstream notification trigger when borrower starts request. |
| `loan.prepayment.completed` | Produce | Partial prepayment completed and revised schedule persisted. |
| `loan.foreclosed` | Produce | Foreclosure completed and account closed. |
| `loan.noc.generated` | Produce | NOC generation completed for borrower communication/archive. |
| `loan.prepayment.failed` | Produce | Failure event for payment, quote expiry, or Mambu update issues. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `idempotency:PREPAY:{loanId}:{requestId}:{tenantId}` | `300s` | Prevent duplicate partial prepayment processing. |
| `idempotency:FORECLOSE:{loanId}:{requestId}:{tenantId}` | `300s` | Prevent duplicate foreclosure execution. |
| `prepayment:quote:{tenantId}:{loanId}:{quoteId}` | `900s` | Quote cache + expiry validation guard. |
| `prepayment:lock:{tenantId}:{loanId}` | `300s` | Block concurrent prepayment operations on same loan. |
| `webhook:stripe:prepayment:{paymentIntentId}:{eventType}` | `86400s` | Webhook replay/out-of-order suppression. |

## 10. Stripe Integration Points
- Use `PaymentIntent` for borrower lump-sum charge (partial/full prepayment).
- `amount` must match accepted quote total (`requested_amount` for partial; `total_payable` for foreclosure).
- Metadata: `tenantId`, `loanAccountId`, `borrowerId`, `prepaymentRequestId`, `prepaymentType`, `prepaymentOption`, `idempotencyKey`.
- Use same idempotency key in Redis lock and Stripe `Idempotency-Key` header.
- Webhook matrix:
  - `payment_intent.succeeded` -> post repayment to Mambu -> persist outcome -> publish completion event.
  - `payment_intent.payment_failed` -> mark request `FAILED` and publish `loan.prepayment.failed`.
  - `payment_intent.canceled` -> mark request `FAILED` and unblock new request.
- Full foreclosure path must not mark loan closed locally until Stripe success and Mambu confirms closure state.

## 11. State Machine Transitions Triggered
- `ACTIVE_REPAYMENT` --`PREPAY_PARTIAL_CONFIRMED`--> `ACTIVE_REPAYMENT`
- `ACTIVE_REPAYMENT` --`FORECLOSURE_CONFIRMED`--> `FORECLOSED`
- `FORECLOSED` --`GENERATE_NOC`--> `FORECLOSED` (audit self-transition)

Transition rules:
- Any state change must be performed through `LoanStateMachineService`.
- Quote generation and payment-intent creation do not change macro lifecycle state.

## 12. Security (Roles, Tenant Isolation)
- Borrower APIs require JWT `ROLE_BORROWER` and strict ownership checks (`tenantId`, `borrowerId`, `loanAccountId`).
- Tenant admin has read-only access to tenant-scoped prepayment analytics/activity endpoints.
- No admin endpoint allows execution/cancellation of borrower prepayment requests.
- Webhook endpoint remains JWT-public but requires valid `Stripe-Signature`.
- Service methods must be `@TenantAware`; all repository queries include `tenant_id` filters.
- Logs must include `tenantId`, `loanId`, `requestId`, and `paymentIntentId` (no raw PAN/Aadhaar/PCI details).

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Quote staleness between simulation and execution | Accrued interest can change; borrower may be under/over-charged. | Quote TTL enforcement (`15 min`), strict amount match at execution, regenerate quote on expiry. |
| Duplicate execution during retries/network timeouts | Can double charge borrower and double post to Mambu. | Redis idempotency + Stripe idempotency + Mambu `externalId` dedupe + loan-level lock. |
| Foreclosure marked closed before external confirmation | Creates accounting/legal mismatch and invalid NOC issuance. | Close only after Stripe success and Mambu status confirms `CLOSED_OBLIGATIONS_MET`. |
| Revised schedule drift after partial prepayment | Borrower/app shows schedule inconsistent with Mambu ledger. | Persist Mambu response snapshot and always re-fetch latest schedule post posting. |
| Tenant isolation leak in admin portfolio views | Cross-tenant financial data exposure. | Tenant-scoped repository methods and integration tests proving A/B data separation. |
| Borrower confusion between partial options | Wrong option selection changes cashflow significantly. | Explicit option enum, validation, and response preview for both scenarios before execution. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-6-prepayment-request-foreclosure-calculation-revised-schedule.md`
- [x] All required Gate 1 sections filled (a-m)
- [ ] Human approval pending
