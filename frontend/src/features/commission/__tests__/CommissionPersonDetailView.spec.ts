import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import {
  clearSelectedManagerBranch,
  setManagerBranches,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'
import CommissionPersonDetailView from '../views/CommissionPersonDetailView.vue'

const mocks = vi.hoisted(() => ({
  report: vi.fn(),
  treatmentReport: vi.fn(),
  closings: vi.fn(),
  payments: vi.fn(),
  adjustments: vi.fn(),
  singleVisitFees: vi.fn(),
  schemes: vi.fn(),
  tiers: vi.fn(),
  reopenClosing: vi.fn(),
  staffGet: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { staffId: '1' }, query: { month: '2026-11' } }),
}))
vi.mock('@/api/client', () => ({
  commissionApi: {
    report: mocks.report,
    treatmentReport: mocks.treatmentReport,
    closings: mocks.closings,
    payments: mocks.payments,
    adjustments: mocks.adjustments,
    singleVisitFees: mocks.singleVisitFees,
    schemes: mocks.schemes,
    tiers: mocks.tiers,
    reopenClosing: mocks.reopenClosing,
  },
  staffApi: { get: mocks.staffGet },
}))
const mountView = () =>
  mount(CommissionPersonDetailView, {
    global: {
      stubs: {
        StaffHeader: true,
        // แสดงปลายทางของลิงก์เป็น JSON ใน data-to เพื่อให้เทสต์อ่านได้
        RouterLink: {
          props: ['to'],
          render() {
            return h('a', { 'data-to': JSON.stringify((this as unknown as { to: unknown }).to) }, (this as unknown as { $slots: { default?: () => unknown } }).$slots.default?.() as never)
          },
        },
      },
    },
  })

const sale = (id: number, saleDate: string, courseName: string) => ({
  id,
  saleDate,
  receiptNo: `RE${id}`,
  patientName: 'ผู้ป่วย A',
  courseName,
  gross: 10000,
  lineDiscount: 0,
  commissionableAmount: 10000,
  sellerName: 'Therapist Demo',
  completedRefund: 0,
})

beforeEach(() => {
  vi.resetAllMocks()
  clearSelectedManagerBranch()
  setManagerBranches([{ code: '01', name: 'พระราม 9' }])
  setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
  mocks.report.mockResolvedValue([
    {
      staffId: 1,
      staffName: 'Therapist Demo',
      branchCode: '01',
      branchName: 'พระราม 9',
      saleCount: 2,
      base: 20000,
      rate: 0.06,
      commission: 1200,
      sales: [sale(1, '2026-11-10', 'คอร์ส A'), sale(2, '2026-11-12', 'คอร์ส B')],
    },
  ])
  mocks.treatmentReport.mockResolvedValue([])
  mocks.closings.mockResolvedValue([{ id: 7, employeeId: 1, status: 'OPEN' }])
  mocks.payments.mockResolvedValue([])
  mocks.adjustments.mockResolvedValue([])
  mocks.singleVisitFees.mockResolvedValue([
    {
      id: 5,
      visitDate: '2026-11-11',
      patientName: 'ผู้ป่วย B',
      serviceName: 'บริการรายครั้ง',
      treatingStaffName: 'Therapist Demo',
      feeRuleName: null,
      treatmentFee: 45,
      receiptNo: 'RE77',
    },
  ])
  mocks.schemes.mockResolvedValue([])
  mocks.tiers.mockResolvedValue([])
  mocks.staffGet.mockResolvedValue({ fullName: 'Therapist Demo', employeeCode: 'THP-001' })
})

