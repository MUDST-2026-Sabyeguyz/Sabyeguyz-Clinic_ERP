import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import AppointmentCalendarWorkspace from '../components/AppointmentCalendarWorkspace.vue'
import { setManagerBranches, setSelectedManagerBranch } from '@/features/staff/data/managerBranch'
const mock = vi.hoisted(() => ({
  search: vi.fn(),
  availability: vi.fn(),
  branches: vi.fn(),
  syncNow: vi.fn(),
  therapists: vi.fn(),
  push: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mock.push }),
  useRoute: () => ({ query: { calendarDate: '2026-10-19', calendarMode: 'week' } }),
}))
vi.mock('@/api/client', () => ({
  appointmentApi: { search: mock.search, availability: mock.availability },
  branchApi: { list: mock.branches },
  calendarApi: { syncNow: mock.syncNow },
  ApiError: class ApiError extends Error {},
}))
vi.mock('../therapists', () => ({ loadAppointmentTherapists: mock.therapists }))
const fixture = (id: number, branchCode = '01', status = 'CONFIRMED') => ({
  id,
  branchCode,
  status,
  startAt: '2026-10-19T02:00:00Z',
  endAt: '2026-10-19T03:00:00Z',
  therapist: { id: 1, fullName: 'Therapist A' },
  patient: { id: 1, fullName: `Patient ${id}` },
  service: { name: 'Therapy' },
})
const render = (manager = false) =>
  mount(AppointmentCalendarWorkspace, {
    props: { manager },
    global: { stubs: { StaffHeader: true, CalendarSyncIndicator: true, RouterLink: true } },
  })
beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()
  localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER' }))
  setManagerBranches([
    { code: '01', name: 'พระราม 9' },
    { code: '02', name: 'แบริ่ง' },
  ])
  setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  mock.branches.mockResolvedValue([
    { code: '01', name: 'พระราม 9', bookingOpenTime: '09:00', bookingCloseTime: '10:00' },
    { code: '02', name: 'แบริ่ง' },
  ])
  mock.syncNow.mockResolvedValue({ pushed: 0, removed: 0, failed: 0, movedFromGoogle: 0, cancelledFromGoogle: 0, revertedInGoogle: 0 })
  mock.therapists.mockResolvedValue([
    { id: 1, branchCode: '01', fullName: 'Therapist A' },
    { id: 2, branchCode: '02', fullName: 'Therapist B' },
  ])
  mock.search.mockResolvedValue({
    items: [fixture(1), fixture(2, '01', 'CANCELLED'), fixture(3, '02')],
    totalPages: 1,
  })
  mock.availability.mockResolvedValue({
    slots: [
      { time: '09:00', available: true },
      { time: '09:30', available: false },
    ],
  })
})
describe('shared appointment calendar', () => {
  it('leaves a cancelled appointment time available when the backend reports it free', async () => {
    mock.search.mockResolvedValue({ items: [fixture(2, '01', 'CANCELLED')], totalPages: 1 })
    const view = render(true)
    await flushPromises()
    expect(view.findAll('.appointment-block')).toHaveLength(0)
    expect(view.findAll('.empty-slot')[0]!.attributes('disabled')).toBeUndefined()
    await view.findAll('.empty-slot')[0]!.trigger('click')
    expect(mock.push).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'staff-appointment-create' }),
    )
    view.unmount()
  })
  it('renders all seven days, excludes cancelled/foreign-branch events and checks real slot availability', async () => {
    const view = render(true)
    await flushPromises()
    expect(view.findAll('.day-column')).toHaveLength(7)
    expect(view.findAll('.appointment-block')).toHaveLength(1)
    expect(view.text()).not.toContain('Patient 2')
    expect(view.text()).not.toContain('Patient 3')
    expect(view.findAll('.empty-slot')[1]!.attributes('disabled')).toBeDefined()
    await view.findAll('.empty-slot')[0]!.trigger('click')
    expect(mock.push).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'staff-appointment-create',
        query: expect.objectContaining({
          source: 'calendar',
          date: '2026-10-19',
          time: '09:00',
          branchCode: '01',
        }),
      }),
    )
    view.unmount()
  })
  it('lets therapists view own-branch appointments without edit/delete controls', async () => {
    localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST', branchCode: '01' }))
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    const view = render()
    await flushPromises()
    expect(mock.search).toHaveBeenCalledWith(expect.objectContaining({ branchCode: '01' }))
    await view.get('.appointment-block').trigger('click')
    await nextTick()
    expect(view.get('dialog').text()).toContain('Patient 1')
    expect(view.get('dialog').text()).not.toContain('จัดการนัดหมาย')
    expect(view.get('dialog').text()).not.toContain('ยกเลิกนัด')
    expect(view.find('.calendar-sync').exists()).toBe(false)
    view.unmount()
  })
  it('shows the sync action to a Manager but not a therapist', async () => {
    const managerView = render(true)
    await flushPromises()
    expect(managerView.find('.calendar-sync').exists()).toBe(true)
    managerView.unmount()

    localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST', branchCode: '01' }))
    const therapistView = render()
    await flushPromises()
    expect(therapistView.find('.calendar-sync').exists()).toBe(false)
    expect(mock.syncNow).not.toHaveBeenCalled()
    therapistView.unmount()
  })
  it('fetches every appointment page and reloads with the Header branch', async () => {
    mock.search.mockImplementation(({ page }: { page: number }) =>
      Promise.resolve({ items: [fixture(page + 1)], totalPages: 2 }),
    )
    const view = render(true)
    await flushPromises()
    expect(mock.search).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
    expect(view.findAll('.appointment-block')).toHaveLength(2)
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    await flushPromises()
    expect(mock.search).toHaveBeenLastCalledWith(expect.objectContaining({ branchCode: '02' }))
    expect(view.findAll('.appointment-block')).toHaveLength(0)
    view.unmount()
  })
  it('fails closed without therapist branch access', async () => {
    localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST' }))
    const view = render()
    await flushPromises()
    expect(mock.search).not.toHaveBeenCalled()
    expect(view.text()).toContain('ไม่มีสิทธิ์เข้าถึง กรุณาติดต่อ Manager')
    view.unmount()
  })
})
