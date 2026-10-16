import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CommissionPaymentsView from '../views/CommissionPaymentsView.vue'
import CommissionCompensationSettingsView from '../views/CommissionCompensationSettingsView.vue'
import CommissionAdjustmentsView from '../views/CommissionAdjustmentsView.vue'
import TherapistCompensationView from '../views/TherapistCompensationView.vue'
import RefundView from '../../refunds/views/RefundView.vue'

const route = vi.hoisted(() => ({ query: {} as Record<string, string>, params: {} }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' },
}))

type Call = { url: string; method: string; body?: Record<string, unknown> }
let calls: Call[]
let responses: Record<string, unknown>
let failing: Record<string, string>
let wrapper: VueWrapper
// the drawers are teleported to <body>, so the page is read from the document
const page = () => new DOMWrapper(document.body)

const stubs = {
  StaffHeader: true,
  ThaiDatePicker: true,
  CommissionTabs: true,
  FilterDropdown: true,
  RouterLink: { template: '<a><slot /></a>' },
}

const payment = (over: Record<string, unknown> = {}) => ({
  id: 1,
  staffId: 7,
  staffName: 'Therapist Demo',
  month: '2026-10',
  status: 'OPEN',
  courseCommission: 75,
  singleVisitFee: 45,
  substituteFee: 0,
  adjustment: 0,
  net: 120,
  paidAt: null,
  closingId: null,
  pendingSessions: 0,
  applications: [],
  ...over,
})

