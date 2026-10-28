import { request } from "./http";
import type { AnnouncementAudience, AnnouncementPage, AnnouncementView } from "../types/domain";

export function fetchAnnouncements(page = 0, size = 20): Promise<AnnouncementPage> {
  return request<AnnouncementPage>(`/api/v1/admin/announcements?page=${page}&size=${size}`);
}

export function createAnnouncement(title: string, content: string, audience: AnnouncementAudience,
                                  targetTeamId?: number, targetUserId?: number): Promise<AnnouncementView> {
  return request<AnnouncementView>("/api/v1/admin/announcements", {
    method: "POST",
    data: { title, content, audience, targetTeamId, targetUserId }
  });
}

export function fetchAnnouncement(id: number): Promise<AnnouncementView> {
  return request<AnnouncementView>(`/api/v1/admin/announcements/${id}`);
}