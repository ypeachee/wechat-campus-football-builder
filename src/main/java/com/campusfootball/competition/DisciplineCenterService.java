package com.campusfootball.competition;

import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerMapper;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import com.campusfootball.user.UserRole;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DisciplineCenterService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final SuspensionService suspensionService;
    private final SuspensionMapper suspensionMapper;
    private final FixtureMapper fixtureMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final AuthorizationService authorizationService;

    public DisciplineCenterService(SuspensionService suspensionService, SuspensionMapper suspensionMapper,
                                   FixtureMapper fixtureMapper, TeamMapper teamMapper, PlayerMapper playerMapper,
                                   AuthorizationService authorizationService) {
        this.suspensionService = suspensionService;
        this.suspensionMapper = suspensionMapper;
        this.fixtureMapper = fixtureMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.authorizationService = authorizationService;
    }

    public DisciplineCenterView get(long actorUserId, long seasonId, Long teamId) {
        List<SuspensionService.SuspensionView> suspensions =
                suspensionService.findBySeasonForManagement(actorUserId, seasonId, teamId);
        UserRole role = authorizationService.currentRole(actorUserId);
        List<Long> managed = role == UserRole.TEAM_LEADER
                ? authorizationService.managedTeamIds(actorUserId) : List.of();
        List<CardHistoryView> cards = suspensionMapper.findFinishedCardEventsBySeason(seasonId).stream()
                .filter(card -> teamId == null || teamId.equals(card.getTeamId()))
                .filter(card -> role != UserRole.TEAM_LEADER || managed.contains(card.getTeamId()))
                .map(card -> toCardView(card))
                .toList();
        return new DisciplineCenterView(suspensions, cards);
    }

    private CardHistoryView toCardView(CardEventRow card) {
        Fixture fixture = fixtureMapper.findById(card.getFixtureId()).orElse(null);
        Team team = teamMapper.findById(card.getTeamId()).orElse(null);
        Player player = playerMapper.findById(card.getPlayerId()).orElse(null);
        return new CardHistoryView(card.getEventId(), card.getFixtureId(), fixtureLabel(fixture),
                card.getTeamId(), team == null ? "未知球队" : team.getName(),
                card.getPlayerId(), player == null ? null : player.getJerseyNo(),
                player == null ? "未知球员" : player.getName(), card.getEventType(),
                card.getMinute(), card.getKickoffAt());
    }

    private String fixtureLabel(Fixture fixture) {
        if (fixture == null) {
            return "比赛已删除";
        }
        String home = teamMapper.findById(fixture.getHomeTeamId()).map(Team::getName).orElse("主队");
        String away = teamMapper.findById(fixture.getAwayTeamId()).map(Team::getName).orElse("客队");
        String time = fixture.getKickoffAt() == null ? "" : fixture.getKickoffAt().format(TIME_FORMAT) + " ";
        return time + home + " vs " + away;
    }

    public record DisciplineCenterView(List<SuspensionService.SuspensionView> suspensions,
                                       List<CardHistoryView> cards) {
    }

    public record CardHistoryView(long eventId, long fixtureId, String fixtureLabel,
                                  long teamId, String teamName, long playerId, String jerseyNo,
                                  String playerName, EventType eventType, Integer minute,
                                  LocalDateTime kickoffAt) {
    }
}