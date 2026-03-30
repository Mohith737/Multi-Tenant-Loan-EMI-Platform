# Gate 1 Plan - Flow 4: Document Submission & Underwriter Approval

## 1. Feature Summary
Flow 4 starts after a borrower has selected an offer in Flow 3 and uploads loan-supporting documents for underwriting. The application is moved into underwriter verification, where a tenant underwriter/admin reviews documents and records a final `APPROVED` or `REJECTED` decision with audit data. On approval, the platform creates and approves the loan in Mambu (`POST /api/v2/loans`, then `POST /api/v2/loans/{id}/approve`), updates state via Spring State Machine, and notifies the borrower; on rejection, state moves to `REJECTED` with reason and borrower can reapply.

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
- `com.loanplatform.loan_platform.statemachine.actions`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.config`
- `com.loanplatform.mambu.controller` (mock contract alignment)
- `com.loanplatform.mambu.model.loan` (mock response fields if needed)
- `com.loanplatform.mambu.repository` (mock persistence checks)

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V8__create_flow4_document_approval_tables.sql` | Create `loan_documents` and `underwriter_decisions` tables with tenant-safe indexes and constraints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanDocument.java` | Persist borrower-uploaded document metadata per application. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanDocumentStatus.java` | Document lifecycle enum (`UPLOADED`, `UNDER_REVIEW`, `VERIFIED`, `REJECTED`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/UnderwriterDecision.java` | Underwriter decision entity (`APPROVED`/`REJECTED`) with remarks and timestamp. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/UnderwriterDecisionType.java` | Decision enum for underwriter action values. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanDocumentRepository.java` | Tenant-aware repository for document upload/query/review operations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/UnderwriterDecisionRepository.java` | Repository for idempotent decision persistence and duplicate-decision prevention. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LoanDocumentService.java` | Orchestrate borrower document upload and underwriting readiness checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/UnderwriterDecisionService.java` | Handle approve/reject flow, Mambu calls, FSM transitions, and notifications. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/UnderwriterQueueService.java` | Build underwriter queue view from application/document aggregates. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/DocumentApprovalUseCase.java` | Inbound Flow 4 use case contract for document and decision endpoints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/LoanDocumentController.java` | Borrower document upload/list endpoints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/UnderwriterController.java` | Underwriter queue and final decision endpoints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanCreateRequest.java` | Typed request DTO for `POST /api/v2/loans`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanAccountResponse.java` | Typed response DTO for Mambu loan account create/approve payload. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanApproveRequest.java` | Typed request DTO for `POST /api/v2/loans/{id}/approve`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/LoanDocumentUploadRequest.java` | Request DTO for borrower document upload with validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/UnderwriterDecisionRequest.java` | Request DTO for underwriter `APPROVED`/`REJECTED` decision and remarks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/LoanDocumentResponse.java` | Response DTO for uploaded/listed loan documents. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/UnderwriterQueueItemResponse.java` | Queue item response for underwriter dashboard. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/UnderwriterQueueResponse.java` | Response wrapper for paged underwriter queue. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/UnderwriterDecisionResponse.java` | Final decision response containing new state and optional Mambu loan id. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/LoanDocumentMapper.java` | MapStruct mapper for `LoanDocument`/`UnderwriterDecision` to API DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/LoanDocumentNotFoundException.java` | Exception for missing document/application document relations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/UnderwriterDecisionConflictException.java` | Exception for duplicate or concurrent underwriter decisions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/MambuLoanApprovalException.java` | Exception wrapper for Flow 4 Mambu create/approve failures. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/actions/ApproveApplicationAction.java` | Action hook executed on `APPROVE` transition. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/actions/RejectApplicationAction.java` | Action hook executed on `REJECT` transition with rejection reason logging. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/LoanDocumentServiceTest.java` | Unit tests for upload validation, status changes, and tenant checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/loan/UnderwriterDecisionServiceTest.java` | Unit tests for approve/reject, Mambu failures, and lock behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/statemachine/LoanFlow4StateMachineTest.java` | Unit tests for `UNDER_VERIFICATION`, `APPROVED`, `REJECTED` transitions and invalid paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/loan/Flow4DocumentApprovalIntegrationTest.java` | Integration tests for end-to-end Flow 4 happy path and edge cases with tenant isolation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/Flow4DocumentApprovalApiSmokeTest.java` | API smoke test for all new Flow 4 endpoints. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/test/java/com/loanplatform/mambu/controller/MambuLoanApprovalContractTest.java` | Contract test asserting create/approve endpoints and response shapes for Flow 4. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplication.java` | Add Flow 4 fields (`underwriterId`, `underwriterRemarks`, `decisionAt`, `mambuLoanId`, `rejectionReason`) needed for approval lifecycle tracking. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanApplicationStatus.java` | Add/align statuses for `UNDER_VERIFICATION`, `APPROVED`, `REJECTED`; ensure active status set reflects underwriting stage. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleState.java` | Extend FSM states to include `UNDER_VERIFICATION`, `APPROVED`, `REJECTED`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/model/LoanLifecycleEvent.java` | Add Flow 4 events (`START_UNDER_VERIFICATION`, `APPROVE`, `REJECT`). |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/repository/LoanApplicationRepository.java` | Add tenant-scoped queue queries and locking read methods for concurrent decision control. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/LoanApplicationUseCase.java` | Keep Flow 3 APIs stable; add handoff helper if needed for selected-offer + document precondition checks. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LoanApplicationService.java` | Update Flow 3->4 handoff logic so selected applications enter/document-track underwriting pipeline correctly. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/loan/service/LoanOfferSelectionService.java` | Align post-offer state behavior with Flow 4 transition path and queue trigger event. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add `createLoanAccount(...)` and `approveLoanAccount(...)` outbound contracts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign methods for `POST /api/v2/loans` and `POST /api/v2/loans/{id}/approve`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Implement mapping for Flow 4 create-loan and approve-loan calls. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/BorrowerNotificationPort.java` | Add borrower notification methods for loan approval and rejection outcomes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/notification/BorrowerNotificationAdapter.java` | Implement new Flow 4 notification adapters with tenant/application context logs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineConfig.java` | Register Flow 4 transitions for underwriting and final decision outcomes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/statemachine/LoanStateMachineService.java` | Keep state machine as source of truth; enforce Flow 4 transition guards and error paths. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/config/SecurityConfig.java` | Confirm route policies for borrower upload endpoints and tenant-admin underwriter endpoints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Map Flow 4 exceptions to standardized error codes/statuses. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add Flow 4 config for required-document types, queue scan settings, and Redis lock TTLs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add/update Flow 4 endpoint contracts and payload schemas. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 4 status (`NOT STARTED` -> `IN PROGRESS` -> `COMPLETE`) at Gate 2/Gate 3 milestones. |
| `/home/admin123/Desktop/Project/Java/mambu-mock-service/src/main/java/com/loanplatform/mambu/controller/MambuLoanAccountController.java` | Validate Flow 4 create/approve request/response shape and add 200ms delay simulation for new/updated endpoints. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V8__create_flow4_document_approval_tables.sql`

### Table: `loan_documents`
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `application_id` | `UUID` | `NOT NULL`, FK -> `loan_applications(id)` |
| `borrower_id` | `UUID` | `NOT NULL`, FK -> `borrowers(id)` |
| `tenant_id` | `UUID` | `NOT NULL`, FK -> `tenants(id)` |
| `document_type` | `VARCHAR(60)` | `NOT NULL` |
| `document_reference` | `VARCHAR(500)` | `NOT NULL` |
| `status` | `VARCHAR(40)` | `NOT NULL` (`UPLOADED`, `UNDER_REVIEW`, `VERIFIED`, `REJECTED`) |
| `rejection_reason` | `VARCHAR(500)` | nullable |
| `verified_by` | `UUID` | nullable (tenant underwriter/admin user id) |
| `verified_at` | `TIMESTAMP` | nullable |

Indexes and constraints:
- `idx_loan_documents_tenant_application` on (`tenant_id`, `application_id`)
- `idx_loan_documents_tenant_status` on (`tenant_id`, `status`)
- `idx_loan_documents_app_borrower` on (`application_id`, `borrower_id`)
- Unique index `uk_loan_documents_ref_per_app` on (`application_id`, `document_type`, `document_reference`)

### Table: `underwriter_decisions`
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `application_id` | `UUID` | `NOT NULL`, FK -> `loan_applications(id)` |
| `underwriter_id` | `UUID` | `NOT NULL` (resolved from authenticated tenant admin) |
| `decision` | `VARCHAR(30)` | `NOT NULL` (`APPROVED`, `REJECTED`) |
| `remarks` | `VARCHAR(1000)` | nullable |
| `decided_at` | `TIMESTAMP` | `NOT NULL` |

Indexes and constraints:
- Unique index `uk_underwriter_decision_application` on (`application_id`) to enforce one final decision per application.
- `idx_underwriter_decisions_underwriter_time` on (`underwriter_id`, `decided_at`)
- `idx_underwriter_decisions_application_time` on (`application_id`, `decided_at`)

## 6. API Endpoints (Method + Path + Purpose + Role)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/v1/loans/{applicationId}/documents` | Borrower uploads a document reference for selected-offer application; marks document `UPLOADED`; pushes app to underwriter verification queue when minimum docs satisfied. | `BORROWER` |
| `GET` | `/api/v1/loans/{applicationId}/documents` | Retrieve uploaded/reviewed documents for borrower self-view or tenant underwriter review. | `BORROWER`, `TENANT_ADMIN` |
| `GET` | `/api/v1/underwriter/queue` | Fetch tenant-scoped pending underwriting queue (`UNDER_VERIFICATION`). | `TENANT_ADMIN` |
| `POST` | `/api/v1/loans/{applicationId}/decision` | Underwriter records final `APPROVED`/`REJECTED` decision with remarks; approval path triggers Mambu create+approve calls. | `TENANT_ADMIN` |
| `GET` | `/api/v1/loans/{applicationId}/decision` | Fetch final decision audit details for application history and support visibility. | `TENANT_ADMIN`, `BORROWER` (read-only own app) |

## 7. Mambu Mock API Calls Required
### Platform -> Mambu Calls (Flow 4 Mandatory)
- `POST /api/v2/loans`  
  Purpose: Create loan account after underwriter decision `APPROVED`.
- `POST /api/v2/loans/{id}/approve`  
  Purpose: Approve newly created Mambu loan account.

### Planned Request Mapping (Platform -> Mambu)
- `clientKey` from borrower `mambuClientKey`
- `productTypeKey` from selected loan product key
- `loanAmount.value` from selected offer principal / approved amount
- `interestRate.value` and `repaymentInstallments` from selected offer snapshot
- `disbursementDetails.expectedDisbursementDate` from approved disbursement plan/default current date

### Planned Response Fields Consumed
- `id` / `encodedKey` -> persist as platform `mambuLoanId` reference
- `accountState` -> validation before local transition to `APPROVED`
- `approvedDate` -> audit/event payload

### Mambu Mock Service Changes
- Ensure `MambuLoanAccountController` create/approve endpoints match expected request/response schema used by Flow 4 DTOs.
- Add deterministic 200ms delay simulation on create and approve endpoints to mirror integration latency behavior.

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Purpose |
|---|---|---|
| `loan.offer.selected` | Consume | Flow 3 handoff trigger; mark application eligible for document intake pipeline. |
| `loan.documents.uploaded` | Produce | Notify underwriting/ops systems when document is uploaded. |
| `loan.underwriter.queue.entered` | Produce | Emit event when application enters `UNDER_VERIFICATION` queue. |
| `loan.application.approved` | Produce | Emit final approval event with Mambu loan id for downstream disbursement flow. |
| `loan.application.rejected` | Produce | Emit rejection event with reason for borrower comms/reapply tracking. |
| `loan.underwriter.decision.failed` | Produce | Operational alert topic for failures (e.g., Mambu create/approve failed after decision request). |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `loan:flow4:document:upload:idempotency:{tenantId}:{applicationId}:{documentHash}` | `600s` | Prevent duplicate upload retries from creating duplicate rows/events. |
| `loan:flow4:decision:lock:{tenantId}:{applicationId}` | `300s` | Distributed lock to prevent concurrent underwriter decision posting. |
| `loan:flow4:decision:result:{tenantId}:{applicationId}` | `3600s` | Cache final decision response for safe retry/read consistency after write. |

## 10. Stripe Integration Points
Flow 4 is not payment-initiating. Stripe integration is `N/A` for this flow.

## 11. State Machine Transitions Triggered
### Required Flow 4 Transition Path
- `APPLICATION_SUBMITTED` --`START_UNDER_VERIFICATION`--> `UNDER_VERIFICATION`
- `UNDER_VERIFICATION` --`APPROVE`--> `APPROVED`
- `UNDER_VERIFICATION` --`REJECT`--> `REJECTED`

### Trigger Conditions
- First valid document upload set completion threshold met -> fire `START_UNDER_VERIFICATION`.
- Underwriter final decision `APPROVED` and successful Mambu create+approve -> fire `APPROVE`.
- Underwriter final decision `REJECTED` -> fire `REJECT` with rejection reason metadata.

### Flow 3 Compatibility Note
Current Flow 3 implementation uses `OFFER_SELECTED` state. Gate 2 implementation must align handoff so Flow 4 state transitions remain deterministic and state updates happen only through `LoanStateMachineService`.

## 12. Security (Roles, Tenant Isolation)
- All Flow 4 APIs require JWT authentication and method-level `@PreAuthorize`.
- Borrower endpoints restricted to `BORROWER` and enforce `tenantId + borrowerId + applicationId` ownership.
- Underwriter endpoints restricted to `TENANT_ADMIN`; `underwriter_id` comes from authenticated principal, not request payload.
- Every repository query must include tenant scope (`tenant_id` via application/document relation).
- Reject any cross-tenant application/document access with `403`.
- Audit logs must include `tenantId`, `applicationId`, `borrowerId` (if available), `underwriterId`, and `requestId`.

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Mambu loan creation/approval fails after underwriter `APPROVED` decision request | System may persist approval intent but fail to create external loan account, causing decision/state mismatch. | Wrap decision + Mambu calls in transactional orchestration with compensating status (`DECISION_PENDING_EXTERNAL`/error event), do not fire final `APPROVE` transition until both Mambu calls succeed, publish failure event for retry/ops. |
| Partial document upload | Underwriter may decide without full mandatory document set; compliance/credit risk. | Enforce required document set validation before queue entry/decision; keep app in pre-verification until threshold satisfied; add explicit API error if decision attempted early. |
| Concurrent decision posting | Two underwriters may approve/reject same application simultaneously, leading to conflicting outcomes. | Redis lock (`loan:flow4:decision:lock:*`) + DB unique constraint on `underwriter_decisions(application_id)` + optimistic check on application status before persisting decision. |
| Tenant data leakage in queue APIs | Underwriters could view applications/documents from another tenant. | Tenant-scoped repository predicates and integration test asserting tenant A cannot read tenant B queue/documents/decisions. |
| State drift outside state machine | Direct status mutation can bypass transition rules and break audit history. | Centralize all status/state changes behind `LoanStateMachineService` and persist transition history for every Flow 4 event. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-4-document-approval.md`
- [x] All required Gate 1 sections filled (a-m)
- [ ] Human approval pending

