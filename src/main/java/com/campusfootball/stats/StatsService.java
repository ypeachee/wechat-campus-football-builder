package com.campusfootball.stats;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.competition.StageMapper;
import com.campusfootball.competition.StageType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatsService {
    private final StatsMapper statsMapper;
    private final StageMapper stageMapper;

    public StatsService(StatsMapper statsMapper, StageMapper stageMapper) {
        this.statsMapper = statsMapper;
        this.stageMapper = stageMapper;
    }

    public List<ScorerRow> scorers(long seasonId) {
        return statsMapper.scorers(new StatsFilter(null, seasonId, null, null));
    }
    public List<AssistRow> assists(long seasonId) {
        return statsMapper.assists(new StatsFilter(null, seasonId, null, null));
    }
    public List<AppearanceRow> appearances(long seasonId) {
        return statsMapper.appearances(new StatsFilter(null, seasonId, null, null));
    }
    public List<ScorerRow> scorers(StatsFilter filter) { return statsMapper.scorers(filter); }
    public List<AssistRow> assists(StatsFilter filter) { return statsMapper.assists(filter); }
    public List<AppearanceRow> appearances(StatsFilter filter) { return statsMapper.appearances(filter); }

    public List<StandingRow> standings(long seasonId) {
        return standings(new StatsFilter(null, seasonId, null, null));
    }

    public List<StandingRow> standings(StatsFilter filter) {
        if (filter.getGroupId() != null) {
            return statsMapper.groupStandings(filter);
        }
        if (filter.getStageId() != null) {
            StageType type = stageMapper.findById(filter.getStageId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "阶段不存在"))
                    .getType();
            if (type == StageType.GROUP) {
                return statsMapper.groupStandings(filter);
            }
            if (type == StageType.KNOCKOUT) {
                throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "淘汰赛没有积分榜");
            }
            return statsMapper.leagueStandings(filter);
        }
        return statsMapper.leagueStandings(filter);
    }
}
