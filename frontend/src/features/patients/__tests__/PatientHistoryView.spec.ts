import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PatientHistoryView from '../views/PatientHistoryView.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '7' }, query: {} }) }))
afterEach(() => vi.unstubAllGlobals())
it('requests receipt pages for one patient and shows whole net instead of paid', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const path = String(url)
      if (path.startsWith('/api/payments?')) {
        const page = new URLSearchParams(path.split('?')[1]).get('page')
        return new Response(
          JSON.stringify({
            items: [
              {
                id: page === '1' ? 9 : 3,
                receiptNo: page === '1' ? 'NEXT' : 'FIRST',
                soldAt: '2026-09-22T10:00:00Z',
                total: 900,
                paid: 100,
                branchCode: '01',
                status: 'PAID',
              },
            ],
            totalPages: 2,
            totalItems: 21,
          }),
        )
      }
      if (path === '/api/patients/7')
        return new Response(JSON.stringify({ id: 7, branch: { id: 1 } }))
      if (path.startsWith('/api/receipts/'))
        return new Response(JSON.stringify({ items: [], payments: [] }))
      return new Response('[]')
    }),
  )
  const wrapper = mount(PatientHistoryView, {
    global: {
      stubs: {
        StaffHeader: true,
        FilterDropdown: true,
        ThaiDatePicker: true,
        RouterLink: {
          props: ['to'],
          template: '<a :href="typeof to === \'string\' ? to : to.path"><slot /></a>',
        },
      },
    },
  })
  await flushPromises()
  const calls = () => vi.mocked(fetch).mock.calls.map(([url]) => String(url))
  expect(calls()).toContain('/api/payments?page=0&size=20&patientId=7&receiptsOnly=true')
  expect(wrapper.find('tbody').text()).toContain('900.00')
  expect(wrapper.find('tbody').text()).not.toContain('100.00')
  expect(wrapper.find('a[href="/staff/receipts/3"]').exists()).toBe(true)
  await wrapper
    .findAll('button')
    .find((b) => b.text() === 'ถัดไป')!
    .trigger('click')
  await flushPromises()
  expect(calls()).toContain('/api/payments?page=1&size=20&patientId=7&receiptsOnly=true')
  expect(wrapper.text()).toContain('NEXT')
  expect(wrapper.text()).not.toContain('FIRST')
  wrapper.unmount()
})
