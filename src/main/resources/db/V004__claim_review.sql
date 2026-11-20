ALTER TABLE expense_claim ADD review_round NUMBER(10) DEFAULT 0 NOT NULL;
ALTER TABLE expense_claim ADD review_note VARCHAR2(240 CHAR);
CREATE TABLE claim_review_event (
    workspace_id VARCHAR2(36) NOT NULL,
    claim_id VARCHAR2(36) NOT NULL,
    review_round NUMBER(10) NOT NULL,
    event_type VARCHAR2(24) NOT NULL,
    actor_role VARCHAR2(16) NOT NULL,
    amount NUMBER(15),
    note VARCHAR2(240 CHAR),
    recorded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (claim_id, review_round, event_type),
    FOREIGN KEY (workspace_id, claim_id) REFERENCES expense_claim(workspace_id,claim_id)
);
