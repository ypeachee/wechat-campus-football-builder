package com.campusfootball.rating;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fixtures/{fixtureId}/referee-rating")
public class RefereeRatingController {
    private final RefereeRatingService service;
    private final CurrentUser currentUser;

    public RefereeRatingController(RefereeRatingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public RefereeRatingSummary summary(@PathVariable long fixtureId) {
        return service.summary(fixtureId);
    }

    @GetMapping("/me")
    public RefereeOwnRatingView mine(@PathVariable long fixtureId,
                                     @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        Long userId = currentUser.optional().map(principal -> principal.userId()).orElse(null);
        var rating = userId != null
                ? service.findMine(userId, fixtureId)
                : service.findMineVisitor(requireVisitorId(visitorId), fixtureId);
        return rating.map(item -> new RefereeOwnRatingView(true, item.getId(), item.getStars(),
                        item.getStars() * 2, service.canEdit(item), item.getCreatedAt(), item.getUpdatedAt()))
                .orElseGet(RefereeOwnRatingView::none);
    }

    @PutMapping
    public RefereeRatingSubmission submit(@PathVariable long fixtureId,
                                          @Valid @RequestBody SubmitRefereeRatingRequest request,
                                          @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        Long userId = currentUser.optional().map(principal -> principal.userId()).orElse(null);
        return userId != null
                ? service.submit(userId, fixtureId, request.stars())
                : service.submitVisitor(requireVisitorId(visitorId), fixtureId, request.stars());
    }

    private String requireVisitorId(String visitorId) {
        String normalized = visitorId == null ? "" : visitorId.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "请提供访客标识或登录");
        }
        return normalized;
    }

    public record SubmitRefereeRatingRequest(@NotNull @Min(1) @Max(5) Integer stars) {}
}
