import { api } from './http'

export type CommissionFormula = {
  asOf: string
  scheme: {
    id: number
    name: string
    version: number
    effectiveFrom: string
    effectiveTo: string | null
  } | null
  tiers: Array<{
    id: number
    order: number
    minimum: number
    maximum: number | null
    rate: number
    active: boolean
  }>
  treatmentFee: {
    id: number
    feeType: 'Fixed' | 'Percentage'
    feeValue: number
    effectiveFrom: string
    effectiveTo: string | null
    enabled: boolean
    used: boolean
  } | null
}

export type TherapistLine = {
  kind: 'COMMISSION' | 'FEE'
  id: number
  date: string
  patientName: string
  patientHn: string | null
  item: string
  role: string
  amount: number | null
  reversal: boolean
  pending: boolean
  paymentStatus: 'OPEN' | 'CLOSED' | 'PAID' | null
  courseNo: string | null
  totalVisits: number | null
  grossAmount: number | null
  lineDiscount: number | null
  base: number | null
  rate: number | null
  tier: string | null
  pool: number | null
  saleNo: string | null
  saleDate: string | null
  saleType: string | null
  feeType: 'Fixed' | 'Percentage' | null
  feeValue: number | null
  feeBase: number | null
  ownerName: string | null
  treatingName: string | null
  visitNo: string | null
  singleVisit: boolean
}

export type TherapistLines = {
  month: string
  courseCommission: number | null
  singleVisitFee: number
  substituteFee: number
  pendingSessions: number
  lines: TherapistLine[]
}

export type TherapistAdjustment = {
  id: number
  originalMonth: string
  appliedMonth: string
  amount: number
  reason: string
  status: string
  processed: number
  remaining: number
  reference: string | null
  approvedBy: string | null
  applications: Array<{
    id: number
    month: string | null
    amount: number
    status: string
    approvedBy: string | null
    approvalReference: string | null
  }>
}

export type ManagerAdjustment = {
  id: number
  staffId: number
  branchCode?: string | null
  approverName?: string | null
  staffName: string
  originalMonth: string
  appliedMonth: string
  amount: number
  reason: string
  status: string
  processed: number
  remaining: number
  automatic?: boolean
  cancellationReason?: string | null
  cancelledAt?: string | null
  allocationId: number | null
  singleVisitFeeId: number | null
  courseId: number | null
  visitId: number | null
  saleTransactionId: number | null
  referenceLabel?: string | null
  approvedBy?: number | null
  approvedAt?: string | null
  applications: Array<{
    id: number
    adjustmentId: number
    month?: string
    amount: number
    status: string
    approved: boolean
    approvedBy?: number | null
    approvedAt?: string | null
    approvalReference: string | null
  }>
}

// null หมายถึง API ยังไม่มีตัวเลข ไม่ใช่ยอดศูนย์
export type ClosingHistoryEvent = {
  kind: 'close' | 'reopen' | 'pay' | 'finalize'
  title: string
  detail: string | null
  reason: string | null
  at: string | null
  by: string | null
}

export type ClosingHistoryMonth = {
  month: string
  status: 'OPEN' | 'CLOSED' | 'PARTIAL' | 'FINALIZED'
  reopenCount: number
  closedBy: string | null
  closedAt: string | null
  lastPaidDate: string | null
  paidTotal: number
  events: ClosingHistoryEvent[]
}

export type StaffPayment = {
  id: number
  staffId: number
  staffName: string
  month: string
  status: 'OPEN' | 'CLOSED' | 'PAID'
  courseCommission: number | null
  singleVisitFee: number | null
  substituteFee: number | null
  adjustment: number | null
  net: number | null
  paidAt: string | null
  paidDate?: string | null
  paymentReference?: string | null
  closingId: number | null
  pendingSessions: number | null
  applications: Array<{
    id: number
    adjustmentId: number
    amount: number
    status: string
    approved: boolean
    approvalReference: string | null
  }>
}

export type TreatmentFeeRule = {
  id: number
  name?: string | null
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo: string | null
  enabled: boolean
  used: boolean
  lastEditedBy?: string | null
  lastEditedAt?: string | null
}

export type TreatmentFeeInput = {
  name?: string
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo?: string | null
  branchCode: string
}

export type CommissionPreviewParams = {
  courseId: number
  branchCode: string
  sellerStaffId?: number
  price?: number
  discountType?: string
  discountValue?: number
}

export type CommissionPreview = {
  netPrice: number
  commissionRate: number
  commissionPool: number
  commissionPerVisit: number
  totalVisits: number
  schemeVersion: number
  monthlySalesToDate: number
}

