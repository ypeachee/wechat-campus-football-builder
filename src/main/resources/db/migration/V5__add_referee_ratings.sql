CREATE TABLE fixture_referee_rating (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    referee_name VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    stars TINYINT NOT NULL,
    edits_used TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_referee_rating_target (fixture_id, referee_name, user_id),
    KEY idx_referee_rating_name_created (referee_name, created_at),
    KEY idx_referee_rating_user_updated (user_id, updated_at),
    CONSTRAINT fk_referee_rating_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE RESTRICT,
    CONSTRAINT fk_referee_rating_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_referee_rating_stars CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT chk_referee_rating_edits CHECK (edits_used IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
