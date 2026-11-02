import { mount } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StaffHeader from './StaffHeader.vue'

const route = reactive({ path: '/staff/appointments', fullPath: '/staff/appointments' })
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/api/client', () => ({ branchApi: { list: async () => [] } }))

describe.each(['THERAPIST', 'MANAGER'])('profile menu %s', (role) => {
  it('places appointments before patients without changing destinations', () => {
    const view = mount(StaffHeader, { global: { stubs: { RouterLink: true, FilterDropdown: true } } })
    const paths = view.findAll('nav router-link-stub').map((link) => link.attributes('to'))
    expect(paths.indexOf('/staff/appointments')).toBeLessThan(paths.indexOf('/staff/patients'))
    view.unmount()
  })
  beforeEach(() => {
    localStorage.setItem('authUser', JSON.stringify({ role }))
    route.fullPath = '/staff/appointments'
    vi.stubGlobal('requestAnimationFrame', () => 1)
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })
  it('closes outside the profile without closing on an internal press', async () => {
    const view = mount(StaffHeader, { attachTo: document.body, global: { stubs: { RouterLink: true, FilterDropdown: true } } })
    await view.get('.profile-trigger').trigger('click')
    const profileLinks = view.get('.profile-menu').findAll('router-link-stub')
    expect(profileLinks.some((link) => link.attributes('to') === '/staff/calendar')).toBe(false)
    expect(profileLinks).toHaveLength(1)
    expect(profileLinks[0]?.attributes('to')).toBe('/')
    view.get('.profile-menu').element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(view.find('.profile-menu').exists()).toBe(true)
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(view.find('.profile-menu').exists()).toBe(false)
    view.unmount()
  })
  it('closes on Escape and navigation', async () => {
    const view = mount(StaffHeader, { global: { stubs: { RouterLink: true, FilterDropdown: true } } })
    await view.get('.profile-trigger').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(view.find('.profile-menu').exists()).toBe(false)
    await view.get('.profile-trigger').trigger('click')
    route.fullPath = '/staff/services'
    await nextTick()
    expect(view.find('.profile-menu').exists()).toBe(false)
    view.unmount()
  })
})
