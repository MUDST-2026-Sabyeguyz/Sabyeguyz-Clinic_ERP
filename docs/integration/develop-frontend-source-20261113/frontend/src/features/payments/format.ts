import type { PaymentLabel } from '@/api/client'

const paymentLabels: Record<PaymentLabel, string> = {
  FullyPaid: 'ชำระเต็ม',
  Installment: 'ผ่อน',
  Deposit: 'มัดจำ',
}

export function formatPaymentLabel(label?: PaymentLabel | null): string {
  return label ? (paymentLabels[label] ?? label) : '—'
}

export function formatPaymentMethod(code?: string, name?: string): string {
  if (!code) return name || '—'
  if (['QR', 'QR_CODE', 'QRCODE', 'PROMPTPAY'].includes(code.toUpperCase())) return 'พร้อมเพย์'
  return name || code
}
