import { refreshSession } from "../../services/auth";
import { fetchAdminTeams } from "../../services/manage";
import { fetchTeam } from "../../services/teams";
import { getSession } from "../../state/session";
import type { Session, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { roleLabel } from "../../utils/status";

type ManageData = {
  session: Session | null;
  roleText: string;
  isAdmin: boolean;
  verified: boolean;
  loading: boolean;
  error: string;
  teams: TeamSummary[];
};

Page({
  data: {
    session: null as Session | null,
    roleText: "",
    isAdmin: false,
    verified: false,
    loading: true,
    error: "",
    teams: [] as TeamSummary[]
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
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER")) {
        this.setData({ session: null, verified: false, loading: false });
        return;
      }
      const teams = session.role === "ADMIN"
        ? await fetchAdminTeams()
        : (await Promise.all(session.managedTeamIds.map((teamId) => fetchTeam(teamId).catch(() => null))))
            .filter((team): team is TeamSummary => team !== null);
      this.setData({
        session,
        roleText: roleLabel(session.role),
        isAdmin: session.role === "ADMIN",
        verified: true,
        teams
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

  goAdminCompetition() {
    wx.navigateTo({ url: "/pages/admin-competition/index" });
  },

  goAdminBracket() {
    wx.navigateTo({ url: "/pages/admin-bracket/index" });
  },

  goFixtureData() {
    wx.navigateTo({ url: "/pages/manage-fixture-data/index" });
  },

  goAdminRatings() {
    wx.navigateTo({
      url: "/pages/admin-ratings/index",
      fail: (error) => {
        console.error("open admin ratings failed", error);
        wx.showToast({ title: `无法打开评分审核：${error.errMsg}`, icon: "none" });
      }
    });
  },

  goProfile() {
    wx.switchTab({ url: "/pages/profile/index" });
  }
});
