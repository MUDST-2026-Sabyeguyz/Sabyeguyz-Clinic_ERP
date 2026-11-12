import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CourseRenewalDialog from '../components/CourseRenewalDialog.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'

const api = vi.hoisted(() => ({
  extensions: vi.fn(), requestExtension: vi.fn(),
  previewDirectExtension: vi.fn(), extendDirect: vi.fn(),
}))
vi.mock('@/api/client', () => ({ courseApi: api }))

const course = {
  id: 43,
  patient: { id: 9, hn: 'TEST9', fullName: 'ผู้ป่วยทดสอบ', phone: '0800000000', active: true },
  purchaser: { id: 9, hn: 'TEST9', fullName: 'ผู้ป่วยทดสอบ' },
  branchCode: '01',
  course: { id: 7, code: 'C-7', name: 'คอร์สทดสอบ' },
  price: 1000,
  sessions: 10,
  bonusSessions: 0,
  purchased: 10,
  used: 10,
  remaining: 0,
  purchasedAt: '2026-01-01T00:00:00Z',
  expiresAt: '2026-12-31T00:00:00Z',
  expired: false,
  usable: false,
  status: 'ACTIVE',
}

function render(manager = false) {
  return mount(CourseRenewalDialog, {
    props: { course, patientId: 9, manager },
    global: {
      directives: { 'modal-focus': { mounted() {} } },
      stubs: { Teleport: true },
    },
  })
}

beforeEach(() => {
  api.extensions.mockReset().mockResolvedValue([])
  api.requestExtension.mockReset().mockResolvedValue({ id: 1, status: 'Pending' })
  const dates = { oldExpiry: '2026-12-31', newExpiry: '2027-01-31' }
  api.previewDirectExtension.mockReset().mockResolvedValue(dates)
  api.extendDirect.mockReset().mockResolvedValue(dates)
})

describe('course extension request dialog', () => {
  it('lets a Manager review and extend without creating a request', async () => {
    const view = render(true)
    expect(view.find('input[type="checkbox"]').exists()).toBe(false)
    await flushPromises()
    const body = { courseId: 43, requestedByPatientId: 9, value: 1, unit: 'Month', reason: '', ownerRequestConfirmed: false }
    expect(api.previewDirectExtension).toHaveBeenCalledWith(body)
    expect(api.extendDirect).not.toHaveBeenCalled()
    expect(view.text()).toContain('คอร์สจะหมดอายุวันที่')
    expect(view.get('button[type="submit"]').text()).toBe('ยืนยันต่ออายุ')
    await view.get('form').trigger('submit')
    await flushPromises()
    expect(api.extendDirect).toHaveBeenCalledWith({ ...body, oldExpiry: '2026-12-31', newExpiry: '2027-01-31', ownerRequestConfirmed: true })
    expect(api.requestExtension).not.toHaveBeenCalled()
    expect(api.extensions).not.toHaveBeenCalled()
    expect(view.emitted('extended')).toEqual([['2027-01-31']])
    view.unmount()
  })

  it('clamps a typed duration to the selected unit limit', async () => {
    const view = render(true)
    await flushPromises()
    api.previewDirectExtension.mockClear()
    const duration = view.get('input[type="number"]')
    expect(duration.attributes('max')).toBe('12')
    await duration.setValue('13')
    expect((duration.element as HTMLInputElement).value).toBe('12')
    expect(api.previewDirectExtension).toHaveBeenCalledWith(expect.objectContaining({ value: 12 }))
    expect(api.extendDirect).not.toHaveBeenCalled()
    view.unmount()
  })

  it('keeps the previous day count when another digit would exceed the limit', async () => {
    const view = render(true)
    await flushPromises()
    view.getComponent(FilterDropdown).vm.$emit('update:modelValue', 'Day')
    await flushPromises()
    const duration = view.get('input[type="number"]')
    expect(duration.attributes('max')).toBe('99')
    await duration.setValue('99')
    api.previewDirectExtension.mockClear()
    await duration.setValue('994')
    expect((duration.element as HTMLInputElement).value).toBe('99')
    expect(api.previewDirectExtension).not.toHaveBeenCalled()
    view.unmount()
  })

  it('submits the main extension request contract and waits for manager approval', async () => {
    const view = render()
    await flushPromises()
    expect(view.get('.course-renewal-duration + .course-renewal-preview').text()).toContain('คอร์สจะหมดอายุวันที่')
    expect(api.previewDirectExtension).not.toHaveBeenCalled()
    await view.get('input[type="number"]').setValue('3')
    await view.get('textarea').setValue('เหตุผลทดสอบ')
    await view.get('form').trigger('submit')
    await flushPromises()

    expect(api.requestExtension).toHaveBeenCalledWith({
      courseId: 43,
      value: 3,
      unit: 'Month',
      reason: 'เหตุผลทดสอบ',
      requestedByPatientId: 9,
    })
    expect(api.extensions).toHaveBeenLastCalledWith({ courseId: 43 })
    expect(view.find('form').exists()).toBe(false)
    expect(view.text()).not.toContain('คำขอและประวัติการต่ออายุ')
    expect(view.emitted('requested')).toHaveLength(1)
    view.unmount()
  })

  it('does not allow a second request while the course has a pending request', async () => {
    api.extensions.mockResolvedValue([
      {
        id: 1,
        courseId: 43,
        requestedPatientId: 9,
        value: 1,
        unit: 'Month',
        reason: 'รออนุมัติ',
        status: 'Pending',
        requestedAt: '2026-10-31T00:00:00Z',
        decidedAt: null,
        decisionNote: null,
        oldExpiry: null,
        newExpiry: null,
      },
    ])
    const view = render()
    await flushPromises()
    expect(view.get('.course-renewal-success').text()).toBe('รอการพิจารณา')
    expect(view.text()).not.toContain(course.course.name)
    expect(view.text()).not.toContain('วันหมดอายุปัจจุบัน')
    expect(view.find('form').exists()).toBe(false)
    expect(api.requestExtension).not.toHaveBeenCalled()
    view.unmount()
  })
})
