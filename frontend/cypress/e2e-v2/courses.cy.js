/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function buyCourse(fx, patient) {
  return cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: fx.courseId, branchCode: '01' })
    .then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    })
}

const useOnce = (purchase) => cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)

describe('patient courses', () => {
  it('shows a patient with no course', () => {
    cy.registerPatient().then((patient) => cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`))
    cy.contains('h1', 'จัดการคอร์สผู้ป่วย').should('be.visible')
    cy.contains('h2', 'ยังไม่มีคอร์สที่ใช้งานได้').should('be.visible')
  })

  it('shows the balance, the bonus visits and the expiry of a bought course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('.course-use-patient', 'สมชาย ใจดี').should('be.visible')
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ6 ครั้ง')
      .and('contain.text', 'ทั้งหมด6 ครั้ง').and('contain.text', 'Bonus 1 ครั้ง').and('contain.text', 'หมดอายุ')
  })

  it('uses one visit of the course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('.course-manager-actions button', 'ใช้สิทธิ์').click()
    cy.get('.course-review-summary').contains('strong', '5 ครั้ง').should('be.visible')
    cy.contains('h1', 'ตรวจสอบการใช้สิทธิ์').should('be.visible')
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.contains('h2', 'ยืนยันการใช้สิทธิ์?').should('be.visible')
    cy.get('.course-usage-confirm').contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.contains('h1', 'ใช้สิทธิ์สำเร็จ').should('be.visible')
    cy.get('.course-use-success-details').should('contain.text', '5 ครั้ง')
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 1)
  })

  it('does not use anything when the confirmation is dismissed', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('.course-manager-actions button', 'ใช้สิทธิ์').click()
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.get('.course-usage-confirm').contains('button', 'ยังไม่ยืนยัน').click()
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 0)
  })

  it('shows the usage history of a course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient).then(useOnce)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'ดูประวัติการใช้').click()
    cy.contains('h1', 'ประวัติการใช้คอร์ส').should('be.visible')
    cy.get('.course-history-entry').should('have.length', 1).and('contain.text', 'ใช้ 1 ครั้ง').and('contain.text', 'คงเหลือหลังใช้ 5 ครั้ง')
  })

  it('transfers visits to another patient', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('manager', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'แชร์คอร์ส').click()
    cy.contains('h1', 'แชร์คอร์ส').should('be.visible')
    cy.contains('button', 'ค้นหาผู้รับ').should('be.disabled')
    cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"]').type('อรุณ')
    cy.contains('button', 'ค้นหาผู้รับ').click()
    cy.contains('.course-transfer-results button', 'อรุณ แสงทอง').click()
    cy.get('input[placeholder="ระบุเหตุผลเพื่อบันทึกในประวัติการแชร์"]').type('ยกให้ญาติ')
    cy.contains('button', 'เลือกผู้รับนี้').click()
    cy.contains('button', 'ตรวจสอบข้อมูล').click()
    cy.contains('h1', 'ตรวจสอบการแชร์คอร์ส').should('be.visible')
    cy.contains('button', 'ยืนยันแชร์คอร์ส').click()
    cy.contains('h2', 'แชร์คอร์สสำเร็จ').should('be.visible')
    cy.api('GET', '/api/course-transfers').its('body').should('have.length', 1)
  })

  it('buys a course from the sale screen and finds it here with the same balance', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        salespersonStaffId: this.fixtures.managerStaffId,
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 9000 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ6 ครั้ง')
  })
})
