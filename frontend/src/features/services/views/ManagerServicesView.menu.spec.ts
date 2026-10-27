import { describe, expect, it } from 'vitest'
import source from './ManagerServicesView.vue?raw'

describe('manager services patient menu', () => {
  it('keeps the two care workflows distinct and action-led', () => {
    expect(source).toContain('เริ่มรายการขาย')
    expect(source).toContain('>ใช้สิทธิ์<')
  })

  it('makes each workflow card its own full-surface button without a nested action button', () => {
    expect(source).toContain('class="service-menu-action service-menu-action--sale"')
    expect(source).toContain('class="service-menu-action service-menu-action--entitlement"')
    expect(source).not.toContain('<article class="service-menu-action')
    expect(source).not.toContain('<button type="button" @click="startSale">เริ่มรายการขาย</button>')
    expect(source).not.toContain(
      '<button type="button" @click="openPatientCourses">ใช้สิทธิ์</button>',
    )
  })

  it('shows real course eligibility and lets staff change the selected patient', () => {
    expect(source).toContain('courseApi.patientCourses')
    expect(source).toContain('มีสิทธิ์คอร์ส')
    expect(source).toContain('เปลี่ยนผู้ป่วย')
  })

  it('uses the phone icon only when a phone number exists', () => {
    expect(source).toContain('<PhoneIcon v-if="patient.phone"')
    expect(source).toContain("patient.phone || '-'")
  })
})
