ALTER TABLE budget_workspace ADD parent_workspace VARCHAR2(36) REFERENCES budget_workspace(workspace_id);
ALTER TABLE budget_workspace ADD CONSTRAINT uq_demo_second_department UNIQUE(parent_workspace);
