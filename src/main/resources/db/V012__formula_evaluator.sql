ALTER TABLE expense_claim ADD evaluator_version VARCHAR2(24) DEFAULT 'decimal-v1' NOT NULL;
ALTER TABLE policy_draft ADD evaluator_version VARCHAR2(24) DEFAULT 'decimal-v1' NOT NULL;
ALTER TABLE policy_publication ADD evaluator_version VARCHAR2(24) DEFAULT 'decimal-v1' NOT NULL;
