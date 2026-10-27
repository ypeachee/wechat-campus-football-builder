import { fetchPublicFixtures, fetchPublicFixturesByStage } from "../../services/competition";
import { fetchCompetitions, fetchGroups, fetchSeasonsByCompetition, fetchStages, withAllGroups } from "../../services/competition-hierarchy";
import { fetchTeams } from "../../services/teams";
import type { Competition, CompetitionGroup, FixtureCard, FixtureStatus, FixtureSummary, Season, Stage, TeamSummary } from "../../types/domain";
import { formatMatchTime } from "../../utils/date";
import { resolveSelectedId } from "../../state/selection";
import { errorMessage } from "../../utils/error";

type FixturesData = {
  loading: boolean; error: string; competitions: Competition[]; seasons: Season[]; stages: Stage[]; groups: CompetitionGroup[];
  selectedCompetition: number; selectedSeason: number; selectedStage: number; selectedGroup: number;
  selectedCompetitionName: string; selectedSeasonName: string; selectedStageName: string; selectedGroupName: string;
  selectedStatus: "ALL" | FixtureStatus; statusFilters: Array<{ value: "ALL" | FixtureStatus; label: string }>;
  fixtureViews: FixtureCard[]; allFixtures: FixtureCard[]; canOpenBracket: boolean;
};

