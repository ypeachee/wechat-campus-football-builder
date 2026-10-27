package com.campusfootball.competition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SeasonController {
    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @GetMapping("/seasons")
    public List<SeasonResponse> seasons(@RequestParam(required = false) Long competitionId) {
        return seasonService.findPublic(competitionId).stream().map(SeasonResponse::from).toList();
    }

    public record SeasonResponse(long id, long competitionId, String name, LocalDate startDate, LocalDate endDate, SeasonStatus status) {
        static SeasonResponse from(Season season) {
            return new SeasonResponse(season.getId(), season.getCompetitionId(), season.getName(), season.getStartDate(),
                    season.getEndDate(), season.getStatus());
        }
    }
}
