CREATE TABLE claim_evidence (
    workspace_id VARCHAR2(36) NOT NULL,
    claim_id VARCHAR2(36) NOT NULL,
    evidence_type VARCHAR2(24) NOT NULL,
    receipt_amount NUMBER(15) NOT NULL,
    added_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (claim_id, evidence_type),
    FOREIGN KEY (workspace_id, claim_id) REFERENCES expense_claim(workspace_id,claim_id)
);
