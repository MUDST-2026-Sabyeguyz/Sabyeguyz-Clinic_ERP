import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { registerPatient, branchList, masterDataList, navigate } = vi.hoisted(() => ({
  registerPatient: vi.fn(),
  branchList: vi.fn(),
  masterDataList: vi.fn(),
  navigate: vi.fn(),
}))

vi.mock('@/api/patients', () => ({ registerPatient }))
vi.mock('@/api/client', () => ({
  branchApi: { list: branchList },
  masterDataApi: { list: masterDataList },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigate }) }))

import PatientRegistrationView from './PatientRegistrationView.vue'

enableAutoUnmount(afterEach)

describe('patient registration field contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    branchList.mockResolvedValue([{ code: '91', name: 'Test branch', active: true }])
    masterDataList.mockResolvedValue([
      { category: 'Customer Group', name: 'Walk-in', active: true },
      { category: 'Referral Channel', name: 'Branch', active: true },
    ])
    registerPatient.mockResolvedValue({ id: 12, hn: '2691100012' })
  })

  it('submits the separate names and allows confirmed optional fields to stay empty', async () => {
    const wrapper = mount(PatientRegistrationView, { global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] } })
    await flushPromises()
    await wrapper.get('input[placeholder="ชื่อ"]').setValue('Ada')
    await wrapper.get('input[placeholder="นามสกุล"]').setValue('Lovelace Byron')
    await wrapper.get('input[placeholder="เบอร์โทร"]').setValue('0812345678')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(registerPatient).toHaveBeenCalledWith(expect.objectContaining({
      branchCode: '91',
      firstName: 'Ada',
      lastName: 'Lovelace Byron',
      birthDate: null,
      patientType: 'Thai',
      identityNumber: '',
      address: null,
      nickname: '',
      customerType: 'Walk-in',
    }))
    const payload = registerPatient.mock.calls[0]![0]
    expect(payload).not.toHaveProperty('email')
    expect(payload).not.toHaveProperty('privacyConsent')
    expect(payload).not.toHaveProperty('emergencyContactPhone')
    expect(payload).not.toHaveProperty('username')
    expect(navigate).toHaveBeenCalledWith('/staff/patients')
    expect(wrapper.text()).not.toContain('อีเมล')
  })

  it.each(['Thai', 'Foreigner'])('submits explicit %s with passport without a birthday', async (type) => {
    const wrapper = mount(PatientRegistrationView, { global: { stubs: ['StaffHeader', 'ThaiDatePicker', 'RouterLink'] } })
    await flushPromises()
    await wrapper.get('input[placeholder="ชื่อ"]').setValue('Alex')
    await wrapper.get('input[placeholder="นามสกุล"]').setValue('Example')
    await wrapper.get('input[placeholder="เบอร์โทร"]').setValue('0812345678')
    await wrapper.get('input[aria-label="สัญชาติ"]').setValue('Canada')
    await wrapper.get('select[aria-label="ประเภทผู้ป่วย"]').setValue(type)
    const documentSelect = wrapper.findAll('select').find((select) => select.find('option[value="PASSPORT"]').exists())!
    await documentSelect.setValue('PASSPORT')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(registerPatient).toHaveBeenLastCalledWith(expect.objectContaining({ patientType: type, nationality: 'Canada', identityDocumentType: 'PASSPORT', birthDate: null }))
    expect(wrapper.text()).not.toContain('D-05')
  })

})
