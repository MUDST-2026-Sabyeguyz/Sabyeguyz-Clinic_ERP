import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clinicDate } from '@/utils/clinicDate'

const { revenue } = vi.hoisted(() => ({ revenue: vi.fn() }))
vi.mock('@/api/client', () => ({
  branchApi: { list: vi.fn().mockResolvedValue([]) },
  reportApi: { revenue }, commissionApi: {}, courseApi: {},
}))
import ReportsView from './ReportsView.vue'

enableAutoUnmount(afterEach)
describe('revenue report date input', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    revenue.mockResolvedValue({ totalRevenue: 3000, byCategory: [{ type: 'SINGLE_VISIT', amount: 3000, share: 1 }] })
  })

  it('clears the date without crashing or requesting an invalid range, then loads the corrected range', async () => {
    const wrapper = mount(ReportsView, { global: { stubs: { StaffHeader: true } } })
    await flushPromises()
    expect(revenue).toHaveBeenCalledTimes(1)
    const today = clinicDate()
    expect(revenue).toHaveBeenCalledWith(`${today.slice(0, 4)}-01-01`, today, undefined)
    const from = wrapper.findAll('input[type="date"]')[0]!
    await from.setValue('')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('เลือกช่วงวันที่ให้ถูกต้อง')
    expect(revenue).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('tbody tr').some((row) => row.text().includes('SINGLE_VISIT'))).toBe(false)
    await from.setValue(`${new Date().getFullYear()}-01-01`)
    await flushPromises()
    expect(revenue).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('tbody').text()).toContain('฿3,000')
  })

  it('does not restore an earlier result after the user clears the date while it is loading', async () => {
    let finishReport!: (value: { totalRevenue: number; byCategory: Array<{ type: string; amount: number; share: number }> }) => void
    revenue.mockImplementationOnce(() => new Promise((resolve) => { finishReport = resolve }))
    const wrapper = mount(ReportsView, { global: { stubs: { StaffHeader: true } } })
    await flushPromises()
    await wrapper.findAll('input[type="date"]')[0]!.setValue('')
    await flushPromises()
    finishReport({ totalRevenue: 3000, byCategory: [{ type: 'SINGLE_VISIT', amount: 3000, share: 1 }] })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('เลือกช่วงวันที่ให้ถูกต้อง')
    expect(wrapper.findAll('tbody tr').some((row) => row.text().includes('SINGLE_VISIT'))).toBe(false)
    expect(wrapper.text()).not.toContain('กำลังโหลดรายงาน...')
  })
})
