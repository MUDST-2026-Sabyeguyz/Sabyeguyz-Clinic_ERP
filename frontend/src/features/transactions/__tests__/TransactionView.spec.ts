import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import TransactionView from '../views/TransactionView.vue'

const sale = {
  id: 84,
  receiptNo: 'API-RECEIPT-84',
  patient: { id: 1, hn: 'HN-API', fullName: 'API Patient', phone: '0812345678', active: true },
  branchCode: '01',
  total: 2400,
  paid: 2400,
  status: 'PAID',
  paymentLabel: 'FullyPaid' as string | null,
  soldAt: '2026-10-10T09:00:00',
  salesperson: { id: 2, employeeCode: 'E02', fullName: 'API Staff' },
  itemDescription: 'API Service',
}
const detail = {
  ...sale,
  items: [{ id: 1, type: 'SERVICE', description: 'Backend detail', quantity: 2, lineTotal: 2400 }],
  payments: [{ id: 1, type: 'PAYMENT', paymentMethodCode: 'CASH', amount: 2400 }],
}
const usage = {
  id: 31,
  coursePurchaseId: 7,
  patientName: 'Usage Patient',
  patientHn: 'HN-U',
  courseName: 'Usage Course',
  branchCode: '01',
  usedAt: '2026-10-11T09:00:00',
  voidedAt: null,
  voidReason: null,
}
let wrapper: VueWrapper
let reverseFails: boolean
let empty: boolean
let reversePending: boolean
let resolveReverse: (response: Response) => void
const calls: Array<{ path: string; init?: RequestInit }> = []
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status })

