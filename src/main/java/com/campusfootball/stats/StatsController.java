package com.campusfootball.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats/scorers")
    public List<ScorerRow> scorers(@RequestParam long seasonId) {
        return statsService.scorers(seasonId);
    }

    @GetMapping("/stats/assists")
    public List<AssistRow> assists(@RequestParam long seasonId) {
        return statsService.assists(seasonId);
    }

    @GetMapping("/stats/appearances")
    public List<AppearanceRow> appearances(@RequestParam long seasonId) {
        return statsService.appearances(seasonId);
    }

    @GetMapping("/standings")
    public List<StandingRow> standings(@RequestParam long seasonId) {
        return statsService.standings(seasonId);
    }
}
