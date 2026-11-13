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
  branchClosing: vi.fn(),
  staff: vi.fn(),
  closingHistory: vi.fn(),
  closeMonth: vi.fn(),
  preparePayments: vi.fn(),
  closePayments: vi.fn(),
  pay: vi.fn(),
  reopenPayment: vi.fn(),
  reopenClosing: vi.fn(),
  reopenAllBranch: vi.fn(),
  feePreview: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { month: '2026-07' } }) }))
vi.mock('@/api/client', () => ({
  commissionApi: {
    report: mocks.report,
    closings: mocks.closings,
    payments: mocks.payments,
    branchClosing: mocks.branchClosing,
    closingHistory: mocks.closingHistory,
    closeMonth: mocks.closeMonth,
    preparePayments: mocks.preparePayments,
    closePayments: mocks.closePayments,
    pay: mocks.pay,
    reopenPayment: mocks.reopenPayment,
    reopenClosing: mocks.reopenClosing,
    reopenAllBranch: mocks.reopenAllBranch,
    feePreview: mocks.feePreview,
  },
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
const metric = (view: ReturnType<typeof mountView>, index: number) =>
  view.findAll('.commission-metrics article')[index]!.get('b').text()

/** Branch 01 with two people whose payments are Closed and whose rates are locked. */
function closedRound(paidStaffIds: number[] = []) {
  mocks.branchClosing.mockResolvedValue({ id: 5, branchCode: '01', month: '2026-07', status: 'CLOSED' })
  mocks.closings.mockResolvedValue([
    { id: 11, employeeId: 1, employeeName: 'Rama therapist', status: 'CLOSED' },
    { id: 12, employeeId: 4, employeeName: 'Second therapist', status: 'CLOSED' },
  ])
  mocks.report.mockResolvedValue([
    { staffId: 1, staffName: 'Rama therapist', branchCode: '01', base: 65000, rate: 0.06, commission: 3900 },
    { staffId: 4, staffName: 'Second therapist', branchCode: '01', base: 10000, rate: 0.05, commission: 500 },
  ])
  mocks.staff.mockResolvedValue([
    { id: 1, fullName: 'Rama therapist', branchCode: '01' },
    { id: 4, fullName: 'Second therapist', branchCode: '01' },
  ])
  mocks.payments.mockResolvedValue(
    [
      { id: 101, staffId: 1, staffName: 'Rama therapist', singleVisitFee: 135, substituteFee: 45, adjustment: 0, net: 4080 },
      { id: 104, staffId: 4, staffName: 'Second therapist', singleVisitFee: 0, substituteFee: 0, adjustment: 0, net: 500 },
    ].map((payment) => ({
      ...payment,
      status: paidStaffIds.includes(payment.staffId) ? 'PAID' : 'CLOSED',
    })),
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  clearSelectedManagerBranch()
  setManagerBranches([
    { code: '01', name: 'พระราม 9' },
    { code: '02', name: 'แบริ่ง' },
  ])
  setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
  mocks.report.mockResolvedValue([
    { staffId: 1, staffName: 'Rama therapist', branchCode: '01', base: 65000, rate: 0.06, commission: 3900 },
    { staffId: 2, staffName: 'Bearing therapist', branchCode: '02', base: 50000, rate: 0.05, commission: 2500 },
  ])
  mocks.closings.mockResolvedValue([])
  mocks.branchClosing.mockResolvedValue({ status: 'OPEN' })
  mocks.staff.mockResolvedValue([
    { id: 1, fullName: 'Rama therapist', branchCode: '01' },
    { id: 2, fullName: 'Bearing therapist', branchCode: '02' },
    { id: 3, fullName: 'Fee only therapist', branchCode: '02' },
  ])
  mocks.payments.mockResolvedValue([
    { staffId: 1, staffName: 'Rama therapist', singleVisitFee: 135, substituteFee: 45 },
    { staffId: 2, staffName: 'Bearing therapist', singleVisitFee: 90, substituteFee: 45 },
  ])
  mocks.branchClosing.mockResolvedValue({ id: null, branchCode: '01', month: '2026-07', status: 'OPEN' })
  mocks.closingHistory.mockResolvedValue([])
  mocks.closeMonth.mockResolvedValue([])
  mocks.preparePayments.mockResolvedValue([])
  mocks.closePayments.mockResolvedValue([])
  mocks.pay.mockResolvedValue({})
  mocks.reopenPayment.mockResolvedValue({})
  mocks.reopenClosing.mockResolvedValue({})
  mocks.reopenAllBranch.mockResolvedValue({})
  mocks.feePreview.mockResolvedValue([])
})
describe('manager commission overview', () => {
  it('reactively filters staff and summary amounts when the Header branch changes', async () => {
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Rama therapist')
    expect(view.get('tbody').text()).not.toContain('Bearing therapist')
    expect(metric(view, 1)).toBe('฿180')
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    await nextTick()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Bearing therapist')
    expect(view.get('tbody').text()).not.toContain('Rama therapist')
    expect(metric(view, 1)).toBe('฿135')
    expect(mocks.payments).toHaveBeenCalledWith('2026-07', '01')
    expect(mocks.payments).toHaveBeenCalledWith('2026-07', '02')
    view.unmount()
  })
  it('does not replace a missing payroll fee snapshot with zero', async () => {
    mocks.payments.mockResolvedValue([])
    const view = mountView()
    await flushPromises()
    expect(metric(view, 1)).toBe('—')
    view.unmount()
  })
  it('shows a real zero fee snapshot as zero', async () => {
    mocks.payments.mockResolvedValue([
      { staffId: 1, staffName: 'Rama therapist', singleVisitFee: 0, substituteFee: 0 },
    ])
    const view = mountView()
    await flushPromises()
    expect(metric(view, 1)).toBe('฿0')
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
    expect(metric(view, 1)).toBe('฿135')
    expect(metric(view, 3)).toBe('฿0')
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

describe('closing a round', () => {
  it('locks rates, prepares and closes the payments in that order, without paying anyone', async () => {
    const view = mountView()
    await flushPromises()
    await view.get('[data-test="close-round"]').trigger('click')
    await view.get('[data-test="confirm-close"]').trigger('click')
    await flushPromises()
    const order = (fn: ReturnType<typeof vi.fn>) => fn.mock.invocationCallOrder[0]!
    expect(mocks.closeMonth).toHaveBeenCalledWith('2026-07', '01')
    expect(order(mocks.closeMonth)).toBeLessThan(order(mocks.preparePayments))
    expect(order(mocks.preparePayments)).toBeLessThan(order(mocks.closePayments))
    expect(mocks.pay).not.toHaveBeenCalled()
    view.unmount()
  })
  it('names the step that failed and lets the Manager try again', async () => {
    mocks.preparePayments.mockRejectedValueOnce(new Error('งวดจ่ายผิดพลาด'))
    const view = mountView()
    await flushPromises()
    await view.get('[data-test="close-round"]').trigger('click')
    await view.get('[data-test="confirm-close"]').trigger('click')
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('เตรียมงวดจ่าย')
    expect(mocks.closePayments).not.toHaveBeenCalled()
    expect(view.find('[data-test="close-round"]').exists()).toBe(true)
    view.unmount()
  })
})

describe('treatment fees before the round is closed', () => {
  it('lists someone who only earned a treatment fee and adds it to the total', async () => {
    mocks.feePreview.mockResolvedValue([
      { staffId: 4, staffName: 'สบายกาย คลินิก', singleVisitFee: 0, substituteFee: 45 },
    ])
    mocks.staff.mockResolvedValue([{ id: 1, fullName: 'Rama therapist', branchCode: '01' }])
    const view = mountView()
    await flushPromises()
    expect(mocks.feePreview).toHaveBeenCalledWith('2026-07', '01')
    expect(view.get('tbody').text()).toContain('สบายกาย คลินิก')
    expect(view.get('tbody').text()).toContain('฿45')
    expect(metric(view, 1)).toBe('฿225') // 180 จากงวดจ่ายของ Rama + 45 ค่ามือที่ยังไม่ปิดรอบ
    view.unmount()
  })
  it('still shows the list when the fee preview cannot be loaded', async () => {
    mocks.feePreview.mockRejectedValue(new Error('boom'))
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').text()).toContain('Rama therapist')
    view.unmount()
  })
})

describe('awaiting payment card', () => {
  it('says the round is waiting to be closed while the amount is unknown, then shows the amount', async () => {
    const view = mountView()
    await flushPromises()
    expect(view.get('[data-test="awaiting-placeholder"]').text()).toBe('รอการปิดรอบ')
    view.unmount()
    closedRound()
    const closed = mountView()
    await flushPromises()
    expect(closed.find('[data-test="awaiting-placeholder"]').exists()).toBe(false)
    expect(closed.findAll('.commission-metrics article')[3]!.get('b').text()).toBe('฿4,580')
    closed.unmount()
  })
})

describe('filter bar', () => {
  it('shows the selected month and moves it with the arrow buttons', async () => {
    const view = mountView()
    await flushPromises()
    const before = view.get('[data-test="month-label"]').text()
    await view.get('[data-test="month-prev"]').trigger('click')
    await flushPromises()
    expect(view.get('[data-test="month-label"]').text()).not.toBe(before)
    expect(mocks.payments).toHaveBeenCalledWith('2026-06', '01')
    view.unmount()
  })
  it('has no status tabs, and shows a clearable chip for a typed name', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    expect(view.find('[data-test="status-tabs"]').exists()).toBe(false)
    expect(view.find('[data-test="active-filters"]').exists()).toBe(false)
    await view.get('input[type="search"]').setValue('Rama')
    expect(view.get('tbody').text()).toContain('Rama therapist')
    expect(view.get('tbody').text()).not.toContain('Second therapist')
    expect(view.get('[data-test="active-filters"]').text()).toContain('Rama')
    await view.get('[data-test="clear-filters"]').trigger('click')
    expect(view.get('tbody').text()).toContain('Second therapist')
    expect(view.find('[data-test="active-filters"]').exists()).toBe(false)
    view.unmount()
  })
  it('uses the new list heading without helper text', async () => {
    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('รายชื่อนักกายภาพ')
    expect(view.text()).not.toContain('รายชื่อผู้รับค่าตอบแทน')
    expect(view.text()).not.toContain('ยอดตามรอบและตัวกรองที่เลือก')
    expect(view.text()).not.toContain('อ่านอย่างเดียว กดแถวเพื่อดูลำดับเหตุการณ์')
    view.unmount()
  })
  it('explains an empty result caused by the filter and offers to clear it', async () => {
    const view = mountView()
    await flushPromises()
    await view.get('input[type="search"]').setValue('ไม่มีชื่อนี้')
    expect(view.get('.empty-row').text()).toContain('ไม่พบรายการที่ตรงกับตัวกรอง')
    await view.get('.empty-row .link-button').trigger('click')
    expect(view.get('tbody').text()).toContain('Rama therapist')
    view.unmount()
  })
})

describe('pagination', () => {
  const months = (count: number) =>
    Array.from({ length: count }, (_, index) => ({
      month: `${2020 + Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`,
      status: 'FINALIZED',
      closedBy: 'Manager',
      reopenCount: 0,
      lastPaidDate: null,
      paidTotal: 0,
      events: [],
    }))
  it('shows no pager for 20 history months or fewer', async () => {
    mocks.closingHistory.mockResolvedValue(months(20))
    const view = mountView()
    await flushPromises()
    expect(view.findAll('.history-row')).toHaveLength(20)
    expect(view.find('[data-test="history-pagination"]').exists()).toBe(false)
    view.unmount()
  })
  it('splits more than 20 history months into pages', async () => {
    mocks.closingHistory.mockResolvedValue(months(25))
    const view = mountView()
    await flushPromises()
    expect(view.findAll('.history-row')).toHaveLength(20)
    expect(view.get('[data-test="history-pagination"]').text()).toContain('หน้า 1 จาก 2')
    expect(view.get('[data-test="history-prev"]').attributes('disabled')).toBeDefined()
    await view.get('[data-test="history-next"]').trigger('click')
    expect(view.findAll('.history-row')).toHaveLength(5)
    expect(view.get('[data-test="history-pagination"]').text()).toContain('หน้า 2 จาก 2')
    expect(view.get('[data-test="history-next"]').attributes('disabled')).toBeDefined()
    view.unmount()
  })
  it('splits more than 20 therapists into pages', async () => {
    const people = Array.from({ length: 23 }, (_, i) => ({
      staffId: 100 + i, staffName: `Therapist ${i}`, branchCode: '01', base: 1000, rate: 0.05, commission: 50,
    }))
    mocks.report.mockResolvedValue(people)
    mocks.staff.mockResolvedValue(people.map((p) => ({ id: p.staffId, fullName: p.staffName, branchCode: '01' })))
    mocks.payments.mockResolvedValue([])
    const view = mountView()
    await flushPromises()
    expect(view.get('tbody').findAll('tr')).toHaveLength(20)
    await view.get('[data-test="list-next"]').trigger('click')
    expect(view.get('tbody').findAll('tr')).toHaveLength(3)
    await view.get('input[type="search"]').setValue('Therapist 1')
    expect(view.find('[data-test="list-pagination"]').exists()).toBe(false)
    view.unmount()
  })
})

describe('round progress bar', () => {
  const stageState = (view: ReturnType<typeof mountView>) =>
    view.findAll('[data-test="stage-row"] li').map((li) => `${li.text().replace(/^[✓\d]/, '')}:${li.classes().join('') || '-'}`)
  it('shows three steps and starts at เปิดรอบ before anyone is closed', async () => {
    const view = mountView()
    await flushPromises()
    expect(stageState(view)).toEqual(['เปิดรอบ:cur', 'ตรวจสอบ:-', 'ปิดรอบ:-'])
    view.unmount()
  })
  it('marks every step done once the whole round is closed', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    expect(stageState(view)).toEqual(['เปิดรอบ:done', 'ตรวจสอบ:done', 'ปิดรอบ:done'])
    view.unmount()
  })
})

describe('paying and reopening', () => {
  it('offers pay and reopen on Closed rows and a branch-wide reopen while nobody is paid', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    expect(view.findAll('[data-test="pay-row"]')).toHaveLength(2)
    expect(view.findAll('[data-test="reopen-row"]')).toHaveLength(2)
    expect(view.find('[data-test="reopen-all"]').exists()).toBe(true)
    expect(view.find('[data-test="close-round"]').exists()).toBe(false)
    view.unmount()
  })
  it('hides every Reopen once one person is Paid and has no pay-all action', async () => {
    closedRound([1])
    const view = mountView()
    await flushPromises()
    expect(view.findAll('[data-test="reopen-row"]')).toHaveLength(0)
    expect(view.find('[data-test="reopen-all"]').exists()).toBe(false)
    expect(view.findAll('[data-test="pay-row"]')).toHaveLength(1)
    expect(view.text()).not.toContain('จ่ายทั้งสาขา')
    view.unmount()
  })
  it('warns before the first payment and takes an optional reference', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    await view.findAll('[data-test="pay-row"]')[0]!.trigger('click')
    expect(view.find('[data-test="first-pay-warning"]').exists()).toBe(true)
    await view.get('[data-test="confirm-pay"]').trigger('click')
    await flushPromises()
    expect(mocks.pay).toHaveBeenCalledTimes(1)
    const [id, , reference] = mocks.pay.mock.calls[0]!
    expect(id).toBe(101)
    expect(reference).toBe('')
    view.unmount()
  })
  it('does not warn about a first payment once someone is already paid', async () => {
    closedRound([1])
    const view = mountView()
    await flushPromises()
    await view.get('[data-test="pay-row"]').trigger('click')
    expect(view.find('[data-test="first-pay-warning"]').exists()).toBe(false)
    view.unmount()
  })
  it('refuses a payment date in the future', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    await view.findAll('[data-test="pay-row"]')[0]!.trigger('click')
    await view.get('[data-test="paid-date"]').setValue('2999-01-01')
    await view.get('[data-test="confirm-pay"]').trigger('click')
    await flushPromises()
    expect(mocks.pay).not.toHaveBeenCalled()
    expect(view.text()).toContain('วันที่จ่ายต้องไม่เกิน')
    view.unmount()
  })
  it('needs a reason to reopen, then reopens the payment before the closing', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    await view.findAll('[data-test="reopen-row"]')[0]!.trigger('click')
    await view.get('[data-test="confirm-reopen"]').trigger('click')
    await flushPromises()
    expect(mocks.reopenPayment).not.toHaveBeenCalled()
    expect(view.text()).toContain('กรอกเหตุผลก่อนเปิดรอบอีกครั้ง')
    await view.get('[data-test="reopen-reason"]').setValue('บันทึกผู้รักษาแทนผิดคน')
    await view.get('[data-test="confirm-reopen"]').trigger('click')
    await flushPromises()
    expect(mocks.reopenPayment).toHaveBeenCalledWith(101, 'บันทึกผู้รักษาแทนผิดคน')
    expect(mocks.reopenClosing).toHaveBeenCalledWith(11, 'บันทึกผู้รักษาแทนผิดคน')
    expect(mocks.reopenPayment.mock.invocationCallOrder[0]!).toBeLessThan(
      mocks.reopenClosing.mock.invocationCallOrder[0]!,
    )
    view.unmount()
  })
  it('reopens the whole branch with one reason', async () => {
    closedRound()
    const view = mountView()
    await flushPromises()
    await view.get('[data-test="reopen-all"]').trigger('click')
    await view.get('[data-test="reopen-reason"]').setValue('ยอดขายคอร์สผิด')
    await view.get('[data-test="confirm-reopen"]').trigger('click')
    await flushPromises()
    expect(mocks.reopenAllBranch).toHaveBeenCalledWith('2026-07', '01', 'ยอดขายคอร์สผิด')
    view.unmount()
  })
})

describe('closing history', () => {
  it('lists the months and opens the timeline of one', async () => {
    mocks.closingHistory.mockResolvedValue([
      {
        month: '2026-06',
        status: 'FINALIZED',
        reopenCount: 1,
        closedBy: 'ผู้จัดการ สมชาย',
        closedAt: '2026-07-01T03:00:00Z',
        lastPaidDate: '2026-07-03',
        paidTotal: 9650,
        events: [
          { kind: 'close', title: 'ปิดรอบ', detail: null, reason: null, at: '2026-07-01T03:00:00Z', by: 'ผู้จัดการ สมชาย' },
          { kind: 'reopen', title: 'เปิดรอบอีกครั้งทั้งสาขา', detail: null, reason: 'ยอดขายขาดไป 1 รายการ', at: '2026-07-02T03:00:00Z', by: 'ผู้จัดการ สมชาย' },
        ],
      },
    ])
    const view = mountView()
    await flushPromises()
    const history = view.get('[data-test="closing-history"]')
    expect(history.text()).toContain('Finalized')
    expect(history.text()).toContain('1 ครั้ง')
    expect(history.text()).not.toContain('ยอดขายขาดไป 1 รายการ')
    await history.get('.history-row').trigger('click')
    expect(history.text()).toContain('เปิดรอบอีกครั้งทั้งสาขา')
    expect(history.text()).toContain('เหตุผล: ยอดขายขาดไป 1 รายการ')
    view.unmount()
  })
})
