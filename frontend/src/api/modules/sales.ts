import { api } from './http'
import { type Patient } from './patient'
import { type Page } from './common'
import { type PaymentLabelCode } from './payment'

export type SaleSummary = {
  id: number
  receiptId?: number | null
  receiptNo: string
  patient: Patient
  branchCode: string
  salesperson?: { id: number; employeeCode: string; fullName: string }
  total: number
  paid: number
  status: string
  soldAt: string
  voidedAt?: string
  voidReason?: string
  itemDescription?: string
}

export type SaleReceipt = SaleSummary & { note?: string }

// Payload ขายรวมรายการชำระและการใช้คอร์สในคำขอเดียว
export type SaleCreateRequest = {
  requestKey?: string
  appointmentId?: number
  patientId: number
  branchCode: string
  salespersonStaffId: number
  treatingStaffId?: number
  note?: string
  paymentLabelCode: PaymentLabelCode
  items: Array<{
    type: 'SERVICE' | 'COURSE'
    referenceId: number
    quantity: number
    discountType?: 'PERCENT' | 'FIXED'
    discountValue?: number
  }>
  billDiscountType?: 'PERCENT' | 'FIXED'
  billDiscountValue?: number
  courseUsages?: Array<{ coursePurchaseId: number; note?: string; treatmentId?: number }>
  payments: Array<{ paymentMethodCode: string; amount: number }>
}

export type SaleDetail = SaleReceipt & {
  paymentLabel: 'FullyPaid' | 'Installment' | 'Deposit'
  subtotalBeforeDiscount: number
  billDiscountAmount: number
  netAmount: number
  taxableAmount?: number
  taxAmount?: number
  branch?: { code: string; name: string; address: string; phone: string; email?: string; licenseNumber?: string }
  items: Array<{
    id: number
    type: string
    description: string
    unitName?: string
    quantity: number
    unitPrice?: number
    lineDiscountAmount?: number
    allocatedBillDiscountAmount?: number
    lineTotal: number
  }>
  payments: Array<{
    id: number
    type: string
    paymentMethodCode: string
    amount: number
    paidAt?: string
  }>
}

export const salesApi = {
  list: (
    params: {
      patientId?: number
      branchCode?: string
      page?: number
      size?: number
      receiptsOnly?: boolean
    } = {},
  ) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 20, ...params }).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value))
    })
    return api.get<Page<SaleSummary>>(`/api/payments?${query}`)
  },
  get: (id: number) => api.get<SaleDetail>('/api/receipts/' + id),
  findReceipt: (receiptNo: string, branchCode: string) =>
    api.get<SaleDetail>(`/api/receipts/search?${new URLSearchParams({ receiptNo, branchCode })}`),
  create: (body: SaleCreateRequest) => api.post<SaleReceipt>('/api/payments', body),
  reverse: (id: number, reason: string) =>
    api.post<SaleDetail>(`/api/payments/${id}/reverse`, { reason }),
}
