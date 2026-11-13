import { appointmentApi, getToken, type Appointment, type AppointmentProgress } from '@/api/client'
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
type LegacyAppointmentProgress = {
  appointmentId: number
  registered: boolean
  patientId: number | null
  paymentStatus: 'NOT_STARTED' | 'REVIEW_REQUIRED' | 'REVERSED' | 'PAID' | 'PARTIAL' | 'UNPAID'
  saleId: number | null
  receiptNo: string | null
  paid: number
  outstanding: number
  courseStatus: 'NOT_STARTED' | 'USED' | 'REVERSED'
  courseId: number | null
  includesCourse: boolean
  canManage: boolean
  canReview: boolean
}
function adaptLegacyAppointmentProgress(value: unknown, id: number, appointment?: Appointment): AppointmentProgress {
  const legacy = value as Partial<LegacyAppointmentProgress> | null
  const validId = (candidate: unknown) => candidate === null || (Number.isSafeInteger(candidate) && Number(candidate) > 0)
  const validLegacy = Boolean(
    legacy && typeof legacy === 'object' && legacy.appointmentId === id &&
    typeof legacy.registered === 'boolean' && validId(legacy.patientId) &&
    ['NOT_STARTED', 'REVIEW_REQUIRED', 'REVERSED', 'PAID', 'PARTIAL', 'UNPAID'].includes(String(legacy.paymentStatus)) &&
    validId(legacy.saleId) && (legacy.receiptNo === null || typeof legacy.receiptNo === 'string') &&
    typeof legacy.paid === 'number' && Number.isFinite(legacy.paid) && legacy.paid >= 0 &&
    typeof legacy.outstanding === 'number' && Number.isFinite(legacy.outstanding) && legacy.outstanding >= 0 &&
    ['NOT_STARTED', 'USED', 'REVERSED'].includes(String(legacy.courseStatus)) && validId(legacy.courseId) &&
    typeof legacy.includesCourse === 'boolean' && typeof legacy.canManage === 'boolean' && typeof legacy.canReview === 'boolean',
  )
  if (!validLegacy || !legacy || !appointment)
    throw new Error('ข้อมูลผลบริการไม่ตรงกับรูปแบบที่รองรับ กรุณาตรวจสอบรุ่น backend')

  const patientMismatch = legacy.patientId !== null && appointment.patient?.id !== legacy.patientId
  const hasEvidence = Boolean(
    legacy.saleId !== null || legacy.paymentStatus !== 'NOT_STARTED' ||
    legacy.courseId !== null || legacy.courseStatus !== 'NOT_STARTED',
  )
  const needsReview = patientMismatch || !legacy.registered ||
    legacy.paymentStatus === 'REVIEW_REQUIRED' || legacy.paymentStatus === 'REVERSED' || legacy.courseStatus === 'REVERSED'
  const state: AppointmentProgress['state'] = needsReview
    ? 'REVIEW_REQUIRED'
    : hasEvidence
      ? 'FOUND'
      : 'NO_LINKED_RECORD'
  const reason = needsReview
    ? patientMismatch
      ? 'รหัสผู้ป่วยในหลักฐานเดิมไม่ตรงกับนัดหมาย ต้องให้ Manager ตรวจสอบ'
      : !legacy.registered
        ? 'ผู้ป่วยยังไม่มี HN กรุณาลงทะเบียนก่อนทำรายการ'
        : 'พบรายการที่ถูกย้อนคืนหรือทำเครื่องหมายให้ตรวจสอบ กรุณาให้ Manager ตรวจสอบก่อนทำรายการ'
    : hasEvidence
      ? `พบรายการเดิมจากระบบ: การชำระเงิน ${legacy.paymentStatus} (ชำระ ${legacy.paid} บาท, ค้าง ${legacy.outstanding} บาท), สิทธิ์คอร์ส ${legacy.courseStatus}${legacy.receiptNo ? `, ใบเสร็จ ${legacy.receiptNo}` : ''}`
      : null

  return {
    appointmentId: id,
    appointmentStatus: appointment.status,
    patientType: appointment.patientType ?? (appointment.patient ? 'CURRENT' : 'NEW'),
    state,
    reason,
    visitId: null,
    servedPatientId: legacy.patientId ?? null,
    completedAt: null,
    sales: [],
    usages: [],
  }
}
export function normalizeAppointmentProgress(value: unknown, id: number, appointment?: Appointment): AppointmentProgress {
  try {
    return validateAppointmentProgress(value as AppointmentProgress, id)
  } catch {
    return adaptLegacyAppointmentProgress(value, id, appointment)
  }
}
export function readAppointmentProgress(id: number, appointment?: Appointment): Promise<AppointmentProgress> {
  const key = String(getToken()) + ':' + id
  const existing = pending.get(key)
  if (existing) return existing
  const request = appointmentApi
    .progress(id)
    .then((result) => normalizeAppointmentProgress(result, id, appointment))
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
