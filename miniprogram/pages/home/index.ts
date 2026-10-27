import { ApiError } from "../../services/http";
import { fetchPublicFixtures } from "../../services/competition";
import { fetchScorers, fetchStandings } from "../../services/stats";
import { fetchSeasons, fetchTeams } from "../../services/teams";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../../state/season";
import type { FixtureCard, RankingDisplayItem, Season } from "../../types/domain";
import { formatMatchTime } from "../../utils/date";

type HomeData = {
  loading: boolean;
  error: string;
  seasons: Season[];
  selectedSeasonId: number;
  selectedSeasonName: string;
  nextFixture: FixtureCard | null;
  standingItems: RankingDisplayItem[];
  scorerItems: RankingDisplayItem[];
};

Page({
  data: {
    loading: true,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonName: "",
    nextFixture: null as FixtureCard | null,
    standingItems: [] as RankingDisplayItem[],
    scorerItems: [] as RankingDisplayItem[]
  } as HomeData,

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
      const savedSeasonId = getSelectedSeasonId();
      const selectedSeasonId = selectDefaultSeason(seasons);
      this.setData({
        seasons,
        selectedSeasonId: selectedSeasonId ?? savedSeasonId ?? 0,
        selectedSeasonName: seasons.find((season) => season.id === selectedSeasonId)?.name ?? ""
      });
      if (selectedSeasonId !== null) {
        await this.loadSeason(selectedSeasonId);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async loadSeason(seasonId: number) {
    const [teams, standings, fixtures, scorers] = await Promise.all([
      fetchTeams(),
      fetchStandings(seasonId),
      fetchPublicFixtures(seasonId),
      fetchScorers(seasonId)
    ]);
    const teamNames = new Map(teams.map((team) => [team.id, team.name]));
    const fixture = fixtures.find((item) => item.status === "LIVE")
      ?? fixtures.find((item) => item.status === "SCHEDULED")
      ?? fixtures[0]
      ?? null;
    const nextFixture: FixtureCard | null = fixture
      ? {
          ...fixture,
          homeTeamName: teamNames.get(fixture.homeTeamId) ?? `球队 #${fixture.homeTeamId}`,
          awayTeamName: teamNames.get(fixture.awayTeamId) ?? `球队 #${fixture.awayTeamId}`,
          timeLabel: formatMatchTime(fixture.kickoffAt)
        }
      : null;

    this.setData({
      nextFixture,
      standingItems: standings.slice(0, 3).map((row, index) => ({
        id: row.teamId,
        rank: index + 1,
        name: row.teamName,
        teamName: `${row.played} 场`,
        value: row.points
      })),
      scorerItems: scorers.slice(0, 3).map((row, index) => ({
        id: row.playerId,
        rank: index + 1,
        name: row.playerName,
        teamName: row.teamName,
        value: row.goals
      }))
    });
  },

  async handleSeasonChange(event: WechatMiniprogram.CustomEvent<{ seasonId: number }>) {
    const seasonId = event.detail.seasonId;
    setSelectedSeasonId(seasonId);
    this.setData({
      selectedSeasonId: seasonId,
      selectedSeasonName: this.data.seasons.find((season) => season.id === seasonId)?.name ?? "",
      loading: true,
      error: ""
    });
    try {
      await this.loadSeason(seasonId);
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },

  openFixture(event: WechatMiniprogram.CustomEvent<{ fixtureId: number }>) {
    wx.navigateTo({ url: `/pages/fixture-detail/index?id=${event.detail.fixtureId}` });
  },

  goFixtures() {
    wx.switchTab({ url: "/pages/fixtures/index" });
  },

  goTeams() {
    wx.switchTab({ url: "/pages/teams/index" });
  },

  goStats() {
    wx.switchTab({ url: "/pages/stats/index" });
  },

  retry() {
    void this.bootstrap();
  }
});

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "加载失败，请稍后重试";
}
