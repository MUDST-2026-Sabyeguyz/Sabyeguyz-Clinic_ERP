import { describe, expect, it } from 'vitest'
import { filterPaymentReportRows } from '../lib/paymentReport'

describe('payment report', () => {
  const source = [
    { date: '2026-10-02', type: 'COURSE_PURCHASE', amount: 1200 },
    { date: '2026-10-03', type: 'SINGLE_VISIT', amount: 400 },
    { date: '2026-10-04', type: 'ASSESSMENT', amount: 200 },
    { date: '2026-11-01', type: 'COURSE_PURCHASE', amount: 800 },
  ]

  it('uses payment-date rows and filters the selected date interval', () => {
    expect(
      filterPaymentReportRows(source, {
        from: '2026-10-03',
        to: '2026-10-04',
        saleType: 'ทั้งหมด',
        treatment: 'ทั้งหมด',
      }),
    ).toEqual([
      { category: 'Single Visit Sales', treatment: 'Exercise Therapy', date: '2026-10-03', paid: 400 },
      { category: 'Assessment', treatment: 'Assessment', date: '2026-10-04', paid: 200 },
    ])
  })

  it('filters by category and treatment while preserving assessments', () => {
    expect(
      filterPaymentReportRows(source, {
        from: '2026-10-01',
        to: '2026-10-31',
        saleType: 'Course Sales',
        treatment: 'Manual Therapy',
      }),
    ).toEqual([
      { category: 'Course Sales', treatment: 'Manual Therapy', date: '2026-10-02', paid: 1200 },
    ])
  })
})
