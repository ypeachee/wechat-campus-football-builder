CREATE TABLE announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(500) NOT NULL,
    audience VARCHAR(20) NOT NULL,
    target_team_id BIGINT NULL,
    target_user_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    recipient_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_announcement_created (created_at, id),
    KEY idx_announcement_creator (created_by, created_at),
    CONSTRAINT fk_announcement_team FOREIGN KEY (target_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_user FOREIGN KEY (target_user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_creator FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_announcement_audience CHECK (audience IN ('ALL', 'ADMIN', 'TEAM_STAFF', 'TEAM', 'USER')),
    CONSTRAINT chk_announcement_target CHECK (
        (audience = 'TEAM' AND target_team_id IS NOT NULL AND target_user_id IS NULL)
        OR (audience = 'USER' AND target_user_id IS NOT NULL AND target_team_id IS NULL)
        OR (audience IN ('ALL', 'ADMIN', 'TEAM_STAFF') AND target_team_id IS NULL AND target_user_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;