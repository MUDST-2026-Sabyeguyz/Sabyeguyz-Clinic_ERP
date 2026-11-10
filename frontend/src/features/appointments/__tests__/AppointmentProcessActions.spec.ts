import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AppointmentProgress } from '@/api/client'
import Actions from '../components/AppointmentProcessActions.vue'

const progressRead = vi.hoisted(() => vi.fn())
vi.mock('../appointmentRequests', () => ({
  readAppointmentProgress: progressRead,
  progressStateLabel: (state: string) => state,
}))

const result: AppointmentProgress = {
  appointmentId: 55,
  appointmentStatus: 'CONFIRMED',
  patientType: 'CURRENT',
  state: 'FOUND',
  reason: null,
  visitId: 20,
  servedPatientId: 9,
  completedAt: null,
  sales: [
    { saleId: 11, receiptId: 21, receiptNo: 'TEST99', receiptStatus: 'Issued', active: true },
    { saleId: 12, receiptId: 21, receiptNo: 'TEST99', receiptStatus: 'Issued', active: true },
  ],
  usages: [],
}

beforeEach(() => progressRead.mockResolvedValue(result))

describe('appointment progress row actions', () => {
  it('opens one receipt per receiptId using its saleId', async () => {
    const view = mount(Actions, {
      props: {
        appointment: {
          id: 55,
          branchCode: '01',
          patient: { id: 9, hn: 'HN9', fullName: 'Test Patient' },
          therapist: { id: 3, fullName: 'Therapist' },
          service: { id: 2, name: 'Service', durationMinutes: 30 },
          startAt: '2026-10-30T02:00:00Z',
          endAt: '2026-10-30T02:30:00Z',
          status: 'CONFIRMED',
        },
        canManage: true,
      },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()
    const receiptLinks = view.findAllComponents(RouterLinkStub).filter(
      (link) => {
        const target = link.props('to')
        return typeof target === 'object' && target !== null && target.name === 'staff-receipt'
      },
    )
    expect(receiptLinks).toHaveLength(1)
    expect(receiptLinks[0]!.props('to')).toEqual({
      name: 'staff-receipt',
      params: { id: 11 },
    })
  })
})
