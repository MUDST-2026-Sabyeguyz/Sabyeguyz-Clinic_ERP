/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const workday = (fx) => {
  const day = new Date(`${fx.tomorrow}T12:00:00Z`)
  while (day.getUTCDay() === 0 || day.getUTCDay() === 1) day.setUTCDate(day.getUTCDate() + 1)
  return day.toISOString().slice(0, 10)
}

const slotAt = (date, time, minutes = 60) => {
  const start = new Date(`${date}T${time}:00+07:00`)
  const end = new Date(start.getTime() + minutes * 60_000)
  return { startAt: start.toISOString(), endAt: end.toISOString() }
}

function book(fx, patient, overrides = {}) {
  return cy.openBookingHours('01').then(() =>
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      patientId: patient.id,
      therapistId: fx.therapistId,
      serviceId: fx.serviceId,
      ...slotAt(workday(fx), '09:00'),
      ...overrides,
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    }),
  )
}

function backendStatus(expected, tries = 20) {
  return cy.api('GET', '/api/appointments').then(({ body }) => {
    if (body.items[0].status === expected) return
    expect(tries, `backend status still ${body.items[0].status}, wanted ${expected}`).to.be.greaterThan(0)
    cy.wait(500)
    backendStatus(expected, tries - 1)
  })
}

describe('appointment list', () => {
  it('shows who is booked with what, when and the status', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('h1', 'นัดหมาย').should('be.visible')
    cy.get('tbody tr').should('have.length', 1)
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '09:00')
    cy.contains('tr', 'สมชาย ใจดี').find('.appointment-status-filter').should('have.attr', 'data-status', 'PENDING')
  })

  it('says so when there is no appointment', () => {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('ยังไม่มีนัดหมาย').should('be.visible')
  })

  it('shows an appointment that is still waiting for confirmation', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('tr', 'สมชาย ใจดี').find('.appointment-status-filter').should('have.attr', 'data-status', 'PENDING')
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', 'ยังไม่ยืนยัน')
  })

  it('filters by service mode and displays cancelled appointments', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
      .then((patient) => book(this.fixtures, patient, { serviceId: this.fixtures.courseId }))
    cy.registerPatient().then((patient) =>
      book(this.fixtures, patient, slotAt(workday(this.fixtures), '10:00'))
        .then((appointment) => cy.api('POST', `/api/appointments/${appointment.id}/cancel`, { reason: 'ทดสอบ' })),
    )
    cy.visitAs('manager', '/staff/appointments')
    cy.get('tbody tr').should('have.length', 2)
    cy.contains('tr', 'สมชาย ใจดี').find('.appointment-status-filter').should('have.attr', 'data-status', 'CANCELLED')
    cy.chooseDropdown('กรองประเภทบริการ', 'รายคอร์ส')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'อรุณ แสงทอง')
    cy.chooseDropdown('กรองประเภทบริการ', 'รายครั้ง')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })
})

