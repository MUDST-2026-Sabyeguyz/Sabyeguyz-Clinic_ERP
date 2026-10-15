import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AppointmentView from '../views/AppointmentView.vue'
import { appointmentApi } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('/calendar-appointment.svg', () => ({ default: '/calendar-appointment.svg' }))
vi.mock('@/api/client', async (original) => {
  const actual = await original<typeof import('@/api/client')>()
  return {
    ...actual,
    appointmentApi: { search: vi.fn() },
    branchApi: { list: vi.fn(async () => [{ branchCode: '01', name: 'Branch', active: true }]) },
    patientApi: { searchAll: vi.fn(async () => []) },
    catalogApi: {
      services: vi.fn(async () => [{ id: 1, name: 'Service' }]),
      therapists: vi.fn(async () => [{ id: 1, fullName: 'Therapist', branchCode: '01' }]),
    },
    calendarApi: {
      status: vi.fn(async () => ({
        enabled: true,
        branches: [
          { branchCode: '01', branchName: 'Branch', googleCalendarId: 'branch@example.test' },
        ],
      })),
    },
  }
})

let wrapper: VueWrapper | undefined
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] })
  localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER', branchCode: '01' }))
  vi.mocked(appointmentApi.search).mockResolvedValue({
    items: [
      {
        id: 1,
        branchCode: '01',
        patient: { id: 1, fullName: 'Patient', hn: 'HN1', phone: '0812345678' },
        therapist: { id: 1, fullName: 'Therapist' },
        service: { id: 1, name: 'Service', durationMinutes: 60 },
        startAt: `${clinicToday()}T09:45:00+07:00`,
        endAt: `${clinicToday()}T10:45:00+07:00`,
        status: 'CONFIRMED',
        patientType: 'CURRENT',
      },
    ],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
  })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  localStorage.clear()
  vi.clearAllMocks()
  vi.useRealTimers()
})
async function open() {
  wrapper = mount(AppointmentView, {
    global: {
      stubs: { StaffHeader: true, FilterDropdown: true, ThaiDatePicker: true, RouterLink: true },
    },
  })
  await flushPromises()
  return wrapper
}

it('keeps the server search during calendar polling and stops polling after unmount', async () => {
  const view = await open()
  await view.get('input[placeholder="ค้นหาชื่อ เบอร์โทร หรือรหัส HN"]').setValue('081234')
  await vi.advanceTimersByTimeAsync(250)
  await flushPromises()
  expect(appointmentApi.search).toHaveBeenLastCalledWith({ page: 0, size: 20, query: '081234' })
  await vi.advanceTimersByTimeAsync(15000)
  await flushPromises()
  expect(appointmentApi.search).toHaveBeenLastCalledWith({ page: 0, size: 20, query: '081234' })
  const calls = vi.mocked(appointmentApi.search).mock.calls.length
  view.unmount()
  wrapper = undefined
  await vi.advanceTimersByTimeAsync(15000)
  expect(appointmentApi.search).toHaveBeenCalledTimes(calls)
})

it('shows the actual off-grid time and phone together with the connected Google calendar link', async () => {
  const view = await open()
  await view.get('button.calendar-entry').trigger('click')
  const slot = view.findAll('.calendar-slot').find((item) => item.text().includes('Patient'))
  expect(slot?.text()).toContain('09:45')
  expect(slot?.text()).toContain('0812345678')
  expect(view.get('[data-cy="google-open-main"]').attributes('href')).toContain(
    'branch%40example.test',
  )
})
