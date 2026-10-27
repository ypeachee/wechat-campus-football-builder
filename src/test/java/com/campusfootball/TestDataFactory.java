package com.campusfootball;

import com.campusfootball.auth.AdminBootstrap;
import com.campusfootball.competition.EventType;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import com.campusfootball.team.PlayerStatus;
import com.campusfootball.team.TeamStatus;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public class TestDataFactory {
    private static final String[] DELETE_ORDER = {
            "audit_log", "fixture_event", "fixture_appearance", "fixture", "season_team", "season",
            "team_manager", "player", "team", "app_user"
    };

    private final JdbcTemplate jdbc;
    private final AdminBootstrap adminBootstrap;

    public TestDataFactory(JdbcTemplate jdbc, AdminBootstrap adminBootstrap) {
        this.jdbc = jdbc;
        this.adminBootstrap = adminBootstrap;
    }

    public void reset() {
        for (String table : DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table);
        }
        adminBootstrap.ensureAdmin();
    }

    public long createUser(String openidPrefix, UserRole role) {
        String openid = openidPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user (wechat_openid, nickname, role_code, status)
                VALUES (?, ?, ?, ?)
                """, openid, openidPrefix, role.name(), UserStatus.ACTIVE.name());
        return jdbc.queryForObject("SELECT id FROM app_user WHERE wechat_openid = ?", Long.class, openid);
    }

    public long findUserId(String openid) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE wechat_openid = ?", Long.class, openid);
    }

    public long createTeam(String namePrefix) {
        return createTeam(namePrefix, TeamStatus.ACTIVE);
    }

    public long createTeam(String namePrefix, TeamStatus status) {
        String name = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO team (name, status) VALUES (?, ?)", name, status.name());
        return jdbc.queryForObject("SELECT id FROM team WHERE name = ?", Long.class, name);
    }

    public long createPlayer(long teamId, String namePrefix, Integer jerseyNo) {
        String name = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO player (team_id, name, jersey_no, status) VALUES (?, ?, ?, ?)",
                teamId, name, jerseyNo, PlayerStatus.ACTIVE.name());
        return jdbc.queryForObject("SELECT id FROM player WHERE name = ?", Long.class, name);
    }


    public long createSeason(String namePrefix, SeasonStatus status) {
        String name = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO season (name, status) VALUES (?, ?)", name, status.name());
        return jdbc.queryForObject("SELECT id FROM season WHERE name = ?", Long.class, name);
    }

    public void addSeasonTeam(long seasonId, long teamId) {
        jdbc.update("INSERT INTO season_team (season_id, team_id) VALUES (?, ?)", seasonId, teamId);
    }

    public long createFixture(long seasonId, long homeTeamId, long awayTeamId, FixtureStatus status,
                              long createdBy, Integer homeScore, Integer awayScore, boolean dataLocked) {
        jdbc.update("""
                INSERT INTO fixture (season_id, home_team_id, away_team_id, status, home_score, away_score,
                                     data_locked, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, seasonId, homeTeamId, awayTeamId, status.name(), homeScore, awayScore, dataLocked, createdBy);
        return jdbc.queryForObject("""
                SELECT id FROM fixture
                WHERE season_id = ? AND home_team_id = ? AND away_team_id = ?
                ORDER BY id DESC LIMIT 1
                """, Long.class, seasonId, homeTeamId, awayTeamId);
    }


    public long createAppearance(long fixtureId, long teamId, long playerId, long createdBy) {
        jdbc.update("""
                INSERT INTO fixture_appearance (fixture_id, team_id, player_id, is_starter, minutes_played, created_by)
                VALUES (?, ?, ?, 1, 90, ?)
                """, fixtureId, teamId, playerId, createdBy);
        return jdbc.queryForObject("""
                SELECT id FROM fixture_appearance WHERE fixture_id = ? AND player_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, fixtureId, playerId);
    }

    public long createEvent(long fixtureId, long teamId, long playerId, EventType eventType,
                            Long relatedPlayerId, int minute, long createdBy) {
        jdbc.update("""
                INSERT INTO fixture_event (fixture_id, team_id, player_id, event_type, related_player_id,
                                           minute, stoppage_minute, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """, fixtureId, teamId, playerId, eventType.name(), relatedPlayerId, minute, createdBy);
        return jdbc.queryForObject("""
                SELECT id FROM fixture_event WHERE fixture_id = ? AND player_id = ? AND event_type = ?
                ORDER BY id DESC LIMIT 1
                """, Long.class, fixtureId, playerId, eventType.name());
    }

    public void grantTeamManager(long userId, long teamId, long grantedBy) {
        jdbc.update("INSERT INTO team_manager (user_id, team_id, granted_by) VALUES (?, ?, ?)",
                userId, teamId, grantedBy);
    }
}
