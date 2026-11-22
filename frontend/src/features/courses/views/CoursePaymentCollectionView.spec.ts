import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from './CoursePaymentCollectionView.vue'

const mock = vi.hoisted(() => ({
  collect: vi.fn(), history: vi.fn(), push: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { patientId: '2' } }),
  useRouter: () => ({ push: mock.push, back: vi.fn() }),
}))
vi.mock('@/features/staff/data/managerBranch', () => ({
  selectedManagerBranch: () => ({ code: '01', name: 'สาขาทดสอบ' }),
}))
vi.mock('@/api/client', () => ({
  branchApi: { list: async () => [{ code: '01', name: 'สาขาทดสอบ' }] },
  patientApi: { get: async () => ({ id: 2, hn: 'SYN2', fullName: 'ผู้ป่วยทดสอบ' }) },
  courseApi: {
    patientCourses: async () => [
      { id: 21, patient: { id: 2 }, purchaser: { id: 2 }, course: { name: 'คอร์ส A' }, remaining: 3, purchased: 10, expiresAt: '2027-01-01' },
      { id: 22, patient: { id: 2 }, purchaser: { id: 2 }, course: { name: 'คอร์ส B' }, remaining: 5, purchased: 10, expiresAt: '2027-02-01' },
      { id: 23, patient: { id: 2 }, purchaser: { id: 9 }, course: { name: 'คอร์สของผู้ซื้ออื่น' }, remaining: 5, purchased: 10, expiresAt: '2027-02-01' },
    ],
    receiptHistory: mock.history,
  },
  paymentMethodApi: { list: async () => [
    { code: 'BankTransfer', name: 'โอนธนาคาร', active: true },
    { code: 'Cash', name: 'เงินสด', active: true },
  ] },
  paymentStatusApi: { list: async () => [{ code: 'INSTALLMENT', label: 'ผ่อนชำระ', enabled: true }] },
  coursePaymentCollectionApi: { collect: mock.collect },
}))

describe('existing course payment collection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER', branchCode: '01' }))
    mock.history.mockResolvedValue([])
    mock.collect.mockResolvedValue({ receiptId: 80, paymentId: 90, receiptNo: 'R-80', netAmount: 1500 })
  })

  it('previews multiple courses bought by the same patient, sends one collection and opens its receipt', async () => {
    const view = mount(View, { global: { stubs: { StaffHeader: true } } })
    await flushPromises()
    expect(view.text()).not.toContain('คอร์สของผู้ซื้ออื่น')
    const checkboxes = view.findAll('input[type="checkbox"]')
    await checkboxes[0]!.setValue(true)
    await checkboxes[1]!.setValue(true)
    await flushPromises()
    const amounts = view.findAll('input[type="number"]')
    await amounts[0]!.setValue('1000.00')
    await amounts[1]!.setValue('500.00')
    const reviewButton = view.findAll('button').find((button) => button.text() === 'ตรวจสอบก่อนยืนยัน')
    expect(reviewButton).toBeDefined()
    await reviewButton!.trigger('click')
    await flushPromises()
    expect(view.text()).toContain('ตรวจสอบก่อนยืนยัน')
    const confirm = view.findAll('button').find((button) => button.text().includes('ยืนยันรับเงิน'))
    expect(confirm).toBeDefined()
    mock.collect.mockRejectedValueOnce(new Error('connection lost'))
    await confirm!.trigger('click')
    await flushPromises()
    expect(view.find('[role="alert"]').text()).toContain('ลองยืนยันซ้ำด้วยข้อมูลเดิม')
    const firstKey = mock.collect.mock.calls[0]![1]
    await confirm!.trigger('click')
    await flushPromises()
    expect(mock.collect).toHaveBeenCalledTimes(2)
    const [request, key] = mock.collect.mock.calls[1]!
    expect(request.items).toEqual([{ courseId: 21, amount: 1000 }, { courseId: 22, amount: 500 }])
    expect(request.paymentAmount).toBe(1500)
    expect(request.branchCode).toBe('01')
    expect(key).toMatch(/^[0-9a-f-]{36}$/i)
    expect(key).toBe(firstKey)
    expect(view.text()).toContain('R-80')
    const receiptLink = view.findAll('button').find((button) => button.text() === 'ดูและพิมพ์ใบเสร็จ')
    await receiptLink!.trigger('click')
    expect(mock.push).toHaveBeenCalledWith({ name: 'staff-course-payment-receipt', params: { id: 80 } })
    view.unmount()
  })

  it('ignores a second confirmation while the original UI request is pending', async () => {
    let resolve!: (value: unknown) => void
    mock.collect.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const view = mount(View, { global: { stubs: { StaffHeader: true } } })
    await flushPromises()
    await view.findAll('input[type="checkbox"]')[0]!.setValue(true)
    await flushPromises()
    await view.find('input[type="number"]').setValue('1000')
    await view.findAll('button').find(button => button.text() === 'ตรวจสอบก่อนยืนยัน')!.trigger('click')
    const confirm = view.findAll('button').find(button => button.text().includes('ยืนยันรับเงิน'))!
    await confirm.trigger('click')
    await confirm.trigger('click')
    expect(mock.collect).toHaveBeenCalledTimes(1)
    expect(confirm.attributes('disabled')).toBeDefined()
    resolve({ receiptId: 80, paymentId: 90, receiptNo: 'R-80', netAmount: 1000 })
    await flushPromises()
    expect(view.text()).toContain('รับเงินสำเร็จ')
    expect(mock.collect).toHaveBeenCalledTimes(1)
    view.unmount()
  })
})
