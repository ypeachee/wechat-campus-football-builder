-- Re-runnable World Cup 2022 sample for the CUGB campus football mini program.
-- Usage:
--   mysql --host=127.0.0.1 --port=3307 --user=campus --password=campus \
--     --database=campus_football --default-character-set=utf8mb4 \
--     < scripts/seed-world-cup-sample.sql

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO app_user (wechat_openid, nickname, role_code, status)
SELECT 'sample-seed-admin', '样本管理员', 'ADMIN', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE role_code = 'ADMIN');
SET @admin_id = (SELECT id FROM app_user WHERE role_code = 'ADMIN' ORDER BY id LIMIT 1);

SET @sample_name = '2022 卡塔尔世界杯（样本）';
SET @old_competition_id = (SELECT id FROM competition WHERE name = @sample_name ORDER BY id LIMIT 1);
SET @old_season_id = (SELECT id FROM season WHERE competition_id = @old_competition_id ORDER BY id LIMIT 1);

DELETE e FROM fixture_event e JOIN fixture f ON f.id = e.fixture_id WHERE f.season_id = @old_season_id;
DELETE a FROM fixture_appearance a JOIN fixture f ON f.id = a.fixture_id WHERE f.season_id = @old_season_id;
DELETE ks FROM knockout_seed ks JOIN stage st ON st.id = ks.stage_id WHERE st.season_id = @old_season_id;
UPDATE knockout_tie kt JOIN stage st ON st.id = kt.stage_id SET kt.next_tie_id = NULL WHERE st.season_id = @old_season_id;
DELETE f FROM fixture f WHERE f.season_id = @old_season_id;
DELETE kt FROM knockout_tie kt JOIN stage st ON st.id = kt.stage_id WHERE st.season_id = @old_season_id;
DELETE kr FROM knockout_round kr JOIN stage st ON st.id = kr.stage_id WHERE st.season_id = @old_season_id;
DELETE gt FROM group_team gt JOIN competition_group cg ON cg.id = gt.group_id JOIN stage st ON st.id = cg.stage_id WHERE st.season_id = @old_season_id;
DELETE cg FROM competition_group cg JOIN stage st ON st.id = cg.stage_id WHERE st.season_id = @old_season_id;
DELETE FROM stage WHERE season_id = @old_season_id;
DELETE FROM season_team WHERE season_id = @old_season_id;
DELETE FROM season WHERE id = @old_season_id;
DELETE FROM competition WHERE id = @old_competition_id;

INSERT INTO competition (name, type, description, status)
VALUES (@sample_name, 'CUP', '按 2022 卡塔尔世界杯射手榜前十球队制作的演示赛事', 'ACTIVE');
SET @competition_id = LAST_INSERT_ID();

INSERT INTO season (competition_id, name, start_date, end_date, status)
VALUES (@competition_id, '2022 卡塔尔世界杯', '2022-11-20', '2022-12-18', 'FINISHED');
SET @season_id = LAST_INSERT_ID();

INSERT INTO stage (season_id, name, type, sort_order, status)
VALUES (@season_id, '小组赛', 'GROUP', 1, 'FINISHED');
SET @group_stage_id = LAST_INSERT_ID();
INSERT INTO stage (season_id, name, type, sort_order, status)
VALUES (@season_id, '淘汰赛', 'KNOCKOUT', 2, 'FINISHED');
SET @knockout_stage_id = LAST_INSERT_ID();

INSERT INTO competition_group (stage_id, name, sort_order) VALUES (@group_stage_id, 'A组', 1);
SET @group_a_id = LAST_INSERT_ID();
INSERT INTO competition_group (stage_id, name, sort_order) VALUES (@group_stage_id, 'B组', 2);
SET @group_b_id = LAST_INSERT_ID();

INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '法国', 'FRA', '迪迪埃·德尚', '卢赛尔体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '法国');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '阿根廷', 'ARG', '利昂内尔·斯卡洛尼', '卢赛尔体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '阿根廷');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '英格兰', 'ENG', '加雷斯·索斯盖特', '海湾球场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '英格兰');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '葡萄牙', 'POR', '费尔南多·桑托斯', '974体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '葡萄牙');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '荷兰', 'NED', '路易斯·范加尔', '哈利法国际体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '荷兰');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '巴西', 'BRA', '蒂特', '卢赛尔体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '巴西');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '西班牙', 'ESP', '路易斯·恩里克', '教育城体育场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '西班牙');
INSERT INTO team (name, short_name, coach_name, home_venue, status)
SELECT '厄瓜多尔', 'ECU', '古斯塔沃·阿尔法罗', '海湾球场', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM team WHERE name = '厄瓜多尔');

SET @fra = (SELECT id FROM team WHERE name = '法国' ORDER BY id LIMIT 1);
SET @arg = (SELECT id FROM team WHERE name = '阿根廷' ORDER BY id LIMIT 1);
SET @eng = (SELECT id FROM team WHERE name = '英格兰' ORDER BY id LIMIT 1);
SET @por = (SELECT id FROM team WHERE name = '葡萄牙' ORDER BY id LIMIT 1);
SET @ned = (SELECT id FROM team WHERE name = '荷兰' ORDER BY id LIMIT 1);
SET @bra = (SELECT id FROM team WHERE name = '巴西' ORDER BY id LIMIT 1);
SET @esp = (SELECT id FROM team WHERE name = '西班牙' ORDER BY id LIMIT 1);
SET @ecu = (SELECT id FROM team WHERE name = '厄瓜多尔' ORDER BY id LIMIT 1);

INSERT INTO season_team (season_id, team_id) VALUES
(@season_id, @fra), (@season_id, @arg), (@season_id, @eng), (@season_id, @por),
(@season_id, @ned), (@season_id, @bra), (@season_id, @esp), (@season_id, @ecu);

INSERT INTO group_team (group_id, team_id, seed_no) VALUES
(@group_a_id, @fra, 1), (@group_a_id, @arg, 2), (@group_a_id, @eng, 3), (@group_a_id, @por, 4),
(@group_b_id, @ned, 1), (@group_b_id, @bra, 2), (@group_b_id, @esp, 3), (@group_b_id, @ecu, 4);

INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @fra, '姆巴佩', 10, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @fra AND name = '姆巴佩');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @fra, '吉鲁', 9, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @fra AND name = '吉鲁');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @fra, '格列兹曼', 7, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @fra AND name = '格列兹曼');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @fra, '图拉姆', 26, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @fra AND name = '图拉姆');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @arg, '梅西', 10, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @arg AND name = '梅西');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @arg, '阿尔瓦雷斯', 9, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @arg AND name = '阿尔瓦雷斯');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @arg, '迪马利亚', 11, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @arg AND name = '迪马利亚');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @arg, '麦卡利斯特', 20, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @arg AND name = '麦卡利斯特');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @eng, '萨卡', 17, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @eng AND name = '萨卡');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @eng, '凯恩', 9, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @eng AND name = '凯恩');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @eng, '贝林厄姆', 22, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @eng AND name = '贝林厄姆');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @por, '贡萨洛·拉莫斯', 26, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @por AND name = '贡萨洛·拉莫斯');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @por, '布鲁诺·费尔南德斯', 8, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @por AND name = '布鲁诺·费尔南德斯');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @por, '贝尔纳多·席尔瓦', 10, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @por AND name = '贝尔纳多·席尔瓦');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @ned, '加克波', 8, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @ned AND name = '加克波');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @ned, '德佩', 10, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @ned AND name = '德佩');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @ned, '德容', 21, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @ned AND name = '德容');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @bra, '理查利森', 9, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @bra AND name = '理查利森');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @bra, '内马尔', 10, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @bra AND name = '内马尔');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @bra, '维尼修斯', 20, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @bra AND name = '维尼修斯');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @esp, '莫拉塔', 7, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @esp AND name = '莫拉塔');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @esp, '奥尔莫', 21, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @esp AND name = '奥尔莫');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @esp, '佩德里', 26, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @esp AND name = '佩德里');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @ecu, '恩纳·瓦伦西亚', 13, '前锋', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @ecu AND name = '恩纳·瓦伦西亚');
INSERT INTO player (team_id, name, jersey_no, position, status)
SELECT @ecu, '凯塞多', 23, '中场', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM player WHERE team_id = @ecu AND name = '凯塞多');

