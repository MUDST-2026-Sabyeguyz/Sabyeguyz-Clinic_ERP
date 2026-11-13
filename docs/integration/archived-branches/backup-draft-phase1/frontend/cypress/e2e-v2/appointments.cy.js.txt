/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const at = (fx, time) => `${fx.tomorrow}T${time}:00+07:00`

function book(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/appointments', {
    branchCode: '01', patientId: patient.id, therapistId: fx.therapistId, serviceId: fx.serviceId,
    startAt: at(fx, '09:00'), ...overrides,
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

// The page shows the chosen value before the save has reached the backend: poll the backend until it has the status.
function backendStatus(expected, tries = 20) {
  return cy.api('GET', '/api/appointments').then(({ body }) => {
    if (body.items[0].status === expected) return
    expect(tries, `backend status still ${body.items[0].status}, wanted ${expected}`).to.be.greaterThan(0)
    cy.wait(500)
    backendStatus(expected, tries - 1)
  })
}
const statusSelect = (name) => cy.contains('tr', name).find('select.status-select')
// The select shows the new value at once; the save is finished when the page says so (only then is the backend updated).
const saved = () => cy.contains('.status-feedback', 'บันทึกแล้ว').should('be.visible')

describe('appointment list', () => {
  it('shows who is booked with what, when and the status', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('h1', 'นัดหมาย').should('be.visible')
    cy.get('tbody tr').should('have.length', 1)
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '09:00')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยืนยันแล้ว')
  })

  it('says so when there is no appointment', () => {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('td', 'ไม่พบข้อมูลนัดหมาย').should('be.visible')
  })

  it('shows an appointment that is still waiting for confirmation', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('tr', 'สมชาย ใจดี').find('.appointment-status-filter').should('have.attr', 'data-status', 'PENDING')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'รอยืนยัน')
  })

  it('searches by patient name and filters by status', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
      .then((patient) => book(this.fixtures, patient))
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { startAt: at(this.fixtures, '10:00') })
      .then((appointment) => cy.api('POST', `/api/appointments/${appointment.id}/cancel`, { reason: 'ทดสอบ' })))
    cy.visitAs('manager', '/staff/appointments')
    cy.get('tbody tr').should('have.length', 2)
    cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทร หรือรหัส HN"]').type('อรุณ')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'อรุณ แสงทอง')
    cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทร หรือรหัส HN"]').clear()
    cy.chooseDropdown('กรองตามสถานะนัดหมาย', 'ยกเลิก')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })
})

describe('creating and changing appointments', () => {
  it('books through the form after delayed master data loads', function () {
    cy.registerPatient()
    cy.intercept('GET', '/api/catalog/therapists', (request) => {
      request.on('response', (response) => response.setDelay(2000))
    }).as('therapists')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').should('be.disabled')
    cy.wait('@therapists')
    cy.contains('button', 'สร้างนัดหมาย').should('be.enabled').click()
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.get('.appointment-date-field').pickDate(this.fixtures.tomorrow)
    cy.chooseDropdown('เลือกเวลา', '10:30')
    cy.get('.appointment-save button.primary').click()
    cy.contains('ยืนยันการสร้างนัดหมายหรือไม่?').should('be.visible')
    cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
    cy.contains('tr', 'สมชาย ใจดี', { timeout: 15000 }).should('contain.text', '10:30')
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '10:30')
    cy.api('GET', '/api/appointments').its('body.items').should('have.length', 1)
      .its('0.startAt').should('contain', 'T03:30:00')
  })

  it('does not create anything when the confirmation is dismissed', function () {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.get('.appointment-date-field').pickDate(this.fixtures.tomorrow)
    cy.get('.appointment-save button.primary').click()
    cy.get('.appointment-confirm-modal').contains('button', 'ไม่ใช่').click()
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('asks for a patient that exists', function () {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('ไม่มีคนนี้')
    cy.get('.appointment-date-field').pickDate(this.fixtures.tomorrow)
    cy.get('.appointment-save button.primary').click()
    cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
    cy.get('.patient-input-error').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('refuses a second booking of the same therapist at the same time', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient)).as('existingAppointment')
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' }).as('secondPatient')
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.chooseBranch('เลือกสาขา', '01')
    cy.get('@existingAppointment').then((appointment) => {
      cy.chooseDropdown('เลือกนักกายภาพ', appointment.therapist.fullName)
    })
    cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('อรุณ')
    cy.contains('.patient-search-results button', 'อรุณ แสงทอง').click()
    cy.get('.appointment-date-field').pickDate(this.fixtures.tomorrow)
    cy.get('button[aria-label="เลือกเวลา"]').click()
    cy.get('[role="listbox"][aria-label="เลือกเวลา"] [role="option"]').should(($options) => {
      expect([...$options].map(option => option.textContent.trim())).not.to.include('09:00')
    })
    // The UI removes occupied slots; a direct attempt must also be rejected by the real API.
    cy.get('@secondPatient').then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId,
        serviceId: this.fixtures.serviceId, startAt: at(this.fixtures, '09:00'),
      }).its('status').should('equal', 400)
    })
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 1)
  })

  it('moves an appointment to another time', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('tr', 'สมชาย ใจดี').contains('button', 'แก้ไข').click()
    cy.contains('h1', 'เลื่อนนัดหมาย').should('be.visible')
    cy.get('.reschedule-time-trigger').click()
    cy.get('.appointment-time-drawer').contains('button', /^14:30/).should('be.enabled').click()
    cy.get('.appointment-time-drawer').contains('button', 'ยืนยันเวลา').click()
    cy.get('.appointment-save button.primary').click()
    cy.get('.appointment-confirm-modal').contains('button', /^ใช่$/).click()
    cy.contains('เลื่อนนัดหมายสำเร็จ').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.items.0.startAt').should('contain', 'T07:30:00')
  })
})

describe('appointment status', () => {
  it('follows the visit: check in, start service, complete', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('ARRIVED')
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'มาถึงคลินิก')
    statusSelect('สมชาย ใจดี').select('IN_SERVICE')
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'กำลังรับบริการ')
    statusSelect('สมชาย ใจดี').select('COMPLETED')
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'เสร็จสิ้น')
    backendStatus('COMPLETED')
  })

  it('confirms an appointment that was waiting for confirmation', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('CONFIRMED')
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยืนยันแล้ว')
    backendStatus('CONFIRMED')
  })

  it('cancels only with a reason', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('CANCELLED')
    cy.contains('h2', 'ยืนยันการยกเลิกนัดหมาย').should('be.visible')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.contains('h2', 'ยืนยันการยกเลิกนัดหมาย').should('be.visible')
    cy.get('.status-modal textarea').type('คนไข้ติดธุระ')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยกเลิก')
    // The reason is kept in the status history; the appointment response does not carry it.
    backendStatus('CANCELLED')
  })

  it('marks a no-show', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('NO_SHOW')
    saved()
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ไม่มาตามนัด')
    backendStatus('NO_SHOW')
  })
})
