import { fetchTeam, fetchTeamPlayers } from "../../services/teams";
import type { PlayerSummary, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type TeamDetailData = {
  loading: boolean;
  error: string;
  teamId: number;
  team: TeamSummary | null;
  players: PlayerSummary[];
};

Page({
  data: {
    loading: true,
    error: "",
    teamId: 0,
    team: null as TeamSummary | null,
    players: [] as PlayerSummary[]
  } as TeamDetailData,

  onLoad(options: Record<string, string | undefined>) {
    const teamId = Number(options.id ?? 0);
    this.setData({ teamId });
    void this.loadTeam(teamId);
  },

  onPullDownRefresh() {
    void this.loadTeam(this.data.teamId);
  },

  async loadTeam(teamId: number) {
    if (!teamId) {
      this.setData({ loading: false, error: "球队不存在" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const [team, players] = await Promise.all([fetchTeam(teamId), fetchTeamPlayers(teamId)]);
      this.setData({ team, players });
      wx.setNavigationBarTitle({ title: team.name });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  openPlayer(event: WechatMiniprogram.BaseEvent) {
    const playerId = event.currentTarget.dataset.id;
    if (playerId) {
      wx.navigateTo({ url: `/pages/player-detail/index?id=${playerId}` });
    }
  },

  retry() {
    void this.loadTeam(this.data.teamId);
  }
});