SET @mbappe = (SELECT id FROM player WHERE team_id = @fra AND name = '姆巴佩' ORDER BY id LIMIT 1);
SET @giroud = (SELECT id FROM player WHERE team_id = @fra AND name = '吉鲁' ORDER BY id LIMIT 1);
SET @griezmann = (SELECT id FROM player WHERE team_id = @fra AND name = '格列兹曼' ORDER BY id LIMIT 1);
SET @thuram = (SELECT id FROM player WHERE team_id = @fra AND name = '图拉姆' ORDER BY id LIMIT 1);
SET @messi = (SELECT id FROM player WHERE team_id = @arg AND name = '梅西' ORDER BY id LIMIT 1);
SET @alvarez = (SELECT id FROM player WHERE team_id = @arg AND name = '阿尔瓦雷斯' ORDER BY id LIMIT 1);
SET @dimaria = (SELECT id FROM player WHERE team_id = @arg AND name = '迪马利亚' ORDER BY id LIMIT 1);
SET @macallister = (SELECT id FROM player WHERE team_id = @arg AND name = '麦卡利斯特' ORDER BY id LIMIT 1);
SET @saka = (SELECT id FROM player WHERE team_id = @eng AND name = '萨卡' ORDER BY id LIMIT 1);
SET @kane = (SELECT id FROM player WHERE team_id = @eng AND name = '凯恩' ORDER BY id LIMIT 1);
SET @bellingham = (SELECT id FROM player WHERE team_id = @eng AND name = '贝林厄姆' ORDER BY id LIMIT 1);
SET @ramos = (SELECT id FROM player WHERE team_id = @por AND name = '贡萨洛·拉莫斯' ORDER BY id LIMIT 1);
SET @bruno = (SELECT id FROM player WHERE team_id = @por AND name = '布鲁诺·费尔南德斯' ORDER BY id LIMIT 1);
SET @bernardo = (SELECT id FROM player WHERE team_id = @por AND name = '贝尔纳多·席尔瓦' ORDER BY id LIMIT 1);
SET @gakpo = (SELECT id FROM player WHERE team_id = @ned AND name = '加克波' ORDER BY id LIMIT 1);
SET @depay = (SELECT id FROM player WHERE team_id = @ned AND name = '德佩' ORDER BY id LIMIT 1);
SET @dejong = (SELECT id FROM player WHERE team_id = @ned AND name = '德容' ORDER BY id LIMIT 1);
SET @richarlison = (SELECT id FROM player WHERE team_id = @bra AND name = '理查利森' ORDER BY id LIMIT 1);
SET @neymar = (SELECT id FROM player WHERE team_id = @bra AND name = '内马尔' ORDER BY id LIMIT 1);
SET @vinicius = (SELECT id FROM player WHERE team_id = @bra AND name = '维尼修斯' ORDER BY id LIMIT 1);
SET @morata = (SELECT id FROM player WHERE team_id = @esp AND name = '莫拉塔' ORDER BY id LIMIT 1);
SET @olmo = (SELECT id FROM player WHERE team_id = @esp AND name = '奥尔莫' ORDER BY id LIMIT 1);
SET @pedri = (SELECT id FROM player WHERE team_id = @esp AND name = '佩德里' ORDER BY id LIMIT 1);
SET @valencia = (SELECT id FROM player WHERE team_id = @ecu AND name = '恩纳·瓦伦西亚' ORDER BY id LIMIT 1);
SET @caicedo = (SELECT id FROM player WHERE team_id = @ecu AND name = '凯塞多' ORDER BY id LIMIT 1);

INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 1, @fra, @eng, '2022-11-22 21:00:00', '卢赛尔体育场', 'FINISHED', 3, 1, 1, @admin_id);
SET @f_a1 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 1, @arg, @por, '2022-11-22 18:00:00', '974体育场', 'FINISHED', 3, 2, 1, @admin_id);
SET @f_a2 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 2, @fra, @arg, '2022-11-26 21:00:00', '卢赛尔体育场', 'FINISHED', 2, 2, 1, @admin_id);
SET @f_a3 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 2, @eng, @por, '2022-11-26 18:00:00', '海湾球场', 'FINISHED', 2, 1, 1, @admin_id);
SET @f_a4 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 3, @fra, @por, '2022-11-30 21:00:00', '974体育场', 'FINISHED', 2, 0, 1, @admin_id);
SET @f_a5 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_a_id, 3, @arg, @eng, '2022-11-30 21:00:00', '海湾球场', 'FINISHED', 2, 1, 1, @admin_id);
SET @f_a6 = LAST_INSERT_ID();

INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 1, @ned, @ecu, '2022-11-21 18:00:00', '哈利法国际体育场', 'FINISHED', 2, 1, 1, @admin_id);
SET @f_b1 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 1, @bra, @esp, '2022-11-21 21:00:00', '教育城体育场', 'FINISHED', 2, 1, 1, @admin_id);
SET @f_b2 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 2, @ned, @bra, '2022-11-25 18:00:00', '卢赛尔体育场', 'FINISHED', 1, 1, 1, @admin_id);
SET @f_b3 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 2, @esp, @ecu, '2022-11-25 21:00:00', '海湾球场', 'FINISHED', 1, 1, 1, @admin_id);
SET @f_b4 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 3, @ned, @bra, '2022-11-29 21:00:00', '974体育场', 'FINISHED', 1, 2, 1, @admin_id);
SET @f_b5 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, group_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @group_stage_id, @group_b_id, 3, @esp, @ecu, '2022-11-29 18:00:00', '哈利法国际体育场', 'FINISHED', 2, 1, 1, @admin_id);
SET @f_b6 = LAST_INSERT_ID();

