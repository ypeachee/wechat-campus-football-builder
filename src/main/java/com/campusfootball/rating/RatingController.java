package com.campusfootball.rating;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RatingController {
    private final RatingService ratingService;
    private final RatingViewMapper viewMapper;
    private final CurrentUser currentUser;

    public RatingController(RatingService ratingService, RatingViewMapper viewMapper, CurrentUser currentUser) {
        this.ratingService = ratingService;
        this.viewMapper = viewMapper;
        this.currentUser = currentUser;
    }

    @GetMapping("/fixtures/{fixtureId}/player-ratings")
    public List<PlayerRatingSummary> fixtureRatings(@PathVariable long fixtureId) {
        return viewMapper.fixtureSummaries(fixtureId).stream().map(this::rounded).toList();
    }

    @GetMapping("/fixtures/{fixtureId}/players/{playerId}/ratings")
    public RatingCommentPage comments(@PathVariable long fixtureId, @PathVariable long playerId,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        PlayerRatingSummary summary = viewMapper.fixtureSummaries(fixtureId).stream()
                .filter(item -> item.playerId() == playerId)
                .findFirst()
                .orElse(null);
        return new RatingCommentPage(summary == null ? 0 : round(summary.averageScore()),
                summary == null ? 0 : summary.ratingCount(), page, size,
                viewMapper.countComments(fixtureId, playerId),
                viewMapper.comments(fixtureId, playerId, size, page * size));
    }

    @GetMapping("/players/{playerId}/rating-summary")
    public PlayerSeasonRatingSummary playerSummary(@PathVariable long playerId,
                                                   @RequestParam(required = false) Long seasonId) {
        PlayerSeasonRatingSummary summary = viewMapper.playerSeasonSummary(playerId, seasonId);
        return new PlayerSeasonRatingSummary(playerId, seasonId, round(summary.averageScore()), summary.ratingCount());
    }

    @GetMapping("/fixtures/{fixtureId}/player-ratings/me")
    public List<MyPlayerRatingState> mineForFixture(@PathVariable long fixtureId,
                                                     @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        Long userId = currentUser.optional().map(principal -> principal.userId()).orElse(null);
        return userId != null
                ? ratingService.findMineForFixture(userId, fixtureId)
                : ratingService.findMineForFixtureVisitor(requireVisitorId(visitorId), fixtureId);
    }

    @GetMapping("/fixtures/{fixtureId}/players/{playerId}/rating/me")
    public OwnRatingView mine(@PathVariable long fixtureId, @PathVariable long playerId,
                              @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        Long userId = currentUser.optional().map(principal -> principal.userId()).orElse(null);
        var rating = userId != null
                ? ratingService.findMine(userId, fixtureId, playerId)
                : ratingService.findMineVisitor(requireVisitorId(visitorId), fixtureId, playerId);
        return rating.map(this::ownView).orElseGet(OwnRatingView::none);
    }

    @PutMapping("/fixtures/{fixtureId}/players/{playerId}/rating")
    public RatingSubmissionResult submit(@PathVariable long fixtureId, @PathVariable long playerId,
                                         @Valid @RequestBody SubmitRatingRequest request,
                                         @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        Long userId = currentUser.optional().map(principal -> principal.userId()).orElse(null);
        return userId != null
                ? ratingService.submit(userId, fixtureId, playerId, request.stars(), request.comment())
                : ratingService.submitVisitor(requireVisitorId(visitorId), fixtureId, playerId, request.stars(), request.comment());
    }

    @PostMapping("/ratings/{ratingId}/report")
    public void report(@PathVariable long ratingId, @Valid @RequestBody ReportRequest request) {
        ratingService.report(currentUser.require().userId(), ratingId, request.reason());
    }

    private OwnRatingView ownView(PlayerMatchRating rating) {
        return new OwnRatingView(true, rating.getId(), rating.getStars(), rating.getStars() * 2,
                rating.getComment(), rating.getStatus(), rating.getModerationReason(),
                ratingService.canEdit(rating), rating.getCreatedAt(), rating.getUpdatedAt());
    }

    private String requireVisitorId(String visitorId) {
        String normalized = visitorId == null ? "" : visitorId.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "请提供访客标识或登录");
        }
        return normalized;
    }

    private PlayerRatingSummary rounded(PlayerRatingSummary summary) {
        return new PlayerRatingSummary(summary.playerId(), summary.playerName(), summary.jerseyNo(),
                summary.teamId(), summary.teamName(), round(summary.averageScore()), summary.ratingCount(),
                summary.star1Count(), summary.star2Count(), summary.star3Count(),
                summary.star4Count(), summary.star5Count());
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record SubmitRatingRequest(@NotNull @Min(1) @Max(5) Integer stars,
                                      @Size(max = 100) String comment) {}
    public record ReportRequest(@NotBlank @Size(max = 100) String reason) {}
}
