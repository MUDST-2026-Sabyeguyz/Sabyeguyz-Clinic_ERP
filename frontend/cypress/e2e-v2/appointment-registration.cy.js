/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
const workday = (fx) => {
  const day = new Date(`${fx.tomorrow}T12:00:00Z`)
  while (day.getUTCDay() === 0 || day.getUTCDay() === 1) day.setUTCDate(day.getUTCDate() + 1)
  return day.toISOString().slice(0, 10)
}

const slot = (date, time = '09:00') => {
  const start = new Date(`${date}T${time}:00+07:00`)
  const end = new Date(start.getTime() + 60 * 60_000)
  return { startAt: start.toISOString(), endAt: end.toISOString() }
}

describe('P3 appointment registration and service navigation', () => {
  it('creates a NEW appointment without creating a patient or showing room/branch selectors to staff', function () {
    cy.openBookingHours('01')
    const date = workday(this.fixtures)
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[type=radio][value=NEW]').check()
    cy.get('input[placeholder="กรอกชื่อเล่น"]').type('คนไข้ ใหม่')
    cy.get('input[placeholder="กรอกเบอร์โทรศัพท์"]').type('0891234567')
    cy.get('button[aria-label="เลือกสาขา"]').should('not.exist')
    cy.get('button[aria-label="เลือกห้อง"]').should('not.exist')
    cy.api('GET', '/api/catalog/therapists').then(({ body: therapists }) => {
      const therapist = therapists.find((item) => item.id === this.fixtures.therapistId)
      cy.get('input.filter-dropdown__button[aria-label="เลือกนักกายภาพ"]').click()
      cy.get('[role="listbox"][aria-label="เลือกนักกายภาพ"] [role="option"]').contains(therapist.fullName).click()
    })
    cy.contains('label', 'วันที่').pickDate(date)
    cy.contains('button', 'ดูตารางเวลาว่าง').click()
    cy.get('.appointment-time-drawer').within(() => {
      cy.contains('.appointment-time-slots button', /^09:00/).click()
      cy.contains('button', 'ยืนยันเวลา').click()
    })
    cy.contains('button', 'รายคอร์ส').click()
    cy.get('textarea[placeholder="ระบุรายละเอียดเพิ่มเติม"]').type('นัดครั้งแรก')
    cy.contains('.appointment-save button.primary', /^บันทึก/).click()
    cy.get('.appointment-confirm-modal button.primary').click()
    cy.contains('tr', 'คนไข้ ใหม่', { timeout: 15000 }).should('contain.text', '0891234567')
    cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
    cy.api('GET', '/api/appointments').then(({ body }) => {
      expect(body.items[0].patientType).to.equal('NEW')
      expect(body.items[0].patient.id).to.equal(null)
      expect(body.items[0].note).to.equal('นัดครั้งแรก')
    })
  })

  it('moves a NEW appointment to patient registration and services with appointment context', function () {
    cy.openBookingHours('01')
    const date = workday(this.fixtures)
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      ...slot(date),
      patientType: 'NEW',
      contactName: 'สมชาย ใจดี',
      contactPhone: '0891234567',
      status: 'Unconfirmed',
    }).then(({ status, body: appointment }) => {
      expect(status, JSON.stringify(appointment)).to.equal(201)
      cy.visitAs('therapist', '/staff/appointments')
      cy.chooseDropdown('สถานะนัดหมายของ สมชาย ใจดี', 'ยืนยัน')
      cy.location('pathname').should('eq', '/staff/patients/new')
      cy.location('search').should('contain', `appointmentId=${appointment.id}`)
      cy.get('input[placeholder="ชื่อ"]').type('สมชาย')
      cy.get('input[placeholder="นามสกุล"]').type('ใจดี')
      cy.get('input[placeholder="ชื่อเล่น"]').type('ชาย')
      cy.get('input[placeholder="กรอกตัวเลขไม่เกิน 10 หลัก"]').should('have.value', '0891234567')
      cy.contains('label', 'วันเกิด').pickDate('1990-01-15')
      cy.contains('label', 'อีเมล').find('input').type('p3@gmail.com')
      cy.get('input[placeholder="x-xxxx-xxxxx-xx-x"]').type('1100000000777')
      cy.get('button[type="submit"]').click()
      cy.get('[aria-labelledby="registration-confirmation-title"]').contains('button', /^ยืนยัน$/).click()
      cy.location('pathname').should('eq', '/staff/services')
      cy.location('search').should('contain', 'patientId=').and('contain', `appointmentId=${appointment.id}`)
      cy.contains('สมชาย ใจดี').should('be.visible')
      cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 1)
    })
  })

  it('edits NEW notes without a patient and clears the contact fields for the next booking', function () {
    cy.openBookingHours('01')
    const date = workday(this.fixtures)
    cy.api('POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      ...slot(date),
      patientType: 'NEW',
      contactName: 'คนไข้ ใหม่',
      contactPhone: '0891234567',
      status: 'Unconfirmed',
    }).then(({ body }) => {
      cy.visitAs('therapist', '/staff/appointments')
      cy.contains('tr', 'คนไข้ ใหม่').contains('button', 'แก้ไข').click()
      cy.get('.appointment-note-field textarea').type('แก้หมายเหตุ')
      cy.intercept('PATCH', `**/api/appointments/${body.id}`).as('patchAppt')
      cy.contains('.appointment-save button.primary', /^บันทึก/).click()
      cy.wait('@patchAppt').its('response.statusCode').should('equal', 200)
      cy.contains('tr', 'คนไข้ ใหม่', { timeout: 15000 }).should('be.visible')
      cy.api('GET', `/api/appointments/${body.id}`).its('body.note').should('eq', 'แก้หมายเหตุ')
      cy.contains('button', 'สร้างนัดหมาย').click()
      cy.get('input[type=radio][value=NEW]').check()
      cy.get('input[placeholder="กรอกเบอร์โทรศัพท์"]').should('have.value', '')
    })
  })

  it('takes an unconfirmed CURRENT patient to services via status confirmation', function () {
    cy.openBookingHours('01')
    const date = workday(this.fixtures)
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        therapistId: this.fixtures.therapistId,
        ...slot(date),
        patientId: patient.id,
        serviceId: this.fixtures.serviceId,
      }).then(({ body }) => {
        cy.visitAs('therapist', '/staff/appointments')
        cy.chooseDropdown('สถานะนัดหมายของ สมชาย ใจดี', 'ยืนยัน')
        cy.location('pathname').should('eq', '/staff/services')
        cy.location('search').should('contain', `patientId=${patient.id}`).and('contain', `appointmentId=${body.id}`)
      })
    })
  })
})
