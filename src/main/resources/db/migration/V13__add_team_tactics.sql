CREATE TABLE team_tactic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    positions_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_tactic_team_name (team_id, name),
    KEY idx_team_tactic_team_updated (team_id, updated_at),
    CONSTRAINT fk_team_tactic_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_tactic_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_tactic_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
