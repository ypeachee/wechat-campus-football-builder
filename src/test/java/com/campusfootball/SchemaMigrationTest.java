package com.campusfootball;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesAllTablesAndNeverCreatesLeagueStanding() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains(
                "app_user", "team_manager", "team", "player", "season",
                "season_team", "fixture", "fixture_appearance", "fixture_event", "audit_log");
        assertThat(tables).doesNotContain("league_standing");
    }

    @Test
    void rejectsDuplicateTeamManagerGrant() {
        long admin = insertUser("admin");
        long manager = insertUser("manager");
        long team = insertTeam("granted-team");

        jdbc.update("INSERT INTO team_manager (user_id, team_id, granted_by) VALUES (?, ?, ?)",
                manager, team, admin);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO team_manager (user_id, team_id, granted_by) VALUES (?, ?, ?)",
                manager, team, admin))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsDuplicateAppearanceForSameFixtureAndPlayer() {
        long user = insertUser("appearance-user");
        long season = insertSeason("appearance-season");
        long home = insertTeam("appearance-home");
        long away = insertTeam("appearance-away");
        long fixture = insertFixture(season, home, away, user, "SCHEDULED");
        long player = insertPlayer(home, "Appearance Player");

        jdbc.update("""
                INSERT INTO fixture_appearance
                    (fixture_id, team_id, player_id, is_starter, minutes_played, created_by)
                VALUES (?, ?, ?, 1, 90, ?)
                """, fixture, home, player, user);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO fixture_appearance
                    (fixture_id, team_id, player_id, is_starter, minutes_played, created_by)
                VALUES (?, ?, ?, 1, 90, ?)
                """, fixture, home, player, user))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsSameHomeAndAwayTeam() {
        long user = insertUser("fixture-user");
        long season = insertSeason("fixture-season");
        long team = insertTeam("fixture-team");

        assertThatThrownBy(() -> insertFixture(season, team, team, user, "DRAFT"))
                .isInstanceOf(DataAccessException.class);
    }

    private long insertUser(String prefix) {
        String openid = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user (wechat_openid, nickname, role_code, status)
                VALUES (?, ?, 'VIEWER', 'ACTIVE')
                """, openid, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE wechat_openid = ?", Long.class, openid);
    }

    private long insertTeam(String prefix) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO team (name, status) VALUES (?, 'ACTIVE')", name);
        return jdbc.queryForObject("SELECT id FROM team WHERE name = ?", Long.class, name);
    }

    private long insertSeason(String prefix) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO season (name, status) VALUES (?, 'ACTIVE')", name);
        return jdbc.queryForObject("SELECT id FROM season WHERE name = ?", Long.class, name);
    }

    private long insertFixture(long seasonId, long homeTeamId, long awayTeamId, long userId, String status) {
        jdbc.update("""
                INSERT INTO fixture (season_id, home_team_id, away_team_id, status, data_locked, created_by)
                VALUES (?, ?, ?, ?, 0, ?)
                """, seasonId, homeTeamId, awayTeamId, status, userId);
        return jdbc.queryForObject("""
                SELECT id FROM fixture
                WHERE season_id = ? AND home_team_id = ? AND away_team_id = ?
                ORDER BY id DESC LIMIT 1
                """, Long.class, seasonId, homeTeamId, awayTeamId);
    }

    private long insertPlayer(long teamId, String name) {
        String uniqueName = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO player (team_id, name, status) VALUES (?, ?, 'ACTIVE')", teamId, uniqueName);
        return jdbc.queryForObject("SELECT id FROM player WHERE name = ?", Long.class, uniqueName);
    }
}

