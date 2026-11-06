package com.campusfootball.news;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/news")
public class SiteNewsAdminController {
    private final SiteNewsService newsService;
    private final CurrentUser currentUser;

    public SiteNewsAdminController(SiteNewsService newsService, CurrentUser currentUser) {
        this.newsService = newsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SiteNewsService.NewsView> list() {
        return newsService.adminList(currentUser.require().userId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SiteNewsService.NewsView create(@RequestParam String title,
                                           @RequestParam String description,
                                           @RequestParam String tag,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedAt,
                                           @RequestParam(defaultValue = "0") int displayOrder,
                                           @RequestParam(defaultValue = "true") boolean active,
                                           @RequestParam("image") MultipartFile image) {
        return newsService.create(currentUser.require().userId(), title, description, tag,
                publishedAt, displayOrder, active, image);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SiteNewsService.NewsView update(@PathVariable long id,
                                           @RequestParam String title,
                                           @RequestParam String description,
                                           @RequestParam String tag,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedAt,
                                           @RequestParam(defaultValue = "0") int displayOrder,
                                           @RequestParam(defaultValue = "true") boolean active,
                                           @RequestParam(value = "image", required = false) MultipartFile image) {
        return newsService.update(currentUser.require().userId(), id, title, description, tag,
                publishedAt, displayOrder, active, image);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        newsService.delete(currentUser.require().userId(), id);
    }

    @PostMapping("/seed-defaults")
    public List<SiteNewsService.NewsView> seedDefaults() {
        return newsService.seedDefaults(currentUser.require().userId());
    }
}
