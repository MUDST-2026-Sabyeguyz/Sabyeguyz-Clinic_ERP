import { mount, flushPromises } from '@vue/test-utils'
// Vitest needs a Blob implementation with .text(), while the app tsconfig omits Node types.
// @ts-expect-error Node's Blob polyfill is available in the Vitest runtime.
import { Blob as NodeBlob } from 'node:buffer'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from './SalesPaymentReportView.vue'

const mocks = vi.hoisted(() => ({ read: vi.fn(), push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/features/staff/data/managerBranch', () => ({ selectedManagerBranch: () => ({ code: '01' }) }))
vi.mock('@/api/client', () => ({
  reportTransactionsApi: { read: mocks.read },
  branchApi: { list: async () => [{ code: '01', name: 'สาขาทดสอบ' }] },
}))
const rows = Array.from({ length: 13 }, (_, i) => ({ source: 'SALE', sourceId: i + 1,
  receiptId: i + 1, receiptNo: `R${i + 1}`, patientId: 2, patientName: 'ผู้ป่วยทดสอบ', patientHn: 'SYN2',
  branchCode: '01', branchName: 'สาขาทดสอบ', date: '2026-11-12', type: 'SingleVisit',
  treatment: `บริการ ${i + 1}`, paymentMethod: 'พร้อมเพย์', amount: 100 }))
const result = () => ({ basis: 'PAYMENT_DATE', from: '2026-11-01', to: '2026-11-30', rows,
  total: 1300, byMethod: { พร้อมเพย์: 1300 }, refunds: [], refundedTotal: 0 })
describe('detailed sales/payment screen and CSV', () => {
  beforeEach(() => {
    vi.restoreAllMocks(); vi.clearAllMocks()
    localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER' }))
    mocks.read.mockResolvedValue(result())
    vi.stubGlobal('Blob', NodeBlob)
  })
  const mountView = () => mount(View, { props: { mode: 'payment' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  it('downloads every filtered row rather than the visible page with the same total', async () => {
    let blob: Blob | undefined
    vi.stubGlobal('URL', { createObjectURL: vi.fn((value: Blob) => { blob = value; return 'blob:test' }), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const view = mountView(); await flushPromises()
    expect(view.findAll('tbody tr')).toHaveLength(10)
    expect(view.text()).toContain('หน้า 1 จาก 2')
    const button = view.findAll('button').find(button => button.text() === 'ส่งออกรายงาน CSV')!
    await button.trigger('click'); await flushPromises()
    const csv = await blob!.text()
    expect(csv).toContain('R13,ผู้ป่วยทดสอบ,SYN2')
    expect(csv).toContain('รวมทั้งหมด,,,,,,,,"1,300.00"')
    expect(csv).toContain('สาขา: 01')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test')
    view.unmount()
  })
  it('uses the applied filters in the download even when the form has unsent edits', async () => {
    let blob: Blob | undefined
    vi.stubGlobal('URL', { createObjectURL: vi.fn((value: Blob) => { blob = value; return 'blob:test' }), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const view = mountView(); await flushPromises()
    await view.find('input[placeholder="ค้นหาชื่อผู้ป่วยหรือ HN"]').setValue('ยังไม่ค้น')
    await view.findAll('button').find(button => button.text() === 'ส่งออกรายงาน CSV')!.trigger('click')
    expect(await blob!.text()).not.toContain('ยังไม่ค้น')
    await view.find('form').trigger('submit'); await flushPromises()
    expect(mocks.read.mock.calls.at(-1)![1].patient).toBe('ยังไม่ค้น')
    view.unmount()
  })
  it('does not download an empty filtered result', async () => {
    mocks.read.mockResolvedValue({ ...result(), rows: [], total: 0 })
    const view = mountView(); await flushPromises()
    expect(view.text()).toContain('ไม่พบข้อมูลตามเงื่อนไขที่เลือก')
    expect(view.findAll('button').find(button => button.text() === 'ส่งออกรายงาน CSV')!.attributes('disabled')).toBeDefined()
    view.unmount()
  })
  it('reports a creation/download failure instead of a success', async () => {
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => { throw new Error('download unavailable') }) })
    const view = mountView(); await flushPromises()
    await view.findAll('button').find(button => button.text() === 'ส่งออกรายงาน CSV')!.trigger('click')
    expect(view.get('[role="alert"]').text()).toContain('download unavailable')
    view.unmount()
  })
  it('does not render an export control for a non-manager', async () => {
    localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST' }))
    const view = mountView(); await flushPromises()
    expect(view.findAll('button').some(button => button.text() === 'ส่งออกรายงาน CSV')).toBe(false)
    view.unmount()
  })
})
