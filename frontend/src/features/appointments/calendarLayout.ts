import type { Appointment, AppointmentStatus } from '@/api/client'

export const dateValue = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
export function addDays(value: string, days: number) {
  const date = new Date(`${value}T12:00:00`)
  date.setDate(date.getDate() + days)
  return dateValue(date)
}
export function weekDates(value: string) {
  const day = new Date(`${value}T12:00:00`).getDay()
  const monday = addDays(value, day === 0 ? -6 : 1 - day)
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index))
}
export function bangkokDate(value: string) {
  // คำนวณวันตามเวลาคลินิก ไม่ใช้ timezone ของเครื่องผู้ใช้
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Bangkok',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}
export function minuteOf(value: string) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
    .format(new Date(value))
    .split(':')
    .map(Number)
  return parts[0]! * 60 + parts[1]!
}
export const clock = (minute: number) =>
  `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
const palette = [
  ['#e2f3ee', '#13594d'],
  ['#e6edff', '#263e87'],
  ['#f6e6fa', '#763280'],
  ['#fff0cf', '#755012'],
  ['#fce6df', '#843a24'],
  ['#e2f2fa', '#185472'],
  ['#efe9fc', '#574187'],
  ['#edf2d9', '#43551b'],
] as const
export function therapistColor(id: number) {
  const pair = palette[Math.abs(id) % palette.length]!
  return { background: pair[0], color: pair[1] }
}
// ใช้สถานะสามค่าที่ฐานข้อมูลอนุญาตให้บันทึก
export const statusText: Record<AppointmentStatus, string> = {
  UNCONFIRMED: 'ยังไม่ยืนยัน',
  CONFIRMED: 'ยืนยันแล้ว',
  CANCELLED: 'ยกเลิก',
}
export function activeAppointments(items: Appointment[], branch: string) {
  return items.filter((item) => item.branchCode === branch && item.status !== 'CANCELLED')
}
type Interval = { id: number; start: number; end: number }
export function layoutEvents<T extends Interval>(
  items: T[],
): Array<T & { lane: number; lanes: number }> {
  // จัดนัดที่เวลาทับกันให้อยู่คนละ lane เพื่อให้แสดงในปฏิทินพร้อมกัน
  const sorted = items.slice().sort((a, b) => a.start - b.start || b.end - a.end || a.id - b.id)
  const result: Array<T & { lane: number; lanes: number }> = []
  let group: Array<T & { lane: number; lanes: number }> = [],
    ends: number[] = [],
    groupEnd = -1
  function flush() {
    for (const row of group) row.lanes = ends.length
    group = []
    ends = []
  }
  for (const item of sorted) {
    if (item.start >= groupEnd) flush()
    let lane = ends.findIndex((end) => end <= item.start)
    if (lane < 0) lane = ends.length
    ends[lane] = item.end
    const row = { ...item, lane, lanes: 1 }
    group.push(row)
    result.push(row)
    groupEnd = Math.max(group.length === 1 ? 0 : groupEnd, item.end)
  }
  flush()
  return result
}
