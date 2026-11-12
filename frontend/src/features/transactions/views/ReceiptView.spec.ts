import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import View from './ReceiptView.vue'
const get = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '11' } }),
  useRouter: () => ({ back: vi.fn() }),
}))
vi.mock('@/api/client', () => ({
  salesApi: { get },
  patientApi: {
    get: async () => ({
      identityNumber: '1234567890123',
      phone: '0812345678',
      address: 'Test patient address',
    }),
  },
  branchApi: {
    list: async () => [
      { code: '01', name: 'Transaction branch', address: 'Test address', phone: '020000000' },
    ],
  },
}))
describe('receipt main response', () => {
  it('opens with saleId and renders missing price snapshots and payment time as unknown', async () => {
    get.mockResolvedValue({
      id: 11,
      receiptId: 99,
      receiptNo: 'TEST99',
      branchCode: '01',
      patient: { id: 9, fullName: 'Test', hn: 'TEST9' },
      status: 'PAID',
      soldAt: '2026-10-31T02:00:00Z',
      paymentLabel: 'FullyPaid',
      subtotalBeforeDiscount: 800,
      billDiscountAmount: 0,
      netAmount: 800,
      paid: 800,
      items: [{ id: 11, type: 'SingleVisit', description: 'Service', quantity: 1, lineTotal: 800 }],
      payments: [{ id: 22, type: 'PAYMENT', paymentMethodCode: 'BankTransfer', amount: 800 }],
    })
    const view = mount(View, { global: { stubs: { StaffHeader: true } } })
    await flushPromises()
    expect(get).toHaveBeenCalledWith(11)
    expect(view.text()).toContain('Transaction branch')
    expect(view.text()).toContain('TEST99')
    expect(view.text()).not.toContain('เลขที่อ้างอิง')
    expect(view.text()).not.toContain('เลขประจำตัวผู้เสียภาษี')
    expect(view.text()).not.toMatch(/NaN|undefined|Invalid Date/)
    expect(view.findAll('tbody td').map((cell) => cell.text())).toContain('—')
    view.unmount()
  })
})
