import { describe, expect, it } from 'vitest'
import { buildSaleRequest, buildCourseUsages } from './careTransactions'
import type { CoursePurchase } from '@/api/client'

const course = {
  id: 101,
  branchCode: '01',
  usable: true,
  remaining: 3,
  patient: { id: 1 },
} as CoursePurchase
const context = {
  patientId: 9,
  branchCode: '01',
  appointmentId: 55,
  salespersonStaffId: 3,
  treatingStaffId: 4,
  paymentLabelCode: 'FULL',
  paymentMethodCode: 'BankTransfer',
  labels: [{ code: 'FULL' as const, label: 'Full', enabled: true }],
  methods: [{ id: 1, code: 'BankTransfer', name: 'BankTransfer', active: true }],
  items: [{ type: 'SERVICE' as const, referenceId: 7, quantity: 1, price: 800 }],
}
describe('main care transactions', () => {
  it('sends distinct seller and actual treating staff, exact net and one payment', () => {
    const request = buildSaleRequest({
      ...context,
      billDiscountType: 'FIXED',
      billDiscountValue: 50,
    })
    expect(request).toMatchObject({
      patientId: 9,
      appointmentId: 55,
      branchCode: '01',
      treatingStaffId: 4,
      salespersonStaffId: 3,
      paymentLabelCode: 'FULL',
      payments: [{ paymentMethodCode: 'BankTransfer', amount: 750 }],
    })
    expect(request.items).toEqual([{ type: 'SERVICE', referenceId: 7, quantity: 1 }])
    expect(request).not.toHaveProperty('requestKey')
  })
  it('expands multiple course purchases into one item per purchased course, without a Visit', () => {
    const request = buildSaleRequest({
      ...context,
      appointmentId: undefined,
      treatingStaffId: undefined,
      items: [{ type: 'COURSE', referenceId: 12, price: 1000, quantity: 2 }],
    })
    expect(request.items).toHaveLength(2)
    expect(request.items.every((item) => item.quantity === 1)).toBe(true)
    expect(request.appointmentId).toBeUndefined()
    expect(request.payments[0]?.amount).toBe(2000)
  })
  it('uses actual transferred patient and repeats usage in one batch request', () => {
    const usages = buildCourseUsages(
      { appointmentId: 55, patientId: 9, treatingStaffId: 4, branchCode: '01' },
      [{ coursePurchaseId: 101, quantity: 2 }],
      [course],
    )
    expect(usages).toHaveLength(2)
    expect(usages[0]).toMatchObject({
      patientId: 9,
      treatingStaffId: 4,
      appointmentId: 55,
      coursePurchaseId: 101,
    })
  })
  it('puts existing course usages in the same sale and does not charge for them again', () => {
    const request = buildSaleRequest({
      ...context,
      usageSelections: [{ coursePurchaseId: 101, quantity: 2 }],
      courses: [course],
    })
    expect(request.courseUsages).toEqual([{ coursePurchaseId: 101 }, { coursePurchaseId: 101 }])
    expect(request.payments[0]?.amount).toBe(800)
  })
  it('allows a service sale without an appointment or treating staff', () => {
    const request = buildSaleRequest({
      ...context,
      appointmentId: undefined,
      treatingStaffId: undefined,
    })
    expect(request.appointmentId).toBeUndefined()
    expect(request.payments[0]?.amount).toBe(800)
  })
  it('rejects a service sale in an appointment without the actual treating staff', () => {
    expect(() => buildSaleRequest({ ...context, treatingStaffId: undefined })).toThrow()
  })
  it.each([
    { paymentMethodCode: 'Cash' },
    { paymentLabelCode: '' },
    { branchCode: '' },
    { billDiscountType: 'FIXED', billDiscountValue: 801 },
  ])('rejects invalid required inputs %j', (change) => {
    expect(() => buildSaleRequest({ ...context, ...change })).toThrow()
  })
  it('rejects mixed sale without a single service line', () => {
    expect(() =>
      buildSaleRequest({
        ...context,
        items: [{ type: 'COURSE', referenceId: 12, price: 1000, quantity: 1 }],
        usageSelections: [{ coursePurchaseId: 101, quantity: 1 }],
        courses: [course],
      }),
    ).toThrow()
  })
  it('rejects overuse including repeated selections, fractions and foreign branches', () => {
    const ctx = { appointmentId: 55, patientId: 9, treatingStaffId: 4, branchCode: '01' }
    expect(() =>
      buildCourseUsages(
        ctx,
        [
          { coursePurchaseId: 101, quantity: 2 },
          { coursePurchaseId: 101, quantity: 2 },
        ],
        [course],
      ),
    ).toThrow()
    expect(() =>
      buildCourseUsages(ctx, [{ coursePurchaseId: 101, quantity: 1.5 }], [course]),
    ).toThrow()
    expect(() =>
      buildCourseUsages(
        ctx,
        [{ coursePurchaseId: 101, quantity: 1 }],
        [{ ...course, branchCode: '02' }],
      ),
    ).toThrow()
  })
})
