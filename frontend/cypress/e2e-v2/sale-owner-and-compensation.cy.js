/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' })
  .format(new Date()).slice(0, 7)

function choosePatient() {
  cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type('สมชาย')
  cy.contains('button.service-patient-result', 'สมชาย ใจดี').click()
}

describe('sale screen: the Owner of a course and the estimated commission', () => {
  it('estimates the commission from the seller\'s tier, not a fixed 10%', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/services')
    choosePatient()
    cy.contains('button', 'เริ่มขายบริการ').click()
    cy.contains('.sale-tabs button', 'Course').click()
    cy.get('.commission').should('contain.text', 'ประมาณการ').and('contain.text', '5%').and('not.contain.text', '10%')
    cy.contains('.sale-tabs button', 'Single Visit').click()
    cy.get('.commission').should('not.exist')
  })

  it('lets the seller/Owner be chosen apart from the treating PT, and credits the course to the chosen Owner', function () {
    cy.registerPatient()
    cy.api('GET', '/api/staff').then(({ body }) => {
      const therapist = body.find((row) => row.id === this.fixtures.therapistId)
      cy.visitAs('manager', '/staff/services')
      choosePatient()
      cy.contains('button', 'เริ่มขายบริการ').click()
      cy.contains('.sale-tabs button', 'Course').click()
      // a course has no treating PT: only the seller/Owner is asked for
      cy.get('button[aria-label="นักกายภาพผู้ให้บริการ"]').should('not.exist')
      cy.chooseDropdown('ผู้ขายหรือเจ้าของยอด', `กภ. ${therapist.fullName}`)
      cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
      cy.contains('button', 'ยืนยันรับชำระเงิน').click()
      cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
      cy.noAlert()
      cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
      cy.api('GET', '/api/patients').then(({ body: patients }) => cy.api('GET', `/api/patients/${patients.items[0].id}/courses`)).then(({ body: courses }) => {
        expect(courses).to.have.length(1)
      })
      cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body: closings }) => {
        expect(closings.map((row) => row.employeeId)).to.include(this.fixtures.therapistId)
      })
    })
  })
})

describe('my compensation page (therapist)', () => {
  function courseWithOneSession(fx) {
    return cy.registerPatient().then((patient) => cy.api('POST', '/api/course-purchases', {
      patientId: patient.id, courseId: fx.courseId, branchCode: '01', caseOwnerStaffId: fx.therapistId,
    }).then(({ body: course }) => cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id }).its('status').should('equal', 200)))
  }

  it('says there is no payment yet', () => {
    cy.visitAs('therapist', '/staff/compensation')
    cy.contains('ยังไม่มีงวดค่าตอบแทน').should('be.visible')
  })

  it('shows the commission as waiting until the sale month is closed, then the amount', function () {
    courseWithOneSession(this.fixtures)
    cy.api('POST', `/api/commission/payments/prepare?month=${month}`).its('status').should('equal', 200)
    cy.visitAs('therapist', '/staff/compensation')
    cy.get('.compensation-summary').should('contain.text', 'รอคำนวณ').and('contain.text', '1 ครั้งที่เดือนขายคอร์สยังไม่ปิดยอด')
    cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${month}`).its('status').should('equal', 200)
    cy.reload()
    cy.get('.compensation-summary').should('contain.text', '฿75.00').and('not.contain.text', 'รอคำนวณ')
  })

  it('lists only the months that were paid in the history', function () {
    courseWithOneSession(this.fixtures)
    cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/payments/prepare?month=${month}`).then(({ body }) => {
      const mine = body.find((row) => row.staffId === this.fixtures.therapistId)
      cy.visitAs('therapist', '/staff/compensation')
      cy.contains('button', 'ประวัติการจ่าย').click()
      cy.contains('ยังไม่มีงวดที่จ่ายแล้ว').should('be.visible')
      cy.api('POST', `/api/commission/payments/${mine.id}/close`).its('status').should('equal', 200)
      cy.api('POST', `/api/commission/payments/${mine.id}/pay`).its('status').should('equal', 200)
      cy.reload()
      cy.contains('button', 'ประวัติการจ่าย').click()
      cy.get('.payment-history-table article').should('have.length', 1).and('contain.text', '฿75.00')
    })
  })
})
