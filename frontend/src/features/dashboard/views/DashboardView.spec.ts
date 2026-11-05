import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import { clinicToday } from '@/utils/clinicDate'
import {
  clearSelectedManagerBranch,
  setManagerBranches,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'

const report = vi.hoisted(() => ({
  dashboard: vi.fn(async (_from: string, _to: string, _branch?: string) => ({
    uniquePatients: 0,
    visits: 0,
    totalRevenue: 0,
    patientsByGender: [],
    cashReceived: 0,
    salesTotal: 0,
    courseRenewals: 0,
    newCustomers: 0,
    returningCustomers: 0,
    packageUpdates: 0,
    packageUpdatesScope: 'CLINIC',
  })),
  appointments: vi.fn(async () => ({
    appointments: 0,
    byStatus: [] as Array<{ status: string; appointments: number }>,
  })),
  balances: vi.fn(async () => ({ purchases: 0 })),
  revenue: vi.fn(async () => ({
    dailyByCategory: [] as Array<{ date: string; type: string; amount: number }>,
  })),
}))
vi.mock('@/api/client', () => ({ reportApi: report }))

beforeEach(() => {
  vi.clearAllMocks()
  report.dashboard.mockImplementation(async () => ({
    uniquePatients: 0,
    visits: 0,
    totalRevenue: 0,
    patientsByGender: [],
    cashReceived: 0,
    salesTotal: 0,
    courseRenewals: 0,
    newCustomers: 0,
    returningCustomers: 0,
    packageUpdates: 0,
    packageUpdatesScope: 'CLINIC',
  }))
  clearSelectedManagerBranch()
})

async function mountSelectedDashboard() {
  setManagerBranches([{ code: '01', name: 'พระราม 9' }])
  setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
  const view = mount(DashboardView, {
    attachTo: document.body,
    global: { stubs: { StaffHeader: true, FilterDropdown: true, ThaiDatePicker: true } },
  })
  await flushPromises()
  return view
}

async function applyDateRange(view: VueWrapper, start: string, end: string) {
  await view.get('.custom-range-button').trigger('click')
  const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
  pickers[0]!.vm.$emit('update:modelValue', start)
  pickers[1]!.vm.$emit('update:modelValue', end)
  await flushPromises()
  await view.get('.dialog-submit').trigger('click')
  await flushPromises()
}

describe('branch-only Manager dashboard', () => {
  it('does not request all branches and follows Header selection without an overview button', async () => {
    clearSelectedManagerBranch()
    const view = mount(DashboardView, {
      global: { stubs: { StaffHeader: true, FilterDropdown: true, ThaiDatePicker: true } },
    })
    await flushPromises()
    expect(report.dashboard).not.toHaveBeenCalled()
    setManagerBranches([
      { code: '01', name: 'สาขาพระราม 9' },
      { code: '02', name: 'สาขาแบริ่ง' },
    ])
    setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
    await flushPromises()
    expect(report.dashboard).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), '01')
    expect(view.text()).not.toContain('ดูภาพรวมทุกสาขา')
    expect(view.get('.dashboard-heading p').text()).toBe('สาขาพระราม9')
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    await flushPromises()
    expect(report.dashboard).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), '02')
    expect(view.get('.dashboard-heading p').text()).toBe('สาขาแบริ่ง')
    await view.get('.custom-range-button').trigger('click')
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    pickers[0]!.vm.$emit('update:modelValue', '2999-12-31')
    await flushPromises()
    expect(view.get('.range-error').text()).toBe(
      'วันเริ่มต้นต้องเป็นวันเดียวกันกับวันสิ้นสุดหรือก่อนวันสิ้นสุด',
    )
    pickers[1]!.vm.$emit('update:modelValue', '2000-01-01')
    await flushPromises()
    expect(view.get('.range-error').text()).toBe(
      'วันสิ้นสุดต้องเป็นวันเดียวกันกับวันเริ่มต้นหรือหลังวันเริ่มต้น',
    )
    view.unmount()
  })

  it('keeps today figures independent from historical report filters and separates sales from receipts', async () => {
    report.dashboard.mockImplementation(async (from, to) => ({
      uniquePatients: from === to ? 3 : 20,
      visits: 30,
      totalRevenue: 9000,
      patientsByGender: [],
      cashReceived: 100,
      salesTotal: 250,
      courseRenewals: 2,
      newCustomers: from === to ? 1 : 4,
      returningCustomers: from === to ? 2 : 6,
      packageUpdates: from === to ? 5 : 8,
      packageUpdatesScope: 'CLINIC',
    }))
    const view = await mountSelectedDashboard()
    expect(view.get('.today-section').text()).toContain('3 คน')
    const todayCards = view.findAll('.today-card')
    expect(todayCards[1]!.text()).toContain('100 บาท')
    expect(todayCards[2]!.text()).toContain('250 บาท')
    expect(todayCards).toHaveLength(3)
    expect(view.get('.summary-layout').text()).toContain('20 คน')
    expect(view.get('.summary-layout').text()).toContain('ยอดรับชำระ')
    expect(view.get('.summary-layout').text()).toContain('ยอดขายตามวันที่ขาย')
    expect(view.get('.summary-layout').text()).toContain('ลูกค้าใหม่')
    expect(view.get('.summary-layout').text()).toContain('ปรับสิทธิ์คอร์ส (ทั้งคลินิก)')
    expect(view.get('.summary-layout').text()).toContain('คอร์สที่ซื้อ')
    await applyDateRange(view, '2025-01-01', '2025-01-31')
    expect(report.dashboard).toHaveBeenCalledWith('2025-01-01', '2025-01-31', '01')
    expect(view.get('.today-section').text()).toContain('3 คน')
    expect(view.findAll('.today-card')[1]!.text()).toContain('100 บาท')
    view.unmount()
  })

  it('refreshes today figures when returning to the dashboard', async () => {
    const view = await mountSelectedDashboard()
    report.dashboard.mockResolvedValue({
      uniquePatients: 7,
      visits: 0,
      totalRevenue: 1200,
      patientsByGender: [],
      cashReceived: 1200,
      salesTotal: 1500,
      courseRenewals: 0,
      newCustomers: 0,
      returningCustomers: 0,
      packageUpdates: 0,
      packageUpdatesScope: 'CLINIC',
    })
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    const cards = view.findAll('.today-card')
    expect(cards[0]!.text()).toContain('7 คน')
    expect(cards[1]!.text()).toContain('1,200 บาท')
    expect(cards[2]!.text()).toContain('1,500 บาท')
    view.unmount()
  })

  it('keeps date filters and retry available after a report fails', async () => {
    report.revenue.mockRejectedValueOnce(new Error('internal server detail'))
    const view = await mountSelectedDashboard()
    expect(view.get('[role="alert"]').text()).toContain('โหลดข้อมูลไม่สำเร็จ')
    expect(view.text()).not.toContain('internal server detail')
    expect(view.find('.custom-range-button').exists()).toBe(true)
    await view.get('.dashboard-retry').trigger('click')
    await flushPromises()
    expect(view.find('[role="alert"]').exists()).toBe(false)
    expect(view.find('.summary-layout').exists()).toBe(true)
    view.unmount()
  })

  it('shows supported appointment statuses without inferring no-show and exposes chart figures', async () => {
    report.appointments.mockResolvedValueOnce({
      appointments: 3,
      byStatus: [
        { status: 'Cancelled', appointments: 2 },
        { status: 'Confirmed', appointments: 1 },
      ],
    })
    report.revenue.mockResolvedValueOnce({
      dailyByCategory: [{ date: '2025-01-05', type: 'CoursePurchase', amount: 1234.5 }],
    })
    const view = await mountSelectedDashboard()
    expect(view.findAll('.status-row')).toHaveLength(3)
    expect(
      view
        .findAll('.status-row')
        .some((row) => row.text().includes('ยกเลิก') && row.text().includes('2')),
    ).toBe(true)
    expect(
      view
        .findAll('.status-row')
        .some((row) => row.text().includes('ยืนยันแล้ว') && row.text().includes('1')),
    ).toBe(true)
    expect(view.text()).not.toContain('ไม่มาตามนัด')
    report.revenue.mockResolvedValueOnce({
      dailyByCategory: [{ date: '2025-01-05', type: 'CoursePurchase', amount: 1234.5 }],
    })
    await applyDateRange(view, '2025-01-01', '2025-01-31')
    expect(view.get('.revenue-table').text()).toContain('1,234.5 บาท')
    view.unmount()
  })

  it('shows a known zero payment as zero and explains an empty revenue chart', async () => {
    const view = await mountSelectedDashboard()
    expect(view.findAll('.today-card')[1]!.text()).toContain('0 บาท')
    expect(view.findAll('.today-card')[1]!.text()).not.toContain('ยังไม่มีข้อมูล')
    expect(view.get('.revenue-card .empty-state').text()).toBe('ยังไม่มียอดรับชำระในช่วงนี้')
    expect(view.findAll('.status-row')).toHaveLength(1)
    expect(view.get('.status-row span').text()).toBe('ยังไม่ยืนยัน')
    expect(view.get('.status-row b').text()).toBe('0')
    expect(view.get('.status-card .empty-state').text()).toBe('ยังไม่มีนัดหมายในช่วงนี้')
    view.unmount()
  })

  it('adds unconfirmed at zero alongside confirmed and cancelled statuses when absent', async () => {
    report.appointments.mockResolvedValueOnce({
      appointments: 6,
      byStatus: [
        { status: 'Confirmed', appointments: 3 },
        { status: 'Cancelled', appointments: 1 },
      ],
    })
    const view = await mountSelectedDashboard()
    const rows = view.findAll('.status-row')
    expect(rows).toHaveLength(3)
    expect(rows.map((row) => row.get('span').text())).toEqual([
      'ยังไม่ยืนยัน',
      'ยืนยันแล้ว',
      'ยกเลิก',
    ])
    expect(rows.map((row) => row.get('b').text())).toEqual(['0', '3', '1'])
    view.unmount()
  })

  it('preserves the actual unconfirmed count without adding a duplicate row', async () => {
    report.appointments.mockResolvedValueOnce({
      appointments: 2,
      byStatus: [{ status: 'Unconfirmed', appointments: 2 }],
    })
    const view = await mountSelectedDashboard()
    expect(view.findAll('.status-row')).toHaveLength(1)
    expect(view.get('.status-row span').text()).toBe('ยังไม่ยืนยัน')
    expect(view.get('.status-row b').text()).toBe('2')
    view.unmount()
  })

  it('uses one period button and applies a preset month only after confirmation', async () => {
    const view = await mountSelectedDashboard()
    expect(view.findAll('.period-actions button')).toHaveLength(2)
    const requestCount = report.dashboard.mock.calls.length
    await view.get('.custom-range-button').trigger('click')
    await view.findAll('.range-month-button')[1]!.trigger('click')
    await flushPromises()
    expect(report.dashboard.mock.calls).toHaveLength(requestCount)
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    const start = pickers[0]!.props('modelValue')
    const end = pickers[1]!.props('modelValue')
    expect(start).toMatch(/^\d{4}-\d{2}-01$/)
    expect(start.slice(0, 7)).toBe(end.slice(0, 7))
    expect(view.findAll('.range-month-button')[1]!.attributes('aria-pressed')).toBe('true')
    await view.get('.dialog-submit').trigger('click')
    await flushPromises()
    expect(report.dashboard).toHaveBeenCalledWith(start, end, '01')
    expect(view.find('[role="dialog"]').exists()).toBe(false)
    view.unmount()
  })

  it('discards an unconfirmed preset when cancelled', async () => {
    const view = await mountSelectedDashboard()
    const originalCaption = view.get('.period-section .section-caption').text()
    const requestCount = report.dashboard.mock.calls.length
    await view.get('.custom-range-button').trigger('click')
    const originalDates = view
      .findAllComponents({ name: 'ThaiDatePicker' })
      .map((picker) => picker.props('modelValue'))
    await view.findAll('.range-month-button')[2]!.trigger('click')
    await view.get('.dialog-cancel').trigger('click')
    await flushPromises()
    expect(report.dashboard.mock.calls).toHaveLength(requestCount)
    expect(view.get('.period-section .section-caption').text()).toBe(originalCaption)
    await view.get('.custom-range-button').trigger('click')
    expect(
      view
        .findAllComponents({ name: 'ThaiDatePicker' })
        .map((picker) => picker.props('modelValue')),
    ).toEqual(originalDates)
    view.unmount()
  })

  it('offers all twelve months of this year and fills a continuous January to March range', async () => {
    const view = await mountSelectedDashboard()
    const year = clinicToday().slice(0, 4)
    await view.get('.custom-range-button').trigger('click')
    const months = view.findAll('.range-month-button')
    expect(months).toHaveLength(12)
    expect(months[0]!.text()).toBe('มกราคม')
    expect(months[11]!.text()).toBe('ธันวาคม')
    expect((view.get('.range-year-picker select').element as HTMLSelectElement).value).toBe(year)
    await months[0]!.trigger('click')
    await months[2]!.trigger('click')
    expect(months.filter((button) => button.attributes('aria-pressed') === 'true')).toHaveLength(3)
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    expect(pickers[0]!.props('modelValue')).toBe(`${year}-01-01`)
    expect(pickers[1]!.props('modelValue')).toBe(`${year}-03-31`)
    await view.get('.dialog-submit').trigger('click')
    await flushPromises()
    expect(report.dashboard).toHaveBeenCalledWith(`${year}-01-01`, `${year}-03-31`, '01')
    view.unmount()
  })

  it('shrinks a selected month range on a second click and disables confirmation when cleared', async () => {
    const view = await mountSelectedDashboard()
    const year = clinicToday().slice(0, 4)
    await view.get('.custom-range-button').trigger('click')
    const months = view.findAll('.range-month-button')
    await months[0]!.trigger('click')
    await months[2]!.trigger('click')
    await months[2]!.trigger('click')
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    const februaryEnd = new Date(Number(year), 2, 0).getDate()
    expect(pickers[1]!.props('modelValue')).toBe(`${year}-02-${februaryEnd}`)
    expect(months.filter((button) => button.attributes('aria-pressed') === 'true')).toHaveLength(2)
    await months[0]!.trigger('click')
    expect(pickers[0]!.props('modelValue')).toBe(`${year}-02-01`)
    await months[1]!.trigger('click')
    expect(pickers[0]!.props('modelValue')).toBe('')
    expect(pickers[1]!.props('modelValue')).toBe('')
    expect(months.every((button) => button.attributes('aria-pressed') === 'false')).toBe(true)
    expect(view.get('.dialog-submit').attributes('disabled')).toBeDefined()
    view.unmount()
  })

  it('accepts a custom date range across years independently of this year month shortcuts', async () => {
    const view = await mountSelectedDashboard()
    await applyDateRange(view, '2024-12-15', '2025-02-10')
    expect(report.dashboard).toHaveBeenCalledWith('2024-12-15', '2025-02-10', '01')
    await view.get('.custom-range-button').trigger('click')
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    expect(pickers[0]!.props('modelValue')).toBe('2024-12-15')
    expect(pickers[1]!.props('modelValue')).toBe('2025-02-10')
    expect(
      view
        .findAll('.range-month-button')
        .every((button) => button.attributes('aria-pressed') === 'false'),
    ).toBe(true)
    view.unmount()
  })

  it('keeps a custom multi-month range and includes months with zero revenue', async () => {
    const view = await mountSelectedDashboard()
    report.revenue.mockResolvedValueOnce({
      dailyByCategory: [
        { date: '2025-01-05', type: 'CoursePurchase', amount: 100 },
        { date: '2025-03-05', type: 'CoursePurchase', amount: 200 },
      ],
    })
    await applyDateRange(view, '2025-01-01', '2025-03-21')
    expect(report.dashboard).toHaveBeenCalledWith('2025-01-01', '2025-03-21', '01')
    expect(view.findAll('.month-group')).toHaveLength(3)
    expect(
      view.findAll('.revenue-table tbody tr').map((row) => row.findAll('td')[0]!.text()),
    ).toEqual(['100 บาท', '0 บาท', '200 บาท'])
    await view.get('.custom-range-button').trigger('click')
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    expect(pickers[0]!.props('modelValue')).toBe('2025-01-01')
    expect(pickers[1]!.props('modelValue')).toBe('2025-03-21')
    expect(
      view
        .findAll('.range-month-button')
        .every((button) => button.attributes('aria-pressed') === 'false'),
    ).toBe(true)
    view.unmount()
  })

  it('selects a past year without submitting and uses its leap-year month boundaries', async () => {
    const view = await mountSelectedDashboard()
    await view.get('.custom-range-button').trigger('click')
    const requestCount = report.dashboard.mock.calls.length
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    const originalStart = pickers[0]!.props('modelValue')
    await view.get('.range-year-picker select').setValue('2024')
    expect(report.dashboard.mock.calls).toHaveLength(requestCount)
    expect(pickers[0]!.props('modelValue')).toBe(originalStart)
    expect(view.get('.range-year-picker select').text()).toContain('2567')
    await view.findAll('.range-month-button')[1]!.trigger('click')
    expect(pickers[0]!.props('modelValue')).toBe('2024-02-01')
    expect(pickers[1]!.props('modelValue')).toBe('2024-02-29')
    await view.get('.dialog-submit').trigger('click')
    await flushPromises()
    expect(report.dashboard).toHaveBeenCalledWith('2024-02-01', '2024-02-29', '01')
    await view.get('.custom-range-button').trigger('click')
    expect((view.get('.range-year-picker select').element as HTMLSelectElement).value).toBe('2024')
    view.unmount()
  })

  it('extends month selection across years and deselects January without an invalid month zero', async () => {
    const view = await mountSelectedDashboard()
    await view.get('.custom-range-button').trigger('click')
    await view.get('.range-year-picker select').setValue('2024')
    await view.findAll('.range-month-button')[11]!.trigger('click')
    await view.get('.range-year-picker select').setValue('2025')
    await view.findAll('.range-month-button')[0]!.trigger('click')
    const pickers = view.findAllComponents({ name: 'ThaiDatePicker' })
    expect(pickers[0]!.props('modelValue')).toBe('2024-12-01')
    expect(pickers[1]!.props('modelValue')).toBe('2025-01-31')
    await view.findAll('.range-month-button')[0]!.trigger('click')
    expect(pickers[0]!.props('modelValue')).toBe('2024-12-01')
    expect(pickers[1]!.props('modelValue')).toBe('2024-12-31')
    view.unmount()
  })

  it('closes the range dialog on Escape and restores focus to the trigger', async () => {
    const view = await mountSelectedDashboard()
    const trigger = view.get('.custom-range-button').element as HTMLButtonElement
    trigger.focus()
    await view.get('.custom-range-button').trigger('click')
    const dialog = view.get('[role="dialog"]')
    expect(document.activeElement).toBe(dialog.element)
    await dialog.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(view.get('.dialog-submit').element)
    await view.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(view.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger)
    view.unmount()
  })
})
