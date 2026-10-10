export function plannedDateSummary(days: readonly { date: string }[]): string {
  const dates = days.map(day => day.date).filter(Boolean).sort()
  if (!dates.length) return '日期待定'
  const first = dates[0]!
  const last = dates[dates.length - 1]!
  const range = first === last ? first : `${first} 至 ${last}`
  return dates.length < days.length ? `${range} · 部分待定` : range
}
