import { api } from './http'
import { type Patient } from './patient'
import { type Course } from './catalog'
import { type PaymentLabelCode } from './payment'
import { type TransferRecord } from './report'

// คำขอต่ออายุใช้ workflow แยกจากการแก้วันหมดอายุโดยตรง
export type CourseExtensionPreview = { oldExpiry: string; newExpiry: string }

export type DirectCourseExtension = {
  courseId: number
  requestedByPatientId: number
  value: number
  unit: CourseExtensionRequest['unit']
  reason: string
  ownerRequestConfirmed: boolean
}

export type CourseExtensionRequest = {
  id: number
  courseId: number
  requestedPatientId: number
  value: number
  unit: 'Day' | 'Month' | 'Year'
  reason: string | null
  status: 'Pending' | 'Approved' | 'Rejected'
  requestedAt: string
  decidedAt: string | null
  decisionNote: string | null
  oldExpiry: string | null
  newExpiry: string | null
}

export type CoursePurchase = {
  id: number
  receiptId?: number | null
  patient: Patient
  purchaser?: { id: number; hn: string; fullName: string }
  branchCode: string
  course: { id: number; code: string; name: string }
  price: number
  sessions: number
  bonusSessions: number
  purchased: number
  used: number
  remaining: number
  purchasedAt: string
  expiresAt: string
  expired: boolean
  usable: boolean
  status: string
}

export type SingleVisitEntitlement = {
  id: number
  saleTransactionId: number
  patientId: number
  branchCode: string
  serviceId: number
  serviceName: string
  purchased: number
  used: number
  remaining: number
  serviceActive: boolean
  purchasedAt: string
  receiptNo: string
}

export type SingleVisitEntitlementUse = {
  id: number
  entitlementId: number
  serviceName: string
  receiptNo: string
  appointmentId: number
  visitId: number
  usedAt: string
  treatingStaffName: string | null
  reversedAt: string | null
  reversalReason: string | null
  /** เหตุผลที่ย้อนไม่ได้ (เช่น ค่ามือถูกจ่ายแล้ว) หรือ null ถ้าย้อนได้ */
  reverseBlockedReason: string | null
}

export type CourseUseRequest = {
  coursePurchaseId: number
  appointmentId: number
  patientId: number
  treatingStaffId: number
  note?: string
  treatmentId?: number
}

export type CourseUsageTransaction = {
  id: number | null
  usageId?: number
  therapistName?: string
  coursePurchaseId: number
  patientName: string
  patientHn: string
  courseName: string
  branchCode: string
  usedAt: string
  voidedAt?: string | null
  voidReason?: string | null
}

export type CourseUsagePrintDocument = {
  id: number
  courseId: number
  usageId: number
  version: number
  issuedAt: string
  issuedBy: string
  documentSnapshot: string
  events: Array<{ id: number; type: 'Issued' | 'Reprint'; printedAt: string; printedBy: string }>
}

