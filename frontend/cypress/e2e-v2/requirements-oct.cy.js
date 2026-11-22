/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const modal = () => cy.get('.drawer')
const sectionButton = (name) => cy.contains('.settings-nav button', name)
const openEditor = (label) => cy.contains('button.settings-add', label).click()
const save = () => {
  cy.get('.drawer').contains('button', 'บันทึก').click()
  cy.noAlert()
}
const branch01 = () => cy.api('GET', '/api/branches').then(({ body }) => body.find((row) => row.branchCode === '01'))
const workday = (fx) => {
  const day = new Date(`${fx.tomorrow}T12:00:00Z`)
  while (day.getUTCDay() === 0 || day.getUTCDay() === 1) day.setUTCDate(day.getUTCDate() + 1)
  return day.toISOString().slice(0, 10)
}
const slot = (date, time = '10:00') => {
  const start = new Date(`${date}T${time}:00+07:00`)
  const end = new Date(start.getTime() + 60 * 60_000)
  return { startAt: start.toISOString(), endAt: end.toISOString() }
}

describe('branch settings', () => {
  it('saves the email, licence, booking window and default appointment length of a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('tbody tr td', /^01$/).closest('tr').contains('button', 'แก้ไข').click()
    modal().contains('label', 'อีเมลสาขา').find('input').clear().type('rama9@clinic.test')
    modal().contains('label', 'เลขใบอนุญาต').find('input').clear().type('PT-1234')
    cy.chooseDropdown('เปิดรับนัดตั้งแต่ ชั่วโมง', '09')
    cy.chooseDropdown('เปิดรับนัดตั้งแต่ นาที', '00')
    cy.chooseDropdown('ถึงเวลา ชั่วโมง', '18')
    cy.chooseDropdown('ถึงเวลา นาที', '00')
    modal().contains('label', 'ระยะเวลานัดเริ่มต้น (นาที)').find('input').clear().type('60')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    branch01().should((branch) => {
      expect(branch).to.include({
        email: 'rama9@clinic.test',
        licenseNo: 'PT-1234',
        bookingOpenTime: '09:00',
        bookingCloseTime: '18:00',
        defaultAppointmentMinutes: 60,
      })
    })
  })

  it('refuses a booking window with only one end', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('tbody tr td', /^01$/).closest('tr').contains('button', 'แก้ไข').click()
    cy.chooseDropdown('เปิดรับนัดตั้งแต่ ชั่วโมง', '09')
    modal().contains('button', 'บันทึก').click()
    cy.get('.drawer').should('contain.text', 'เปิดรับนัด')
    branch01().should((branch) => expect(branch.bookingOpenTime).to.equal(null))
  })
})

describe('service and course settings', () => {
  it('keeps the appointment length of a single visit, which has no sessions or expiry', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('คอร์สและบริการ').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อบริการ/คอร์ส').find('input').type('นวดสั้น')
    cy.get('.drawer button.filter-dropdown__button[aria-label="ประเภท"]').click()
    cy.get('[role="listbox"][aria-label="ประเภท"] [role="option"]').contains('บริการรายครั้ง').click()
    modal().contains('label', 'ราคา (บาท)').find('input').type('700')
    modal().should('not.contain.text', 'อายุคอร์ส').and('not.contain.text', 'จำนวนครั้งรวม')
    modal().contains('label', 'ระยะเวลา (นาที)').find('input').type('45')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.api('GET', '/api/services').its('body').should((rows) => {
      expect(rows.find((row) => row.name === 'นวดสั้น').defaultDurationMinutes).to.equal(45)
    })
  })

  it('saves a course with its total sessions and a validity in months', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('คอร์สและบริการ').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อบริการ/คอร์ส').find('input').type('คอร์สครึ่งปี')
    cy.get('.drawer button.filter-dropdown__button[aria-label="ประเภท"]').click()
    cy.get('[role="listbox"][aria-label="ประเภท"] [role="option"]').contains('คอร์ส').click()
    modal().contains('label', 'ราคา (บาท)').find('input').type('6000')
    modal().contains('label', 'จำนวนครั้งรวม').find('input').clear().type('6')
    modal().contains('label', 'อายุคอร์ส').find('input').clear().type('6')
    modal().find('select[aria-label="หน่วยอายุคอร์ส"]').select('Month')
    modal().contains('label', 'ระยะเวลา (นาที)').find('input').clear().type('60')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.api('GET', '/api/courses').its('body').should((rows) => {
      expect(rows.find((row) => row.name === 'คอร์สครึ่งปี')).to.include({
        sessions: 6,
        validityValue: 6,
        validityUnit: 'Month',
      })
    })
  })
})

