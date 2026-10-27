import { refreshSession } from "../../services/auth";
import { fetchAdminFixtures, fetchAdminSeasons, fetchFixtureDetail, fetchPublicFixtures } from "../../services/competition";
import {
  createAppearance,
  createEvent,
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
  PlayerSummary,
  Season,
  Session,
  TeamSummary
} from "../../types/domain";
import { errorMessage } from "../../utils/error";
import { eventTypeLabel, eventTypeTone, fixtureStatusLabel } from "../../utils/status";

type Option = { id: number; name: string };
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
    loading: true,
    submitting: false,
    error: "",
    seasons: [] as Season[],
    selectedSeasonId: 0,
    selectedSeasonName: "",
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
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER")) {
        this.setData({ verified: false, error: "没有比赛数据录入权限" });
        return;
      }
      const seasons = session.role === "ADMIN" ? await fetchAdminSeasons() : await fetchSeasons();
      const selectedSeasonId = selectDefaultSeason(seasons) ?? getSelectedSeasonId() ?? 0;
      this.setData({
        verified: true,
        isAdmin: session.role === "ADMIN",
        seasons,
        selectedSeasonId,
        selectedSeasonName: seasons.find((season) => season.id === selectedSeasonId)?.name ?? ""
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
      session.role === "ADMIN" ? fetchAdminFixtures(seasonId) : fetchPublicFixtures(seasonId),
      session.role === "ADMIN" ? fetchAdminTeams() : fetchTeams()
    ]);
    const managedIds = new Set(session.managedTeamIds);
    const allowedFixtures = session.role === "ADMIN"
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
    const [homePlayers, awayPlayers] = this.data.isAdmin
      ? await Promise.all([fetchManagedTeamPlayers(fixture.homeTeam.id), fetchManagedTeamPlayers(fixture.awayTeam.id)])
      : await Promise.all([fetchTeamPlayers(fixture.homeTeam.id), fetchTeamPlayers(fixture.awayTeam.id)]);
    const playerMap = new Map<number, PlayerSummary>();
    [...homePlayers, ...awayPlayers].forEach((player) => playerMap.set(player.id, player));
    const playerName = (id: number | null) => id === null ? "" : playerMap.get(id)?.name ?? `球员 #${id}`;
    const managedIds = new Set(session.managedTeamIds);
    const teamOptions: Option[] = [
      { id: fixture.homeTeam.id, name: fixture.homeTeam.name },
      { id: fixture.awayTeam.id, name: fixture.awayTeam.name }
    ].filter((team) => session.role === "ADMIN" || managedIds.has(team.id));

    const canWriteData = !fixture.dataLocked && (
      session.role === "ADMIN"
        ? ["SCHEDULED", "LIVE", "FINISHED"].includes(fixture.status)
        : ["LIVE", "FINISHED"].includes(fixture.status)
    );
    this.setData({
      fixture,
      statusLabel: fixtureStatusLabel(fixture.status),
      canWriteData,
      teamOptions,
      homePlayers,
      awayPlayers,
      eventViews: fixture.events.map((event) => ({
        id: event.id,
        minuteLabel: `${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'`,
        label: eventTypeLabel(event.eventType),
        tone: eventTypeTone(event.eventType),
        playerName: playerName(event.playerId),
        relatedName: playerName(event.relatedPlayerId)
      })),
      appearanceViews: fixture.appearances.map((appearance) => ({
        id: appearance.id,
        playerName: playerName(appearance.playerId),
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
    const eventTeamPlayers = allPlayers.filter((player) => player.teamId === playerTeamId)
      .map((player) => ({ id: player.id, name: player.name }));
    const relatedPlayers = [{ id: 0, name: "无助攻" }, ...allPlayers
      .filter((player) => player.teamId === eventTeamId)
      .map((player) => ({ id: player.id, name: player.name }))];
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
      .map((player) => ({ id: player.id, name: player.name }));
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
      selectedFixtureLabel: ""
    });
  },

  goBack() {
    wx.navigateBack();
  }
});
