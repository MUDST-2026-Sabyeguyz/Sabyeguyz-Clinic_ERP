import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ServicesView from '../views/servicesView.vue'
import ManagerServicesView from '../views/ManagerServicesView.vue'
import { paymentMethodApi, staffApi } from '@/api/client'
const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { patientId: '1' } }),
  useRouter: () => ({ push }),
}))
const patient = { id: 1, hn: 'HN1', fullName: 'Patient', phone: '', active: true, branchCode: '01' }
let wrapper: VueWrapper
let label: string | null
let posted: unknown
let failedPost = false
beforeEach(() => {
  push.mockReset()
  failedPost = false
  label = 'Deposit'
  posted = undefined
  localStorage.clear()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const path = String(input)
      let body: unknown = []
      if (path === '/api/catalog/services')
        body = [{ id: 1, code: 'MANUAL', name: 'Manual', price: 100 }]
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
        if (failedPost)
          return new Response(JSON.stringify({ message: 'Payment rejected' }), { status: 400 })
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
  vi.restoreAllMocks()
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
}
it('keeps one selected payment code in the request while showing QR as พร้อมเพย์', async () => {
  await pay()
  expect(posted).toMatchObject({ payments: [{ paymentMethodCode: 'QR', amount: 100 }] })
  expect((posted as { payments: unknown[] }).payments).toHaveLength(1)
})
it('opens the persisted receipt automatically after successful payment', async () => {
  await pay()
  expect(push).toHaveBeenCalledWith('/staff/receipts/3')
})
it('hides Assessment for new sales', async () => {
  wrapper = mount(ServicesView, { global: { stubs: { StaffHeader: true, FilterDropdown: true } } })
  await flushPromises()
  await click('เริ่มขายบริการ')
  expect(wrapper.findAll('.sale-tabs button').map((b) => b.text())).not.toContain('Assessment')
})

it.each([
  ['therapist', ServicesView],
  ['manager', ManagerServicesView],
] as const)('honors closed payment methods from settings in the %s sales screen', async (_role, component) => {
  vi.spyOn(paymentMethodApi, 'list').mockResolvedValue([
    { id: 1, code: 'Cash', name: 'Cash', active: false },
    { id: 2, code: 'QR', name: 'QR code', active: true },
  ])
  vi.spyOn(staffApi, 'list').mockResolvedValue([])
  wrapper = mount(component, { global: { stubs: { StaffHeader: true, FilterDropdown: true } } })
  await flushPromises()
  await click('เริ่มขายบริการ')
  expect(wrapper.findAll('.payment-options button').map((button) => button.text())).toEqual(['พร้อมเพย์'])
})

it('keeps a failed POST on the form without opening a receipt', async () => {
  failedPost = true
  await pay()
  expect(push).not.toHaveBeenCalled()
  expect(wrapper.text()).not.toContain('บันทึกการขายสำเร็จ')
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
})
it('keeps a successful POST locked when navigation fails and offers the saved receipt', async () => {
  push.mockRejectedValueOnce(new Error('Navigation failed'))
  await pay()
  expect(wrapper.text()).toContain('บันทึกการขายสำเร็จ')
  await click('ดูและพิมพ์ใบเสร็จ')
  expect(push).toHaveBeenLastCalledWith('/staff/receipts/3')
  expect(
    vi
      .mocked(fetch)
      .mock.calls.filter(
        ([url, init]) => String(url) === '/api/payments' && init?.method === 'POST',
      ),
  ).toHaveLength(1)
})
