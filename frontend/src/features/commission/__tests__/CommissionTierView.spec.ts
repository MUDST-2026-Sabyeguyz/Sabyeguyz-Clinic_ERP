import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CommissionTierView from '../views/CommissionTierView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' },
}))

type Call = { url: string; method: string; body?: Record<string, unknown> }
let calls: Call[]
let wrapper: VueWrapper
let effectiveFrom: string // the version on the page: in force (past) or not started (future)
// the drawer is teleported to <body>
const page = () => new DOMWrapper(document.body)
const stubs = { StaffHeader: true, ThaiDatePicker: true, CommissionTabs: true, FilterDropdown: true }

beforeEach(() => {
  calls = []
  effectiveFrom = '2026-01-01'
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined })
      const result =
        url === '/api/commission/schemes' && method === 'GET'
          ? [{ id: 9, name: 'Scheme', version: '1', effectiveFrom, effectiveTo: null }]
          : url === '/api/commission/schemes/9/tiers'
            ? [
                { minimum: 0, maximum: 59999.99, rate: 0.05, order: 1 },
                { minimum: 60000, maximum: null, rate: 0.1, order: 2 },
              ]
            : { id: 10, name: 'Scheme', version: '2', effectiveFrom: '2026-11-01', effectiveTo: null }
      return { ok: true, text: async () => JSON.stringify(result) }
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})

async function openNewVersion() {
  wrapper = mount(CommissionTierView, { global: { stubs }, attachTo: document.body })
  await flushPromises()
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('แก้กฎคอมมิชชัน'))!
    .trigger('click')
}
const rows = () => page().findAll('.scheme-tier-editor article')
const inputs = (row: number) => rows()[row]!.findAll('input')
const button = (text: string) => page().findAll('button').find((item) => item.text().includes(text))!

describe('new Scheme Version tiers', () => {
  it('adds a tier that continues the last range and closes an unbounded last tier', async () => {
    await openNewVersion()
    expect(rows()).toHaveLength(2)
    await button('เพิ่ม Tier').trigger('click')
    expect(rows()).toHaveLength(3)
    expect(rows()[2]!.text()).toContain('Tier 3')
    // the old last tier (60,000 and up) now ends one satang below the new tier
    expect(Number((inputs(1)[1]!.element as HTMLInputElement).value)).toBe(69999.99)
    expect(Number((inputs(2)[0]!.element as HTMLInputElement).value)).toBe(70000)
    expect((inputs(2)[1]!.element as HTMLInputElement).value).toBe('')
  })

  it('removes a tier and renumbers the rest, but keeps at least one', async () => {
    await openNewVersion()
    await button('เพิ่ม Tier').trigger('click')
    await page().get('button[aria-label="ลบ Tier 2"]').trigger('click')
    expect(rows()).toHaveLength(2)
    expect(rows()[1]!.text()).toContain('Tier 2')
    await page().get('button[aria-label="ลบ Tier 2"]').trigger('click')
    expect(rows()).toHaveLength(1)
    expect(page().get('button[aria-label="ลบ Tier 1"]').attributes('disabled')).toBeDefined()
  })

  it('makes the last tier unlimited again when the tier below its range is removed', async () => {
    await openNewVersion()
    await button('เพิ่ม Tier').trigger('click') // tiers: 0-59999.99, 60000-69999.99, 70000-unlimited
    await button('เพิ่ม Tier').trigger('click') // 70000-79999.99, 80000-unlimited
    expect(rows()).toHaveLength(4)
    await page().get('button[aria-label="ลบ Tier 4"]').trigger('click')
    // Tier 3 is the last one now and has no ceiling, whatever it had before
    expect((inputs(2)[1]!.element as HTMLInputElement).value).toBe('')
    expect(rows()).toHaveLength(3)
  })

  it('saves the tiers that are left, renumbered', async () => {
    await openNewVersion()
    await button('เพิ่ม Tier').trigger('click')
    await inputs(2)[2]!.setValue('12')
    await page().get('button[aria-label="ลบ Tier 1"]').trigger('click')
    // the tiers 60,000+ and the new one no longer start at 0, but they are still contiguous
    await button('บันทึก').trigger('click')
    await flushPromises()
    const post = calls.find((call) => call.method === 'POST' && call.url === '/api/commission/schemes')
    expect(post?.body?.tiers).toEqual([
      { minimum: 60000, maximum: 69999.99, rate: 0.1, order: 1 },
      { minimum: 70000, maximum: null, rate: 0.12, order: 2 },
    ])
  })

  it('takes only the start month: the version begins on the 1st and has no end of its own', async () => {
    await openNewVersion()
    const months = page().findAll('input[type="month"]')
    expect(months).toHaveLength(1) // no end month: the system ends a version the day before the next one starts
    await months[0]!.setValue('2026-11')
    await button('บันทึก').trigger('click')
    await flushPromises()
    const post = calls.find((call) => call.method === 'POST' && call.url === '/api/commission/schemes')
    expect(post?.body).toMatchObject({ effectiveFrom: '2026-11-01' })
    expect(post?.body).not.toHaveProperty('effectiveTo')
  })

  it('shows the version number as read only and has no status for a tier', async () => {
    await openNewVersion()
    const version = page().findAll('.drawer-form input').find((input) => (input.element as HTMLInputElement).disabled)
    expect(version).toBeTruthy()
    expect(page().text()).not.toContain('เดือนสิ้นสุด')
    expect(page().text()).toContain('โหลดกฎปัจจุบันมาให้แก้')
  })

  describe('a version that has not started yet', () => {
    beforeEach(() => {
      effectiveFrom = '2999-01-01'
    })

    it('is edited in place: the button says so and saving updates it without creating a version', async () => {
      wrapper = mount(CommissionTierView, { global: { stubs }, attachTo: document.body })
      await flushPromises()
      const open = wrapper.findAll('button').find((item) => item.text().includes('แก้ไขเวอร์ชันที่รอเริ่มใช้'))!
      expect(open.text()).toContain('V1')
      await open.trigger('click')
      expect(page().text()).toContain('บันทึกแล้วไม่สร้างเวอร์ชันใหม่')
      expect(page().get('input[disabled]').element).toBeTruthy() // the version number stays
      await inputs(1)[2]!.setValue('11')
      await button('บันทึก').trigger('click')
      await flushPromises()
      expect(calls.some((call) => call.method === 'POST')).toBe(false)
      const put = calls.find((call) => call.method === 'PUT' && call.url === '/api/commission/schemes/9')
      expect(put?.body).toMatchObject({ effectiveFrom: '2999-01-01' })
      expect((put?.body?.tiers as Array<{ rate: number }>)[1]!.rate).toBe(0.11)
    })

    it('saves a single tier change into the same version', async () => {
      wrapper = mount(CommissionTierView, { global: { stubs }, attachTo: document.body })
      await flushPromises()
      await wrapper.findAll('tbody button')[0]!.trigger('click')
      expect(page().get('.drawer-form input:nth-of-type(1)').element).toBeTruthy()
      const rate = page().findAll('.drawer-form input')[2]!
      await rate.setValue('6')
      await button('บันทึก').trigger('click')
      await flushPromises()
      expect(calls.some((call) => call.method === 'POST')).toBe(false)
      expect(calls.some((call) => call.method === 'PUT' && call.url === '/api/commission/schemes/9')).toBe(true)
    })
  })
})
