DROP INDEX IF EXISTS uk_borrowers_email;
DROP INDEX IF EXISTS idx_borrowers_tenant_email;

CREATE UNIQUE INDEX IF NOT EXISTS uk_borrowers_tenant_email ON borrowers (tenant_id, email);
