import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from '../views/CourseActionView.vue'
const mock = vi.hoisted(() => ({ use: vi.fn(), batch: vi.fn(), push: vi.fn(), hasAppointment: true }))
vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/staff/courses',
    params: {},
    query: mock.hasAppointment
      ? { patientId: '9', appointmentId: '55', courseId: '101', action: 'use' }
      : { patientId: '9' },
  }),
  useRouter: () => ({ push: mock.push, replace: mock.push }),
}))
vi.mock('@/features/appointments/useAppointmentProcess', () => ({
  useAppointmentProcess: () => ({
    appointmentId: ref(mock.hasAppointment ? 55 : undefined),
    sourceAppointment: ref({ branchCode: '01', therapist: { id: 4, fullName: 'Treating PT' } }),
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
  usageReview: boolean
  action: string
  confirm: () => Promise<void>
  patientCourses: Array<{ id: number; remaining: number }>
  selectedCourse: ReturnType<typeof purchase> & { name: string; balance: number; total: number }
  courseUsageHistory: Array<{ id: number; type: string; quantity: number; createdAt: string; patientName: string; therapistName: string }>
  coursePrintPreview: boolean
}
beforeEach(() => {
  vi.clearAllMocks()
  mock.hasAppointment = true
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
  return { view, state }
}
describe('course usage main contract', () => {
  it('shows the patient and treating therapist in the course print preview', async () => {
    const { view, state } = await render()
    state.selectedCourse = { ...purchase(101), name: 'Course 101', balance: 3, total: 10 }
    state.courseUsageHistory = [{
      id: 1, type: 'USAGE', quantity: -2, createdAt: '2026-11-13T10:00:00Z',
      patientName: 'Actual recipient', therapistName: 'Treating PT',
    }]
    state.action = 'history'
    state.coursePrintPreview = true
    await nextTick()

    const cells = view.findAll('.course-history-document tbody td').map((cell) => cell.text())
    expect(cells.slice(1)).toEqual(['Treating PT', 'Actual recipient', '2'])
    view.unmount()
  })
  it('returns from usage history to course management without the completed appointment context', async () => {
    const { view, state } = await render()
    state.action = 'history'
    await nextTick()
    await view.get('.course-history-back').trigger('click')
    await flushPromises()
    expect(mock.push).toHaveBeenCalledWith({
      path: '/staff/courses',
      query: { patientId: 9, courseId: 101 },
    })
    expect(state.action).toBe('manage')
    expect(view.find('.course-history-main').exists()).toBe(false)
    view.unmount()
  })
  it('stays on the course list and explains the appointment requirement', async () => {
    mock.hasAppointment = false
    const { view } = await render()
    const useButton = view.findAll('button').find((button) => button.text() === 'ใช้สิทธิ์คอร์ส')
    expect(useButton).toBeDefined()
    await useButton!.trigger('click')
    expect(mock.push).not.toHaveBeenCalled()
    expect(view.get('.course-appointment-notice').text()).toContain('ต้องมีนัดหมายก่อนใช้สิทธิ์คอร์ส')
    expect(mock.use).not.toHaveBeenCalled()
    view.unmount()
  })
  it('opens the new review directly without rendering the old usage form', async () => {
    const { view } = await render()
    expect(view.find('.course-use-main > .course-use-form').exists()).toBe(false)
    expect(view.find('.course-use-columns').exists()).toBe(false)
    expect(view.find('.course-use-review').exists()).toBe(true)
    expect(view.text()).not.toContain('ผู้รับบริการจริง')
    expect(view.text()).not.toContain('เพิ่มคอร์สที่ใช้สิทธิ์')
    expect(view.find('button[aria-label="แก้ไขนักกายภาพ"]').exists()).toBe(true)
    view.unmount()
  })

  it('defaults the treating therapist from the appointment', async () => {
    const { view, state } = await render()
    expect(state.treatingStaffId).toBe(4)
    expect(view.text()).toContain('Treating PT')
    view.unmount()
  })

  it('hides the setup card while reviewing course usage', async () => {
    const { view, state } = await render()
    state.usageReview = true
    await nextTick()
    expect(view.find('.course-use-main > .course-use-form').exists()).toBe(false)
    expect(view.find('.course-use-review').exists()).toBe(true)
    view.unmount()
  })

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
