/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)

// A course owned by the therapist with one session used by the Owner: 9,000 THB at 5% over 6 sessions = 75.00 for the session.
function courseWithOneSession(fx) {
  return cy.registerPatient().then((patient) => cy.api('POST', '/api/course-purchases', {
    patientId: patient.id, courseId: fx.courseId, branchCode: '01', caseOwnerStaffId: fx.therapistId,
  }).then(({ status, body: course }) => {
    expect(status, JSON.stringify(course)).to.equal(201)
    return cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id }).then((used) => {
      expect(used.status).to.equal(200)
      return course
    })
  }))
}
const closeMonth = () => cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)

describe('monthly payments page', () => {
  it('is closed to a therapist', () => {
    for (const path of ['/staff/commission/payments', '/staff/commission/adjustments', '/staff/refunds']) {
      cy.visitAs('therapist', path)
      cy.location('pathname').should('eq', '/staff/patients')
    }
  })

  it('shows the commission as waiting (not 0) until the sale month is closed', function () {
    courseWithOneSession(this.fixtures)
    cy.visitAs('manager', '/staff/commission/payments')
    cy.contains('button', 'เตรียม/คำนวณงวดจ่าย').click()
    cy.contains('tbody tr', 'รอคำนวณ').should('contain.text', '1 ครั้งที่เดือนขายยังไม่ปิดยอด')
    cy.contains('tbody tr', 'Therapist').contains('button', 'ปิดงวด').should('be.disabled')
  })

  it('prepares, closes and pays a payment; the month it depends on is finalized', function () {
    courseWithOneSession(this.fixtures)
    closeMonth()
    cy.visitAs('manager', '/staff/commission/payments')
    cy.contains('button', 'เตรียม/คำนวณงวดจ่าย').click()
    cy.contains('tbody tr', 'Therapist').should('contain.text', '฿75.00').and('contain.text', 'เปิดอยู่')
    cy.contains('tbody tr', 'Therapist').contains('button', 'ปิดงวด').click()
    cy.contains('tbody tr', 'Therapist').should('contain.text', 'ปิดงวด รอจ่าย')
    cy.contains('tbody tr', 'Therapist').contains('button', 'เปิดงวดอีกครั้ง').click()
    cy.contains('tbody tr', 'Therapist').should('contain.text', 'เปิดอยู่')
    cy.contains('tbody tr', 'Therapist').contains('button', 'ปิดงวด').click()
    cy.contains('tbody tr', 'Therapist').contains('button', 'ยืนยันจ่าย').click()
    cy.get('.confirm-modal').should('contain.text', 'Finalize').contains('button', 'ยืนยันจ่าย').click()
    cy.noAlert()
    cy.contains('tbody tr', 'Therapist').should('contain.text', 'จ่ายแล้ว')
    cy.contains('tbody tr', 'Therapist').find('button').should('not.exist')
    cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => {
      expect(body.find((row) => row.employeeId === this.fixtures.therapistId).status).to.equal('FINALIZED')
    })
    // the closing page now shows it as closed and paid, without a Reopen button
    cy.visitAs('manager', '/staff/commission/closing')
    cy.get('tbody tr').should('contain.text', 'ปิดรอบแล้ว (จ่ายแล้ว)').find('.reopen-button').should('not.exist')
  })
})

describe('reopening a closed month', () => {
  it('asks for a reason and puts the month back to waiting', function () {
    courseWithOneSession(this.fixtures)
    closeMonth()
    cy.visitAs('manager', '/staff/commission/closing')
    cy.get('tbody tr').should('contain.text', 'ปิดรอบแล้ว')
    cy.get('.reopen-button').click()
    cy.get('.confirm-modal').contains('button', 'ยืนยัน Reopen').click()
    cy.get('.confirm-modal [role="alert"]').should('contain.text', 'เหตุผล')
    cy.get('.confirm-modal input').type('ขายคอร์สเพิ่มก่อนจ่าย')
    cy.get('.confirm-modal').contains('button', 'ยืนยัน Reopen').click()
    cy.noAlert()
    cy.get('tbody tr').should('contain.text', 'รอปิดรอบ')
  })
})

