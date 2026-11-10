import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Panel from '../components/AppointmentProcessPanel.vue'
import type { AppointmentProgress } from '@/api/client'
const progress: AppointmentProgress = {
 appointmentId: 55, appointmentStatus: 'CONFIRMED', patientType: 'CURRENT', state: 'REVIEW_REQUIRED',
 reason: 'พบเอกสารที่ย้อนรายการ ต้องตรวจรายละเอียดเดิม', visitId: 20, servedPatientId: 9,
 completedAt: '2026-10-30T03:00:00Z',
 sales: [{ saleId: 11, receiptId: 21, receiptNo: 'TEST99', receiptStatus: 'Voided', active: false }],
 usages: [{ usageId: 31, movementId: 41, coursePurchaseId: 101, status: 'Reversed' }],
}
const render = (value = progress) => mount(Panel, { props: { progress: value }, global: { stubs: { RouterLink: RouterLinkStub } } })
describe('backend progress summary', () => {
 it('opens the receipt with saleId and shows backend review state and reason', () => {
  const view = render()
  expect(view.findAllComponents(RouterLinkStub)[0]!.props('to')).toEqual({ name: 'staff-receipt', params: { id: 11 } })
  expect(view.text()).toContain('ต้องตรวจสอบรายการเดิม')
  expect(view.text()).toContain('พบเอกสารที่ย้อนรายการ')
  expect(view.text()).toContain('TEST99')
 })
 it('shows the served patient and course purchase IDs returned by backend', () => {
  const view = render()
  expect(view.text()).toContain('Visit #20')
  expect(view.text()).toContain('ผู้ป่วย #9')
  expect(view.text()).toContain('คอร์ส #101')
  expect(view.findAllComponents(RouterLinkStub)[1]!.props('to')).toMatchObject({
   query: { patientId: '9', courseId: '101', action: 'history' },
  })
 })
 it('shows the original state and refreshes explicitly', async () => {
  const view = render({ ...progress, state: 'FOUND', reason: null })
  expect(view.text()).toContain('พบรายการเดิม')
  await view.get('button').trigger('click')
  expect(view.emitted('refresh')).toHaveLength(1)
 })
 it('groups sale documents that share one receipt while opening it with a saleId', () => {
  const view = render({
   ...progress,
   sales: [
    ...progress.sales,
    { saleId: 12, receiptId: 21, receiptNo: 'TEST99', receiptStatus: 'Voided', active: false },
   ],
  })
  expect(view.findAllComponents(RouterLinkStub).filter(link => {
   const target = link.props('to')
   return typeof target === 'object' && target !== null && target.name === 'staff-receipt'
  })).toHaveLength(1)
  expect(view.findAllComponents(RouterLinkStub)[0]!.props('to')).toEqual({ name: 'staff-receipt', params: { id: 11 } })
 })
})
