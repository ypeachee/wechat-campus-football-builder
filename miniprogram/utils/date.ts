export function formatMatchTime(value: string | null | undefined): string {
  if (!value) {
    return "时间待定";
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) {
    return value;
  }
  return `${Number(match[2])}月${Number(match[3])}日 ${match[4]}:${match[5]}`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "未设置";
  }
  return value.slice(0, 10);
}
