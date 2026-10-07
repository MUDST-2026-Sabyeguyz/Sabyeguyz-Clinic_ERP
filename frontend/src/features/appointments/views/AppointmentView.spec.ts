import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { search, patientSearch, reschedule, create, checkIn, branches } = vi.hoisted(() => ({
  search: vi.fn(), patientSearch: vi.fn(), reschedule: vi.fn(), create: vi.fn(), checkIn: vi.fn(), branches: vi.fn(),
}))
vi.mock('@/api/client', () => ({
  appointmentApi: { search, reschedule, create, checkIn }, branchApi: { list: branches },
  patientApi: { search: patientSearch }, resourceApi: { list: vi.fn().mockResolvedValue([]) },
  catalogApi: { services: vi.fn().mockResolvedValue([{ id: 10, name: 'Configured service' }]), therapists: vi.fn().mockResolvedValue([{ id: 9, fullName: 'Actual therapist' }]) },
}))
import AppointmentView from './AppointmentView.vue'
enableAutoUnmount(afterEach)
const app = { id: 7, branchCode: '01', patient: { id: 8, hn: '2601100008', fullName: 'Outside page' },
  therapist: { id: 9, fullName: 'Actual therapist' }, service: { id: 10, name: 'Configured service' },
  startAt: '2026-10-06T17:30:00Z', endAt: '2026-10-06T18:00:00Z', status: 'CONFIRMED', room: { id: 3, name: 'Configured room' } }
function view() { return mount(AppointmentView, { global: { stubs: { StaffHeader: true,
  RouterLink: { props: ['to'], template: '<a href="/staff/services"><slot /></a>' },
  ThaiDatePicker: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input class="test-date" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' } } } }) }
describe('appointment server contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ items: [app], page: 0, size: 20, totalItems: 23, totalPages: 2 })
    branches.mockResolvedValue([{ id: 1, code: '01', name: 'Allowed branch', active: true }])
    patientSearch.mockResolvedValue({ items: [], totalItems: 0 })
    create.mockResolvedValue({ id: 99 })
  })
  it('sends keyword/date/branch/sort before paging and preserves server totals', async () => {
    const wrapper = view(); await flushPromises()
    expect(branches).toHaveBeenCalledWith({ accessible: true })
    await wrapper.get('input[placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"]').setValue('Outside page')
    await wrapper.get('select[aria-label="กรองตามสาขา"]').setValue('01')
    await wrapper.get('.test-date').setValue('2026-10-07')
    await wrapper.get('select[aria-label="เรียงลำดับวันที่นัด"]').setValue('oldest')
    await flushPromises()
    expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ query: 'Outside page', branchCode: '01', date: '2026-10-07', sort: 'oldest', page: 0 }))
    await wrapper.findAll('nav.appointment-pagination button')[1]!.trigger('click'); await flushPromises()
    expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, query: 'Outside page' }))
    expect(wrapper.text()).toContain('ทั้งหมด 23 นัดหมาย')
    expect(wrapper.text()).toContain('Configured room')
  })
  it('edits the Bangkok calendar date and offers only existing allowed actions', async () => {
    const wrapper = view(); await flushPromises()
    const labels = wrapper.findAll('tbody select option').map((option) => option.attributes('value'))
    expect(labels).toEqual(['CONFIRMED', 'ARRIVED', 'CANCELLED', 'NO_SHOW'])
    await wrapper.findAll('button').find((button) => button.text() === 'แก้ไข')!.trigger('click')
    expect(wrapper.get('.test-date').element).toHaveProperty('value', '2026-10-07')
  })
  it('uses remote patient lookup instead of preloading the complete patient database', async () => {
    const wrapper = view(); await flushPromises()
    expect(patientSearch).not.toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('สร้างนัดหมาย'))!.trigger('click')
    await wrapper.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').setValue('260110')
    await flushPromises()
    expect(patientSearch).toHaveBeenLastCalledWith('260110', 0, 8)
  })
  it('submits the exact selected patient ID when two patients share a name', async () => {
    const first = { id: 81, hn: '2601100081', fullName: 'ชื่อซ้ำ', phone: '0800000081' }
    const second = { id: 82, hn: '2601100082', fullName: 'ชื่อซ้ำ', phone: '0800000082' }
    patientSearch.mockResolvedValue({ items: [first, second], totalItems: 2 })
    const wrapper = view(); await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('สร้างนัดหมาย'))!.trigger('click')
    await wrapper.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').setValue('ชื่อซ้ำ')
    await flushPromises()
    await wrapper.findAll('.patient-search-results button')[1]!.trigger('mousedown')
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ patientId: second.id }))
  })
  it('does not resolve a typed name to an arbitrary patient without explicit selection', async () => {
    patientSearch.mockResolvedValue({ items: [{ id: 81, hn: '2601100081', fullName: 'ชื่อซ้ำ', phone: '0800000081' }], totalItems: 1 })
    const wrapper = view(); await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('สร้างนัดหมาย'))!.trigger('click')
    await wrapper.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').setValue('ชื่อซ้ำ')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('กรุณาเลือกผู้ป่วยจากรายการค้นหา')
  })
})
