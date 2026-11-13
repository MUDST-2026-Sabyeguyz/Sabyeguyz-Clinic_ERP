import type { StaffPayment } from '@/api/client'

/** ปิดได้เฉพาะงวด OPEN ที่ข้อมูลครบ และไม่แก้แถวที่จ่ายแล้ว */
export function canClosePaymentBatch(rows: StaffPayment[]): boolean {
  const openRows = rows.filter((row) => row.status === 'OPEN')
  return openRows.length > 0 && openRows.every((row) => row.pendingSessions != null && row.net != null)
}

export function isPaymentDateAllowed(paidDate: string, today: string): boolean {
  // ตรวจวันที่จริงและห้ามระบุวันจ่ายในอนาคต
  if (!/^\d{4}-\d{2}-\d{2}$/.test(paidDate) || !/^\d{4}-\d{2}-\d{2}$/.test(today)) return false
  const [year, month, day] = paidDate.split('-').map(Number)
  const parsed = new Date(Date.UTC(year!, month! - 1, day!))
  const valid =
    parsed.getUTCFullYear() === year &&
    parsed.getUTCMonth() === month! - 1 &&
    parsed.getUTCDate() === day
  return valid && paidDate <= today
}
