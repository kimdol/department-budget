ALTER TABLE scheduled_policy ADD source_period VARCHAR2(7);
UPDATE scheduled_policy SET source_period = (
    SELECT w.period_key FROM budget_workspace w WHERE w.workspace_id = scheduled_policy.workspace_id
);
ALTER TABLE scheduled_policy MODIFY source_period NOT NULL;
ALTER TABLE budget_workspace ADD CONSTRAINT workspace_current_period_uq UNIQUE (workspace_id, period_key);
ALTER TABLE scheduled_policy ADD CONSTRAINT scheduled_current_period_fk
    FOREIGN KEY (workspace_id, source_period) REFERENCES budget_workspace(workspace_id, period_key);
