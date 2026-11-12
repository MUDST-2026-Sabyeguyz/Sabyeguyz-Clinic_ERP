import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppointmentView from '../views/AppointmentView.vue'
import ManagerAppointmentView from '../views/ManagerAppointmentView.vue'
import type { Appointment } from '@/api/client'

const api = vi.hoisted(() => ({
  search: vi.fn(),
  create: vi.fn(),
  reschedule: vi.fn(),
  availability: vi.fn(),
  cancel: vi.fn(),
}))
vi.mock('@/api/client', () => ({
  getToken: () => null,
  appointmentApi: api,
  branchApi: { list: vi.fn(async () => [{ code: '01', name: 'Test branch', active: true }]) },
  patientApi: {
    searchAll: vi.fn(async () => [{ id: 9, hn: 'TEST009', fullName: 'Test patient' }]),
  },
  catalogApi: {
    services: vi.fn(async () => [{ id: 7, name: 'Test service', durationMinutes: 30 }]),
    courses: vi.fn(async () => []),
  },
  staffApi: { list: vi.fn(async () => [{ id: 3, userId: 1 }]) },
  courseApi: { patientCourses: vi.fn(async () => []) },
}))
vi.mock('../therapists', () => ({
  loadAppointmentTherapists: vi.fn(async () => [
    { id: 3, branchCode: '01', fullName: 'Test therapist' },
  ]),
}))

const appointment = {
  id: 55,
  branchCode: '01',
  patientType: 'CURRENT',
  status: 'UNCONFIRMED',
  patient: { id: 9, hn: 'TEST009', fullName: 'Test patient', phone: '' },
  therapist: { id: 3, fullName: 'Test therapist' },
  service: { id: 7, name: 'Test service', durationMinutes: 30 },
  // Stored 105 minute interval crosses the UTC date boundary; template is only 30 minutes.
  startAt: '2030-01-07T18:30:00Z',
  endAt: '2030-01-07T20:15:00Z',
} as Appointment

type Form = {
  patientId: number
  therapistId: number
  serviceId: number
  branchCode: string
  date: string
  time: string
  note: string
}
type State = {
  requestStatusChange: (item: Appointment, status: string) => Promise<void>
  confirmCancellation: () => Promise<void>
  cancellationReason: string
  form: Form
  patientQuery: string
  serviceMode: string
  formServiceModel: string
  appointmentPatientType: string
  newPatientForm: { firstName: string; lastName: string; nickname: string; phone: string }
  openCreate: () => void
  openEdit: (item: Appointment) => void
  save: () => Promise<void>
  requestSaveConfirmation: () => Promise<void>
  errorMessage: string
  pendingSaveConfirmation: boolean
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.setItem('authUser', JSON.stringify({ id: 1, staffId: 3, role: 'THERAPIST', branchCode: '01' }))
  api.search.mockResolvedValue({ items: [], totalPages: 0 })
  api.create.mockResolvedValue(appointment)
  api.reschedule.mockResolvedValue(appointment)
  api.cancel.mockResolvedValue({ ...appointment, status: 'CANCELLED' })
  api.availability.mockResolvedValue({ slots: [{ time: '01:30', available: true }] })
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
})

