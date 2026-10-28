import type { EventType, FixtureStatus, UserRole } from "../types/domain";

const FIXTURE_STATUS_LABELS: Record<FixtureStatus, string> = {
  DRAFT: "草稿",
  SCHEDULED: "未开始",
  LIVE: "进行中",
  FINISHED: "已完赛",
  CANCELLED: "已取消"
};

const EVENT_LABELS: Record<EventType, string> = {
  GOAL: "进球",
  OWN_GOAL: "乌龙球",
  YELLOW_CARD: "黄牌",
  RED_CARD: "红牌"
};

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: "超级管理员",
  TEAM_MANAGER: "授权管理员",
  TEAM_LEADER: "领队",
  VIEWER: "普通用户"
};

export function fixtureStatusLabel(status: FixtureStatus | string): string {
  return FIXTURE_STATUS_LABELS[status as FixtureStatus] ?? String(status);
}

export function eventTypeLabel(type: EventType | string): string {
  return EVENT_LABELS[type as EventType] ?? String(type);
}

export function roleLabel(role: UserRole | string): string {
  return ROLE_LABELS[role as UserRole] ?? String(role);
}

export function fixtureStatusTone(status: FixtureStatus): "default" | "primary" | "success" | "warning" | "danger" {
  switch (status) {
    case "LIVE":
      return "danger";
    case "FINISHED":
      return "primary";
    case "CANCELLED":
      return "warning";
    case "SCHEDULED":
      return "success";
    default:
      return "default";
  }
}

export function eventTypeTone(type: EventType): "default" | "primary" | "success" | "warning" | "danger" {
  if (type === "GOAL") return "success";
  if (type === "OWN_GOAL") return "warning";
  if (type === "RED_CARD") return "danger";
  return "warning";
}
