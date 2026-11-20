CREATE TABLE budget_event_receipt (
    event_id CHAR(36) PRIMARY KEY,
    payload_hash CHAR(64) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE budget_claim_summary (
    workspace_id CHAR(36) NOT NULL,
    claim_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    PRIMARY KEY(workspace_id,claim_id)
) ENGINE=InnoDB;
