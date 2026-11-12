import { clinicToday } from '@/utils/clinicDate'

// What the commission settings pages say about a Scheme Version (its state today and the months it covers).
export type VersionRange = { effectiveFrom: string; effectiveTo: string | null }

export function versionStatus(item: VersionRange) {
  const today = clinicToday()
  if (item.effectiveFrom > today) return { label: 'รอเริ่มใช้', tone: 'pending' }
  if (item.effectiveTo && item.effectiveTo < today) return { label: 'สิ้นสุดแล้ว', tone: 'ended' }
  return { label: 'ใช้อยู่', tone: 'current' }
}

export const thaiMonth = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(new Date(`${value}T12:00:00`))

/** "มีผลสำหรับรอบเดือน…": a version starts on the 1st of a month and ends on the last day of one. */
export function versionCycles(item: VersionRange) {
  if (!item.effectiveTo) return `มีผลตั้งแต่รอบเดือน${thaiMonth(item.effectiveFrom)} เป็นต้นไป`
  return item.effectiveFrom.slice(0, 7) === item.effectiveTo.slice(0, 7)
    ? `มีผลสำหรับรอบเดือน${thaiMonth(item.effectiveFrom)}`
    : `มีผลสำหรับรอบเดือน${thaiMonth(item.effectiveFrom)} – ${thaiMonth(item.effectiveTo)}`
}

/** A date and time in clinic time, for the "last changed" columns; "-" when there is none. */
export const recordedAt = (value: string | null | undefined) =>
  value
    ? new Intl.DateTimeFormat('th-TH', {
        timeZone: 'Asia/Bangkok',
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date(value))
    : '-'

export const baht2 = (value: number) =>
  Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
