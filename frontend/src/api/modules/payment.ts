import { api } from './http'

export type PaymentMethod = {
  id: number
  code: string
  name: string
  active: boolean
}

export type PaymentLabelCode = 'FULL' | 'PAID' | 'INSTALLMENT' | 'DEPOSIT'

export type PaymentStatusOption = {
  code: PaymentLabelCode
  label: string
  enabled: boolean
}

export const paymentMethodApi = {
  list: () => api.get<PaymentMethod[]>('/api/payment-methods'),
  create: (body: { code: string; name: string }) =>
    api.post<PaymentMethod>('/api/payment-methods', body),
  update: (id: number, body: { code: string; name: string }) =>
    api.put<PaymentMethod>(`/api/payment-methods/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/payment-methods/${id}`),
  activate: (id: number) => api.patch<PaymentMethod>(`/api/payment-methods/${id}/activate`, {}),
}

export const paymentStatusApi = {
  list: () => api.get<PaymentStatusOption[]>('/api/payment-statuses'),
  setEnabled: (code: PaymentStatusOption['code'], enabled: boolean) =>
    api.patch<PaymentStatusOption>(`/api/payment-statuses/${code}`, { enabled }),
}
