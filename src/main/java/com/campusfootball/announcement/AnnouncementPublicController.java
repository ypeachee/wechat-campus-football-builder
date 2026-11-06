package com.campusfootball.announcement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementPublicController {
    private final AnnouncementService announcementService;

    public AnnouncementPublicController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<AnnouncementService.PublicAnnouncementView> latest(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return announcementService.publicList(limit);
    }
}
