# Flow 6 Implementation Summary (Gate 2)

## Scope Implemented
Flow 6 implementation was completed for:
- EMI collection dispatch via scheduled job
- Stripe PaymentIntent creation for off-session EMI charging
- Stripe webhook handling for payment outcomes
- Mambu repayment posting for ledger sync on payment success
- Retry, overdue marking, and reconciliation report generation
- Borrower and tenant-admin APIs for installment/payment/reconciliation visibility

## Workflow Implemented
1. `EmiSchedulerJob` runs at `09:00 IST` and picks installments due today (`PENDING`, `RETRY_SCHEDULED`).
2. `EmiCollectionService` validates preconditions (tenant active, loan in active repayment, borrower/tenant Stripe IDs present).
3. Redis idempotency key is enforced: `idempotency:EMI:{mambuLoanId}:{emiNumber}:{tenantId}`.
4. Installment is marked `PROCESSING` and Stripe PaymentIntent is created with `confirm=true`, `off_session=true`.
5. `StripeWebhookController` verifies Stripe signature and acknowledges quickly.
6. `EmiWebhookProcessingService` handles async event processing:
   - `payment_intent.succeeded` -> `LedgerUpdateService` posts repayment to Mambu, marks installment `PAID`, persists transaction IDs, publishes `emi.collected`.
   - `payment_intent.payment_failed` -> marks `RETRY_SCHEDULED`, increments retry count, publishes `emi.failed`.
   - `payment_intent.requires_action` -> marks `REQUIRES_ACTION`.
   - `payment_intent.canceled` -> marks `FAILED`.
7. `RetryJob` runs at `18:00 IST` for `RETRY_SCHEDULED` installments.
8. Retry exhaustion marks installment `OVERDUE` and publishes `emi.overdue`.
9. `MambuRepaymentSyncRetryJob` retries Mambu posting every 5 minutes for `mambuSyncPending=true` records.
10. `ReconciliationJob` runs at `23:00 IST`, stores `reconciliation_reports`, and emits discrepancy event when mismatch is found.

## Data Model & Storage Added
- Migration `V10__create_flow6_emi_collection_reconciliation_tables.sql`
- `loan_installments` extended with Flow 6 tracking fields
- New table `emi_payment_attempts`
- New table `reconciliation_reports`
- Borrower + Tenant Stripe linkage fields added

## API Surface Added
- `GET /api/v1/loans/{applicationId}/installments`
- `GET /api/v1/loans/{applicationId}/payment-history`
- `GET /api/v1/admin/emi/due-today`
- `GET /api/v1/admin/emi/failed`
- `GET /api/v1/admin/emi/overdue`
- `POST /api/v1/admin/emi/{installmentId}/waive`
- `GET /api/v1/admin/reconciliation/{date}`
- Extended `POST /api/v1/webhooks/stripe` for Flow 6 payment intent events

## Events/Idempotency Implemented
- Kafka events: `emi.collected`, `emi.failed`, `emi.overdue`
- Redis idempotency + webhook dedupe keys implemented
- Mambu repayment uses same idempotency key as `externalId`

## Notes
- No test execution was performed in this phase, as requested.
- `mambu-mock-service` files were not modified or committed in this implementation sequence.
