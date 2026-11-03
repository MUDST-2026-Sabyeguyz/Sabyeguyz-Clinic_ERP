import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CourseRenewalDialog from '../components/CourseRenewalDialog.vue'

const api = vi.hoisted(() => ({ extensions: vi.fn(), requestExtension: vi.fn() }))
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

function render() {
  return mount(CourseRenewalDialog, {
    props: { course, patientId: 9 },
    global: {
      directives: { 'modal-focus': { mounted() {} } },
      stubs: { Teleport: true },
    },
  })
}

beforeEach(() => {
  api.extensions.mockReset().mockResolvedValue([])
  api.requestExtension.mockReset().mockResolvedValue({ id: 1, status: 'Pending' })
})

describe('course extension request dialog', () => {
  it('submits the main extension request contract and waits for manager approval', async () => {
    const view = render()
    await flushPromises()
    await view.get('input[type="number"]').setValue('3')
    await view.get('select').setValue('Month')
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
    expect(view.text()).toContain('ยังไม่เปลี่ยนวันหมดอายุ')
    expect(view.text()).toContain('รอ Manager อนุมัติก่อนวันหมดอายุจะเปลี่ยน')
    expect(view.emitted('requested')).toHaveLength(1)
    view.unmount()
  })

  it('allows an extension request without an optional reason', async () => {
    const view = render()
    await flushPromises()
    await view.get('input[type="number"]').setValue('2')
    await view.get('form').trigger('submit')
    await flushPromises()

    expect(api.requestExtension).toHaveBeenCalledWith({
      courseId: 43,
      value: 2,
      unit: 'Month',
      reason: undefined,
      requestedByPatientId: 9,
    })
    expect(view.get('textarea').attributes('required')).toBeUndefined()
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
    expect(view.text()).toContain('มีคำขอต่ออายุที่รอ Manager พิจารณาอยู่แล้ว')
    expect(view.find('form').exists()).toBe(false)
    expect(api.requestExtension).not.toHaveBeenCalled()
    view.unmount()
  })
})
