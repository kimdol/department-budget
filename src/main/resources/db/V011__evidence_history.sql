CREATE TABLE evidence_replacement (
    workspace_id VARCHAR2(36) NOT NULL,
    claim_id VARCHAR2(36) NOT NULL,
    evidence_type VARCHAR2(24) NOT NULL,
    version_no NUMBER(10) NOT NULL,
    previous_amount NUMBER(15) NOT NULL,
    replacement_amount NUMBER(15) NOT NULL,
    review_round NUMBER(10) NOT NULL,
    PRIMARY KEY (claim_id,evidence_type,version_no),
    FOREIGN KEY (workspace_id,claim_id) REFERENCES expense_claim(workspace_id,claim_id)
);
