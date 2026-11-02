import { appointmentApi, getToken, type AppointmentProgress } from '@/api/client'
// รวมเฉพาะคำขออ่านที่ซ้อนกัน และไม่เก็บสถานะธุรกรรมหลังคำขอจบ
const pending = new Map<string, Promise<AppointmentProgress>>()
export function validateAppointmentProgress(result: AppointmentProgress, id: number) {
  // ตรวจสัญญา progress ของ backend ก่อนใช้ตัดสินใจทำธุรกรรม
  const nullableId = (value: unknown) =>
    value === null || (Number.isSafeInteger(value) && Number(value) > 0)
  const documentId = (value: unknown) => Number.isSafeInteger(value) && Number(value) > 0
  const validSales = Array.isArray(result?.sales) && result.sales.every((sale) =>
    sale && documentId(sale.saleId) && documentId(sale.receiptId) &&
    typeof sale.receiptNo === 'string' && typeof sale.receiptStatus === 'string' &&
    typeof sale.active === 'boolean',
  )
  const validUsages = Array.isArray(result?.usages) && result.usages.every((usage) =>
    usage && documentId(usage.usageId) && documentId(usage.movementId) &&
    documentId(usage.coursePurchaseId) && typeof usage.status === 'string',
  )
  if (
    !result || result.appointmentId !== id ||
    !['UNCONFIRMED', 'CONFIRMED', 'CANCELLED'].includes(result.appointmentStatus) ||
    !['CURRENT', 'NEW'].includes(result.patientType) ||
    !['FOUND', 'NO_LINKED_RECORD', 'REVIEW_REQUIRED'].includes(result.state) ||
    !(result.reason === null || typeof result.reason === 'string') ||
    !nullableId(result.visitId) || !nullableId(result.servedPatientId) ||
    !(result.completedAt === null || (typeof result.completedAt === 'string' && Number.isFinite(Date.parse(result.completedAt)))) ||
    !validSales || !validUsages
  ) {
    throw new Error('ข้อมูลผลบริการไม่ตรงกับรูปแบบที่รองรับ กรุณาตรวจสอบรุ่น backend')
  }
  return result
}
export function readAppointmentProgress(id: number): Promise<AppointmentProgress> {
  const key = String(getToken()) + ':' + id
  const existing = pending.get(key)
  if (existing) return existing
  const request = appointmentApi
    .progress(id)
    .then((result) => validateAppointmentProgress(result, id))
    .finally(() => {
      if (pending.get(key) === request) pending.delete(key)
    })
  pending.set(key, request)
  return request
}

// แม้รายการถูกย้อนคืนแล้วก็ยังเป็นหลักฐานเดิม ห้ามเริ่มธุรกรรมซ้ำ
export function hasProgressRecord(progress: AppointmentProgress) {
  return progress.state !== 'NO_LINKED_RECORD' || progress.sales.length > 0 || progress.usages.length > 0
}
export const progressStateLabel = (state: AppointmentProgress['state']) => ({
  FOUND: 'พบรายการเดิม',
  NO_LINKED_RECORD: 'ไม่พบหลักฐานเชื่อมรายการ',
  REVIEW_REQUIRED: 'ต้องตรวจสอบรายการเดิม',
})[state]
