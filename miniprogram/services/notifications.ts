import { request } from "./http";
import type { NotificationPage } from "../types/domain";

export function fetchNotifications(page = 0, size = 30): Promise<NotificationPage> {
  return request<NotificationPage>(`/api/v1/notifications?page=${page}&size=${size}`);
}

export function markNotificationRead(id: number): Promise<void> {
  return request<void>(`/api/v1/notifications/${id}/read`, { method: "PUT" });
}

export function markAllNotificationsRead(): Promise<void> {
  return request<void>("/api/v1/notifications/read-all", { method: "PUT" });
}