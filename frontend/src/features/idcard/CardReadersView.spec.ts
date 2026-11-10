import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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
    expect(wrapper.text()).toContain('เพิกถอนแล้ว')
  })

  it('adds a reader and shows its pairing code once', async () => {
    api.createWorkstation.mockResolvedValue({
      id: 3,
      name: 'เคาน์เตอร์ 2',
      branchCode: '01',
      pairingCode: 'ABCD2345',
      pairingExpiresAt: '2026-11-10T10:10:00Z',
    })
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('input[name="name"]').setValue('เคาน์เตอร์ 2')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createWorkstation).toHaveBeenCalledWith('เคาน์เตอร์ 2', '01')
    expect(wrapper.text()).toContain('ABCD2345')
  })

  it('revokes a reader after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.revokeWorkstation.mockResolvedValue(undefined)
    const wrapper = mount(CardReadersView)
    await flushPromises()
    await wrapper.get('button.revoke').trigger('click')
    await flushPromises()
    expect(api.revokeWorkstation).toHaveBeenCalledWith(1)
  })
})
