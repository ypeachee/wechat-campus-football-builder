package com.campusfootball.competition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class StageController {
    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @GetMapping("/stages")
    public List<StageResponse> list(@RequestParam long seasonId) {
        return stageService.findBySeason(seasonId).stream().map(StageResponse::from).toList();
    }

    public record StageResponse(long id, long seasonId, String name, StageType type,
                                int sortOrder, StageStatus status) {
        public static StageResponse from(Stage stage) {
            return new StageResponse(stage.getId(), stage.getSeasonId(), stage.getName(), stage.getType(),
                    stage.getSortOrder(), stage.getStatus());
        }
    }
}
