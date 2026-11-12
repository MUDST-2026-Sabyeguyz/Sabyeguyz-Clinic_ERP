import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import FeeRuleHistoryView from '../views/FeeRuleHistoryView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

let wrapper: VueWrapper
let rules: unknown[]
let failing = false

const rule = (over: Record<string, unknown>) => ({
  id: 1, feeType: 'Fixed', feeValue: 45, effectiveFrom: '2020-01-01', effectiveTo: null, enabled: true, used: false,
  name: null, lastEditedBy: null, lastEditedAt: null, ...over,
})

beforeEach(() => {
  failing = false
  rules = [
    rule({ id: 3, feeType: 'Percentage', feeValue: 10, effectiveFrom: '2999-01-01', name: 'ค่ามือใหม่', lastEditedBy: 'Manager Demo', lastEditedAt: '2026-10-16T08:46:24Z' }),
    rule({ id: 2, feeValue: 60, effectiveFrom: '2020-02-01', effectiveTo: '2998-12-31', lastEditedBy: 'Manager Demo', lastEditedAt: '2026-10-16T08:46:24Z' }),
    rule({ id: 1, feeValue: 45, effectiveFrom: '2020-01-01', effectiveTo: '2020-01-31' }),
  ]
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => {
      if (failing) return { ok: false, status: 500, text: async () => JSON.stringify({ message: 'Backend unavailable' }) }
      return { ok: true, text: async () => JSON.stringify(rules) }
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

async function start() {
  wrapper = mount(FeeRuleHistoryView, { global: { stubs: { StaffHeader: true } } })
  await flushPromises()
}

describe('treatment fee rule history page', () => {
  it('numbers the rules from the oldest, lists the newest first with their state from the dates', async () => {
    await start()
    const table = wrapper.get('table.versions')
    expect(table.findAll('thead th').map((th) => th.text())).toEqual([
      'เวอร์ชัน', 'ช่วงที่มีผล', 'สถานะ', 'ผู้บันทึกล่าสุด', 'แก้ไขล่าสุด', 'รายละเอียด',
    ])
    const rows = table.findAll('tbody tr').map((row) => row.findAll('td').map((td) => td.text()))
    expect(rows.map((cells) => cells[0])).toEqual(['V3', 'V2', 'V1'])
    expect(table.findAll('.version-status').map((item) => item.text())).toEqual(['รอเริ่มใช้', 'ใช้อยู่', 'สิ้นสุดแล้ว'])
    expect(rows[0]![3]).toBe('Manager Demo')
    expect(rows[2]![3]).toBe('-')
    expect(wrapper.get('.history-back').attributes('href')).toBe('/staff/settings?section=commission')
  })

  it('opens the rule in force first, read only', async () => {
    await start()
    const detail = wrapper.get('.detail')
    expect(detail.text()).toContain('รายละเอียดเวอร์ชัน V2')
    expect(detail.text()).toContain('อ่านอย่างเดียว')
    expect(detail.text()).toContain('จำนวนเงินคงที่ 60 บาท / ครั้ง')
    expect(detail.find('input, select, textarea').exists()).toBe(false)
  })

  it('shows another version when its details are chosen', async () => {
    await start()
    await wrapper.findAll('.version-link')[0]!.trigger('click')
    expect(wrapper.get('.detail').text()).toContain('รายละเอียดเวอร์ชัน V3')
    expect(wrapper.get('.detail').text()).toContain('เปอร์เซ็นต์ 10% ของยอดรายการ')
    expect(wrapper.get('.detail').text()).toContain('ค่ามือใหม่')
  })

  it('says so when there is no rule, and when loading fails', async () => {
    rules = []
    await start()
    expect(wrapper.text()).toContain('ยังไม่มีกฎค่ามือ')
    wrapper.unmount()
    failing = true
    await start()
    expect(wrapper.get('.history-error').text()).toContain('Backend unavailable')
  })
})
