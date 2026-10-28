import { refreshSession } from "../../services/auth";
import {
  createManagedPlayer,
  createSuspension,
  createTeam,
  fetchAdminTeams,
  fetchManagedTeamPlayers,
  fetchTeamSuspensions,
  updateManagedPlayer,
  updateManagedTeam,
  updateSuspension
} from "../../services/manage";
import { fetchSeasons, fetchTeam } from "../../services/teams";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type { PlayerStatus, PlayerSuspension, PlayerSummary, Season, Session, SuspensionStatus, TeamStatus, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { hasFieldErrors, validatePlayerForm, validateTeamForm, type FieldErrors } from "../../utils/validation";

type TeamFormState = {
  name: string;
  shortName: string;
  coachName: string;
  homeVenue: string;
  status: TeamStatus;
};

type ManagedPlayerView = PlayerSummary & {
  suspended: boolean;
  suspensionText: string;
  suspensionRemaining: number;
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
    players: [] as ManagedPlayerView[],
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonIndex: 0,
    suspensionItems: [] as PlayerSuspension[],
    activeSuspensionItems: [] as PlayerSuspension[],
    suspensionPlayerOptions: [] as Array<{ id: number; name: string }>,
    suspensionForm: { playerIndex: 0, matchesTotal: "1", reason: "" },
    suspensionSubmitting: false,
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
      const isAdmin = session.role === "ADMIN" || session.role === "TEAM_MANAGER";
      if (this.data.createMode && !isAdmin) {
        this.setData({ error: "只有超级管理员或授权管理员可以创建球队", verified: false });
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
      const [players, seasons] = await Promise.all([
        fetchManagedTeamPlayers(this.data.teamId),
        fetchSeasons()
      ]);
      const selectedSeasonId = seasons.find((season) => season.status === "ACTIVE")?.id ?? seasons[0]?.id ?? 0;
      const suspensionItems = selectedSeasonId
        ? await fetchTeamSuspensions(this.data.teamId, selectedSeasonId)
        : [];
      this.setData({
        isAdmin,
        verified: true,
        team,
        seasons,
        selectedSeasonId,
        selectedSeasonIndex: Math.max(0, seasons.findIndex((season) => season.id === selectedSeasonId)),
        players: buildManagedPlayers(players, suspensionItems),
        suspensionItems,
        activeSuspensionItems: suspensionItems.filter((item) => item.status === "ACTIVE" && item.matchesRemaining > 0),
        suspensionPlayerOptions: buildSuspensionPlayerOptions(players, suspensionItems),
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

  async selectSuspensionSeason(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const season = this.data.seasons[index];
    if (!season) return;
    this.setData({ selectedSeasonId: season.id, selectedSeasonIndex: index });
    await this.reloadPlayers();
  },

  handleSuspensionPlayerChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ "suspensionForm.playerIndex": Number(event.detail.value) });
  },

  handleSuspensionMatchesInput(event: WechatMiniprogram.Input) {
    this.setData({ "suspensionForm.matchesTotal": event.detail.value });
  },

  handleSuspensionReasonInput(event: WechatMiniprogram.Input) {
    this.setData({ "suspensionForm.reason": event.detail.value });
  },

  async createManualSuspension() {
    if (!this.data.isAdmin || this.data.suspensionSubmitting) return;
    const player = this.data.players[this.data.suspensionForm.playerIndex];
    const matches = Number(this.data.suspensionForm.matchesTotal);
    const reason = this.data.suspensionForm.reason.trim();
    if (!player || !Number.isInteger(matches) || matches < 1 || matches > 20) {
      wx.showToast({ title: "停赛场次必须为1到20场", icon: "none" });
      return;
    }
    if (!reason) {
      wx.showToast({ title: "请填写禁赛原因", icon: "none" });
      return;
    }
    this.setData({ suspensionSubmitting: true });
    try {
      await createSuspension(player.id, this.data.selectedSeasonId, matches, reason);
      this.setData({ suspensionForm: { playerIndex: 0, matchesTotal: "1", reason: "" } });
      await this.reloadPlayers();
      wx.showToast({ title: "禁赛已添加", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ suspensionSubmitting: false });
    }
  },

  extendSuspension(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const item = this.data.suspensionItems.find((suspension) => suspension.id === id);
    if (!item) return;
    void this.updateSuspensionStatus(id, item.matchesTotal + 1, "ACTIVE");
  },

  cancelSuspension(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const item = this.data.suspensionItems.find((suspension) => suspension.id === id);
    if (!item) return;
    wx.showModal({
      title: "解除禁赛",
      content: `确认解除 ${item.playerName} 的剩余 ${item.matchesRemaining} 场禁赛？`,
      success: (result) => {
        if (result.confirm) void this.updateSuspensionStatus(id, item.matchesTotal, "CANCELLED");
      }
    });
  },

  async updateSuspensionStatus(id: number, matchesTotal: number, status: SuspensionStatus) {
    if (!this.data.isAdmin || this.data.suspensionSubmitting) return;
    this.setData({ suspensionSubmitting: true });
    try {
      await updateSuspension(id, matchesTotal, status);
      await this.reloadPlayers();
      wx.showToast({ title: status === "CANCELLED" ? "禁赛已解除" : "禁赛已延长", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ suspensionSubmitting: false });
    }
  },

  async reloadPlayers() {
    const players = await fetchManagedTeamPlayers(this.data.teamId);
    const suspensionItems = this.data.selectedSeasonId
      ? await fetchTeamSuspensions(this.data.teamId, this.data.selectedSeasonId)
      : [];
    this.setData({
      players: buildManagedPlayers(players, suspensionItems),
      suspensionItems,
      activeSuspensionItems: suspensionItems.filter((item) => item.status === "ACTIVE" && item.matchesRemaining > 0),
      suspensionPlayerOptions: buildSuspensionPlayerOptions(players, suspensionItems)
    });
  }
});

function buildManagedPlayers(players: PlayerSummary[], suspensions: PlayerSuspension[]): ManagedPlayerView[] {
  return players.map((player) => {
    const active = suspensions.filter((item) =>
      item.playerId === player.id && item.status === "ACTIVE" && item.matchesRemaining > 0);
    const remaining = active.reduce((total, item) => total + item.matchesRemaining, 0);
    return {
      ...player,
      suspended: remaining > 0,
      suspensionRemaining: remaining,
      suspensionText: remaining > 0 ? `停赛 ${remaining} 场` : ""
    };
  });
}

function buildSuspensionPlayerOptions(players: PlayerSummary[], suspensions: PlayerSuspension[]) {
  return buildManagedPlayers(players, suspensions).map((player) => ({
    id: player.id,
    name: player.jerseyNo === null ? player.name : `${player.jerseyNo} ${player.name}`
  }));
}

function canManageTeamWithSession(session: Session, teamId: number): boolean {
  return session.role === "ADMIN"
    || session.role === "TEAM_MANAGER"
    || (session.role === "TEAM_LEADER" && session.managedTeamIds.includes(teamId));
}

