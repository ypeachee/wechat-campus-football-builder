ALTER TABLE competition
    ADD COLUMN yellow_suspension_threshold SMALLINT NOT NULL DEFAULT 3 AFTER status,
    ADD COLUMN red_suspension_matches SMALLINT NOT NULL DEFAULT 1 AFTER yellow_suspension_threshold,
    ADD COLUMN cards_cross_stages TINYINT(1) NOT NULL DEFAULT 1 AFTER red_suspension_matches,
    ADD CONSTRAINT chk_competition_yellow_threshold CHECK (yellow_suspension_threshold BETWEEN 1 AND 9),
    ADD CONSTRAINT chk_competition_red_matches CHECK (red_suspension_matches BETWEEN 1 AND 20),
    ADD CONSTRAINT chk_competition_cards_cross_stages CHECK (cards_cross_stages IN (0, 1));

ALTER TABLE player_suspension
    ADD COLUMN source_stage_id BIGINT NULL AFTER source_fixture_id,
    ADD KEY idx_player_suspension_source_stage (source_stage_id),
    ADD CONSTRAINT fk_player_suspension_source_stage FOREIGN KEY (source_stage_id) REFERENCES stage (id) ON DELETE RESTRICT;