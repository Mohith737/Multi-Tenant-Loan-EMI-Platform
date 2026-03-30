# Gate 1 Plan - Flow 5: Loan Disbursement -> Repayment Schedule Generation -> Borrower Notification

## 1. Feature Summary
Flow 5 starts after Flow 4 approval, when a tenant admin initiates disbursement for an `APPROVED` loan application that already has `mambuLoanId`. The platform triggers Mambu disbursement (`POST /api/v2/loans/{loanId}/disbursement`), fetches the generated repayment schedule (`GET /api/v2/loans/{loanId}/schedule`), persists installments locally, and only then sends borrower notification/events. The flow is idempotent via Redis, tenant-scoped end-to-end, and keeps lifecycle transitions controlled by Spring State Machine.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.mambu.dto`
- `com.loanplatform.loan_platform.adapter.outbound.notification`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.mambu.controller` (mock contract alignment)
- `com.loanplatform.mambu.repository` (mock schedule/disbursement persistence checks)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V9__create_flow5_disbursement_and_installments.sql` | Add Flow 5 schema: disbursement metadata on `loan_applications` and local `loan_installments` table. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/DisbursementStatus.java` | Enum for disbursement process tracking (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanInstallment.java` | JPA entity for persisted repayment schedule installments per tenant/application. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanInstallmentStatus.java` | Local installment status enum (`PENDING`, `PAID`, `OVERDUE`, `FAILED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanInstallmentRepository.java` | Tenant-aware repository for schedule reads/persistence and due-date lookups. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/DisbursementService.java` | Flow 5 orchestration: preconditions, idempotency lock, Mambu disbursement, transitions, notification/event publish. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/RepaymentScheduleService.java` | Fetch and persist Mambu schedule with upsert/idempotent behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/DisbursementRetryScheduler.java` | Retry failed schedule persistence/disbursement sync paths with bounded retries. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/DisbursementUseCase.java` | Inbound Flow 5 use case contract for disburse/status/schedule APIs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/LoanDisbursementRequest.java` | Admin disbursement request DTO (`disbursementDate`, optional `firstRepaymentDate`, `notes`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanDisbursementResponse.java` | Response DTO for disbursement initiation/result payload. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/DisbursementStatusResponse.java` | Admin status DTO with disbursement metadata and retry state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanScheduleResponse.java` | Borrower/admin schedule DTO wrapper and installment list model. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/DisbursementMapper.java` | MapStruct mapper for `LoanApplication` + `LoanInstallment` to Flow 5 response DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/LoanDisbursementController.java` | Flow 5 REST endpoints for admin disbursement and borrower/admin schedule retrieval. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanDisbursementRequest.java` | Typed Mambu request DTO for disbursement call. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanDisbursementResponse.java` | Typed Mambu response DTO for disbursement transaction fields. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuRepaymentScheduleResponse.java` | Typed Mambu schedule response DTO for installment parsing and persistence. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/DisbursementPreconditionFailedException.java` | Exception for explicit precondition violations with error code details. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/DisbursementInProgressException.java` | Exception for duplicate requests blocked by Redis idempotency/lock. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/RepaymentSchedulePersistenceException.java` | Exception for schedule fetch/persist failure scenarios. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/DisbursementServiceTest.java` | Unit tests for Flow 5 orchestration, idempotency, state transitions, and failure paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/RepaymentScheduleServiceTest.java` | Unit tests for Mambu schedule mapping/upsert and installment persistence logic. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/statemachine/LoanFlow5StateMachineTest.java` | Unit tests for Flow 5 transition happy/error paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow5DisbursementIntegrationTest.java` | Integration tests for disbursement -> schedule persistence -> notification path and edge cases. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow5DisbursementApiSmokeTest.java` | API smoke tests for all new Flow 5 endpoints. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuDisbursementScheduleContractTest.java` | Contract tests for disbursement and schedule payload shape parity with platform DTO consumption. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplication.java` | Add Flow 5 columns mapped fields (`disbursementStatus`, `mambuDisbursementTxnId`, `netDisbursedAmount`, `processingFeeCharged`, `firstRepaymentDate`, `disbursedAt`, failure flags/reason). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleState.java` | Add lifecycle states required by Flow 5 (`DISBURSED`, `ACTIVE_REPAYMENT`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleEvent.java` | Add Flow 5 events (`DISBURSE`, `ACTIVATE_REPAYMENT`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanApplicationRepository.java` | Add tenant-scoped lock query for approved apps and status filters used in disbursement orchestration and retries. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add outbound contracts: `disburseLoan(...)` and `getRepaymentSchedule(...)`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign mappings for `POST /api/v2/loans/{loanId}/disbursement` and `GET /api/v2/loans/{loanId}/schedule`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Implement Flow 5 request/response mapping for new Mambu operations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/BorrowerNotificationPort.java` | Add borrower notification contract for disbursement + schedule-ready communication. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/notification/BorrowerNotificationAdapter.java` | Implement new Flow 5 notification method with tenant/application context logging. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Add transitions: `APPROVED --DISBURSE--> DISBURSED`, `DISBURSED --ACTIVATE_REPAYMENT--> ACTIVE_REPAYMENT`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Ensure Flow 5 events resolve correctly and invalid transitions fail deterministically. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add error mappings for Flow 5 exceptions (`409`, `422`, `502`/`500`) with stable error codes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/config/SecurityConfig.java` | Ensure Flow 5 endpoint route coverage remains authenticated; webhook/public exclusions unchanged. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add Flow 5 config entries for Redis TTLs, Kafka topic names, retry intervals, and disbursement defaults. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Update Flow 5 endpoint contract examples and response schema to match applicationId-based APIs + local schedule model. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 5 status progression (`NOT STARTED` -> `IN PROGRESS` -> `COMPLETE`) during Gate 2/Gate 3. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuDisbursementController.java` | Ensure exact response shape consumed by platform DTOs and add required 200ms delay simulation. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuRepaymentController.java` | Ensure schedule endpoint response field parity and add 200ms delay simulation for `GET /schedule`. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuMockFullLifecycleTest.java` | Extend assertions for Flow 5 response fields consumed by platform (`amount`, `fees.amount`, installment structure). |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V9__create_flow5_disbursement_and_installments.sql`

### Table: `loan_applications` (ALTER)
| Column | Type | Constraints / Notes |
|---|---|---|
| `disbursement_status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING'` (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`) |
| `mambu_disbursement_txn_id` | `VARCHAR(120)` | nullable |
| `net_disbursed_amount` | `NUMERIC(19,2)` | nullable |
| `processing_fee_charged` | `NUMERIC(19,2)` | nullable |
| `disbursed_at` | `TIMESTAMP` | nullable |
| `first_repayment_date` | `DATE` | nullable |
| `schedule_fetch_failed` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `mambu_disburse_sync_failed` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `disbursement_failure_reason` | `VARCHAR(500)` | nullable |
| `schedule_persisted_at` | `TIMESTAMP` | nullable |

Indexes:
- `idx_loan_applications_tenant_disbursement_status` on (`tenant_id`, `disbursement_status`)
- `idx_loan_applications_tenant_disbursed_at` on (`tenant_id`, `disbursed_at`)
- `idx_loan_applications_schedule_fetch_failed` on (`schedule_fetch_failed`, `status`)

### Table: `loan_installments` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL`, FK -> `tenants(id)` |
| `application_id` | `UUID` | `NOT NULL`, FK -> `loan_applications(id)` |
| `borrower_id` | `UUID` | `NOT NULL`, FK -> `borrowers(id)` |
| `mambu_loan_id` | `VARCHAR(120)` | `NOT NULL` |
| `installment_number` | `INTEGER` | `NOT NULL` |
| `due_date` | `DATE` | `NOT NULL` |
| `principal_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `interest_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `total_due` | `NUMERIC(19,2)` | `NOT NULL` |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING'` |
| `mambu_installment_state` | `VARCHAR(30)` | nullable (snapshot of Mambu installment state) |
| `last_paid_date` | `DATE` | nullable |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes and constraints:
- Unique index `uk_loan_installments_app_number` on (`application_id`, `installment_number`)
- `idx_loan_installments_tenant_due_status` on (`tenant_id`, `due_date`, `status`)
- `idx_loan_installments_tenant_borrower` on (`tenant_id`, `borrower_id`)
- `idx_loan_installments_tenant_mambu_loan` on (`tenant_id`, `mambu_loan_id`)

## 6. API Endpoints (Method + Path + Purpose + Role)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/v1/loans/{applicationId}/disburse` | Initiate Flow 5 disbursement for approved loan, call Mambu disbursement, fetch+persist schedule, trigger events/notifications. | `TENANT_ADMIN` |
| `GET` | `/api/v1/loans/{applicationId}/disbursement-status` | Fetch disbursement processing/result metadata and retry state for operations dashboard. | `TENANT_ADMIN` |
| `GET` | `/api/v1/loans/{applicationId}/schedule` | Return locally persisted repayment schedule for borrower self-view and tenant support view. | `BORROWER`, `TENANT_ADMIN` |
| `GET` | `/api/v1/loans/{applicationId}/disbursement-details` | Return disbursement summary (`disbursedAmount`, `disbursedAt`, `firstRepaymentDate`, installments count). | `BORROWER`, `TENANT_ADMIN` |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu Calls (Flow 5 Mandatory)
- `POST /api/v2/loans/{mambuLoanId}/disbursement`
- `GET /api/v2/loans/{mambuLoanId}/schedule`

### Planned Request Mapping (Platform -> Mambu)
- `notes` <- admin request notes
- `disbursementDate` <- request date or `LocalDate.now()` fallback
- `firstRepaymentDate` <- validated override if provided, else +1 month rule
- `externalId` <- `DISB:{applicationId}:{tenantId}`
- `transactionDetails.transactionChannelId` <- `BANK_TRANSFER`

### Planned Response Fields Consumed
Disbursement response:
- `id` -> `loan_applications.mambu_disbursement_txn_id`
- `amount` -> `loan_applications.net_disbursed_amount`
- `fees.amount` -> `loan_applications.processing_fee_charged`
- `valueDate` -> `loan_applications.disbursed_at` / audit context

Schedule response (for each installment):
- `number` -> `loan_installments.installment_number`
- `dueDate` -> `loan_installments.due_date`
- `state` -> `loan_installments.mambu_installment_state`
- `principal.amount.value` -> `loan_installments.principal_amount`
- `interest.amount.value` -> `loan_installments.interest_amount`
- `principal.due.value + interest.due.value` (or `totalDue.value` if provided) -> `loan_installments.total_due`

### Mambu Mock Service Changes
- Ensure `POST /api/v2/loans/{loanId}/disbursement` and `GET /api/v2/loans/{loanId}/schedule` exactly match response structure consumed by Flow 5 DTOs.
- Add deterministic 200ms delay simulation for both endpoints.
- Add/extend contract tests validating disbursement + schedule fields used by platform mappers.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.application.approved` | Consume (optional) | Optional warm-path trigger for operations visibility; manual admin trigger remains source action. |
| `loan.disbursed` | Produce | Emit successful disbursement summary for notifications and downstream analytics. |
| `loan.schedule.persisted` | Produce | Emit confirmation that local schedule persistence completed successfully. |
| `loan.disbursement.failed` | Produce | Operational alert/event for Mambu or persistence failure and retry workflows. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `idempotency:DISB:{loanId}:{tenantId}` | `300s` | Prevent duplicate disbursement execution for same application (`loanId` token maps to `applicationId`). |
| `loan:flow5:disbursement:lock:{tenantId}:{applicationId}` | `300s` | Short distributed lock to prevent concurrent admin disbursement requests. |
| `loan:flow5:schedule:retry:{tenantId}:{applicationId}` | `1800s` | Track retry attempts for failed schedule fetch/persistence path. |

## 10. Stripe Integration Points
Flow 5 primary path in this plan is Mambu-first disbursement and schedule persistence. Stripe payment initiation (`PaymentIntent`) is not part of Flow 5 and remains under Flow 6 rules; therefore:
- No blocking Stripe call is introduced in Flow 5 critical path.
- If tenant payout integration is enabled later, any call must go through `StripeGatewayService` with same `DISB` idempotency key metadata, without bypassing current Flow 5 Mambu + schedule guarantees.

## 11. State Machine Transitions Triggered
### Required Flow 5 Transition Path
- `APPROVED` --`DISBURSE`--> `DISBURSED`
- `DISBURSED` --`ACTIVATE_REPAYMENT`--> `ACTIVE_REPAYMENT`

### Trigger Conditions
- Fire `DISBURSE` only after successful Mambu disbursement response is persisted locally.
- Fire `ACTIVATE_REPAYMENT` only after repayment schedule is fetched from Mambu and persisted in `loan_installments`.
- On failure (precondition fail, Mambu fail, schedule persist fail), do not mutate lifecycle state outside state machine; keep status retryable with explicit failure flags.

### Flow 4 Compatibility Note
Flow 4 currently ends at `APPROVED` with `mambuLoanId` present. Flow 5 implementation must consume that output contract and perform all further lifecycle transitions only via `LoanStateMachineService`.

## 12. Security (Roles, Tenant Isolation)
- All Flow 5 APIs require JWT auth and method-level `@PreAuthorize`.
- `POST /disburse` and admin status APIs restricted to `TENANT_ADMIN`.
- Borrower schedule/details APIs require `BORROWER` ownership checks (`tenantId + borrowerId + applicationId`) and allow same-tenant admin support read.
- All repository operations must include tenant-scoped predicates; no cross-tenant joins/reads.
- Reject cross-tenant access with `403` and audit with `tenantId`, `applicationId`, `borrowerId`, `requestId`.
- Log entry/exit at `DEBUG`, failures at `ERROR` with tenant and loan context.

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Duplicate disbursement requests from retries/click races | Can create duplicate external postings and inconsistent local state. | Redis idempotency key + short lock + DB status check before Mambu call; return `409` on duplicate in-flight request. |
| Mambu disbursement succeeds but schedule persistence fails | Borrower may be disbursed but app cannot show repayment plan. | Mark `schedule_fetch_failed`, publish `loan.disbursement.failed`, retry with scheduler, delay borrower notification until schedule persistence succeeds. |
| Lifecycle drift from direct status mutation | Breaks auditability and transition invariants. | Restrict lifecycle changes to `LoanStateMachineService` events (`DISBURSE`, `ACTIVATE_REPAYMENT`) and persist `loan_state_history` entries. |
| Tenant data leakage in schedule APIs | High-severity exposure of repayment data across tenants. | Tenant-scoped repository methods + ownership checks + integration tests proving tenant A cannot read tenant B schedule. |
| Contract mismatch with Mambu mock | Runtime mapping failures if response shape drifts from expected schema. | Dedicated Flow 5 DTOs + mambu-mock contract tests + 200ms delay parity to test retries/timeouts. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-5-loanDisbursemnt-repayment-schedule.md`
- [x] All required Gate 1 sections filled (a-m)
- [ ] Human approval pending
