import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('AppointmentView', () => {
  for (const filename of ['AppointmentView.vue', 'ManagerAppointmentView.vue']) {
    it(`${filename} follows the main appointment status and interval contract`, () => {
      const source = readFileSync(
        resolve(process.cwd(), `src/features/appointments/views/${filename}`),
        'utf8',
      )
      const statusChange = source.match(/async function requestStatusChange\([\s\S]*?\n\}/)?.[0] ?? ''
      const statusChoices = source.match(/const appointmentStatusOptions:[\s\S]*?\n\]/)?.[0] ?? ''

      expect(source).toContain('appointmentInterval(')
      expect(source).toContain('endAt: interval.endAt')
      expect(source).toContain('appointmentEndFields(')
      expect(source).toContain('appointmentLocalFields(item.startAt)')
      expect(source).toContain('?.durationMinutes || 0')
      expect(source).not.toContain('?.durationMinutes || 60')
      expect(source).toContain('appointmentDurationMinutes.value = result.durationMinutes')
      expect(source).not.toMatch(/appointmentApi\.(confirm|checkIn|startService|complete|noShow)\(/)
      expect(source).not.toMatch(/status === '(RESCHEDULED|NO_SHOW|COMPLETED|ARRIVED|IN_SERVICE)'/)
      expect(statusChoices).not.toContain("'PENDING'")
      expect(statusChange).toContain("item.status !== 'UNCONFIRMED'")
      expect(statusChange).toContain("name: 'patient-registration'")
      expect(statusChange).toContain("name: 'staff-services'")
      expect(statusChange).not.toContain("saveStatus(item, 'CONFIRMED')")
      expect(source).toContain("value !== 'PENDING'")
    })

    it(`${filename} keeps the appointment table free of a separate actions column`, () => {
      const source = readFileSync(
        resolve(process.cwd(), `src/features/appointments/views/${filename}`),
        'utf8',
      )
      const table = source.match(/<table[\s\S]*?class="staff-table"[\s\S]*?>([\s\S]*?)<\/table>/)?.[1] ?? ''
      const header = table.match(/<thead>([\s\S]*?)<\/thead>/)?.[1] ?? ''
      const row = table.match(/<tbody>([\s\S]*?)<\/tbody>/)?.[1] ?? ''

      expect(header.match(/<th\b/g) ?? []).toHaveLength(7)
      expect(row.match(/<td\b/g) ?? []).toHaveLength(7)
      expect(header).not.toContain('status-notice-cell')
      expect(table).not.toContain('AppointmentProcessActions')
      expect(source).not.toContain('class="appointment-register"')
      expect(source).toContain('class="appointment-edit"')
    })
  }

  it('does not show a patient search box in the appointment list filters', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const filters = source.match(
      /<div v-if="scheduleView === 'list'" class="appointment-list-summary">([\s\S]*?)<section v-if="scheduleView !== 'list'" class="appointment-calendar">/,
    )

    expect(filters?.[1]).toBeDefined()
    expect(filters?.[1]).not.toContain('class="search-box"')
    expect(filters?.[1]).not.toContain('v-model="query"')
  })

  it('keeps the date filter in the list summary and the service filter in the table header', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )

    const summary = source.match(
      /<div v-if="scheduleView === 'list'" class="appointment-list-summary">([\s\S]*?)<section v-if="scheduleView !== 'list'" class="appointment-calendar">/,
    )
    const header = source.match(/<table[\s\S]*?class="staff-table"[\s\S]*?<thead>([\s\S]*?)<\/thead>/)

    expect(summary?.[1]).toBeDefined()
    expect(summary?.[1]).toContain('appointment-list-filters')
    expect(summary?.[1]).toContain('<AppointmentDateFilter')
    expect(header?.[1]).toContain('class="appointment-service-filter"')
    expect(header?.[1]).toContain('v-model="listServiceMode"')
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

  it('orders the create form by flow: patient, then service, then date and time, then note', () => {
    const styles = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/styles/appointment-create-layout.css'),
      'utf8',
    )
    const areas = (selector: RegExp) =>
      [...(styles.match(selector)?.[1].matchAll(/'([^']+)'/g) ?? [])].map((match) => match[1])
    const current = areas(
      /\.appointment-create-grid \{[^}]*?grid-template-areas:([\s\S]*?);/,
    )
    const newPatient = areas(
      /\.appointment-create-grid\.is-new-patient \{\s*grid-template-areas:([\s\S]*?);/,
    )

    expect(current).toEqual([
      'patient-type patient-type',
      'patient phone',
      'service-mode service-mode',
      'selection therapist',
      'summary summary',
      'date time',
      'note note',
    ])
    expect(newPatient).toEqual([
      'patient-type patient-type',
      'new-patient new-patient',
      'service-mode service-mode',
      'selection therapist',
      'summary summary',
      'date time',
      'note note',
    ])
  })

  it('shows only the HN under the selected patient and scrolls the new form to the top', () => {
    for (const file of ['AppointmentView.vue', 'ManagerAppointmentView.vue']) {
      const source = readFileSync(resolve(process.cwd(), `src/features/appointments/views/${file}`), 'utf8')
      expect(source).toContain('HN {{ selectedFormPatient.hn }}')
      expect(source).not.toContain('{{ selectedFormPatient.hn }} · {{ selectedFormPatient.phone }}')
      expect(source).toContain('void nextTick(() => window.scrollTo({ top: 0, behavior')
    }
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
    expect(source).toContain('@click="goToAppointmentList"')
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
    const table = source.match(/<table[\s\S]*?class="staff-table"[\s\S]*?>([\s\S]*?)<\/table>/)

    expect(table?.[1]).toBeDefined()
    expect(table?.[1]).not.toContain('<th>ห้อง</th>')
    expect(table?.[1]).not.toContain('{{ appointmentRoom(item) }}')
  })

  it('does not show a branch column in the appointment list', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/appointments/views/AppointmentView.vue'),
      'utf8',
    )
    const table = source.match(/<table[\s\S]*?class="staff-table"[\s\S]*?>([\s\S]*?)<\/table>/)

    expect(table?.[1]).toBeDefined()
    expect(table?.[1]).not.toContain('<th>สาขา</th>')
    expect(table?.[1]).not.toContain('{{ branchName(item.branchCode) }}')
  })

})
