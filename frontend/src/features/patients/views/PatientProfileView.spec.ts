import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getPatient, updatePatient, receiptHistory, courseHistory, treatmentHistory } = vi.hoisted(() => ({
  getPatient: vi.fn(),
  updatePatient: vi.fn(),
  receiptHistory: vi.fn(),
  courseHistory: vi.fn(),
  treatmentHistory: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  patientApi: { get: getPatient, update: updatePatient },
  salesApi: { list: receiptHistory },
  courseApi: { patientHistory: courseHistory },
  treatmentApi: { historyByPatient: treatmentHistory },
  masterDataApi: { list: vi.fn().mockResolvedValue([]) },
}))

vi.mock('vue-router', async () => {
  const { ref } = await import('vue')
  const routeId = ref('91')
  const params = Object.defineProperty({}, 'id', { get: () => routeId.value })
  return {
    useRoute: () => ({ params }),
    setTestRouteId: (id: string) => { routeId.value = id },
  }
})

import PatientProfileView from './PatientProfileView.vue'

enableAutoUnmount(afterEach)

async function setTestRouteId(id: string) {
  const mockedRouter = await import('vue-router') as typeof import('vue-router') & { setTestRouteId: (nextId: string) => void }
  mockedRouter.setTestRouteId(id)
}

