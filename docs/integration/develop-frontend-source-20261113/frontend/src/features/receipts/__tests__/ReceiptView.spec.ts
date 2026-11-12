import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ReceiptView from '../views/ReceiptView.vue'
const routeQuery = vi.hoisted(() => ({}) as Record<string, string>)
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '3' }, query: routeQuery }) }))
let wrapper: VueWrapper
let broken = false
const receipt = {
  id: 3,
  receiptId: 2,
  receiptNo: 'OLD-PERSISTED-2',
  soldAt: '2026-09-22T10:00:00Z',
  patient: { id: 1, hn: 'HN1', fullName: 'Patient' },
  branchCode: '02',
  paymentLabel: 'Deposit',
  subtotalBeforeDiscount: 1000,
  billDiscountAmount: 100,
  taxableAmount: 900,
  taxAmount: 0,
  netAmount: 900,
  paid: 100,
  status: 'PAID',
  items: [
    {
      id: 3,
      type: 'ASSESSMENT',
      description: 'Historical Assessment',
      quantity: 1,
      lineTotal: 1000,
    },
  ],
  payments: [{ id: 1, type: 'PAYMENT', paymentMethodCode: 'QR', amount: 100 }],
}
beforeEach(() => {
  delete routeQuery.print
  broken = false
  vi.stubGlobal('print', vi.fn())
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (String(url) === '/api/receipts/3')
        return new Response(JSON.stringify(broken ? { ...receipt, netAmount: null } : receipt))
      if (String(url) === '/api/branches')
        return new Response(
          JSON.stringify([
            { id: 2, branchCode: '02', name: 'Actual sale branch', address: 'Receipt address' },
          ]),
        )
      return new Response('{}', { status: 500 })
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})
async function open() {
  wrapper = mount(ReceiptView, {
    global: { stubs: { StaffHeader: true, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
}
it('reads the legacy transaction ID and prints persisted net, discount, tax and partial paid separately', async () => {
  await open()
  expect(fetch).toHaveBeenCalledWith('/api/receipts/3', expect.anything())
  expect(wrapper.text()).toContain('OLD-PERSISTED-2')
  expect(wrapper.text()).toContain('Actual sale branch')
  expect(wrapper.text()).toContain('Historical Assessment')
  expect(wrapper.get('[data-test="net"]').text()).toContain('900.00')
  expect(wrapper.get('[data-test="paid"]').text()).toContain('100.00')
  expect(wrapper.get('[data-test="discount"]').text()).toContain('100.00')
  expect(wrapper.get('[data-test="tax"]').text()).toContain('0.00')
  expect(wrapper.text()).toContain('มัดจำ')
  expect(wrapper.text()).toContain('พร้อมเพย์')
  expect(wrapper.text()).not.toContain('แต้มคงเหลือ')
  expect(wrapper.text()).not.toContain('วงเงินคงเหลือ')
  await wrapper.get('[data-test="print"]').trigger('click')
  await flushPromises()
  expect(window.print).toHaveBeenCalledOnce()
  expect(
    vi.mocked(fetch).mock.calls.every(([, init]) => !init?.method || init.method === 'GET'),
  ).toBe(true)
})
it('shows an error without a printable document when persisted totals are missing', async () => {
  broken = true
  await open()
  expect(wrapper.find('.printable-receipt').exists()).toBe(false)
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  expect(window.print).not.toHaveBeenCalled()
})

it('retains the persisted Installment label independently of receipt lifecycle', async () => {
  const original = receipt.paymentLabel
  receipt.paymentLabel = 'Installment'
  await open()
  expect(wrapper.text()).toContain('ผ่อน')
  expect(wrapper.text()).not.toContain('ชำระเต็ม')
  receipt.paymentLabel = original
})

it('does not open auto print after leaving while enrichment is still loading', async () => {
  routeQuery.print = '1'
  let finish: (response: Response) => void = () => {}
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (String(url) === '/api/receipts/3') return new Response(JSON.stringify(receipt))
      if (String(url) === '/api/branches')
        return await new Promise<Response>((resolve) => {
          finish = resolve
        })
      return new Response('{}', { status: 500 })
    }),
  )
  await open()
  wrapper.unmount()
  finish(new Response('[]'))
  await flushPromises()
  expect(window.print).not.toHaveBeenCalled()
})
