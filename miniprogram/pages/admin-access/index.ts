import { refreshSession } from "../../services/auth";
import { fetchAdminTeams, fetchTeamManagers, grantTeamManager, revokeTeamManager, searchUsers as searchUsersApi } from "../../services/manage";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type { TeamManagerAssignment, TeamSummary, UserSearchItem } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { roleLabel } from "../../utils/status";

type AssignmentRole = Extract<TeamManagerAssignment["role"], "TEAM_MANAGER" | "TEAM_LEADER">;
type AssignmentView = TeamManagerAssignment & { roleText: string };

const submitLock = new SubmitLock();

Page({
  data: {
    verified: false,
    loading: true,
    submitting: false,
    error: "",
    teams: [] as TeamSummary[],
    selectedTeamId: 0,
    selectedTeam: null as TeamSummary | null,
    managers: [] as AssignmentView[],
    grantUserId: "",
    userQuery: "",
    userSearching: false,
    userResults: [] as Array<UserSearchItem & { roleText: string }>,
    grantRole: "TEAM_LEADER" as AssignmentRole,
    grantRoleOptions: [
      { value: "TEAM_MANAGER", label: "授权管理员" },
      { value: "TEAM_LEADER", label: "领队" }
    ] as Array<{ value: AssignmentRole; label: string }>
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
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER")) {
        this.setData({ verified: false, error: "仅超级管理员或授权管理员可以管理球队授权" });
        return;
      }
      const teams = await fetchAdminTeams();
      const selectedTeamId = this.data.selectedTeamId || teams[0]?.id || 0;
      this.setData({ verified: true, teams, selectedTeamId });
      if (selectedTeamId) {
        await this.loadManagers(selectedTeamId);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  selectTeam(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.id);
    if (!teamId) {
      return;
    }
    this.setData({ selectedTeamId: teamId });
    void this.loadManagers(teamId).catch((error) =>
      wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  async loadManagers(teamId: number) {
    const managers = (await fetchTeamManagers(teamId)).map((item) => ({
      ...item,
      roleText: roleLabel(item.role)
    }));
    this.setData({
      selectedTeam: this.data.teams.find((team) => team.id === teamId) ?? null,
      managers
    });
  },

  handleUserQueryInput(event: WechatMiniprogram.Input) {
    this.setData({ userQuery: event.detail.value });
  },

  async searchUsers() {
    if (this.data.userSearching) return;
    this.setData({ userSearching: true });
    try {
      const results = await searchUsersApi(this.data.userQuery);
      this.setData({
        userResults: results.map((item) => ({ ...item, roleText: roleLabel(item.role) }))
      });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ userSearching: false });
    }
  },

  selectUser(event: WechatMiniprogram.BaseEvent) {
    const userId = Number(event.currentTarget.dataset.id);
    if (!userId) return;
    this.setData({ grantUserId: String(userId), userResults: [] });
  },

  handleUserIdInput(event: WechatMiniprogram.Input) {
    this.setData({ grantUserId: event.detail.value });
  },

  selectGrantRole(event: WechatMiniprogram.BaseEvent) {
    this.setData({ grantRole: String(event.currentTarget.dataset.value) as AssignmentRole });
  },

  async grantManager() {
    const userId = Number(this.data.grantUserId);
    if (!Number.isInteger(userId) || userId <= 0) {
      wx.showToast({ title: "请输入有效的用户 ID", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      const saved = await withSubmitLock(submitLock, async () => {
        await grantTeamManager(this.data.selectedTeamId, userId, this.data.grantRole);
        return true;
      });
      if (!saved) {
        return;
      }
      this.setData({ grantUserId: "" });
      await this.loadManagers(this.data.selectedTeamId);
      wx.showToast({ title: "授权成功", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  revokeManager(event: WechatMiniprogram.BaseEvent) {
    const userId = Number(event.currentTarget.dataset.id);
    if (!userId) {
      return;
    }
    wx.showModal({
      title: "撤销球队授权",
      content: `确认撤销用户 ${userId} 的球队管理权限？`,
      success: async (result) => {
        if (!result.confirm) {
          return;
        }
        try {
          await revokeTeamManager(this.data.selectedTeamId, userId);
          await this.loadManagers(this.data.selectedTeamId);
          wx.showToast({ title: "授权已撤销", icon: "success" });
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