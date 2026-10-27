import { fetchPublicFixtures } from "../../services/competition";
import { fetchSeasons, fetchTeams } from "../../services/teams";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../../state/season";
import type { FixtureCard, FixtureStatus, Season, TeamSummary } from "../../types/domain";
import { formatMatchTime } from "../../utils/date";
import { errorMessage } from "../../utils/error";

type StatusFilter = { value: "ALL" | FixtureStatus; label: string };

type FixturesData = {
  loading: boolean;
  error: string;
  seasons: Season[];
  selectedSeasonId: number;
  selectedSeasonName: string;
  selectedStatus: "ALL" | FixtureStatus;
  statusFilters: StatusFilter[];
  allFixtures: FixtureCard[];
  filteredFixtures: FixtureCard[];
};

Page({
  data: {
    loading: true,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonName: "",
    selectedStatus: "ALL",
    statusFilters: [
      { value: "ALL", label: "全部" },
      { value: "SCHEDULED", label: "未开始" },
      { value: "LIVE", label: "进行中" },
      { value: "FINISHED", label: "已完赛" },
      { value: "CANCELLED", label: "已取消" }
    ] as StatusFilter[],
    allFixtures: [] as FixtureCard[],
    filteredFixtures: [] as FixtureCard[]
  } as FixturesData,

  onLoad() {
    void this.bootstrap();
  },

  onPullDownRefresh() {
    void this.bootstrap();
  },

  async bootstrap() {
    this.setData({ loading: true, error: "" });
    try {
      const seasons = await fetchSeasons();
      const selectedSeasonId = selectDefaultSeason(seasons);
      this.setData({
        seasons,
        selectedSeasonId: selectedSeasonId ?? getSelectedSeasonId() ?? 0,
        selectedSeasonName: seasons.find((season) => season.id === selectedSeasonId)?.name ?? ""
      });
      if (selectedSeasonId !== null) {
        await this.loadFixtures(selectedSeasonId);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async loadFixtures(seasonId: number) {
    const [fixtures, teams] = await Promise.all([fetchPublicFixtures(seasonId), fetchTeams()]);
    const teamMap = new Map(teams.map((team) => [team.id, team.name]));
    const allFixtures = fixtures.map((fixture) => toCard(fixture, teamMap));
    this.setData({ allFixtures });
    this.applyStatus(this.data.selectedStatus, allFixtures);
  },

  handleSeasonChange(event: WechatMiniprogram.CustomEvent<{ seasonId: number }>) {
    const seasonId = event.detail.seasonId;
    setSelectedSeasonId(seasonId);
    this.setData({
      selectedSeasonId: seasonId,
      selectedSeasonName: this.data.seasons.find((season) => season.id === seasonId)?.name ?? "",
      loading: true,
      error: ""
    });
    void this.loadFixtures(seasonId)
      .catch((error) => this.setData({ error: errorMessage(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  handleStatusFilter(event: WechatMiniprogram.BaseEvent) {
    const selectedStatus = (event.currentTarget.dataset.value ?? "ALL") as "ALL" | FixtureStatus;
    this.setData({ selectedStatus });
    this.applyStatus(selectedStatus, this.data.allFixtures);
  },

  applyStatus(selectedStatus: "ALL" | FixtureStatus, fixtures: FixtureCard[]) {
    const filteredFixtures = selectedStatus === "ALL"
      ? fixtures
      : fixtures.filter((fixture) => fixture.status === selectedStatus);
    this.setData({ filteredFixtures });
  },

  openFixture(event: WechatMiniprogram.CustomEvent<{ fixtureId: number }>) {
    wx.navigateTo({ url: `/pages/fixture-detail/index?id=${event.detail.fixtureId}` });
  },

  retry() {
    void this.bootstrap();
  }
});

function toCard(
  fixture: ReturnType<typeof fetchPublicFixtures> extends Promise<infer T> ? T extends Array<infer U> ? U : never : never,
  teamMap: Map<number, string>
): FixtureCard {
  return {
    ...fixture,
    homeTeamName: teamMap.get(fixture.homeTeamId) ?? `球队 #${fixture.homeTeamId}`,
    awayTeamName: teamMap.get(fixture.awayTeamId) ?? `球队 #${fixture.awayTeamId}`,
    timeLabel: formatMatchTime(fixture.kickoffAt)
  };
}
