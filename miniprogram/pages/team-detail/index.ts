import { getApiBaseUrl } from "../../config/env";
import { ApiError } from "../../services/http";
import { fetchTeam, fetchTeamPlayers } from "../../services/teams";
import type { PlayerSummary, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type TeamDetailData = {
  loading: boolean;
  error: string;
  staleTeam: boolean;
  teamId: number;
  team: TeamSummary | null;
  players: PlayerSummary[];
  qrVisible: boolean;
  qrUrl: string;
  shareImagePath: string;
  sharePreparing: boolean;
};

Page({
  data: {
    loading: true, error: "", staleTeam: false, teamId: 0,
    team: null as TeamSummary | null, players: [] as PlayerSummary[],
    qrVisible: false, qrUrl: "", shareImagePath: "", sharePreparing: false
  } as TeamDetailData,

  onLoad(options: Record<string, string | undefined>) {
    wx.showShareMenu({ menus: ["shareAppMessage", "shareTimeline"] });
    const scene = options.scene ? decodeURIComponent(options.scene) : "";
    const sceneTeamId = scene.startsWith("t=") ? Number(scene.slice(2)) : 0;
    const teamId = Number(options.id ?? sceneTeamId ?? 0);
    this.setData({ teamId, qrUrl: teamId ? `${getApiBaseUrl()}/api/v1/teams/${teamId}/qr` : "" });
    void this.loadTeam(teamId);
  },

  onPullDownRefresh() { void this.loadTeam(this.data.teamId); },

  onShareAppMessage() {
    const team = this.data.team;
    return {
      title: team ? `${team.name}｜CUGB校园足球` : "CUGB校园足球",
      path: `/pages/team-detail/index?id=${this.data.teamId}`,
      imageUrl: this.data.shareImagePath || this.data.qrUrl || undefined
    };
  },

  onShareTimeline() {
    const team = this.data.team;
    return {
      title: team ? `${team.name}｜CUGB校园足球` : "CUGB校园足球",
      query: `id=${this.data.teamId}`,
      imageUrl: this.data.shareImagePath || undefined
    };
  },

  async loadTeam(teamId: number) {
    if (!teamId) { this.setData({ loading: false, error: "球队不存在" }); return; }
    this.setData({ loading: true, error: "", staleTeam: false });
    try {
      const [team, players] = await Promise.all([fetchTeam(teamId), fetchTeamPlayers(teamId)]);
      this.setData({ team, players, sharePreparing: true });
      wx.setNavigationBarTitle({ title: team.name });
      await this.prepareShareCard(team);
    } catch (error) {
      const staleTeam = error instanceof ApiError && error.status === 404;
      this.setData({ staleTeam, error: staleTeam ? "球队数据已更新，请返回列表刷新后重进" : errorMessage(error) });
    } finally {
      this.setData({ loading: false, sharePreparing: false });
      wx.stopPullDownRefresh();
    }
  },

  async prepareShareCard(team: TeamSummary) {
    try {
      const query = wx.createSelectorQuery().in(this);
      const result: any = await new Promise((resolve) => query.select("#shareCanvas").fields({ node: true, size: true }).exec(resolve));
      const canvas = result?.[0]?.node;
      if (!canvas) return;
      const width = 750, height = 600;
      canvas.width = width; canvas.height = height;
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#0B6B4B"; ctx.fillRect(0, 0, width, height);
      ctx.fillStyle = "#B7F34A"; ctx.fillRect(0, 0, width, 18);
      ctx.fillStyle = "#FFFFFF"; ctx.font = "bold 54px sans-serif"; ctx.fillText(team.name, 48, 145);
      ctx.fillStyle = "#D7EEE2"; ctx.font = "28px sans-serif";
      ctx.fillText(team.shortName || "CUGB CAMPUS FOOTBALL", 48, 198);
      ctx.fillStyle = "#FFFFFF"; ctx.font = "25px sans-serif";
      ctx.fillText(`主教练  ${team.coachName || "待定"}`, 48, 310);
      ctx.fillText(`主  场  ${team.homeVenue || "待定"}`, 48, 358);
      ctx.fillStyle = "#B7F34A"; ctx.font = "bold 24px sans-serif";
      ctx.fillText("CUGB 校园足球", 48, 530);
      const info: any = await new Promise((resolve, reject) => wx.getImageInfo({ src: this.data.qrUrl, success: resolve, fail: reject }));
      const image = canvas.createImage();
      await new Promise((resolve, reject) => {
        image.onload = resolve; image.onerror = reject; image.src = info.path;
      });
      ctx.fillStyle = "#FFFFFF"; ctx.fillRect(450, 238, 252, 252);
      ctx.drawImage(image, 456, 244, 240, 240);
      const exported: any = await new Promise((resolve, reject) => wx.canvasToTempFilePath({
        canvas, x: 0, y: 0, width, height, destWidth: width, destHeight: height, fileType: "png",
        success: resolve, fail: reject
      }, this));
      this.setData({ shareImagePath: exported.tempFilePath });
    } catch {
      this.setData({ shareImagePath: "" });
    }
  },

  showQr() { this.setData({ qrVisible: true }); },
  closeQr() { this.setData({ qrVisible: false }); },
  noop() {},

  saveQr() {
    wx.downloadFile({
      url: this.data.qrUrl,
      success: (download) => {
        if (download.statusCode !== 200) {
          wx.showToast({ title: "二维码下载失败", icon: "none" });
          return;
        }
        wx.saveImageToPhotosAlbum({
          filePath: download.tempFilePath,
          success: () => wx.showToast({ title: "已保存到相册", icon: "success" }),
          fail: () => wx.showToast({ title: "保存失败，请检查相册权限", icon: "none" })
        });
      },
      fail: () => wx.showToast({ title: "二维码下载失败", icon: "none" })
    });
  },

  openPlayer(event: WechatMiniprogram.BaseEvent) {
    const playerId = event.currentTarget.dataset.id;
    if (playerId) wx.navigateTo({ url: `/pages/player-detail/index?id=${playerId}` });
  },

  retry() {
    if (this.data.staleTeam) { wx.switchTab({ url: "/pages/teams/index" }); return; }
    void this.loadTeam(this.data.teamId);
  }
});