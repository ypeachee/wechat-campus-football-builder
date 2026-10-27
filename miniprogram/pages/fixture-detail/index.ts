import { fetchFixtureDetail } from "../../services/competition";
import { fetchFixtureRatings, fetchMyRating, fetchMyRefereeRating, fetchPlayerComments, fetchRefereeRating, reportRating, submitPlayerRating, submitRefereeRating } from "../../services/ratings";
import { fetchTeamPlayers } from "../../services/teams";
import type { FixtureDetail, OwnRating, PlayerRatingSummary, PlayerSummary, RatingComment, RefereeOwnRating, RefereeRatingSummary } from "../../types/domain";
import { getSession } from "../../state/session";
import { canRateFixture, ratingStatusText, starLabel, starsToScore } from "../../utils/rating";
import { formatMatchTime } from "../../utils/date";
import { errorMessage } from "../../utils/error";
import { eventTypeLabel, eventTypeTone, fixtureStatusLabel, fixtureStatusTone } from "../../utils/status";

type EventView = {
  id: number;
  minuteLabel: string;
  eventLabel: string;
  eventTone: "default" | "primary" | "success" | "warning" | "danger";
  teamName: string;
  playerName: string;
  relatedName: string;
  note: string;
};

type AppearanceView = {
  id: number;
  playerName: string;
  jerseyText: string;
  starterText: string;
  minutesText: string;
};

type PlayerRatingView = PlayerRatingSummary & { starsText: string };
type FixtureDetailData = {
  loading: boolean;
  error: string;
  fixtureId: number;
  fixture: FixtureDetail | null;
  statusLabel: string;
  statusTone: "default" | "primary" | "success" | "warning" | "danger";
  timeLabel: string;
  eventViews: EventView[];
  homeAppearances: AppearanceView[];
  awayAppearances: AppearanceView[];
  ratingEnabled: boolean;
  homeRatings: PlayerRatingView[];
  awayRatings: PlayerRatingView[];
  ratingDialogVisible: boolean;
  selectedPlayerId: number;
  selectedPlayerName: string;
  selectedTeamName: string;
  selectedRating: PlayerRatingSummary | null;
  selectedMyRating: OwnRating | null;
  selectedRatingStars: number;
  selectedRatingComment: string;
  selectedRatingStatusText: string;
  selectedRatingComments: RatingComment[];
  ratingSubmitting: boolean;
  refereeRating: RefereeRatingSummary | null;
  refereeStarsText: string;
  refereeOwnRating: RefereeOwnRating | null;
  refereeDialogVisible: boolean;
  refereeStars: number;
  refereeSubmitting: boolean;
};

