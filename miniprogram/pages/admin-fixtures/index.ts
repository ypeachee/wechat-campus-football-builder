import { refreshSession } from "../../services/auth";
import {
  changeFixtureStatus,
  createFixture,
  fetchAdminFixtures,
  fetchAdminSeasons,
  fetchSeasonTeams,
  finishFixture,
  setFixtureLocked,
  updateFixture,
  updateOfficialScore
} from "../../services/competition";
import { fetchAdminTeams } from "../../services/manage";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type { FixtureCard, FixtureStatus, FixtureSummary, Season, TeamSummary } from "../../types/domain";
import { formatMatchTime } from "../../utils/date";
import { errorMessage } from "../../utils/error";
import { fixtureStatusLabel } from "../../utils/status";

type AdminFixtureView = FixtureCard & { statusLabel: string };

type ScoreMode = "score" | "finish";

const submitLock = new SubmitLock();

Page({
  data: {
    verified: false,
    loading: true,
    submitting: false,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    seasonTeams: [] as TeamSummary[],
    allTeams: [] as TeamSummary[],
    fixtureViews: [] as AdminFixtureView[],
    editingFixtureId: 0,
    createForm: {
      roundNo: "",
      homeTeamIndex: -1,
      awayTeamIndex: -1,
      kickoffDate: "",
      kickoffTime: "19:00",
      venue: ""
    },
    homeTeamName: "",
    awayTeamName: "",
    scoreDialogVisible: false,
    scoreMode: "score" as ScoreMode,
    scoreFixtureId: 0,
    scoreHome: "",
    scoreAway: "",
    scoreHomePenalty: "",
    scoreAwayPenalty: ""
  },

  onLoad() {
    void this.verifyAndLoad();
  },

  onPullDownRefresh() {
    void this.verifyAndLoad();
  },

  async verifyAndLoad() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || session.role !== "ADMIN") {
        this.setData({ verified: false, error: "仅联赛管理员可以管理赛程" });
        return;
      }
      const [seasons, allTeams] = await Promise.all([fetchAdminSeasons(), fetchAdminTeams()]);
      const selectedSeasonId = this.data.selectedSeasonId || seasons[0]?.id || 0;
      const today = new Date().toISOString().slice(0, 10);
      this.setData({
        verified: true,
        seasons,
        allTeams,
        selectedSeasonId,
        "createForm.kickoffDate": this.data.createForm.kickoffDate || today
      });
      if (selectedSeasonId) {
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
    const [seasonTeams, fixtures] = await Promise.all([
      fetchSeasonTeams(seasonId),
      fetchAdminFixtures(seasonId)
    ]);
    const teamMap = new Map(this.data.allTeams.map((team) => [team.id, team.name]));
    const fixtureViews = fixtures.map((fixture) => toView(fixture, teamMap));
    this.setData({ seasonTeams, fixtureViews });
  },

  selectSeason(event: WechatMiniprogram.BaseEvent) {
    const seasonId = Number(event.currentTarget.dataset.id);
    if (!seasonId) {
      return;
    }
    this.setData({
      selectedSeasonId: seasonId,
      "createForm.homeTeamIndex": -1,
      "createForm.awayTeamIndex": -1,
      homeTeamName: "",
      awayTeamName: ""
    });
    void this.loadSeason(seasonId).catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  handleFormInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as "roundNo" | "venue";
    this.setData({ [`createForm.${field}`]: event.detail.value });
  },

  handleDateChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ "createForm.kickoffDate": String(event.detail.value) });
  },

  handleTimeChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ "createForm.kickoffTime": String(event.detail.value) });
  },

  handleHomeTeamChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({
      "createForm.homeTeamIndex": index,
      homeTeamName: this.data.seasonTeams[index]?.name ?? ""
    });
  },

  handleAwayTeamChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({
      "createForm.awayTeamIndex": index,
      awayTeamName: this.data.seasonTeams[index]?.name ?? ""
    });
  },

  openEditFixture(fixture: AdminFixtureView) {
    const homeIndex = this.data.seasonTeams.findIndex((team) => team.id === fixture.homeTeamId);
    const awayIndex = this.data.seasonTeams.findIndex((team) => team.id === fixture.awayTeamId);
    const [date, time = ""] = (fixture.kickoffAt ?? "").split("T");
    this.setData({
      editingFixtureId: fixture.id,
      "createForm.roundNo": fixture.roundNo === null ? "" : String(fixture.roundNo),
      "createForm.homeTeamIndex": homeIndex,
      "createForm.awayTeamIndex": awayIndex,
      "createForm.kickoffDate": date,
      "createForm.kickoffTime": time.slice(0, 5) || "19:00",
      "createForm.venue": fixture.venue ?? "",
      homeTeamName: fixture.homeTeamName,
      awayTeamName: fixture.awayTeamName
    });
    wx.pageScrollTo({ scrollTop: 0, duration: 250 });
  },

  cancelEdit() {
    this.setData({
      editingFixtureId: 0,
      "createForm.roundNo": "",
      "createForm.homeTeamIndex": -1,
      "createForm.awayTeamIndex": -1,
      "createForm.venue": "",
      homeTeamName: "",
      awayTeamName: ""
    });
  },

  async createFixture() {
    const form = this.data.createForm;
    const home = this.data.seasonTeams[form.homeTeamIndex];
    const away = this.data.seasonTeams[form.awayTeamIndex];
    if (!this.data.selectedSeasonId || !home || !away) {
      wx.showToast({ title: "请选择赛季和主客队", icon: "none" });
      return;
    }
    if (home.id === away.id) {
      wx.showToast({ title: "主队和客队不能相同", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    const wasEditing = Boolean(this.data.editingFixtureId);
    try {
      const saved = await withSubmitLock(submitLock, async () => {
        const payload = {
          seasonId: this.data.selectedSeasonId,
          roundNo: form.roundNo ? Number(form.roundNo) : null,
          homeTeamId: home.id,
          awayTeamId: away.id,
          kickoffAt: form.kickoffDate ? `${form.kickoffDate}T${form.kickoffTime}:00` : null,
          venue: form.venue.trim() || null
        };
        if (this.data.editingFixtureId) {
          await updateFixture(this.data.editingFixtureId, payload);
        } else {
          await createFixture(payload);
        }
        return true;
      });
      if (!saved) {
        return;
      }
      this.cancelEdit();
      await this.loadSeason(this.data.selectedSeasonId);
      wx.showToast({ title: wasEditing ? "赛程已更新" : "比赛已创建", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  handleFixtureAction(event: WechatMiniprogram.BaseEvent) {
    const action = String(event.currentTarget.dataset.action ?? "");
    const fixtureId = Number(event.currentTarget.dataset.id);
    const fixture = this.data.fixtureViews.find((item) => item.id === fixtureId);
    if (!fixture) {
      return;
    }
    if (action === "edit") {
      this.openEditFixture(fixture);
      return;
    }
    if (action === "score") {
      this.openScoreDialog(fixture, "score");
      return;
    }
    if (action === "finish") {
      this.openScoreDialog(fixture, "finish");
      return;
    }
    if (action === "lock") {
      void this.changeLock(fixture);
      return;
    }
    if (action === "scheduled" || action === "live" || action === "cancelled") {
      const statusMap: Record<string, FixtureStatus> = {
        scheduled: "SCHEDULED",
        live: "LIVE",
        cancelled: "CANCELLED"
      };
      void this.changeStatus(fixtureId, statusMap[action]);
    }
  },

  async changeStatus(fixtureId: number, status: FixtureStatus) {
    try {
      await changeFixtureStatus(fixtureId, status);
      await this.loadSeason(this.data.selectedSeasonId);
      wx.showToast({ title: "状态已更新", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    }
  },

  async changeLock(fixture: AdminFixtureView) {
    try {
      await setFixtureLocked(fixture.id, !fixture.dataLocked);
      await this.loadSeason(this.data.selectedSeasonId);
      wx.showToast({ title: fixture.dataLocked ? "已解锁" : "已锁定", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    }
  },

  openScoreDialog(fixture: AdminFixtureView, mode: ScoreMode) {
    this.setData({
      scoreDialogVisible: true,
      scoreMode: mode,
      scoreFixtureId: fixture.id,
      scoreHome: fixture.homeScore === null ? "" : String(fixture.homeScore),
      scoreAway: fixture.awayScore === null ? "" : String(fixture.awayScore),
      scoreHomePenalty: fixture.homePenaltyScore === null ? "" : String(fixture.homePenaltyScore),
      scoreAwayPenalty: fixture.awayPenaltyScore === null ? "" : String(fixture.awayPenaltyScore)
    });
  },

  closeScoreDialog() {
    this.setData({ scoreDialogVisible: false });
  },

  handleScorePopupChange(event: WechatMiniprogram.CustomEvent<any>) {
    const detail = event.detail as { visible?: boolean } | boolean;
    this.setData({ scoreDialogVisible: typeof detail === "boolean" ? detail : Boolean(detail?.visible) });
  },

  handleScoreInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as "scoreHome" | "scoreAway" | "scoreHomePenalty" | "scoreAwayPenalty";
    this.setData({ [field]: event.detail.value });
  },

  async saveScore() {
    const homeScore = Number(this.data.scoreHome);
    const awayScore = Number(this.data.scoreAway);
    if (!Number.isInteger(homeScore) || !Number.isInteger(awayScore) || homeScore < 0 || awayScore < 0) {
      wx.showToast({ title: "比分必须是非负整数", icon: "none" });
      return;
    }
    const homePenalty = this.data.scoreHomePenalty ? Number(this.data.scoreHomePenalty) : null;
    const awayPenalty = this.data.scoreAwayPenalty ? Number(this.data.scoreAwayPenalty) : null;
    if (this.data.scoreMode === "finish" && homeScore === awayScore
        && (homePenalty === null || awayPenalty === null || homePenalty === awayPenalty)) {
      wx.showToast({ title: "平局完赛必须填写不同点球比分", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      if (this.data.scoreMode === "finish") {
        await finishFixture(this.data.scoreFixtureId, homeScore, awayScore, homePenalty, awayPenalty);
      } else {
        await updateOfficialScore(this.data.scoreFixtureId, homeScore, awayScore, homePenalty, awayPenalty);
      }
      this.setData({ scoreDialogVisible: false });
      await this.loadSeason(this.data.selectedSeasonId);
      wx.showToast({ title: this.data.scoreMode === "finish" ? "比赛已完赛" : "比分已更新", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  goBack() {
    wx.navigateBack();
  }
});

function toView(fixture: FixtureSummary, teamMap: Map<number, string>): AdminFixtureView {
  return {
    ...fixture,
    homeTeamName: teamMap.get(fixture.homeTeamId) ?? `球队 #${fixture.homeTeamId}`,
    awayTeamName: teamMap.get(fixture.awayTeamId) ?? `球队 #${fixture.awayTeamId}`,
    timeLabel: formatMatchTime(fixture.kickoffAt),
    statusLabel: fixtureStatusLabel(fixture.status)
  };
}
