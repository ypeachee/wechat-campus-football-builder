import { refreshSession } from "../../services/auth";
import { fetchAdminFixtures, fetchAdminSeasons, fetchFixtureDetail, fetchPublicFixtures } from "../../services/competition";
import {
  createAppearance,
  createEvent,
  replaceTeamLineup,
  fetchFixtureSuspensions,
  deleteAppearance,
  deleteEvent,
  fetchAdminTeams,
  fetchManagedTeamPlayers,
  updateAppearance,
  updateEvent
} from "../../services/manage";
import { fetchSeasons, fetchTeamPlayers, fetchTeams } from "../../services/teams";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../../state/season";
import { SubmitLock, withSubmitLock } from "../../state/submit-lock";
import type {
  EventType,
  FixtureDetail,
  FixtureSummary,
  PlayerSuspension,
  PlayerSummary,
  Season,
  Session,
  TeamSummary
} from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { eventTypeLabel, eventTypeTone, fixtureStatusLabel } from "../../utils/status";

type Option = { id: number; name: string };
type LineupPlayerView = {
  playerId: number;
  label: string;
  positionLabel: string;
  visible: boolean;
  starter: boolean;
  substitute: boolean;
  suspended: boolean;
  suspensionText: string;
  minutesPlayed: string;
};
type LineupEditorView = {
  teamId: number;
  teamName: string;
  players: LineupPlayerView[];
  searchText: string;
  saving: boolean;
};
type EventForm = {
  id: number | null;
  teamIndex: number;
  playerIndex: number;
  eventType: EventType;
  relatedIndex: number;
  minute: string;
  stoppageMinute: string;
  note: string;
};
type AppearanceForm = {
  id: number | null;
  teamIndex: number;
  playerIndex: number;
  isStarter: boolean;
  minutesPlayed: string;
};

const submitLock = new SubmitLock();

