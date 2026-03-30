CREATE TABLE IF NOT EXISTS borrowers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    pan_encrypted VARCHAR(512) NOT NULL,
    aadhaar_last_four VARCHAR(4) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_city VARCHAR(100) NOT NULL,
    address_state VARCHAR(100) NOT NULL,
    address_pincode VARCHAR(15) NOT NULL,
    status VARCHAR(40) NOT NULL,
    mambu_client_id VARCHAR(100),
    mambu_client_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrowers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_borrowers_tenant_email ON borrowers (tenant_id, email);
CREATE UNIQUE INDEX IF NOT EXISTS uk_borrowers_tenant_pan ON borrowers (tenant_id, pan_encrypted);
CREATE INDEX IF NOT EXISTS idx_borrowers_tenant_status ON borrowers (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_borrowers_failed_mambu_sync ON borrowers (status, mambu_client_id);

CREATE TABLE IF NOT EXISTS kyc_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    document_number VARCHAR(128) NOT NULL,
    document_reference VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by VARCHAR(180),
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_kyc_documents_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers (id)
);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_tenant_borrower_active
    ON kyc_documents (tenant_id, borrower_id, is_archived);
CREATE INDEX IF NOT EXISTS idx_kyc_documents_pending_queue
    ON kyc_documents (tenant_id, status, is_archived, created_at);

CREATE TABLE IF NOT EXISTS credit_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    credit_score INTEGER NOT NULL,
    credit_bureau VARCHAR(30) NOT NULL,
    monthly_income NUMERIC(19, 2) NOT NULL,
    existing_emi_obligations NUMERIC(19, 2) NOT NULL,
    employment_type VARCHAR(40) NOT NULL,
    employer_name VARCHAR(180),
    employment_months INTEGER NOT NULL,
    dti_ratio NUMERIC(10, 2) NOT NULL,
    eligibility_category VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_credit_profiles_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_profiles_tenant_borrower ON credit_profiles (tenant_id, borrower_id);
CREATE INDEX IF NOT EXISTS idx_credit_profiles_tenant ON credit_profiles (tenant_id);
