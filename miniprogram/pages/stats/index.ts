import { fetchAppearances, fetchAssists, fetchScorers, fetchStandings } from "../../services/stats";
import { fetchSeasons } from "../../services/teams";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../../state/season";
import type {
  AppearanceRow,
  AssistRow,
  RankingDisplayItem,
  ScorerRow,
  Season,
  StandingRow
} from "../../types/domain";
import { errorMessage } from "../../utils/error";

type StatsData = {
  loading: boolean;
  error: string;
  seasons: Season[];
  selectedSeasonId: number;
  selectedSeasonName: string;
  activeTab: string;
  standings: StandingRow[];
  scorerItems: RankingDisplayItem[];
  assistItems: RankingDisplayItem[];
  appearanceItems: RankingDisplayItem[];
};

Page({
  data: {
    loading: true,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonName: "",
    activeTab: "standings",
    standings: [] as StandingRow[],
    scorerItems: [] as RankingDisplayItem[],
    assistItems: [] as RankingDisplayItem[],
    appearanceItems: [] as RankingDisplayItem[]
  } as StatsData,

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
        await this.loadStats(selectedSeasonId);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async loadStats(seasonId: number) {
    const [standings, scorers, assists, appearances] = await Promise.all([
      fetchStandings(seasonId),
      fetchScorers(seasonId),
      fetchAssists(seasonId),
      fetchAppearances(seasonId)
    ]);
    this.setData({
      standings,
      scorerItems: scorers.map(scorerItem),
      assistItems: assists.map(assistItem),
      appearanceItems: appearances.map(appearanceItem)
    });
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
    void this.loadStats(seasonId)
      .catch((error) => this.setData({ error: errorMessage(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  handleTabChange(event: WechatMiniprogram.CustomEvent<{ value?: string | number }>) {
    const value = event.detail?.value;
    if (value !== undefined) {
      this.setData({ activeTab: String(value) });
    }
  },

  retry() {
    void this.bootstrap();
  }
});

function scorerItem(row: ScorerRow, index: number): RankingDisplayItem {
  return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.goals };
}

function assistItem(row: AssistRow, index: number): RankingDisplayItem {
  return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.assists };
}

function appearanceItem(row: AppearanceRow, index: number): RankingDisplayItem {
  return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.appearances };
}

