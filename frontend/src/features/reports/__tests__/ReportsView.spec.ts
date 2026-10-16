import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import ReportsView from '../views/ReportsView.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { branchApi, courseApi, reportApi, type RevenueReport } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

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

it('keeps the selected transfer report when an earlier revenue request finishes late', async () => {
  let finishRevenue!: (value: RevenueReport) => void
  vi.spyOn(branchApi, 'list').mockResolvedValue([])
  vi.spyOn(courseApi, 'list').mockResolvedValue([])
  vi.spyOn(reportApi, 'revenue').mockReturnValue(new Promise(resolve => { finishRevenue = resolve }))
  vi.spyOn(courseApi, 'transferHistory').mockResolvedValue([{
    id: 7, sourcePatientId: 1, sourcePatientHn: 'HN01', sourcePatientName: 'Source Patient',
    destinationPatientId: 2, destinationPatientHn: 'HN02', destinationPatientName: 'Recipient Patient',
    branchCode: '01', courseId: 1, quantity: 1, reason: 'Synthetic regression',
    createdAt: `${clinicToday()}T09:00:00+07:00`,
  }])
  const renderError = vi.fn()
  const wrapper = mount(ReportsView, {
    global: {
      stubs: { StaffHeader: true, ThaiDatePicker: true },
      config: { errorHandler: renderError },
    },
  })
  try {
    await flushPromises()
    wrapper.findComponent(FilterDropdown).vm.$emit('update:modelValue', 'transfer')
    await flushPromises()
    expect(wrapper.text()).toContain('Recipient Patient')
    finishRevenue({ totalRevenue: 0, byCategory: [], dailyByCategory: [] })
    await flushPromises()
    expect(renderError).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Source Patient')
    expect(wrapper.text()).toContain('Recipient Patient')
  } finally { wrapper.unmount() }
})
