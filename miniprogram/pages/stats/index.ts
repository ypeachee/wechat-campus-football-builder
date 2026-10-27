import { fetchAppearancesFiltered, fetchAssistsFiltered, fetchScorersFiltered, fetchStandingsFiltered } from "../../services/stats";
import { fetchCompetitions, fetchGroups, fetchSeasonsByCompetition, fetchStages } from "../../services/competition-hierarchy";
import type { AppearanceRow, AssistRow, Competition, CompetitionGroup, RankingDisplayItem, ScorerRow, Season, Stage, StandingRow } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: {
    loading: true, error: "", competitions: [] as Competition[], seasons: [] as Season[], stages: [] as Stage[], groups: [] as CompetitionGroup[],
    selectedCompetition: 0, selectedSeason: 0, selectedStage: 0, selectedGroup: 0,
    selectedCompetitionName: "", selectedSeasonName: "", selectedStageName: "", selectedGroupName: "", selectedStageType: "",
    activeTab: "standings", standings: [] as StandingRow[], scorerItems: [] as RankingDisplayItem[], assistItems: [] as RankingDisplayItem[], appearanceItems: [] as RankingDisplayItem[], canOpenBracket: false
  },
  onLoad() { void this.bootstrap(); },
  onPullDownRefresh() { void this.bootstrap(); },
  async bootstrap() {
    this.setData({ loading: true, error: "" });
    try {
      const competitions = await fetchCompetitions();
      const competitionId = this.data.selectedCompetition || competitions[0]?.id || 0;
      const seasons = competitionId ? await fetchSeasonsByCompetition(competitionId) : [];
      const seasonId = this.data.selectedSeason || seasons[0]?.id || 0;
      const stages = seasonId ? await fetchStages(seasonId) : [];
      const stageId = this.data.selectedStage || stages[0]?.id || 0;
      const stage = stages.find((item) => item.id === stageId);
      const groups = stage?.type === "GROUP" ? await fetchGroups(stageId) : [];
      const groupId = stage?.type === "GROUP" ? (this.data.selectedGroup || groups[0]?.id || 0) : 0;
      this.setData({ competitions, seasons, stages, groups, selectedCompetition: competitionId, selectedSeason: seasonId, selectedStage: stageId, selectedGroup: groupId,
        selectedCompetitionName: competitions.find((item) => item.id === competitionId)?.name ?? "",
        selectedSeasonName: seasons.find((item) => item.id === seasonId)?.name ?? "", selectedStageName: stage?.name ?? "", selectedGroupName: groups.find((item) => item.id === groupId)?.name ?? "", selectedStageType: stage?.type ?? "", canOpenBracket: stage?.type === "KNOCKOUT" });
      await this.loadStats();
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },
  filter() { return { competitionId: this.data.selectedCompetition, seasonId: this.data.selectedSeason, stageId: this.data.selectedStage, groupId: this.data.selectedGroup }; },
  async loadStats() {
    const filter = this.filter();
    const knockout = this.data.selectedStageType === "KNOCKOUT";
    if (knockout) {
      this.setData({ standings: [] });
    }
    const [standings, scorers, assists, appearances] = await Promise.all([
      knockout ? Promise.resolve([] as StandingRow[]) : fetchStandingsFiltered(filter),
      fetchScorersFiltered(filter), fetchAssistsFiltered(filter), fetchAppearancesFiltered(filter)
    ]);
    this.setData({ standings, scorerItems: scorers.map(scorerItem), assistItems: assists.map(assistItem), appearanceItems: appearances.map(appearanceItem) });
  },
  async handleCompetitionChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value); const competitionId = this.data.competitions[index]?.id ?? 0;
    const seasons = competitionId ? await fetchSeasonsByCompetition(competitionId) : [];
    this.setData({ selectedCompetition: competitionId, selectedCompetitionName: this.data.competitions[index]?.name ?? "", seasons, selectedSeason: seasons[0]?.id ?? 0, selectedSeasonName: seasons[0]?.name ?? "", stages: [], groups: [], selectedStage: 0, selectedGroup: 0, selectedStageName: "", selectedGroupName: "" });
    await this.loadStages();
  },
  async handleSeasonChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value); this.setData({ selectedSeason: this.data.seasons[index]?.id ?? 0, selectedSeasonName: this.data.seasons[index]?.name ?? "", stages: [], groups: [], selectedStage: 0, selectedGroup: 0, selectedStageName: "", selectedGroupName: "" });
    await this.loadStages();
  },
  async loadStages() {
    const stages = this.data.selectedSeason ? await fetchStages(this.data.selectedSeason) : [];
    this.setData({ stages, selectedStage: stages[0]?.id ?? 0, selectedStageName: stages[0]?.name ?? "", selectedStageType: stages[0]?.type ?? "" });
    await this.loadGroups();
  },
  async handleStageChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value); this.setData({ selectedStage: this.data.stages[index]?.id ?? 0, selectedStageName: this.data.stages[index]?.name ?? "", selectedStageType: this.data.stages[index]?.type ?? "", selectedGroup: 0, groups: [], selectedGroupName: "" });
    await this.loadGroups();
  },
  async loadGroups() {
    const stage = this.data.stages.find((item) => item.id === this.data.selectedStage);
    const groups = stage?.type === "GROUP" ? await fetchGroups(stage.id) : [];
    this.setData({ groups, selectedGroup: groups[0]?.id ?? 0, selectedGroupName: groups[0]?.name ?? "", canOpenBracket: stage?.type === "KNOCKOUT" });
    await this.loadStats();
  },
  async handleGroupChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value); this.setData({ selectedGroup: this.data.groups[index]?.id ?? 0, selectedGroupName: this.data.groups[index]?.name ?? "" }); await this.loadStats();
  },
  handleTabChange(event: WechatMiniprogram.CustomEvent<{ value?: string | number }>) { const value = event.detail?.value; if (value !== undefined) this.setData({ activeTab: String(value) }); },
  openBracket() { if (this.data.selectedStage) wx.navigateTo({ url: `/pages/bracket/index?stageId=${this.data.selectedStage}` }); },
  retry() { void this.bootstrap(); }
});

function scorerItem(row: ScorerRow, index: number): RankingDisplayItem { return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.goals }; }
function assistItem(row: AssistRow, index: number): RankingDisplayItem { return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.assists }; }
function appearanceItem(row: AppearanceRow, index: number): RankingDisplayItem { return { id: row.playerId, rank: index + 1, name: row.playerName, teamName: row.teamName, value: row.appearances }; }
