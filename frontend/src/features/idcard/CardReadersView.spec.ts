import { enableAutoUnmount, flushPromises, mount as mountComponent } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/client')>()),
  idCardApi: {
    workstations: vi.fn(),
    createWorkstation: vi.fn(),
    newPairingCode: vi.fn(),
    revokeWorkstation: vi.fn(),
  },
}))
vi.mock('@/features/staff/data/managerBranch', () => ({
  selectedManagerBranch: () => ({ code: '01', name: 'สาขาพระราม 9' }),
}))
vi.mock('@/features/staff/components/StaffHeader.vue', () => ({ default: { template: '<header />' } }))

import { idCardApi } from '@/api/client'
import CardReadersView from './CardReadersView.vue'

const api = vi.mocked(idCardApi)
const mount = (component: typeof CardReadersView) => mountComponent(component, { global: { stubs: { Teleport: true } } })
enableAutoUnmount(afterEach)

describe('CardReadersView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.workstations.mockResolvedValue([
      { id: 1, name: 'เคาน์เตอร์ 1', branchCode: '01', online: true, status: 'Active' },
      { id: 2, name: 'เก่า', branchCode: '01', online: false, status: 'Revoked' },
    ])
  })

  it('lists the readers with their state', async () => {
    const wrapper = mount(CardReadersView)
    await flushPromises()
    expect(wrapper.text()).toContain('เคาน์เตอร์ 1')
    expect(wrapper.text()).toContain('ออนไลน์')
    expect(wrapper.text()).not.toContain('เพิกถอนแล้ว')
  })

  it('opens an add form separately from the reader list and discards a cancelled draft', async () => {
    const wrapper = mount(CardReadersView)
    await flushPromises()
    expect(wrapper.find('input[name="name"]').exists()).toBe(false)

    await wrapper.get('.card-reader-heading .add-reader-trigger').trigger('click')
    const dialog = wrapper.get('.add-reader-dialog')
    expect(dialog.get('h2').text()).toBe('เพิ่มเครื่องอ่านบัตร')
    expect(dialog.get('button[type="submit"]').element).toHaveProperty('disabled', true)
    await dialog.get('input[name="name"]').setValue('เครื่องชั่วคราว')
    await dialog.get('button[type="button"]').trigger('click')
    expect(wrapper.find('.add-reader-dialog').exists()).toBe(false)
    expect(api.createWorkstation).not.toHaveBeenCalled()

    await wrapper.get('.add-reader-trigger').trigger('click')
    expect(wrapper.get('input[name="name"]').element).toHaveProperty('value', '')
  })

  it('adds a reader and shows its pairing code once', async () => {
    api.createWorkstation.mockResolvedValue({
      id: 3,
      name: 'เคาน์เตอร์ 2',
      branchCode: '01',
      pairingCode: 'ABCD2345',
      pairingExpiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('.add-reader-trigger').trigger('click')
    await wrapper.get('input[name="name"]').setValue('  เคาน์เตอร์ 2  ')
    await wrapper.get('.add-reader-form').trigger('submit')
    await flushPromises()
    expect(api.createWorkstation).toHaveBeenCalledWith('เคาน์เตอร์ 2', '01')
    expect(wrapper.find('.add-reader-dialog').exists()).toBe(false)
    expect(wrapper.text()).toContain('ABCD2345')
    expect(wrapper.find('tbody').text()).toContain('เคาน์เตอร์ 2')
    expect(wrapper.findAll('thead th')).toHaveLength(3)
    await wrapper.get('[aria-label="ปิดรหัสจับคู่"]').trigger('click')
    await wrapper.get('.add-reader-trigger').trigger('click')
    expect(wrapper.get('input[name="name"]').element).toHaveProperty('value', '')
  })

  it('keeps the name and displays a creation error inside the add dialog', async () => {
    api.createWorkstation.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('.add-reader-trigger').trigger('click')
    await wrapper.get('input[name="name"]').setValue('เครื่องใหม่')
    await wrapper.get('.add-reader-form').trigger('submit')
    await flushPromises()
    expect(api.createWorkstation).toHaveBeenCalledWith('เครื่องใหม่', '01')
    const dialog = wrapper.get('.add-reader-dialog')
    expect(dialog.get('[role="alert"]').text()).toBe('เพิ่มเครื่องอ่านบัตรไม่สำเร็จ')
    expect(dialog.get('input[name="name"]').element).toHaveProperty('value', 'เครื่องใหม่')
    expect(dialog.get('button[type="submit"]').element).toHaveProperty('disabled', false)
  })

  it('keeps the successfully created reader visible if refreshing the list fails', async () => {
    api.createWorkstation.mockResolvedValue({
      id: 3, name: 'เครื่องใหม่', branchCode: '01', pairingCode: 'ABCD2345',
      pairingExpiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    const wrapper = mount(CardReadersView)
    await flushPromises()
    api.workstations.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('.add-reader-trigger').trigger('click')
    await wrapper.get('input[name="name"]').setValue('เครื่องใหม่')
    await wrapper.get('.add-reader-form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('tbody').text()).toContain('เครื่องใหม่')
    expect(wrapper.find('tbody').text()).toContain('เคาน์เตอร์ 1')
    expect(wrapper.text()).toContain('โหลดรายการเครื่องอ่านบัตรไม่สำเร็จ')
    expect(wrapper.find('.add-reader-dialog').exists()).toBe(false)
  })

  it('revokes a reader after confirming', async () => {
    api.revokeWorkstation.mockResolvedValue(undefined)
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('button.revoke').trigger('click')
    await flushPromises()
    expect(api.revokeWorkstation).not.toHaveBeenCalled()
    await wrapper.get('.cancel-reader-actions button').trigger('click')
    expect(wrapper.find('.cancel-reader-dialog').exists()).toBe(false)
    await wrapper.get('button.revoke').trigger('click')
    await wrapper.get('.confirm-cancel-reader').trigger('click')
    await flushPromises()
    expect(api.revokeWorkstation).toHaveBeenCalledWith(1)
  })
})
