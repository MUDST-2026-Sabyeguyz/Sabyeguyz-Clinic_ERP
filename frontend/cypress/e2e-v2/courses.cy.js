/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function buyCourse(fx, patient) {
  return cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: fx.courseId, branchCode: '01' })
    .then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(200)
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
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ 6 ครั้ง')
      .and('contain.text', 'จากทั้งหมด 6 ครั้ง').and('contain.text', 'Bonus 1 ครั้ง').and('contain.text', 'หมดอายุ')
  })

  it('uses one visit of the course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'ใช้สิทธิ์คอร์ส').click()
    cy.contains('h1', 'ใช้สิทธิ์คอร์ส').should('be.visible')
    cy.contains('strong', 'จำนวนคงเหลือ 5 ครั้ง').should('be.visible')
    cy.contains('button', 'ตรวจสอบและใช้สิทธิ์').click()
    cy.contains('h1', 'ยืนยันการใช้สิทธิ์').should('be.visible')
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.contains('h2', 'ยืนยันการใช้สิทธิ์ 1 ครั้ง ?').should('be.visible')
    cy.contains('button', 'ยืนยันใช้ 1 ครั้ง').click()
    cy.contains('h1', 'บันทึกการใช้สิทธิ์สำเร็จ').should('be.visible')
    cy.contains('เหลือ 5 จาก 6 ครั้ง').should('be.visible')
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 1)
  })

  it('does not use anything when the confirmation is dismissed', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'ใช้สิทธิ์คอร์ส').click()
    cy.contains('button', 'ตรวจสอบและใช้สิทธิ์').click()
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.contains('button', 'กลับไปตรวจสอบ').click()
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 0)
  })

  it('shows the usage history of a course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient).then(useOnce)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'ดูประวัติการใช้').click()
    cy.contains('h1', 'ประวัติการใช้คอร์ส').should('be.visible')
    cy.get('.course-history-entry').should('have.length', 1).and('contain.text', 'ใช้ 1 ครั้ง').and('contain.text', 'คงเหลือ 5 ครั้ง')
  })

  it('transfers visits to another patient', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('manager', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'โอนสิทธิ์คอร์ส').click()
    cy.contains('h1', 'โอนสิทธิ์คอร์ส').should('be.visible')
    cy.contains('button', 'ตรวจสอบการโอน').should('be.disabled')
    cy.get('input[placeholder="ค้นหาด้วยชื่อ, HN หรือเบอร์โทรศัพท์"]').type('อรุณ')
    cy.contains('.course-transfer-results button', 'อรุณ แสงทอง').click()
    cy.get('textarea[placeholder="ระบุเหตุผลการโอนสิทธิ์"]').type('ยกให้ญาติ')
    cy.contains('button', 'ตรวจสอบการโอน').click()
    cy.contains('h2', 'ตรวจสอบรายการโอน').should('be.visible')
    cy.contains('button', 'ยืนยันโอนสิทธิ์').click()
    cy.contains('h2', 'โอนสิทธิ์คอร์สสำเร็จ').should('be.visible')
    cy.api('GET', '/api/course-transfers').its('body').should('have.length', 1)
  })

  it('buys a course from the sale screen and finds it here with the same balance', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 9000 }],
      }).its('status').should('equal', 201)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ 6 ครั้ง')
  })
})
