CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wechat_openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64) NULL,
    nickname VARCHAR(64) NULL,
    avatar_url VARCHAR(512) NULL,
    role_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_openid (wechat_openid),
    CONSTRAINT chk_app_user_role CHECK (role_code IN ('ADMIN', 'TEAM_MANAGER', 'VIEWER')),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    short_name VARCHAR(32) NULL,
    logo_url VARCHAR(512) NULL,
    coach_name VARCHAR(64) NULL,
    home_venue VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_team_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_manager (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    granted_by BIGINT NOT NULL,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_manager_user_team (user_id, team_id),
    KEY idx_team_manager_team (team_id),
    CONSTRAINT fk_team_manager_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_manager_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_manager_granted_by FOREIGN KEY (granted_by) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    jersey_no SMALLINT NULL,
    position VARCHAR(20) NULL,
    birth_date DATE NULL,
    photo_url VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_player_team_status (team_id, status),
    CONSTRAINT fk_player_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT chk_player_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE season (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_season_status CHECK (status IN ('DRAFT', 'ACTIVE', 'FINISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE season_team (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_season_team (season_id, team_id),
    KEY idx_season_team_team (team_id),
    CONSTRAINT fk_season_team_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE RESTRICT,
    CONSTRAINT fk_season_team_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fixture (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    round_no INT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    kickoff_at DATETIME NULL,
    venue VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL,
    home_score SMALLINT NULL,
    away_score SMALLINT NULL,
    data_locked TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fixture_season_status_kickoff (season_id, status, kickoff_at),
    KEY idx_fixture_home_team (home_team_id),
    KEY idx_fixture_away_team (away_team_id),
    CONSTRAINT fk_fixture_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_home_team FOREIGN KEY (home_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_away_team FOREIGN KEY (away_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_fixture_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'FINISHED', 'CANCELLED')),
    CONSTRAINT chk_fixture_distinct_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT chk_fixture_score_pair CHECK ((home_score IS NULL AND away_score IS NULL) OR (home_score IS NOT NULL AND away_score IS NOT NULL)),
    CONSTRAINT chk_fixture_scores_nonnegative CHECK ((home_score IS NULL OR home_score >= 0) AND (away_score IS NULL OR away_score >= 0)),
    CONSTRAINT chk_fixture_finished_score CHECK (status <> 'FINISHED' OR (home_score IS NOT NULL AND away_score IS NOT NULL)),
    CONSTRAINT chk_fixture_data_locked CHECK (data_locked IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fixture_appearance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    is_starter TINYINT(1) NOT NULL DEFAULT 0,
    minutes_played SMALLINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixture_appearance_fixture_player (fixture_id, player_id),
    KEY idx_fixture_appearance_player_fixture (player_id, fixture_id),
    KEY idx_fixture_appearance_fixture_team (fixture_id, team_id),
    CONSTRAINT fk_fixture_appearance_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_appearance_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_appearance_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_appearance_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_fixture_appearance_starter CHECK (is_starter IN (0, 1)),
    CONSTRAINT chk_fixture_appearance_minutes CHECK (minutes_played IS NULL OR (minutes_played >= 0 AND minutes_played <= 130))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fixture_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fixture_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    related_player_id BIGINT NULL,
    minute SMALLINT NOT NULL,
    stoppage_minute SMALLINT NOT NULL DEFAULT 0,
    note VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fixture_event_fixture_team (fixture_id, team_id),
    KEY idx_fixture_event_player_type (player_id, event_type),
    KEY idx_fixture_event_related_player_type (related_player_id, event_type),
    CONSTRAINT fk_fixture_event_fixture FOREIGN KEY (fixture_id) REFERENCES fixture (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_event_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_event_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_event_related_player FOREIGN KEY (related_player_id) REFERENCES player (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fixture_event_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_fixture_event_type CHECK (event_type IN ('GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD')),
    CONSTRAINT chk_fixture_event_minute CHECK (minute >= 1 AND minute <= 130),
    CONSTRAINT chk_fixture_event_stoppage CHECK (stoppage_minute >= 0 AND stoppage_minute <= 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    team_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_log_team_created (team_id, created_at),
    KEY idx_audit_log_entity (entity_type, entity_id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_log_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
