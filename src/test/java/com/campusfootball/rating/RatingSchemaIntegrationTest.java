package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingSchemaIntegrationTest extends IntegrationTestBase {
    @Test
    void createsRatingReportAndSensitiveTermTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
                """, String.class);
        List<String> ratingColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'player_match_rating'
                """, String.class);

        assertThat(tables).contains("player_match_rating", "rating_report", "sensitive_term");
        assertThat(ratingColumns).contains(
                "fixture_id", "player_id", "team_id", "user_id", "stars", "comment",
                "status", "moderation_reason", "edits_used");
    }

    @Test
    void rejectsInvalidStarsAndDuplicateUserRating() {
        RatingFixture fixture = ratingFixture();
        jdbc.update("""
                INSERT INTO player_match_rating (fixture_id, player_id, team_id, user_id, stars, status)
                VALUES (?, ?, ?, ?, 5, 'VISIBLE')
                """, fixture.fixtureId(), fixture.playerId(), fixture.teamId(), fixture.userId());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO player_match_rating (fixture_id, player_id, team_id, user_id, stars, status)
                VALUES (?, ?, ?, ?, 4, 'VISIBLE')
                """, fixture.fixtureId(), fixture.playerId(), fixture.teamId(), fixture.userId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO player_match_rating (fixture_id, player_id, team_id, user_id, stars, status)
                VALUES (?, ?, ?, ?, 6, 'VISIBLE')
                """, fixture.fixtureId(), fixture.playerId(), fixture.teamId(), fixture.userId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsDuplicateReportsAndSensitiveTerms() {
        RatingFixture fixture = ratingFixture();
        long ratingId = insertRating(fixture);
        jdbc.update("INSERT INTO rating_report (rating_id, reporter_user_id, reason) VALUES (?, ?, '不当内容')",
                ratingId, fixture.userId());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO rating_report (rating_id, reporter_user_id, reason) VALUES (?, ?, '重复举报')",
                ratingId, fixture.userId())).isInstanceOf(DataAccessException.class);

        jdbc.update("""
                INSERT INTO sensitive_term (term, category, action, enabled, created_by)
                VALUES ('测试敏感词', '测试', 'REVIEW', 1, ?)
                """, fixture.userId());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sensitive_term (term, category, action, enabled, created_by)
                VALUES ('测试敏感词', '重复', 'BLOCK', 1, ?)
                """, fixture.userId())).isInstanceOf(DataAccessException.class);
    }

    private long insertRating(RatingFixture fixture) {
        jdbc.update("""
                INSERT INTO player_match_rating (fixture_id, player_id, team_id, user_id, stars, status)
                VALUES (?, ?, ?, ?, 5, 'VISIBLE')
                """, fixture.fixtureId(), fixture.playerId(), fixture.teamId(), fixture.userId());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private RatingFixture ratingFixture() {
        long userId = data.findUserId("openid-admin");
        long seasonId = data.createSeason("RatingSchema", SeasonStatus.ACTIVE);
        long home = data.createTeam("RatingHome");
        long away = data.createTeam("RatingAway");
        data.addSeasonTeam(seasonId, home);
        data.addSeasonTeam(seasonId, away);
        long fixtureId = data.createFixture(seasonId, home, away, FixtureStatus.FINISHED,
                userId, 1, 0, true);
        long playerId = data.createPlayer(home, "RatingPlayer", 10);
        data.createAppearance(fixtureId, home, playerId, userId);
        return new RatingFixture(fixtureId, playerId, home, userId);
    }

    private record RatingFixture(long fixtureId, long playerId, long teamId, long userId) {
    }
}