describe('appointments: free times and new patient', () => {
  it('offers only the times the therapist is free, inside the branch booking window', function () {
    const date = workday(this.fixtures)
    branch01().then((branch) =>
      cy.api('PUT', `/api/branches/${branch.id}`, {
        branchCode: '01',
        name: branch.name,
        address: branch.address,
        phone: branch.phone,
        bookingOpenTime: '09:00',
        bookingCloseTime: '12:00',
        defaultAppointmentMinutes: 60,
      }).its('status').should('equal', 200),
    )
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: this.fixtures.therapistId,
        ...slot(date, '10:00'),
      }).then(({ status, body: booked }) => {
        expect(status, JSON.stringify(booked)).to.equal(201)
        cy.intercept('GET', '**/api/catalog/therapists').as('therapists')
        cy.visitAs('manager', '/staff/appointments')
        cy.contains('button', 'สร้างนัดหมาย').first().click()
        cy.wait('@therapists')
        cy.contains('label', 'วันที่').pickDate(date)
        cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
        cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(booked.therapist.fullName).click()
        cy.contains('button', 'ดูตารางเวลาว่าง').click()
        cy.get('.appointment-time-drawer').within(() => {
          cy.contains('.appointment-time-slots button', /^09:00/).should('not.be.disabled')
          cy.contains('.appointment-time-slots button', /^10:00/).should('be.disabled')
          cy.contains('.appointment-time-slots button', /^11:00/).should('not.be.disabled')
        })
      }),
    )
  })

  it('does not require a first and last name of a new patient, only the nickname and phone', () => {
    cy.intercept('GET', '**/api/catalog/courses').as('courses')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.location('pathname').should('eq', '/staff/appointments/new')
    cy.wait('@courses')
    cy.get('input[type=radio][value=NEW]').check().should('be.checked')
    cy.get('input[placeholder="กรอกชื่อเล่น"]').should('be.visible')
    cy.get('input[placeholder="กรอกเบอร์โทรศัพท์"]').should('be.visible')
    cy.get('.appointment-new-patient-fields').within(() => {
      cy.contains('label', 'ชื่อเล่น').should('contain.text', '*')
      cy.contains('label', 'เบอร์โทรศัพท์').should('contain.text', '*')
      cy.contains('label', 'นามสกุล').should('not.contain.text', '*')
    })
  })
})

describe('course extension workflow', () => {
  it('lets a therapist request an extension and lets the Manager approve it', function () {
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        caseOwnerStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ status, body: course }) => {
        expect(status).to.equal(201)
        cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
        cy.contains('.course-manager-actions button', 'ต่ออายุคอร์ส').click()
        cy.get('.course-renewal-duration input[type="number"]').clear().type('2')
        cy.chooseDropdown('หน่วยเวลา', 'เดือน')
        cy.contains('button', 'ส่งคำขอให้ Manager').click()
        cy.get('.course-manager-card .renewal-status').should('contain.text', 'รอพิจารณา')
        cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: before }) => {
          const expiryBefore = before[0].expiresAt
          cy.visitAs('manager', `/staff/courses/extensions?patientId=${patient.id}`)
          cy.contains('.request-card button', 'อนุมัติ').click()
          cy.get('.decision-dialog button.decision-confirm').click()
          cy.api('GET', `/api/patients/${patient.id}/courses`).its('body.0.expiresAt').should('not.equal', expiryBefore)
        })
        cy.api('GET', `/api/course-extensions?courseId=${course.id}`).its('body.0.status').should('equal', 'Approved')
      }),
    )
  })
})

describe('branch isolation and transaction tabs', () => {
  it('isolates therapist transactions to their home branch and gives manager sales and usage tabs', function () {
    cy.openBookingHours('01')
    cy.openBookingHours('02')
    cy.registerPatient({ branchCode: '02' }).then((patient) =>
      cy.api('POST', '/api/payments', {
        patientId: patient.id,
        branchCode: '02',
        salespersonStaffId: this.fixtures.therapist2Id,
        treatingStaffId: this.fixtures.therapist2Id,
        paymentLabelCode: 'FULL',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'PromptPay', amount: 800 }],
      }).its('status').should('equal', 201),
    )
    cy.visitAs('therapist', '/staff/transactions')
    cy.contains('ไม่พบรายการ').should('be.visible')
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        caseOwnerStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
      }).then(({ body: course }) =>
        cy.api('POST', '/api/appointments', {
          branchCode: '01',
          patientId: patient.id,
          therapistId: this.fixtures.therapistId,
          serviceId: this.fixtures.courseId,
          ...slot(workday(this.fixtures), '11:00'),
        }).then(({ body: appt }) =>
          cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id, appointmentId: appt.id }),
        ),
      ),
    )
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-tabs button').should('have.length', 3)
    cy.contains('.transaction-tabs button', 'รายการใช้บริการ').click()
    cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })
})

describe('Google Calendar for a therapist', () => {
  it('shows the state cleanly without a sync trigger for a therapist', () => {
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('button', 'ดูปฏิทินนัดหมาย').click()
    cy.location('pathname').should('eq', '/staff/appointments/calendar')
    cy.contains('h1', 'ปฏิทินนัดหมาย').should('be.visible')
    cy.get('.calendar-sync').should('not.exist')
    cy.contains('button', 'ซิงก์').should('not.exist')
  })
})
