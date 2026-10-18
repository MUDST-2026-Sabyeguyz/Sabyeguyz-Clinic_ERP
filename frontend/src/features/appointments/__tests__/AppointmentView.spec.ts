import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('AppointmentView', () => {
  it('does not show a patient search box in the appointment list filters', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const filters = source.match(
      /<header class="panel-head appointment-list-filters">([\s\S]*?)<\/header>/,
    )

    expect(filters?.[1]).not.toContain('class="search-box"')
    expect(filters?.[1]).not.toContain('v-model="query"')
  })

  it('keeps the service mode toggle and date filters in the same control row', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const controls = source.match(
      /<div v-show="scheduleView === 'list'" class="appointment-list-controls">([\s\S]*?)<\/header>\s*<\/div>/,
    )

    expect(controls?.[1]).toContain('class="appointment-list-mode-toggle"')
    expect(controls?.[1]).toContain('class="panel-head appointment-list-filters"')
  })

  it('shows the Figma patient details form when creating an appointment for a new patient', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    expect(source).toContain('v-if="appointmentPatientType === \'CURRENT\'"')
    expect(source).toContain('v-model.trim="newPatientForm.firstName"')
    expect(source).toContain('v-model.trim="newPatientForm.lastName"')
    expect(source).toContain('v-model.trim="newPatientForm.nickname"')
    expect(source).toContain('v-model.trim="newPatientForm.phone"')
    expect(source).toContain('ผู้จอง NEW ยังไม่มี HN')
  })

  it('places service type beside patient type for current and new patients', () => {
    const styles = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/styles/appointments.css'),
      'utf8',
    )
    const createGrid = styles.match(
      /\.appointment-create-card \.appointment-create-grid \{([\s\S]*?)\n\}/,
    )
    const newPatientGrid = styles.match(
      /\.appointment-create-card \.appointment-create-grid\.is-new-patient \{([\s\S]*?)\n\}/,
    )

    expect(createGrid?.[1]).toContain("'patient-type service-mode'")
    expect(createGrid?.[1]).toContain("'patient selection'")
    expect(newPatientGrid?.[1]).toContain("'patient-type service-mode'")
    expect(newPatientGrid?.[1]).toContain("'new-patient selection'")
  })

  it('returns calendar-created appointments to the calendar instead of the list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )
    const goBack = source.match(
      /function goToAppointmentList\(\) \{([\s\S]*?)\n\}\nfunction choosePatient/,
    )

    expect(goBack?.[1]).toContain("createSource.value === 'calendar'")
    expect(goBack?.[1]).toContain("name: 'staff-appointment-calendar'")
    expect(goBack?.[1]).toContain("name: 'staff-appointments'")
    expect(source).toContain("isCalendarCreate ? '← กลับไปหน้าปฏิทิน' : '← กลับไปหน้านัดหมาย'")
  })

  it('allows a new-patient appointment after validating its contact details', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const requestSave = source.match(/function requestSaveConfirmation\(\) \{([\s\S]*?)\n\}/)

    expect(requestSave?.[1]).toContain("appointmentPatientType.value === 'NEW'")
    expect(requestSave?.[1]).toContain('isContactPhoneValid(newPatientForm.value.phone)')
    expect(source).toContain("patientType: 'NEW' as const")
    expect(source).not.toContain('ยังไม่รองรับการบันทึกผู้ป่วย NEW')
  })

  it('keeps required markers beside new-patient field labels', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    expect(source).toContain('<span>ชื่อ <small>(ไม่บังคับ)</small></span>')
    expect(source).toContain('<span>นามสกุล <small>(ไม่บังคับ)</small></span>')
    expect(source).toContain('<span>ชื่อเล่น <em>*</em></span>')
    expect(source).toContain('<span>เบอร์โทรศัพท์ <em>*</em></span>')
  })

  it('does not show a room column in the appointment list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )
    const table = source.match(/<table class="staff-table">([\s\S]*?)<\/table>/)

    expect(table?.[1]).not.toContain('<th>ห้อง</th>')
    expect(table?.[1]).not.toContain('{{ appointmentRoom(item) }}')
  })

  it('does not show a branch column in the appointment list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )
    const table = source.match(/<table class="staff-table">([\s\S]*?)<\/table>/)

    expect(table?.[1]).not.toContain('<th>สาขา</th>')
    expect(table?.[1]).not.toContain('{{ branchName(item.branchCode) }}')
    expect(table?.[1]).toContain('colspan="8"')
  })
})
