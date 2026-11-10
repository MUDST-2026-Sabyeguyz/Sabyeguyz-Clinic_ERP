import { describe, expect, it } from 'vitest'
import type { StaffPayment } from '@/api/client'
import { canClosePaymentBatch, isPaymentDateAllowed } from '../lib/paymentBatch'

function payment(status: StaffPayment['status'], overrides: Partial<StaffPayment> = {}): StaffPayment {
  return {
    id: 1,
    staffId: 3,
    staffName: 'Test Staff',
    month: '2026-10',
    status,
    courseCommission: 0,
    singleVisitFee: 0,
    substituteFee: 0,
    adjustment: 0,
    net: 0,
    paidAt: null,
    closingId: null,
    pendingSessions: 0,
    applications: [],
    ...overrides,
  }
}

describe('branch payment batch close', () => {
  it('does not close an empty month', () => {
    expect(canClosePaymentBatch([])).toBe(false)
  })

  it('allows a mixed batch because the backend closes OPEN rows and preserves PAID rows', () => {
    expect(canClosePaymentBatch([payment('OPEN'), payment('PAID')])).toBe(true)
    expect(canClosePaymentBatch([payment('PAID')])).toBe(false)
  })

  it('blocks a batch while an amount or pending-session count is unknown', () => {
    expect(canClosePaymentBatch([payment('OPEN', { net: null })])).toBe(false)
    expect(canClosePaymentBatch([payment('OPEN', { pendingSessions: null })])).toBe(false)
  })

  it('rejects payment dates after today', () => {
    expect(isPaymentDateAllowed('2026-10-30', '2026-10-31')).toBe(true)
    expect(isPaymentDateAllowed('2026-11-01', '2026-10-31')).toBe(false)
    expect(isPaymentDateAllowed('not-a-date', '2026-10-31')).toBe(false)
  })
})
