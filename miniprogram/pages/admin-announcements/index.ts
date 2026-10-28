import { refreshSession } from "../../services/auth";
import { createAnnouncement, fetchAnnouncements } from "../../services/announcements";
import { fetchAdminTeams } from "../../services/manage";
import type { AnnouncementAudience, AnnouncementView, Session, TeamSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

const AUDIENCE_LABELS: Record<AnnouncementAudience, string> = {
  ALL: "全部用户",
  ADMIN: "管理员",
  TEAM_STAFF: "球队工作人员",
  TEAM: "指定球队",
  USER: "指定用户"
};

Page({
  data: {
    loading: true, submitting: false, error: "", session: null as Session | null,
    isSuperAdmin: false, teams: [] as TeamSummary[], teamIndex: 0,
    audienceOptions: [] as Array<{ value: AnnouncementAudience; label: string }>,
    audienceIndex: 0, title: "", content: "", targetUserId: "",
    announcements: [] as Array<AnnouncementView & { audienceLabel: string; timeLabel: string }>
  },

  onShow() { void this.load(); },
  onPullDownRefresh() { void this.load(); },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER")) {
        this.setData({ session: null, error: "仅管理员可以发布公告" });
        return;
      }
      const isSuperAdmin = session.role === "ADMIN";
      const audiences: AnnouncementAudience[] = isSuperAdmin
        ? ["ALL", "ADMIN", "TEAM_STAFF", "TEAM", "USER"] : ["TEAM"];
      const [teams, page] = await Promise.all([fetchAdminTeams(), fetchAnnouncements()]);
      this.setData({
        session, isSuperAdmin, teams,
        audienceOptions: audiences.map((value) => ({ value, label: AUDIENCE_LABELS[value] })),
        audienceIndex: 0,
        announcements: page.items.map((item) => ({
          ...item,
          audienceLabel: AUDIENCE_LABELS[item.audience],
          timeLabel: item.createdAt ? item.createdAt.replace("T", " ").slice(0, 16) : ""
        }))
      });
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },

  input(event: WechatMiniprogram.Input) {
    this.setData({ [String(event.currentTarget.dataset.field)]: event.detail.value });
  },

  selectAudience(event: WechatMiniprogram.PickerChange) {
    this.setData({ audienceIndex: Number(event.detail.value) });
  },

  selectTeam(event: WechatMiniprogram.PickerChange) {
    this.setData({ teamIndex: Number(event.detail.value) });
  },

  async submit() {
    if (this.data.submitting) return;
    const audience = this.data.audienceOptions[this.data.audienceIndex]?.value;
    const targetTeamId = audience === "TEAM" ? this.data.teams[this.data.teamIndex]?.id : undefined;
    const targetUserId = audience === "USER" ? Number(this.data.targetUserId) : undefined;
    if (!this.data.title.trim() || !this.data.content.trim()) {
      wx.showToast({ title: "请填写标题和正文", icon: "none" });
      return;
    }
    if (audience === "TEAM" && !targetTeamId) {
      wx.showToast({ title: "请选择球队", icon: "none" });
      return;
    }
    if (audience === "USER" && (!Number.isInteger(targetUserId) || Number(targetUserId) <= 0)) {
      wx.showToast({ title: "请输入有效用户 ID", icon: "none" });
      return;
    }
    this.setData({ submitting: true });
    try {
      await createAnnouncement(this.data.title.trim(), this.data.content.trim(),
        audience as AnnouncementAudience, targetTeamId, targetUserId);
      this.setData({ title: "", content: "", targetUserId: "" });
      await this.load();
      wx.showToast({ title: "公告已发送", icon: "success" });
    } catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
    finally { this.setData({ submitting: false }); }
  },

  goBack() { wx.navigateBack(); }
});