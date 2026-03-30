# Gate 1 Plan - Flow 7: Prepayment Request -> Foreclosure Calculation -> Revised Schedule

## 1. Feature Summary
Flow 7 is fully borrower-driven: borrower requests partial prepayment or full foreclosure, platform calculates payable amount, Stripe collects funds, and Mambu recalculates the schedule (partial) or closes the account (full). Tenant admin has read-only visibility in dashboard/reporting and does not approve or trigger the transaction.

Business scenarios covered:
- Partial prepayment example: outstanding `400000`, borrower pays `100000` -> outstanding `300000`; borrower chooses either `REDUCE_TENURE_KEEP_EMI` or `REDUCE_EMI_KEEP_TENURE`; Mambu returns revised schedule.
- Full foreclosure example: principal `400000` + accrued interest `4100` + penalty `8000` -> total `412100`; on payment success Mambu updates account to `CLOSED_OBLIGATIONS_MET` and platform issues NOC metadata.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.mambu.dto`
- `com.loanplatform.loan_platform.adapter.outbound.stripe`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.mambu.controller` (mock contract parity)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V11__create_flow7_prepayment_foreclosure_tables.sql` | Create Flow 7 tables for prepayment requests, foreclosure quotes, and revised schedule snapshots. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentRequest.java` | Aggregate capturing borrower request, strategy, quote, payment, and execution status. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentType.java` | Enum for `PARTIAL`, `FULL`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentOption.java` | Enum for partial options: `REDUCE_TENURE_KEEP_EMI`, `REDUCE_EMI_KEEP_TENURE`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/PrepaymentStatus.java` | Request lifecycle enum (`QUOTE_GENERATED`, `PAYMENT_PENDING`, `PAYMENT_CONFIRMED`, `MAMBU_UPDATED`, `COMPLETED`, `FAILED`, `EXPIRED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/ForeclosureQuote.java` | Persistent quote breakdown (principal, accrued interest, penalty, total, validity window). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/RevisedScheduleSnapshot.java` | Snapshot of Mambu-calculated revised schedule after partial prepayment. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/PrepaymentRequestRepository.java` | Tenant-scoped borrower/admin query operations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/ForeclosureQuoteRepository.java` | Quote retrieval and expiry validation queries. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/RevisedScheduleSnapshotRepository.java` | Access latest revised schedule snapshot per request/loan. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/PrepaymentService.java` | Orchestrates simulate + execute, Stripe collection, and Mambu sync. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/ForeclosureService.java` | Handles full-closure amount checks and final closure flow. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/PrepaymentWebhookService.java` | Processes Stripe webhook outcomes asynchronously and idempotently. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/PrepaymentUseCase.java` | Inbound contract for borrower prepayment and admin read APIs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/PrepaymentSimulationRequest.java` | Validate simulation input (`type`, `amount`, `option`, `requestedDate`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/PrepaymentExecutionRequest.java` | Validate execution input (quote reference + idempotency key). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentSimulationResponse.java` | Return partial option previews and full foreclosure payable breakdown. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentExecutionResponse.java` | Return execution status and payment/Mambu references. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/PrepaymentAdminViewResponse.java` | Tenant admin portfolio activity payload (read-only). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/PrepaymentMapper.java` | MapStruct entity <-> DTO mapping for Flow 7. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/PrepaymentQuoteExpiredException.java` | Structured error for stale quote execution. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/InvalidPrepaymentOptionException.java` | Error for incompatible type/option combinations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/ForeclosureCalculationException.java` | Error for payable mismatch/invalid closure attempt. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/PrepaymentServiceTest.java` | Unit coverage for simulation, execution, and idempotency rules. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/ForeclosureServiceTest.java` | Unit coverage for full closure amount and state transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow7PrepaymentIntegrationTest.java` | Integration coverage for happy and edge paths plus tenant isolation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow7PrepaymentApiSmokeTest.java` | Smoke test for Flow 7 APIs and payload shapes. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/PrepaymentController.java` | Implement/align borrower simulate + execute + status APIs for Flow 7. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/AnalyticsController.java` | Add tenant admin read-only prepayment/foreclosure activity endpoint. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/StripeWebhookController.java` | Route prepayment webhook events to async Flow 7 handler. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add/confirm Feign mappings for early repayment preview, repayment posting, and closure verification. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Implement DTO mapping for revised schedule and closure response fields. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add methods for preview early repayment, execute prepayment, fetch revised schedule/account status. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/stripe/StripeGatewayService.java` | Add Flow 7 PaymentIntent call path with idempotency metadata. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kafka/KafkaEventAdapter.java` | Publish Flow 7 events (`loan.prepayment.completed`, `loan.foreclosed`, `loan.noc.generated`, failures). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplication.java` | Add fields for `lastPrepaymentAt`, `foreclosedAt`, `foreclosureAmount`, `closureReason`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Add Flow 7 events/transitions for partial prepayment and foreclosure closure. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Ensure all Flow 7 lifecycle updates happen only via state machine events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add API error codes for quote expiry, option mismatch, foreclosure mismatch. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add quote TTL, retry, and webhook settings via env-backed properties. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add/align Flow 7 request/response and error contracts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 7 tracker status on Gate 2/Gate 3 completion. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuLoanAccountController.java` | Align early repayment preview response schema and closure state fields. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuRepaymentController.java` | Ensure idempotent prepayment posting behavior and 200ms delay parity. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuMockFullLifecycleTest.java` | Add contract assertions for partial schedule revision and foreclosure closure path. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V11__create_flow7_prepayment_foreclosure_tables.sql`

### Table: `prepayment_requests` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `borrower_id` | `UUID` | `NOT NULL` |
| `prepayment_type` | `VARCHAR(20)` | `NOT NULL` (`PARTIAL`, `FULL`) |
| `prepayment_option` | `VARCHAR(40)` | Nullable for `FULL`, required for `PARTIAL` |
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

Indexes:
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

Indexes:
- Index `idx_foreclosure_quote_tenant_loan_expiry` on (`tenant_id`, `loan_application_id`, `quote_expires_at`)

### Table: `revised_schedule_snapshots` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `prepayment_request_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `snapshot_version` | `INTEGER` | `NOT NULL DEFAULT 1` |
| `schedule_payload_json` | `TEXT` | `NOT NULL` |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes:
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

## 6. API Endpoints (Method + Path + Purpose)
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/loans/{loanAccountId}/prepayment/simulate` | Generate quote and revised schedule preview for partial/full request. |
| `POST` | `/api/v1/loans/{loanAccountId}/prepayment/execute` | Execute borrower-selected partial prepayment or full foreclosure. |
| `GET` | `/api/v1/loans/{loanAccountId}/prepayment/{requestId}` | Get request/payment/Mambu status and resulting schedule or closure output. |
| `GET` | `/api/v1/admin/prepayments` | Tenant admin read-only activity view for dashboard. |
| `GET` | `/api/v1/loans/{loanAccountId}/noc` | Get NOC metadata/content for foreclosed loan. |
| `POST` | `/api/v1/webhooks/stripe` | Receive prepayment payment outcome events. |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu Calls
- `GET /api/v2/loans/{loanId}/preview-early-repayment`
- `POST /api/v2/loans/{loanId}/repayments`
- `GET /api/v2/loans/{loanId}/schedule`
- `GET /api/v2/loans/{loanId}` (verify closure state)

### Request/Response Mapping
- Foreclosure total: `principal_outstanding + accrued_interest + prepayment_penalty`
- Partial options passed to Mambu:
  - `REDUCE_TENURE_KEEP_EMI`
  - `REDUCE_EMI_KEEP_TENURE`
- Persist response fields:
  - repayment `id`, `encodedKey`, `valueDate`
  - revised schedule snapshot
  - final account state (`CLOSED_OBLIGATIONS_MET` for full closure)

### Mock Service Requirement
- Maintain idempotency by `externalId` on repayment posting.
- Add deterministic 200ms delay on early repayment preview and repayment endpoints.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.prepayment.requested` | Produce | Borrower initiated prepayment/foreclosure request. |
| `loan.prepayment.completed` | Produce | Partial prepayment posted and revised schedule stored. |
| `loan.foreclosed` | Produce | Full closure completed and account status updated. |
| `loan.noc.generated` | Produce | NOC generated and available to borrower. |
| `loan.prepayment.failed` | Produce | Failure path for payment, quote, or Mambu sync issues. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `idempotency:PREPAY:{loanId}:{requestId}:{tenantId}` | `300s` | Duplicate guard for partial prepayment execution. |
| `idempotency:FORECLOSE:{loanId}:{requestId}:{tenantId}` | `300s` | Duplicate guard for foreclosure execution. |
| `prepayment:quote:{tenantId}:{loanId}:{quoteId}` | `900s` | Quote expiry validation cache. |
| `prepayment:lock:{tenantId}:{loanId}` | `300s` | Concurrency lock per loan during execution. |
| `webhook:stripe:prepayment:{paymentIntentId}:{eventType}` | `86400s` | Replay and out-of-order event suppression. |

## 10. Stripe Integration Points (if payment-related)
- Create PaymentIntent for borrower lump-sum payment after quote acceptance.
- Amount rules:
  - Partial: accepted `requested_amount`
  - Full: quote `total_payable`
- Metadata: `tenantId`, `loanAccountId`, `borrowerId`, `prepaymentRequestId`, `prepaymentType`, `prepaymentOption`, `idempotencyKey`.
- Webhook behavior:
  - `payment_intent.succeeded` -> post to Mambu -> mark completed.
  - `payment_intent.payment_failed` -> mark failed/retry eligibility.
  - `payment_intent.canceled` -> mark failed and unlock.
- Loan closure only after Stripe success plus Mambu closure confirmation.

## 11. State Machine Transitions Triggered
- `ACTIVE_REPAYMENT` --`PREPAY_PARTIAL_CONFIRMED`--> `ACTIVE_REPAYMENT`
- `ACTIVE_REPAYMENT` --`FORECLOSURE_CONFIRMED`--> `FORECLOSED`
- `FORECLOSED` --`GENERATE_NOC`--> `FORECLOSED`

Rules:
- All lifecycle mutations via `LoanStateMachineService`.
- No direct DB status writes for macro lifecycle transitions.

## 12. Security (Roles, tenant isolation)
- Borrower endpoints require `ROLE_BORROWER` and ownership checks (`tenantId`, `borrowerId`, `loanAccountId`).
- Tenant admin can only read same-tenant activity and cannot trigger borrower actions.
- Webhook is public for JWT but requires valid Stripe signature.
- All service methods in Flow 7 use tenant-scoped repository queries and `@TenantAware`.
- Logs include `tenantId`, `loanId`, `requestId`, `paymentIntentId`; never log sensitive PII.

## 13. Risk flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Quote stale at execution time | Accrued interest changes daily; wrong amount can be charged. | 15-minute quote TTL + strict amount revalidation. |
| Duplicate charging/posting | Financial and accounting risk. | Redis idempotency + Stripe idempotency + Mambu `externalId` dedupe. |
| Premature closure | Invalid legal/accounting state if closure marked before Mambu confirmation. | Mark `FORECLOSED` only after confirmed Mambu `CLOSED_OBLIGATIONS_MET`. |
| Revised schedule mismatch | Borrower-facing schedule can drift from Mambu truth. | Persist Mambu schedule snapshot and fetch latest on read. |
| Cross-tenant admin visibility leak | Data privacy breach. | Tenant-filtered queries + integration isolation tests. |
| Wrong borrower option selection | Incorrect cashflow expectations. | Preview both options clearly before execution; validate option+type pair. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-7-prepayment-request-foreclosure-calculation-revised-schedule.md`
- [x] All required sections filled
- [ ] Human approval pending
