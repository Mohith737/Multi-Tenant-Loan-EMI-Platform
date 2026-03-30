ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS mambu_branch_encoded_key VARCHAR(100);

CREATE TABLE IF NOT EXISTS tenant_loan_products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    mambu_loan_product_id VARCHAR(100) NOT NULL,
    mambu_loan_product_encoded_key VARCHAR(100) NOT NULL,
    loan_product_name VARCHAR(150),
    min_loan_amount NUMERIC(19,2),
    max_loan_amount NUMERIC(19,2),
    min_tenure_months INTEGER,
    max_tenure_months INTEGER,
    annual_interest_rate NUMERIC(10,4),
    processing_fee_percent NUMERIC(10,4),
    prepayment_penalty_percent NUMERIC(10,4),
    loan_currency VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_loan_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_tenant_loan_products_tenant_mambu UNIQUE (tenant_id, mambu_loan_product_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_loan_products_tenant_id ON tenant_loan_products (tenant_id);
