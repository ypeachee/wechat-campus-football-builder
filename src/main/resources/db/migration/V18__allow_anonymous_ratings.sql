ALTER TABLE player_match_rating DROP FOREIGN KEY fk_player_rating_user;
ALTER TABLE player_match_rating MODIFY user_id BIGINT NULL;
ALTER TABLE player_match_rating ADD COLUMN visitor_id VARCHAR(64) NULL AFTER user_id;
ALTER TABLE player_match_rating ADD UNIQUE KEY uk_player_rating_visitor_target (fixture_id, player_id, visitor_id);
ALTER TABLE player_match_rating ADD CONSTRAINT chk_player_rating_actor CHECK ((user_id IS NULL) <> (visitor_id IS NULL));
ALTER TABLE player_match_rating ADD CONSTRAINT fk_player_rating_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT;

ALTER TABLE fixture_referee_rating DROP FOREIGN KEY fk_referee_rating_user;
ALTER TABLE fixture_referee_rating MODIFY user_id BIGINT NULL;
ALTER TABLE fixture_referee_rating ADD COLUMN visitor_id VARCHAR(64) NULL AFTER user_id;
ALTER TABLE fixture_referee_rating ADD UNIQUE KEY uk_referee_rating_visitor_target (fixture_id, referee_name, visitor_id);
ALTER TABLE fixture_referee_rating ADD CONSTRAINT chk_referee_rating_actor CHECK ((user_id IS NULL) <> (visitor_id IS NULL));
ALTER TABLE fixture_referee_rating ADD CONSTRAINT fk_referee_rating_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT;
