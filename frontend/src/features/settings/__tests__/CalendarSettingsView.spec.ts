import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { setManagerBranches, setSelectedManagerBranch } from '@/features/staff/data/managerBranch'
import CalendarSettingsView from '../views/CalendarSettingsView.vue'
const api = vi.hoisted(() => ({ status: vi.fn(), save: vi.fn(), sync: vi.fn() }))
vi.mock('@/api/client', () => ({
  ApiError: class extends Error {},
  calendarApi: { status: api.status, setBranchCalendar: api.save, syncNow: api.sync },
}))
describe('calendar settings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '')
    }
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open')
    }
  })
  it('uses a branch form and explicit disconnect confirmation, not an editable table', async () => {
    api.status.mockResolvedValue({
      enabled: true,
      lastRunAt: null,
      branches: [
        {
          branchCode: '01',
          branchName: 'พระราม 9',
          googleCalendarId: 'rama@example.com',
          waiting: 2,
          failed: 1,
        },
      ],
    })
    setManagerBranches([{ code: '01', name: 'พระราม 9' }])
    const view = mount(CalendarSettingsView, {
      global: { stubs: { StaffHeader: true, RouterLink: true } },
    })
    await flushPromises()
    expect(view.find('table').exists()).toBe(false)
    expect(view.text()).toContain('บันทึกการเชื่อมต่อ')
    expect(view.get('a[data-cy="calendar-open"]').attributes('href')).toContain(
      encodeURIComponent('rama@example.com'),
    )
    expect(view.text()).toContain('ซิงก์ล่าสุด:')
    expect(view.text()).not.toContain('รอบรวมทุกสาขา')
    expect(view.get('[data-cy="calendar-sync"]').text()).toBe('sync ↻')
    view.unmount()
  })
  it('switches branch-specific IDs without overwriting the other branch', async () => {
    api.status.mockResolvedValue({
      enabled: false,
      branches: [
        {
          branchCode: '01',
          branchName: 'พระราม 9',
          googleCalendarId: 'rama@example.com',
          waiting: 0,
          failed: 0,
        },
        {
          branchCode: '02',
          branchName: 'แบริ่ง',
          googleCalendarId: 'bearing@example.com',
          waiting: 0,
          failed: 0,
        },
      ],
    })
    setManagerBranches([
      { code: '01', name: 'พระราม 9' },
      { code: '02', name: 'แบริ่ง' },
    ])
    setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
    const view = mount(CalendarSettingsView, {
      global: { stubs: { StaffHeader: true, RouterLink: true } },
    })
    await flushPromises()
    expect(view.find('[data-cy="calendar-row-01"]').exists()).toBe(true)
    expect(view.find('[data-cy="calendar-row-02"]').exists()).toBe(false)
    setSelectedManagerBranch({ code: '02', name: 'แบริ่ง' })
    await nextTick()
    expect(view.find('[data-cy="calendar-row-01"]').exists()).toBe(false)
    expect((view.get('input').element as HTMLInputElement).value).toBe('bearing@example.com')
    await view.get('input').setValue('rama@example.com')
    api.save.mockResolvedValue({ branchCode: '02', googleCalendarId: 'rama@example.com' })
    await view.get('form').trigger('submit')
    await flushPromises()
    expect(api.save).toHaveBeenCalledExactlyOnceWith('02', 'rama@example.com')
    expect(view.text()).not.toContain('แต่ละสาขาต้องใช้ Calendar ID คนละค่า')
    setSelectedManagerBranch({ code: '01', name: 'พระราม 9' })
    await nextTick()
    expect((view.get('input').element as HTMLInputElement).value).toBe('rama@example.com')
    view.unmount()
  })
  it('only disconnects the selected branch after confirmation', async () => {
    api.status.mockResolvedValue({ enabled: true, branches: [
      { branchCode: '01', branchName: 'พระราม 9', googleCalendarId: 'rama@example.com', waiting: 0, failed: 0 },
    ] })
    api.save.mockResolvedValue({ branchCode: '01', googleCalendarId: null })
    setManagerBranches([{ code: '01', name: 'พระราม 9' }])
    const view = mount(CalendarSettingsView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    await view.get('[data-cy="calendar-disconnect"]').trigger('click')
    expect(view.get('dialog').attributes('open')).toBeDefined()
    expect(api.save).not.toHaveBeenCalled()
    await view.get('[data-cy="calendar-confirm-disconnect"]').trigger('click')
    await flushPromises()
    expect(api.save).toHaveBeenCalledExactlyOnceWith('01', '')
    view.unmount()
  })
  it('does not use an empty input as a disconnect request', async () => {
    api.status.mockResolvedValue({ enabled: true, branches: [
      { branchCode: '01', branchName: 'พระราม 9', googleCalendarId: 'rama@example.com', waiting: 0, failed: 0 },
    ] })
    setManagerBranches([{ code: '01', name: 'พระราม 9' }])
    const view = mount(CalendarSettingsView, { global: { stubs: { StaffHeader: true, RouterLink: true } } })
    await flushPromises()
    await view.get('input').setValue('')
    await view.get('form').trigger('submit')
    expect(api.save).not.toHaveBeenCalled()
    expect(view.get('[data-cy="calendar-error"]').text()).toContain('กรุณากรอก Calendar ID')
    view.unmount()
  })
})
