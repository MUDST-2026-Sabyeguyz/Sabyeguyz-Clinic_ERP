import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import CourseActionView from '../views/CourseActionView.vue'
import { branchApi, courseApi, patientApi, staffApi, type Patient, type TransferRecord } from '@/api/client'

afterEach(() => vi.restoreAllMocks())

describe('CourseActionView', () => {
  it('waits for initial course history before allowing the transfer form to open', async () => {
    const patient = { id: 1, fullName: 'Synthetic Patient', hn: 'HN01', branchCode: '01' } as Patient
    let finishHistory!: (value: TransferRecord[]) => void
    vi.spyOn(patientApi, 'searchAll').mockResolvedValue([patient])
    vi.spyOn(staffApi, 'list').mockResolvedValue([])
    vi.spyOn(branchApi, 'list').mockResolvedValue([])
    vi.spyOn(courseApi, 'patientCourses').mockResolvedValue([{
      id: 1, patient, branchCode: '01', course: { id: 1, code: 'TEST', name: 'Synthetic course' },
      price: 9000, sessions: 6, bonusSessions: 0, purchased: 6, used: 0, remaining: 6,
      purchasedAt: '2026-10-16', expiresAt: '2099-12-31', expired: false, usable: true, status: 'ACTIVE',
    }])
    vi.spyOn(courseApi, 'transferHistory').mockReturnValue(new Promise(resolve => { finishHistory = resolve }))
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/staff/courses', component: CourseActionView }] })
    await router.push('/staff/courses?patientId=1')
    const wrapper = mount(CourseActionView, {
      global: { plugins: [router], stubs: { StaffHeader: true, ThaiDatePicker: true, CourseExtensionPanel: true } },
    })
    try {
      await flushPromises()
      expect(wrapper.findAll('button').some(button => button.text() === 'แชร์คอร์ส')).toBe(false)
      finishHistory([])
      await flushPromises()
      const transfer = wrapper.findAll('button').find(button => button.text() === 'แชร์คอร์ส')
      expect(transfer).toBeDefined()
      await transfer!.trigger('click')
      await flushPromises()
      expect(wrapper.find('.course-transfer-main').exists()).toBe(true)
      expect(wrapper.find('input[placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"]').isVisible()).toBe(true)
    } finally { wrapper.unmount() }
  })

  it('does not show the redundant expiry edit button on patient course management cards', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/courses/views/CourseActionView.vue'),
      'utf8',
    )

    expect(source).not.toMatch(
      /<button[^>]*title="แก้ไขผ่านเมนูต่ออายุคอร์ส"[^>]*>\s*แก้ไขวันหมดอายุ\s*<\/button>/,
    )
  })
})
