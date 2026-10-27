import { refreshSession } from "../../services/auth";
import { addGroupTeam, createCompetition, createGroup, createSeason, createStage, fetchAdminGroups, fetchAdminSeasons, fetchAllCompetitions } from "../../services/admin-hierarchy";
import { fetchAdminTeams } from "../../services/manage";
import type { Competition, CompetitionGroup, Season, Stage, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: {
    verified: false, loading: true, error: "",
    competitions: [] as Competition[], seasons: [] as Season[], stages: [] as Stage[], groups: [] as CompetitionGroup[], teams: [] as TeamSummary[],
    competitionIndex: 0, seasonIndex: 0, stageIndex: 0, groupIndex: 0,
    competitionName: "", competitionType: "CUP", seasonName: "", stageName: "", stageType: "GROUP", groupName: "", groupTeamId: ""
  },
  onLoad() { void this.load(); },
  onPullDownRefresh() { void this.load(); },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || session.role !== "ADMIN") { this.setData({ verified: false, error: "仅管理员可配置赛事" }); return; }
      const [competitions, teams] = await Promise.all([fetchAllCompetitions(), fetchAdminTeams()]);
      this.setData({ verified: true, competitions, teams, competitionIndex: 0, seasonIndex: 0, stageIndex: 0, groupIndex: 0 });
      await this.loadSeasons();
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },
  async loadSeasons() {
    const competition = this.data.competitions[this.data.competitionIndex];
    const seasons = competition ? await fetchAdminSeasons(competition.id) : [];
    this.setData({ seasons, seasonIndex: 0, stages: [], groups: [], stageIndex: 0, groupIndex: 0 });
    await this.loadStages();
  },
  async loadStages() {
    const season = this.data.seasons[this.data.seasonIndex];
    const stages = season ? await (await import("../../services/competition-hierarchy")).fetchStages(season.id) : [];
    this.setData({ stages, stageIndex: 0, groups: [], groupIndex: 0 });
    await this.loadGroups();
  },
  async loadGroups() {
    const stage = this.data.stages[this.data.stageIndex];
    const groups = stage?.type === "GROUP" ? await fetchAdminGroups(stage.id) : [];
    this.setData({ groups, groupIndex: 0 });
  },
  async handleCompetitionChange(e: WechatMiniprogram.PickerChange) { this.setData({ competitionIndex: Number(e.detail.value) }); await this.loadSeasons(); },
  async handleSeasonChange(e: WechatMiniprogram.PickerChange) { this.setData({ seasonIndex: Number(e.detail.value) }); await this.loadStages(); },
  async handleStageChange(e: WechatMiniprogram.PickerChange) { this.setData({ stageIndex: Number(e.detail.value) }); await this.loadGroups(); },
  handleGroupChange(e: WechatMiniprogram.PickerChange) { this.setData({ groupIndex: Number(e.detail.value) }); },
  input(e: WechatMiniprogram.Input) { this.setData({ [String(e.currentTarget.dataset.field)]: e.detail.value }); },
  selectCompetitionType(e: WechatMiniprogram.BaseEvent) { this.setData({ competitionType: String(e.currentTarget.dataset.value) }); },
  selectStageType(e: WechatMiniprogram.BaseEvent) { this.setData({ stageType: String(e.currentTarget.dataset.value) }); },
  async submitCompetition() {
    if (!this.data.competitionName.trim()) return;
    try { await createCompetition(this.data.competitionName.trim(), this.data.competitionType as "LEAGUE"|"CUP"); this.setData({ competitionName: "" }); await this.load(); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async submitSeason() {
    const competition = this.data.competitions[this.data.competitionIndex];
    if (!competition || !this.data.seasonName.trim()) return;
    try { await createSeason(competition.id, this.data.seasonName.trim(), "ACTIVE"); this.setData({ seasonName: "" }); await this.loadSeasons(); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async submitStage() {
    const season = this.data.seasons[this.data.seasonIndex];
    if (!season || !this.data.stageName.trim()) return;
    try { await createStage(season.id, this.data.stageName.trim(), this.data.stageType as "LEAGUE"|"GROUP"|"KNOCKOUT"); this.setData({ stageName: "" }); await this.loadStages(); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async submitGroup() {
    const stage = this.data.stages[this.data.stageIndex];
    if (!stage || !this.data.groupName.trim()) return;
    try { await createGroup(stage.id, this.data.groupName.trim()); this.setData({ groupName: "" }); await this.loadGroups(); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async addTeam() {
    const group = this.data.groups[this.data.groupIndex];
    const teamId = Number(this.data.groupTeamId);
    if (!group || !teamId) return;
    try { await addGroupTeam(group.id, teamId); this.setData({ groupTeamId: "" }); wx.showToast({ title: "球队已加入小组", icon: "success" }); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  goBack() { wx.navigateBack(); }
});
