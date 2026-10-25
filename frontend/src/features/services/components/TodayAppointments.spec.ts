import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TodayAppointments from './TodayAppointments.vue'
import { clinicToday } from '@/utils/clinicDate'
const search = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => ({ appointmentApi: { search } }))
const fixture = (id: number, branchCode = '02') => ({
  id,
  branchCode,
  startAt: `${clinicToday()}T03:00:00Z`,
  status: 'CONFIRMED',
  patient: { id, hn: `HN${id}`, fullName: `Patient ${id}` },
  therapist: { fullName: 'Therapist' },
  service: { name: 'Service' },
})
describe('today appointments on treatment programs', () => {
  beforeEach(() => {
    search.mockReset()
  })
  it('loads every page for today and never shows another branch', async () => {
    search.mockImplementation(({ page }) =>
      Promise.resolve({
        items: page ? [fixture(2), fixture(3, '01')] : [fixture(1)],
        totalPages: 2,
      }),
    )
    const view = mount(TodayAppointments, { props: { branchCode: '02' } })
    await flushPromises()
    expect(search).toHaveBeenCalledWith({
      branchCode: '02',
      date: clinicToday(),
      page: 0,
      size: 100,
    })
    expect(view.text()).toContain('Patient 1')
    expect(view.text()).toContain('Patient 2')
    expect(view.text()).not.toContain('Patient 3')
    await view
      .findAll('button')
      .find((button) => button.text() === 'เลือกผู้ป่วย')!
      .trigger('click')
    expect(view.emitted('select')?.[0]?.[0]).toMatchObject({ id: 1 })
    view.unmount()
  })
  it('does not request all-branch data without a branch', async () => {
    const view = mount(TodayAppointments, { props: { branchCode: '' } })
    await flushPromises()
    expect(search).not.toHaveBeenCalled()
    expect(view.text()).toContain('กรุณาเลือกสาขา')
    view.unmount()
  })
})
