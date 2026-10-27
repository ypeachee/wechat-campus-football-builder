package com.campusfootball;

import com.campusfootball.auth.AdminBootstrap;
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

    public void grantTeamManager(long userId, long teamId, long grantedBy) {
        jdbc.update("INSERT INTO team_manager (user_id, team_id, granted_by) VALUES (?, ?, ?)",
                userId, teamId, grantedBy);
    }
}
