import { clinicToday } from '@/utils/clinicDate'

// สรุปสถานะ Version และรอบเดือนที่มีผล
export type VersionRange = { effectiveFrom: string; effectiveTo: string | null }

export function versionStatus(item: VersionRange) {
  const today = clinicToday()
  if (item.effectiveFrom > today) return { label: 'รอเริ่มใช้', tone: 'pending' }
  if (item.effectiveTo && item.effectiveTo < today) return { label: 'สิ้นสุดแล้ว', tone: 'ended' }
  return { label: 'ใช้อยู่', tone: 'current' }
}

export const thaiMonth = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(new Date(`${value}T12:00:00`))

/** หา date เริ่ม Version ใหม่ต่อจากวันสิ้นสุดเดิมหรือเดือนถัดไป */
export function nextSchemeEffectiveFrom(effectiveTo: string | null, today = clinicToday()) {
  if (effectiveTo) {
    const [year, month, day] = effectiveTo.split('-').map(Number)
    return new Date(Date.UTC(year!, month! - 1, day! + 1)).toISOString().slice(0, 10)
  }
  const [year, month] = today.slice(0, 7).split('-').map(Number)
  return new Date(Date.UTC(year!, month!, 1)).toISOString().slice(0, 10)
}

/** แปลงช่วงวันที่ของ Version เป็นข้อความรอบเดือน */
export function versionCycles(item: VersionRange) {
  if (!item.effectiveTo) return `มีผลตั้งแต่รอบเดือน${thaiMonth(item.effectiveFrom)} เป็นต้นไป`
  return item.effectiveFrom.slice(0, 7) === item.effectiveTo.slice(0, 7)
    ? `มีผลสำหรับรอบเดือน${thaiMonth(item.effectiveFrom)}`
    : `มีผลสำหรับรอบเดือน${thaiMonth(item.effectiveFrom)} – ${thaiMonth(item.effectiveTo)}`
}

/** แสดงวันเวลาที่แก้ไขตามเวลาไทย หรือขีดเมื่อไม่มีข้อมูล */
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
