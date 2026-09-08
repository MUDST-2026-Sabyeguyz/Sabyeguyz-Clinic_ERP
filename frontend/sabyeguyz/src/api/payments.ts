import { authFetch } from './http'

const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export interface PaymentMethod { id: number; code: string; name: string; active: boolean }
export interface ReceiptSummary { id: number; receiptNumber: string; patientId: number; patientName: string; branchCode: string; total: number; paid: number; status: string; issuedAt: string }
export interface ReceiptPage { items: ReceiptSummary[]; page: number; size: number; totalItems: number; totalPages: number }
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await authFetch(`${base}${url}`, { ...options, headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) } })
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.message ?? 'ไม่สามารถดำเนินการด้านการชำระเงินได้')
  return body as T
}
export const listPaymentMethods = () => request<PaymentMethod[]>('/api/payment-methods')
export const listReceipts = (page = 0, size = 20, branchCode?: string) => request<ReceiptPage>(`/api/payments?page=${page}&size=${size}${branchCode ? `&branchCode=${encodeURIComponent(branchCode)}` : ''}`)
export const reverseReceipt = (id: number, reason: string) => request(`/api/payments/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) })
