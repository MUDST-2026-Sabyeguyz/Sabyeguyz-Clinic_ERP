import { workflowRequest as request } from './workflowHttp'
export interface PaymentMethod { id: number; code: string; name: string; active: boolean }
export type PaymentMethodPayload = Pick<PaymentMethod, 'code' | 'name'>
export const listPaymentMethods = () => request<PaymentMethod[]>('/api/payment-methods')
export const createPaymentMethod = (body: PaymentMethodPayload) => request<PaymentMethod>('/api/payment-methods', 'POST', body)
export const updatePaymentMethod = (id: number, body: PaymentMethodPayload) => request<PaymentMethod>(`/api/payment-methods/${id}`, 'PUT', body)
export const deactivatePaymentMethod = (id: number) => request<void>(`/api/payment-methods/${id}`, 'DELETE')
