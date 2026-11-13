import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import PatientSearchView from '../views/PatientSearchView.vue'

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

it('filters patients using the branchCode returned by the branch API', async () => {
  vi.stubGlobal('fetch', async (input: string) => {
    const url = new URL(input, 'http://localhost')
    let body: unknown
    if (url.pathname === '/api/branches') {
      body = [{ id: 1, branchCode: '01', name: 'ราชพฤกษ์', address: 'นนทบุรี', phone: '021234567', active: true }]
    } else if (url.pathname === '/api/patients') {
      const items = url.searchParams.get('branchCode') === '01'
        ? [{ id: 7, hn: '2601000007', fullName: 'ผู้ป่วยราชพฤกษ์', phone: '0812345678', branchCode: '01', active: true }]
        : []
      body = { items, page: 0, size: 20, totalPages: 1, totalItems: items.length }
    } else if (url.pathname === '/api/patients/7') {
      body = { birthDate: '1990-01-01' }
    } else {
      throw new Error(`Unexpected request: ${input}`)
    }
    return { ok: true, text: async () => JSON.stringify(body) }
  })
  const wrapper = mount(PatientSearchView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
  try {
    await flushPromises()
    await wrapper.get('button[aria-label="กรองตามสาขา"]').trigger('click')
    const option = wrapper.findAll('[role="option"]').find((item) => item.text().includes('ราชพฤกษ์'))!
    await option.trigger('click')
    await flushPromises()
    expect(wrapper.get('tbody').text()).toContain('ผู้ป่วยราชพฤกษ์')
    expect(wrapper.get('.result-count').text()).toContain('1 ราย')
  } finally {
    wrapper.unmount()
  }
})
