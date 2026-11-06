package com.campusfootball.news;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/news")
public class SiteNewsPublicController {
    private final SiteNewsService newsService;

    public SiteNewsPublicController(SiteNewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public SiteNewsService.PublicFeed feed(@RequestParam(defaultValue = "8") int limit) {
        return newsService.publicFeed(limit);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        SiteNews news = newsService.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(news.getImageContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(news.getImageData());
    }
}
