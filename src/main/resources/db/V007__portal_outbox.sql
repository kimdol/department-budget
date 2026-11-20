CREATE TABLE portal_outbox (
    event_id VARCHAR2(36) PRIMARY KEY,
    workspace_id VARCHAR2(36) NOT NULL,
    claim_id VARCHAR2(36) NOT NULL,
    sequence_no NUMBER(3) NOT NULL,
    event_type VARCHAR2(20) NOT NULL,
    amount NUMBER(15) NOT NULL,
    attempts NUMBER(12) DEFAULT 0 NOT NULL,
    delivered NUMBER(1) DEFAULT 0 NOT NULL,
    last_error VARCHAR2(64),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    UNIQUE(claim_id,sequence_no),
    FOREIGN KEY (workspace_id,claim_id) REFERENCES expense_claim(workspace_id,claim_id)
);
CREATE INDEX portal_outbox_queue ON portal_outbox(delivered,created_at,event_id);
