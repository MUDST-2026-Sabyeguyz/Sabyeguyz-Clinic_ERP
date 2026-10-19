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
  it('groups insurance types under insurance companies without search or descriptions', () => {
    const view = mount(MasterDataGroups, { props: { items } })
    expect(view.get('section[aria-label="กลุ่มลูกค้า"]').text()).toContain('VIP')
    expect(view.get('section[aria-label="ช่องทางแนะนำ"]').text()).toContain('Friend')
    const insurance = view.get('section[aria-label="บริษัทประกัน"]')
    expect(insurance.text()).toContain('Example Insurance')
    expect(insurance.text()).toContain('None')
    expect(insurance.get('.status-off').text()).toBe('ปิดใช้งาน')
    expect(view.find('details').exists()).toBe(false)
    expect(view.find('input').exists()).toBe(false)
    view.unmount()
  })

  it('emits the stored insurance category and stable IDs', async () => {
    const view = mount(MasterDataGroups, { props: { items } })
    await view.get('section[aria-label="ช่องทางแนะนำ"] .master-add').trigger('click')
    expect(view.emitted('add')).toEqual([['Referral Channel']])
    await view.get('section[aria-label="บริษัทประกัน"] .master-add').trigger('click')
    expect(view.emitted('add')?.[1]).toEqual(['Insurance Type'])
    await view.get('button[aria-label="แก้ไข Friend"]').trigger('click')
    expect(view.emitted('edit')).toEqual([[8]])
    await view.get('button[aria-label="แก้ไข None"]').trigger('click')
    expect(view.emitted('edit')?.[1]).toEqual([9])
    view.unmount()
  })

  it('keeps all three groups available when no data exists', () => {
    const view = mount(MasterDataGroups, { props: { items: [] } })
    expect(view.findAll('section')).toHaveLength(3)
    expect(view.findAll('.master-add')).toHaveLength(3)
    expect(view.findAll('.master-empty')).toHaveLength(3)
    view.unmount()
  })
})