export const courseApi = {
  usagePrints: (courseId: number) =>
    api.get<CourseUsagePrintDocument[]>(`/api/course-purchases/${courseId}/usage-prints`),
  usagePrint: (printId: number) =>
    api.get<CourseUsagePrintDocument>(`/api/course-usage-prints/${printId}`),
  reprintUsage: (printId: number) =>
    api.post<CourseUsagePrintDocument>(`/api/course-usage-prints/${printId}/reprints`, {}),
  reviseUsagePrint: (usageId: number) =>
    api.post<CourseUsagePrintDocument>(`/api/course-usages/${usageId}/print-revisions`, {}),
  // ขอรายการต่ออายุหรือกรองตามคอร์ส/สถานะ
  extensions: (params: { courseId?: number; status?: CourseExtensionRequest['status'] } = {}) => {
    const query = new URLSearchParams()
    if (params.courseId) query.set('courseId', String(params.courseId))
    if (params.status) query.set('status', params.status)
    return api.get<CourseExtensionRequest[]>(
      `/api/course-extensions${query.size ? `?${query.toString()}` : ''}`,
    )
  },
  // ส่งคำขอให้ Manager พิจารณาก่อนปรับวันหมดอายุ
  requestExtension: (body: {
    courseId: number
    value: number
    unit: CourseExtensionRequest['unit']
    reason?: string
    requestedByPatientId: number
  }) => api.post<CourseExtensionRequest>('/api/course-extensions', body),
  previewDirectExtension: (body: DirectCourseExtension) =>
    api.post<CourseExtensionPreview>('/api/course-extensions/direct/preview', body),
  extendDirect: (body: DirectCourseExtension & CourseExtensionPreview) =>
    api.post<CourseExtensionPreview>('/api/course-extensions/direct', body),
  approveExtension: (id: number, note?: string) =>
    api.post<CourseExtensionRequest>(`/api/course-extensions/${id}/approve`, { note }),
  rejectExtension: (id: number, note?: string) =>
    api.post<CourseExtensionRequest>(`/api/course-extensions/${id}/reject`, { note }),
  usageTransactions: () => api.get<CourseUsageTransaction[]>('/api/course-usages'),
  list: () => api.get<Array<Course & { active: boolean }>>('/api/courses'),
  get: (id: number) => api.get<Course>(`/api/courses/${id}`),
  create: (body: Omit<Course, 'id' | 'active'>) => api.post<Course>('/api/courses', body),
  update: (id: number, body: Omit<Course, 'id' | 'active'>) =>
    api.put<Course>(`/api/courses/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/courses/${id}`),
  patientCourses: (patientId: number) =>
    api.get<CoursePurchase[]>(`/api/patients/${patientId}/courses`),
  purchase: (body: { patientId: number; courseId: number; branchCode: string; caseOwnerStaffId: number; paymentLabelCode: PaymentLabelCode; discountType?: string; discountValue?: number }) =>
    api.post<CoursePurchase>('/api/course-purchases', body),
  ledger: (id: number) =>
    api.get<{
      purchase: CoursePurchase & {
        commissionStatus?: string
        lockedCommissionRate?: number | null
        totalCourseCommissionPool?: number | null
        commissionAllocationPerVisit?: number | null
      }
      movements: unknown[]
      allocations: Array<{
        visitDate: string
        gross: number | null
        fee: number | null
        ownerNet: number | null
      }>
    }>(`/api/course-purchases/${id}`),
  use: (body: CourseUseRequest) => api.post<CoursePurchase>('/api/course-usages', body),
  useBatch: (body: { usages: CourseUseRequest[] }) => api.post<CoursePurchase[]>('/api/course-usages/batch', body),
  reverse: (movementId: number, reason: string) =>
    api.post<CoursePurchase>(`/api/course-movements/${movementId}/reverse`, {
      reason,
    }),
  cancel: (id: number, reason: string) =>
    api.post<CoursePurchase>(`/api/course-purchases/${id}/cancel`, { reason }),
  transfer: (body: {
    sourcePurchaseId: number
    destinationPatientId: number
    quantity: number
    reason: string
  }) => api.post<TransferRecord>('/api/course-transfers', body),
  transferHistory: (patientId?: number) =>
    api.get<TransferRecord[]>(`/api/course-transfers${patientId ? `?patientId=${patientId}` : ''}`),
}

export const singleVisitEntitlementApi = {
  list: (patientId: number) =>
    api.get<SingleVisitEntitlement[]>(`/api/patients/${patientId}/single-visit-entitlements`),
  uses: (patientId: number) =>
    api.get<SingleVisitEntitlementUse[]>(`/api/patients/${patientId}/single-visit-entitlement-uses`),
  use: (entitlementId: number, body: { appointmentId: number; treatingStaffId: number }) =>
    api.post<{ id: number; entitlementId: number; appointmentId: number; visitId: number; usedAt: string }>(
      `/api/single-visit-entitlements/${entitlementId}/uses`, body),
  reverse: (usageId: number, reason: string) =>
    api.post(`/api/single-visit-entitlement-uses/${usageId}/reverse`, { reason }),
}
