import type { CourseExtensionRequest } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

const bangkokDate = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: 'Asia/Bangkok',
})

export function estimateCourseExpiry(expiresAt: string, duration: number, unit: CourseExtensionRequest['unit']) {
  const parsed = new Date(expiresAt)
  if (!Number.isFinite(parsed.getTime())) return undefined
  const expiry = expiresAt.length === 10 ? expiresAt : bangkokDate.format(parsed)
  const today = clinicToday()
  const date = new Date(`${expiry < today ? today : expiry}T00:00:00Z`)
  if (unit === 'Day') {
    date.setUTCDate(date.getUTCDate() + duration)
  } else {
    const day = date.getUTCDate()
    date.setUTCDate(1)
    date.setUTCMonth(date.getUTCMonth() + duration * (unit === 'Year' ? 12 : 1))
    const monthEnd = new Date(date)
    monthEnd.setUTCMonth(monthEnd.getUTCMonth() + 1, 0)
    date.setUTCDate(Math.min(day, monthEnd.getUTCDate()))
  }
  return Number.isFinite(date.getTime()) && date.getUTCFullYear() <= 9999
    ? date.toISOString().slice(0, 10)
    : undefined
}
