import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { route, appointmentGet, patientGet, serviceList, branchList, saleCreate, saleGet, routerReplace } = vi.hoisted(() => ({
  route: { query: { appointmentId: '70' } },
  appointmentGet: vi.fn(), patientGet: vi.fn(), serviceList: vi.fn(), branchList: vi.fn(), saleCreate: vi.fn(), saleGet: vi.fn(), routerReplace: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace: routerReplace }), RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('@/api/client', () => ({
  appointmentApi: { get: appointmentGet },
  patientApi: { searchAll: vi.fn().mockResolvedValue([]), search: vi.fn(), get: patientGet },
  catalogApi: { services: serviceList, courses: vi.fn().mockResolvedValue([]) },
  branchApi: { list: branchList },
  courseApi: { patientCourses: vi.fn(), use: vi.fn() },
  paymentMethodApi: { list: vi.fn().mockResolvedValue([{ code: 'CASH', name: 'Cash' }]) },
  salesApi: { create: saleCreate, get: saleGet }, staffApi: { list: vi.fn().mockResolvedValue([]) },
}))
import ServicesView from './servicesView.vue'

enableAutoUnmount(afterEach)
describe('appointment to service handoff', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = { appointmentId: '70' }
    serviceList.mockResolvedValue([{ id: 12, code: 'VISIT', name: 'Single Visit', price: 700 }])
    branchList.mockResolvedValue([{ id: 85, code: '85', name: 'North', address: '-', phone: '-', active: true }, { id: 86, code: '86', name: 'South', address: '-', phone: '-', active: true }])
    appointmentGet.mockResolvedValue({ id: 70, branchCode: '85', patient: { id: 31 }, service: { id: 12 } })
    patientGet.mockResolvedValue({ id: 31, hn: '2601100031', fullName: 'Handoff Patient', phone: '0800000031', active: true })
    saleCreate.mockResolvedValue({ id: 1 })
    saleGet.mockResolvedValue({ id: 1, receiptNo: 'RE-0000001', patient: { fullName: 'Handoff Patient', hn: '2601100031' }, branchCode: '86', items: [], grossTotal: 0, lineDiscountTotal: 0, billDiscountAmount: 0, total: 0, paid: 0, commissionBase: 0 })
  })

  it('loads patient and service by the server-verified appointment identity on direct navigation', async () => {
    const wrapper = mount(ServicesView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    expect(appointmentGet).toHaveBeenCalledWith(70)
    expect(branchList).toHaveBeenCalledWith({ accessible: true })
    expect(patientGet).toHaveBeenCalledWith(31)
    expect(wrapper.text()).toContain('Handoff Patient')
    const selects = wrapper.findAll('select')
    expect(selects[0]?.element).toHaveProperty('value', 'single')
    expect(selects[1]?.element).toHaveProperty('value', '85')
    expect(wrapper.find('option[value="assessment"]').exists()).toBe(false)
    expect(selects[2]?.element).toHaveProperty('value', '12')
    await selects[1]?.setValue('86')
    await wrapper.findAll('button').find((button) => button.text().includes('ยืนยันรายการ'))?.trigger('click')
    await flushPromises()
    expect(saleCreate).toHaveBeenCalledWith(expect.objectContaining({ branchCode: '86', patientId: 31 }))
  })

  it('does not accept a patient from the route when appointment access fails', async () => {
    appointmentGet.mockRejectedValue(new Error('not found'))
    const wrapper = mount(ServicesView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    expect(patientGet).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('not found')
    expect(wrapper.find('.card').exists()).toBe(false)
  })

  it.each([
    ['line', 1500, { lineDiscountAmount: 500 }],
    ['bill', 2000, {}],
  ] as const)('submits the confirmed %s fixed discount with its separate commission base', async (kind, expectedBase, lineDiscount) => {
    serviceList.mockResolvedValue([{ id: 12, code: 'VISIT', name: 'Single Visit', price: 2000 }])
    saleGet.mockResolvedValue({ id: 1, receiptNo: 'RE-0000001', patient: { fullName: 'Handoff Patient', hn: '2601100031' }, branchCode: '85', items: [], grossTotal: 2000, lineDiscountTotal: kind === 'line' ? 500 : 0, billDiscountAmount: kind === 'bill' ? 500 : 0, total: 1500, paid: 1500, commissionBase: expectedBase })
    const wrapper = mount(ServicesView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    await wrapper.get('select[aria-label="ส่วนลดแบบบาท"]').setValue(kind)
    await wrapper.get('input[aria-label="จำนวนส่วนลดบาท"]').setValue('500')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(saleCreate).toHaveBeenCalledWith(expect.objectContaining({
      items: [expect.objectContaining({ ...lineDiscount })],
      billDiscountAmount: kind === 'bill' ? 500 : 0,
      payments: [expect.objectContaining({ amount: 1500 })],
    }))
    expect(saleGet).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain(`commission base ฿${expectedBase.toLocaleString()}`)
  })
})
