import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from '../views/ManagerPatientRegistrationView.vue'
import TherapistView from '../views/PatientRegistrationView.vue'
import { setSelectedManagerBranch } from '@/features/staff/data/managerBranch'

const mocks = vi.hoisted(() => ({ register: vi.fn() }))
vi.mock('@/api/patients', () => ({ registerPatient: mocks.register }))
vi.mock('@/api/client', () => ({
  branchApi: {
    list: async () => [
      { code: '01', name: 'พระราม 9', active: true },
      { code: '02', name: 'แบริ่ง', active: true },
    ],
  },
  masterDataApi: { list: async () => [] },
  patientApi: { searchAll: async () => [] },
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

describe('Manager patient registration save flow', () => {
  it('locks the Manager branch and updates it when the Header branch changes', async () => {
    localStorage.setItem('authUser', JSON.stringify({ role: 'MANAGER' }))
    setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
    const view = mount(View, {
      global: {
        stubs: {
          StaffHeader: true,
          RouterLink: true,
          ThaiDatePicker: true,
          FilterDropdown: true,
          Teleport: true,
        },
      },
    })
    await flushPromises()
    const state = view.vm as unknown as { form: { branchCode: string } }
    expect(state.form.branchCode).toBe('01')
    expect(
      view
        .findAllComponents({ name: 'FilterDropdown' })
        .find((item) => item.props('label') === 'สาขาที่ลงทะเบียน')
        ?.props('disabled'),
    ).toBe(true)
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    window.dispatchEvent(new Event('manager-branch-changed'))
    await flushPromises()
    expect(state.form.branchCode).toBe('02')
    view.unmount()
  })
  it.each(['01', '02'])(
    'defaults therapist registration to the signed-in branch %s',
    async (branchCode) => {
      localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST', branchCode }))
      const view = mount(TherapistView, {
        global: {
          stubs: {
            StaffHeader: true,
            RouterLink: true,
            ThaiDatePicker: true,
            FilterDropdown: true,
            Teleport: true,
          },
        },
      })
      await flushPromises()
      const state = view.vm as unknown as { form: { branchCode: string } }
      expect(state.form.branchCode).toBe(branchCode)
      const branch = view
        .findAllComponents({ name: 'FilterDropdown' })
        .find((item) => item.props('label') === 'สาขาที่ลงทะเบียน')
      expect(branch?.props('disabled')).toBe(true)
      expect(branch?.props('hideChevron')).toBe(true)
      const referral = view
        .findAllComponents({ name: 'FilterDropdown' })
        .find((item) => item.props('label') === 'ช่องทางแนะนำ')
      expect(referral?.props('modelValue')).toBe('')
      expect(referral?.props('defaultLabel')).toBe('ไม่ระบุ')
      expect(referral?.props('hideDefaultOption')).toBe(false)
      view.unmount()
    },
  )
  it('shows an invalid birth date immediately and clears the warning after correction', async () => {
    const view = mount(View, {
      global: {
        stubs: {
          StaffHeader: true,
          RouterLink: true,
          ThaiDatePicker: true,
          FilterDropdown: true,
          Teleport: true,
        },
      },
    })
    await flushPromises()
    expect(view.text()).not.toContain('วันเกิดต้องเป็นวันก่อนวันนี้')
    const picker = view.findComponent({ name: 'ThaiDatePicker' })
    picker.vm.$emit('update:modelValue', '2999-01-01')
    await flushPromises()
    expect(picker.props('invalid')).toBe(true)
    expect(view.text()).toContain('วันเกิดต้องเป็นวันก่อนวันนี้')
    picker.vm.$emit('update:modelValue', '1990-01-01')
    await flushPromises()
    expect(picker.props('invalid')).toBe(false)
    expect(view.text()).not.toContain('วันเกิดต้องเป็นวันก่อนวันนี้')
    expect(mocks.register).not.toHaveBeenCalled()
    view.unmount()
  })
  beforeEach(() => {
    localStorage.clear()
    mocks.register.mockReset()
  })
  it('offers unspecified gender and blood group O', async () => {
    const view = mount(View, {
      global: {
        stubs: {
          StaffHeader: true,
          RouterLink: true,
          ThaiDatePicker: true,
          FilterDropdown: true,
          Teleport: true,
        },
      },
    })
    await flushPromises()
    const dropdowns = view.findAllComponents({ name: 'FilterDropdown' })
    expect(
      dropdowns.find((item) => item.props('label') === 'เพศ')?.props('options'),
    ).toContainEqual({ value: 'ไม่ระบุ', label: 'ไม่ระบุ' })
    expect(
      dropdowns.find((item) => item.props('label') === 'กรุ๊ปเลือด')?.props('options'),
    ).toContainEqual({ value: 'O', label: 'O' })
    view.unmount()
  })
  it('confirms before saving and rejects repeat clicks while saving', async () => {
    let finish!: (value: unknown) => void
    mocks.register.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const view = mount(View, {
      global: {
        stubs: {
          StaffHeader: true,
          RouterLink: true,
          ThaiDatePicker: true,
          FilterDropdown: true,
          Teleport: true,
        },
      },
    })
    await flushPromises()
    const state = view.vm as unknown as {
      form: Record<string, unknown>
      submit: () => Promise<void>
    }
    Object.assign(state.form, {
      firstName: 'สมชาย',
      lastName: 'ใจดี',
      phone: '0891234567',
      birthDate: '1990-01-01',
      gender: 'ไม่ระบุ',
    })
    await view.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.register).not.toHaveBeenCalled()
    const dialog = view.get('[aria-labelledby="registration-confirmation-title"]')
    await dialog.findAll('button')[1]!.trigger('click')
    void state.submit()
    expect(mocks.register).toHaveBeenCalledTimes(1)
    expect(mocks.register).toHaveBeenCalledWith(expect.objectContaining({ gender: 'ไม่ระบุ' }))
    finish({ id: 1, hn: 'TEST' })
    await flushPromises()
    view.unmount()
  })
})
