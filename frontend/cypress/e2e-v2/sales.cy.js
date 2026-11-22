/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function sell(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id,
    branchCode: '01',
    paymentLabelCode: 'FULL',
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
    ...overrides,
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

function choosePatient(term = 'สมชาย') {
  cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type(term)
  cy.contains('button.service-patient-result', 'สมชาย ใจดี').click()
}

function chooseCoursePurchase(fx, patient) {
  return cy.api('POST', '/api/course-purchases', {
    patientId: patient.id,
    courseId: fx.courseId,
    branchCode: '01',
    caseOwnerStaffId: fx.therapistId,
    paymentLabelCode: 'FULL',
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return cy.openBookingHours('01').then(() =>
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: fx.therapistId,
        serviceId: fx.courseId,
        startAt: `${fx.tomorrow}T09:00:00+07:00`,
        endAt: `${fx.tomorrow}T10:00:00+07:00`,
      }).then(({ status: apptStatus, body: appointment }) => {
        expect(apptStatus, JSON.stringify(appointment)).to.equal(201)
        return cy.api('POST', '/api/course-usages', {
          coursePurchaseId: body.id,
          appointmentId: appointment.id,
        }).then(({ status: usageStatus }) => {
          expect(usageStatus).to.equal(200)
          return body
        })
      }),
    )
  })
}

describe('selling a service or a course', () => {
  it('finds a patient by name and shows the service action menu', () => {
    cy.registerPatient()
    cy.visitAs('therapist', '/staff/services')
    cy.contains('h1', 'ชำระเงิน').should('be.visible')
    choosePatient()
    cy.contains('.service-menu-patient', 'สมชาย ใจดี').should('be.visible')
    cy.get('button.service-menu-action--sale').should('be.visible')
    cy.get('button.service-menu-action--entitlement').should('be.visible')
  })

  it('says so when no patient matches', () => {
    cy.visitAs('therapist', '/staff/services')
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type('ไม่มีคนนี้')
    cy.contains('ไม่พบผู้ป่วยที่ตรงกับคำค้น').should('be.visible')
  })

  it('sells a single visit and records a receipt', () => {
    cy.registerPatient()
    cy.visitAs('therapist', '/staff/services')
    choosePatient()
    cy.get('button.service-menu-action--sale').click()
    cy.contains('h1', 'รายละเอียดการชำระเงิน').should('be.visible')
    cy.chooseDropdown('ประเภทการชำระ', 'รายครั้ง')
    cy.get('input.filter-dropdown__button[aria-label="ค้นหารายการ"]').click()
    cy.get('[role="listbox"][aria-label="ค้นหารายการ"] [role="option"]').contains('กายภาพบำบัดทั่วไป 30 นาที').click()
    cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
    cy.get('.payment-options').contains('button', 'พร้อมเพย์').click()
    cy.contains('button.primary', 'ชำระเงิน').click()
    cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
    cy.contains('เลขที่เอกสาร: RE').should('be.visible')
    cy.api('GET', '/api/payments').then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      expect(body.items[0].status).to.equal('PAID')
      expect(body.items[0].patient.fullName).to.equal('สมชาย ใจดี')
    })
  })

  it('sells a course and goes on to use it', function () {
    cy.registerPatient().then((patient) => {
      cy.visitAs('manager', '/staff/services')
      choosePatient()
      cy.get('button.service-menu-action--sale').click()
      cy.chooseDropdown('ประเภทการชำระ', 'รายคอร์ส')
      cy.get('input.filter-dropdown__button[aria-label="ค้นหารายการ"]').click()
      cy.get('[role="listbox"][aria-label="ค้นหารายการ"] [role="option"]').contains('คอร์สทดสอบ API').click()
      cy.contains('.summary p.net strong', '฿9,000').should('be.visible')
      cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
      cy.get('.payment-options').contains('button', 'โอนเงินผ่านธนาคาร').click()
      cy.contains('button.primary', 'ชำระเงิน').click()
      cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
      cy.visitAs('manager', `/staff/courses?patientId=${patient.id}`)
      cy.location('pathname').should('eq', '/staff/courses')
      cy.api('GET', '/api/payments').its('body.totalItems').should('equal', 1)
    })
  })

  it('goes back from checkout without recording anything', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/services')
    choosePatient()
    cy.get('button.service-menu-action--sale').click()
    cy.contains('button.app-back-link', 'กลับ').click()
    cy.location('pathname').should('eq', '/staff/services')
    cy.api('GET', '/api/payments').its('body.totalItems').should('equal', 0)
  })
})

