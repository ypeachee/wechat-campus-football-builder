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
    public List<ScorerRow> scorers(@RequestParam(required = false) Long seasonId,
                                   @RequestParam(required = false) Long competitionId,
                                   @RequestParam(required = false) Long stageId,
                                   @RequestParam(required = false) Long groupId) {
        return statsService.scorers(filter(competitionId, seasonId, stageId, groupId));
    }

    @GetMapping("/stats/assists")
    public List<AssistRow> assists(@RequestParam(required = false) Long seasonId,
                                   @RequestParam(required = false) Long competitionId,
                                   @RequestParam(required = false) Long stageId,
                                   @RequestParam(required = false) Long groupId) {
        return statsService.assists(filter(competitionId, seasonId, stageId, groupId));
    }

    @GetMapping("/stats/appearances")
    public List<AppearanceRow> appearances(@RequestParam(required = false) Long seasonId,
                                           @RequestParam(required = false) Long competitionId,
                                           @RequestParam(required = false) Long stageId,
                                           @RequestParam(required = false) Long groupId) {
        return statsService.appearances(filter(competitionId, seasonId, stageId, groupId));
    }

    @GetMapping("/standings")
    public List<StandingRow> standings(@RequestParam(required = false) Long seasonId,
                                       @RequestParam(required = false) Long competitionId,
                                       @RequestParam(required = false) Long stageId,
                                       @RequestParam(required = false) Long groupId) {
        return statsService.standings(filter(competitionId, seasonId, stageId, groupId));
    }

    private StatsFilter filter(Long competitionId, Long seasonId, Long stageId, Long groupId) {
        return new StatsFilter(competitionId, seasonId, stageId, groupId);
    }
}
