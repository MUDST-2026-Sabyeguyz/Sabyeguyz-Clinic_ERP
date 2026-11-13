import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import PatientHistoryView from '../views/PatientHistoryView.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '1' }, query: {} }) }))
let wrapper: VueWrapper
const receipt = {
  id: 3,
  receiptNo: 'R3',
  patient: { id: 1, fullName: 'Patient', hn: 'HN1' },
  total: 100,
  paid: 100,
  status: 'PAID',
  paymentLabel: 'Installment',
  branchCode: '01',
  soldAt: '2026-10-14T10:00:00',
  items: [{ id: 1, description: 'Service', quantity: 1, lineTotal: 100 }],
  payments: [{ id: 1, type: 'PAYMENT', paymentMethodCode: 'QR', amount: 100 }],
}
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})
it('keeps receipt lifecycle separate from persisted payment label and omits unsupported credit', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string) => {
      const path = String(input)
      let body: unknown = []
      if (path === '/api/patients/1')
        body = { ...receipt.patient, branch: { id: 1, name: 'Branch' } }
      else if (path.startsWith('/api/payments?')) body = { items: [receipt], totalPages: 1 }
      else if (path === '/api/receipts/3') body = receipt
      else if (path === '/api/branches/1') body = { id: 1, name: 'Branch', branchCode: '01' }
      return new Response(JSON.stringify(body), { status: 200 })
    }),
  )
  wrapper = mount(PatientHistoryView, {
    global: {
      stubs: { StaffHeader: true, RouterLink: true, FilterDropdown: true, ThaiDatePicker: true },
    },
  })
  await flushPromises()
  expect(wrapper.find('.history-status').text()).toBe('ชำระแล้ว')
  expect(wrapper.text()).toContain('พร้อมเพย์')
  expect(wrapper.find('router-link-stub[to="/staff/receipts/3"]').exists()).toBe(true)
  expect(wrapper.text()).not.toContain('แต้มคงเหลือ')
  expect(wrapper.text()).not.toContain('วงเงินคงเหลือ')
})
