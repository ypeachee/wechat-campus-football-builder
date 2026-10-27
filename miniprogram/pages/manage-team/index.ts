import { refreshSession } from "../../services/auth";
import {
  createManagedPlayer,
  createTeam,
  fetchAdminTeams,
  fetchManagedTeamPlayers,
  updateManagedPlayer,
  updateManagedTeam
} from "../../services/manage";
import { fetchTeam } from "../../services/teams";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type { PlayerStatus, PlayerSummary, Session, TeamStatus, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { hasFieldErrors, validatePlayerForm, validateTeamForm, type FieldErrors } from "../../utils/validation";

type TeamFormState = {
  name: string;
  shortName: string;
  coachName: string;
  homeVenue: string;
  status: TeamStatus;
};

type PlayerFormState = {
  id: number | null;
  name: string;
  jerseyNo: number | null;
  position: string;
  status: PlayerStatus;
};

const submitLock = new SubmitLock();

Page({
  data: {
    teamId: 0,
    createMode: false,
    isAdmin: false,
    verified: false,
    loading: true,
    submitting: false,
    error: "",
    team: null as TeamSummary | null,
    players: [] as PlayerSummary[],
    teamForm: { name: "", shortName: "", coachName: "", homeVenue: "", status: "ACTIVE" } as TeamFormState,
    teamErrors: {} as FieldErrors,
    playerDialogVisible: false,
    playerForm: { id: null, name: "", jerseyNo: null, position: "", status: "ACTIVE" } as PlayerFormState,
    playerErrors: {} as FieldErrors
  },

  onLoad(options: Record<string, string | undefined>) {
    const teamId = Number(options.id ?? 0);
    this.setData({ teamId, createMode: teamId === 0 || options.create === "1" });
    void this.verifyAndLoad();
  },

  onPullDownRefresh() {
    void this.verifyAndLoad();
  },

  async verifyAndLoad() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session) {
        this.setData({ error: "登录状态已失效，请重新登录", verified: false });
        return;
      }
      const isAdmin = session.role === "ADMIN";
      if (this.data.createMode && !isAdmin) {
        this.setData({ error: "只有联赛管理员可以创建球队", verified: false });
        return;
      }
      if (!this.data.createMode && !canManageTeamWithSession(session, this.data.teamId)) {
        this.setData({ error: "没有权限管理该球队", verified: false });
        return;
      }
      if (this.data.createMode) {
        this.setData({
          isAdmin,
          verified: true,
          team: null,
          players: [],
          teamForm: { name: "", shortName: "", coachName: "", homeVenue: "", status: "ACTIVE" }
        });
        return;
      }

      const team = isAdmin
        ? (await fetchAdminTeams()).find((item) => item.id === this.data.teamId) ?? null
        : await fetchTeam(this.data.teamId);
      if (!team) {
        this.setData({ error: "球队不存在", verified: false });
        return;
      }
      const players = await fetchManagedTeamPlayers(this.data.teamId);
      this.setData({
        isAdmin,
        verified: true,
        team,
        players,
        teamForm: {
          name: team.name,
          shortName: team.shortName ?? "",
          coachName: team.coachName ?? "",
          homeVenue: team.homeVenue ?? "",
          status: team.status
        }
      });
      wx.setNavigationBarTitle({ title: team.name });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  handleTeamInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as keyof TeamFormState;
    this.setData({
      [`teamForm.${field}`]: event.detail.value,
      [`teamErrors.${field}`]: ""
    });
  },

  handleTeamStatusChange(event: WechatMiniprogram.SwitchChange) {
    this.setData({
      "teamForm.status": event.detail.value ? "ACTIVE" : "INACTIVE",
      "teamErrors.status": ""
    });
  },

  async saveTeam() {
    if (!this.data.verified || this.data.submitting) {
      return;
    }
    const form = this.data.teamForm;
    const errors = validateTeamForm(form, this.data.isAdmin);
    this.setData({ teamErrors: errors });
    if (hasFieldErrors(errors)) {
      return;
    }
    this.setData({ submitting: true });
    try {
      const teamId = await withSubmitLock(submitLock, async () => {
        const payload = {
          name: form.name.trim(),
          shortName: form.shortName.trim() || null,
          coachName: form.coachName.trim() || null,
          homeVenue: form.homeVenue.trim() || null
        };
        if (this.data.createMode) {
          const response = await createTeam(payload);
          return response.id;
        }
        await updateManagedTeam(this.data.teamId, this.data.isAdmin
          ? { ...payload, status: form.status }
          : payload);
        return this.data.teamId;
      });
      if (!teamId) {
        return;
      }
      wx.showToast({ title: "保存成功", icon: "success" });
      if (this.data.createMode) {
        wx.redirectTo({ url: `/pages/manage-team/index?id=${teamId}` });
      } else {
        await this.verifyAndLoad();
      }
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  openCreatePlayer() {
    this.setData({
      playerDialogVisible: true,
      playerErrors: {},
      playerForm: { id: null, name: "", jerseyNo: null, position: "", status: "ACTIVE" }
    });
  },

  openEditPlayer(event: WechatMiniprogram.BaseEvent) {
    const playerId = Number(event.currentTarget.dataset.id);
    const player = this.data.players.find((item) => item.id === playerId);
    if (!player) {
      return;
    }
    this.setData({
      playerDialogVisible: true,
      playerErrors: {},
      playerForm: {
        id: player.id,
        name: player.name,
        jerseyNo: player.jerseyNo,
        position: player.position ?? "",
        status: player.status
      }
    });
  },

  closePlayerDialog() {
    this.setData({ playerDialogVisible: false });
  },

  handlePlayerPopupChange(event: WechatMiniprogram.CustomEvent<any>) {
    const detail = event.detail as { visible?: boolean } | boolean;
    const visible = typeof detail === "boolean" ? detail : Boolean(detail?.visible);
    this.setData({ playerDialogVisible: visible });
  },

  handlePlayerInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as "name" | "position";
    this.setData({
      [`playerForm.${field}`]: event.detail.value,
      [`playerErrors.${field}`]: ""
    });
  },

  handlePlayerJerseyInput(event: WechatMiniprogram.Input) {
    const raw = event.detail.value.trim();
    const jerseyNo = raw ? Number(raw) : null;
    this.setData({
      "playerForm.jerseyNo": jerseyNo,
      "playerErrors.jerseyNo": ""
    });
  },

  handlePlayerStatusChange(event: WechatMiniprogram.SwitchChange) {
    this.setData({ "playerForm.status": event.detail.value ? "ACTIVE" : "INACTIVE" });
  },

  async savePlayer() {
    if (this.data.submitting) {
      return;
    }
    const form = this.data.playerForm;
    const errors = validatePlayerForm(form);
    this.setData({ playerErrors: errors });
    if (hasFieldErrors(errors)) {
      return;
    }
    this.setData({ submitting: true });
    try {
      const saved = await withSubmitLock(submitLock, async () => {
        const payload = {
          name: form.name.trim(),
          jerseyNo: form.jerseyNo,
          position: form.position.trim() || null
        };
        if (form.id === null) {
          await createManagedPlayer(this.data.teamId, payload);
        } else {
          await updateManagedPlayer(form.id, { ...payload, status: form.status });
        }
        return true;
      });
      if (!saved) {
        return;
      }
      this.setData({ playerDialogVisible: false });
      await this.reloadPlayers();
      wx.showToast({ title: "球员已保存", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  async reloadPlayers() {
    this.setData({ players: await fetchManagedTeamPlayers(this.data.teamId) });
  }
});

function canManageTeamWithSession(session: Session, teamId: number): boolean {
  return session.role === "ADMIN"
    || (session.role === "TEAM_MANAGER" && session.managedTeamIds.includes(teamId));
}

