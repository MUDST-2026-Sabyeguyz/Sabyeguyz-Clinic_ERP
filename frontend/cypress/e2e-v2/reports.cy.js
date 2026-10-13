/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function sellVisit(fx, patient, branchCode = '01') {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode,
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
  }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
}

function sellCourse(fx, patient) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01',
    salespersonStaffId: fx.managerStaffId,
    items: [{ type: 'COURSE', referenceId: fx.courseId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 9000 }],
  }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
}

describe('reports', () => {
  it('is closed to a therapist', () => {
    cy.visitAs('therapist', '/staff/reports')
    cy.location('pathname').should('eq', '/staff/patients')
  })

  it('offers the four reports', () => {
    cy.visitAs('manager', '/staff/reports')
    cy.contains('h1', 'รายงาน').should('be.visible')
    cy.get('#report-select option').then((options) => {
      expect([...options].map((option) => option.textContent.trim())).to.deep.equal(
        ['รายงานยอดขาย', 'ยอดคงเหลือคอร์ส', 'ประวัติการโอนคอร์ส', 'ค่าคอมมิชชั่น'])
    })
  })

  it('says so when there are no sales', () => {
    cy.visitAs('manager', '/staff/reports')
    cy.contains('td', 'ไม่พบข้อมูลตามตัวกรองที่เลือก').should('be.visible')
  })

  it('shows the revenue by sale type with totals', function () {
    cy.registerPatient().then((patient) => {
      sellVisit(this.fixtures, patient)
      sellCourse(this.fixtures, patient)
    })
    cy.visitAs('manager', '/staff/reports')
    cy.contains('.kpi-col', 'รายได้รวม').should('contain.text', '฿9,800')
    cy.contains('.kpi-col', 'รายได้จาก Course Sales').should('contain.text', '฿9,000')
    cy.contains('.kpi-col', 'รายได้จาก Single Visit').should('contain.text', '฿800')
    cy.get('.report-figma-table tbody tr').should('have.length.at.least', 2)
  })

  it('filters the revenue by sale type and by branch', function () {
    cy.registerPatient().then((patient) => {
      sellVisit(this.fixtures, patient)
      sellCourse(this.fixtures, patient)
    })
    cy.visitAs('manager', '/staff/reports')
    cy.contains('label', 'ประเภทการขาย').parent().find('select').select('Course Sales')
    cy.contains('.kpi-col', 'รายได้จาก Course Sales').should('be.visible')
    cy.contains('.kpi-col', 'รายได้จาก Single Visit').should('not.exist')
    cy.contains('label', 'ประเภทการขาย').parent().find('select').select('ทั้งหมด')
    cy.contains('label', 'สาขา').parent().find('select').select('02')
    cy.contains('td', 'ไม่พบข้อมูลตามตัวกรองที่เลือก').should('be.visible')
  })

  it('shows the balance of a bought course', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.visitAs('manager', '/staff/reports')
    cy.get('#report-select').select('balance')
    cy.contains('h2', 'ยอดคงเหลือคอร์ส').should('be.visible')
    cy.get('.report-figma-table tbody tr').first().should('contain.text', 'สมชาย ใจดี').and('contain.text', '6')
  })

  it('searches the balance report by patient', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.visitAs('manager', '/staff/reports')
    cy.get('#report-select').select('balance')
    cy.get('input[placeholder="ค้นหาชื่อผู้ป่วย"]').type('ไม่มีคนนี้')
    cy.contains('td', 'ไม่พบข้อมูลตามตัวกรองที่เลือก').should('be.visible')
  })

  it('shows a course transfer in its history', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' }).as('receiver')
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01' })
        .then(({ body: purchase }) => cy.get('@receiver').then((receiver) => cy.api('POST', '/api/course-transfers', {
          sourcePurchaseId: purchase.id, destinationPatientId: receiver.id, quantity: 1, reason: 'ทดสอบ',
        }))).its('status').should('equal', 201)
    })
    cy.visitAs('manager', '/staff/reports')
    cy.get('#report-select').select('transfer')
    cy.get('.report-figma-table tbody tr').first().should('contain.text', 'สมชาย ใจดี').and('contain.text', 'อรุณ แสงทอง')
  })

  it('loads the commission report without an error', () => {
    cy.visitAs('manager', '/staff/reports')
    cy.get('#report-select').select('commission')
    cy.contains('h2', 'รายงานค่าคอมมิชชั่น').should('be.visible')
    cy.get('.report-error').should('not.exist')
  })
})
