# Gate 1 Plan - Flow 8: NPA Flagging -> Recovery Workflow -> Legal Escalation

## 1. Feature Summary
Flow 8 introduces automated NPA detection for loans that cross 90+ DPD (days past due), flags those loans in Mambu as `NON_PERFORMING`, and persists tenant-scoped internal NPA records for operations visibility. After flagging, the platform runs a staged internal recovery workflow at Day 1, Day 7, and Day 30, with Day 30 triggering legal escalation.

This implementation also exposes tenant-admin APIs to list NPA loans and override an NPA lock for controlled exception handling (for example negotiated settlement), while preserving tenant isolation and audit trail.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.testsupport`

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V14__create_flow8_npa_recovery_tables.sql` | Create NPA portfolio and recovery escalation audit tables with tenant-aware indexes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/NpaLoanRecord.java` | Aggregate for one flagged loan (DPD, stage, Mambu state, override metadata). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/NpaRecoveryAction.java` | Audit log of each Day 1/7/30 action triggered by scheduler. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/NpaRecoveryStage.java` | Enum for escalation stages (`DAY_1_REMINDER`, `DAY_7_ESCALATION`, `DAY_30_LEGAL_ESCALATION`, `OVERRIDDEN`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/NpaRecoveryActionType.java` | Enum for action history (`FLAGGED_NON_PERFORMING`, `DAY_1_REMINDER`, `DAY_7_ESCALATION`, `DAY_30_LEGAL_ESCALATION`, `OVERRIDE_CLEARED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/NpaLoanRecordRepository.java` | Tenant-scoped read/write and scheduler queries for NPA records. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/NpaRecoveryActionRepository.java` | Persist and query per-loan recovery audit entries. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/NpaUseCase.java` | Inbound contract for Flow 8 admin APIs and scheduler triggers. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/NpaService.java` | Core Flow 8 business logic: detect 90+ DPD, Mambu patch, stage escalation, override handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/NpaSchedulerJob.java` | Scheduled execution for NPA scan and Day 1/7/30 progression. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/NpaController.java` | REST endpoints for tenant-admin NPA listing and override action. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/NpaOverrideRequest.java` | Validation for override payload (`overrideReason`, `adminNote`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/NpaPortfolioResponse.java` | Response contract for `GET /api/v1/admin/npa`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/NpaOverrideResponse.java` | Response contract for override API. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/NpaMapper.java` | MapStruct mapper from domain entities to Flow 8 DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/NpaOverrideNotAllowedException.java` | Structured error for invalid override scenarios. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/NpaServiceTest.java` | Unit tests for detection, escalation, override, and error paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow8NpaIntegrationTest.java` | Integration coverage for API behavior, tenant isolation, and recovery progression. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow8NpaApiSmokeTest.java` | API smoke tests for Flow 8 endpoints. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleState.java` | Add `NON_PERFORMING` and `LEGAL_ESCALATION` lifecycle states for Flow 8 transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleEvent.java` | Add `FLAG_NON_PERFORMING`, `ESCALATE_TO_LEGAL`, `CLEAR_NPA_OVERRIDE` events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Register Flow 8 transitions from active repayment to NPA and legal escalation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanInstallmentRepository.java` | Add query to find 90+ DPD unpaid installments and DPD max per application. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanApplicationRepository.java` | Add query for active repayment + mambu loan ids scoped by tenant/app ids. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add `NPA_OVERRIDE_NOT_ALLOWED` handler. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Reuse existing patch state call for `NON_PERFORMING`; no new raw HTTP path. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add Flow 8 scheduler, DPD threshold, and Redis TTL config. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/statemachine/LoanStateMachineServiceTest.java` | Add happy/error tests for new Flow 8 transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/testsupport/DatabaseCleanupHelper.java` | Include Flow 8 tables in cleanup order. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add finalized Flow 8 endpoint examples and behavior notes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 8 status to `🔵 IN PROGRESS` during implementation and `✅ COMPLETE` after Gate 3. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V14__create_flow8_npa_recovery_tables.sql`

### Table: `npa_loan_records` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `borrower_id` | `UUID` | `NOT NULL` |
| `mambu_loan_id` | `VARCHAR(120)` | `NOT NULL` |
| `max_dpd` | `INTEGER` | `NOT NULL` |
| `outstanding_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `npa_flagged_at` | `TIMESTAMP` | `NOT NULL` |
| `recovery_stage` | `VARCHAR(50)` | `NOT NULL` |
| `mambu_state` | `VARCHAR(50)` | `NOT NULL` (`NON_PERFORMING` default) |
| `legal_escalated_at` | `TIMESTAMP` | Nullable |
| `override_reason` | `VARCHAR(500)` | Nullable |
| `admin_note` | `VARCHAR(1000)` | Nullable |
| `overridden_by` | `UUID` | Nullable |
| `overridden_at` | `TIMESTAMP` | Nullable |
| `active` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes:
- Unique index `uk_npa_loan_records_tenant_application_active` on (`tenant_id`, `loan_application_id`, `active`)
- Index `idx_npa_loan_records_tenant_stage_flagged` on (`tenant_id`, `recovery_stage`, `npa_flagged_at`)
- Index `idx_npa_loan_records_tenant_mambu_loan` on (`tenant_id`, `mambu_loan_id`)

### Table: `npa_recovery_actions` (NEW)
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL` |
| `npa_loan_record_id` | `UUID` | `NOT NULL` FK -> `npa_loan_records(id)` |
| `loan_application_id` | `UUID` | `NOT NULL` |
| `action_type` | `VARCHAR(60)` | `NOT NULL` |
| `action_note` | `VARCHAR(1000)` | Nullable |
| `triggered_by` | `VARCHAR(40)` | `NOT NULL` (`SYSTEM`/`ADMIN`) |
| `triggered_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Indexes:
- Index `idx_npa_recovery_actions_tenant_record_time` on (`tenant_id`, `npa_loan_record_id`, `triggered_at`)
- Unique index `uk_npa_recovery_action_once` on (`tenant_id`, `npa_loan_record_id`, `action_type`)

## 6. API Endpoints (Method + Path + Purpose)
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/npa` | List active tenant NPA loans with DPD, stage, flagged date, and Mambu state. |
| `POST` | `/api/v1/admin/npa/{loanAccountId}/override` | Allow tenant admin to clear NPA lock with reason/note and audit. |

## 7. Mambu Mock API Calls Required
- `PUT /api/v2/loans/{loanId}` with payload containing `loanState=NON_PERFORMING` and notes.
- `PUT /api/v2/loans/{loanId}` with payload containing `loanState=ACTIVE` for admin override clearance.

Payload rules:
- Flag event note: `90+ DPD — Flagged as NPA by system on {date}`.
- Legal escalation note at Day 30: `Day 30 legal escalation initiated`.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.npa.flagged` | Produce | Loan crossed threshold and was flagged NON_PERFORMING. |
| `loan.npa.recovery.day1` | Produce | Day 1 internal reminder action recorded. |
| `loan.npa.recovery.day7` | Produce | Day 7 escalation action recorded. |
| `loan.npa.recovery.day30.legal` | Produce | Day 30 legal escalation action recorded. |
| `loan.npa.override.cleared` | Produce | Admin cleared lock for an NPA record. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `NPA:FLAG:{loanAccountId}:{tenantId}` | `86400s` | Dedupe repeated NPA flag job processing on same day. |
| `NPA:ESCALATION:{npaRecordId}:{stage}` | `86400s` | Avoid duplicate stage action publication for scheduler reruns. |
| `NPA:OVERRIDE:{loanAccountId}:{tenantId}` | `300s` | Short lock for concurrent admin override requests. |

## 10. Stripe Integration Points (if payment-related)
- Not applicable for Flow 8.

## 11. State Machine Transitions Triggered
- `ACTIVE_REPAYMENT` --`FLAG_NON_PERFORMING`--> `NON_PERFORMING`
- `NON_PERFORMING` --`ESCALATE_TO_LEGAL`--> `LEGAL_ESCALATION`
- `NON_PERFORMING` --`CLEAR_NPA_OVERRIDE`--> `ACTIVE_REPAYMENT`
- `LEGAL_ESCALATION` --`CLEAR_NPA_OVERRIDE`--> `ACTIVE_REPAYMENT`

All lifecycle updates remain routed through `LoanStateMachineService`.

## 12. Security (Roles, tenant isolation)
- Both Flow 8 endpoints are restricted to `ROLE_TENANT_ADMIN` (`@PreAuthorize("hasRole('TENANT_ADMIN')")`).
- Every repository query is tenant scoped by `tenantId`.
- Override API resolves loan only within current tenant and rejects cross-tenant access.
- Logs include `tenantId`, `loanApplicationId`, `loanAccountId`, `requestId` context where available.

## 13. Risk flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| False-positive NPA from stale installment statuses | Operational/legal impact if a paid loan is marked NPA. | Filter only unpaid statuses and compute DPD from due date at runtime; add tests. |
| Duplicate NPA flagging under scheduler retries | Causes repeated Mambu calls and event noise. | Redis dedupe + DB uniqueness + idempotent action history. |
| Missed escalation stages due to scheduler downtime | Collection/legal action delays. | Stage computation based on `npa_flagged_at` elapsed days, not single-run counters. |
| Cross-tenant override access | Security/privacy breach. | Strict tenant-scoped lookup + integration tenant isolation tests. |
| State divergence between platform and Mambu | Portfolio reporting inconsistency. | Persist Mambu state response and hard-fail on Mambu patch errors. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-8-npa-flagging-recovery-legal-escalation.md`
- [x] All required sections filled
- [x] Human approved (explicit instruction to continue implementation in same run)
