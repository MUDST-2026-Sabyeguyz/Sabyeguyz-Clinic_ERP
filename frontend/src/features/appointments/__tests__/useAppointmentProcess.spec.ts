import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAppointmentProcess } from '../useAppointmentProcess'
import { ApiError } from '@/api/client'

const mocks = vi.hoisted(() => ({ progress: vi.fn(), get: vi.fn(), staff: vi.fn() }))
let route = reactive({ query: { appointmentId: '55', patientId: '9' } })
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/api/client', async (original) => ({
  ...(await original<typeof import('@/api/client')>()),
  appointmentApi: { progress: mocks.progress, get: mocks.get },
  staffApi: { list: mocks.staff },
}))
const empty = {
  appointmentId: 55,
  appointmentStatus: 'UNCONFIRMED',
  patientType: 'CURRENT',
  state: 'NO_LINKED_RECORD',
  reason: null,
  visitId: null,
  servedPatientId: null,
  completedAt: null,
  sales: [],
  usages: [],
}
beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  route = reactive({ query: { appointmentId: '55', patientId: '9' } })
  localStorage.setItem(
    'authUser',
    JSON.stringify({ id: 1, staffId: 3, role: 'THERAPIST', branchCode: '01' }),
  )
  mocks.progress.mockResolvedValue(empty)
  mocks.get.mockResolvedValue({
    id: 55,
    branchCode: '01',
    status: 'UNCONFIRMED',
    patientType: 'CURRENT',
    patient: { id: 9 },
    therapist: { id: 3 },
  })
  mocks.staff.mockResolvedValue([{ id: 3, userId: 1 }])
})
function render() {
  let process!: ReturnType<typeof useAppointmentProcess>
  const view = mount({
    setup() {
      process = useAppointmentProcess()
      return () => null
    },
  })
  return {
    view,
    get process() {
      return process
    },
  }
}
describe('main appointment progress', () => {
  it('requires the explicit staffId and never substitutes the account ID', async () => {
    localStorage.setItem('authUser', JSON.stringify({ id: 3, role: 'THERAPIST', branchCode: '01' }))
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('does not invent documents from absent legacy fields', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    expect(process.hasAppointmentDocument.value).toBe(false)
    expect(process.canStartAppointment.value).toBe(true)
    view.unmount()
  })
  it('blocks FOUND and REVIEW_REQUIRED and preserves linked IDs', async () => {
    const { view, process } = render()
    mocks.progress.mockResolvedValue({
      ...empty,
      state: 'FOUND',
      sales: [{ saleId: 11, receiptId: 12, receiptNo: 'TEST', receiptStatus: 'Issued', active: true }],
    })
    await process.refreshAppointmentProcess()
    expect(process.hasAppointmentDocument.value).toBe(true)
    expect(process.canStartAppointment.value).toBe(false)
    expect(process.appointmentProgress.value?.sales[0]?.saleId).toBe(11)
    mocks.progress.mockResolvedValue({ ...empty, state: 'REVIEW_REQUIRED', reason: 'ตรวจสอบ' })
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('clears stale evidence on network failure and never treats it as no record', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    mocks.progress.mockRejectedValue(new Error('network failed'))
    await process.refreshAppointmentProcess()
    expect(process.appointmentProgress.value).toBeNull()
    expect(process.processError.value).toContain('network failed')
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('requires review after an uncertain write even if reading returns no linked record', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    process.beginAppointmentWrite()
    await process.recoverAppointmentWrite()
    expect(process.recoveryRequired.value).toBe(true)
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('keeps a 409 recovery marker across reload and does not retry a write', async () => {
    const first = render()
    await first.process.refreshAppointmentProcess()
    first.process.beginAppointmentWrite()
    await first.process.recoverAppointmentWrite(new ApiError('conflict', 409))
    first.view.unmount()
    const second = render()
    await second.process.refreshAppointmentProcess()
    expect(second.process.recoveryRequired.value).toBe(true)
    expect(second.process.canStartAppointment.value).toBe(false)
    second.view.unmount()
  })
  it('clears the pending marker after a definitive validation failure', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    process.beginAppointmentWrite()
    await process.recoverAppointmentWrite(new ApiError('invalid', 400))
    expect(process.recoveryRequired.value).toBe(false)
    expect(process.canStartAppointment.value).toBe(true)
    view.unmount()
  })
  it('does not enable a duplicate write when success is followed by a missing link', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    process.beginAppointmentWrite()
    process.finishAppointmentWrite()
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(false)
    mocks.progress.mockResolvedValue({ ...empty, state: 'FOUND', sales: [{ saleId: 11, receiptId: 12, receiptNo: 'TEST', receiptStatus: 'Issued', active: true }] })
    await process.refreshAppointmentProcess()
    expect(process.recoveryRequired.value).toBe(false)
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('does not expect a Visit after a confirmed course-only purchase', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    process.beginAppointmentWrite()
    process.finishAppointmentWrite(false)
    await process.refreshAppointmentProcess()
    expect(process.recoveryRequired.value).toBe(false)
    expect(process.canStartAppointment.value).toBe(true)
    view.unmount()
  })
  it('accepts the explicitly supplied registered patient for NEW without linking it to the booking', async () => {
    mocks.get.mockResolvedValue({
      id: 55,
      branchCode: '01',
      status: 'UNCONFIRMED',
      patientType: 'NEW',
      patient: null,
      therapist: { id: 3 },
    })
    mocks.progress.mockResolvedValue({ ...empty, patientType: 'NEW', servedPatientId: null })
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(true)
    expect(process.appointmentProgress.value?.servedPatientId).toBeNull()
    view.unmount()
  })
  it('blocks a different patient or staff owner', async () => {
    const { view, process } = render()
    route.query.patientId = '10'
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(false)
    route.query.patientId = '9'
    localStorage.setItem(
      'authUser',
      JSON.stringify({ id: 1, staffId: 8, role: 'THERAPIST', branchCode: '01' }),
    )
    await process.refreshAppointmentProcess()
    expect(process.canStartAppointment.value).toBe(false)
    view.unmount()
  })
  it('does not let an older appointment response overwrite new navigation', async () => {
    const { view, process } = render()
    await process.refreshAppointmentProcess()
    let finish!: (value: unknown) => void
    mocks.progress.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    const old = process.refreshAppointmentProcess()
    route.query.appointmentId = '56'
    mocks.get.mockResolvedValue({
      id: 56,
      branchCode: '01',
      status: 'UNCONFIRMED',
      patientType: 'CURRENT',
      patient: { id: 9 },
      therapist: { id: 3 },
    })
    mocks.progress.mockResolvedValue({ ...empty, appointmentId: 56 })
    await flushPromises()
    await process.refreshAppointmentProcess()
    finish(empty)
    await old
    expect(process.appointmentProgress.value?.appointmentId).toBe(56)
    view.unmount()
  })
})
