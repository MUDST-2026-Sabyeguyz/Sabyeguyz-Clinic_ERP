import { describe, expect, it } from 'vitest'
import { filterPaymentReportRows, saleTypeLabel } from '../lib/paymentReport'

describe('payment report', () => {
  const source = [
    { date: '2026-10-02', type: 'CoursePurchase', amount: 1200 },
    { date: '2026-10-03', type: 'SingleVisit', amount: 400 },
    { date: '2026-10-04', type: 'Assessment', amount: 200 },
    { date: '2026-11-01', type: 'CoursePurchase', amount: 800 },
  ]

  it('maps backend transaction types to Thai sale type labels', () => {
    expect(saleTypeLabel('CoursePurchase')).toBe('ขายคอร์ส')
    expect(saleTypeLabel('COURSE_PURCHASE')).toBe('ขายคอร์ส')
    expect(saleTypeLabel('SingleVisit')).toBe('ขายรายครั้ง')
    expect(saleTypeLabel('SINGLE_VISIT')).toBe('ขายรายครั้ง')
    expect(saleTypeLabel('Assessment')).toBe('อื่นๆ')
  })

  it('uses payment-date rows and filters the selected date interval', () => {
    expect(
      filterPaymentReportRows(source, {
        from: '2026-10-03',
        to: '2026-10-04',
        saleType: 'ทั้งหมด',
      }),
    ).toEqual([
      { category: 'ขายรายครั้ง', date: '2026-10-03', paid: 400 },
      { category: 'อื่นๆ', date: '2026-10-04', paid: 200 },
    ])
  })

  it('filters by sale type', () => {
    expect(
      filterPaymentReportRows(source, {
        from: '2026-10-01',
        to: '2026-10-31',
        saleType: 'ขายคอร์ส',
      }),
    ).toEqual([{ category: 'ขายคอร์ส', date: '2026-10-02', paid: 1200 }])
  })
})
