ALTER TABLE budget_workspace ADD last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
CREATE INDEX budget_demo_activity ON budget_workspace(last_active_at,parent_workspace);
CREATE TABLE demo_portal_cleanup (
    workspace_id VARCHAR2(36) PRIMARY KEY
);
CREATE TABLE demo_receipt_cleanup (
    workspace_id VARCHAR2(36) NOT NULL REFERENCES demo_portal_cleanup(workspace_id),
    event_id VARCHAR2(36) PRIMARY KEY
);