describe('transactions', () => {
  it('lists sales with receipt number, patient, branch and status', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/transactions')
    cy.contains('h1', 'รายการธุรกรรม').should('be.visible')
    cy.get('.transaction-table tbody tr').should('have.length', 1)
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', 'RE').and('contain.text', 'ปกติ').and('contain.text', '฿800')
  })

  it('searches and filters the sales', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
      .then((patient) => sell(this.fixtures, patient).then((receipt) => cy.api('POST', `/api/payments/${receipt.id}/reverse`, { reason: 'ทดสอบ' })))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-table tbody tr').should('have.length', 2)
    cy.get('input[aria-label="ค้นหารายการ"]').type('อรุณ')
    cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain.text', 'ยกเลิกแล้ว')
    cy.get('input[aria-label="ค้นหารายการ"]').clear()
    cy.chooseDropdown('กรองรายการตามสถานะ', 'ปกติ')
    cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })

  it('shows the detail of a sale', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-table').contains('button', /^ยกเลิก$/).click()
    cy.contains('h1', 'รายละเอียดรายการ').should('be.visible')
    cy.contains('dt', 'ยอดสุทธิ').parent().should('contain.text', '฿800')
  })

  it('cancels a sale with a reason and keeps the evidence', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-table').contains('button', /^ยกเลิก$/).click()
    cy.contains('button', 'ยืนยันการยกเลิก').should('be.disabled')
    cy.contains('label.transaction-reason-option', 'บันทึกรายการซ้ำ').find('input').check()
    cy.get('#transaction-other-reason').type('บันทึกรายการซ้ำในการขาย')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.get('[role="alertdialog"]').contains('button', /^ยืนยัน$/).click()
    cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
    cy.get('.transaction-success-result').should('contain.text', 'บันทึกรายการซ้ำ')
    cy.api('GET', '/api/payments').its('body.items.0.status').should('equal', 'VOIDED')
    cy.contains('button', 'ดูประวัติการยกเลิก').click()
    cy.contains('tbody tr', 'สมชาย ใจดี').should('contain.text', 'บันทึกรายการซ้ำ')
  })

  it('needs a written reason when "other" is chosen', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-table').contains('button', /^ยกเลิก$/).click()
    cy.contains('label.transaction-reason-option', 'อื่น ๆ').find('input').check()
    cy.contains('button', 'ยืนยันการยกเลิก').should('be.disabled')
    cy.get('#transaction-other-reason').type('ลูกค้าขอคืนรายการชำระเงิน')
    cy.contains('button', 'ยืนยันการยกเลิก').should('be.enabled')
  })

  it('changes nothing when the cancellation is not confirmed', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-table').contains('button', /^ยกเลิก$/).click()
    cy.contains('label.transaction-reason-option', 'ลงรายการหรือเลือกบริการผิด').find('input').check()
    cy.get('#transaction-other-reason').type('รายละเอียดประกอบการตรวจสอบ')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.get('[role="alertdialog"]').contains('button', 'ยกเลิก').click()
    cy.api('GET', '/api/payments').its('body.items.0.status').should('equal', 'PAID')
  })

  it('lists course usage and can cancel one', function () {
    cy.registerPatient().then((patient) => chooseCoursePurchase(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('.transaction-tabs button', 'รายการใช้บริการ').click()
    cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
    cy.get('.transaction-table').contains('button', /^ยกเลิก$/).click()
    cy.contains('label.transaction-reason-option', 'บันทึกรายการซ้ำ').find('input').check()
    cy.get('#transaction-other-reason').type('บันทึกใช้สิทธิ์ซ้ำ')
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.get('[role="alertdialog"]').contains('button', /^ยืนยัน$/).click()
    cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
    cy.api('GET', '/api/course-usages').its('body.0.voidReason').should('contain', 'บันทึกใช้สิทธิ์ซ้ำ')
  })

  it('says there is no cancellation history when nothing was cancelled', () => {
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('.transaction-tabs button', 'ประวัติการยกเลิก').click()
    cy.contains('ยังไม่มีประวัติการยกเลิก').should('be.visible')
  })
})
