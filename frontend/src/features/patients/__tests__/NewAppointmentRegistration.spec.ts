import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Staff from '../views/PatientRegistrationView.vue'
import Manager from '../views/ManagerPatientRegistrationView.vue'

const mock = vi.hoisted(() => ({ get: vi.fn(), push: vi.fn(), link: vi.fn() }))
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn(),
  useRoute: () => ({ query: { appointmentId: '55' } }),
  useRouter: () => ({ push: mock.push }),
}))
vi.mock('@/api/client', () => ({
  appointmentApi: { get: mock.get, linkPatient: mock.link },
  branchApi: { list: async () => [{ code: '01', name: 'Test', active: true }] },
  masterDataApi: { list: async () => [{ active: true, category: 'Customer Group', name: 'Test' }] },
  patientApi: { searchAll: async () => [] },
}))
vi.mock('@/api/patients', () => ({ registerPatient: vi.fn() }))
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST', branchCode: '01' }))
  mock.get.mockResolvedValue({
    id: 55,
    patientType: 'NEW',
    status: 'UNCONFIRMED',
    branchCode: '01',
    contactName: 'Test',
    contactPhone: '0812345678',
    startAt: '2030-01-08T09:00:00+07:00',
  })
})
for (const [role, Component] of [
  ['staff', Staff],
  ['manager', Manager],
] as const) {
  describe(`${role} NEW appointment registration`, () => {
    async function render() {
      const view = mount(Component, {
        global: {
          stubs: {
            StaffHeader: true,
            RouterLink: true,
            ThaiDatePicker: true,
            FilterDropdown: true,
            Teleport: true,
          },
        },
      })
      await flushPromises()
      const state = view.vm as unknown as {
        sourceError: string
        continueToServices: (id: number) => Promise<boolean>
      }
      return { view, state }
    }
    it('accepts UNCONFIRMED NEW and forwards actual patient ID without linking', async () => {
      const { view, state } = await render()
      expect(state.sourceError).toBe('')
      expect(await state.continueToServices(987)).toBe(true)
      expect(mock.push).toHaveBeenCalledWith({
        name: 'staff-services',
        query: { patientId: '987', appointmentId: '55' },
      })
      expect(mock.link).not.toHaveBeenCalled()
      view.unmount()
    })
    it('blocks continuation if appointment was cancelled after registration', async () => {
      const { view, state } = await render()
      mock.get.mockResolvedValue({ patientType: 'NEW', status: 'CANCELLED' })
      expect(await state.continueToServices(987)).toBe(false)
      expect(mock.push).not.toHaveBeenCalled()
      expect(mock.link).not.toHaveBeenCalled()
      view.unmount()
    })
    it.each(['CANCELLED', 'CONFIRMED'])('rejects initial registration for %s', async (status) => {
      mock.get.mockResolvedValue({ id: 55, patientType: 'NEW', status, branchCode: '01' })
      const { view, state } = await render()
      expect(state.sourceError).not.toBe('')
      expect(mock.link).not.toHaveBeenCalled()
      view.unmount()
    })
  })
}
