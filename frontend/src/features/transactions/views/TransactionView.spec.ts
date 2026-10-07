import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { route, appointmentGet, patientGet, serviceList } = vi.hoisted(() => ({
  route: { query: { appointmentId: '70' } },
  appointmentGet: vi.fn(), patientGet: vi.fn(), serviceList: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => route, RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('@/api/client', () => ({
  appointmentApi: { get: appointmentGet },
  patientApi: { get: patientGet },
  catalogApi: { services: serviceList },
  courseApi: { transferHistory: vi.fn().mockResolvedValue([]) },
  salesApi: { list: vi.fn().mockResolvedValue({ items: [] }), reverse: vi.fn() },
}))
import TransactionView from './TransactionView.vue'

enableAutoUnmount(afterEach)
describe('appointment transaction handoff', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = { appointmentId: '70' }
    appointmentGet.mockResolvedValue({ id: 70, status: 'CONFIRMED', patient: { id: 31 }, service: { id: 12 } })
    patientGet.mockResolvedValue({ id: 31, hn: '2601100031', fullName: 'Handoff Patient', phone: '0800000031', active: true })
    serviceList.mockResolvedValue([{ id: 12, code: 'VISIT', name: 'Single Visit', price: 700 }])
  })

  it('verifies appointment, patient, and service before exposing the service-entry handoff', async () => {
    const wrapper = mount(TransactionView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    expect(appointmentGet).toHaveBeenCalledWith(70)
    expect(patientGet).toHaveBeenCalledWith(31)
    expect(wrapper.text()).toContain('Handoff Patient')
    expect(wrapper.text()).toContain('Single Visit')
    expect(wrapper.text()).toContain('ตรวจข้อมูลและไปหน้าบริการ')
  })

  it('does not expose a handoff when appointment access fails', async () => {
    appointmentGet.mockRejectedValue(new Error('appointment unavailable'))
    const wrapper = mount(TransactionView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    expect(patientGet).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('appointment unavailable')
    expect(wrapper.find('.appointment-service-handoff').exists()).toBe(false)
  })
})
