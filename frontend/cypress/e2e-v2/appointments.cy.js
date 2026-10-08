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

const statusSelect = (name) => cy.contains('tr', name).find('select.status-select')

describe('appointment list', () => {
  it('shows who is booked with what, when and the status', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('h1', 'นัดหมาย').should('be.visible')
    cy.get('tbody tr').should('have.length', 1)
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '09:00').and('contain.text', '01')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยืนยันแล้ว')
  })

  it('says so when there is no appointment', () => {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('td', 'ไม่พบข้อมูลนัดหมาย').should('be.visible')
  })

  it('shows an appointment that is still waiting for confirmation', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'รอยืนยัน')
  })

  it('searches by patient name and filters by status', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
      .then((patient) => book(this.fixtures, patient))
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { startAt: at(this.fixtures, '10:00') })
      .then((appointment) => cy.api('POST', `/api/appointments/${appointment.id}/cancel`, { reason: 'ทดสอบ' })))
    cy.visitAs('manager', '/staff/appointments')
    cy.get('tbody tr').should('have.length', 2)
    cy.get('input[placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"]').type('อรุณ')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'อรุณ แสงทอง')
    cy.get('input[placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"]').clear()
    cy.get('select[aria-label="กรองตามสถานะนัดหมาย"]').select('CANCELLED')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })
})

describe('creating and changing appointments', () => {
  it('books an appointment through the form', function () {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
    cy.contains('label', 'เวลา').find('select').select('10:00')
    cy.contains('button', 'บันทึกนัดหมาย').click()
    cy.contains('ต้องการสร้างนัดหมายนี้ใช่หรือไม่').should('be.visible')
    cy.contains('button', 'ยืนยันการสร้าง').click()
    cy.contains('สร้างนัดหมายเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', '10:00')
    cy.api('GET', '/api/appointments').its('body.items').should('have.length', 1)
      .its('0.startAt').should('contain', `${this.fixtures.tomorrow}T10:00`)
  })

  it('does not create anything when the confirmation is dismissed', function () {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').type('สมชาย')
    cy.contains('.patient-search-results button', 'สมชาย ใจดี').click()
    cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
    cy.contains('button', 'บันทึกนัดหมาย').click()
    cy.get('.appointment-confirm-modal').contains('button', 'ยกเลิก').click()
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('asks for a patient that exists', function () {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').type('ไม่มีคนนี้')
    cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
    cy.contains('button', 'บันทึกนัดหมาย').click()
    cy.contains('button', 'ยืนยันการสร้าง').click()
    cy.get('.patient-input-error').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 0)
  })

  it('refuses a second booking of the same therapist at the same time', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').type('อรุณ')
    cy.contains('.patient-search-results button', 'อรุณ แสงทอง').click()
    cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
    cy.contains('label', 'เวลา').find('select').select('09:00')
    cy.contains('button', 'บันทึกนัดหมาย').click()
    cy.contains('button', 'ยืนยันการสร้าง').click()
    cy.get('[role="alert"]').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 1)
  })

  it('moves an appointment to another time', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('tr', 'สมชาย ใจดี').contains('button', 'แก้ไข').click()
    cy.contains('h1', 'แก้ไขนัดหมาย').should('be.visible')
    cy.contains('label', 'เวลา').find('select').select('14:00')
    cy.contains('button', 'บันทึกการแก้ไข').click()
    cy.contains('button', 'ยืนยันการแก้ไข').click()
    cy.contains('แก้ไขนัดหมายเรียบร้อยแล้ว').should('be.visible')
    cy.api('GET', '/api/appointments').its('body.items.0.startAt').should('contain', 'T14:00')
  })
})

describe('appointment status', () => {
  it('follows the visit: check in, start service, complete', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('ARRIVED')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'มาถึงคลินิก')
    statusSelect('สมชาย ใจดี').select('IN_SERVICE')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'กำลังรับบริการ')
    statusSelect('สมชาย ใจดี').select('COMPLETED')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'เสร็จสิ้น')
    cy.api('GET', '/api/appointments').its('body.items.0.status').should('equal', 'Completed')
  })

  it('confirms an appointment that was waiting for confirmation', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient, { status: 'Unconfirmed' }))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('CONFIRMED')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยืนยันแล้ว')
    cy.api('GET', '/api/appointments').its('body.items.0.status').should('equal', 'Confirmed')
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
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ยกเลิก')
    cy.api('GET', '/api/appointments').its('body.items.0').then((appointment) => {
      expect(appointment.status).to.equal('Cancelled')
      expect(appointment.cancelReason).to.equal('คนไข้ติดธุระ')
    })
  })

  it('marks a no-show', function () {
    cy.registerPatient().then((patient) => book(this.fixtures, patient))
    cy.visitAs('manager', '/staff/appointments')
    statusSelect('สมชาย ใจดี').select('NO_SHOW')
    statusSelect('สมชาย ใจดี').find('option:selected').should('have.text', 'ไม่มาตามนัด')
    cy.api('GET', '/api/appointments').its('body.items.0.status').should('equal', 'NoShow')
  })
})
