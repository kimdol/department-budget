ALTER TABLE budget_workspace ADD organization_type VARCHAR2(16) DEFAULT 'COMPANY' NOT NULL;
ALTER TABLE budget_workspace ADD CONSTRAINT ck_budget_org_type CHECK (organization_type IN ('BANK','HOSPITAL','COMPANY'));