// Endpoint ปิดยอดและจ่ายคอมมิชชันต้องระบุเดือนกับสาขาตาม contract
export const commissionApi = {
  adjustmentSources: (staffId: number, type: string, branchCode: string) =>
    api.get<Array<{ id: number; label: string; month: string }>>(
      `/api/commission/adjustments/sources?${new URLSearchParams({ staffId: String(staffId), type, branchCode })}`,
    ),
  adjustmentAllowedMonths: (branchCode: string) =>
    api.get<string[]>(`/api/commission/adjustments/allowed-months?${new URLSearchParams({ branchCode })}`),
  preview: (params: CommissionPreviewParams) => {
    const query = new URLSearchParams({
      courseId: String(params.courseId),
      branchCode: params.branchCode,
    })
    if (params.sellerStaffId != null) query.set('sellerStaffId', String(params.sellerStaffId))
    if (params.price != null) query.set('price', String(params.price))
    if (params.discountType) query.set('discountType', params.discountType)
    if (params.discountValue != null) query.set('discountValue', String(params.discountValue))
    return api.get<CommissionPreview>(`/api/commission/preview?${query.toString()}`)
  },
  payments: (month: string, branchCode: string) =>
    api.get<StaffPayment[]>(`/api/commission/payments?${new URLSearchParams({ month, branchCode })}`),
  /** ค่ามือของคนที่ยังไม่มีงวดจ่าย (ก่อนปิดรอบ) */
  feePreview: (month: string, branchCode: string) =>
    api.get<
      Array<{ staffId: number; staffName: string; singleVisitFee: number; substituteFee: number }>
    >(`/api/commission/payments/fee-preview?${new URLSearchParams({ month, branchCode })}`),
  schemes: () => api.get('/api/commission/schemes'),
  tiers: (schemeId: number) => api.get(`/api/commission/schemes/${schemeId}/tiers`),
  createScheme: (body: unknown) => api.post('/api/commission/schemes', body),
  createSchemeVersion: (id: number, body: unknown) =>
    api.post(`/api/commission/schemes/${id}/versions`, body),
  updateScheme: (id: number, body: unknown) => api.put(`/api/commission/schemes/${id}`, body),
  activateScheme: (id: number) => api.patch(`/api/commission/schemes/${id}/activate`, {}),
  deactivateScheme: (id: number) => api.delete(`/api/commission/schemes/${id}`),
  formula: () => api.get<CommissionFormula>('/api/commission/formula'),
  report: (from: string, to: string) =>
    api.get<
      Array<{
        staffId: number | null
        staffName: string
        branchCode: string
        branchName: string
        saleCount: number
        base: number
        rate: number
        commission: number
        sales: Array<{
          id: number
          saleDate: string
          receiptNo: string
          courseNo: string
          courseName: string
          patientName: string
          sellerName: string
          gross: number
          lineDiscount: number
          commissionableAmount: number
          completedRefund: number
        }>
      }>
    >(`/api/commission/report?${new URLSearchParams({ from, to })}`),
  treatmentReport: (from: string, to: string) =>
    api.get<
      Array<{
        id: number
        visitDate: string
        coursePurchaseId: number
        courseCode: string
        patientId: number
        patientHn: string
        patientName: string
        branchCode: string
        branchName: string
        treatingStaffId: number | null
        treatingStaffName: string
        ownerStaffId: number | null
        ownerStaffName: string
        gross: number
        treatmentFee: number
        ownerNet: number
        serviceName: string
        feeRuleName: string | null
      }>
    >(`/api/commission/treatment-report?${new URLSearchParams({ from, to })}`),
  singleVisitFees: (staffId: number, branchCode: string, from: string, to: string) =>
    api.get<Array<{
      id: number
      visitDate: string
      patientName: string
      serviceName: string
      treatingStaffName: string
      feeRuleName: string | null
      treatmentFee: number
      /** ใบเสร็จของการขายบริการรายครั้งที่ก่อให้เกิดค่ามือนี้ (null ถ้ามาจากสิทธิ์ที่ซื้อไว้ล่วงหน้า) */
      receiptNo: string | null
    }>>(`/api/commission/single-visit-fees?${new URLSearchParams({ staffId: String(staffId), branchCode, from, to })}`),
  closings: (month: string, branchCode: string) =>
    api.get<
      Array<{
        id: number
        month: string
        employeeId: number
        employeeName: string
        sales: number
        version: string
        rate: number
        status: string
      }>
    >(`/api/commission/closings?${new URLSearchParams({ month, branchCode })}`),
  branchClosing: (month: string, branchCode: string) =>
    api.get<{ id: number | null; branchCode: string; month: string; status: 'OPEN' | 'CLOSED'; closedAt: string | null; closedBy: number | null }>(
      `/api/commission/branch-closings?${new URLSearchParams({ month, branchCode })}`,
    ),
  reopenEmptyBranch: (month: string, branchCode: string, reason: string) =>
    api.post(`/api/commission/branch-closings/${month}/reopen?${new URLSearchParams({ branchCode })}`, { reason }),
  closeMonth: (month: string, branchCode: string) =>
    api.post(`/api/commission/closings/${month}?${new URLSearchParams({ branchCode })}`, {}),
  preparePayments: (month: string, branchCode: string) =>
    api.post<StaffPayment[]>(`/api/commission/payments/prepare?${new URLSearchParams({ month, branchCode })}`, {}),
  closePayments: (month: string, branchCode: string) =>
    api.post<StaffPayment[]>(`/api/commission/payments/close?${new URLSearchParams({ month, branchCode })}`, {}),
  reopenPayment: (id: number, reason?: string) =>
    api.post<StaffPayment>(`/api/commission/payments/${id}/reopen`, reason ? { reason } : {}),
  reopenAllBranch: (month: string, branchCode: string, reason: string) =>
    api.post<{ id: number | null; branchCode: string; month: string; status: string }>(
      `/api/commission/branch-closings/${month}/reopen-all?${new URLSearchParams({ branchCode })}`,
      { reason },
    ),
  closingHistory: (branchCode: string, limit = 12) =>
    api.get<ClosingHistoryMonth[]>(`/api/commission/closing-history?${new URLSearchParams({ branchCode, limit: String(limit) })}`),
  pay: (id: number, paidDate: string, reference?: string) =>
    api.post<StaffPayment>(`/api/commission/payments/${id}/pay`, { paidDate, reference: reference?.trim() || null }),
  reopenClosing: (id: number, reason: string) =>
    api.post(`/api/commission/closings/${id}/reopen`, { reason }),
  treatmentFees: () => api.get('/api/commission/treatment-fees'),
  createTreatmentFee: (body: TreatmentFeeInput) =>
    api.post<TreatmentFeeRule>('/api/commission/treatment-fees', body),
  updateTreatmentFee: (id: number, body: Partial<TreatmentFeeInput>) =>
    api.put<TreatmentFeeRule>(`/api/commission/treatment-fees/${id}`, body),
  setTreatmentFeeEnabled: (id: number, enabled: boolean) =>
    api.patch<TreatmentFeeRule>(
      `/api/commission/treatment-fees/${id}/${enabled ? 'enable' : 'disable'}`,
      {},
    ),
  backfillSingleVisitFees: (from: string, to: string) =>
    api.post<{ recorded: number }>(
      `/api/commission/single-visit-fees/backfill?${new URLSearchParams({ from, to })}`,
      {},
    ),
  myPayments: (month?: string) =>
    api.get<StaffPayment[]>(`/api/commission/my-payments${month ? `?month=${encodeURIComponent(month)}` : ''}`),
  myLines: (month: string) =>
    api.get<TherapistLines>(`/api/commission/my-lines?month=${encodeURIComponent(month)}`),
  myAdjustments: () => api.get<TherapistAdjustment[]>('/api/commission/my-adjustments'),
  adjustments: () => api.get<ManagerAdjustment[]>('/api/commission/adjustments'),
  adjustment: (id: number) => api.get<ManagerAdjustment>(`/api/commission/adjustments/${id}`),
  approveAdjustment: (id: number) =>
    api.post<ManagerAdjustment>(`/api/commission/adjustments/${id}/approve`, {}),
  cancelAdjustment: (id: number) =>
    api.post<ManagerAdjustment>(`/api/commission/adjustments/${id}/cancel`, {}),
  createAdjustmentApplication: (id: number, month: string, amount: number, branchCode: string) =>
    api.post(`/api/commission/adjustments/${id}/applications`, { month, amount, branchCode }),
  approveAdjustmentApplication: (id: number, reference?: string) =>
    api.post(`/api/commission/adjustment-applications/${id}/approve`, { reference }),
  cancelAdjustmentApplication: (id: number) =>
    api.post(`/api/commission/adjustment-applications/${id}/cancel`, {}),
  createAdjustment: (body: {
    staffId: number
    originalMonth: string
    appliedMonth: string
    amount: number
    reason: string
    branchCode: string
    approvalReference?: string
    allocationId?: number
    singleVisitFeeId?: number
    courseId?: number
    visitId?: number
    saleTransactionId?: number
  }) => api.post<ManagerAdjustment>('/api/commission/adjustments', body),
}
