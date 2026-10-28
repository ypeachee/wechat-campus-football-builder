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
  jerseyNo?: string | number | null;
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
    errors.status = "领队不能修改球队状态";
  }
  return errors;
}

export function validatePlayerForm(form: PlayerForm): FieldErrors {
  const errors: FieldErrors = {};
  if (!form.name || !form.name.trim()) {
    errors.name = "请输入球员姓名";
  }
  if (form.jerseyNo !== undefined && form.jerseyNo !== null) {
    const normalized = String(form.jerseyNo).trim();
    if (normalized.length > 20) {
      errors.jerseyNo = "球衣号码不能超过 20 个字符";
    }
  }
  return errors;
}

export function hasFieldErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
