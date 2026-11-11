import { flushPromises, mount as mountComponent } from '@vue/test-utils'
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
vi.mock('./agentLocal', () => ({ agentLocal: { status: vi.fn<() => Promise<unknown>>(), pair: vi.fn<() => Promise<unknown>>() } }))
vi.mock('@/features/staff/data/managerBranch', () => ({
  selectedManagerBranch: () => ({ code: '01', name: 'สาขาพระราม 9' }),
}))
vi.mock('@/features/staff/components/StaffHeader.vue', () => ({ default: { template: '<header />' } }))

import { idCardApi } from '@/api/client'
import { agentLocal } from './agentLocal'
import CardReadersView from './CardReadersView.vue'

const api = vi.mocked(idCardApi)
const local = vi.mocked(agentLocal)
const agentStatus = (over = {}) => ({
  paired: false,
  workstationName: null,
  branchCode: null,
  workstationId: null,
  version: '1.0.0',
  ...over,
})
const created = () => ({
  id: 3,
  name: 'เคาน์เตอร์ 2',
  branchCode: '01',
  pairingCode: 'ABCD2345',
  pairingExpiresAt: new Date(Date.now() + 600000).toISOString(),
})
async function addReader(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('input[name="name"]').setValue('เคาน์เตอร์ 2')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}
const mount = (component: typeof CardReadersView) => mountComponent(component, { global: { stubs: { Teleport: true } } })

describe('CardReadersView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    local.status.mockResolvedValue(null)
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
    await wrapper.get('input[name="name"]').setValue('เคาน์เตอร์ 2')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createWorkstation).toHaveBeenCalledWith('เคาน์เตอร์ 2', '01')
    expect(wrapper.text()).toContain('ABCD2345')
    expect(wrapper.find('tbody').text()).toContain('เคาน์เตอร์ 2')
    expect(wrapper.findAll('thead th')).toHaveLength(3)
    expect(wrapper.get('input[name="name"]').element).toHaveProperty('value', '')
  })

  it('keeps the successfully created reader visible if refreshing the list fails', async () => {
    api.createWorkstation.mockResolvedValue({
      id: 3, name: 'เครื่องใหม่', branchCode: '01', pairingCode: 'ABCD2345',
      pairingExpiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    const wrapper = mount(CardReadersView)
    await flushPromises()
    api.workstations.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('input[name="name"]').setValue('เครื่องใหม่')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('tbody').text()).toContain('เครื่องใหม่')
    expect(wrapper.find('tbody').text()).toContain('เคาน์เตอร์ 1')
    expect(wrapper.text()).toContain('โหลดรายการเครื่องอ่านบัตรไม่สำเร็จ')
    expect(wrapper.get('input[name="name"]').element).toHaveProperty('value', '')
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

  describe('with the agent installed on this PC', () => {
    it('offers no button, only the code, when this PC has no agent', async () => {
      api.createWorkstation.mockResolvedValue(created())
      const wrapper = mount(CardReadersView)
      await flushPromises()
      await addReader(wrapper)
      expect(wrapper.text()).toContain('ABCD2345')
      expect(wrapper.find('.pair-local').exists()).toBe(false)
      expect(wrapper.find('.local-agent-note').exists()).toBe(false)
    })

    it('pairs the agent on this PC with one click and no typing', async () => {
      local.status.mockResolvedValue(agentStatus())
      local.pair.mockResolvedValue({ ok: true, status: agentStatus({ paired: true, workstationName: 'เคาน์เตอร์ 2', workstationId: 3 }) })
      api.createWorkstation.mockResolvedValue(created())
      const wrapper = mount(CardReadersView)
      await flushPromises()
      expect(wrapper.get('.local-agent-note').text()).toContain('ยังไม่ได้จับคู่')
      await addReader(wrapper)
      expect(local.pair).not.toHaveBeenCalled() // never on its own: the manager may be at another PC
      await wrapper.get('.pair-local').trigger('click')
      await flushPromises()
      expect(local.pair).toHaveBeenCalledWith('ABCD2345')
      expect(wrapper.text()).toContain('จับคู่โปรแกรมบนเครื่องนี้สำเร็จ')
      expect(wrapper.find('.pair-local').exists()).toBe(false)
    })

    it('keeps the code on screen and says why when pairing fails', async () => {
      local.status.mockResolvedValue(agentStatus())
      local.pair.mockResolvedValue({ ok: false, message: 'โปรแกรมตัวกลางไม่อนุญาตให้เว็บนี้จับคู่' })
      api.createWorkstation.mockResolvedValue(created())
      const wrapper = mount(CardReadersView)
      await flushPromises()
      await addReader(wrapper)
      await wrapper.get('.pair-local').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('ไม่อนุญาต')
      expect(wrapper.text()).toContain('ABCD2345')
      expect((wrapper.get('.pair-local').element as HTMLButtonElement).disabled).toBe(false)
    })

    it('warns that pairing again replaces the reader this PC is paired with', async () => {
      local.status.mockResolvedValue(agentStatus({ paired: true, workstationName: 'เคาน์เตอร์ 1', workstationId: 1 }))
      api.createWorkstation.mockResolvedValue(created())
      const wrapper = mount(CardReadersView)
      await flushPromises()
      expect(wrapper.get('.local-agent-note').text()).toContain('เคาน์เตอร์ 1')
      await addReader(wrapper)
      expect(wrapper.get('.pairing-local').text()).toContain('แทนที่')
    })
  })
})
