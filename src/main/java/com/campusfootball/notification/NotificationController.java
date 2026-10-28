package com.campusfootball.notification;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public NotificationService.Page list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "30") int size) {
        return notificationService.list(currentUser.require().userId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount() {
        return Map.of("count", notificationService.unreadCount(currentUser.require().userId()));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable long id) {
        notificationService.markRead(currentUser.require().userId(), id);
    }

    @PutMapping("/read-all")
    public void markAllRead() {
        notificationService.markAllRead(currentUser.require().userId());
    }
}