INSERT INTO fixture_event (fixture_id, team_id, player_id, event_type, related_player_id, minute, stoppage_minute, created_by) VALUES
(@f_a1, @fra, @mbappe, 'GOAL', @griezmann, 12, 0, @admin_id),
(@f_a1, @fra, @mbappe, 'GOAL', @griezmann, 58, 0, @admin_id),
(@f_a1, @fra, @giroud, 'GOAL', @mbappe, 77, 0, @admin_id),
(@f_a1, @eng, @saka, 'GOAL', @bellingham, 43, 0, @admin_id),
(@f_a2, @arg, @messi, 'GOAL', @dimaria, 11, 0, @admin_id),
(@f_a2, @arg, @alvarez, 'GOAL', @messi, 31, 0, @admin_id),
(@f_a2, @arg, @messi, 'GOAL', @alvarez, 68, 0, @admin_id),
(@f_a2, @por, @ramos, 'GOAL', @bruno, 49, 0, @admin_id),
(@f_a2, @por, @ramos, 'GOAL', @bernardo, 82, 0, @admin_id),
(@f_a3, @fra, @mbappe, 'GOAL', @griezmann, 22, 0, @admin_id),
(@f_a3, @fra, @giroud, 'GOAL', @mbappe, 64, 0, @admin_id),
(@f_a3, @arg, @messi, 'GOAL', @dimaria, 39, 0, @admin_id),
(@f_a3, @arg, @macallister, 'GOAL', @messi, 74, 0, @admin_id),
(@f_a4, @eng, @saka, 'GOAL', @kane, 18, 0, @admin_id),
(@f_a4, @eng, @kane, 'GOAL', @bellingham, 70, 0, @admin_id),
(@f_a4, @por, @ramos, 'GOAL', @bruno, 55, 0, @admin_id),
(@f_a5, @fra, @mbappe, 'GOAL', @griezmann, 36, 0, @admin_id),
(@f_a5, @fra, @giroud, 'GOAL', @griezmann, 61, 0, @admin_id),
(@f_a6, @arg, @messi, 'GOAL', @dimaria, 27, 0, @admin_id),
(@f_a6, @arg, @alvarez, 'GOAL', @messi, 63, 0, @admin_id),
(@f_a6, @eng, @saka, 'GOAL', @kane, 80, 0, @admin_id),
(@f_b1, @ned, @gakpo, 'GOAL', @depay, 14, 0, @admin_id),
(@f_b1, @ned, @gakpo, 'GOAL', @dejong, 72, 0, @admin_id),
(@f_b1, @ecu, @valencia, 'GOAL', @caicedo, 45, 0, @admin_id),
(@f_b2, @bra, @richarlison, 'GOAL', @vinicius, 19, 0, @admin_id),
(@f_b2, @bra, @richarlison, 'GOAL', @neymar, 66, 0, @admin_id),
(@f_b2, @esp, @morata, 'GOAL', @pedri, 88, 0, @admin_id),
(@f_b3, @ned, @depay, 'GOAL', @dejong, 33, 0, @admin_id),
(@f_b3, @bra, @neymar, 'GOAL', @vinicius, 59, 0, @admin_id),
(@f_b4, @esp, @morata, 'GOAL', @olmo, 51, 0, @admin_id),
(@f_b4, @ecu, @valencia, 'GOAL', @caicedo, 76, 0, @admin_id),
(@f_b5, @ned, @gakpo, 'GOAL', @depay, 24, 0, @admin_id),
(@f_b5, @bra, @richarlison, 'GOAL', @vinicius, 41, 0, @admin_id),
(@f_b5, @bra, @neymar, 'GOAL', @richarlison, 83, 0, @admin_id),
(@f_b6, @esp, @morata, 'GOAL', @pedri, 17, 0, @admin_id),
(@f_b6, @esp, @olmo, 'GOAL', @morata, 62, 0, @admin_id),
(@f_b6, @ecu, @valencia, 'GOAL', @caicedo, 90, 0, @admin_id);

INSERT INTO knockout_round (stage_id, round_no, name, sort_order) VALUES (@knockout_stage_id, 1, '半决赛', 1);
SET @round_sf = LAST_INSERT_ID();
INSERT INTO knockout_round (stage_id, round_no, name, sort_order) VALUES (@knockout_stage_id, 2, '决赛', 2);
SET @round_final = LAST_INSERT_ID();

INSERT INTO knockout_tie (stage_id, round_id, bracket_position, home_team_id, away_team_id, winner_team_id, status)
VALUES (@knockout_stage_id, @round_sf, 1, @fra, @ned, @fra, 'FINISHED');
SET @sf1 = LAST_INSERT_ID();
INSERT INTO knockout_tie (stage_id, round_id, bracket_position, home_team_id, away_team_id, winner_team_id, status)
VALUES (@knockout_stage_id, @round_sf, 2, @bra, @arg, @arg, 'FINISHED');
SET @sf2 = LAST_INSERT_ID();
INSERT INTO knockout_tie (stage_id, round_id, bracket_position, home_team_id, away_team_id, winner_team_id, status)
VALUES (@knockout_stage_id, @round_final, 1, @arg, @fra, @arg, 'FINISHED');
SET @final_tie = LAST_INSERT_ID();

UPDATE knockout_tie SET next_tie_id = @final_tie, next_slot = 'AWAY' WHERE id = @sf1;
UPDATE knockout_tie SET next_tie_id = @final_tie, next_slot = 'HOME' WHERE id = @sf2;

INSERT INTO knockout_seed (stage_id, source_group_id, source_rank, target_tie_id, target_slot) VALUES
(@knockout_stage_id, @group_a_id, 1, @sf1, 'HOME'),
(@knockout_stage_id, @group_b_id, 2, @sf1, 'AWAY'),
(@knockout_stage_id, @group_b_id, 1, @sf2, 'HOME'),
(@knockout_stage_id, @group_a_id, 2, @sf2, 'AWAY');

