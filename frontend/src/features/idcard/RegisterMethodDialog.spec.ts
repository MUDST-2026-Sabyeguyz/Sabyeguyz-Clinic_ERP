import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const read = vi.fn()
const reader = {
  workstations: ref([{ id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' }]),
  selectedId: ref<number | null>(1),
  state: ref('idle'),
  message: ref(''),
  load: vi.fn().mockResolvedValue(undefined),
  select: vi.fn(),
  read,
  cancel: vi.fn(),
}
vi.mock('./useIdCardReader', () => ({ useIdCardReader: () => reader }))

import RegisterMethodDialog from './RegisterMethodDialog.vue'
import { takePendingCard } from './pendingCard'

const mountDialog = () =>
  mount(RegisterMethodDialog, {
    props: { open: true, branchCode: '01' },
    global: { stubs: { RouterLink: true } },
  })

describe('RegisterMethodDialog', () => {
  beforeEach(() => {
    push.mockReset()
    read.mockReset()
    reader.state.value = 'idle'
    reader.message.value = ''
    reader.selectedId.value = 1
    reader.workstations.value = [
      { id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' },
    ]
    takePendingCard()
  })

  it('offers typing the data by hand or reading the card', () => {
    const wrapper = mountDialog()
    expect(wrapper.get('.method-manual').text()).toContain('กรอกข้อมูลเอง')
    expect(wrapper.get('.method-card').text()).toContain('อ่านข้อมูลจากบัตรประชาชน')
  })

  it('goes to the plain registration form when the staff type the data by hand', async () => {
    const wrapper = mountDialog()
    await wrapper.get('.method-manual').trigger('click')
    expect(push).toHaveBeenCalledWith('/staff/patients/new')
    expect(wrapper.emitted('close')).toBeTruthy()
    expect(read).not.toHaveBeenCalled()
  })

  it('asks for the card, then says it is reading, with nothing to press while it waits', async () => {
    let finish: (value: unknown) => void = () => {}
    read.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const wrapper = mountDialog()
    await wrapper.get('.method-card').trigger('click')

    reader.state.value = 'waiting'
    await nextTick()
    expect(wrapper.text()).toContain('กรุณาเสียบบัตรประชาชน')
    expect(wrapper.find('.id-card-graphic').exists()).toBe(true)
    expect(wrapper.findAll('button')).toHaveLength(0)

    reader.state.value = 'reading'
    await nextTick()
    expect(wrapper.text()).toContain('ระบบกำลังอ่านข้อมูล')
    expect(wrapper.findAll('button')).toHaveLength(0)

    await wrapper.get('.register-dialog-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    finish(null)
    await flushPromises()
  })

  it('shows the patient who is already registered, with a green button to their details', async () => {
    read.mockResolvedValue({
      status: 'DONE',
      lookup: { exists: true, patient: { id: 5, hn: 'HN0005', fullName: 'สมชาย ใจดี' } },
    })
    const wrapper = mountDialog()
    await wrapper.get('.method-card').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('พบผู้ป่วยในระบบแล้ว')
    expect(wrapper.text()).toContain('HN0005')
    expect(wrapper.text()).toContain('สมชาย ใจดี')
    expect(takePendingCard()).toBeNull()

    await wrapper.get('.found-details').trigger('click')
    expect(push).toHaveBeenCalledWith('/staff/patients/5')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('hands a new patient’s card to the registration form', async () => {
    read.mockResolvedValue({
      status: 'DONE',
      lookup: { exists: false, patient: null },
      card: { nationalId: '1101700005119', firstNameTh: 'สมชาย' },
    })
    const wrapper = mountDialog()
    await wrapper.get('.method-card').trigger('click')
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/staff/patients/new')
    expect(takePendingCard()?.nationalId).toBe('1101700005119')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('says why the read failed and offers a retry or typing by hand', async () => {
    read.mockImplementation(async () => {
      reader.state.value = 'failed'
      reader.message.value = 'ไม่พบบัตร ใส่บัตรแล้วลองใหม่'
      return null
    })
    const wrapper = mountDialog()
    await wrapper.get('.method-card').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('ไม่พบบัตร ใส่บัตรแล้วลองใหม่')
    await wrapper.get('.retry').trigger('click')
    await flushPromises()
    expect(read).toHaveBeenCalledTimes(2)

    await wrapper.get('.method-manual-after-failure').trigger('click')
    expect(push).toHaveBeenCalledWith('/staff/patients/new')
  })

  it('disables reading and explains when the branch has no card reader', () => {
    reader.selectedId.value = null
    reader.workstations.value = []
    const wrapper = mountDialog()
    expect(wrapper.get('.method-card').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('ยังไม่มีเครื่องอ่านบัตร')
  })

  it('hides the technical connection error and shows a plain cross icon', async () => {
    read.mockImplementation(async () => {
      reader.state.value = 'failed'
      reader.message.value = 'connect() failed'
      return null
    })
    const wrapper = mountDialog()
    await wrapper.get('.method-card').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('อ่านบัตรไม่สำเร็จ')
    expect(wrapper.text()).not.toContain('connect() failed')
    expect(wrapper.find('.read-failed-icon rect').exists()).toBe(false)
    expect(wrapper.findAll('.read-failed .actions button')).toHaveLength(2)
  })

  it('asks which reader to use when there are several and none is chosen', () => {
    reader.selectedId.value = null
    reader.workstations.value = [
      { id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' },
      { id: 2, name: 'เคาน์เตอร์ 2', branchCode: '01', online: false, status: 'Active' },
    ]
    const wrapper = mountDialog()
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.get('.method-card').attributes('disabled')).toBeDefined()
  })
})