describe('person commission detail', () => {
  it('does not claim the person is closed while the round is still open', async () => {
    const view = mountView()
    await flushPromises()
    expect(view.text()).not.toContain('ปิดยอดรายบุคคลแล้ว')
    expect(view.text()).not.toContain('เหตุผลที่เปิดยอดอีกครั้ง')
    expect(view.find('textarea').exists()).toBe(false)
    view.unmount()
  })

  it('offers reopen only once the person is closed', async () => {
    mocks.closings.mockResolvedValue([{ id: 7, employeeId: 1, status: 'CLOSED' }])
    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('ปิดยอดรายบุคคลแล้ว')
    expect(view.find('textarea').exists()).toBe(true)
    view.unmount()
  })

  it('shows no closed/permanent note for a paid period', async () => {
    mocks.payments.mockResolvedValue([{ id: 1, staffId: 1, staffName: 'Therapist Demo', status: 'PAID' }])
    const view = mountView()
    await flushPromises()
    expect(view.text()).not.toContain('ปิดยอดรายบุคคลแล้ว')
    expect(view.text()).not.toContain('ปิดแก้ไขถาวร')
    expect(view.find('textarea').exists()).toBe(false)
    view.unmount()
  })

  it('puts "รายการทั้งหมด" before the course sales tab and lists every kind of row', async () => {
    const view = mountView()
    await flushPromises()
    const tabs = view.findAll('[role="tab"]').map((tab) => tab.text())
    expect(tabs).toEqual(['รายการทั้งหมด 3', 'รายการขายคอร์ส 2', 'รายการค่ามือ 1', 'รายการปรับยอด 0'])
    expect(view.get('[role="tab"][aria-selected="true"]').text()).toContain('รายการทั้งหมด')
    const rows = view.get('[data-test="all-rows"] tbody').findAll('tr').map((row) => row.text())
    expect(rows).toHaveLength(3)
    expect(rows[0]).toContain('คอร์ส B') // newest first
    expect(rows[1]).toContain('ค่ามือ')
    expect(rows[2]).toContain('คอร์ส A')
    view.unmount()
  })
})

describe('"แก้ไข" links', () => {
  const targets = (view: ReturnType<typeof mountView>) =>
    view.findAll('[data-test="edit-link"]').map((link) => JSON.parse(link.attributes('data-to') ?? '{}'))
  const treatment = {
    id: 9,
    visitDate: '2026-11-12',
    patientName: 'ผู้ป่วย C',
    serviceName: 'ทำแทน',
    treatingStaffId: 1,
    ownerStaffId: 3,
    branchCode: '01',
    treatmentFee: 45,
  }
  const adjustment = {
    id: 21,
    staffId: 1,
    branchCode: '01',
    appliedMonth: '2026-11',
    applications: [],
    reason: 'แก้ยอด',
    approverName: 'Manager Demo',
    amount: -100,
  }

  it('links each kind of row to its source screen', async () => {
    mocks.treatmentReport.mockResolvedValue([treatment])
    mocks.adjustments.mockResolvedValue([adjustment])
    const view = mountView()
    await flushPromises()
    const found = targets(view)
    // 2 ขายคอร์ส + 1 ค่ามือจากคอร์ส + 1 ค่ามือรายครั้ง (ใบเสร็จ RE77) + 1 ปรับยอด
    expect(found).toHaveLength(5)
    expect(found).toContainEqual({ path: '/staff/transactions', query: { tab: 'sales', search: 'RE77' } })
    expect(found).toContainEqual({ path: '/staff/transactions', query: { tab: 'sales', search: 'RE1' } })
    expect(found).toContainEqual({ path: '/staff/transactions', query: { usageId: '9' } })
    expect(found).toContainEqual({ path: '/staff/commission/adjustments/21' })
    view.unmount()
  })

  it('gives a single-visit fee without a receipt (prepaid right) no link', async () => {
    mocks.singleVisitFees.mockResolvedValue([
      {
        id: 5,
        visitDate: '2026-11-11',
        patientName: 'ผู้ป่วย B',
        serviceName: 'บริการรายครั้ง',
        treatingStaffName: 'Therapist Demo',
        feeRuleName: null,
        treatmentFee: 45,
        receiptNo: null,
      },
    ])
    const view = mountView()
    await flushPromises()
    expect(targets(view)).toHaveLength(2) // เหลือแค่ 2 ขายคอร์ส
    view.unmount()
  })

  it('disables source links until the person is reopened, but keeps adjustments editable', async () => {
    mocks.treatmentReport.mockResolvedValue([treatment])
    mocks.adjustments.mockResolvedValue([adjustment])
    mocks.closings.mockResolvedValue([{ id: 7, employeeId: 1, status: 'CLOSED' }])
    const view = mountView()
    await flushPromises()
    expect(view.findAll('[data-test="edit-disabled"]')).toHaveLength(4)
    expect(view.get('[data-test="edit-disabled"]').attributes('title')).toContain('เปิดรอบอีกครั้ง')
    expect(targets(view)).toEqual([{ path: '/staff/commission/adjustments/21' }])
    view.unmount()
  })

  it('says a paid period is closed for good', async () => {
    mocks.payments.mockResolvedValue([{ id: 1, staffId: 1, staffName: 'Therapist Demo', status: 'PAID' }])
    const view = mountView()
    await flushPromises()
    expect(view.get('[data-test="edit-disabled"]').attributes('title')).toContain('ปิดถาวร')
    view.unmount()
  })
})
