import { refreshSession } from "../../services/auth";
import { addGroupTeam, createCompetition, createGroup, createSeason, createStage, fetchAdminGroups, fetchAdminSeasons, fetchAllCompetitions, fetchDisciplineRule, updateDisciplineRule } from "../../services/admin-hierarchy";
import { fetchAdminTeams } from "../../services/manage";
import type { Competition, CompetitionGroup, DisciplineRule, Season, Stage, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: {
    verified: false, loading: true, error: "",
    competitions: [] as Competition[], seasons: [] as Season[], stages: [] as Stage[], groups: [] as CompetitionGroup[], teams: [] as TeamSummary[],
    competitionIndex: 0, seasonIndex: 0, stageIndex: 0, groupIndex: 0, groupTeamIndex: 0,
    competitionName: "", competitionType: "CUP", seasonName: "", stageName: "", stageType: "GROUP", groupName: "", groupTeamName: "",
    disciplineRule: null as DisciplineRule | null, disciplineThreshold: "3", disciplineRedMatches: "1", cardsCrossStages: true
  },
  onLoad() { void this.load(); },
  onPullDownRefresh() { void this.load(); },
  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER")) { this.setData({ verified: false, error: "仅超级管理员或授权管理员可配置赛事" }); return; }
      const [competitions, teams] = await Promise.all([fetchAllCompetitions(), fetchAdminTeams()]);
      this.setData({ verified: true, competitions, teams, competitionIndex: 0, seasonIndex: 0, stageIndex: 0, groupIndex: 0, groupTeamIndex: 0, groupTeamName: teams[0]?.name ?? "" });
      await this.loadSeasons();
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },
  async loadSeasons() {
    await this.loadDisciplineRule();
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
  async loadDisciplineRule() {
    const competition = this.data.competitions[this.data.competitionIndex];
    if (!competition) { this.setData({ disciplineRule: null }); return; }
    const rule = await fetchDisciplineRule(competition.id);
    this.setData({
      disciplineRule: rule,
      disciplineThreshold: String(rule.yellowSuspensionThreshold),
      disciplineRedMatches: String(rule.redSuspensionMatches),
      cardsCrossStages: rule.cardsCrossStages
    });
  },
  handleDisciplineInput(e: WechatMiniprogram.Input) {
    this.setData({ [String(e.currentTarget.dataset.field)]: e.detail.value });
  },
  handleCardsCrossStages(e: WechatMiniprogram.SwitchChange) {
    this.setData({ cardsCrossStages: e.detail.value });
  },
  async saveDisciplineRule() {
    const competition = this.data.competitions[this.data.competitionIndex];
    const threshold = Number(this.data.disciplineThreshold);
    const redMatches = Number(this.data.disciplineRedMatches);
    if (!competition || !Number.isInteger(threshold) || threshold < 1 || threshold > 9
        || !Number.isInteger(redMatches) || redMatches < 1 || redMatches > 20) {
      wx.showToast({ title: "停赛规则参数不合法", icon: "none" });
      return;
    }
    try {
      const rule = await updateDisciplineRule(competition.id, threshold, redMatches, this.data.cardsCrossStages);
      this.setData({ disciplineRule: rule, disciplineThreshold: String(rule.yellowSuspensionThreshold), disciplineRedMatches: String(rule.redSuspensionMatches), cardsCrossStages: rule.cardsCrossStages });
      wx.showToast({ title: "纪律规则已保存", icon: "success" });
    } catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async handleSeasonChange(e: WechatMiniprogram.PickerChange) { this.setData({ seasonIndex: Number(e.detail.value) }); await this.loadStages(); },
  async handleStageChange(e: WechatMiniprogram.PickerChange) { this.setData({ stageIndex: Number(e.detail.value) }); await this.loadGroups(); },
  handleGroupChange(e: WechatMiniprogram.PickerChange) { this.setData({ groupIndex: Number(e.detail.value) }); },
  handleGroupTeamChange(e: WechatMiniprogram.PickerChange) {
    const groupTeamIndex = Number(e.detail.value);
    this.setData({ groupTeamIndex, groupTeamName: this.data.teams[groupTeamIndex]?.name ?? "" });
  },
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
    const team = this.data.teams[this.data.groupTeamIndex];
    if (!group || !team) return;
    try { await addGroupTeam(group.id, team.id); wx.showToast({ title: "球队已加入小组", icon: "success" }); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  goBack() { wx.navigateBack(); }
});
