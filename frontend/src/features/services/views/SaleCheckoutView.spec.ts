import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setManagerBranches, setSelectedManagerBranch } from '@/features/staff/data/managerBranch'
import View from './SaleCheckoutView.vue'
import { ApiError } from '@/api/client'

const mock = vi.hoisted(() => ({ create: vi.fn(), use: vi.fn(), push: vi.fn() }))
const bookingId = ref<number | null>(55)
const booking = ref<{ branchCode: string } | null>({ branchCode: '01' })
const progressState = ref<Record<string, unknown> | null>(null)
const canStart = ref(true)
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { patientId: '9', appointmentId: '55' } }),
  useRouter: () => ({ push: mock.push }),
}))
vi.mock('@/features/appointments/useAppointmentProcess', () => ({
  useAppointmentProcess: () => ({
    appointmentId: bookingId,
    sourceAppointment: booking,
    appointmentProgress: progressState,
    processLoading: ref(false),
    processError: ref(''),
    canStartAppointment: canStart,
    processBlockReason: ref(''),
    recoveryRequired: ref(false),
    refreshAppointmentProcess: async () => {},
    beginAppointmentWrite: () => {},
    finishAppointmentWrite: () => {},
    recoverAppointmentWrite: async () => {},
  }),
}))
vi.mock('@/api/client', async (original) => ({
  ...(await original<typeof import('@/api/client')>()),
  salesApi: { create: mock.create },
  patientApi: { get: async () => ({ id: 9, fullName: 'Test', hn: 'TEST9' }) },
  catalogApi: {
    services: async () => [{ id: 7, code: 'SERVICE', name: 'Service', price: 800 }],
    courses: async () => [{ id: 12, name: 'Course', price: 1000 }],
  },
  staffApi: {
    list: async () =>
      [1, 4].map((id) => ({
        id,
        fullName: `PT${id}`,
        active: true,
        position: 'Therapist',
        branchCode: '01',
      })),
  },
  paymentMethodApi: {
    list: async () => [
      { id: 1, code: 'BankTransfer', name: 'BankTransfer', active: true },
      { id: 2, code: 'Cash', name: 'Cash', active: true },
    ],
  },
  paymentStatusApi: { list: async () => [{ code: 'PAID', label: 'Full', enabled: true }] },
  courseApi: {
    use: mock.use,
    patientCourses: async () => [
      { id: 101, course: { name: 'Existing' }, usable: true, remaining: 3, branchCode: '01' },
    ],
  },
}))
type State = {
  lines: Array<{
    key: string
    category: string
    catalogKey: string
    quantity: number
    discountType: string
    discountValue: number
  }>
  sellerId: number
  treatingId: number
  paymentLabel: string
  methodCode: string
  billDiscountType: string
  billDiscountValue: number
  error: string
  uncertainSale: boolean
  save: () => Promise<void>
}
beforeEach(() => {
  vi.clearAllMocks()
  progressState.value = null
  canStart.value = true
  sessionStorage.clear()
  localStorage.setItem(
    'authUser',
    JSON.stringify({ id: 2, staffId: 2, role: 'MANAGER', branchCode: '01' }),
  )
  setManagerBranches([{ code: '01', name: 'Sale branch' }, { code: '02', name: 'Other branch' }])
  setSelectedManagerBranch({ code: '02', name: 'Other branch' })
  bookingId.value = 55
  booking.value = { branchCode: '01' }
  mock.create.mockResolvedValue({ id: 77, receiptId: 88, receiptNo: 'TEST88' })
})
async function render() {
  const view = mount(View, {
    global: {
      stubs: {
        StaffHeader: true,
        RouterLink: true,
        FilterDropdown: true,
        AppointmentProcessPanel: true,
      },
    },
  })
  await flushPromises()
  const state = view.vm as unknown as State
  state.lines = [
    {
      key: 'test',
      category: 'service',
      catalogKey: 'service:7',
      quantity: 1,
      discountType: 'NONE',
      discountValue: 0,
    },
  ]
  state.treatingId = 4
  state.paymentLabel = 'PAID'
  state.methodCode = 'BankTransfer'
  return { view, state }
}
describe('checkout main payload', () => {
  it('submits the sale from the payment button and shows the receipt action', async () => {
    const { view } = await render()
    await view.get('button.primary').trigger('click')
    await flushPromises()
    expect(mock.create).toHaveBeenCalledOnce()
    expect(mock.create.mock.calls[0]![0].requestKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    )
    expect(mock.push).toHaveBeenCalledWith({ name: 'staff-receipt', params: { id: 77 } })
    expect(view.find('h1').text()).toBe('บันทึกการขายสำเร็จ')
    expect(view.text()).toContain('ดูและพิมพ์ใบเสร็จ')
    view.unmount()
  })
  it('warns immediately and hides the net amount when the bill discount exceeds the subtotal', async () => {
    const { view, state } = await render()
    state.billDiscountType = 'FIXED'
    state.billDiscountValue = 10_000_000
    await view.vm.$nextTick()
    expect(view.find('#bill-discount-error').text()).toContain('ส่วนลดต้องไม่เกินยอดหลังส่วนลดรายการ')
    expect(view.find('.net strong').text()).toBe('—')
    await state.save()
    expect(state.error).toBe('ส่วนลดท้ายบิลเกินยอดรายการ')
    expect(mock.create).not.toHaveBeenCalled()
    view.unmount()
  })
  it('hides the seller picker and uses the signed-in therapist for the sale', async () => {
    localStorage.setItem(
      'authUser',
      JSON.stringify({ id: 4, staffId: 4, role: 'THERAPIST', branchCode: '01' }),
    )
    const { view, state } = await render()
    expect(view.text()).not.toContain('ผู้ขาย / เจ้าของคอร์ส')
    expect(state.sellerId).toBe(4)
    await state.save()
    expect(mock.create.mock.calls[0]![0].salespersonStaffId).toBe(4)
    view.unmount()
  })
  it('keeps the successful sale and receipt action visible after progress becomes FOUND', async () => {
    const { view, state } = await render()
    mock.create.mockImplementationOnce(async () => {
      progressState.value = { paymentStatus: 'PAID', saleId: 77 }
      canStart.value = false
      return { id: 77, receiptId: 88, receiptNo: 'TEST88' }
    })
    await state.save()
    await flushPromises()
    expect(view.find('h1').text()).toBe('บันทึกการขายสำเร็จ')
    expect(view.text()).toContain('ดูและพิมพ์ใบเสร็จ')
    await state.save()
    expect(mock.create).toHaveBeenCalledOnce()
    view.unmount()
  })
  it('allows course-only purchase without an appointment and creates one item per course', async () => {
    bookingId.value = null
    booking.value = null
    setSelectedManagerBranch({ code: '01', name: 'Sale branch' })
    const { view, state } = await render()
    state.lines = [{ key: 'courses', category: 'course', catalogKey: 'course:12', quantity: 2, discountType: 'NONE', discountValue: 0 }]
    await state.save()
    expect(state.error).toBe('')
    const request = mock.create.mock.calls[0]![0]
    expect(request.appointmentId).toBeUndefined()
    expect(request.treatingStaffId).toBeUndefined()
    expect(request.items).toEqual([{ type: 'COURSE', referenceId: 12, quantity: 1 }, { type: 'COURSE', referenceId: 12, quantity: 1 }])
    expect(request.payments[0].amount).toBe(2000)
    view.unmount()
  })
  it('uses appointment branch and independently chosen treating staff', async () => {
    const { view, state } = await render()
    await state.save()
    expect(mock.create).toHaveBeenCalledOnce()
    expect(mock.create).toHaveBeenCalledWith(
      expect.objectContaining({
        branchCode: '01',
        salespersonStaffId: 1,
        treatingStaffId: 4,
        appointmentId: 55,
        patientId: 9,
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        items: [{ type: 'SERVICE', referenceId: 7, quantity: 1 }],
      }),
    )
    view.unmount()
  })
  it('hides course usage controls and sells a service without course usages', async () => {
    const { view, state } = await render()
    await view.vm.$nextTick()
    expect(view.text()).not.toContain('สิทธิ์คอร์สเสริมที่ใช้ในครั้งนี้')
    expect(view.text()).not.toContain('เพิ่มคอร์สที่ใช้สิทธิ์')
    await state.save()
    expect(mock.create.mock.calls[0]![0].courseUsages).toBeUndefined()
    expect(mock.use).not.toHaveBeenCalled()
    view.unmount()
  })
  it('requires a selected label and actual treating staff', async () => {
    const { view, state } = await render()
    state.paymentLabel = ''
    await state.save()
    expect(mock.create).not.toHaveBeenCalled()
    state.paymentLabel = 'PAID'
    state.treatingId = 0
    await state.save()
    expect(mock.create).not.toHaveBeenCalled()
    view.unmount()
  })
  it('does not send the same uncertain sale twice', async () => {
    const { view, state } = await render()
    mock.create.mockRejectedValue(new ApiError('conflict', 409))
    await state.save()
    await state.save()
    expect(mock.create).toHaveBeenCalledOnce()
    expect(state.uncertainSale).toBe(true)
    view.unmount()
  })
})
