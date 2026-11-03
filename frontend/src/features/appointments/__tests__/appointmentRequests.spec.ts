import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readAppointmentProgress, hasProgressRecord } from '../appointmentRequests'
const progress = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ appointmentApi: { progress }, getToken: () => 'test-session' }))
const result = {
  appointmentId: 55,
  appointmentStatus: 'UNCONFIRMED',
  patientType: 'CURRENT',
  state: 'NO_LINKED_RECORD',
  reason: 'ไม่พบหลักฐานเชื่อม ไม่ใช่ข้อยืนยันว่าไม่เคยมีรายการ',
  visitId: null,
  servedPatientId: null,
  completedAt: null,
  sales: [],
  usages: [],
}
const appointment = {
  id: 55,
  branchCode: '01',
  patientType: 'CURRENT' as const,
  patient: { id: 7, hn: 'HN-7', fullName: 'Test Patient' },
  therapist: { id: 9, fullName: 'Test Therapist' },
  service: { id: 1, name: 'Service', durationMinutes: 60 },
  startAt: '2026-11-03T10:00:00+07:00',
  endAt: '2026-11-03T11:00:00+07:00',
  status: 'UNCONFIRMED' as const,
}
beforeEach(() => progress.mockReset())
describe('progress reads', () => {
  it('coalesces overlapping reads but reads fresh again after completion', async () => {
    let finish!: (value: unknown) => void
    progress.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    const first = readAppointmentProgress(55)
    const second = readAppointmentProgress(55)
    expect(progress).toHaveBeenCalledTimes(1)
    finish(result)
    await Promise.all([first, second])
    progress.mockResolvedValue(result)
    await readAppointmentProgress(55)
    expect(progress).toHaveBeenCalledTimes(2)
  })
  it('accepts main progress without interpreting missing links as proof no transaction exists', async () => {
    progress.mockResolvedValue(result)
    const current = await readAppointmentProgress(55)
    expect(current).toEqual(result)
    expect(hasProgressRecord(current)).toBe(false)
  })
  it('rejects progress outside the main response contract', async () => {
    progress.mockResolvedValue({ appointmentId: 55, paymentStatus: 'NOT_STARTED' })
    await expect(readAppointmentProgress(55)).rejects.toThrow('รูปแบบ')
  })
  it('adapts the running legacy backend response and allows a new appointment with no linked records', async () => {
    progress.mockResolvedValue({
      appointmentId: 55, registered: true, patientId: 7,
      paymentStatus: 'NOT_STARTED', saleId: null, receiptNo: null,
      paid: 0, outstanding: 0, courseStatus: 'NOT_STARTED', courseId: null,
      includesCourse: false, canManage: false, canReview: true,
    })
    const current = await readAppointmentProgress(55, appointment)
    expect(current.state).toBe('NO_LINKED_RECORD')
    expect(current.appointmentStatus).toBe('UNCONFIRMED')
    expect(hasProgressRecord(current)).toBe(false)
  })
  it('blocks legacy progress with existing payment or course evidence instead of treating it as empty', async () => {
    progress.mockResolvedValue({
      appointmentId: 55, registered: true, patientId: 7,
      paymentStatus: 'PAID', saleId: 18, receiptNo: 'R-18',
      paid: 1200, outstanding: 0, courseStatus: 'NOT_STARTED', courseId: null,
      includesCourse: false, canManage: true, canReview: false,
    })
    const current = await readAppointmentProgress(55, appointment)
    expect(current.state).toBe('FOUND')
    expect(current.reason).toContain('PAID')
    expect(hasProgressRecord(current)).toBe(true)
  })
  it('does not retain a network failure as an empty result or cache it', async () => {
    progress.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(result)
    await expect(readAppointmentProgress(55)).rejects.toThrow('offline')
    expect(await readAppointmentProgress(55)).toEqual(result)
    expect(progress).toHaveBeenCalledTimes(2)
  })
})

describe('backend progress validation', () => {
  it.each(['FOUND', 'REVIEW_REQUIRED'])('blocks a new transaction for %s', async (state) => {
    progress.mockResolvedValue({ ...result, state })
    expect(hasProgressRecord(await readAppointmentProgress(55))).toBe(true)
  })
  it('blocks a transaction when main returns sale or usage documents', async () => {
    progress.mockResolvedValue({
      ...result,
      state: 'FOUND',
      sales: [{ saleId: 11, receiptId: 12, receiptNo: 'TEST', receiptStatus: 'Issued', active: true }],
      usages: [{ usageId: 13, movementId: 14, coursePurchaseId: 101, status: 'Completed' }],
    })
    expect(hasProgressRecord(await readAppointmentProgress(55))).toBe(true)
  })
  it.each([
    { appointmentId: 99 },
    { state: 'UNKNOWN' },
    { patientType: 'UNKNOWN' },
    { sales: [{ saleId: -1, receiptId: 12, receiptNo: 'TEST', receiptStatus: 'Issued', active: true }] },
    { usages: [{ usageId: 13, movementId: 14, coursePurchaseId: null, status: 'Completed' }] },
  ])('rejects malformed response %j', async (patch) => {
    progress.mockResolvedValue({ ...result, ...patch })
    await expect(readAppointmentProgress(55)).rejects.toThrow('รูปแบบ')
  })
})
