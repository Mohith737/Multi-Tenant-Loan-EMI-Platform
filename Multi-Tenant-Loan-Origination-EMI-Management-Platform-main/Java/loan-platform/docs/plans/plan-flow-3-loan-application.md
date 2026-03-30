# Gate 1 Plan - Flow 3: Loan Application & Offer Generation

## 1. Feature Summary
Flow 3 enables a borrower to submit a loan application, run eligibility checks using borrower credit and product constraints, and generate exactly three offer options using Mambu simulation for requested tenure variants (`requested`, `requested-12`, `requested+12`). If all three simulations succeed, the platform persists the application in `APPLICATION_SUBMITTED` state, stores generated offers with a 48-hour validity window, triggers Spring State Machine `SUBMIT`, and returns offers to the borrower. Borrower can then select one offer to proceed into Flow 4 document submission.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.loan.model`
- `com.loanplatform.loan_platform.domain.loan.repository`
- `com.loanplatform.loan_platform.domain.loan.service`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.mambu.dto`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.statemachine`
- `com.loanplatform.loan_platform.statemachine.actions`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.mambu.controller` (Mambu mock service contract validation for simulation)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V7__create_flow3_loan_application_tables.sql` | Create `loan_applications`, `loan_offers`, `loan_state_history` tables and Flow 3 indexes/constraints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplication.java` | Loan application aggregate root with tenant scope and request snapshots. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanOffer.java` | Offer entity storing tenure-wise EMI/interest and expiry metadata. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanStateHistory.java` | Audit trail entity for state machine transitions per application. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplicationStatus.java` | Application status enum (`APPLICATION_SUBMITTED`, `OFFER_SELECTED`, `OFFER_EXPIRED`, etc.). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanOfferStatus.java` | Offer status enum (`ACTIVE`, `SELECTED`, `EXPIRED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleState.java` | Loan lifecycle FSM state enum for state machine integration. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleEvent.java` | Loan lifecycle FSM event enum (`SUBMIT`, `SELECT_OFFER`, `EXPIRE_OFFERS`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/EligibilityDecision.java` | Value object containing eligibility outcome, reasons, and threshold snapshots. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanApplicationRepository.java` | Tenant-aware repository for application persistence and duplicate active-loan checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanOfferRepository.java` | Repository for offer read/write and selection update logic. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanStateHistoryRepository.java` | Repository for state transition history audit records. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/EligibilityEngine.java` | Implements credit score, DTI, income, employment, amount range, duplicate active-loan eligibility rules. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LoanApplicationService.java` | Orchestrates submit flow, simulation fan-out, offer generation, persistence, and FSM trigger. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LoanOfferSelectionService.java` | Handles borrower offer selection, expiry validation, and transition to next workflow state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/LoanApplicationUseCase.java` | Inbound use case contract for Flow 3 endpoints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/LoanStateMachinePort.java` | Outbound port to trigger lifecycle transitions via Spring State Machine service. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/KafkaEventPort.java` | Outbound event contract for loan application and offer selection events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/LoanApplicationController.java` | REST APIs for submit, offers fetch, and offer selection. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanSimulationRequest.java` | Typed request DTO for `POST /api/v2/loans:simulate`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanSimulationResponse.java` | Typed simulation response DTO containing EMI and total interest fields. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kafka/KafkaEventAdapter.java` | Kafka producer adapter for Flow 3 domain events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | State machine config for Flow 3 states/events and transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Service wrapper used by domain service to dispatch FSM events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/actions/SubmitApplicationAction.java` | Action to persist state audit side-effects on `SUBMIT` event. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/LoanApplicationRequest.java` | Borrower request payload for application submission. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/LoanOfferSelectionRequest.java` | Request payload to select a generated offer. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanApplicationResponse.java` | Submit response payload including eligibility result + 3 offers. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanOfferResponse.java` | Offer item response with EMI, interest, rate, expiry. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanOfferSelectionResponse.java` | Response payload for selected offer confirmation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/LoanApplicationMapper.java` | MapStruct mapper for loan entities to Flow 3 response DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/LoanApplicationNotFoundException.java` | Not found exception for application lookups. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/LoanEligibilityFailedException.java` | Exception for ineligible application requests. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/DuplicateActiveLoanException.java` | Exception for existing active loan/application prevention. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/LoanOfferExpiredException.java` | Exception for selecting expired offer. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/MambuSimulationException.java` | Wrapper for simulation call failures and timeout handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/EligibilityEngineTest.java` | Unit tests for all eligibility rules and boundary conditions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/LoanApplicationServiceTest.java` | Unit tests for orchestration, simulation fan-out, and persistence behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/statemachine/LoanStateMachineServiceTest.java` | Unit tests for `SUBMIT` and `SELECT_OFFER` transition paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/LoanApplicationFlowIntegrationTest.java` | Integration tests for Flow 3 happy path, expiry, duplicate prevention, and tenant isolation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/LoanApplicationApiSmokeTest.java` | API smoke cURL-equivalent tests for submit/offers/select endpoints. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuSimulationControllerContractTest.java` | Contract test asserting simulation payload/response shape used by Flow 3 platform adapter. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/pom.xml` | Add dependencies for Spring State Machine and Kafka if missing for Flow 3 implementation/tests. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/LoanPlatformApplication.java` | Enable required components for new loan/state machine adapters (if not already auto-configured). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/config/SecurityConfig.java` | Register Flow 3 endpoint authorization policy and keep auth required by default. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add outbound `simulateLoan(...)` contract used by loan application service. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign method mapping for `POST /api/v2/loans:simulate`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Implement simulation request mapping and response parsing for offer generation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add Flow 3 exceptions and standard API error mappings. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add Flow 3 config: offer expiry hours, simulation timeout/retry, Kafka topic names, Redis key TTLs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add/update Flow 3 endpoint contract (submit, offers retrieval, offer selection). |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 3 status from `NOT STARTED` to `IN PROGRESS` (Gate 2) and `COMPLETE` (Gate 3). |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuSimulationController.java` | Align response fields (if required) to exact platform contract and enforce 200ms delay consistency. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V7__create_flow3_loan_application_tables.sql`

### Table: `loan_applications`
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL`, FK -> `tenants(id)` |
| `borrower_id` | `UUID` | `NOT NULL`, FK -> `borrowers(id)` |
| `loan_product_id` | `UUID` | `NOT NULL`, FK -> `loan_products(id)` |
| `loan_product_key` | `VARCHAR(100)` | `NOT NULL` (snapshot of Mambu product key used for simulation) |
| `requested_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `requested_tenure_months` | `INTEGER` | `NOT NULL` |
| `loan_purpose` | `VARCHAR(120)` | nullable |
| `requested_disbursement_date` | `DATE` | nullable |
| `status` | `VARCHAR(40)` | `NOT NULL` (application status) |
| `loan_state` | `VARCHAR(40)` | `NOT NULL` (FSM state snapshot) |
| `eligibility_passed` | `BOOLEAN` | `NOT NULL` |
| `eligibility_reason_code` | `VARCHAR(80)` | nullable (e.g., `LOW_CREDIT_SCORE`) |
| `risk_category` | `VARCHAR(30)` | nullable |
| `credit_score_snapshot` | `INTEGER` | `NOT NULL` |
| `dti_ratio_snapshot` | `NUMERIC(10,2)` | `NOT NULL` |
| `monthly_income_snapshot` | `NUMERIC(19,2)` | `NOT NULL` |
| `employment_type_snapshot` | `VARCHAR(40)` | `NOT NULL` |
| `offers_expires_at` | `TIMESTAMP` | `NOT NULL` (`submitted_at + 48h`) |
| `selected_offer_id` | `UUID` | nullable, FK -> `loan_offers(id)` |
| `submitted_at` | `TIMESTAMP` | `NOT NULL` |
| `created_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |

Indexes and constraints:
- `idx_loan_applications_tenant_borrower` on (`tenant_id`, `borrower_id`, `created_at` DESC)
- `idx_loan_applications_tenant_status` on (`tenant_id`, `status`)
- `idx_loan_applications_offer_expiry` on (`tenant_id`, `offers_expires_at`)
- Partial unique index `uk_loan_app_active_per_borrower` on (`tenant_id`, `borrower_id`) where `status IN ('APPLICATION_SUBMITTED','OFFER_SELECTED','UNDER_REVIEW','APPROVED','DISBURSED','ACTIVE_REPAYMENT')`

### Table: `loan_offers`
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL`, FK -> `tenants(id)` |
| `application_id` | `UUID` | `NOT NULL`, FK -> `loan_applications(id)` |
| `offer_rank` | `SMALLINT` | `NOT NULL` (1, 2, 3 for tenure variant ordering) |
| `tenure_months` | `INTEGER` | `NOT NULL` |
| `principal_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `emi_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `total_interest` | `NUMERIC(19,2)` | `NOT NULL` |
| `effective_annual_rate` | `NUMERIC(10,4)` | `NOT NULL` |
| `processing_fee_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `total_payable_amount` | `NUMERIC(19,2)` | `NOT NULL` |
| `expires_at` | `TIMESTAMP` | `NOT NULL` (48h from submission) |
| `status` | `VARCHAR(30)` | `NOT NULL` (`ACTIVE`, `SELECTED`, `EXPIRED`) |
| `mambu_simulation_ref` | `VARCHAR(120)` | nullable (request hash/correlation key) |
| `created_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `selected_at` | `TIMESTAMP` | nullable |

Indexes and constraints:
- `idx_loan_offers_tenant_application` on (`tenant_id`, `application_id`)
- `idx_loan_offers_tenant_status` on (`tenant_id`, `status`)
- `idx_loan_offers_expiry` on (`tenant_id`, `expires_at`)
- Unique index `uk_loan_offers_rank_per_application` on (`application_id`, `offer_rank`)

### Table: `loan_state_history`
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `tenant_id` | `UUID` | `NOT NULL`, FK -> `tenants(id)` |
| `application_id` | `UUID` | `NOT NULL`, FK -> `loan_applications(id)` |
| `from_state` | `VARCHAR(40)` | nullable for initial transition |
| `to_state` | `VARCHAR(40)` | `NOT NULL` |
| `event` | `VARCHAR(40)` | `NOT NULL` |
| `changed_by` | `VARCHAR(120)` | `NOT NULL` (`borrower:{id}` or `system`) |
| `reason` | `VARCHAR(500)` | nullable |
| `request_id` | `VARCHAR(100)` | nullable |
| `metadata_json` | `JSONB` | nullable (context for audits/debug) |
| `changed_at` | `TIMESTAMP` | `NOT NULL` |
| `created_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |

Indexes:
- `idx_loan_state_history_tenant_app_time` on (`tenant_id`, `application_id`, `changed_at` DESC)
- `idx_loan_state_history_tenant_to_state` on (`tenant_id`, `to_state`)

## 6. API Endpoints (Method + Path + Purpose + Role)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/v1/loans/applications` | Submit borrower loan application, run eligibility, generate and persist 3 offers, trigger `SUBMIT` event, return offers. | `BORROWER` |
| `GET` | `/api/v1/loans/applications/{applicationId}/offers` | Fetch all currently generated offers for an application (must belong to same tenant+borrower). | `BORROWER` |
| `POST` | `/api/v1/loans/applications/{applicationId}/offers/select` | Select one active non-expired offer and transition application for Flow 4 handoff. | `BORROWER` |
| `GET` | `/api/v1/loans/applications/{applicationId}` | Retrieve application summary with eligibility and state for borrower self-view/admin ops. | `BORROWER`, `TENANT_ADMIN` |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu
- `POST /api/v2/loans:simulate` (called 3 times per eligible application)

### Tenure variants
- Variant 1: requested tenure (`requestedTenureMonths`)
- Variant 2: shorter tenure (`max(requestedTenureMonths - 12, product.minTenureMonths)`)
- Variant 3: longer tenure (`min(requestedTenureMonths + 12, product.maxTenureMonths)`)

### Request fields used
- `loanAmount.value` = requested amount
- `interestRate.value` = tenant product annual interest rate snapshot
- `repaymentInstallments` = variant tenure
- `loanProductTypeKey` = `loan_products.mambu_product_key`
- `clientKey` = `borrowers.mambu_client_key`
- `disbursementDetails.expectedDisbursementDate` = requested disbursement date

### Response fields consumed
- `periodicPayment` -> `loan_offers.emi_amount`
- `totalInterestCharged` -> `loan_offers.total_interest`
- `annualPercentageRate` -> `loan_offers.effective_annual_rate`
- `totalAmountRepaid` -> `loan_offers.total_payable_amount`

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.application.submitted` | Produce | Notify downstream Flow 4 document collection pipeline after successful `SUBMIT`. |
| `loan.offer.selected` | Produce | Notify Flow 4 that borrower selected an offer and app is ready for document intake. |
| `loan.offer.expired` | Produce | Emit expiry event for cleanup/notification workflows when no offer selected in 48h. |
| `loan.application.submitted.retry` | Consume (optional) | Optional replay topic for safe reprocessing in failure scenarios. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `loan:application:idempotency:{tenantId}:{borrowerId}:{requestHash}` | `600s` | Prevent duplicate application submission retries from creating multiple records. |
| `loan:offer:expiry:{applicationId}` | `172800s` (48h) | Fast expiry lookup and scheduler trigger support for offer timeout handling. |
| `loan:offer:select:lock:{tenantId}:{applicationId}` | `300s` | Short lock to prevent concurrent offer selection race conditions. |

## 10. Stripe Integration Points
Not applicable for Flow 3 (`N/A`).

## 11. State Machine Transitions Triggered
### States used in Flow 3
- `APPLICATION_DRAFT` (initial)
- `APPLICATION_SUBMITTED`
- `OFFER_SELECTED`
- `OFFER_EXPIRED`

### Events used in Flow 3
- `SUBMIT`
- `SELECT_OFFER`
- `EXPIRE_OFFERS`

### Planned transitions
- `APPLICATION_DRAFT` --`SUBMIT`--> `APPLICATION_SUBMITTED`
- `APPLICATION_SUBMITTED` --`SELECT_OFFER`--> `OFFER_SELECTED` (handoff to Flow 4)
- `APPLICATION_SUBMITTED` --`EXPIRE_OFFERS`--> `OFFER_EXPIRED`

Flow requirement-specific trigger: on successful application persistence, trigger state machine `SUBMIT` event and persist transition in `loan_state_history`.

## 12. Security (Roles, Tenant Isolation)
- All Flow 3 APIs require JWT; borrower actions guarded with `@PreAuthorize("hasRole('BORROWER')")`.
- `tenantId` and `borrowerId` are resolved from JWT/context and enforced in service/repository queries.
- If request body contains `borrowerId`, service validates it equals authenticated borrower; otherwise reject with `403`.
- `TENANT_ADMIN` read access is limited to same-tenant applications only.
- All repository methods include `tenantId` predicates; no cross-tenant reads/writes permitted.
- Logs include `tenantId`, `borrowerId`, `applicationId`, and `requestId` for auditability.

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Mambu partial failure handling (one of 3 simulations fails) | Returning fewer than 3 offers violates Flow 3 contract and can create inconsistent application state. | Retry each failed simulation with bounded retry; if any still fails, fail request atomically (no `loan_application`/`loan_offers` persist), return integration error with correlation ID. |
| Offer expiry correctness (48-hour TTL) | Borrower may select stale offer after expiry, causing pricing/legal inconsistency. | Persist absolute `expires_at`, enforce selection-time validation, run expiry scheduler/event, transition to `OFFER_EXPIRED`. |
| Duplicate application prevention | Duplicate active applications for same borrower can bypass underwriting controls and create downstream conflicts. | Redis idempotency key + partial unique DB index for active statuses + eligibility rule check for existing active loan/application. |
| Cross-tenant access risk | Sensitive borrower/offer data leakage between tenants. | JWT-derived tenant context + tenant-scoped repositories + integration tests proving tenant A cannot access tenant B records. |
| State drift outside state machine | Direct status writes can desync lifecycle from audit history. | Enforce lifecycle updates through `LoanStateMachineService` only and write every transition to `loan_state_history`. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-3-loan-application.md`
- [x] All required sections from AGENTS Gate 1 filled
- [ ] Human approval pending
