import { refreshSession } from "../../services/auth";
import { fetchAdminTeams } from "../../services/manage";
import { fetchManagementDashboard } from "../../services/dashboard";
import {
  createSensitiveTerm, deleteSensitiveTerm, fetchModerationRatings, fetchSensitiveTerms,
  importSensitiveTerms, moderateRating, updateSensitiveTerm
} from "../../services/ratings";
import { fetchTeam } from "../../services/teams";
import { getSession } from "../../state/session";
import type {
  DashboardView, RatingModerationItem, RatingStatus, SensitiveTerm, SensitiveTermAction, Session, TeamSummary
} from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { roleLabel } from "../../utils/status";

type ManageData = {
  session: Session | null;
  roleText: string;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  isTeamLeader: boolean;
  verified: boolean;
  loading: boolean;
  error: string;
  teams: TeamSummary[];
  ratingPanelVisible: boolean;
  ratingActiveTab: "ratings" | "terms";
  ratingFilters: Array<{ value: string; label: string }>;
  ratingFilter: string;
  ratingItems: RatingModerationItem[];
  ratingTotal: number;
  ratingTerms: SensitiveTerm[];
  ratingTermName: string;
  ratingTermCategory: string;
  ratingTermAction: SensitiveTermAction;
  ratingImportText: string;
  ratingImportAction: SensitiveTermAction;
  ratingPanelBusy: boolean;
  dashboard: DashboardView | null;
};

