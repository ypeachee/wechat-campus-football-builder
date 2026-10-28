package com.campusfootball.announcement;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/announcements")
public class AnnouncementAdminController {
    private final AnnouncementService announcementService;
    private final CurrentUser currentUser;

    public AnnouncementAdminController(AnnouncementService announcementService, CurrentUser currentUser) {
        this.announcementService = announcementService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AnnouncementService.Page list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return announcementService.list(currentUser.require().userId(), page, size);
    }

    @GetMapping("/{id}")
    public AnnouncementService.AnnouncementView detail(@PathVariable long id) {
        return announcementService.detail(currentUser.require().userId(), id);
    }

    @PostMapping
    public AnnouncementService.AnnouncementView create(@Valid @RequestBody CreateAnnouncementRequest request) {
        return announcementService.create(currentUser.require().userId(), request.title(), request.content(),
                request.audience(), request.targetTeamId(), request.targetUserId());
    }

    public record CreateAnnouncementRequest(@NotBlank @Size(max = 120) String title,
                                            @NotBlank @Size(max = 500) String content,
                                            @NotNull AnnouncementAudience audience,
                                            Long targetTeamId,
                                            Long targetUserId) {
    }
}