beforeEach(() => {
  route.query = {}
  calls = []
  failing = {}
  responses = {}
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      const body = init?.body ? JSON.parse(String(init.body)) : undefined
      calls.push({ url, method, body })
      const key = `${method} ${url.split('?')[0]}`
      if (failing[key])
        return new Response(JSON.stringify({ message: failing[key] }), { status: 409 })
      const found = responses[key] ?? responses[`${method} ${url}`]
      return new Response(JSON.stringify(found ?? (method === 'GET' ? [] : {})), { status: 200 })
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

async function mountView(component: object) {
  wrapper = mount(component, { global: { stubs }, attachTo: document.body })
  await flushPromises()
}
async function click(text: string) {
  const button = page()
    .findAll('button')
    .find((b) => b.text().includes(text))
  expect(button, text).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}

describe('monthly payments page', () => {
  it('shows an uncalculated commission as waiting, never as 0, and cannot close it', async () => {
    responses['GET /api/commission/payments'] = [
      payment({ courseCommission: null, net: null, pendingSessions: 3 }),
    ]
    await mountView(CommissionPaymentsView)
    const row = page().find('tbody tr')
    expect(row.text()).toContain('รอคำนวณ')
    expect(row.text()).toContain('3 ครั้งที่เดือนขายยังไม่ปิดยอด')
    const cells = row.findAll('td')
    expect(cells[2]!.text()).not.toContain('฿') // commission: waiting, not 0
    expect(cells[6]!.text()).not.toContain('฿') // net: waiting, not 0
    const close = row.findAll('button').find((b) => b.text() === 'ปิดงวด')!
    expect(close.attributes('disabled')).toBeDefined()
  })

  it('closes an Open payment and pays a Closed one only after confirming', async () => {
    responses['GET /api/commission/payments'] = [payment({ status: 'CLOSED' })]
    await mountView(CommissionPaymentsView)
    await click('ยืนยันจ่าย')
    expect(calls.some((c) => c.url.endsWith('/pay'))).toBe(false)
    expect(page().find('.confirm-modal').text()).toContain('Finalize')
    await page().find('.confirm-modal .confirm').trigger('click')
    await flushPromises()
    expect(
      calls.filter((c) => c.method === 'POST' && c.url === '/api/commission/payments/1/pay'),
    ).toHaveLength(1)
  })

  it('sends the payment date and reference the Manager entered', async () => {
    responses['GET /api/commission/payments'] = [payment({ status: 'CLOSED' })]
    await mountView(CommissionPaymentsView)
    await click('ยืนยันจ่าย')
    await page().find('input[aria-label="วันที่จ่าย"]').setValue('2026-10-05')
    await page().find('input[aria-label="เลขที่อ้างอิง"]').setValue('TRF-001')
    await page().find('.confirm-modal .confirm').trigger('click')
    await flushPromises()
    const pay = calls.find((c) => c.method === 'POST' && c.url === '/api/commission/payments/1/pay')
    expect(pay?.body).toEqual({ paidDate: '2026-10-05', reference: 'TRF-001' })
  })

  it('shows the payment date and reference of a paid row', async () => {
    responses['GET /api/commission/payments'] = [
      payment({ status: 'PAID', paidDate: '2026-10-05', paymentReference: 'TRF-001' }),
    ]
    await mountView(CommissionPaymentsView)
    expect(page().find('tbody tr').text()).toContain('อ้างอิง TRF-001')
  })

  it('shows the server refusal of an action', async () => {
    responses['GET /api/commission/payments'] = [payment()]
    failing['POST /api/commission/payments/1/close'] = 'ยังปิดงวดไม่ได้'
    await mountView(CommissionPaymentsView)
    await click('ปิดงวด')
    expect(page().find('[role="alert"]').text()).toBe('ยังปิดงวดไม่ได้')
  })

  it('prepares the payments of the chosen month', async () => {
    responses['POST /api/commission/payments/prepare'] = []
    await mountView(CommissionPaymentsView)
    await click('เตรียม/คำนวณงวดจ่าย')
    expect(
      calls.some(
        (c) => c.method === 'POST' && c.url.startsWith('/api/commission/payments/prepare?month='),
      ),
    ).toBe(true)
  })
})

describe('adjustments page', () => {
  const adjustment = {
    id: 5,
    staffId: 7,
    staffName: 'Therapist Demo',
    originalMonth: '2026-10',
    appliedMonth: '2026-11',
    amount: -140,
    reason: 'บันทึกผิดคน',
    status: 'Approved',
    processed: 0,
    remaining: 140,
    allocationId: 9,
    singleVisitFeeId: null,
    courseId: null,
    visitId: null,
    saleTransactionId: null,
    applications: [
      {
        id: 3,
        adjustmentId: 5,
        amount: -140,
        status: 'Draft',
        approved: false,
        approvalReference: null,
      },
    ],
  }

  it('lists adjustments with a minus sign and what is left', async () => {
    responses['GET /api/commission/adjustments'] = [adjustment]
    await mountView(CommissionAdjustmentsView)
    const row = page().find('tbody tr')
    expect(row.text()).toContain('−฿140.00')
    expect(row.text()).toContain('อนุมัติแล้ว')
  })

  it('needs the consent reference before a deduction is approved', async () => {
    responses['GET /api/commission/adjustments'] = [adjustment]
    await mountView(CommissionAdjustmentsView)
    await click('รายละเอียด')
    // the reference box exists for a deduction; approving without it is sent without a reference and the server refuses
    failing['POST /api/commission/adjustment-applications/3/approve'] =
      'การหักเงินต้องระบุเลขอ้างอิง'
    const approve = page()
      .findAll('.detail table button')
      .find((b) => b.text() === 'อนุมัติ')!
    await approve.trigger('click')
    await flushPromises()
    expect(page().find('[role="alert"]').text()).toContain('เลขอ้างอิง')
    expect(
      calls.find((c) => c.url.endsWith('/adjustment-applications/3/approve'))?.body?.reference,
    ).toBe('')
  })

  it('refuses to save an incomplete adjustment without calling the server', async () => {
    await mountView(CommissionAdjustmentsView)
    await click('+ สร้างใบปรับยอด')
    await click('บันทึกและนำไปใช้ในงวด')
    expect(page().find('.drawer-error').text()).toContain('กรุณาเลือกพนักงาน')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('asks for the consent reference when a deduction is applied in the same step', async () => {
    responses['GET /api/staff'] = [
      { id: 7, userId: 1, username: 't', role: 'THERAPIST', employeeCode: 'T1', fullName: 'Therapist Demo', active: true },
    ]
    await mountView(CommissionAdjustmentsView)
    await click('+ สร้างใบปรับยอด')
    const numbers = page().findAll('.rule-form input[type="number"]')
    await numbers[0]!.setValue('50')
    await numbers[1]!.setValue('12')
    await page().find('.rule-form input[maxlength="255"]').setValue('ผู้รักษาผิดคน')
    await click('บันทึกและนำไปใช้ในงวด')
    expect(page().find('.drawer-error').text()).toContain('เลขที่อ้างอิงความยินยอม')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })
})

describe('treatment fee rules page', () => {
  const rule = {
    id: 1,
    feeType: 'Fixed',
    feeValue: 45,
    effectiveFrom: '2026-01-01',
    effectiveTo: null,
    enabled: true,
    used: true,
  }

  it('lists the rule and locks type and value of a rule already used', async () => {
    responses['GET /api/commission/treatment-fees'] = [rule]
    await mountView(CommissionCompensationSettingsView)
    expect(page().find('.rule-card').text()).toContain('฿45')
    expect(page().find('.rule-card').text()).toContain('ถูกใช้คำนวณแล้ว')
    await click('แก้ไข')
    expect(page().find('.rule-drawer select').attributes('disabled')).toBeDefined()
    expect(page().find('.rule-drawer input[type="number"]').attributes('disabled')).toBeDefined()
  })

  it('rejects a percentage above 100 before calling the server', async () => {
    responses['GET /api/commission/treatment-fees'] = []
    await mountView(CommissionCompensationSettingsView)
    await click('+ เพิ่มกฎค่ามือ')
    await page().find('.rule-drawer select').setValue('Percentage')
    await page().find('.rule-drawer input[type="number"]').setValue(150)
    await click('บันทึกกฎ')
    expect(page().find('.drawer-error').text()).toContain('0 ถึง 100')
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('sends a new fixed rule', async () => {
    responses['GET /api/commission/treatment-fees'] = []
    await mountView(CommissionCompensationSettingsView)
    await click('+ เพิ่มกฎค่ามือ')
    await page().find('.rule-drawer input[type="number"]').setValue(60)
    await click('บันทึกกฎ')
    const post = calls.find(
      (c) => c.method === 'POST' && c.url === '/api/commission/treatment-fees',
    )
    expect(post?.body).toMatchObject({ feeType: 'Fixed', feeValue: 60 })
  })
})

describe('my compensation page (therapist)', () => {
  const line = (over: Record<string, unknown> = {}) => ({
    kind: 'COMMISSION', id: 1, date: '2026-10-03', patientName: 'สมชาย ใจดี', patientHn: '2601080022',
    item: 'Manual Therapy', role: 'เจ้าของคอร์ส + ผู้รักษา', amount: 92.31, reversal: false, pending: false,
    paymentStatus: 'OPEN', courseNo: 'C1', totalVisits: 13, grossAmount: 1500, lineDiscount: 300, base: 1200,
    rate: 0.1, tier: 'Tier 2', pool: 1200, saleNo: 'RE1', saleDate: '2026-10-01', saleType: 'คอร์ส', feeType: null,
    feeValue: null, feeBase: null, ownerName: null, treatingName: null, visitNo: null, singleVisit: false, ...over,
  })

  it('shows the amounts of the latest month, the lines behind them and waiting when not calculated', async () => {
    responses['GET /api/commission/my-payments'] = [
      payment({ month: '2026-10', courseCommission: null, net: null, pendingSessions: 2, adjustment: -140 }),
      payment({ id: 2, month: '2026-09', status: 'PAID', paidAt: '2026-10-05T03:00:00Z' }),
    ]
    responses['GET /api/commission/my-lines'] = {
      month: '2026-10', courseCommission: null, singleVisitFee: 45, substituteFee: 0, pendingSessions: 2, lines: [line()],
    }
    await mountView(TherapistCompensationView)
    const summary = page().find('.therapist-summary-card').text()
    expect(summary).toContain('รอคำนวณ')
    expect(summary).toContain('2 ครั้งที่เดือนขายคอร์สยังไม่ปิดยอด')
    expect(summary).toContain('−฿140.00') // a deduction keeps its sign in front of the baht sign
    expect(page().find('.therapist-line-row').text()).toContain('฿1,200.00 ÷ 13')
    expect(page().find('.therapist-line-row').text()).toContain('฿92.31')
  })

  it('lists only the paid months in the history', async () => {
    route.query = { tab: 'payments' }
    responses['GET /api/commission/my-payments'] = [
      payment({ month: '2026-10', courseCommission: null, net: null, pendingSessions: 2 }),
      payment({ id: 2, month: '2026-09', status: 'PAID', paidAt: '2026-10-05T03:00:00Z' }),
    ]
    await mountView(TherapistCompensationView)
    const rows = page().findAll('.therapist-payment-row')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.text()).toContain('฿120.00')
  })

  it('says so when there is nothing to show yet', async () => {
    await mountView(TherapistCompensationView)
    expect(page().text()).toContain('ยังไม่มีรายการค่าตอบแทนในเดือนนี้')
  })
})

describe('refund by course page', () => {
  const sale = {
    id: 11,
    receiptId: 21,
    receiptNo: 'RE2610',
    patient: { id: 4, hn: 'HN4', fullName: 'Patient', phone: '', active: true },
    branchCode: '01',
    total: 9000,
    paid: 9000,
    status: 'PAID',
    soldAt: '2026-10-01T00:00:00Z',
  }
  const detail = {
    ...sale,
    items: [],
    payments: [{ id: 31, type: 'PAYMENT', paymentMethodCode: 'Cash', amount: 9000 }],
  }
  const course = {
    id: 41,
    course: { id: 1, code: 'C1', name: 'คอร์ส 6 ครั้ง' },
    price: 9000,
    remaining: 5,
    receiptId: 21,
  }

  async function chooseReceipt() {
    responses['GET /api/payments'] = {
      items: [sale],
      page: 0,
      size: 100,
      totalItems: 1,
      totalPages: 1,
    }
    responses['GET /api/receipts/11'] = detail
    responses['GET /api/patients/4/courses'] = [course, { ...course, id: 42, receiptId: 99 }]
    await mountView(RefundView)
    await page().find('button[aria-label="เลือกใบเสร็จ RE2610"]').trigger('click')
    await flushPromises()
  }

  it('lists only the courses of the chosen receipt', async () => {
    await chooseReceipt()
    expect(page().findAll('.courses tbody tr')).toHaveLength(1)
    expect(page().find('.courses').text()).toContain('คอร์ส 6 ครั้ง')
  })

  it('requires a reason and a chosen course, then sends the amount and the sessions per course', async () => {
    await chooseReceipt()
    await click('บันทึกคำขอคืนเงิน')
    expect(page().find('.page-error').text()).toContain('ระบุเหตุผล')
    await page().find('input[maxlength="255"]').setValue('ผู้ป่วยขอคืน')
    await click('บันทึกคำขอคืนเงิน')
    expect(page().find('.page-error').text()).toContain('เลือกคอร์ส')
    await page().find('.courses input[type="checkbox"]').setValue(true)
    const numbers = page().findAll('.courses input[type="number"]')
    await numbers[0]!.setValue(1500)
    await numbers[1]!.setValue(5)
    await click('บันทึกคำขอคืนเงิน')
    const post = calls.find((c) => c.method === 'POST' && c.url === '/api/payments/31/refunds')
    expect(post?.body).toEqual({
      amount: 1500,
      paymentMethodCode: 'Cash',
      reason: 'ผู้ป่วยขอคืน',
      items: [{ courseId: 41, refundAmount: 1500, cancelledVisits: 5 }],
    })
  })
})
