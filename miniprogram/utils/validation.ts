import type { PlayerStatus, TeamStatus } from "../types/domain";

export type FieldErrors = Record<string, string>;

export type TeamForm = {
  name: string;
  shortName?: string;
  logoUrl?: string;
  coachName?: string;
  homeVenue?: string;
  status?: TeamStatus;
};

export type PlayerForm = {
  name: string;
  jerseyNo?: number | null;
  position?: string;
  birthDate?: string | null;
  photoUrl?: string | null;
  status?: PlayerStatus;
};

export function validateTeamForm(form: TeamForm, isAdmin: boolean): FieldErrors {
  const errors: FieldErrors = {};
  if (!form.name || !form.name.trim()) {
    errors.name = "请输入球队名称";
  }
  if (!isAdmin && form.status === "INACTIVE") {
    errors.status = "球队管理员不能修改球队状态";
  }
  return errors;
}

export function validatePlayerForm(form: PlayerForm): FieldErrors {
  const errors: FieldErrors = {};
  if (!form.name || !form.name.trim()) {
    errors.name = "请输入球员姓名";
  }
  if (form.jerseyNo !== undefined && form.jerseyNo !== null) {
    if (!Number.isInteger(form.jerseyNo) || form.jerseyNo < 0 || form.jerseyNo > 999) {
      errors.jerseyNo = "球衣号码必须是 0 到 999 的整数";
    }
  }
  return errors;
}

export function hasFieldErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
