import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppointmentView from '../views/AppointmentView.vue'
import ManagerAppointmentView from '../views/ManagerAppointmentView.vue'
import { clinicToday } from '@/utils/clinicDate'

const api = vi.hoisted(() => ({ search: vi.fn() }))
vi.mock('@/api/client', () => ({
  getToken: () => 'test-token',
  appointmentApi: { search: api.search },
  branchApi: { list: vi.fn(async () => [{ code: '01', name: 'Demo branch', active: true }]) },
  staffApi: { list: vi.fn(async () => [{ id: 1, fullName: 'Demo therapist', branchCode: '01' }]) },
  patientApi: { searchAll: vi.fn(async () => []) },
  catalogApi: { services: vi.fn(async () => []) },
  courseApi: {},
}))
vi.mock('../therapists', () => ({ loadAppointmentTherapists: vi.fn(async () => []) }))

function record(id: number, course = false) {
  return {
    id,
    startAt: `${clinicToday()}T09:00:00+07:00`,
    status: 'UNCONFIRMED',
    branchCode: '01',
    patient: { id, fullName: course ? 'Course patient' : `Patient ${id}` },
    service: { id: 1, name: course ? 'คอร์สกายภาพ' : 'รายครั้ง' },
    therapist: { id: 1, fullName: 'Demo therapist' },
  }
}

beforeEach(() => {
  api.search.mockReset()
})

for (const [role, Component] of [
  ['therapist', AppointmentView],
  ['manager', ManagerAppointmentView],
] as const) {
  describe(`${role} appointment daily list`, () => {
    async function render() {
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [{ path: '/staff/appointments', name: 'staff-appointments', component: Component }],
      })
      await router.push('/staff/appointments')
      return mount(Component, {
        global: {
          plugins: [router],
          stubs: {
            StaffHeader: true,
            ThaiDatePicker: true,
            MonthFilterPicker: true,
            FilterDropdown: true,
            GoogleCalendarPanel: true,
          },
        },
      })
    }

    it('opens without a date filter, includes both service types, and filters across server pages before local pagination', async () => {
      api.search.mockImplementation(async ({ page }: { page: number }) => ({
        items:
          page === 0 ? Array.from({ length: 20 }, (_, i) => record(i + 1)) : [record(21, true)],
        totalPages: 2,
      }))
      const view = await render()
      await flushPromises()
      expect(api.search).toHaveBeenCalledWith(
        expect.objectContaining({
          startDate: undefined,
          endDate: undefined,
          sortOrder: 'newest',
        }),
      )
      expect(view.get('.appointment-summary-date').text()).toBe('นัดหมายทั้งหมด')
      expect(view.findAll('tbody tr')).toHaveLength(20)
      expect(view.get('.appointment-sort').text()).toBe('ล่าสุด ↓')
      expect(view.find('tbody tr').text()).toContain('Course patient')
      const requests = api.search.mock.calls.length
      const serviceFilter = view.findComponent('.appointment-service-filter') as VueWrapper
      serviceFilter.vm.$emit('update:modelValue', 'course')
      await view.vm.$nextTick()
      await flushPromises()
      expect(view.findAll('tbody tr')).toHaveLength(1)
      expect(view.get('tbody').text()).toContain('Course patient')
      expect(view.get('.appointment-pagination').text()).toContain('หน้า 1 จาก 1')
      expect(api.search.mock.calls).toHaveLength(requests)
      view.unmount()
    })

    it('distinguishes initial loading from an empty schedule', async () => {
      let finish!: (value: { items: never[]; totalPages: number }) => void
      api.search.mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      )
      const view = await render()
      await flushPromises()
      expect(view.get('[role="status"]').text()).toContain('กำลังโหลดนัดหมาย')
      expect(view.find('.appointment-empty').exists()).toBe(false)
      finish({ items: [], totalPages: 0 })
      await flushPromises()
      expect(view.get('.appointment-list-feedback').text()).toContain('ยังไม่มีนัดหมาย')
      view.unmount()
    })

    it('lets staff retry a failed list request and clears the recovered error', async () => {
      api.search
        .mockRejectedValueOnce(new Error('network error'))
        .mockResolvedValue({ items: [record(1)], totalPages: 1 })
      const view = await render()
      await flushPromises()
      expect(view.get('[role="alert"]').text()).toContain('โหลดนัดหมายไม่สำเร็จ')
      await view.get('.appointment-retry').trigger('click')
      await flushPromises()
      expect(view.find('[role="alert"]').exists()).toBe(false)
      expect(view.get('tbody').text()).toContain('Patient 1')
      view.unmount()
    })
  })
}
