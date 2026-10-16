import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CommissionVersionHistoryView from '../views/CommissionVersionHistoryView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

let wrapper: VueWrapper
let fixtures: Record<string, unknown>
let failing = false

const versions = [
  { id: 3, name: 'S', version: 3, effectiveFrom: '2999-01-01', effectiveTo: null, locked: false, createdBy: 'ณัฐชยา', createdAt: '2026-10-31T11:00:00Z', updatedAt: null, updatedBy: null },
  { id: 2, name: 'S', version: 2, effectiveFrom: '2020-02-01', effectiveTo: '2999-12-31', locked: true, createdBy: 'ณัฐชยา', createdAt: '2020-01-30T11:00:00Z', updatedAt: '2020-02-02T04:30:00Z', updatedBy: 'Manager Demo' },
  { id: 1, name: 'S', version: 1, effectiveFrom: '2020-01-01', effectiveTo: '2020-01-31', locked: true, createdBy: null, createdAt: null, updatedAt: null, updatedBy: null },
]

beforeEach(() => {
  failing = false
  fixtures = {
    '/api/commission/schemes': versions,
    '/api/commission/schemes/2/tiers': [
      { id: 7, order: 1, minimum: 0, maximum: 50000, rate: 0.05, active: true },
      { id: 8, order: 2, minimum: 50000.01, maximum: null, rate: 0.07, active: true },
    ],
    '/api/commission/schemes/1/tiers': [{ id: 5, order: 1, minimum: 0, maximum: null, rate: 0.04, active: true }],
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (failing) return { ok: false, status: 500, text: async () => JSON.stringify({ message: 'Backend unavailable' }) }
      return { ok: true, text: async () => JSON.stringify(fixtures[url] ?? []) }
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

async function start() {
  wrapper = mount(CommissionVersionHistoryView, { global: { stubs: { StaffHeader: true } } })
  await flushPromises()
}

describe('commission version history page', () => {
  it('lists every version with its state, recorder and time, and links back to the settings', async () => {
    await start()
    // back to the commission rules category, not the first category of the settings
    expect(wrapper.get('.history-back').attributes('href')).toBe('/staff/settings?section=tiers')
    const table = wrapper.get('table.versions')
    expect(table.findAll('thead th').map((th) => th.text())).toEqual([
      'เวอร์ชัน', 'ช่วงที่มีผล', 'สถานะ', 'ผู้บันทึกล่าสุด', 'แก้ไขล่าสุด', 'รายละเอียด',
    ])
    expect(table.findAll('.version-status').map((item) => item.text())).toEqual(['รอเริ่มใช้', 'ใช้อยู่', 'สิ้นสุดแล้ว'])
    expect(table.text()).toContain('เป็นต้นไป')
    expect(table.text()).toContain('ณัฐชยา')
  })

  it('shows who recorded a version last and when: the editor if it was edited, else who created it', async () => {
    await start()
    const rows = wrapper.findAll('table.versions tbody tr')
    const cells = (row: number) => rows[row]!.findAll('td').map((td) => td.text())
    // V3 was never edited: its creator and creation time
    expect(cells(0)[3]).toBe('ณัฐชยา')
    expect(cells(0)[4]).toContain('2569')
    // V2 was edited on 2 Feb 2020 (2563 BE) by someone else
    expect(cells(1)[3]).toBe('Manager Demo')
    expect(cells(1)[4]).toContain('2563')
    // V1 has neither: dashes
    expect(cells(2)[3]).toBe('-')
    expect(cells(2)[4]).toBe('-')
  })

  it('opens the version in force first, read only, with its tiers', async () => {
    await start()
    const detail = wrapper.get('.detail')
    expect(detail.text()).toContain('รายละเอียดเวอร์ชัน V2')
    expect(detail.text()).toContain('อ่านอย่างเดียว')
    expect(detail.text()).toContain('มีผลสำหรับรอบเดือน')
    expect(detail.findAll('tbody tr')).toHaveLength(2)
    expect(detail.text()).toContain('50,000.01')
    expect(detail.text()).toContain('ไม่จำกัด')
    expect(detail.text()).toContain('7%')
    expect(detail.find('input, select, button').exists()).toBe(false) // nothing to edit
  })

  it('shows another version when its details are chosen', async () => {
    await start()
    await wrapper.findAll('.version-link')[2]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.detail').text()).toContain('รายละเอียดเวอร์ชัน V1')
    expect(wrapper.get('.detail').text()).toContain('4%')
    expect(wrapper.findAll('tr.picked')).toHaveLength(1)
  })

  it('says so when there is no version, and when loading fails', async () => {
    fixtures['/api/commission/schemes'] = []
    await start()
    expect(wrapper.text()).toContain('ยังไม่มีเวอร์ชัน')
    wrapper.unmount()
    failing = true
    await start()
    expect(wrapper.get('.history-error').text()).toContain('Backend unavailable')
  })
})
