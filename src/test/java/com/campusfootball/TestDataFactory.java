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
            "app_notification", "announcement", "audit_log", "rating_report", "player_match_rating", "fixture_referee_rating", "sensitive_term",
            "knockout_seed", "player_suspension_serving", "player_suspension", "fixture_event", "fixture_appearance", "fixture",
            "knockout_tie", "knockout_round", "group_team", "competition_group", "stage",
            "season_team", "season", "competition", "team_manager", "player", "team", "app_user"
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
        jdbc.update("INSERT INTO competition (name, type, status) VALUES (?, 'LEAGUE', 'ACTIVE')",
                "CUGB校园足球联赛");
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


    public long createCompetition(String namePrefix, String type) {
        String name = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO competition (name, type, status) VALUES (?, ?, 'ACTIVE')", name, type);
        return jdbc.queryForObject("SELECT id FROM competition WHERE name = ?", Long.class, name);
    }

    public long defaultCompetitionId() {
        return jdbc.queryForObject("SELECT id FROM competition WHERE type = 'LEAGUE' ORDER BY id LIMIT 1", Long.class);
    }

    public long createSeason(String namePrefix, SeasonStatus status) {
        String name = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO season (competition_id, name, status) VALUES (?, ?, ?)",
                defaultCompetitionId(), name, status.name());
        long seasonId = jdbc.queryForObject("SELECT id FROM season WHERE name = ?", Long.class, name);
        jdbc.update("INSERT INTO stage (season_id, name, type, sort_order, status) VALUES (?, '联赛阶段', 'LEAGUE', 0, 'ACTIVE')", seasonId);
        return seasonId;
    }

    public long createStage(long seasonId, String name, String type) {
        jdbc.update("INSERT INTO stage (season_id, name, type, sort_order, status) VALUES (?, ?, ?, 99, 'ACTIVE')",
                seasonId, name, type);
        return jdbc.queryForObject("SELECT id FROM stage WHERE season_id = ? AND name = ?", Long.class, seasonId, name);
    }

    public long createGroup(long stageId, String name) {
        jdbc.update("INSERT INTO competition_group (stage_id, name, sort_order) VALUES (?, ?, 0)", stageId, name);
        return jdbc.queryForObject("SELECT id FROM competition_group WHERE stage_id = ? AND name = ?", Long.class, stageId, name);
    }

    public void addGroupTeam(long groupId, long teamId) {
        jdbc.update("INSERT INTO group_team (group_id, team_id) VALUES (?, ?)", groupId, teamId);
    }

    public long leagueStageId(long seasonId) {
        return jdbc.queryForObject("SELECT id FROM stage WHERE season_id = ? AND type = 'LEAGUE' ORDER BY id LIMIT 1",
                Long.class, seasonId);
    }

    public void addSeasonTeam(long seasonId, long teamId) {
        jdbc.update("INSERT INTO season_team (season_id, team_id) VALUES (?, ?)", seasonId, teamId);
    }

    public long createFixture(long seasonId, long homeTeamId, long awayTeamId, FixtureStatus status,
                              long createdBy, Integer homeScore, Integer awayScore, boolean dataLocked) {
        long stageId = leagueStageId(seasonId);
        jdbc.update("""
                INSERT INTO fixture (season_id, stage_id, home_team_id, away_team_id, status,
                                     home_score, away_score, data_locked, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, seasonId, stageId, homeTeamId, awayTeamId, status.name(), homeScore, awayScore,
                dataLocked, createdBy);
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
        grantTeamManager(userId, teamId, grantedBy, UserRole.TEAM_MANAGER);
    }

    public void grantTeamManager(long userId, long teamId, long grantedBy, UserRole role) {
        jdbc.update("INSERT INTO team_manager (user_id, team_id, granted_by, role_code) VALUES (?, ?, ?, ?)",
                userId, teamId, grantedBy, role.name());
    }
}
