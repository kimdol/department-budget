ALTER TABLE budget_posting ADD period_key VARCHAR2(7);
UPDATE budget_posting p SET period_key=(SELECT w.period_key FROM budget_workspace w WHERE w.workspace_id=p.workspace_id);
ALTER TABLE budget_posting MODIFY period_key NOT NULL;
ALTER TABLE budget_posting ADD original_kind VARCHAR2(20);
ALTER TABLE budget_posting ADD CONSTRAINT fk_posting_original FOREIGN KEY (claim_id,original_kind) REFERENCES budget_posting(claim_id,kind);