describe('creating and changing appointments', () => {
  it('books through the form after delayed master data loads', function () {
    cy.openBookingHours('01')
    cy.registerPatient()
    cy.intercept('GET', '/api/catalog/therapists', (request) => {
      request.on('response', (response) => response.setDelay(1500))
    }).as('therapists')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.wait('@therapists')
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.api('GET', '/api/catalog/therapists').then(({ body: therapists }) => {
      const therapist = therapists.find((item) => item.id === this.fixtures.therapistId)
      cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
      cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(therapist.fullName).click()
    })
    cy.contains('label', 'วันที่').pickDate(workday(this.fixtures))
    cy.contains('button', 'ดูตารางเวลาว่าง').click()
    cy.get('.appointment-time-drawer').within(() => {
      cy.contains('.appointment-time-slots button', /^10:00/).click()
      cy.contains('button', 'ยืนยันเวลา').click()
    })
    cy.contains('.appointment-save button.primary', /^บันทึก/).click()
    cy.contains('ยืนยันการสร้างนัดหมายหรือไม่?').should('be.visible')
    cy.get('.appointment-confirm-modal button.primary').click()
    cy.contains('tr', 'สมชาย ใจดี', { timeout: 15000 }).should('contain.text', '10:00')
    cy.api('GET', '/api/appointments').its('body.items').should('have.length', 1)
  })

  it('does not create anything when the confirmation is dismissed', function () {
    cy.openBookingHours('01')
    cy.registerPatient()
    cy.intercept('GET', '**/api/catalog/therapists').as('therapists')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.wait('@therapists')
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.api('GET', '/api/catalog/therapists').then(({ body: therapists }) => {
      const therapist = therapists.find((item) => item.id === this.fixtures.therapistId)
      cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
      cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(therapist.fullName).click()
    })
    cy.contains('label', 'วันที่').pickDate(workday(this.fixtures))
    cy.contains('button', 'ดูตารางเวลาว่าง').click()
    cy.get('.appointment-time-drawer').within(() => {
      cy.contains('.appointment-time-slots button', /^10:00/).click()
      cy.contains('button', 'ยืนยันเวลา').click()
    })
    cy.contains('.appointment-save button.primary', /^บันทึก/).click()
    cy.get('.appointment-confirm-modal button.secondary').click()
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('asks for a patient that exists', function () {
    cy.openBookingHours('01')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('ไม่มีคนนี้')
    cy.contains('label', 'วันที่').pickDate(workday(this.fixtures))
    cy.contains('.appointment-save button.primary', /^บันทึก/).click()
    cy.contains('[role="alert"]', 'กรุณาเลือกผู้ป่วย').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('refuses a second booking of the same therapist at the same time', function () {
    const date = workday(this.fixtures)
    cy.registerPatient().then((patient) => book(this.fixtures, patient)).as('existingAppointment')
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' }).as('otherPatient')
    cy.intercept('GET', '**/api/catalog/therapists').as('therapists')
    cy.intercept('GET', '**/api/staff').as('staffList')
    cy.intercept('GET', '**/api/catalog/courses').as('courses')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.wait(['@therapists', '@staffList', '@courses'])
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('อรุณ')
    cy.contains('.patient-search-results button', 'อรุณ แสงทอง').click()
    cy.get('@existingAppointment').then((appointment) => {
      cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
      cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(appointment.therapist.fullName).click()
    })
    cy.contains('label', 'วันที่').pickDate(date)
    cy.contains('button', 'ดูตารางเวลาว่าง').click()
    cy.get('.appointment-time-drawer').within(() => {
      cy.contains('.appointment-time-slots button', /^09:00/).should('be.disabled')
    })
    cy.get('@otherPatient').then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: this.fixtures.therapistId,
        serviceId: this.fixtures.serviceId,
        ...slotAt(date, '09:00'),
      }).its('status').should('be.oneOf', [400, 409])
    })
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 1)
  })

  it('moves an appointment to another time', function () {
    const date = workday(this.fixtures)
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('tr', 'สมชาย ใจดี').contains('button', 'แก้ไข').click()
    cy.contains('button', 'ดูตารางเวลาว่าง').click()
    cy.get('.appointment-time-drawer').within(() => {
      cy.contains('.appointment-time-slots button', /^14:00/).click()
      cy.contains('button', 'ยืนยันเวลา').click()
    })
    cy.contains('.appointment-save button.primary', /^บันทึก/).click()
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '14:00')
    cy.api('GET', '/api/appointments').its('body.items.0.startAt').should((val) => {
      expect(new Date(val).getTime()).to.equal(new Date(slotAt(date, '14:00').startAt).getTime())
    })
  })
})

describe('appointment status', () => {
  it('cancels only with a reason', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('manager', '/staff/appointments')
    cy.chooseDropdown('สถานะนัดหมายของ สมชาย ใจดี', 'ยกเลิก')
    cy.contains('h2', 'ยืนยันการยกเลิกนัดหมาย').should('be.visible')
    cy.contains('button', 'ยืนยันการยกเลิก').should('be.disabled')
    cy.get('.status-modal textarea').type('คนไข้ติดธุระ')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.contains('tr', 'สมชาย ใจดี').find('.appointment-status-filter').should('have.attr', 'data-status', 'CANCELLED')
    backendStatus('CANCELLED')
  })

  it('confirms automatically when paid through an appointment', function () {
    cy.registerPatient().then((patient) => {
      book(this.fixtures, patient).then((appointment) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appointment.id,
          paymentLabelCode: 'FULL',
          items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'PromptPay', amount: 800 }],
        }).its('status').should('equal', 201)
        backendStatus('CONFIRMED')
      })
    })
  })
})
