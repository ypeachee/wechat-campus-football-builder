import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from "../../services/notifications";
import { getSession } from "../../state/session";
import type { AppNotification } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type NotificationView = AppNotification & { typeLabel: string; timeLabel: string };

Page({
  data: {
    loading: true, error: "", loggedIn: false,
    items: [] as NotificationView[], unreadCount: 0, marking: false
  },

  onShow() { void this.load(); },
  onPullDownRefresh() { void this.load(); },

  async load() {
    this.setData({ loading: true, error: "", loggedIn: Boolean(getSession()) });
    if (!getSession()) { this.setData({ loading: false, items: [], unreadCount: 0 }); return; }
    try {
      const page = await fetchNotifications();
      this.setData({
        items: page.items.map((item) => ({
          ...item,
          typeLabel: typeLabel(item.type),
          timeLabel: item.createdAt ? item.createdAt.replace("T", " ").slice(5, 16) : ""
        })),
        unreadCount: page.unreadCount
      });
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },

  async openNotification(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const link = String(event.currentTarget.dataset.link ?? "");
    const item = this.data.items.find((row) => row.id === id);
    if (!item) return;
    if (!item.read) {
      await markNotificationRead(id).catch(() => undefined);
      this.setData({
        items: this.data.items.map((row) => row.id === id ? { ...row, read: true } : row),
        unreadCount: Math.max(0, this.data.unreadCount - 1)
      });
    }
    if (link) wx.navigateTo({ url: link });
  },

  async markAllRead() {
    if (this.data.marking || this.data.unreadCount === 0) return;
    this.setData({ marking: true });
    try {
      await markAllNotificationsRead();
      this.setData({ items: this.data.items.map((item) => ({ ...item, read: true })), unreadCount: 0 });
    } catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
    finally { this.setData({ marking: false }); }
  },

  goLogin() { wx.switchTab({ url: "/pages/profile/index" }); },
  retry() { void this.load(); }
});

function typeLabel(type: AppNotification["type"]): string {
  switch (type) {
    case "FIXTURE_START": return "比赛提醒";
    case "LINEUP_DEADLINE": return "阵容提醒";
    case "SUSPENSION": return "停赛通知";
    default: return "审核通知";
  }
}