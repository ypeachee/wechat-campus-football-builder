import { refreshSession } from "../../services/auth";
import { fetchCompetitions, fetchSeasonsByCompetition } from "../../services/competition-hierarchy";
import { fetchDisciplineCenter } from "../../services/discipline";
import { fetchAdminTeams } from "../../services/manage";
import { fetchTeam } from "../../services/teams";
import type { Competition, DisciplineCardHistory, PlayerSuspension, Season, Session, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { eventTypeLabel, roleLabel } from "../../utils/status";

type TeamOption = TeamSummary | { id: 0; name: "全部球队" };

Page({
  data: {
    loading: true, error: "", session: null as Session | null, roleText: "",
    competitions: [] as Competition[], seasons: [] as Season[], teams: [] as TeamOption[],
    competitionIndex: 0, seasonIndex: 0, teamIndex: 0,
    selectedSeasonId: 0, selectedTeamId: 0,
    suspensions: [] as Array<PlayerSuspension & { statusText: string; sourceText: string }>,
    cards: [] as Array<DisciplineCardHistory & { typeText: string }>
  },

  onShow() { void this.bootstrap(); },
  onPullDownRefresh() { void this.bootstrap(); },

  async bootstrap() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER" && session.role !== "TEAM_LEADER")) {
        this.setData({ session: null, error: "仅管理员、授权管理员和领队可以查看纪律中心" });
        return;
      }
      const competitions = await fetchCompetitions();
      const competition = competitions[this.data.competitionIndex] ?? competitions[0];
      const seasons = competition ? await fetchSeasonsByCompetition(competition.id) : [];
      const season = seasons[this.data.seasonIndex] ?? seasons[0];
      const allTeams = session.role === "TEAM_LEADER"
        ? (await Promise.all(session.managedTeamIds.map((id) => fetchTeam(id).catch(() => null))))
          .filter((team): team is TeamSummary => team !== null)
        : await fetchAdminTeams();
      const teams: TeamOption[] = [{ id: 0, name: "全部球队" }, ...allTeams];
      const team = teams[this.data.teamIndex] ?? teams[0];
      this.setData({
        session, roleText: roleLabel(session.role), competitions, seasons, teams,
        competitionIndex: Math.max(0, competitions.findIndex((item) => item.id === competition?.id)),
        seasonIndex: Math.max(0, seasons.findIndex((item) => item.id === season?.id)),
        teamIndex: Math.max(0, teams.findIndex((item) => item.id === team?.id)),
        selectedSeasonId: season?.id ?? 0, selectedTeamId: team?.id ?? 0
      });
      if (season) await this.loadDiscipline();
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },

  async loadDiscipline() {
    if (!this.data.selectedSeasonId) { this.setData({ suspensions: [], cards: [] }); return; }
    const result = await fetchDisciplineCenter(this.data.selectedSeasonId, this.data.selectedTeamId || undefined);
    this.setData({
      suspensions: result.suspensions.map((item) => ({
        ...item,
        statusText: item.status === "ACTIVE" ? "执行中" : item.status === "SERVED" ? "已执行" : "已取消",
        sourceText: item.sourceType === "MANUAL" ? "手动处罚" : item.sourceType === "RED_CARD" ? "直接红牌" : "黄牌累计"
      })),
      cards: result.cards.map((item) => ({ ...item, typeText: eventTypeLabel(item.eventType) }))
    });
  },

  async handleCompetitionChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const competition = this.data.competitions[index];
    if (!competition) return;
    const seasons = await fetchSeasonsByCompetition(competition.id);
    const season = seasons[0];
    this.setData({ competitionIndex: index, seasons, seasonIndex: 0,
      selectedSeasonId: season?.id ?? 0, teamIndex: 0, selectedTeamId: 0 });
    await this.loadDiscipline();
  },

  async handleSeasonChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const season = this.data.seasons[index];
    this.setData({ seasonIndex: index, selectedSeasonId: season?.id ?? 0 });
    await this.loadDiscipline();
  },

  async handleTeamChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const team = this.data.teams[index];
    this.setData({ teamIndex: index, selectedTeamId: team?.id ?? 0 });
    await this.loadDiscipline();
  },

  openTeam(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.id);
    if (teamId) wx.navigateTo({ url: `/pages/manage-team/index?id=${teamId}` });
  },

  goBack() { wx.navigateBack(); },
  retry() { void this.bootstrap(); }
});