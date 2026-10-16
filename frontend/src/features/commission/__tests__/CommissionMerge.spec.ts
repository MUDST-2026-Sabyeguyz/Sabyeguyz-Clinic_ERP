import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { commissionApi, staffApi } from '@/api/client'
import CommissionClosingView from '../views/CommissionClosingView.vue'
import CommissionPersonDetailView from '../views/CommissionPersonDetailView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { staffId: '7' }, query: { month: '2026-10' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

const stubs = { StaffHeader: true, CommissionTabs: true, FilterDropdown: true }
const schemes = [
  { id: 8, version: 2, name: 'Next version', effectiveFrom: '2026-11-01', effectiveTo: null },
  { id: 9, version: 1, name: 'October rule', effectiveFrom: '2026-01-01', effectiveTo: '2026-10-31' },
]

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-16T12:00:00+07:00'))
  vi.spyOn(commissionApi, 'schemes').mockResolvedValue(schemes)
  vi.spyOn(commissionApi, 'tiers').mockResolvedValue([
    { minimum: 0, maximum: null, rate: 0.1, order: 1 },
  ])
  vi.spyOn(commissionApi, 'closings').mockResolvedValue([])
  vi.spyOn(commissionApi, 'report').mockResolvedValue([
    { staffId: 7, staffName: 'Therapist', branchCode: '01', branchName: 'Clinic', saleCount: 1, base: 1000, rate: 0.1, commission: 100 },
  ])
  vi.spyOn(commissionApi, 'treatmentReport').mockResolvedValue([])
  vi.spyOn(staffApi, 'get').mockResolvedValue({ id: 7, userId: 7, username: 'therapist', role: 'THERAPIST', employeeCode: 'PT7', fullName: 'Therapist', active: true })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

it('allows closing using the dated settings rule, including its last day, without an active field', async () => {
  const wrapper = mount(CommissionClosingView, { global: { stubs } })
  try {
    await flushPromises()
    expect(commissionApi.tiers).toHaveBeenCalledWith(9)
    const close = wrapper.findAll('button').find((button) => button.text() === 'ปิดรอบ')!
    expect(close.attributes('disabled')).toBeUndefined()
  } finally { wrapper.unmount() }
})

it('loads the dated tier for staff details before a closing exists', async () => {
  const wrapper = mount(CommissionPersonDetailView, { global: { stubs } })
  try {
    await flushPromises()
    expect(commissionApi.tiers).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('Tier 1')
  } finally { wrapper.unmount() }
})

it('matches numeric scheme versions to the string version returned in closing history', async () => {
  vi.mocked(commissionApi.closings).mockResolvedValue([
    { id: 1, month: '2026-10', employeeId: 7, employeeName: 'Therapist', sales: 1000, version: '1', rate: 0.1, status: 'CLOSED' },
  ])
  const wrapper = mount(CommissionPersonDetailView, { global: { stubs } })
  try {
    await flushPromises()
    expect(commissionApi.tiers).toHaveBeenCalledWith(9)
  } finally { wrapper.unmount() }
})
