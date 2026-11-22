const clinicParts = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Bangkok',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** แปลงเวลาที่บันทึกและตรวจช่วงนัดโดยยึดเขตเวลา Asia/Bangkok */
export function appointmentLocalFields(instant: string) {
  const parts = Object.fromEntries(
    clinicParts.formatToParts(new Date(instant)).map(({ type, value }) => [type, value]),
  )
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    time: `${parts.hour}:${parts.minute}`,
  }
}

function localInstant(date: string, time: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) {
    throw new Error('กรุณาระบุวันและเวลาเริ่มกับจบนัดให้ครบถ้วน')
  }
  const instant = `${date}T${time}:00+07:00`
  const parsed = Date.parse(instant)
  if (!Number.isFinite(parsed) || appointmentLocalFields(instant).date !== date) {
    throw new Error('วันที่หรือเวลานัดไม่ถูกต้อง')
  }
  return instant
}

export function appointmentInterval(date: string, time: string, endDate: string, endTime: string) {
  const startAt = localInstant(date, time)
  const endAt = localInstant(endDate, endTime)
  if (Date.parse(endAt) <= Date.parse(startAt)) {
    throw new Error('เวลาจบนัดต้องอยู่หลังเวลาเริ่มนัด')
  }
  return { startAt, endAt }
}

/** คำนวณเวลาจบเริ่มต้นจากระยะเวลาบริการ โดยเก็บวันและเวลาตามเขตกรุงเทพฯ */
export function appointmentEndFields(date: string, time: string, durationMinutes: number) {
  if (!Number.isSafeInteger(durationMinutes) || durationMinutes <= 0) {
    throw new Error('ระยะเวลาบริการไม่ถูกต้อง')
  }
  const startAt = localInstant(date, time)
  const endAt = new Date(Date.parse(startAt) + durationMinutes * 60_000).toISOString()
  return appointmentLocalFields(endAt)
}
