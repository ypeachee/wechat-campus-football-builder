import { fetchTeams } from "../../services/teams";
import type { TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type TeamsData = {
  loading: boolean;
  error: string;
  keyword: string;
  teams: TeamSummary[];
  filteredTeams: TeamSummary[];
};

Page({
  data: {
    loading: true,
    error: "",
    keyword: "",
    teams: [] as TeamSummary[],
    filteredTeams: [] as TeamSummary[]
  } as TeamsData,

  onShow() {
    void this.loadTeams();
  },

  onPullDownRefresh() {
    void this.loadTeams();
  },

  async loadTeams() {
    this.setData({ loading: true, error: "" });
    try {
      const teams = await fetchTeams();
      this.setData({ teams });
      this.applyFilter(this.data.keyword, teams);
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  handleSearch(event: WechatMiniprogram.Input) {
    const keyword = event.detail.value.trim();
    this.setData({ keyword });
    this.applyFilter(keyword, this.data.teams);
  },

  applyFilter(keyword: string, teams: TeamSummary[]) {
    const normalized = keyword.toLowerCase();
    const filteredTeams = normalized
      ? teams.filter((team) =>
          [team.name, team.shortName ?? "", team.coachName ?? ""]
            .some((value) => value.toLowerCase().includes(normalized)))
      : teams;
    this.setData({ filteredTeams });
  },

  openTeam(event: WechatMiniprogram.CustomEvent<{ teamId: number }>) {
    wx.navigateTo({ url: `/pages/team-detail/index?id=${event.detail.teamId}` });
  },

  openTeamFromRow(event: WechatMiniprogram.BaseEvent<{ id: number }>) {
    wx.navigateTo({ url: `/pages/team-detail/index?id=${event.currentTarget.dataset.id}` });
  },

  retry() {
    void this.loadTeams();
  }
});
