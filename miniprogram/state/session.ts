import type { Session, UserRole } from "../types/domain";

const SESSION_KEY = "campus_football_session";

export function getSession(): Session | null {
  const stored = wx.getStorageSync(SESSION_KEY);
  if (!stored || typeof stored !== "object") {
    return null;
  }
  const session = stored as Session;
  if (!session.token || !session.role) {
    return null;
  }
  return {
    ...session,
    managedTeamIds: Array.isArray(session.managedTeamIds) ? session.managedTeamIds : []
  };
}

export function saveSession(session: Session): void {
  wx.setStorageSync(SESSION_KEY, session);
}

export function clearSession(): void {
  wx.removeStorageSync(SESSION_KEY);
}

export function isAdmin(): boolean {
  return getSession()?.role === "ADMIN";
}

export function isManagementAdmin(): boolean {
  const role = getSession()?.role;
  return role === "ADMIN" || role === "TEAM_MANAGER";
}

export function isTeamLeader(): boolean {
  return getSession()?.role === "TEAM_LEADER";
}

export function canManageTeam(teamId: number): boolean {
  const session = getSession();
  if (!session) {
    return false;
  }
  if (session.role === "ADMIN" || session.role === "TEAM_MANAGER") {
    return true;
  }
  return session.role === "TEAM_LEADER" && session.managedTeamIds.includes(teamId);
}

export function requireRole(roles: UserRole[], teamId?: number): boolean {
  const session = getSession();
  if (!session || !roles.includes(session.role)) {
    return false;
  }
  return teamId === undefined ? true : canManageTeam(teamId);
}