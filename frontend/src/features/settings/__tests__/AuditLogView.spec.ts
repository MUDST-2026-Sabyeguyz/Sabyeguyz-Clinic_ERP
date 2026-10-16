import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AuditLogView from '../views/AuditLogView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

let wrapper: VueWrapper
let urls: string[]
let failing = false

const entry = (over: Record<string, unknown> = {}) => ({
  id: 1,
  tableName: 'commission_scheme',
  recordId: '2',
  action: 'UPDATE',
  oldValue: JSON.stringify({ effective_to: null, scheme_version: 2 }),
  newValue: JSON.stringify({ effective_to: '2026-10-31', scheme_version: 2, reason: 'ended by the new version' }),
  changedBy: 2,
  changedAt: '2026-10-16T08:46:24Z',
  changedByName: 'Manager Demo',
  ...over,
})
const page = (items: unknown[], over: Record<string, unknown> = {}) => ({
  items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...over,
})

beforeEach(() => {
  urls = []
  failing = false
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      urls.push(url)
      if (failing) return { ok: false, status: 500, text: async () => JSON.stringify({ message: 'Backend unavailable' }) }
      const body = url.startsWith('/api/audit-logs/tables')
        ? ['commission_scheme', 'treatment_fee_rule']
        : page([
            entry(),
            entry({ id: 2, tableName: 'appointment', recordId: '5', changedBy: null, changedByName: null, action: 'UPDATE',
              oldValue: JSON.stringify({ appointment_status: 'Confirmed' }),
              newValue: JSON.stringify({ source: 'GoogleCalendar', appointment_status: 'Cancelled' }) }),
            entry({ id: 3, action: 'DELETE', oldValue: 'plain text', newValue: null }),
          ])
      return { ok: true, text: async () => JSON.stringify(body) }
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

async function start() {
  wrapper = mount(AuditLogView, { global: { stubs: { StaffHeader: true, ThaiDatePicker: true } } })
  await flushPromises()
}

describe('audit log page', () => {
  it('lists who changed what and when, newest first as the server sends them', async () => {
    await start()
    const rows = wrapper.findAll('table.audit-table > tbody > tr')
    expect(rows).toHaveLength(3)
    expect(rows[0]!.text()).toContain('Manager Demo')
    expect(rows[0]!.text()).toContain('Scheme ค่าคอมมิชชัน')
    expect(rows[0]!.text()).toContain('แก้ไข')
    expect(wrapper.get('.audit-back').attributes('href')).toBe('/staff/settings')
  })

  it('names the source of an automatic event instead of a user', async () => {
    await start()
    expect(wrapper.findAll('table.audit-table > tbody > tr')[1]!.text()).toContain('ระบบอัตโนมัติ (GoogleCalendar)')
  })

  it('shows the value before and after, marking what changed', async () => {
    await start()
    await wrapper.findAll('.audit-link')[0]!.trigger('click')
    const detail = wrapper.get('.audit-detail')
    const changed = detail.findAll('tr.changed').map((row) => row.text())
    expect(changed.some((text) => text.includes('สิ้นสุด') && text.includes('2026-10-31'))).toBe(true)
    expect(changed.some((text) => text.includes('เหตุผล') && text.includes('ended by the new version'))).toBe(true)
    // the version did not change: it is listed but not marked
    expect(detail.findAll('tr').some((row) => row.text().includes('เวอร์ชัน') && !row.classes().includes('changed'))).toBe(true)
  })

  it('shows the tiers of a scheme one per line', async () => {
    await start()
    // replace the first row with a scheme that carries tiers
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      text: async () => JSON.stringify(page([entry({
        oldValue: null,
        newValue: JSON.stringify({ tiers: [
          { tier_order: 1, minimum_monthly_sales: 0, maximum_monthly_sales: 59999.99, commission_rate: 0.05 },
          { tier_order: 2, minimum_monthly_sales: 60000, maximum_monthly_sales: null, commission_rate: 0.1 },
        ] }),
      })])),
    })))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('.audit-link').trigger('click')
    const text = wrapper.get('.audit-detail').text()
    expect(text).toContain('Tier 1: 0 – 59999.99 → 5%')
    expect(text).toContain('Tier 2: 60000 – ไม่จำกัด → 10%')
  })

  it('shows text that is not JSON as it is', async () => {
    await start()
    await wrapper.findAll('.audit-link')[2]!.trigger('click')
    expect(wrapper.get('.audit-detail').text()).toContain('plain text')
  })

  it('asks the server for the chosen filters and the first page again', async () => {
    await start()
    await wrapper.get('select').setValue('treatment_fee_rule')
    await wrapper.findAll('select')[1]!.setValue('UPDATE')
    await wrapper.get('input').setValue(' 3 ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const last = urls[urls.length - 1]!
    expect(last).toContain('tableName=treatment_fee_rule')
    expect(last).toContain('action=UPDATE')
    expect(last).toContain('recordId=3')
    expect(last).toContain('page=0')
  })

  it('pages through the results', async () => {
    await start()
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      urls.push(url)
      return { ok: true, text: async () => JSON.stringify(page([entry()], { page: 1, totalPages: 3, totalItems: 41 })) }
    }))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.audit-pager').text()).toContain('หน้า 2 / 3')
    await wrapper.findAll('.audit-pager button')[1]!.trigger('click')
    await flushPromises()
    expect(urls[urls.length - 1]).toContain('page=2')
  })

  it('says so when nothing matches, and when loading fails', async () => {
    failing = true
    await start()
    expect(wrapper.get('.audit-error').text()).toContain('Backend unavailable')
  })
})
