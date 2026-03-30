# Flow 2 Swagger Payload Files

Use these files directly in Swagger request body fields.

## Required token flow (important)
1. Call `POST /api/v1/auth/register` and copy borrower `accessToken`.
2. Use borrower token for `POST /api/v1/kyc/submit`.
3. Call `POST /api/v1/auth/login` with tenant admin credentials to get tenant admin token.
4. Use tenant admin token only for `PUT /api/v1/admin/kyc/{kycDocumentId}/verify` and `GET /api/v1/admin/kyc/pending`.
5. After admin marks all borrower docs as `VERIFIED`, use borrower token for `POST /api/v1/credit-profile`.
6. Use borrower token for `GET /api/v1/borrowers/me/sync-status` to confirm Mambu client sync state.

Borrower login uses `username=email` + `password` (tenantId optional).  
Tenant admin login requires `tenantId`.

`ACCESS_DENIED` on admin KYC verify with borrower token is expected by design.

## Endpoint -> File
- `POST /api/v1/auth/login` (tenant admin JWT): `01-auth-login-tenant-admin.json`
- `POST /api/v1/auth/register` valid: `02-register-borrower-valid.json`
- `POST /api/v1/auth/register` invalid tenant: `03-register-borrower-invalid-tenant.json`
- `POST /api/v1/auth/register` underage: `04-register-borrower-underage.json`
- `POST /api/v1/auth/login` (existing borrower JWT): `13-auth-login-borrower-existing.json`
- `POST /api/v1/kyc/submit` valid PAN + AADHAAR: `05-kyc-submit-valid-pan-aadhaar.json`
- `POST /api/v1/kyc/submit` valid PAN + ADDRESS_PROOF: `06-kyc-submit-valid-pan-address-proof.json`
- `POST /api/v1/kyc/submit` invalid (missing PAN): `07-kyc-submit-missing-pan.json`
- `PUT /api/v1/admin/kyc/{kycDocumentId}/verify` approve: `08-admin-kyc-verify-approved.json`
- `PUT /api/v1/admin/kyc/{kycDocumentId}/verify` reject: `09-admin-kyc-verify-rejected.json`
- `POST /api/v1/credit-profile` PRIME sample: `10-credit-profile-prime.json`
- `POST /api/v1/credit-profile` NEAR_PRIME sample: `11-credit-profile-near-prime.json`
- `POST /api/v1/credit-profile` SUBPRIME sample: `12-credit-profile-subprime.json`
- `GET /api/v1/borrowers/me/sync-status` (no request body): `14-borrower-sync-status-no-body.json`

Consolidated payload bundle: `swagger-flow2-borrower-payloads.json`
