ALTER TABLE tenants DROP COLUMN IF EXISTS mambu_loan_product_id;
ALTER TABLE tenants DROP COLUMN IF EXISTS loan_product_name;
ALTER TABLE tenants DROP COLUMN IF EXISTS min_loan_amount;
ALTER TABLE tenants DROP COLUMN IF EXISTS max_loan_amount;
ALTER TABLE tenants DROP COLUMN IF EXISTS min_tenure_months;
ALTER TABLE tenants DROP COLUMN IF EXISTS max_tenure_months;
ALTER TABLE tenants DROP COLUMN IF EXISTS annual_interest_rate;
ALTER TABLE tenants DROP COLUMN IF EXISTS processing_fee_percent;
ALTER TABLE tenants DROP COLUMN IF EXISTS prepayment_penalty_percent;
ALTER TABLE tenants DROP COLUMN IF EXISTS loan_currency;

CREATE TABLE IF NOT EXISTS loan_products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    mambu_product_id VARCHAR(100) NOT NULL,
    mambu_product_key VARCHAR(100) NOT NULL,
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
    CONSTRAINT fk_loan_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_loan_products_tenant_mambu UNIQUE (tenant_id, mambu_product_id)
);

INSERT INTO loan_products (
    id,
    tenant_id,
    mambu_product_id,
    mambu_product_key,
    loan_product_name,
    min_loan_amount,
    max_loan_amount,
    min_tenure_months,
    max_tenure_months,
    annual_interest_rate,
    processing_fee_percent,
    prepayment_penalty_percent,
    loan_currency,
    created_at,
    updated_at
)
SELECT
    id,
    tenant_id,
    mambu_loan_product_id,
    mambu_loan_product_encoded_key,
    loan_product_name,
    min_loan_amount,
    max_loan_amount,
    min_tenure_months,
    max_tenure_months,
    annual_interest_rate,
    processing_fee_percent,
    prepayment_penalty_percent,
    loan_currency,
    created_at,
    updated_at
FROM tenant_loan_products
WHERE NOT EXISTS (
    SELECT 1 FROM loan_products lp WHERE lp.id = tenant_loan_products.id
);

DROP TABLE IF EXISTS tenant_loan_products;

CREATE INDEX IF NOT EXISTS idx_loan_products_tenant_id ON loan_products (tenant_id);