Page({
  data: {
    loading: true,
    error: "",
    fixtureId: 0,
    fixture: null as FixtureDetail | null,
    statusLabel: "",
    statusTone: "default",
    timeLabel: "",
    eventViews: [] as EventView[],
    homeAppearances: [] as AppearanceView[],
    awayAppearances: [] as AppearanceView[],
    ratingEnabled: false,
    homeRatings: [] as PlayerRatingView[],
    awayRatings: [] as PlayerRatingView[],
    ratingDialogVisible: false,
    selectedPlayerId: 0,
    selectedPlayerName: "",
    selectedTeamName: "",
    selectedRating: null as PlayerRatingSummary | null,
    selectedMyRating: null as OwnRating | null,
    selectedRatingStars: 0,
    selectedRatingComment: "",
    selectedRatingStatusText: "",
    selectedRatingComments: [] as RatingComment[],
    ratingSubmitting: false,
    refereeRating: null as RefereeRatingSummary | null,
    refereeStarsText: "☆☆☆☆☆",
    refereeOwnRating: null as RefereeOwnRating | null,
    refereeDialogVisible: false,
    refereeStars: 0,
    refereeSubmitting: false
  } as FixtureDetailData,

  onLoad(options: Record<string, string | undefined>) {
    const fixtureId = Number(options.id ?? 0);
    this.setData({ fixtureId });
    void this.loadFixture(fixtureId);
  },

  onPullDownRefresh() {
    void this.loadFixture(this.data.fixtureId);
  },

  async loadFixture(fixtureId: number) {
    if (!fixtureId) {
      this.setData({ loading: false, error: "比赛不存在" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const fixture = await fetchFixtureDetail(fixtureId);
      const [homePlayers, awayPlayers, ratingSummaries, refereeRating] = await Promise.all([
        fetchTeamPlayers(fixture.homeTeam.id).catch(() => []),
        fetchTeamPlayers(fixture.awayTeam.id).catch(() => []),
        canRateFixture(fixture.status) ? fetchFixtureRatings(fixture.id).catch(() => []) : Promise.resolve([]),
        canRateFixture(fixture.status) && fixture.referee ? fetchRefereeRating(fixture.id).catch(() => null) : Promise.resolve(null)
      ]);
      const playerMap = new Map<number, PlayerSummary>();
      [...homePlayers, ...awayPlayers].forEach((player) => playerMap.set(player.id, player));
      const teamName = (teamId: number) => fixture.homeTeam.id === teamId
        ? fixture.homeTeam.name
        : fixture.awayTeam.name;
      const playerName = (playerId: number | null) => playerId === null
        ? ""
        : playerMap.get(playerId)?.name ?? `球员 #${playerId}`;

      const ratingViews = ratingSummaries.map((rating) => ({ ...rating, starsText: starLabel(rating.averageScore / 2) }));
      this.setData({
        fixture,
        ratingEnabled: canRateFixture(fixture.status),
        refereeRating,
        refereeStarsText: starLabel((refereeRating?.averageScore ?? 0) / 2),
        homeRatings: ratingViews.filter((rating) => rating.teamId === fixture.homeTeam.id),
        awayRatings: ratingViews.filter((rating) => rating.teamId === fixture.awayTeam.id),
        statusLabel: fixtureStatusLabel(fixture.status),
        statusTone: fixtureStatusTone(fixture.status),
        timeLabel: formatMatchTime(fixture.kickoffAt),
        eventViews: fixture.events.map((event) => ({
          id: event.id,
          minuteLabel: `${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'`,
          eventLabel: eventTypeLabel(event.eventType),
          eventTone: eventTypeTone(event.eventType),
          teamName: teamName(event.teamId),
          playerName: playerName(event.playerId),
          relatedName: playerName(event.relatedPlayerId),
          note: event.note ?? ""
        })),
        homeAppearances: buildAppearances(fixture, fixture.homeTeam.id, playerName),
        awayAppearances: buildAppearances(fixture, fixture.awayTeam.id, playerName)
      });
      wx.setNavigationBarTitle({ title: `${fixture.homeTeam.name} vs ${fixture.awayTeam.name}` });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async openRatingDialog(event: WechatMiniprogram.BaseEvent) {
    const playerId = Number(event.currentTarget.dataset.playerId);
    const rating = [...this.data.homeRatings, ...this.data.awayRatings].find((item) => item.playerId === playerId);
    if (!rating || !this.data.fixture) {
      return;
    }
    const auth = Boolean(getSession());
    this.setData({
      ratingDialogVisible: true,
      selectedPlayerId: playerId,
      selectedPlayerName: rating.playerName,
      selectedTeamName: rating.teamName,
      selectedRating: rating,
      selectedMyRating: null,
      selectedRatingStars: 0,
      selectedRatingComment: "",
      selectedRatingStatusText: "",
      selectedRatingComments: []
    });
    const [mine, comments] = await Promise.all([
      auth ? fetchMyRating(this.data.fixture.id, playerId).catch(() => null) : Promise.resolve(null),
      fetchPlayerComments(this.data.fixture.id, playerId, 0, 10).catch(() => null)
    ]);
    this.setData({
      selectedMyRating: mine?.rated ? mine : null,
      selectedRatingStars: mine?.stars ?? 0,
      selectedRatingComment: mine?.comment ?? "",
      selectedRatingStatusText: ratingStatusText(mine?.status ?? null),
      selectedRatingComments: comments?.comments ?? []
    });
  },

  closeRatingDialog() {
    if (!this.data.ratingSubmitting) {
      this.setData({ ratingDialogVisible: false });
    }
  },

  noop() {},

  handleRatingStars(event: WechatMiniprogram.CustomEvent<{ stars: number }>) {
    this.setData({ selectedRatingStars: event.detail.stars });
  },

  handleRatingComment(event: WechatMiniprogram.Input) {
    this.setData({ selectedRatingComment: event.detail.value });
  },

  async saveRating() {
    if (!getSession()) {
      wx.showToast({ title: "请先登录后评分", icon: "none" });
      return;
    }
    const playerId = this.data.selectedPlayerId;
    const stars = this.data.selectedRatingStars;
    if (!this.data.fixture || playerId <= 0 || stars < 1 || stars > 5) {
      wx.showToast({ title: "请选择 1–5 星", icon: "none" });
      return;
    }
    if (this.data.selectedRatingComment.length > 100) {
      wx.showToast({ title: "短评不能超过 100 字", icon: "none" });
      return;
    }
    this.setData({ ratingSubmitting: true });
    try {
      const result = await submitPlayerRating(this.data.fixture.id, playerId, stars,
        this.data.selectedRatingComment);
      this.setData({
        selectedMyRating: {
          rated: true, id: result.id, stars: result.stars, score: starsToScore(result.stars),
          comment: this.data.selectedRatingComment || null, status: result.status,
          moderationReason: result.moderationReason, canEdit: result.canEdit,
          createdAt: null, updatedAt: null
        },
        selectedRatingStatusText: ratingStatusText(result.status)
      });
      await this.refreshRatings();
      const comments = await fetchPlayerComments(this.data.fixture.id, playerId, 0, 10).catch(() => null);
      this.setData({ selectedRatingComments: comments?.comments ?? [] });
      wx.showToast({ title: result.status === "VISIBLE" ? "评分已发布" : "评价已提交审核", icon: "none" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ ratingSubmitting: false });
    }
  },

  reportComment(event: WechatMiniprogram.BaseEvent) {
    if (!getSession()) {
      wx.showToast({ title: "请先登录后举报", icon: "none" });
      return;
    }
    const ratingId = Number(event.currentTarget.dataset.id);
    if (!ratingId) return;
    wx.showModal({
      title: "举报评价",
      editable: true,
      placeholderText: "请输入举报原因",
      success: async (result) => {
        if (!result.confirm || !result.content?.trim()) return;
        try {
          await reportRating(ratingId, result.content);
          wx.showToast({ title: "举报已提交", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  async openRefereeRatingDialog() {
    if (!this.data.fixture?.referee) {
      wx.showToast({ title: "本场比赛尚未填写当值主裁", icon: "none" });
      return;
    }
    this.setData({ refereeDialogVisible: true, refereeOwnRating: null, refereeStars: 0 });
    if (getSession() && this.data.fixture) {
      const mine = await fetchMyRefereeRating(this.data.fixture.id).catch(() => null);
      this.setData({ refereeOwnRating: mine?.rated ? mine : null, refereeStars: mine?.stars ?? 0 });
    }
  },

  closeRefereeRatingDialog() {
    if (!this.data.refereeSubmitting) this.setData({ refereeDialogVisible: false });
  },

  handleRefereeStars(event: WechatMiniprogram.CustomEvent<{ stars: number }>) {
    this.setData({ refereeStars: event.detail.stars });
  },

  async saveRefereeRating() {
    if (!getSession()) {
      wx.showToast({ title: "请先登录后评分", icon: "none" });
      return;
    }
    if (!this.data.fixture || this.data.refereeStars < 1) {
      wx.showToast({ title: "请选择 1–5 星", icon: "none" });
      return;
    }
    this.setData({ refereeSubmitting: true });
    try {
      await submitRefereeRating(this.data.fixture.id, this.data.refereeStars);
      const rating = await fetchRefereeRating(this.data.fixture.id).catch(() => null);
      this.setData({
        refereeRating: rating,
        refereeStarsText: starLabel((rating?.averageScore ?? 0) / 2),
        refereeDialogVisible: false
      });
      wx.showToast({ title: "主裁评分已提交", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ refereeSubmitting: false });
    }
  },

  async refreshRatings() {
    if (!this.data.fixture) return;
    const summaries = await fetchFixtureRatings(this.data.fixture.id);
    const ratingViews = summaries.map((rating) => ({ ...rating, starsText: starLabel(rating.averageScore / 2) }));
    this.setData({
      homeRatings: ratingViews.filter((rating) => rating.teamId === this.data.fixture?.homeTeam.id),
      awayRatings: ratingViews.filter((rating) => rating.teamId === this.data.fixture?.awayTeam.id),
      selectedRating: ratingViews.find((rating) => rating.playerId === this.data.selectedPlayerId) ?? null
    });
  },

  retry() {
    void this.loadFixture(this.data.fixtureId);
  }
});

function buildAppearances(
  fixture: FixtureDetail,
  teamId: number,
  playerName: (id: number | null) => string
): AppearanceView[] {
  return fixture.appearances
    .filter((appearance) => appearance.teamId === teamId)
    .map((appearance) => ({
      id: appearance.id,
      playerName: playerName(appearance.playerId),
      jerseyText: "",
      starterText: appearance.starter ? "首发" : "替补",
      minutesText: appearance.minutesPlayed === null ? "分钟未录" : `${appearance.minutesPlayed} 分钟`
    }));
}
