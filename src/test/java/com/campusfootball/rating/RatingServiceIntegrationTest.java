package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.common.api.AppException;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RatingServiceIntegrationTest extends IntegrationTestBase {
    @Autowired
    private RatingService ratingService;

    @Autowired
    private RatingMapper ratingMapper;

    @MockBean
    private ContentModerationService contentModerationService;

    @BeforeEach
    void stubModeration() {
        when(contentModerationService.evaluate(anyLong(), anyString())).thenReturn(ModerationResult.pass());
    }

    @Test
    void rejectsInvalidStarsUnfinishedFixtureAndPlayerWithoutAppearance() {
        RatingFixture fixture = fixture(FixtureStatus.SCHEDULED, true);
        long user = fixture.userId();

        assertThatThrownBy(() -> ratingService.submit(user, fixture.fixtureId(), fixture.playerId(), 0, null))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> ratingService.submit(user, fixture.fixtureId(), fixture.playerId(), 6, null))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> ratingService.submit(user, fixture.fixtureId(), fixture.playerId(), 5, null))
                .isInstanceOf(AppException.class);

        RatingFixture finishedWithoutAppearance = fixture(FixtureStatus.FINISHED, false);
        assertThatThrownBy(() -> ratingService.submit(finishedWithoutAppearance.userId(),
                finishedWithoutAppearance.fixtureId(), finishedWithoutAppearance.playerId(), 5, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    void createsVisibleRatingAndCalculatesTenPointScore() {
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, true);

        RatingSubmissionResult result = ratingService.submit(fixture.userId(), fixture.fixtureId(), fixture.playerId(), 4, null);

        assertThat(result.stars()).isEqualTo(4);
        assertThat(result.score()).isEqualTo(8);
        assertThat(result.status()).isEqualTo(RatingStatus.VISIBLE);
        assertThat(ratingMapper.findByTarget(fixture.fixtureId(), fixture.playerId(), fixture.userId()))
                .get().extracting(PlayerMatchRating::getStars).isEqualTo(4);
    }

    @Test
    void mapsReviewAndBlockModerationToPendingAndRejected() {
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, true);
        when(contentModerationService.evaluate(fixture.userId(), "需要审核"))
                .thenReturn(ModerationResult.review("审核词"));
        when(contentModerationService.evaluate(fixture.userId(), "直接拦截"))
                .thenReturn(ModerationResult.block("禁发词"));

        RatingSubmissionResult pending = ratingService.submit(
                fixture.userId(), fixture.fixtureId(), fixture.playerId(), 5, "需要审核");
        assertThat(pending.status()).isEqualTo(RatingStatus.PENDING);

        RatingFixture second = fixture(FixtureStatus.FINISHED, true);
        RatingSubmissionResult rejected = ratingService.submit(
                second.userId(), second.fixtureId(), second.playerId(), 5, "直接拦截");
        assertThat(rejected.status()).isEqualTo(RatingStatus.REJECTED);
    }

    @Test
    void allowsOneEditWithinTwentyFourHoursThenLocks() {
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, true);
        RatingSubmissionResult first = ratingService.submit(
                fixture.userId(), fixture.fixtureId(), fixture.playerId(), 3, null);
        RatingSubmissionResult edited = ratingService.submit(
                fixture.userId(), fixture.fixtureId(), fixture.playerId(), 5, "改判");
        assertThat(edited.stars()).isEqualTo(5);
        assertThatThrownBy(() -> ratingService.submit(
                fixture.userId(), fixture.fixtureId(), fixture.playerId(), 4, "再次修改"))
                .isInstanceOf(AppException.class);

        RatingFixture expired = fixture(FixtureStatus.FINISHED, true);
        ratingService.submit(expired.userId(), expired.fixtureId(), expired.playerId(), 3, null);
        jdbc.update("UPDATE player_match_rating SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(25), ratingMapper.findByTarget(
                        expired.fixtureId(), expired.playerId(), expired.userId()).orElseThrow().getId());
        assertThatThrownBy(() -> ratingService.submit(
                expired.userId(), expired.fixtureId(), expired.playerId(), 4, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rateLimitsMoreThanTwentySubmissionsInTenMinutes() {
        RatingFixture target = fixture(FixtureStatus.FINISHED, true);
        for (int i = 0; i < 20; i++) {
            long playerId = data.createPlayer(target.homeTeamId(), "LimitPlayer" + i, i + 1);
            jdbc.update("""
                    INSERT INTO player_match_rating
                        (fixture_id, player_id, team_id, user_id, stars, status, edits_used)
                    VALUES (?, ?, ?, ?, 5, 'VISIBLE', 0)
                    """, target.fixtureId(), playerId, target.homeTeamId(), target.userId());
        }

        assertThatThrownBy(() -> ratingService.submit(
                target.userId(), target.fixtureId(), target.playerId(), 5, null))
                .isInstanceOf(AppException.class);
    }

    private RatingFixture fixture(FixtureStatus status, boolean withAppearance) {
        long userId = data.findUserId("openid-admin");
        long seasonId = data.createSeason("RatingService", SeasonStatus.ACTIVE);
        long home = data.createTeam("RatingHome");
        long away = data.createTeam("RatingAway");
        data.addSeasonTeam(seasonId, home);
        data.addSeasonTeam(seasonId, away);
        long fixtureId = data.createFixture(seasonId, home, away, status, userId, 1, 0, true);
        long playerId = data.createPlayer(home, "RatingPlayer", 10);
        if (withAppearance) {
            data.createAppearance(fixtureId, home, playerId, userId);
        }
        return new RatingFixture(fixtureId, playerId, home, userId);
    }

    private record RatingFixture(long fixtureId, long playerId, long homeTeamId, long userId) {
    }
}
