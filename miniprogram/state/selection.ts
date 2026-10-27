export interface SelectionState {
  competitionId: number;
  seasonId: number;
  stageId: number;
  groupId: number;
}

const KEY = "campus_football_selection";

const EMPTY: SelectionState = { competitionId: 0, seasonId: 0, stageId: 0, groupId: 0 };

export function getSelection(): SelectionState {
  const stored = wx.getStorageSync(KEY);
  return stored && typeof stored === "object" ? { ...EMPTY, ...stored } : { ...EMPTY };
}

export function saveSelection(selection: SelectionState): void {
  wx.setStorageSync(KEY, selection);
}

export function selectCompetition(competitionId: number): SelectionState {
  const next = { ...EMPTY, competitionId };
  saveSelection(next);
  return next;
}

export function selectSeason(selection: SelectionState, seasonId: number): SelectionState {
  const next = { ...selection, seasonId, stageId: 0, groupId: 0 };
  saveSelection(next);
  return next;
}

export function selectStage(selection: SelectionState, stageId: number): SelectionState {
  const next = { ...selection, stageId, groupId: 0 };
  saveSelection(next);
  return next;
}

export function selectGroup(selection: SelectionState, groupId: number): SelectionState {
  const next = { ...selection, groupId };
  saveSelection(next);
  return next;
}


export function resolveSelectedId(items: Array<{ id: number }>, selectedId: number): number {
  return items.some((item) => item.id === selectedId) ? selectedId : (items[0]?.id ?? 0);
}
