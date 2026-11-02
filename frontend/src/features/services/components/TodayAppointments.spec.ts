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
      .find((button) => button.text() === 'ดูรายการเดิม')!
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
  it('lets a NEW appointment without HN enter registration', async () => {
    search.mockResolvedValue({ items: [{ ...fixture(7), patientType: 'NEW', patient: null, contactName: 'New test', status: 'UNCONFIRMED' }], totalPages: 1 })
    const view = mount(TodayAppointments, { props: { branchCode: '02' } })
    await flushPromises()
    const button = view.findAll('button').find(item => item.text() === 'ลงทะเบียนผู้ป่วย')!
    expect(button.attributes('disabled')).toBeUndefined()
    await button.trigger('click')
    expect(view.emitted('select')?.[0]?.[0]).toMatchObject({ id: 7 })
    view.unmount()
  })
  it('shows waiting and completed appointments but excludes cancelled ones', async () => {
    search.mockResolvedValue({ items: [fixture(1), { ...fixture(2), status: 'UNCONFIRMED' }, { ...fixture(3), status: 'CANCELLED' }], totalPages: 1 })
    const view = mount(TodayAppointments, { props: { branchCode: '02' } })
    await flushPromises()
    expect(view.text()).toContain('Patient 1')
    expect(view.text()).toContain('Patient 2')
    expect(view.text()).not.toContain('Patient 3')
    view.unmount()
  })
})
