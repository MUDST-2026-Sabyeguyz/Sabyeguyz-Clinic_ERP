import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import ReportsView from '../views/ReportsView.vue'

afterEach(() => vi.unstubAllGlobals())

it('shows a lookup error instead of invented branch and course filters', async () => {
  vi.stubGlobal('fetch', async (input: string) => ({
    ok: !input.startsWith('/api/branches'),
    status: input.startsWith('/api/branches') ? 503 : 200,
    text: async () => JSON.stringify(input.startsWith('/api/branches')
      ? { message: 'โหลดสาขาไม่ได้' }
      : input.startsWith('/api/courses') ? []
        : { totalRevenue: 0, byCategory: [], dailyByCategory: [] }),
  }))
  const wrapper = mount(ReportsView, {
    global: { stubs: { StaffHeader: true, ThaiDatePicker: true } },
  })
  try {
    await flushPromises()
    expect(wrapper.text()).toContain('โหลดสาขาไม่ได้')
  } finally { wrapper.unmount() }
})
