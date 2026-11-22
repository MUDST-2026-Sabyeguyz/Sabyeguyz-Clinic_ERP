import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CommissionFormulaView from '../views/CommissionFormulaView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' },
}))

const formula = {
  asOf: '2026-10-16',
  scheme: { id: 1, name: 'Default Tier Scheme', version: 1, effectiveFrom: '2026-01-01', effectiveTo: null },
  tiers: [
    { id: 1, order: 1, minimum: 0, maximum: 59999.99, rate: 0.05, active: true },
    { id: 2, order: 2, minimum: 60000, maximum: null, rate: 0.1, active: true },
  ],
  treatmentFee: { id: 1, feeType: 'Fixed', feeValue: 45, effectiveFrom: '2026-01-01', effectiveTo: null, enabled: true, used: true },
}
let wrapper: VueWrapper
let calls: Array<{ url: string; method: string }>

beforeEach(() => {
  calls = []
  localStorage.clear()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      calls.push({ url: String(input), method: init?.method ?? 'GET' })
      return new Response(JSON.stringify(formula), { status: 200 })
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})
async function open(role: 'MANAGER' | 'THERAPIST') {
  localStorage.setItem('authUser', JSON.stringify({ role }))
  wrapper = mount(CommissionFormulaView, { global: { stubs: { StaffHeader: true } } })
  await flushPromises()
}

describe('how the pay is worked out (read only)', () => {
  it('shows the tiers, the treatment fee and the rules to a therapist', async () => {
    await open('THERAPIST')
    const text = wrapper.text()
    expect(text).toContain('Tier 1')
    expect(text).toContain('5%')
    expect(text).toContain('10%')
    expect(text).toContain('ไม่จำกัด')
    expect(text).toContain('฿45.00 ต่อครั้งที่รักษา')
    expect(text).toContain('อัตราถูกล็อกเมื่อปิดยอดเดือนที่ขาย')
    expect(text).toContain('Manager เป็นผู้กำหนด')
  })

  it('has nothing to edit and sends only reads', async () => {
    await open('MANAGER')
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.findAll('input, select, textarea')).toHaveLength(0)
    expect(calls).toEqual([{ url: '/api/commission/formula', method: 'GET' }])
  })

  it('says so when no tier or fee is in force', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ asOf: '2026-10-16', scheme: null, tiers: [], treatmentFee: null }), { status: 200 })),
    )
    await open('THERAPIST')
    expect(wrapper.text()).toContain('ยังไม่มี Tier ที่ใช้อยู่ในวันนี้')
    expect(wrapper.text()).toContain('ยังไม่มีกฎค่ามือที่ใช้อยู่ในวันนี้')
  })
})
