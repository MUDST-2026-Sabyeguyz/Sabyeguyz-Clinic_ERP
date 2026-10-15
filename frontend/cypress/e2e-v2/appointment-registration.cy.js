/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
const at = (fx) => `${fx.tomorrow}T09:00:00+07:00`
describe('P3 appointment registration and service navigation', () => {
  it('creates a NEW appointment without creating a patient or showing room/branch selectors to staff', function () {
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[type=radio][value=NEW]').check()
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('คนไข้ ใหม่')
    cy.get('input[placeholder="เบอร์โทรผู้ป่วยใหม่"]').type('0891234567')
    cy.get('button[aria-label="เลือกสาขา"]').should('not.exist')
    cy.get('button[aria-label="เลือกห้อง"]').should('not.exist')
    cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
    cy.chooseDropdown('เลือกประเภทบริการ', 'รายคอร์ส')
    cy.get('textarea[placeholder="ระบุรายละเอียดเพิ่มเติม"]').type('นัดครั้งแรก')
    cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
    cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
    cy.contains('.appointment-list-mode-toggle button', 'รายคอร์ส').click()
    cy.contains('tr', 'คนไข้ ใหม่').should('contain.text', '0891234567')
    cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
    cy.api('GET', '/api/appointments').then(({ body }) => {
      expect(body.items[0].patientType).to.equal('NEW')
      expect(body.items[0].patient.id).to.equal(null)
      expect(body.items[0].note).to.equal('นัดครั้งแรก')
      expect(body.items[0].service.name).to.equal('Course')
    })
  })

  it('confirms NEW, registers once and retries linking without making a duplicate patient', function () {
    cy.api('POST', '/api/appointments', { branchCode: '01', therapistId: this.fixtures.therapistId,
      startAt: at(this.fixtures), patientType: 'NEW', contactName: 'สมชาย ใจดี', contactPhone: '0891234567', status: 'Unconfirmed' })
      .then(({ body: appointment }) => {
        cy.visitAs('therapist', '/staff/appointments')
        cy.contains('tr', 'สมชาย ใจดี').contains('button', 'ยืนยัน').click()
        cy.contains('tr', 'สมชาย ใจดี').contains('button', 'ลงทะเบียน').click()
        cy.location('pathname').should('eq', '/staff/patients/new')
        cy.get('input[placeholder="ชื่อ"]').type('สมชาย')
        cy.get('input[placeholder="นามสกุล"]').type('ใจดี')
        cy.get('input[placeholder="ชื่อเล่น"]').type('ชาย')
        cy.get('input[placeholder="xxx-xxx-xxxx"]').should('have.value', '0891234567')
        cy.contains('label', 'วันเกิด').pickDate('1990-01-15')
        cy.contains('label', 'อีเมล').find('input').type('p3@gmail.com')
        cy.get('button[aria-label="ประเภทเอกสารยืนยันตัวตน"]').should('contain.text', 'บัตรประชาชน')
        cy.get('input[placeholder="กรอกเลขบัตรประชาชน 13 หลัก"]').type('1100000000777')
        cy.intercept({ method: 'POST', url: `**/api/appointments/${appointment.id}/link-patient`, times: 1 },
          { statusCode: 503, body: { message: 'เชื่อมนัดหมายไม่สำเร็จชั่วคราว' } }).as('linkFailure')
        cy.contains('button', 'บันทึก').click()
        cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
        cy.wait('@linkFailure')
        cy.contains('button', 'ลองเชื่อมนัดหมายอีกครั้ง').click()
        cy.location('pathname').should('eq', '/staff/services')
        cy.location('search').should('contain', `appointmentId=${appointment.id}`)
        cy.contains('สมชาย ใจดี').should('be.visible')
        cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 1)
        cy.api('GET', `/api/appointments/${appointment.id}`).its('body.patientType').should('equal', 'CURRENT')
      })
  })

  it('edits NEW notes without a patient and clears the contact fields for the next booking', function () {
    cy.api('POST', '/api/appointments', { branchCode: '01', therapistId: this.fixtures.therapistId,
      startAt: at(this.fixtures), patientType: 'NEW', contactName: 'คนไข้ ใหม่', contactPhone: '0891234567', status: 'Unconfirmed' })
      .then(({ body }) => {
        cy.visitAs('therapist', '/staff/appointments')
        cy.contains('tr', 'คนไข้ ใหม่').contains('button', 'แก้ไข').click()
        cy.get('.reschedule-note-field textarea').type('แก้หมายเหตุ')
        cy.contains('.appointment-save button', 'บันทึก').click()
        cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
        cy.contains('เลื่อนนัดหมายสำเร็จ').should('be.visible')
        cy.api('GET', `/api/appointments/${body.id}`).its('body.note').should('eq', 'แก้หมายเหตุ')
        cy.contains('button', 'สร้างนัดหมาย').click()
        cy.get('input[type=radio][value=NEW]').check()
        cy.get('input[placeholder="เบอร์โทรผู้ป่วยใหม่"]').should('have.value', '')
        cy.get('button[aria-label="เลือกประเภทบริการ"]').should('contain.text', 'รายครั้ง')
      })
  })

  it('shows NEW contact phone on the calendar and removes the service filter and staff branch selector', function () {
    cy.api('POST', '/api/appointments', { branchCode: '01', therapistId: this.fixtures.therapistId,
      startAt: at(this.fixtures), patientType: 'NEW', contactName: 'คนไข้ ปฏิทิน', contactPhone: '0891234567' })
    cy.visitAs('therapist', '/staff/appointments')
    cy.get('button[aria-label="กรองบริการ"]').should('not.exist')
    cy.contains('button', 'ดูปฏิทินนัดหมาย').click()
    cy.get('button[aria-label="กรองสาขา"]').should('not.exist')
    cy.get('button[aria-label="วันถัดไป"]').click()
    cy.contains('.calendar-slot', 'คนไข้ ปฏิทิน').should('contain.text', '0891234567')
    cy.contains('.calendar-slot', 'คนไข้ ปฏิทิน').click()
    cy.location('pathname').should('eq', '/staff/patients/new')
    cy.location('search').should('contain', 'appointmentId=')
  })

  it('takes a confirmed CURRENT patient directly to services without registering or searching again', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/appointments', { branchCode: '01', therapistId: this.fixtures.therapistId,
        startAt: at(this.fixtures), patientId: patient.id, serviceId: this.fixtures.serviceId }).then(({ body }) => {
        cy.visitAs('therapist', '/staff/appointments')
        cy.contains('tr', 'สมชาย ใจดี').contains('button', 'แก้ไข').should('not.exist')
        cy.contains('tr', 'สมชาย ใจดี').contains('button', 'ใช้บริการ').click()
        cy.location('pathname').should('eq', '/staff/services')
        cy.location('search').should('contain', `patientId=${patient.id}`).and('contain', `appointmentId=${body.id}`)
        cy.contains(`อ้างอิงนัดหมาย #${body.id}`).should('be.visible')
        cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 1)
        cy.api('GET', `/api/appointments/${body.id}`).its('body.status').should('equal', 'CONFIRMED')
        cy.contains('button', 'เปลี่ยนผู้ป่วย').click()
        cy.location('search').should('not.contain', 'appointmentId').and('not.contain', 'patientId')
        cy.contains(`อ้างอิงนัดหมาย #${body.id}`).should('not.exist')
      })
    })
  })
})
