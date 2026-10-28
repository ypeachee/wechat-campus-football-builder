import { loginWithWechat, logout, refreshSession } from "../../services/auth";
import { clearSession, getSession } from "../../state/session";
import type { Session, UserRole } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { roleLabel } from "../../utils/status";

type ProfileData = {
  session: Session | null;
  roleText: string;
  managedTeamsText: string;
  verified: boolean;
  loading: boolean;
  submitting: boolean;
  error: string;
  manageRoles: UserRole[];
};

Page({
  data: {
    session: null as Session | null,
    roleText: "",
    managedTeamsText: "",
    verified: false,
    loading: false,
    submitting: false,
    error: "",
    manageRoles: ["ADMIN", "TEAM_MANAGER", "TEAM_LEADER"] as UserRole[]
  } as ProfileData,

  onShow() {
    const local = getSession();
    this.setData({
      session: local,
      roleText: local ? roleLabel(local.role) : "",
      managedTeamsText: managedTeams(local),
      verified: false,
      error: ""
    });
    if (local) {
      void this.verifySession();
    }
  },

  async verifySession() {
    this.setData({ loading: true });
    try {
      const refreshed = await refreshSession();
      if (!refreshed) {
        this.setData({ session: null, roleText: "", verified: false });
        return;
      }
      this.setData({
        session: refreshed,
        roleText: roleLabel(refreshed.role),
        managedTeamsText: managedTeams(refreshed),
        verified: true
      });
    } catch (error) {
      this.setData({ error: errorMessage(error, "无法刷新登录状态"), verified: false });
    } finally {
      this.setData({ loading: false });
    }
  },

  async handleLogin() {
    if (this.data.submitting) {
      return;
    }
    this.setData({ submitting: true, error: "" });
    try {
      const session = await loginWithWechat();
      this.setData({
        session,
        roleText: roleLabel(session.role),
        managedTeamsText: managedTeams(session),
        verified: true
      });
    } catch (error) {
      this.setData({ error: errorMessage(error, "微信登录失败") });
    } finally {
      this.setData({ submitting: false });
    }
  },

  handleLogout() {
    logout();
    clearSession();
    this.setData({ session: null, roleText: "", managedTeamsText: "", verified: false, error: "" });
  },

  goManage() {
    wx.navigateTo({ url: "/pages/manage/index" });
  },

  goFixtures() {
    wx.switchTab({ url: "/pages/fixtures/index" });
  },

  goTeams() {
    wx.switchTab({ url: "/pages/teams/index" });
  },

  goStats() {
    wx.switchTab({ url: "/pages/stats/index" });
  }
});

function managedTeams(session: Session | null): string {
  if (!session) {
    return "";
  }
  if (session.role === "ADMIN" || session.role === "TEAM_MANAGER") {
    return "全部球队";
  }
  return session.managedTeamIds.length ? session.managedTeamIds.join(", ") : "暂无";
}
