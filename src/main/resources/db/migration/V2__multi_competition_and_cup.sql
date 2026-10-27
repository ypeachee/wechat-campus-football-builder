CREATE TABLE competition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_competition_type_status (type, status),
    CONSTRAINT chk_competition_type CHECK (type IN ('LEAGUE', 'CUP')),
    CONSTRAINT chk_competition_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO competition (name, type, description, status)
VALUES ('CUGB校园足球联赛', 'LEAGUE', '默认迁移赛事', 'ACTIVE');

ALTER TABLE season
    ADD COLUMN competition_id BIGINT NULL AFTER id;

UPDATE season SET competition_id = (SELECT id FROM competition WHERE name = 'CUGB校园足球联赛' LIMIT 1);

ALTER TABLE season
    MODIFY COLUMN competition_id BIGINT NOT NULL,
    ADD KEY idx_season_competition (competition_id),
    ADD CONSTRAINT fk_season_competition FOREIGN KEY (competition_id) REFERENCES competition (id) ON DELETE RESTRICT;

CREATE TABLE stage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stage_season_name (season_id, name),
    KEY idx_stage_season_sort (season_id, sort_order),
    CONSTRAINT fk_stage_season FOREIGN KEY (season_id) REFERENCES season (id) ON DELETE RESTRICT,
    CONSTRAINT chk_stage_type CHECK (type IN ('LEAGUE', 'GROUP', 'KNOCKOUT')),
    CONSTRAINT chk_stage_status CHECK (status IN ('DRAFT', 'ACTIVE', 'FINISHED')),
    CONSTRAINT chk_stage_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO stage (season_id, name, type, sort_order, status)
SELECT id, '联赛阶段', 'LEAGUE', 0,
       CASE status
           WHEN 'DRAFT' THEN 'DRAFT'
           WHEN 'ACTIVE' THEN 'ACTIVE'
           ELSE 'FINISHED'
       END
FROM season;

ALTER TABLE fixture
    ADD COLUMN stage_id BIGINT NULL AFTER season_id,
    ADD COLUMN group_id BIGINT NULL AFTER stage_id,
    ADD COLUMN home_penalty_score SMALLINT NULL AFTER away_score,
    ADD COLUMN away_penalty_score SMALLINT NULL AFTER home_penalty_score;

UPDATE fixture f
JOIN stage s ON s.season_id = f.season_id AND s.type = 'LEAGUE' AND s.name = '联赛阶段'
SET f.stage_id = s.id;

ALTER TABLE fixture
    MODIFY COLUMN stage_id BIGINT NOT NULL,
    ADD KEY idx_fixture_stage_group (stage_id, group_id),
    ADD CONSTRAINT fk_fixture_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_fixture_penalty_pair CHECK ((home_penalty_score IS NULL AND away_penalty_score IS NULL) OR (home_penalty_score IS NOT NULL AND away_penalty_score IS NOT NULL)),
    ADD CONSTRAINT chk_fixture_penalties_nonnegative CHECK ((home_penalty_score IS NULL OR home_penalty_score >= 0) AND (away_penalty_score IS NULL OR away_penalty_score >= 0));

CREATE TABLE competition_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_competition_group_stage_name (stage_id, name),
    KEY idx_competition_group_stage_sort (stage_id, sort_order),
    CONSTRAINT fk_competition_group_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE RESTRICT,
    CONSTRAINT chk_competition_group_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_team (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    seed_no INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_team_group_team (group_id, team_id),
    KEY idx_group_team_team (team_id),
    CONSTRAINT fk_group_team_group FOREIGN KEY (group_id) REFERENCES competition_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_group_team_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE fixture
    ADD CONSTRAINT fk_fixture_group FOREIGN KEY (group_id) REFERENCES competition_group (id) ON DELETE RESTRICT;

CREATE TABLE knockout_round (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knockout_round_stage_round (stage_id, round_no),
    KEY idx_knockout_round_stage_sort (stage_id, sort_order),
    CONSTRAINT fk_knockout_round_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knockout_tie (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    bracket_position INT NOT NULL,
    home_team_id BIGINT NULL,
    away_team_id BIGINT NULL,
    winner_team_id BIGINT NULL,
    next_tie_id BIGINT NULL,
    next_slot VARCHAR(10) NULL,
    is_third_place TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knockout_tie_round_position (round_id, bracket_position),
    KEY idx_knockout_tie_stage (stage_id),
    KEY idx_knockout_tie_next (next_tie_id),
    CONSTRAINT fk_knockout_tie_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_tie_round FOREIGN KEY (round_id) REFERENCES knockout_round (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_tie_home_team FOREIGN KEY (home_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_tie_away_team FOREIGN KEY (away_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_tie_winner_team FOREIGN KEY (winner_team_id) REFERENCES team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_tie_next_tie FOREIGN KEY (next_tie_id) REFERENCES knockout_tie (id) ON DELETE SET NULL,
    CONSTRAINT chk_knockout_tie_next_slot CHECK (next_slot IS NULL OR next_slot IN ('HOME', 'AWAY')),
    CONSTRAINT chk_knockout_tie_third_place CHECK (is_third_place IN (0, 1)),
    CONSTRAINT chk_knockout_tie_status CHECK (status IN ('PENDING', 'READY', 'FINISHED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knockout_seed (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    source_group_id BIGINT NOT NULL,
    source_rank INT NOT NULL,
    target_tie_id BIGINT NOT NULL,
    target_slot VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knockout_seed_source (stage_id, source_group_id, source_rank),
    UNIQUE KEY uk_knockout_seed_target (target_tie_id, target_slot),
    CONSTRAINT fk_knockout_seed_stage FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_seed_group FOREIGN KEY (source_group_id) REFERENCES competition_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knockout_seed_tie FOREIGN KEY (target_tie_id) REFERENCES knockout_tie (id) ON DELETE RESTRICT,
    CONSTRAINT chk_knockout_seed_rank CHECK (source_rank >= 1),
    CONSTRAINT chk_knockout_seed_slot CHECK (target_slot IN ('HOME', 'AWAY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE fixture
    ADD COLUMN knockout_tie_id BIGINT NULL AFTER group_id,
    ADD UNIQUE KEY uk_fixture_knockout_tie (knockout_tie_id),
    ADD CONSTRAINT fk_fixture_knockout_tie FOREIGN KEY (knockout_tie_id) REFERENCES knockout_tie (id) ON DELETE RESTRICT;
