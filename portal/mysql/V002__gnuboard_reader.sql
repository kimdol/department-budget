CREATE TABLE budget_portal_reader (
    member_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    workspace_id CHAR(36) NOT NULL,
    PRIMARY KEY (member_id,workspace_id)
) ENGINE=InnoDB;
