package com.campusfootball.rating;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public RefereeOwnRatingView mine(@PathVariable long fixtureId) {
        return service.findMine(currentUser.require().userId(), fixtureId)
                .map(rating -> new RefereeOwnRatingView(true, rating.getId(), rating.getStars(),
                        rating.getStars() * 2, service.canEdit(rating), rating.getCreatedAt(), rating.getUpdatedAt()))
                .orElseGet(RefereeOwnRatingView::none);
    }

    @PutMapping
    public RefereeRatingSubmission submit(@PathVariable long fixtureId,
                                          @Valid @RequestBody SubmitRefereeRatingRequest request) {
        return service.submit(currentUser.require().userId(), fixtureId, request.stars());
    }

    public record SubmitRefereeRatingRequest(@NotNull @Min(1) @Max(5) Integer stars) {}
}
