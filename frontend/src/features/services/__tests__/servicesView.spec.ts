import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ServicesView from '../views/servicesView.vue'
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { patientId: '1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
const patient = { id: 1, hn: 'HN1', fullName: 'Patient', phone: '', active: true, branchCode: '01' }
let wrapper: VueWrapper
let label: string | null
let posted: unknown
beforeEach(() => {
  label = 'Deposit'
  posted = undefined
  localStorage.clear()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const path = String(input)
      let body: unknown = []
      if (path === '/api/catalog/services')
        body = [{ id: 1, code: 'ASSESS', name: 'Assessment', price: 100 }]
      else if (path === '/api/patients/1') body = patient
      else if (path.startsWith('/api/patients')) body = { items: [patient], totalPages: 1 }
      else if (path === '/api/branches')
        body = [{ id: 1, branchCode: '01', name: 'Branch', active: true }]
      else if (path === '/api/payment-methods')
        body = [
          { id: 1, code: 'Cash', name: 'Cash' },
          { id: 2, code: 'QR', name: 'QR code' },
        ]
      else if (path === '/api/payments') {
        posted = JSON.parse(String(init?.body))
        body = {
          id: 3,
          receiptNo: 'R3',
          paymentLabel: label,
          status: 'PAID',
          paid: 100,
          total: 100,
        }
      }
      return new Response(JSON.stringify(body), { status: 200 })
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})
async function click(text: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === text)
  expect(button, text).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}
async function pay() {
  wrapper = mount(ServicesView, { global: { stubs: { StaffHeader: true, FilterDropdown: true } } })
  await flushPromises()
  await click('เริ่มขายบริการ')
  const options = wrapper.findAll('.payment-options button')
  expect(options).toHaveLength(2)
  await options[1]!.trigger('click')
  expect(wrapper.findAll('.payment-options button.active')).toHaveLength(1)
  expect(wrapper.find('.payment-options button.active').text()).toBe('พร้อมเพย์')
  await click('ตรวจสอบและชำระเงิน')
  await click('ยืนยันรับชำระเงิน')
  await click('ยืนยันการชำระเงิน')
  await click('ดูและพิมพ์ใบเสร็จ')
}
it('keeps one selected payment code in the request while showing QR as พร้อมเพย์', async () => {
  await pay()
  expect(posted).toMatchObject({ payments: [{ paymentMethodCode: 'QR', amount: 100 }] })
  expect((posted as { payments: unknown[] }).payments).toHaveLength(1)
  expect(wrapper.text()).toContain('พร้อมเพย์')
})
it('shows persisted payment label instead of inventing FullyPaid from the paid amount', async () => {
  await pay()
  expect(wrapper.find('.printable-receipt').text()).toContain('มัดจำ')
  expect(wrapper.find('.printable-receipt').text()).not.toContain('ชำระเต็ม')
})
it('omits unsupported points and credit balances from the receipt', async () => {
  await pay()
  expect(wrapper.find('.printable-receipt').text()).not.toContain('แต้มคงเหลือ')
  expect(wrapper.find('.printable-receipt').text()).not.toContain('วงเงินคงเหลือ')
})