describe('patient profile update contract', () => {
  beforeEach(async () => {
    await setTestRouteId('91')
    vi.clearAllMocks()
    getPatient.mockResolvedValue({
      id: 91,
      hn: '2699099991',
      fullName: 'Patient A',
      phone: '0899999991',
      active: true,
      branchId: 99,
      branchCode: '99',
      branchName: 'Test branch',
      branch: { id: 99, code: '99', name: 'Test branch', active: true },
      title: 'นาย',
      firstName: 'Patient',
      lastName: 'A',
      birthDate: '2000-01-01',
      gender: 'ชาย',
      identityDocumentType: 'NATIONAL_ID',
      identityNumber: '1234567890123',
      address: '1 Test Road',
      drugAllergy: false,
      foodAllergy: false,
      chronicDisease: '',
      emergencyContactName: '',
      emergencyContactRelationship: '',
      emergencyContactPhone: '',
      privacyConsent: true,
      customerType: 'Thai',
      customerGroup: 'ลูกค้าทั่วไป',
      customerSource: 'หน้าสาขา',
    })
    updatePatient.mockResolvedValue({ id: 91, hn: '2699099991', fullName: 'Edited Patient', branchId: 99, branchCode: '99', branchName: 'Test branch', branch: { id: 99, code: '99', name: 'Test branch', active: true }, phone: '0899999991', active: true })
    receiptHistory.mockResolvedValue({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 })
    courseHistory.mockResolvedValue({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 })
    treatmentHistory.mockResolvedValue({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 })
  })

  it('sends the existing branch code when saving an edited profile', async () => {
    const wrapper = mount(PatientProfileView, {
      global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] },
    })
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updatePatient).toHaveBeenCalledWith(
      91,
      expect.objectContaining({ branchCode: '99', firstName: 'Patient', lastName: 'A' }),
    )
    const sent = updatePatient.mock.calls[0]![1]
    expect(sent).not.toHaveProperty('email')
    expect(sent).not.toHaveProperty('privacyConsent')
    expect(sent).not.toHaveProperty('emergencyContactName')
    expect(wrapper.text()).toContain('บันทึกข้อมูลผู้ป่วยแล้ว')
    expect(wrapper.text()).toContain('HN 2699099991')
  })

  it('keeps the editor open and reports the API error when save fails', async () => {
    updatePatient.mockRejectedValueOnce(new Error('บันทึกไม่สำเร็จ'))
    const wrapper = mount(PatientProfileView, {
      global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] },
    })
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('form.patient-edit').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('บันทึกไม่สำเร็จ')
    expect(wrapper.text()).not.toContain('บันทึกข้อมูลผู้ป่วยแล้ว')
  })

  it('loads real patient scoped receipt, course, and care history with page controls', async () => {
    receiptHistory.mockResolvedValueOnce({ items: [{ id: 7, receiptNo: 'RE-0000007', soldAt: '2026-10-01', total: 1200, status: 'PAID' }], page: 0, size: 10, totalItems: 11, totalPages: 2 })
    courseHistory.mockResolvedValueOnce({ items: [{ id: 8, course: { name: 'Course A' }, purchasedAt: '2026-10-02', price: 5000, remaining: 3, status: 'Active' }], page: 0, size: 10, totalItems: 1, totalPages: 1 })
    treatmentHistory.mockResolvedValueOnce({ items: [{ id: 9, patientId: 91, service: 'Therapy A', note: 'Synthetic care record', createdAt: '2026-10-03T10:00:00' }], page: 0, size: 10, totalItems: 1, totalPages: 1 })
    const wrapper = mount(PatientProfileView, {
      global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] },
    })
    await flushPromises()

    expect(receiptHistory).toHaveBeenCalledWith({ patientId: 91, page: 0, size: 10 })
    expect(courseHistory).toHaveBeenCalledWith(91, 0, 10)
    expect(treatmentHistory).toHaveBeenCalledWith(91, 0, 10)
    expect(wrapper.text()).toContain('RE-0000007')
    expect(wrapper.text()).toContain('Course A')
    expect(wrapper.text()).toContain('Synthetic care record')

    await wrapper.get('[aria-labelledby="receipt-history-heading"] button:last-child').trigger('click')
    await flushPromises()
    expect(receiptHistory).toHaveBeenLastCalledWith({ patientId: 91, page: 1, size: 10 })
  })

  it('shows real empty states and a history request error without fallback records', async () => {
    courseHistory.mockRejectedValueOnce(new Error('history unavailable'))
    const wrapper = mount(PatientProfileView, {
      global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('ยังไม่มีประวัติใบเสร็จ')
    expect(wrapper.text()).toContain('ยังไม่มีประวัติการรักษา')
    expect(wrapper.text()).toContain('history unavailable')
    expect(wrapper.text()).not.toContain('ตัวอย่าง')
  })

  it('reloads on patient route change and ignores the late response for the prior patient', async () => {
    let resolvePreviousPatient: ((value: unknown) => void) | undefined
    getPatient.mockImplementationOnce(() => new Promise((resolve) => { resolvePreviousPatient = resolve }))
    getPatient.mockResolvedValueOnce({
      id: 92,
      hn: '2699099992',
      fullName: 'Patient B',
      phone: '0899999992',
      active: true,
      branch: { id: 99, code: '99', name: 'Test branch', active: true },
      title: 'นาย',
      firstName: 'Patient',
      lastName: 'B',
      birthDate: '2000-01-01',
      gender: 'ชาย',
      identityDocumentType: 'NATIONAL_ID',
      identityNumber: '1234567890124',
      address: '2 Test Road',
    })
    const wrapper = mount(PatientProfileView, {
      global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] },
    })
    await setTestRouteId('92')
    await nextTick()
    await flushPromises()
    expect(getPatient).toHaveBeenLastCalledWith(92)
    await vi.waitFor(() => expect(wrapper.get('h1').text()).toContain('Patient B'))
    expect(receiptHistory).toHaveBeenLastCalledWith({ patientId: 92, page: 0, size: 10 })

    resolvePreviousPatient?.({ id: 91, hn: '2699099991', fullName: 'Patient A' })
    await flushPromises()
    expect(wrapper.get('h1').text()).toContain('Patient B')
    expect(wrapper.get('h1').text()).not.toContain('Patient A')
    expect(receiptHistory).not.toHaveBeenCalledWith({ patientId: 91, page: 0, size: 10 })
  })
})
