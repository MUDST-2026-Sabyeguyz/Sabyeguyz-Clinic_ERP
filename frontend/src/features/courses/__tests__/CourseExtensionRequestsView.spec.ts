import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import View from '../views/CourseExtensionRequestsView.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
const testRoute = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => testRoute }))

const api = vi.hoisted(() => ({
  extensions: vi.fn(),
  approveExtension: vi.fn(),
  rejectExtension: vi.fn(),
  patientGet: vi.fn(),
  ledger: vi.fn(),
}))
vi.mock('@/api/client', () => ({
  courseApi: {
    extensions: api.extensions,
    approveExtension: api.approveExtension,
    rejectExtension: api.rejectExtension,
    ledger: api.ledger,
  },
  patientApi: { get: api.patientGet },
}))

const request = {
  id: 8,
  courseId: 43,
  requestedPatientId: 9,
  value: 3,
  unit: 'Month' as const,
  reason: 'พักรักษาตัว',
  status: 'Pending' as const,
  requestedAt: '2026-10-30T03:00:00Z',
  decidedAt: null,
  decisionNote: null,
  oldExpiry: null,
  newExpiry: null,
}

function render() {
  return mount(View, { global: { stubs: { StaffHeader: true, RouterLink: true, Teleport: true } } })
}

beforeEach(() => {
  testRoute.query = {}
  api.extensions.mockReset().mockResolvedValue([request])
  api.approveExtension.mockReset().mockResolvedValue({
    ...request,
    status: 'Approved',
    decidedAt: '2026-10-31T03:00:00Z',
    decisionNote: 'อนุมัติ',
    oldExpiry: '2026-12-31',
    newExpiry: '2027-03-31',
  })
  api.rejectExtension.mockReset().mockResolvedValue({ ...request, status: 'Rejected' })
  api.patientGet.mockResolvedValue({ id: 9, hn: 'HN9', fullName: 'ผู้ป่วยทดสอบ' })
  api.ledger.mockResolvedValue({ purchase: { course: { name: 'คอร์สทดสอบ' } } })
})

describe('manager course extension decisions', () => {
  it('shows only the selected patient and supports searching within that scope', async () => {
    testRoute.query = { patientId: '9' }
    api.extensions.mockResolvedValue([request, { ...request, id: 99, requestedPatientId: 10 }])
    const view = render()
    await flushPromises()
    expect(view.text()).toContain('คำขอของผู้ป่วยรายนี้')
    expect(view.findAll('.request-card')).toHaveLength(1)
    expect(api.patientGet).not.toHaveBeenCalledWith(10)
    await view.get('input[type="search"]').setValue('ไม่ตรงกับข้อมูล')
    expect(view.findAll('.request-card')).toHaveLength(0)
    await view.get('input[type="search"]').setValue('HN9')
    expect(view.findAll('.request-card')).toHaveLength(1)
    view.unmount()
  })

  it('loads the main pending request contract and approves through the decision endpoint', async () => {
    const view = render()
    await flushPromises()
    expect(api.extensions).toHaveBeenCalledWith({ status: 'Pending' })
    expect(view.text()).toContain('คอร์สทดสอบ')
    expect(view.text()).toContain('ผู้ป่วยทดสอบ')
    expect(view.text()).toContain('เพิ่ม 3 เดือน')
    expect(view.get('.request-status').text()).toBe('รอพิจารณา')

    await view.get('button.approve').trigger('click')
    expect(api.approveExtension).not.toHaveBeenCalled()
    expect(view.get('[role="dialog"]').text()).toContain('ยืนยันอนุมัติคำขอ?')
    await view.get('.decision-confirm').trigger('click')
    await flushPromises()
    expect(api.approveExtension).toHaveBeenCalledWith(8, undefined)
    expect(view.text()).not.toContain('อนุมัติคำขอแล้ว')
    expect(view.find('button.approve').exists()).toBe(false)
    view.unmount()
  })

  it('loads the selected status and rejects through the decision endpoint', async () => {
    const view = render()
    await flushPromises()
    view.getComponent(FilterDropdown).vm.$emit('update:modelValue', 'Rejected')
    await flushPromises()
    expect(api.extensions).toHaveBeenLastCalledWith({ status: 'Rejected' })

    view.getComponent(FilterDropdown).vm.$emit('update:modelValue', 'Pending')
    await flushPromises()
    await view.get('.request-card footer button').trigger('click')
    expect(api.rejectExtension).not.toHaveBeenCalled()
    expect(view.get('[role="dialog"]').text()).toContain('ยืนยันปฏิเสธคำขอ?')
    await view.get('.decision-confirm').trigger('click')
    await flushPromises()
    expect(api.rejectExtension).toHaveBeenCalledWith(8, undefined)
    expect(view.text()).not.toContain('ปฏิเสธคำขอแล้ว')
    view.unmount()
  })
})
