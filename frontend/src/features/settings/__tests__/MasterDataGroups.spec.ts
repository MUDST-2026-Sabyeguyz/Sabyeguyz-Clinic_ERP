import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MasterDataGroups from '../components/MasterDataGroups.vue'

const items = [
  { id: 8, category: 'Referral Channel', name: 'Friend', status: 'ใช้งาน' },
  { id: 2, category: 'Customer Group', name: 'VIP', status: 'ใช้งาน' },
  { id: 9, category: 'Insurance Type', name: 'None', status: 'ใช้งาน' },
  { id: 4, category: 'Insurance Company', name: 'Example Insurance', status: 'ปิดใช้งาน' },
]

describe('MasterDataGroups', () => {
  it('groups insurance types with insurance companies and expands a single category at a time', async () => {
    const view = mount(MasterDataGroups, { props: { items } })
    expect(view.get('section[aria-label="กลุ่มลูกค้า"]').text()).toContain('VIP')
    expect(view.get('section[aria-label="ช่องทางแนะนำ"]').text()).not.toContain('Friend')
    await view.get('section[aria-label="ช่องทางแนะนำ"] .master-group-heading').trigger('click')
    expect(view.get('section[aria-label="ช่องทางแนะนำ"]').text()).toContain('Friend')
    expect(view.get('section[aria-label="กลุ่มลูกค้า"] .master-group-heading').attributes('aria-expanded')).toBe('false')
    await view.get('section[aria-label="บริษัทประกัน"] .master-group-heading').trigger('click')
    const insurance = view.get('section[aria-label="บริษัทประกัน"]')
    expect(insurance.text()).toContain('Example Insurance')
    expect(insurance.text()).toContain('None')
    expect(insurance.get('.status-off').text()).toBe('ปิดใช้งาน')
    expect(view.find('details').exists()).toBe(false)
    expect(view.find('input[type="search"]').exists()).toBe(true)
    view.unmount()
  })

  it('emits stable IDs when editing items in expanded categories', async () => {
    const view = mount(MasterDataGroups, { props: { items } })
    await view.get('section[aria-label="ช่องทางแนะนำ"] .master-group-heading').trigger('click')
    await view.get('button[aria-label="แก้ไข Friend"]').trigger('click')
    expect(view.emitted('edit')).toEqual([[8]])
    await view.get('section[aria-label="บริษัทประกัน"] .master-group-heading').trigger('click')
    await view.get('button[aria-label="แก้ไข None"]').trigger('click')
    expect(view.emitted('edit')?.[1]).toEqual([9])
    view.unmount()
  })

  it('keeps all three groups available when no data exists', () => {
    const view = mount(MasterDataGroups, { props: { items: [] } })
    expect(view.findAll('section')).toHaveLength(3)
    expect(view.findAll('.master-add')).toHaveLength(0)
    expect(view.findAll('.master-empty')).toHaveLength(1)
    expect(view.find('.master-empty').text()).toContain('ยังไม่มีรายการกลุ่มลูกค้า')
    view.unmount()
  })
})
