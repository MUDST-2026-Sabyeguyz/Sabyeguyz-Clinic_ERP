import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PatientHistoryView from '../views/PatientHistoryView.vue'

const mocks = vi.hoisted(() => ({
  patientGet: vi.fn(),
  salesList: vi.fn(),
  salesGet: vi.fn(),
  patientCourses: vi.fn(),
  entitlements: vi.fn(),
  uses: vi.fn(),
  reverse: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '5' }, query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))
vi.mock('@/api/client', () => ({
  patientApi: { get: mocks.patientGet },
  salesApi: { list: mocks.salesList, get: mocks.salesGet },
  courseApi: { patientCourses: mocks.patientCourses },
  singleVisitEntitlementApi: {
    list: mocks.entitlements,
    uses: mocks.uses,
    reverse: mocks.reverse,
  },
}))

const use = (overrides: Record<string, unknown> = {}) => ({
  id: 31,
  entitlementId: 3,
  serviceName: 'กายภาพบำบัดทั่วไป',
  receiptNo: 'RE26110001',
  appointmentId: 8,
  visitId: 9,
  usedAt: '2026-11-12T10:00:00Z',
  treatingStaffName: 'สบายกาย คลินิก',
  reversedAt: null,
  reversalReason: null,
  reverseBlockedReason: null,
  ...overrides,
})

const mountView = () =>
  mount(PatientHistoryView, {
    global: {
      stubs: {
        StaffHeader: true,
        FilterDropdown: true,
        ThaiDatePicker: true,
        RouterLink: true,
      },
    },
  })

beforeEach(() => {
  localStorage.setItem('authUser', JSON.stringify({ id: 1, role: 'MANAGER' }))
  vi.resetAllMocks()
  mocks.patientGet.mockResolvedValue({ id: 5, fullName: 'ผู้ป่วย A', hn: 'HN1' })
  mocks.salesList.mockResolvedValue({ items: [], totalPages: 1 })
  mocks.salesGet.mockResolvedValue({ items: [] })
  mocks.patientCourses.mockResolvedValue([])
  mocks.entitlements.mockResolvedValue([])
  mocks.uses.mockResolvedValue([use()])
  mocks.reverse.mockResolvedValue({})
})

describe('single-visit use history', () => {
  it('lists the uses and lets the manager reverse one with a reason', async () => {
    const view = mountView()
    await flushPromises()
    const section = view.get('[data-test="single-visit-uses"]')
    expect(section.text()).toContain('กายภาพบำบัดทั่วไป')
    expect(section.text()).toContain('สบายกาย คลินิก')

    await view.get('[data-test="reverse-use"]').trigger('click')
    // ต้องระบุเหตุผลก่อน
    await view.get('[data-test="confirm-reverse"]').trigger('click')
    expect(mocks.reverse).not.toHaveBeenCalled()
    expect(view.text()).toContain('ระบุเหตุผลที่ย้อนการใช้สิทธิ์')

    mocks.uses.mockResolvedValue([use({ reversedAt: '2026-11-13T01:00:00Z', reversalReason: 'ผู้รักษาผิดคน' })])
    await view.get('[data-test="reverse-reason"]').setValue('ผู้รักษาผิดคน')
    await view.get('[data-test="confirm-reverse"]').trigger('click')
    await flushPromises()
    expect(mocks.reverse).toHaveBeenCalledWith(31, 'ผู้รักษาผิดคน')
    // โหลดรายการใหม่: ขึ้นว่าย้อนแล้ว และไม่มีปุ่มย้อนอีก
    expect(section.text()).toContain('ย้อนแล้ว')
    expect(view.find('[data-test="reverse-use"]').exists()).toBe(false)
    view.unmount()
  })

  it('disables the reverse button with the reason when the fee was already paid', async () => {
    mocks.uses.mockResolvedValue([use({ reverseBlockedReason: 'ค่ามือของการใช้สิทธิ์นี้ถูกจ่ายแล้ว' })])
    const view = mountView()
    await flushPromises()
    expect(view.find('[data-test="reverse-use"]').exists()).toBe(false)
    const blocked = view.get('[data-test="reverse-blocked"]')
    expect(blocked.attributes('disabled')).toBeDefined()
    expect(blocked.attributes('title')).toContain('จ่ายแล้ว')
    view.unmount()
  })

  it('shows the error when the server refuses the reversal', async () => {
    mocks.reverse.mockRejectedValue(new Error('ไม่มีสิทธิ์ย้อน'))
    const view = mountView()
    await flushPromises()
    await view.get('[data-test="reverse-use"]').trigger('click')
    await view.get('[data-test="reverse-reason"]').setValue('ผิดคน')
    await view.get('[data-test="confirm-reverse"]').trigger('click')
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('ไม่มีสิทธิ์ย้อน')
    view.unmount()
  })

  it('shows nothing when the patient has no single-visit uses', async () => {
    mocks.uses.mockResolvedValue([])
    const view = mountView()
    await flushPromises()
    expect(view.find('[data-test="single-visit-uses"]').exists()).toBe(false)
    view.unmount()
  })
})

describe('single-visit use history for other roles', () => {
  it('hides the uses and does not load them for a therapist', async () => {
    localStorage.setItem('authUser', JSON.stringify({ id: 2, role: 'THERAPIST' }))
    const view = mountView()
    await flushPromises()
    expect(view.find('[data-test="single-visit-uses"]').exists()).toBe(false)
    expect(mocks.uses).not.toHaveBeenCalled()
  })
})
