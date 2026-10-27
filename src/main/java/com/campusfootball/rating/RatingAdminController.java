package com.campusfootball.rating;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ratings")
public class RatingAdminController {
    private final RatingModerationService service;
    private final CurrentUser currentUser;

    public RatingAdminController(RatingModerationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public RatingModerationService.Page list(@RequestParam(defaultValue = "PENDING") String status,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(currentUser.require().userId(), status, page, size);
    }

    @PutMapping("/{ratingId}/status")
    public void moderate(@PathVariable long ratingId, @Valid @RequestBody ModerateRequest request) {
        service.moderate(currentUser.require().userId(), ratingId,
                request.status(), request.reason());
    }

    public record ModerateRequest(@NotNull RatingStatus status, @Size(max = 100) String reason) {}
}