describe('pay adjustments page', () => {
  function paidMonth(fx) {
    return courseWithOneSession(fx).then((course) => {
      closeMonth()
      return cy.api('POST', `/api/commission/payments/prepare?month=${month}`).then(({ body }) => {
        const payment = body.find((row) => row.staffId === fx.therapistId)
        cy.api('POST', `/api/commission/payments/${payment.id}/close`).its('status').should('equal', 200)
        cy.api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 200)
        return cy.api('GET', `/__cypress/commission/courses/${course.id}/allocations`).its('body.0.allocation_id')
      })
    })
  }

  it('creates an adjustment, approves it and reserves it on the next payment', function () {
    paidMonth(this.fixtures).then((allocationId) => {
      cy.api('GET', '/api/staff').then(({ body }) => {
        const therapist = body.find((row) => row.id === this.fixtures.therapistId)
        cy.visitAs('manager', '/staff/commission/adjustments')
        cy.contains('button', '+ สร้างใบปรับยอด').click()
        // a source of the right staff member and a reason are required
        cy.get('.rule-drawer').contains('button', 'บันทึกใบปรับยอด').click()
        cy.get('.drawer-error').should('contain.text', 'กรุณาเลือกพนักงาน')
        cy.get('.rule-drawer').contains('label', 'พนักงาน').find('select').select(therapist.fullName)
        cy.get('.rule-drawer').contains('label', 'ยอด (บาท)').find('input').clear().type('140')
        cy.get('.rule-drawer').contains('label', 'เหตุผล').find('input').type('บันทึกผู้รักษาผิดคน')
        cy.get('.rule-drawer').contains('label', 'เลขที่ต้นทาง').find('input').clear().type(String(allocationId))
        cy.get('.rule-drawer').contains('button', 'บันทึกใบปรับยอด').click()
        cy.noAlert()
        cy.get('tbody tr').should('have.length', 1).and('contain.text', '−฿140.00').and('contain.text', 'รออนุมัติ')
        cy.get('.detail').should('contain.text', 'allocation #')
        cy.get('.detail').contains('button', 'อนุมัติใบปรับยอด').click()
        cy.get('tbody tr').should('contain.text', 'อนุมัติแล้ว')
        cy.get('.detail .apply-form').contains('button', 'เพิ่มในงวดจ่าย').click()
        cy.noAlert()
        cy.get('.detail table tbody tr').should('contain.text', 'Draft').and('contain.text', 'ยังไม่อนุมัติ')
        // a deduction needs the consent reference before it is approved
        cy.get('.detail table').contains('button', 'อนุมัติ').click()
        cy.get('[role="alert"], .page-error').should('contain.text', 'เลขอ้างอิง')
        cy.get('.apply-form').contains('label', 'เลขอ้างอิงความยินยอม').find('input').type('ใบยินยอม 2569/10')
        cy.get('.detail table').contains('button', 'อนุมัติ').click()
        cy.noAlert()
        cy.get('.detail table tbody tr').should('contain.text', 'อนุมัติแล้ว').and('contain.text', 'ใบยินยอม 2569/10')
      })
    })
    cy.api('GET', '/api/commission/adjustments').its('body.0.applications.0.status').should('equal', 'Draft')
  })

  it('refuses an adjustment without a source and cancels one that was not applied', function () {
    paidMonth(this.fixtures).then((allocationId) => {
      cy.api('POST', '/api/commission/adjustments', {
        staffId: this.fixtures.therapistId, amount: 10, reason: 'ทดสอบยกเลิก', allocationId,
      }).then(({ body }) => {
        cy.visitAs('manager', '/staff/commission/adjustments')
        cy.contains('tbody tr', 'ทดสอบยกเลิก').contains('button', 'รายละเอียด').click()
        cy.get('.detail').contains('button', 'ยกเลิกใบปรับยอด').click()
        cy.get('tbody tr').should('contain.text', 'ยกเลิก')
        expect(body.status).to.equal('Pending')
      })
    })
  })
})
