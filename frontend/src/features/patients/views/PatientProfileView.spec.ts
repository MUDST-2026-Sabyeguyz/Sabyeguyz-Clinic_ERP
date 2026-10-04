import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getPatient, updatePatient } = vi.hoisted(() => ({
  getPatient: vi.fn(),
  updatePatient: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  patientApi: { get: getPatient, update: updatePatient },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '91' } }),
}))

import PatientProfileView from './PatientProfileView.vue'

describe('patient profile update contract', () => {
  beforeEach(() => {
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
    })
    updatePatient.mockResolvedValue({ id: 91, fullName: 'Patient A' })
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
      expect.objectContaining({ branchCode: '99' }),
    )
  })
})
