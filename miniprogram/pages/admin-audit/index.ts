import { refreshSession } from "../../services/auth";
import { fetchAuditLogs } from "../../services/audit";
import type { AuditLogRow } from "../../types/domain";
import { formatDateTime } from "../../utils/date";
import { errorMessage } from "../../utils/error";

type AuditView = AuditLogRow & { timeLabel: string; expanded: boolean };

Page({
  data: {
    verified: false,
    loading: true,
    error: "",
    logs: [] as AuditView[]
  },

  onLoad() {
    void this.loadLogs();
  },

  onPullDownRefresh() {
    void this.loadLogs();
  },

  async loadLogs() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || session.role !== "ADMIN") {
        this.setData({ verified: false, error: "没有权限查看审计日志" });
        return;
      }
      const logs = await fetchAuditLogs();
      this.setData({
        verified: true,
        logs: logs.map((log) => ({ ...log, timeLabel: formatDateTime(log.createdAt), expanded: false }))
      });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  toggleLog(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const logs = this.data.logs.map((log) => log.id === id ? { ...log, expanded: !log.expanded } : log);
    this.setData({ logs });
  },

  goBack() {
    wx.navigateBack();
  }
});
