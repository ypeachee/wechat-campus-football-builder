package com.campusfootball.competition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CompetitionController {
    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping("/competitions")
    public List<CompetitionResponse> list() {
        return competitionService.findActive().stream().map(CompetitionResponse::from).toList();
    }

    public record CompetitionResponse(long id, String name, CompetitionType type,
                                      String description, CompetitionStatus status) {
        public static CompetitionResponse from(Competition competition) {
            return new CompetitionResponse(competition.getId(), competition.getName(), competition.getType(),
                    competition.getDescription(), competition.getStatus());
        }
    }
}