for (const [name, Component] of [
  ['staff', AppointmentView],
  ['manager', ManagerAppointmentView],
] as const) {
  describe(`${name} appointment interval form`, () => {
    async function render() {
      if (name === 'manager')
        localStorage.setItem('authUser', JSON.stringify({ id: 1, role: 'MANAGER' }))
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [
          { path: '/staff/appointments', name: 'staff-appointments', component: Component },
          { path: '/services', name: 'staff-services', component: { template: '<div />' } },
          { path: '/register', name: 'patient-registration', component: { template: '<div />' } },
        ],
      })
      await router.push('/staff/appointments')
      const view = mount(Component, {
        global: {
          plugins: [router],
          stubs: {
            StaffHeader: true,
            ThaiDatePicker: true,
            AppointmentDateFilter: true,
            FilterDropdown: true,
            GoogleCalendarPanel: true,
            AppointmentProcessActions: true,
          },
        },
      })
      await flushPromises()
      const state = view.vm as unknown as State
      return { view, state, router }
    }

    it('opens CURRENT services without changing status', async () => {
      const { view, state, router } = await render()
      await state.requestStatusChange(appointment, 'CONFIRMED')
      await flushPromises()
      expect(router.currentRoute.value.name).toBe('staff-services')
      expect(router.currentRoute.value.query).toEqual({ patientId: '9', appointmentId: '55' })
      expect(appointment.status).toBe('UNCONFIRMED')
      view.unmount()
    })
    it('cancels only the appointment with the supplied reason', async () => {
      const { view, state } = await render()
      await state.requestStatusChange({ ...appointment }, 'CANCELLED')
      state.cancellationReason = 'Patient requested cancellation'
      await state.confirmCancellation()
      expect(api.cancel).toHaveBeenCalledWith(55, 'Patient requested cancellation')
      view.unmount()
    })

    it('lets backend availability decide Sunday booking instead of blocking the weekday', async () => {
      const { view, state } = await render()
      state.openEdit({ ...appointment, startAt: '2030-01-06T02:00:00Z', endAt: '2030-01-06T03:00:00Z' })
      await flushPromises()
      await state.save()
      expect(api.reschedule).toHaveBeenCalledWith(55, expect.objectContaining({
        startAt: '2030-01-06T09:00:00+07:00', endAt: '2030-01-06T10:00:00+07:00',
      }))
      view.unmount()
    })

    it('opens NEW registration and blocks cancelled appointments', async () => {
      const { view, state, router } = await render()
      await state.requestStatusChange({ ...appointment, status: 'CANCELLED' }, 'CONFIRMED')
      await flushPromises()
      expect(router.currentRoute.value.name).toBe('staff-appointments')
      await state.requestStatusChange({ ...appointment, patientType: 'NEW' }, 'CONFIRMED')
      await flushPromises()
      expect(router.currentRoute.value.name).toBe('patient-registration')
      expect(router.currentRoute.value.query.appointmentId).toBe('55')
      view.unmount()
    })

    it('opens the stored start/end in Bangkok and sends the same interval on PATCH', async () => {
      const { view, state } = await render()
      state.openEdit(appointment)
      await flushPromises()
      expect(state.form).toMatchObject({ date: '2030-01-08', time: '01:30' })
      expect(view.find('input[aria-label="เวลาจบนัด"]').exists()).toBe(false)
      expect(
        view
          .findAllComponents({ name: 'ThaiDatePicker' })
          .some(
            (picker) => picker.props('modelValue') === '2030-01-08' && picker.props('minDate'),
          ),
      ).toBe(true)
      await state.save()
      expect(api.reschedule).toHaveBeenCalledWith(
        55,
        expect.objectContaining({
          startAt: '2030-01-08T01:30:00+07:00',
          endAt: '2030-01-08T03:15:00+07:00',
        }),
      )
      view.unmount()
    })

    it('preserves a stored end on the following clinic day', async () => {
      const { view, state } = await render()
      state.openEdit({
        ...appointment,
        startAt: '2030-01-08T16:30:00Z',
        endAt: '2030-01-08T17:30:00Z',
      })
      await flushPromises()
      expect(state.form).toMatchObject({ date: '2030-01-08', time: '23:30' })
      await state.save()
      expect(api.reschedule).toHaveBeenCalledWith(
        55,
        expect.objectContaining({
          startAt: '2030-01-08T23:30:00+07:00',
          endAt: '2030-01-09T00:30:00+07:00',
        }),
      )
      view.unmount()
    })

    it.each(['CURRENT', 'NEW'])('derives the end from the service duration on %s create', async (patientType) => {
      const { view, state } = await render()
      state.openCreate()
      await flushPromises()
      state.serviceMode = 'single'
      state.appointmentPatientType = patientType
      state.patientQuery = 'TEST009'
      state.newPatientForm = { firstName: '', lastName: '', nickname: 'Test', phone: '0812345678' }
      Object.assign(state.form, {
        patientId: 9,
        therapistId: 3,
        branchCode: '01',
        date: '2030-01-08',
        time: '01:30',
      })
      state.formServiceModel = '7'
      await nextTick()
      await state.save()
      expect(api.create).toHaveBeenCalledWith(
        expect.objectContaining({
          startAt: '2030-01-08T01:30:00+07:00',
          endAt: '2030-01-08T02:00:00+07:00',
        }),
      )
      view.unmount()
    })

    it('uses the backend-resolved branch duration when the service has no explicit duration', async () => {
      const { catalogApi } = await import('@/api/client')
      vi.mocked(catalogApi.services).mockResolvedValueOnce([
        { id: 7, name: 'Service using branch duration', durationMinutes: null },
      ] as never)
      api.availability.mockResolvedValue({
        durationMinutes: 45,
        slots: [{ time: '01:30', available: true }],
      })
      const { view, state } = await render()
      state.openCreate()
      await flushPromises()
      state.serviceMode = 'single'
      state.appointmentPatientType = 'CURRENT'
      state.patientQuery = 'TEST009'
      Object.assign(state.form, {
        patientId: 9, therapistId: 3, branchCode: '01', date: '2030-01-08', time: '01:30',
      })
      state.formServiceModel = '7'
      await nextTick()
      await state.requestSaveConfirmation()
      expect(state.pendingSaveConfirmation).toBe(true)
      await state.save()
      expect(api.create).toHaveBeenCalledWith(expect.objectContaining({
        startAt: '2030-01-08T01:30:00+07:00',
        endAt: '2030-01-08T02:15:00+07:00',
      }))
      view.unmount()
    })
    it('does not offer a separate end time field', async () => {
      const { view, state } = await render()
      state.openEdit(appointment)
      await flushPromises()
      expect(view.find('input[aria-label="เวลาจบนัด"]').exists()).toBe(false)
      view.unmount()
    })

    it('checks availability and keeps the stored duration when only the start time changes', async () => {
      api.availability.mockResolvedValue({ slots: [{ time: '02:00', available: true }] })
      const { view, state } = await render()
      state.openEdit(appointment)
      await flushPromises()
      state.form.time = '02:00'
      await state.requestSaveConfirmation()
      expect(api.availability).toHaveBeenCalledWith(
        expect.objectContaining({ excludeAppointmentId: 55 }),
      )
      expect(api.reschedule).toHaveBeenCalledWith(
        55,
        expect.objectContaining({
          startAt: '2030-01-08T02:00:00+07:00',
          endAt: '2030-01-08T03:45:00+07:00',
        }),
      )
      view.unmount()
    })
  })
}
