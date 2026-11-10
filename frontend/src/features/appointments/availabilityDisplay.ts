import type { AppointmentAvailability } from '@/api/client'

export function displayAvailability(result: AppointmentAvailability, fallbackDuration: number) {
  const duration = Math.max(1, result.durationMinutes || fallbackDuration || 30)
  if (result.slots.length) {
    let nextStart = -1
    const slots = [...result.slots]
      .sort((a, b) => a.time.localeCompare(b.time))
      .filter((slot) => {
        const [hours, minutes] = slot.time.split(':').map(Number)
        const start = hours! * 60 + minutes!
        if (start < nextStart) return false
        nextStart = start + duration
        return true
      })
    return { slots, message: '' }
  }
  const message = !result.windowConfigured
    ? 'สาขายังไม่ได้ตั้งเวลาเปิด–ปิดรับนัด กรุณาตั้งค่าเวลาทำการก่อน ช่วงเวลาที่แสดงยังจองไม่ได้'
    : !result.durationMinutes
      ? 'ยังไม่ได้กำหนดระยะเวลานัด ช่วงเวลาที่แสดงยังจองไม่ได้'
      : 'ไม่มีช่วงเวลาที่รองรับระยะเวลาบริการในวันและเวลาทำการนี้'
  const parse = (value: string | null | undefined, fallback: number) => {
    if (!value || !/^\d{2}:\d{2}/.test(value)) return fallback
    const [hours, minutes] = value.split(':').map(Number)
    return hours! * 60 + minutes!
  }
  const open = parse(result.openTime, 9 * 60)
  const close = parse(result.closeTime, 18 * 60)
  const slots: AppointmentAvailability['slots'] = []
  for (let value = open; value + duration <= close; value += duration) {
    slots.push({
      time: `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`,
      available: false,
      reason: message,
    })
  }
  return { slots, message }
}
