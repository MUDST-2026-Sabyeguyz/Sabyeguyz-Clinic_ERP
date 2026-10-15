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

  it('keeps the backend-supported contact fields for a new-patient appointment', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    expect(source).toContain('v-if="appointmentPatientType === \'CURRENT\'"')
    expect(source).toContain('v-model.trim="patientQuery"')
    expect(source).toContain('v-model.trim="contactPhone"')
    expect(source).toContain("patientType: 'NEW' as const")
    expect(source).toContain('contactName: patientQuery.value')
  })

  it('places service type on the row below patient type for current and new patients', () => {
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

    expect(createGrid?.[1]).toContain("'patient-type selection'")
    expect(createGrid?.[1]).toContain("'service-mode therapist'")
    expect(newPatientGrid?.[1]).toContain("'patient-type selection'")
    expect(newPatientGrid?.[1]).toContain("'service-mode therapist'")
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

  it('allows the new-patient appointment path to reach the backend', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const requestSave = source.match(/function requestSaveConfirmation\(\) \{([\s\S]*?)\n\}/)

    expect(requestSave?.[1]).not.toContain('ยังไม่รองรับการบันทึกผู้ป่วย NEW')
    expect(source).toContain("const creatingNew = !editingId.value && appointmentPatientType.value === 'NEW'")
    expect(source).toContain("patientType: 'NEW' as const")
  })

  it('marks the new-patient name and phone as required', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    // a new patient is asked for a nickname (required) and a phone (required); the service is optional
    expect(source).toContain("appointmentPatientType === 'NEW' ? 'ชื่อเล่น' : 'ผู้ป่วย'")
    expect(source).toContain('เบอร์โทร <em>*</em>')
    expect(source).toContain('ประเภทบริการ (ไม่บังคับ)')
  })

  it('offers the free times of the therapist from the server instead of a fixed list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    expect(source).toContain('appointmentApi.availability')
    expect(source).toContain(':disabled="!slot.available"')
    expect(source).not.toContain('เวลานอกกะ')
    expect(source).not.toContain('ติด Busy ใน Google Calendar')
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

  it('keeps the branch visible in the appointment list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )
    const table = source.match(/<table class="staff-table">([\s\S]*?)<\/table>/)

    expect(table?.[1]).toContain('<th>สาขา</th>')
    expect(table?.[1]).toContain('{{ branchName(item.branchCode) }}')
    expect(table?.[1]).toContain('colspan="9"')
  })
})