Page({
  data: {
    session: null as Session | null,
    roleText: "",
    isAdmin: false,
    isSuperAdmin: false,
    isTeamLeader: false,
    verified: false,
    loading: true,
    error: "",
    teams: [] as TeamSummary[],
    ratingPanelVisible: false,
    ratingActiveTab: "ratings",
    ratingFilters: [
      { value: "PENDING", label: "待审核" },
      { value: "REPORTED", label: "被举报" },
      { value: "HIDDEN", label: "已隐藏" }
    ],
    ratingFilter: "PENDING",
    ratingItems: [] as RatingModerationItem[],
    ratingTotal: 0,
    ratingTerms: [] as SensitiveTerm[],
    ratingTermName: "",
    ratingTermCategory: "通用",
    ratingTermAction: "REVIEW" as SensitiveTermAction,
    ratingImportText: "",
    ratingImportAction: "REVIEW" as SensitiveTermAction,
    ratingPanelBusy: false,
    dashboard: null
  } as ManageData,

  onShow() {
    void this.verifyAndLoad();
  },

  onPullDownRefresh() {
    void this.verifyAndLoad();
  },

  async verifyAndLoad() {
    const local = getSession();
    if (!local) {
      this.setData({ loading: false, verified: false, session: null });
      return;
    }
    this.setData({ loading: true, error: "", session: local, roleText: roleLabel(local.role) });
    try {
      const session = await refreshSession();
      const isSuperAdmin = session?.role === "ADMIN";
      const isAuthorizedAdmin = isSuperAdmin || session?.role === "TEAM_MANAGER";
      const isLeader = session?.role === "TEAM_LEADER";
      if (!session || (!isAuthorizedAdmin && !isLeader)) {
        this.setData({ session: null, verified: false, loading: false });
        return;
      }
      const [teams, dashboard] = await Promise.all([
        isAuthorizedAdmin
          ? fetchAdminTeams()
          : Promise.all(session.managedTeamIds.map((teamId) => fetchTeam(teamId).catch(() => null)))
              .then((items) => items.filter((team): team is TeamSummary => team !== null)),
        fetchManagementDashboard().catch(() => null)
      ]);
      this.setData({
        session,
        roleText: roleLabel(session.role),
        isAdmin: isAuthorizedAdmin,
        isSuperAdmin,
        isTeamLeader: isLeader,
        verified: true,
        teams,
        dashboard
      });
    } catch (error) {
      this.setData({ error: errorMessage(error), verified: false });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  openTeam(event: WechatMiniprogram.CustomEvent<{ teamId: number }>) {
    wx.navigateTo({ url: `/pages/manage-team/index?id=${event.detail.teamId}` });
  },

  createTeam() {
    wx.navigateTo({ url: "/pages/manage-team/index?id=0&create=1" });
  },

  goAdminSeason() {
    wx.navigateTo({ url: "/pages/admin-season/index" });
  },

  goAdminFixtures() {
    wx.navigateTo({ url: "/pages/admin-fixtures/index" });
  },

  goAdminAccess() {
    wx.navigateTo({ url: "/pages/admin-access/index" });
  },

  goAdminAudit() {
    wx.navigateTo({ url: "/pages/admin-audit/index" });
  },

  goDisciplineCenter() {
    wx.navigateTo({ url: "/pages/discipline/index" });
  },

  goAnnouncements() {
    wx.navigateTo({ url: "/pages/admin-announcements/index" });
  },

  goImportData() {
    wx.navigateTo({ url: "/pages/import-data/index" });
  },

  openPendingRatings() {
    this.setData({ ratingFilter: "PENDING", ratingPanelVisible: true });
    void this.loadRatingPanel().catch((error) => wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  openReportedRatings() {
    this.setData({ ratingFilter: "REPORTED", ratingPanelVisible: true });
    void this.loadRatingPanel().catch((error) => wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  goTomorrowFixtures() {
    this.goFixtureData();
  },

  goSuspensions() {
    this.goDisciplineCenter();
  },

  goAdminCompetition() {
    wx.navigateTo({ url: "/pages/admin-competition/index" });
  },

  goAdminBracket() {
    wx.navigateTo({ url: "/pages/admin-bracket/index" });
  },

  goFixtureData() {
    wx.navigateTo({ url: "/pages/manage-fixture-data/index" });
  },

  openRatingPanel() {
    this.setData({ ratingPanelVisible: true });
    void this.loadRatingPanel().catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  closeRatingPanel() {
    if (!this.data.ratingPanelBusy) {
      this.setData({ ratingPanelVisible: false });
    }
  },

  async loadRatingPanel() {
    if (this.data.ratingActiveTab === "ratings") {
      const page = await fetchModerationRatings(this.data.ratingFilter, 0, 50);
      this.setData({ ratingItems: page.items, ratingTotal: page.total });
    } else {
      this.setData({ ratingTerms: await fetchSensitiveTerms() });
    }
  },

  switchRatingTab(event: WechatMiniprogram.BaseEvent) {
    this.setData({ ratingActiveTab: String(event.currentTarget.dataset.tab) as "ratings" | "terms" });
    void this.loadRatingPanel().catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  selectRatingFilter(event: WechatMiniprogram.BaseEvent) {
    this.setData({ ratingFilter: String(event.currentTarget.dataset.value) });
    void this.loadRatingPanel().catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  moderateRatingItem(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const action = String(event.currentTarget.dataset.action);
    const status: RatingStatus = action === "approve" ? "VISIBLE" : action === "reject" ? "REJECTED" : "HIDDEN";
    wx.showModal({
      title: action === "approve" ? "通过评价" : action === "reject" ? "拒绝评价" : "隐藏评价",
      editable: true,
      placeholderText: "审核原因，可选",
      success: async (result) => {
        if (!result.confirm) return;
        this.setData({ ratingPanelBusy: true });
        try {
          await moderateRating(id, status, result.content ?? "");
          await this.loadRatingPanel();
          wx.showToast({ title: "审核已处理", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        } finally {
          this.setData({ ratingPanelBusy: false });
        }
      }
    });
  },

  ratingInput(event: WechatMiniprogram.Input) {
    this.setData({ [String(event.currentTarget.dataset.field)]: event.detail.value });
  },

  selectRatingTermAction(event: WechatMiniprogram.BaseEvent) {
    this.setData({ [String(event.currentTarget.dataset.field)]: String(event.currentTarget.dataset.value) });
  },

  async createRatingTerm() {
    if (!this.data.ratingTermName.trim() || !this.data.ratingTermCategory.trim()) {
      wx.showToast({ title: "请填写敏感词和分类", icon: "none" });
      return;
    }
    this.setData({ ratingPanelBusy: true });
    try {
      await createSensitiveTerm(this.data.ratingTermName, this.data.ratingTermCategory, this.data.ratingTermAction);
      this.setData({ ratingTermName: "" });
      await this.loadRatingPanel();
      wx.showToast({ title: "敏感词已添加", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ ratingPanelBusy: false });
    }
  },

  async toggleRatingTerm(event: WechatMiniprogram.BaseEvent) {
    const term = this.data.ratingTerms.find((item) => item.id === Number(event.currentTarget.dataset.id));
    if (!term) return;
    try {
      await updateSensitiveTerm(term.id, term.category, term.action, !term.enabled);
      await this.loadRatingPanel();
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    }
  },

  removeRatingTerm(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    wx.showModal({
      title: "删除敏感词",
      content: "确认删除该词条？",
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await deleteSensitiveTerm(id);
          await this.loadRatingPanel();
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  async importRatingTerms() {
    const terms = this.data.ratingImportText.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
    if (!terms.length) {
      wx.showToast({ title: "请填写批量词条", icon: "none" });
      return;
    }
    this.setData({ ratingPanelBusy: true });
    try {
      const result = await importSensitiveTerms(terms, this.data.ratingImportAction);
      this.setData({ ratingImportText: "" });
      await this.loadRatingPanel();
      wx.showToast({ title: `已导入 ${result.inserted} 条`, icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ ratingPanelBusy: false });
    }
  },

  goProfile() {
    wx.switchTab({ url: "/pages/profile/index" });
  }
});
