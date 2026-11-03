import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from '../views/CourseActionView.vue'
const mock = vi.hoisted(() => ({ use: vi.fn(), batch: vi.fn(), push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/staff/courses',
    params: {},
    query: { patientId: '9', appointmentId: '55', courseId: '101', action: 'use' },
  }),
  useRouter: () => ({ push: mock.push, replace: mock.push }),
}))
vi.mock('@/features/appointments/useAppointmentProcess', () => ({
  useAppointmentProcess: () => ({
    appointmentId: ref(55),
    sourceAppointment: ref({ branchCode: '01' }),
    appointmentProgress: ref(null),
    processLoading: ref(false),
    processError: ref(''),
    refreshAppointmentProcess: async () => {},
    canStartAppointment: ref(true),
    processBlockReason: ref(''),
    recoveryRequired: ref(false),
    beginAppointmentWrite: () => {},
    finishAppointmentWrite: () => {},
    recoverAppointmentWrite: async () => {},
  }),
}))
const purchase = (id: number, remaining = 3) => ({
  id,
  course: { id: 12, name: `Course ${id}` },
  patient: { id: 1 },
  purchaser: { id: 1 },
  branchCode: '01',
  purchased: 10,
  sessions: 10,
  bonusSessions: 0,
  used: 7,
  remaining,
  usable: true,
  expired: false,
  expiresAt: '2030-01-01T00:00:00Z',
  purchasedAt: '2026-01-01T00:00:00Z',
  status: 'ACTIVE',
})
vi.mock('@/api/client', () => ({
  patientApi: {
    searchAll: async () => [{ id: 9, fullName: 'Actual recipient', hn: 'TEST9', branchCode: '01' }],
  },
  staffApi: {
    list: async () => [
      { id: 4, position: 'Therapist', active: true, branchCode: '01', fullName: 'Treating PT' },
    ],
  },
  branchApi: { list: async () => [] },
  courseApi: {
    patientCourses: async () => [purchase(101), purchase(102)],
    use: mock.use,
    useBatch: mock.batch,
  },
}))
type State = {
  treatingStaffId: number
  usageSelections: Array<{ coursePurchaseId: number; quantity: number }>
  errorMessage: string
  usageSuccess: boolean
  confirm: () => Promise<void>
  patientCourses: Array<{ id: number; remaining: number }>
}
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER' }))
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  mock.use.mockResolvedValue(purchase(101, 2))
  mock.batch.mockResolvedValue([purchase(101, 1), purchase(102, 2)])
})
async function render() {
  const view = mount(View, {
    global: {
      stubs: {
        StaffHeader: true,
        RouterLink: true,
        ThaiDatePicker: true,
        CourseRenewalDialog: true,
      },
    },
  })
  await flushPromises()
  const state = view.vm as unknown as State
  state.treatingStaffId = 4
  return { view, state }
}
describe('course usage main contract', () => {
  it('sends actual recipient, appointment and therapist for a single entitlement', async () => {
    const { view, state } = await render()
    state.usageSelections = [{ coursePurchaseId: 101, quantity: 1 }]
    await state.confirm()
    expect(mock.use).toHaveBeenCalledWith(
      expect.objectContaining({
        patientId: 9,
        treatingStaffId: 4,
        appointmentId: 55,
        coursePurchaseId: 101,
      }),
    )
    expect(mock.batch).not.toHaveBeenCalled()
    expect(state.usageSuccess).toBe(true)
    view.unmount()
  })
  it('uses one batch POST for repeated and multiple courses, reading distinct response rows by ID', async () => {
    const { view, state } = await render()
    state.usageSelections = [
      { coursePurchaseId: 101, quantity: 2 },
      { coursePurchaseId: 102, quantity: 1 },
    ]
    await state.confirm()
    expect(mock.batch).toHaveBeenCalledOnce()
    expect(mock.batch.mock.calls[0]![0].usages).toHaveLength(3)
    expect(mock.use).not.toHaveBeenCalled()
    expect(state.patientCourses.map((row) => row.remaining)).toEqual([1, 2])
    view.unmount()
  })
  it('does not manufacture a remaining balance from a malformed response', async () => {
    const { view, state } = await render()
    state.usageSelections = [{ coursePurchaseId: 101, quantity: 1 }]
    mock.use.mockResolvedValue({ id: 101, remaining: null, used: null })
    await state.confirm()
    expect(state.usageSuccess).toBe(false)
    expect(state.patientCourses[0]?.remaining).toBe(3)
    expect(state.errorMessage).toContain('ยืนยัน')
    view.unmount()
  })
})