Page({
  data: {
    verified: false,
    isAdmin: false,
    isTeamLeader: false,
    canWriteEvents: false,
    fixtureSuspensions: [] as PlayerSuspension[],
    loading: true,
    submitting: false,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonName: "",
    selectedSeasonIndex: 0,
    fixtureOptions: [] as Array<{ id: number; label: string }>,
    selectedFixtureIndex: 0,
    selectedFixtureLabel: "",
    fixture: null as FixtureDetail | null,
    statusLabel: "",
    canWriteData: false,
    eventViews: [] as Array<{ id: number; minuteLabel: string; label: string; tone: string; playerName: string; relatedName: string }>,
    appearanceViews: [] as Array<{ id: number; playerName: string; teamName: string; starterText: string; minutesText: string }>,
    teamOptions: [] as Option[],
    homePlayers: [] as PlayerSummary[],
    awayPlayers: [] as PlayerSummary[],
    eventTeamPlayers: [] as Option[],
    eventRelatedPlayers: [{ id: 0, name: "无助攻" }] as Option[],
    appearancePlayers: [] as Option[],
    eventTypeOptions: [
      { value: "GOAL", label: "进球" },
      { value: "OWN_GOAL", label: "乌龙球" },
      { value: "YELLOW_CARD", label: "黄牌" },
      { value: "RED_CARD", label: "红牌" }
    ] as Array<{ value: EventType; label: string }>,
    eventForm: { id: null, teamIndex: 0, playerIndex: 0, eventType: "GOAL", relatedIndex: 0, minute: "1", stoppageMinute: "0", note: "" } as EventForm,
    appearanceForm: { id: null, teamIndex: 0, playerIndex: 0, isStarter: true, minutesPlayed: "90" } as AppearanceForm,
    eventTeamName: "",
    appearanceTeamName: "",
    lineupEditors: [] as LineupEditorView[],
    eventPlayerName: "",
    appearancePlayerName: "",
    eventRelatedName: "无助攻",
    eventTypeLabelText: "进球"
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
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER" && session.role !== "TEAM_LEADER")) {
        this.setData({ verified: false, error: "没有比赛数据录入权限" });
        return;
      }
      const isAuthorizedAdmin = session.role === "ADMIN" || session.role === "TEAM_MANAGER";
      const isLeader = session.role === "TEAM_LEADER";
      const seasons = isAuthorizedAdmin ? await fetchAdminSeasons() : await fetchSeasons();
      const selectedSeasonId = selectDefaultSeason(seasons) ?? getSelectedSeasonId() ?? 0;
      this.setData({
        verified: true,
        isAdmin: isAuthorizedAdmin,
        isTeamLeader: isLeader,
        canWriteEvents: isAuthorizedAdmin,
        seasons,
        selectedSeasonId,
        selectedSeasonName: seasons.find((season) => season.id === selectedSeasonId)?.name ?? "",
        selectedSeasonIndex: Math.max(0, seasons.findIndex((season) => season.id === selectedSeasonId))
      });
      if (selectedSeasonId) {
        await this.loadFixtures(selectedSeasonId, session);
      }
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async loadFixtures(seasonId: number, session: Session) {
    const [fixtures, teams] = await Promise.all([
      (session.role === "ADMIN" || session.role === "TEAM_MANAGER") ? fetchAdminFixtures(seasonId) : fetchPublicFixtures(seasonId),
      (session.role === "ADMIN" || session.role === "TEAM_MANAGER") ? fetchAdminTeams() : fetchTeams()
    ]);
    const managedIds = new Set(session.managedTeamIds);
    const allowedFixtures = (session.role === "ADMIN" || session.role === "TEAM_MANAGER")
      ? fixtures
      : fixtures.filter((fixture) => managedIds.has(fixture.homeTeamId) || managedIds.has(fixture.awayTeamId));
    const teamMap = new Map(teams.map((team) => [team.id, team.name]));
    const fixtureOptions = allowedFixtures.map((fixture) => ({
      id: fixture.id,
      label: `${teamMap.get(fixture.homeTeamId) ?? fixture.homeTeamId} vs ${teamMap.get(fixture.awayTeamId) ?? fixture.awayTeamId}`
    }));
    this.setData({
      fixtureOptions,
      selectedFixtureIndex: 0,
      selectedFixtureLabel: fixtureOptions[0]?.label ?? ""
    });
    if (allowedFixtures.length > 0) {
      await this.loadFixture(allowedFixtures[0].id, session);
    } else {
      this.clearFixture();
    }
  },

  async loadFixture(fixtureId: number, session: Session) {
    const fixture = await fetchFixtureDetail(fixtureId);
    const [homePlayers, awayPlayers, fixtureSuspensions] = (session.role === "ADMIN" || session.role === "TEAM_MANAGER")
      ? await Promise.all([fetchManagedTeamPlayers(fixture.homeTeam.id), fetchManagedTeamPlayers(fixture.awayTeam.id),
          fetchFixtureSuspensions(fixture.id)])
      : await Promise.all([fetchTeamPlayers(fixture.homeTeam.id), fetchTeamPlayers(fixture.awayTeam.id),
          fetchFixtureSuspensions(fixture.id)]);
    const playerMap = new Map<number, PlayerSummary>();
    [...homePlayers, ...awayPlayers].forEach((player) => playerMap.set(player.id, player));
    const suspensionByPlayer = new Map<number, number>();
    fixtureSuspensions.forEach((item) => {
      suspensionByPlayer.set(item.playerId, (suspensionByPlayer.get(item.playerId) ?? 0) + item.matchesRemaining);
    });
    const playerLabel = (id: number | null) => {
      if (id === null) return "";
      const player = playerMap.get(id);
      if (!player) return `球员 #${id}`;
      return player.jerseyNo === null ? player.name : `${player.jerseyNo} ${player.name}`;
    };
    const managedIds = new Set(session.managedTeamIds);
    const teamOptions: Option[] = [
      { id: fixture.homeTeam.id, name: fixture.homeTeam.name },
      { id: fixture.awayTeam.id, name: fixture.awayTeam.name }
    ].filter((team) => session.role === "ADMIN" || session.role === "TEAM_MANAGER" || managedIds.has(team.id));

    const canWriteData = !fixture.dataLocked
      && ["SCHEDULED", "LIVE", "FINISHED"].includes(fixture.status);
    const lineupEditors = teamOptions.map((team) => {
      const existing = fixture.appearances.filter((appearance) => appearance.teamId === team.id);
      const roster = [...homePlayers, ...awayPlayers]
        .filter((player) => player.teamId === team.id && player.status === "ACTIVE")
        .sort((a, b) => positionRank(a.position) - positionRank(b.position)
          || jerseySort(a.jerseyNo, b.jerseyNo) || a.name.localeCompare(b.name));
      return {
        teamId: team.id,
        teamName: team.name,
        searchText: "",
        saving: false,
        players: roster.map((player) => {
          const appearance = existing.find((item) => item.playerId === player.id);
          const starter = Boolean(appearance?.starter);
          return {
            playerId: player.id,
            label: player.jerseyNo === null ? player.name : `${player.jerseyNo} ${player.name}`,
            positionLabel: positionLabel(player.position),
            visible: true,
            starter,
            substitute: starter || Boolean(appearance && !appearance.starter),
            suspended: suspensionByPlayer.has(player.id),
            suspensionText: suspensionByPlayer.has(player.id)
              ? `停赛 ${suspensionByPlayer.get(player.id)} 场` : "",
            minutesPlayed: appearance?.minutesPlayed === null || appearance?.minutesPlayed === undefined
              ? "30" : String(appearance.minutesPlayed)
          };
        })
      };
    });
    this.setData({
      fixture,
      statusLabel: fixtureStatusLabel(fixture.status),
      canWriteData,
      fixtureSuspensions,
      teamOptions,
      homePlayers,
      awayPlayers,
      eventViews: fixture.events.map((event) => ({
        id: event.id,
        minuteLabel: `${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'`,
        label: eventTypeLabel(event.eventType),
        tone: eventTypeTone(event.eventType),
        playerName: playerLabel(event.playerId),
        relatedName: playerLabel(event.relatedPlayerId)
      })),
      lineupEditors,
      appearanceViews: fixture.appearances.map((appearance) => ({
        id: appearance.id,
        playerName: playerLabel(appearance.playerId),
        teamName: appearance.teamId === fixture.homeTeam.id ? fixture.homeTeam.name : fixture.awayTeam.name,
        starterText: appearance.starter ? "首发" : "替补",
        minutesText: appearance.minutesPlayed === null ? "未录分钟" : `${appearance.minutesPlayed} 分钟`
      })),
      eventTeamName: teamOptions[0]?.name ?? "",
      appearanceTeamName: teamOptions[0]?.name ?? "",
      eventPlayerName: "",
      appearancePlayerName: "",
      eventRelatedName: "无助攻"
    });
    this.refreshEventPlayers(fixture, playerMap, teamOptions[0]?.id ?? 0, "GOAL", 0);
    this.refreshAppearancePlayers(fixture, playerMap, teamOptions[0]?.id ?? 0, 0);
    this.resetForms();
  },

  selectSeason(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const seasonId = this.data.seasons[index]?.id;
    if (!seasonId) {
      return;
    }
    setSelectedSeasonId(seasonId);
    this.setData({
      selectedSeasonId: seasonId,
      selectedSeasonName: this.data.seasons.find((season) => season.id === seasonId)?.name ?? "",
      selectedSeasonIndex: index,
      loading: true
    });
    void refreshSession()
      .then((session) => session ? this.loadFixtures(seasonId, session) : undefined)
      .catch((error) => this.setData({ error: errorMessage(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  selectFixture(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const fixture = this.data.fixtureOptions[index];
    if (!fixture) {
      return;
    }
    this.setData({ selectedFixtureIndex: index, selectedFixtureLabel: fixture.label, loading: true });
    void refreshSession()
      .then((session) => session ? this.loadFixture(fixture.id, session) : undefined)
      .catch((error) => this.setData({ error: errorMessage(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  async refresh() {
    const session = await refreshSession();
    if (session && this.data.fixture) {
      await this.loadFixture(this.data.fixture.id, session);
    }
  },

  resetForms() {
    this.setData({
      eventForm: { id: null, teamIndex: 0, playerIndex: 0, eventType: "GOAL", relatedIndex: 0, minute: "1", stoppageMinute: "0", note: "" },
      appearanceForm: { id: null, teamIndex: 0, playerIndex: 0, isStarter: true, minutesPlayed: "90" }
    });
  },

  refreshEventPlayers(fixture: FixtureDetail, playerMap: Map<number, PlayerSummary>, eventTeamId: number, eventType: EventType, relatedIndex: number) {
    const opponentId = eventTeamId === fixture.homeTeam.id ? fixture.awayTeam.id : fixture.homeTeam.id;
    const playerTeamId = eventType === "OWN_GOAL" ? opponentId : eventTeamId;
    const allPlayers = [...playerMap.values()].filter((player) => player.status === "ACTIVE");
    const label = (player: PlayerSummary) => player.jerseyNo === null
      ? player.name : `${player.jerseyNo} ${player.name}`;
    const eventTeamPlayers = allPlayers.filter((player) => player.teamId === playerTeamId)
      .map((player) => ({ id: player.id, name: label(player) }));
    const relatedPlayers = [{ id: 0, name: "无助攻" }, ...allPlayers
      .filter((player) => player.teamId === eventTeamId)
      .map((player) => ({ id: player.id, name: label(player) }))];
    this.setData({
      eventTeamPlayers,
      eventRelatedPlayers: relatedPlayers,
      "eventForm.playerIndex": 0,
      "eventForm.relatedIndex": Math.min(relatedIndex, Math.max(0, relatedPlayers.length - 1)),
      eventPlayerName: eventTeamPlayers[0]?.name ?? "",
      eventRelatedName: relatedPlayers[Math.min(relatedIndex, Math.max(0, relatedPlayers.length - 1))]?.name ?? "无助攻"
    });
  },

  refreshAppearancePlayers(fixture: FixtureDetail, playerMap: Map<number, PlayerSummary>, teamId: number, playerIndex: number) {
    const appearancePlayers = [...playerMap.values()]
      .filter((player) => player.teamId === teamId && player.status === "ACTIVE")
      .map((player) => ({
        id: player.id,
        name: player.jerseyNo === null ? player.name : `${player.jerseyNo} ${player.name}`
      }));
    const nextIndex = Math.min(playerIndex, Math.max(0, appearancePlayers.length - 1));
    this.setData({
      appearancePlayers,
      "appearanceForm.playerIndex": nextIndex,
      appearancePlayerName: appearancePlayers[nextIndex]?.name ?? ""
    });
  },

  handleEventTeamChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const team = this.data.teamOptions[index];
    const fixture = this.data.fixture;
    if (!team || !fixture) return;
    const playerMap = this.buildPlayerMap();
    this.setData({ "eventForm.teamIndex": index, eventTeamName: team.name });
    this.refreshEventPlayers(fixture, playerMap, team.id, this.data.eventForm.eventType, 0);
  },

  handleEventTypeChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const type = this.data.eventTypeOptions[index]?.value ?? "GOAL";
    const fixture = this.data.fixture;
    const team = this.data.teamOptions[this.data.eventForm.teamIndex];
    if (!fixture || !team) return;
    this.setData({
      "eventForm.eventType": type,
      eventTypeLabelText: this.data.eventTypeOptions[index]?.label ?? "进球"
    });
    this.refreshEventPlayers(fixture, this.buildPlayerMap(), team.id, type, 0);
  },

  handleEventPlayerChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({
      "eventForm.playerIndex": index,
      eventPlayerName: this.data.eventTeamPlayers[index]?.name ?? ""
    });
  },

  handleEventRelatedChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({
      "eventForm.relatedIndex": index,
      eventRelatedName: this.data.eventRelatedPlayers[index]?.name ?? "无助攻"
    });
  },

  toggleLineupStarter(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    const playerId = Number(event.currentTarget.dataset.playerId);
    this.updateLineupPlayer(teamId, playerId, (player) => {
      if (player.suspended) return;
      player.starter = !player.starter;
      player.substitute = player.starter;
      if (player.starter) player.minutesPlayed = "90";
    });
  },

  toggleLineupSubstitute(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    const playerId = Number(event.currentTarget.dataset.playerId);
    this.updateLineupPlayer(teamId, playerId, (player) => {
      if (player.suspended || player.starter) return;
      player.substitute = !player.substitute;
      if (player.substitute && !player.minutesPlayed) player.minutesPlayed = "30";
    });
  },

  handleLineupSearch(event: WechatMiniprogram.Input) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    const query = event.detail.value.trim().toLowerCase();
    const editors = this.data.lineupEditors.map((editor) => ({
      ...editor,
      searchText: editor.teamId === teamId ? event.detail.value : editor.searchText,
      players: editor.players.map((player) => ({
        ...player,
        visible: editor.teamId !== teamId || !query
          || `${player.label} ${player.positionLabel}`.toLowerCase().includes(query)
      }))
    }));
    this.setData({ lineupEditors: editors });
  },

  clearLineup(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    wx.showModal({
      title: "清空阵容",
      content: "清空当前球队的首发和替补勾选？保存后才会生效。",
      success: (result) => {
        if (!result.confirm) return;
        const editors = this.data.lineupEditors.map((editor) => editor.teamId === teamId ? {
          ...editor,
          players: editor.players.map((player) => ({ ...player, starter: false, substitute: false, minutesPlayed: "30" }))
        } : editor);
        this.setData({ lineupEditors: editors });
      }
    });
  },

  async copyPreviousLineup(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    if (!teamId || !this.data.fixture) return;
    for (let index = this.data.selectedFixtureIndex - 1; index >= 0; index -= 1) {
      const option = this.data.fixtureOptions[index];
      if (!option) continue;
      const detail = await fetchFixtureDetail(option.id).catch(() => null);
      const previous = detail?.appearances.filter((item) => item.teamId === teamId) ?? [];
      if (previous.length === 0) continue;
      const byPlayer = new Map(previous.map((item) => [item.playerId, item]));
      const editors = this.data.lineupEditors.map((editor) => editor.teamId === teamId ? {
        ...editor,
        players: editor.players.map((player) => {
          const appearance = byPlayer.get(player.playerId);
          return {
            ...player,
            starter: Boolean(appearance?.starter),
            substitute: Boolean(appearance),
            minutesPlayed: appearance?.minutesPlayed === null || appearance?.minutesPlayed === undefined
              ? "30" : String(appearance.minutesPlayed)
          };
        })
      } : editor);
      this.setData({ lineupEditors: editors });
      wx.showToast({ title: "已复制上一场", icon: "success" });
      return;
    }
    wx.showToast({ title: "没有可复制的上一场阵容", icon: "none" });
  },

  handleLineupMinutes(event: WechatMiniprogram.Input) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    const playerId = Number(event.currentTarget.dataset.playerId);
    this.updateLineupPlayer(teamId, playerId, (player) => {
      if (!player.starter) player.minutesPlayed = event.detail.value;
    });
  },

  updateLineupPlayer(teamId: number, playerId: number, update: (player: LineupPlayerView) => void) {
    const editors = this.data.lineupEditors.map((editor) => ({
      ...editor,
      players: editor.players.map((player) => ({ ...player }))
    }));
    const editor = editors.find((item) => item.teamId === teamId);
    const player = editor?.players.find((item) => item.playerId === playerId);
    if (!editor || !player) return;
    update(player);
    this.setData({ lineupEditors: editors });
  },

  async saveLineup(event: WechatMiniprogram.BaseEvent) {
    const teamId = Number(event.currentTarget.dataset.teamId);
    const editorIndex = this.data.lineupEditors.findIndex((item) => item.teamId === teamId);
    const editor = this.data.lineupEditors[editorIndex];
    if (!this.data.fixture || !editor) return;
    const starters = editor.players.filter((player) => player.starter && !player.suspended).map((player) => player.playerId);
    const substitutes = editor.players
      .filter((player) => !player.starter && player.substitute && !player.suspended)
      .map((player) => ({ playerId: player.playerId, minutesPlayed: Number(player.minutesPlayed) || 0 }));
    if (substitutes.some((item) => item.minutesPlayed < 0 || item.minutesPlayed > 130)) {
      wx.showToast({ title: "替补出场分钟必须为 0–130", icon: "none" });
      return;
    }
    this.setData({ [`lineupEditors[${editorIndex}].saving`]: true });
    try {
      await replaceTeamLineup(this.data.fixture.id, teamId, starters, substitutes);
      await this.refresh();
      wx.showToast({ title: "阵容已保存", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ [`lineupEditors[${editorIndex}].saving`]: false });
    }
  },

  noop() {},

  handleAppearanceTeamChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const team = this.data.teamOptions[index];
    const fixture = this.data.fixture;
    if (!team || !fixture) return;
    this.setData({ "appearanceForm.teamIndex": index, appearanceTeamName: team.name });
    this.refreshAppearancePlayers(fixture, this.buildPlayerMap(), team.id, 0);
  },

  handleAppearancePlayerChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    this.setData({
      "appearanceForm.playerIndex": index,
      appearancePlayerName: this.data.appearancePlayers[index]?.name ?? ""
    });
  },

  handleEventInput(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field as "minute" | "stoppageMinute" | "note";
    this.setData({ [`eventForm.${field}`]: event.detail.value });
  },

  handleAppearanceInput(event: WechatMiniprogram.Input) {
    this.setData({ "appearanceForm.minutesPlayed": event.detail.value });
  },

  handleAppearanceStarter(event: WechatMiniprogram.SwitchChange) {
    this.setData({ "appearanceForm.isStarter": event.detail.value });
  },

  async saveEvent() {
    const fixture = this.data.fixture;
    const team = this.data.teamOptions[this.data.eventForm.teamIndex];
    const player = this.data.eventTeamPlayers[this.data.eventForm.playerIndex];
    if (!fixture || !team || !player) {
      wx.showToast({ title: "请选择球队和球员", icon: "none" });
      return;
    }
    const minute = Number(this.data.eventForm.minute);
    const stoppage = Number(this.data.eventForm.stoppageMinute);
    if (!Number.isInteger(minute) || minute < 1 || minute > 130 || !Number.isInteger(stoppage) || stoppage < 0 || stoppage > 30) {
      wx.showToast({ title: "比赛时间不合法", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      const related = this.data.eventRelatedPlayers[this.data.eventForm.relatedIndex];
      const payload = {
        teamId: team.id,
        playerId: player.id,
        eventType: this.data.eventForm.eventType,
        relatedPlayerId: related && related.id > 0 ? related.id : null,
        minute,
        stoppageMinute: stoppage,
        note: this.data.eventForm.note.trim() || null
      };
      const saved = await withSubmitLock(submitLock, async () => {
        if (this.data.eventForm.id === null) {
          await createEvent(fixture.id, payload);
        } else {
          const { teamId: _teamId, ...updatePayload } = payload;
          await updateEvent(this.data.eventForm.id, updatePayload);
        }
        return true;
      });
      if (saved) {
        this.resetEventForm();
        await this.refresh();
        wx.showToast({ title: "事件已保存", icon: "success" });
      }
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  async saveAppearance() {
    const fixture = this.data.fixture;
    const team = this.data.teamOptions[this.data.appearanceForm.teamIndex];
    const player = this.data.appearancePlayers[this.data.appearanceForm.playerIndex];
    if (!fixture || !team || !player) {
      wx.showToast({ title: "请选择球队和球员", icon: "none" });
      return;
    }
    const minutes = this.data.appearanceForm.minutesPlayed ? Number(this.data.appearanceForm.minutesPlayed) : null;
    if (minutes !== null && (!Number.isInteger(minutes) || minutes < 0 || minutes > 130)) {
      wx.showToast({ title: "出场分钟必须在 0 到 130 之间", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      const saved = await withSubmitLock(submitLock, async () => {
        if (this.data.appearanceForm.id === null) {
          await createAppearance(fixture.id, {
            teamId: team.id,
            playerId: player.id,
            isStarter: this.data.appearanceForm.isStarter,
            minutesPlayed: minutes
          });
        } else {
          await updateAppearance(this.data.appearanceForm.id, {
            isStarter: this.data.appearanceForm.isStarter,
            minutesPlayed: minutes
          });
        }
        return true;
      });
      if (saved) {
        this.resetAppearanceForm();
        await this.refresh();
        wx.showToast({ title: "出场记录已保存", icon: "success" });
      }
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  resetEventForm() {
    const team = this.data.teamOptions[0];
    this.setData({
      eventForm: { id: null, teamIndex: 0, playerIndex: 0, eventType: "GOAL", relatedIndex: 0, minute: "1", stoppageMinute: "0", note: "" },
      eventTeamName: team?.name ?? "",
      eventPlayerName: "",
      eventRelatedName: "无助攻",
      eventTypeLabelText: "进球"
    });
    if (this.data.fixture && team) {
      this.refreshEventPlayers(this.data.fixture, this.buildPlayerMap(), team.id, "GOAL", 0);
    }
  },

  resetAppearanceForm() {
    const team = this.data.teamOptions[0];
    this.setData({
      appearanceForm: { id: null, teamIndex: 0, playerIndex: 0, isStarter: true, minutesPlayed: "90" },
      appearanceTeamName: team?.name ?? "",
      appearancePlayerName: ""
    });
    if (this.data.fixture && team) {
      this.refreshAppearancePlayers(this.data.fixture, this.buildPlayerMap(), team.id, 0);
    }
  },

  cancelEventEdit() {
    this.resetEventForm();
  },

  cancelAppearanceEdit() {
    this.resetAppearanceForm();
  },

  editEvent(event: WechatMiniprogram.BaseEvent) {
    const eventId = Number(event.currentTarget.dataset.id);
    const item = this.data.fixture?.events.find((row) => row.id === eventId);
    if (!item || !this.data.fixture) return;
    const teamIndex = this.data.teamOptions.findIndex((team) => team.id === item.teamId);
    if (teamIndex < 0) return;
    const team = this.data.teamOptions[teamIndex];
    this.setData({
      eventForm: {
        id: item.id,
        teamIndex,
        playerIndex: 0,
        eventType: item.eventType,
        relatedIndex: 0,
        minute: String(item.minute),
        stoppageMinute: String(item.stoppageMinute),
        note: item.note ?? ""
      },
      eventTeamName: team.name,
      eventTypeLabelText: eventTypeLabel(item.eventType)
    });
    this.refreshEventPlayers(this.data.fixture, this.buildPlayerMap(), team.id, item.eventType, 0);
    const players = this.data.eventTeamPlayers;
    const playerIndex = players.findIndex((player) => player.id === item.playerId);
    const relatedIndex = this.data.eventRelatedPlayers.findIndex((player) => player.id === item.relatedPlayerId);
    this.setData({
      "eventForm.playerIndex": Math.max(0, playerIndex),
      "eventForm.relatedIndex": Math.max(0, relatedIndex),
      eventPlayerName: players[Math.max(0, playerIndex)]?.name ?? "",
      eventRelatedName: this.data.eventRelatedPlayers[Math.max(0, relatedIndex)]?.name ?? "无助攻"
    });
  },

  editAppearance(event: WechatMiniprogram.BaseEvent) {
    const appearanceId = Number(event.currentTarget.dataset.id);
    const item = this.data.fixture?.appearances.find((row) => row.id === appearanceId);
    if (!item || !this.data.fixture) return;
    const teamIndex = this.data.teamOptions.findIndex((team) => team.id === item.teamId);
    if (teamIndex < 0) return;
    const team = this.data.teamOptions[teamIndex];
    this.setData({
      appearanceForm: {
        id: item.id,
        teamIndex,
        playerIndex: 0,
        isStarter: item.starter,
        minutesPlayed: item.minutesPlayed === null ? "" : String(item.minutesPlayed)
      },
      appearanceTeamName: team.name
    });
    this.refreshAppearancePlayers(this.data.fixture, this.buildPlayerMap(), team.id, 0);
    const playerIndex = this.data.appearancePlayers.findIndex((player) => player.id === item.playerId);
    this.setData({
      "appearanceForm.playerIndex": Math.max(0, playerIndex),
      appearancePlayerName: this.data.appearancePlayers[Math.max(0, playerIndex)]?.name ?? ""
    });
  },

  deleteEventRecord(event: WechatMiniprogram.BaseEvent) {
    const eventId = Number(event.currentTarget.dataset.id);
    wx.showModal({
      title: "删除比赛事件",
      content: "确认删除该事件？",
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await deleteEvent(eventId);
          await this.refresh();
          wx.showToast({ title: "已删除", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  deleteAppearanceRecord(event: WechatMiniprogram.BaseEvent) {
    const appearanceId = Number(event.currentTarget.dataset.id);
    wx.showModal({
      title: "删除出场记录",
      content: "确认删除该出场记录？",
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await deleteAppearance(appearanceId);
          await this.refresh();
          wx.showToast({ title: "已删除", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  buildPlayerMap(): Map<number, PlayerSummary> {
    const map = new Map<number, PlayerSummary>();
    [...this.data.homePlayers, ...this.data.awayPlayers].forEach((player) => map.set(player.id, player));
    return map;
  },

  clearFixture() {
    this.setData({
      fixture: null,
      fixtureOptions: [],
      eventViews: [],
      appearanceViews: [],
      teamOptions: [],
      homePlayers: [],
      awayPlayers: [],
      eventTeamPlayers: [],
      appearancePlayers: [],
      lineupEditors: [],
      selectedFixtureLabel: ""
    });
  },

  goBack() {
    wx.navigateBack();
  }
});

function positionRank(position: string | null): number {
  const value = (position ?? "").toUpperCase();
  if (["GK", "门将", "守门员"].includes(value)) return 0;
  if (["DF", "DEF", "后卫"].includes(value)) return 1;
  if (["MF", "MID", "中场"].includes(value)) return 2;
  if (["FW", "FWD", "前锋"].includes(value)) return 3;
  return 4;
}

function positionLabel(position: string | null): string {
  switch (positionRank(position)) {
    case 0: return "门将";
    case 1: return "后卫";
    case 2: return "中场";
    case 3: return "前锋";
    default: return "其他";
  }
}

function jerseySort(left: string | number | null, right: string | number | null): number {
  return String(left ?? "999").localeCompare(String(right ?? "999"), "zh-CN", { numeric: true });
}
