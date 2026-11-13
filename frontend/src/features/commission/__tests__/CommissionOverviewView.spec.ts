import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import {
  clearSelectedManagerBranch,
  setManagerBranches,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'
import CommissionOverviewView from '../views/CommissionOverviewView.vue'

const mocks = vi.hoisted(() => ({
  report: vi.fn(),
  closings: vi.fn(),
  payments: vi.fn(),
  staff: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { month: '2026-07' } }) }))
vi.mock('@/api/client', () => ({
  commissionApi: { report: mocks.report, closings: mocks.closings, payments: mocks.payments },
  staffApi: { list: mocks.staff },
}))
const mountView = () =>
  mount(CommissionOverviewView, {
    global: {
      stubs: {
        StaffHeader: true,
        CommissionTabs: true,
        MonthFilterPicker: true,
        FilterDropdown: true,
        RouterLink: true,
      },
    },
  })
beforeEach(() => {
  vi.resetAllMocks()
  clearSelectedManagerBranch()
  setManagerBranches([
    { code: '01', name: 'พระราม 9' },
    { code: '02', name: 'แบริ่ง' },
  ])
  mocks.report.mockResolvedValue([
    {
      staffId: 1,
      staffName: 'Rama therapist',
      branchCode: '01',
      base: 65000,
      rate: 0.06,
      commission: 3900,
    },
    {
      staffId: 2,
      staffName: 'Bearing therapist',
      branchCode: '02',
      base: 50000,
      rate: 0.05,
      commission: 2500,
    },
  ])
  mocks.closings.mockResolvedValue([])
  mocks.staff.mockResolvedValue([
    { id: 1, fullName: 'Rama therapist', branchCode: '01' },
    { id: 2, fullName: 'Bearing therapist', branchCode: '02' },
    { id: 3, fullName: 'Fee only therapist', branchCode: '02' },
  ])
  mocks.payments.mockResolvedValue([
    { staffId: 1, staffName: 'Rama therapist', singleVisitFee: 135, substituteFee: 45 },
    { staffId: 2, staffName: 'Bearing therapist', singleVisitFee: 90, substituteFee: 45 },
  ])
})
describe('manager commission overview', () => {
  it('reactively filters staff and summary amounts when the Header branch changes', async () => {
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Rama therapist')
    expect(view.get('tbody').text()).not.toContain('Bearing therapist')
    expect(view.findAll('.commission-metrics article')[2]!.text()).toContain('฿180')
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    await nextTick()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Bearing therapist')
    expect(view.get('tbody').text()).not.toContain('Rama therapist')
    expect(view.findAll('.commission-metrics article')[2]!.text()).toContain('฿135')
    expect(mocks.payments).toHaveBeenCalledWith('2026-07', '01')
    expect(mocks.payments).toHaveBeenCalledWith('2026-07', '02')
    view.unmount()
  })
  it('does not replace a missing payroll fee snapshot with zero', async () => {
    mocks.payments.mockResolvedValue([])
    const view = mountView()
    await flushPromises()
    expect(view.findAll('.commission-metrics article')[2]!.get('b').text()).toBe('—')
    expect(view.text()).toContain('ยังไม่มีข้อมูลสรุปค่ามือ')
    view.unmount()
  })
  it('shows a real zero fee snapshot as zero', async () => {
    mocks.payments.mockResolvedValue([
      { staffId: 1, staffName: 'Rama therapist', singleVisitFee: 0, substituteFee: 0 },
    ])
    const view = mountView()
    await flushPromises()
    expect(view.findAll('.commission-metrics article')[2]!.get('b').text()).toBe('฿0')
    view.unmount()
  })
  it('includes fee-only staff in their home branch and excludes paid payroll from pending money', async () => {
    mocks.payments.mockResolvedValue([
      {
        staffId: 3,
        staffName: 'Fee only therapist',
        singleVisitFee: 90,
        substituteFee: 45,
        status: 'PAID',
        net: 135,
      },
    ])
    mocks.report.mockResolvedValue([])
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Fee only therapist')
    expect(view.findAll('.commission-metrics article')[2]!.get('b').text()).toBe('฿135')
    expect(view.findAll('.commission-metrics article')[3]!.get('b').text()).toBe('฿0')
    view.unmount()
  })
  it('does not expose all branches when no Header branch is selected', async () => {
    clearSelectedManagerBranch()
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').text()).not.toContain('Rama therapist')
    expect(view.get('tbody').text()).not.toContain('Bearing therapist')
    view.unmount()
  })
})
