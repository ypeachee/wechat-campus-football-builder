import { refreshSession } from "../../services/auth";
import {
  addSeasonTeam,
  createSeason,
  fetchAdminSeasons,
  fetchSeasonTeams,
  removeSeasonTeam
} from "../../services/competition";
import { fetchAdminTeams } from "../../services/manage";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type { Season, SeasonStatus, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type SeasonForm = { name: string; startDate: string; endDate: string; status: SeasonStatus };

const submitLock = new SubmitLock();

Page({
  data: {
    verified: false,
    loading: true,
    submitting: false,
    error: "",
    seasons: [] as Season[],
    teams: [] as TeamSummary[],
    selectedSeasonId: 0,
    selectedSeason: null as Season | null,
    seasonTeams: [] as TeamSummary[],
    availableTeams: [] as TeamSummary[],
    seasonForm: { name: "", startDate: "", endDate: "", status: "DRAFT" } as SeasonForm,
    statusOptions: [
      { value: "DRAFT", label: "草稿" },
      { value: "ACTIVE", label: "进行中" },
      { value: "FINISHED", label: "已结束" }
    ] as Array<{ value: SeasonStatus; label: string }>
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
        this.setData({ verified: false, error: "仅联赛管理员可以管理赛季" });
        return;
      }
      const [seasons, teams] = await Promise.all([fetchAdminSeasons(), fetchAdminTeams()]);
      const selectedSeasonId = this.data.selectedSeasonId || seasons[0]?.id || 0;
      this.setData({ verified: true, seasons, teams, selectedSeasonId });
      if (selectedSeasonId) {
        await this.loadSeasonTeams(selectedSeasonId);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  selectSeason(event: WechatMiniprogram.BaseEvent) {
    const seasonId = Number(event.currentTarget.dataset.id);
    if (!seasonId) {
      return;
    }
    this.setData({ selectedSeasonId: seasonId });
    void this.loadSeasonTeams(seasonId).catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  async loadSeasonTeams(seasonId: number) {
    const seasonTeams = await fetchSeasonTeams(seasonId);
    const teamIds = new Set(seasonTeams.map((team) => team.id));
    this.setData({
      selectedSeason: this.data.seasons.find((season) => season.id === seasonId) ?? null,
      seasonTeams,
      availableTeams: this.data.teams.filter((team) => !teamIds.has(team.id))
    });
  },

  handleFormInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as "name" | "startDate" | "endDate";
    this.setData({ [`seasonForm.${field}`]: event.detail.value });
  },

  handleStartDate(event: WechatMiniprogram.PickerChange) {
    this.setData({ "seasonForm.startDate": String(event.detail.value) });
  },

  handleEndDate(event: WechatMiniprogram.PickerChange) {
    this.setData({ "seasonForm.endDate": String(event.detail.value) });
  },

  selectStatus(event: WechatMiniprogram.BaseEvent) {
    this.setData({ "seasonForm.status": event.currentTarget.dataset.value as SeasonStatus });
  },

  async createSeason() {
    const form = this.data.seasonForm;
    if (!form.name.trim()) {
      wx.showToast({ title: "请输入赛季名称", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      const seasonId = await withSubmitLock(submitLock, async () => {
        const response = await createSeason({
          name: form.name.trim(),
          startDate: form.startDate || null,
          endDate: form.endDate || null,
          status: form.status
        });
        return response.id;
      });
      if (!seasonId) {
        return;
      }
      this.setData({
        selectedSeasonId: seasonId,
        seasonForm: { name: "", startDate: "", endDate: "", status: "DRAFT" }
      });
      await this.verifyAndLoad();
      wx.showToast({ title: "赛季已创建", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  async addTeam(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.id);
    if (!teamId || !this.data.selectedSeasonId) {
      return;
    }
    try {
      await addSeasonTeam(this.data.selectedSeasonId, teamId);
      await this.loadSeasonTeams(this.data.selectedSeasonId);
      wx.showToast({ title: "球队已加入", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    }
  },

  removeTeam(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.id);
    if (!teamId || !this.data.selectedSeasonId) {
      return;
    }
    wx.showModal({
      title: "移除参赛球队",
      content: "已有比赛的球队不能移除，确认继续？",
      success: async (result) => {
        if (!result.confirm) {
          return;
        }
        try {
          await removeSeasonTeam(this.data.selectedSeasonId, teamId);
          await this.loadSeasonTeams(this.data.selectedSeasonId);
          wx.showToast({ title: "已移除", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  goBack() {
    wx.navigateBack();
  }
});