INSERT INTO fixture (season_id, stage_id, knockout_tie_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @knockout_stage_id, @sf1, 1, @fra, @ned, '2022-12-14 21:00:00', '海湾球场', 'FINISHED', 2, 0, 1, @admin_id);
SET @f_sf1 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, knockout_tie_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, data_locked, created_by)
VALUES (@season_id, @knockout_stage_id, @sf2, 1, @bra, @arg, '2022-12-13 21:00:00', '卢赛尔体育场', 'FINISHED', 0, 2, 1, @admin_id);
SET @f_sf2 = LAST_INSERT_ID();
INSERT INTO fixture (season_id, stage_id, knockout_tie_id, round_no, home_team_id, away_team_id, kickoff_at, venue, status, home_score, away_score, home_penalty_score, away_penalty_score, data_locked, created_by)
VALUES (@season_id, @knockout_stage_id, @final_tie, 2, @arg, @fra, '2022-12-18 18:00:00', '卢赛尔体育场', 'FINISHED', 3, 3, 4, 2, 1, @admin_id);
SET @f_final = LAST_INSERT_ID();

INSERT INTO fixture_event (fixture_id, team_id, player_id, event_type, related_player_id, minute, stoppage_minute, created_by) VALUES
(@f_sf1, @fra, @mbappe, 'GOAL', @griezmann, 35, 0, @admin_id),
(@f_sf1, @fra, @giroud, 'GOAL', @mbappe, 69, 0, @admin_id),
(@f_sf2, @arg, @messi, 'GOAL', @dimaria, 42, 0, @admin_id),
(@f_sf2, @arg, @alvarez, 'GOAL', @messi, 78, 0, @admin_id),
(@f_final, @arg, @messi, 'GOAL', @dimaria, 23, 0, @admin_id),
(@f_final, @arg, @alvarez, 'GOAL', @messi, 54, 0, @admin_id),
(@f_final, @arg, @messi, 'GOAL', @dimaria, 108, 0, @admin_id),
(@f_final, @fra, @mbappe, 'GOAL', @griezmann, 80, 0, @admin_id),
(@f_final, @fra, @mbappe, 'GOAL', @griezmann, 81, 0, @admin_id),
(@f_final, @fra, @mbappe, 'GOAL', @thuram, 118, 0, @admin_id);

UPDATE fixture
SET referee = CASE
    WHEN group_id = @group_a_id THEN '马宁'
    WHEN group_id = @group_b_id THEN '傅明'
    WHEN round_no = 2 THEN '马宁'
    ELSE '张雷'
END
WHERE season_id = @season_id;

INSERT INTO fixture_appearance (fixture_id, team_id, player_id, is_starter, minutes_played, created_by)
SELECT f.id, p.team_id, p.id, 1, 90, @admin_id
FROM fixture f
JOIN player p ON p.team_id = f.home_team_id OR p.team_id = f.away_team_id
WHERE f.season_id = @season_id
  AND p.team_id IN (@fra, @arg, @eng, @por, @ned, @bra, @esp, @ecu)
ON DUPLICATE KEY UPDATE is_starter = VALUES(is_starter), minutes_played = VALUES(minutes_played);

COMMIT;

SELECT @competition_id AS competition_id, @season_id AS season_id,
       @group_stage_id AS group_stage_id, @knockout_stage_id AS knockout_stage_id,
       (SELECT COUNT(*) FROM season_team WHERE season_id = @season_id) AS teams,
       (SELECT COUNT(*) FROM fixture WHERE season_id = @season_id) AS fixtures,
       (SELECT COUNT(*) FROM fixture_event e JOIN fixture f ON f.id = e.fixture_id WHERE f.season_id = @season_id) AS events,
       (SELECT COUNT(*) FROM fixture_appearance a JOIN fixture f ON f.id = a.fixture_id WHERE f.season_id = @season_id) AS appearances;
