CREATE TABLE scheduled_policy (
    workspace_id VARCHAR2(36) NOT NULL,
    category VARCHAR2(20) NOT NULL,
    target_period VARCHAR2(7) NOT NULL,
    draft_id VARCHAR2(36) NOT NULL,
    review_hash VARCHAR2(64) NOT NULL,
    PRIMARY KEY (workspace_id, category),
    FOREIGN KEY (workspace_id, draft_id) REFERENCES policy_draft(workspace_id, draft_id)
);