Page({
  data: {
    loading: true, error: "",
    competitions: [] as Competition[], seasons: [] as Season[], stages: [] as Stage[], groups: [] as CompetitionGroup[],
    selectedCompetition: 0, selectedSeason: 0, selectedStage: 0, selectedGroup: 0,
    selectedCompetitionName: "", selectedSeasonName: "", selectedStageName: "", selectedGroupName: "", selectedStatus: "ALL",
    statusFilters: [{value:"ALL",label:"全部"},{value:"SCHEDULED",label:"未开始"},{value:"LIVE",label:"进行中"},{value:"FINISHED",label:"已完赛"},{value:"CANCELLED",label:"已取消"}] as Array<{value:"ALL"|FixtureStatus;label:string}>,
    fixtureViews: [] as FixtureCard[], allFixtures: [] as FixtureCard[], canOpenBracket: false
  } as FixturesData,
  onShow() { void this.bootstrap(); },
  onPullDownRefresh() { void this.bootstrap(); },
  async bootstrap() {
    this.setData({ loading: true, error: "" });
    try {
      const competitions = await fetchCompetitions();
      const competitionId = resolveSelectedId(competitions, this.data.selectedCompetition);
      const seasons = competitionId ? await fetchSeasonsByCompetition(competitionId) : [];
      const seasonId = resolveSelectedId(seasons, this.data.selectedSeason);
      const stages = seasonId ? await fetchStages(seasonId) : [];
      const stageId = resolveSelectedId(stages, this.data.selectedStage);
      const stage = stages.find((item) => item.id === stageId);
      const groups = stage?.type === "GROUP" ? withAllGroups(stageId, await fetchGroups(stageId)) : [];
      const groupId = stage?.type === "GROUP" ? resolveSelectedId(groups, this.data.selectedGroup) : 0;
      this.setData({ competitions, seasons, stages, groups, selectedCompetition: competitionId, selectedSeason: seasonId, selectedStage: stageId, selectedGroup: groupId,
        selectedCompetitionName: competitions.find((item) => item.id === competitionId)?.name ?? "",
        selectedSeasonName: seasons.find((item) => item.id === seasonId)?.name ?? "",
        selectedStageName: stage?.name ?? "", selectedGroupName: groups.find((item) => item.id === groupId)?.name ?? "",
        canOpenBracket: stage?.type === "KNOCKOUT" });
      await this.loadFixtures();
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },
  async loadFixtures() {
    const stage = this.data.stages.find((item) => item.id === this.data.selectedStage);
    const [fixtures, teams] = await Promise.all([
      stage ? fetchPublicFixturesByStage(stage.id, stage.type === "GROUP" ? this.data.selectedGroup : null) : fetchPublicFixtures(this.data.selectedSeason),
      fetchTeams()
    ]);
    const teamMap = new Map(teams.map((team: TeamSummary) => [team.id, team.name]));
    const allFixtures = fixtures.map((fixture: FixtureSummary) => this.toCard(fixture, teamMap));
    this.setData({ allFixtures });
    this.applyStatus(this.data.selectedStatus, allFixtures);
  },
  toCard(fixture: FixtureSummary, teamMap: Map<number, string>): FixtureCard {
    return { ...fixture, homeTeamName: teamMap.get(fixture.homeTeamId) ?? `球队 #${fixture.homeTeamId}`, awayTeamName: teamMap.get(fixture.awayTeamId) ?? `球队 #${fixture.awayTeamId}`, timeLabel: formatMatchTime(fixture.kickoffAt) };
  },
  async handleCompetitionChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const competitionId = this.data.competitions[index]?.id ?? 0;
    const seasons = competitionId ? await fetchSeasonsByCompetition(competitionId) : [];
    this.setData({ selectedCompetition: competitionId, selectedCompetitionName: this.data.competitions[index]?.name ?? "", seasons, selectedSeason: seasons[0]?.id ?? 0, selectedSeasonName: seasons[0]?.name ?? "", stages: [], groups: [], selectedStage: 0, selectedGroup: 0, selectedStageName: "", selectedGroupName: "" });
    await this.loadStages();
  },
  async handleSeasonChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({ selectedSeason: this.data.seasons[index]?.id ?? 0, selectedSeasonName: this.data.seasons[index]?.name ?? "", stages: [], groups: [], selectedStage: 0, selectedGroup: 0, selectedStageName: "", selectedGroupName: "" });
    await this.loadStages();
  },
  async loadStages() {
    const stages = this.data.selectedSeason ? await fetchStages(this.data.selectedSeason) : [];
    this.setData({ stages, selectedStage: stages[0]?.id ?? 0, selectedStageName: stages[0]?.name ?? "" });
    await this.loadGroups();
  },
  async handleStageChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({ selectedStage: this.data.stages[index]?.id ?? 0, selectedStageName: this.data.stages[index]?.name ?? "", selectedGroup: 0, selectedGroupName: "", groups: [] });
    await this.loadGroups();
  },
  async loadGroups() {
    const stage = this.data.stages.find((item) => item.id === this.data.selectedStage);
    const groups = stage?.type === "GROUP" ? withAllGroups(stage.id, await fetchGroups(stage.id)) : [];
    this.setData({ groups, selectedGroup: 0, selectedGroupName: groups[0]?.name ?? "", canOpenBracket: stage?.type === "KNOCKOUT" });
    await this.loadFixtures();
  },
  async handleGroupChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({ selectedGroup: this.data.groups[index]?.id ?? 0, selectedGroupName: this.data.groups[index]?.name ?? "" });
    await this.loadFixtures();
  },
  handleStatusFilter(event: WechatMiniprogram.BaseEvent) {
    const selectedStatus = (event.currentTarget.dataset.value ?? "ALL") as "ALL" | FixtureStatus;
    this.setData({ selectedStatus });
    this.applyStatus(selectedStatus, this.data.allFixtures);
  },
  applyStatus(selectedStatus: "ALL" | FixtureStatus, fixtures: FixtureCard[]) {
    this.setData({ fixtureViews: selectedStatus === "ALL" ? fixtures : fixtures.filter((fixture) => fixture.status === selectedStatus) });
  },
  openFixture(event: WechatMiniprogram.CustomEvent<{ fixtureId: number }>) { wx.navigateTo({ url: `/pages/fixture-detail/index?id=${event.detail.fixtureId}` }); },
  openBracket() { if (this.data.selectedStage) wx.navigateTo({ url: `/pages/bracket/index?stageId=${this.data.selectedStage}` }); },
  retry() { void this.bootstrap(); }
});
