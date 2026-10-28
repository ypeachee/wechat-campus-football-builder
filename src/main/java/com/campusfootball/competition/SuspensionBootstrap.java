package com.campusfootball.competition;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SuspensionBootstrap implements ApplicationRunner {
    private final SuspensionMapper suspensionMapper;
    private final SuspensionService suspensionService;

    public SuspensionBootstrap(SuspensionMapper suspensionMapper, SuspensionService suspensionService) {
        this.suspensionMapper = suspensionMapper;
        this.suspensionService = suspensionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (long seasonId : suspensionMapper.findSeasonIdsWithFinishedCards()) {
            suspensionService.recalculateSeason(seasonId);
        }
    }
}
