import type { Session, UserRole } from "./domain";

export interface ApiErrorBody {
  code: string;
  message: string;
  requestId: string;
}

export interface AuthResponse extends Session {
}

export interface MeResponse {
  userId: number;
  nickname: string | null;
  role: UserRole;
  managedTeamIds: number[];
}

export interface IdResponse {
  id: number;
}

export interface LoginRequest {
  code: string;
}
