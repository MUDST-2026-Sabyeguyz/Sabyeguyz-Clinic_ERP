/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function sellCourse(fx, patient) {
  return cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: fx.courseId, branchCode: '01' })
    .then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    })
}

describe('commission pages', () => {
  it('are closed to a therapist', () => {
    for (const path of ['/staff/commission/transactions', '/staff/commission/tiers', '/staff/commission/closing']) {
      cy.visitAs('therapist', path)
      cy.location('pathname').should('eq', '/staff/patients')
    }
  })

  it('shows an empty sales list when nothing was sold', () => {
    cy.visitAs('manager', '/staff/commission/transactions')
    cy.contains('h1', 'Sales Transaction').should('be.visible')
    cy.contains('td', 'ไม่พบข้อมูลตามตัวกรอง').should('be.visible')
  })

  it('lists the seller of a course in the sales transactions', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.visitAs('manager', '/staff/commission/transactions')
    cy.get('.summary-cards').should('contain.text', '1 รายการ')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', '฿9,000').and('contain.text', '5%')
  })

  it('switches to the treatment transactions', function () {
    cy.registerPatient().then((patient) => {
      sellCourse(this.fixtures, patient)
        .then((purchase) => cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200))
    })
    cy.visitAs('manager', '/staff/commission/transactions')
    cy.contains('label', 'เลือกประเภท').find('select').select('Treatment')
    cy.contains('h1', 'Treatment Transaction').should('be.visible')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })

  it('goes to the monthly closing from the type picker', () => {
    cy.visitAs('manager', '/staff/commission/transactions')
    cy.contains('label', 'เลือกประเภท').find('select').select('Closing')
    cy.location('pathname').should('eq', '/staff/commission/closing')
    cy.contains('h1', 'ปิดรอบค่าคอมมิชชัน').should('be.visible')
  })
})

describe('monthly closing', () => {
  it('has nothing to close when no course was sold', () => {
    cy.visitAs('manager', '/staff/commission/closing')
    cy.contains('td', 'ไม่พบข้อมูลตามตัวกรอง').should('be.visible')
    cy.get('.checklist').contains('button', 'ยืนยันปิดรอบ').should('be.disabled')
  })

  it('closes the month: the seller goes from waiting to closed with the tier rate locked', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.visitAs('manager', '/staff/commission/closing')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'รอปิดรอบ').and('contain.text', '฿9,000').and('contain.text', '5%')
    cy.get('.checklist').contains('button', 'ยืนยันปิดรอบ').click()
    cy.contains('h2', 'ต้องการปิดรอบเดือนหรือไม่?').should('be.visible')
    cy.get('.confirm-modal').contains('button', 'ยืนยันปิดรอบ').click()
    cy.noAlert()
    cy.get('tbody tr').should('contain.text', 'ปิดรอบแล้ว')
    cy.contains('h1', 'ปิดรอบสำเร็จ').should('be.visible')
    cy.get('@fixtures').then(() => cy.api('GET', '/api/patients').its('body.items.0.id').then((id) => cy.api('GET', `/api/patients/${id}/courses`)))
      .its('body.0.commissionStatus').should('equal', 'LOCKED')
  })

  it('changes nothing when the confirmation is declined', function () {
    cy.registerPatient().then((patient) => sellCourse(this.fixtures, patient))
    cy.visitAs('manager', '/staff/commission/closing')
    cy.get('.checklist').contains('button', 'ยืนยันปิดรอบ').click()
    cy.get('.confirm-modal').contains('button', 'ยกเลิก').click()
    cy.get('tbody tr').should('contain.text', 'รอปิดรอบ')
  })
})
