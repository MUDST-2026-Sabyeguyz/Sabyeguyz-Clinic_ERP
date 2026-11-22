/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function buyCourse(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id,
    courseId: fx.courseId,
    branchCode: '01',
    caseOwnerStaffId: fx.managerStaffId,
    paymentLabelCode: 'FULL',
    ...overrides,
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

function bookVisit(fx, patient, branchCode = '01') {
  return cy.openBookingHours(branchCode).then(() =>
    cy.api('POST', '/api/appointments', {
      branchCode,
      patientId: patient.id,
      therapistId: fx.therapistId,
      serviceId: fx.courseId,
      startAt: `${fx.tomorrow}T09:00:00+07:00`,
      endAt: `${fx.tomorrow}T10:00:00+07:00`,
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      return body
    }),
  )
}

const useOnce = (fx, patient, purchase) =>
  bookVisit(fx, patient).then((appointment) =>
    cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id, appointmentId: appointment.id })
      .its('status').should('equal', 200),
  )

describe('patient courses', () => {
  it('shows a patient with no course', () => {
    cy.registerPatient().then((patient) => cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`))
    cy.contains('h1', 'คอร์สของผู้ป่วย').should('be.visible')
    cy.contains('h2', 'ยังไม่มีคอร์สที่ใช้งานได้').should('be.visible')
  })

  it('shows the total visits and the expiry of a bought course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('.course-use-patient', 'สมชาย ใจดี').should('be.visible')
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ5 ครั้ง')
      .and('contain.text', 'ทั้งหมด5 ครั้ง').and('contain.text', 'วันหมดอายุคอร์ส')
    cy.api('GET', '/api/patients').its('body.items.0.id').then((patientId) => {
      cy.api('GET', `/api/patients/${patientId}/courses`).its('body.0.purchased').should('equal', 5)
    })
  })

  it('uses one visit of the course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient).then((course) =>
        bookVisit(this.fixtures, patient).then((appointment) =>
          cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}&courseId=${course.id}&appointmentId=${appointment.id}&action=use`),
        ),
      )
    })
    cy.get('.course-review-summary').contains('strong', '4 ครั้ง').should('be.visible')
    cy.contains('h1', 'ตรวจสอบการใช้สิทธิ์').should('be.visible')
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.get('.course-usage-confirm').should('be.visible').contains('button', /^ยืนยัน$/).click()
    cy.contains('h1', 'ใช้สิทธิ์สำเร็จ').should('be.visible')
    cy.get('.usage-success-details').should('contain.text', '4 ครั้ง')
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 1)
  })

  it('lets another PT treat the session: a substitute is recorded and the Owner earns nothing for it', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient, { caseOwnerStaffId: this.fixtures.managerStaffId }).then((course) =>
        bookVisit(this.fixtures, patient).then((appointment) =>
          cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}&courseId=${course.id}&appointmentId=${appointment.id}&action=use`),
        ),
      )
    })
    // the PT must work at the branch of the visit (branch 01)
    cy.get('button.review-therapist-edit').click()
    cy.api('GET', '/api/staff').then(({ body }) => {
      const therapist = body.find((row) => row.id === this.fixtures.therapistId)
      cy.get('select.review-therapist-select').select(therapist.fullName)
    })
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.get('.course-usage-confirm').contains('button', /^ยืนยัน$/).click()
    cy.noAlert()
    cy.contains('h1', 'ใช้สิทธิ์สำเร็จ').should('be.visible')
    cy.api('GET', '/api/commission/treatment-report?from=2020-01-01&to=2099-12-31').then(({ body }) => {
      expect(body).to.have.length(1)
      expect(body[0].treatingStaffId).to.not.equal(body[0].ownerStaffId)
      expect(Number(body[0].treatmentFee)).to.be.greaterThan(0)
      expect(Number(body[0].ownerNet)).to.equal(0)
    })
  })

  it('does not use anything when the confirmation is dismissed', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient).then((course) =>
        bookVisit(this.fixtures, patient).then((appointment) =>
          cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}&courseId=${course.id}&appointmentId=${appointment.id}&action=use`),
        ),
      )
    })
    cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
    cy.get('.course-usage-confirm').contains('button', /^ยกเลิก$/).click()
    cy.api('GET', '/api/course-usages').its('body').should('have.length', 0)
  })

  it('shows the usage history of a course', function () {
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient).then((purchase) => useOnce(this.fixtures, patient, purchase))
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.get('.course-manager-card').should('have.length', 1)
    cy.contains('.course-manager-actions button', 'ดูประวัติการใช้').click()
    cy.contains('h1', 'ประวัติการใช้คอร์ส').should('be.visible')
    cy.get('.course-history-summary').should('contain.text', 'คงเหลือ 4 ครั้ง')
    cy.get('.course-history-entry').should('have.length', 1).and('contain.text', 'ใช้ 1 ครั้ง')
  })

  it('transfers visits to another patient', function () {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
    cy.registerPatient().then((patient) => {
      buyCourse(this.fixtures, patient)
      cy.visitAs('manager', `/staff/courses?patientId=${patient.id}`)
    })
    cy.contains('button', 'แชร์คอร์ส').click()
    cy.contains('h1', 'แชร์คอร์ส').should('be.visible')
    cy.get('.course-transfer-footer').contains('button', /^\s*ยืนยัน\s*$/).should('be.disabled')
    cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"]').type('อรุณ')
    cy.contains('.course-transfer-results button', 'อรุณ แสงทอง').click()
    cy.get('.course-transfer-footer').contains('button', /^\s*ยืนยัน\s*$/).click()
    cy.get('.course-transfer-confirm').contains('button', /^\s*ยืนยัน\s*$/).click()
    cy.contains('h2', 'แชร์คอร์สสำเร็จ').should('be.visible')
    cy.api('GET', '/api/course-transfers').its('body').should('have.length', 1)
  })

  it('buys a course from the sale screen and finds it here with the same balance', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id,
        branchCode: '01',
        salespersonStaffId: this.fixtures.managerStaffId,
        paymentLabelCode: 'FULL',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'PromptPay', amount: 9000 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
      cy.visitAs('therapist', `/staff/courses?patientId=${patient.id}`)
    })
    cy.get('.course-manager-card').should('have.length', 1).and('contain.text', 'คงเหลือ5 ครั้ง')
  })
})