beforeEach(() => {
  sale.paymentLabel = 'FullyPaid'
  detail.paymentLabel = 'FullyPaid'
  detail.payments[0]!.paymentMethodCode = 'CASH'
  reverseFails = false
  reversePending = false
  empty = false
  calls.length = 0
  localStorage.clear()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const path = String(input)
      calls.push({ path, init })
      if (path.includes('/reverse')) {
        if (reversePending)
          return new Promise<Response>((resolve) => {
            resolveReverse = resolve
          })
        if (reverseFails) return response({ message: 'Backend refused reversal' }, 409)
        return response({
          ...detail,
          status: 'VOIDED',
          voidedAt: '2026-10-12T10:00:00',
          voidReason: 'ลงรายการหรือเลือกบริการผิด',
        })
      }
      if (path.startsWith('/api/payments?')) {
        const page = new URL(path, 'http://localhost').searchParams.get('page')
        return response({
          items: empty
            ? []
            : [page === '0' ? sale : { ...sale, id: 85, receiptNo: 'API-SECOND-PAGE' }],
          totalPages: empty ? 1 : 2,
          totalElements: empty ? 0 : 2,
          page: Number(page),
          size: 100,
        })
      }
      if (path === '/api/course-usages') return response(empty ? [] : [usage])
      if (path === '/api/branches')
        return response([
          { id: 1, branchCode: '01', name: 'Backend Branch', address: '', phone: '', active: true },
        ])
      if (path === '/api/receipts/84') return response(detail)
      throw new Error(`Unexpected endpoint ${path}`)
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
async function render() {
  wrapper = mount(TransactionView, {
    attachTo: document.body,
    global: { stubs: { StaffHeader: true } },
  })
  await flushPromises()
}
async function click(text: string) {
  const scope = wrapper.find('[role="alertdialog"]').exists()
    ? wrapper.find('[role="alertdialog"]')
    : wrapper
  const button = scope.findAll('button').find((item) => item.text().replace(/^←\s*/, '') === text)
  expect(button, `button ${text}`).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}
async function review(kind: 'sale' | 'usage' = 'sale') {
  if (kind === 'usage') await click('รายการใช้บริการ')
  await click('ตรวจสอบ')
  await click('ดำเนินการยกเลิก')
  expect(
    wrapper.find('.transaction-reason-actions .transaction-primary').attributes('disabled'),
  ).toBeDefined()
  await wrapper.find('input[name="void-reason"][value="ลงรายการหรือเลือกบริการผิด"]').setValue()
  await click('ตรวจสอบก่อนยืนยัน')
  await click('ยืนยันการยกเลิก')
}

describe('TransactionView backend integration', () => {
  it.each([
    ['FullyPaid', 'ชำระเต็ม'],
    ['Installment', 'ผ่อน'],
    ['Deposit', 'มัดจำ'],
  ])(
    'shows persisted %s independently of PAID lifecycle and fully paid amount',
    async (label, thai) => {
      sale.paymentLabel = label!
      detail.paymentLabel = label!
      await render()
      await click('ตรวจสอบ')
      expect(wrapper.text()).toContain(thai!)
    },
  )
  it('does not invent a payment label for legacy receipts without one', async () => {
    sale.paymentLabel = null
    detail.paymentLabel = null
    await render()
    await click('ตรวจสอบ')
    expect(wrapper.text()).not.toContain('ชำระเต็ม')
    expect(wrapper.text()).not.toContain('มัดจำ')
  })
  it.each(['QR', 'QR_CODE', 'PROMPTPAY', 'PromptPay'])('displays %s as พร้อมเพย์', async (code) => {
    detail.payments[0]!.paymentMethodCode = code
    await render()
    await click('ตรวจสอบ')
    expect(wrapper.text()).toContain('พร้อมเพย์')
  })

  it('prevents entering internal notes that the reversal API cannot persist', async () => {
    await render()
    await click('ตรวจสอบ')
    await click('ดำเนินการยกเลิก')
    expect(wrapper.find('#transaction-note').attributes('disabled')).toBeDefined()
    expect(wrapper.find('label[for="transaction-note"]').text()).toContain('ยังไม่เปิดใช้งาน')
  })
  it('shows backend receipts from every page and resolves branchCode names', async () => {
    await render()
    expect(wrapper.text()).toContain('API-RECEIPT-84')
    expect(wrapper.text()).toContain('API-SECOND-PAGE')
    expect(wrapper.text()).toContain('Backend Branch')
    expect(wrapper.text()).not.toContain('SL-2569-0084')
    await click('รายการใช้บริการ')
    expect(wrapper.text()).toContain('Usage Patient')
  })
  it('shows no seed transactions when the backend is empty', async () => {
    empty = true
    await render()
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('Manual Therapy')
  })
  it('loads backend sale details instead of fabricated contact and quantity', async () => {
    await render()
    await click('ตรวจสอบ')
    expect(wrapper.text()).toContain('Backend detail')
    expect(wrapper.text()).toContain('0812345678')
    expect(wrapper.text()).toContain('CASH')
    expect(wrapper.text()).not.toContain('คอร์ส 10 ครั้ง')
  })
  it.each(['sale', 'usage'] as const)(
    'keeps %s reversal failure out of success and history',
    async (kind) => {
      reverseFails = true
      await render()
      await review(kind)
      await click('ยืนยันการยกเลิก')
      expect(wrapper.text()).toContain('Backend refused reversal')
      expect(wrapper.text()).not.toContain('ยกเลิกรายการสำเร็จ')
      await click('กลับไปแก้ไข')
      await click('กลับหน้าตรวจสอบ')
      await click('กลับหน้ารายการ')
      await click('ประวัติการยกเลิก')
      expect(wrapper.text()).toContain('ยังไม่มีประวัติการยกเลิก')
    },
  )
  it('waits for backend confirmation, retains warning focus/Escape and records success', async () => {
    reversePending = true
    await render()
    await review()
    const back = wrapper.find('.transaction-modal .transaction-secondary')
    expect(document.activeElement).toBe(back.element)
    await back.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    await click('ยืนยันการยกเลิก')
    await click('ยืนยันการยกเลิก')
    expect(wrapper.text()).not.toContain('ยกเลิกรายการสำเร็จ')
    const request = calls.find((call) => call.path === '/api/payments/84/reverse')
    expect(request?.init?.body).toBe(JSON.stringify({ reason: 'ลงรายการหรือเลือกบริการผิด' }))
    resolveReverse(
      response({
        ...detail,
        status: 'VOIDED',
        voidedAt: '2026-10-12T10:00:00',
        voidReason: 'ลงรายการหรือเลือกบริการผิด',
      }),
    )
    await flushPromises()
    expect(wrapper.text()).toContain('ยกเลิกรายการสำเร็จ')
    await click('ดูประวัติการยกเลิก')
    await click('ดูรายละเอียด')
    expect(wrapper.text()).toContain('รายละเอียดประวัติการยกเลิก')
    expect(wrapper.text()).toContain('Backend detail')
  })
})
