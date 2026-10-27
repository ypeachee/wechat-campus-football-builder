import { fetchFixtureDetail } from "../../services/competition";
import { fetchTeamPlayers } from "../../services/teams";
import type { FixtureDetail, PlayerSummary } from "../../types/domain";
import { formatMatchTime } from "../../utils/date";
import { errorMessage } from "../../utils/error";
import { eventTypeLabel, eventTypeTone, fixtureStatusLabel, fixtureStatusTone } from "../../utils/status";

type EventView = {
  id: number;
  minuteLabel: string;
  eventLabel: string;
  eventTone: "default" | "primary" | "success" | "warning" | "danger";
  teamName: string;
  playerName: string;
  relatedName: string;
  note: string;
};

type AppearanceView = {
  id: number;
  playerName: string;
  jerseyText: string;
  starterText: string;
  minutesText: string;
};

type FixtureDetailData = {
  loading: boolean;
  error: string;
  fixtureId: number;
  fixture: FixtureDetail | null;
  statusLabel: string;
  statusTone: "default" | "primary" | "success" | "warning" | "danger";
  timeLabel: string;
  eventViews: EventView[];
  homeAppearances: AppearanceView[];
  awayAppearances: AppearanceView[];
};

Page({
  data: {
    loading: true,
    error: "",
    fixtureId: 0,
    fixture: null as FixtureDetail | null,
    statusLabel: "",
    statusTone: "default",
    timeLabel: "",
    eventViews: [] as EventView[],
    homeAppearances: [] as AppearanceView[],
    awayAppearances: [] as AppearanceView[]
  } as FixtureDetailData,

  onLoad(options: Record<string, string | undefined>) {
    const fixtureId = Number(options.id ?? 0);
    this.setData({ fixtureId });
    void this.loadFixture(fixtureId);
  },

  onPullDownRefresh() {
    void this.loadFixture(this.data.fixtureId);
  },

  async loadFixture(fixtureId: number) {
    if (!fixtureId) {
      this.setData({ loading: false, error: "比赛不存在" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const fixture = await fetchFixtureDetail(fixtureId);
      const [homePlayers, awayPlayers] = await Promise.all([
        fetchTeamPlayers(fixture.homeTeam.id).catch(() => []),
        fetchTeamPlayers(fixture.awayTeam.id).catch(() => [])
      ]);
      const playerMap = new Map<number, PlayerSummary>();
      [...homePlayers, ...awayPlayers].forEach((player) => playerMap.set(player.id, player));
      const teamName = (teamId: number) => fixture.homeTeam.id === teamId
        ? fixture.homeTeam.name
        : fixture.awayTeam.name;
      const playerName = (playerId: number | null) => playerId === null
        ? ""
        : playerMap.get(playerId)?.name ?? `球员 #${playerId}`;

      this.setData({
        fixture,
        statusLabel: fixtureStatusLabel(fixture.status),
        statusTone: fixtureStatusTone(fixture.status),
        timeLabel: formatMatchTime(fixture.kickoffAt),
        eventViews: fixture.events.map((event) => ({
          id: event.id,
          minuteLabel: `${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'`,
          eventLabel: eventTypeLabel(event.eventType),
          eventTone: eventTypeTone(event.eventType),
          teamName: teamName(event.teamId),
          playerName: playerName(event.playerId),
          relatedName: playerName(event.relatedPlayerId),
          note: event.note ?? ""
        })),
        homeAppearances: buildAppearances(fixture, fixture.homeTeam.id, playerName),
        awayAppearances: buildAppearances(fixture, fixture.awayTeam.id, playerName)
      });
      wx.setNavigationBarTitle({ title: `${fixture.homeTeam.name} vs ${fixture.awayTeam.name}` });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  retry() {
    void this.loadFixture(this.data.fixtureId);
  }
});

function buildAppearances(
  fixture: FixtureDetail,
  teamId: number,
  playerName: (id: number | null) => string
): AppearanceView[] {
  return fixture.appearances
    .filter((appearance) => appearance.teamId === teamId)
    .map((appearance) => ({
      id: appearance.id,
      playerName: playerName(appearance.playerId),
      jerseyText: "",
      starterText: appearance.starter ? "首发" : "替补",
      minutesText: appearance.minutesPlayed === null ? "分钟未录" : `${appearance.minutesPlayed} 分钟`
    }));
}
