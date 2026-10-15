/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function sell(fx, patient, overrides = {}) {
  return cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01',
    items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
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

describe('selling a service or a course', () => {
  it('finds a patient by name and shows the two service menus', () => {
    cy.registerPatient()
    cy.visitAs('therapist', '/staff/services')
    cy.contains('h1', 'บริการและคอร์ส').should('be.visible')
    choosePatient()
    cy.contains('.patient-summary', 'สมชาย ใจดี').should('be.visible')
    cy.contains('h2', 'ขายบริการและคอร์ส').should('be.visible')
    cy.contains('h2', 'ใช้สิทธิ์และจัดการคอร์ส').should('be.visible')
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
    cy.contains('button', 'เริ่มขายบริการ').click()
    cy.contains('.sale-tabs button', 'Single Visit').click()
    cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
    cy.contains('button', 'ยืนยันรับชำระเงิน').click()
    cy.contains('h2', 'ยืนยันรับชำระเงิน').should('be.visible')
    cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
    cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
    cy.contains('เลขที่เอกสาร: RE').should('be.visible')
    cy.api('GET', '/api/payments').then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      expect(body.items[0].status).to.equal('PAID')
      expect(body.items[0].patient.fullName).to.equal('สมชาย ใจดี')
    })
  })

  it('sells a course and goes on to use it', function () {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/services')
    choosePatient()
    cy.contains('button', 'เริ่มขายบริการ').click()
    cy.contains('.sale-tabs button', 'Course').click()
    cy.contains('label', 'รายการคอร์ส').find('select').select(String(this.fixtures.courseId))
    cy.contains('.sale-summary-card b', '฿9,000').should('be.visible')
    cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
    cy.contains('button', 'ยืนยันรับชำระเงิน').click()
    cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
    cy.contains('h1', 'ใบเสร็จรับเงิน').should('be.visible')
    cy.contains('a', 'ดูคอร์สผู้ป่วย').click()
    cy.location('pathname').should('eq', '/staff/courses')
    cy.api('GET', '/api/payments').its('body.totalItems').should('equal', 1)
  })

  it('goes back from the review without recording anything', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/services')
    choosePatient()
    cy.contains('button', 'เริ่มขายบริการ').click()
    cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
    cy.contains('button.sale-edit', 'แก้ไข').click()
    cy.contains('h1', 'ขายบริการและคอร์ส').should('be.visible')
    cy.api('GET', '/api/payments').its('body.totalItems').should('equal', 0)
  })
})

describe('transactions', () => {
  it('lists sales with receipt number, patient, branch and status', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('therapist', '/staff/transactions')
    cy.contains('h1', 'ยกเลิกและ Void รายการ').should('be.visible')
    cy.get('tbody tr').should('have.length', 1)
    cy.contains('tr', 'สมชาย ใจดี').should('contain.text', 'RE').and('contain.text', 'ชำระแล้ว').and('contain.text', '฿800')
  })

  it('searches and filters the sales', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', identityNumber: '1100000000102', phone: '0822222222' })
      .then((patient) => sell(this.fixtures, patient).then((receipt) => cy.api('POST', `/api/payments/${receipt.id}/reverse`, { reason: 'ทดสอบ' })))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('tbody tr').should('have.length', 2)
    cy.get('input[aria-label="ค้นหารายการ"]').type('อรุณ')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'ยกเลิก')
    cy.get('input[aria-label="ค้นหารายการ"]').clear()
    cy.chooseDropdown('สถานะ', 'ปกติ')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี')
  })

  it('shows the detail of a sale', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('button', /^ตรวจสอบ$/).click()
    cy.contains('h1', 'ตรวจสอบรายการที่ต้องการยกเลิก').should('be.visible')
    cy.get('.transaction-service-detail').should('contain.text', 'Cash')
    cy.contains('dt', 'ยอดสุทธิ').parent().should('contain.text', '฿800')
  })

  it('cancels a sale with a reason and keeps the evidence', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('button', /^ตรวจสอบ$/).click()
    cy.contains('button', 'ดำเนินการยกเลิก').click()
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').should('be.disabled')
    cy.contains('label.transaction-reason-option', 'บันทึกรายการซ้ำ').find('input').check()
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').click()
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.contains('h2', 'ยืนยันการยกเลิกรายการ').should('be.visible')
    cy.get('[role="alertdialog"]').contains('button', 'ยืนยันการยกเลิก').click()
    cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
    cy.get('.transaction-success-result').should('contain.text', 'บันทึกรายการซ้ำ')
    cy.api('GET', '/api/payments').its('body.items.0.status').should('equal', 'VOIDED')
    cy.contains('button', 'ดูประวัติการยกเลิก').click()
    cy.contains('tbody tr', 'สมชาย ใจดี').should('contain.text', 'บันทึกรายการซ้ำ')
  })

  it('needs a written reason when "other" is chosen', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('button', /^ตรวจสอบ$/).click()
    cy.contains('button', 'ดำเนินการยกเลิก').click()
    cy.contains('label.transaction-reason-option', 'อื่น ๆ').find('input').check()
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').should('be.disabled')
    cy.get('#transaction-other-reason').type('ลูกค้าขอคืนรายการชำระเงิน')
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').should('be.enabled')
  })

  it('changes nothing when the cancellation is not confirmed', function () {
    cy.registerPatient().then((patient) => sell(this.fixtures, patient))
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('button', /^ตรวจสอบ$/).click()
    cy.contains('button', 'ดำเนินการยกเลิก').click()
    cy.contains('label.transaction-reason-option', 'ลงรายการหรือเลือกบริการผิด').find('input').check()
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').click()
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.get('[role="alertdialog"]').contains('button', 'แก้ไข').click()
    cy.api('GET', '/api/payments').its('body.items.0.status').should('equal', 'PAID')
  })

  it('lists course usage and can cancel one', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01' })
        .then(({ body: purchase }) => cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }))
    })
    cy.visitAs('manager', '/staff/transactions')
    cy.chooseDropdown('ประเภท', 'ใช้สิทธิ์คอร์ส')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'สมชาย ใจดี').and('contain.text', 'ใช้สิทธิ์คอร์ส 1 ครั้ง')
    cy.contains('button', /^ตรวจสอบ$/).click()
    cy.contains('button', 'ดำเนินการยกเลิก').click()
    cy.contains('label.transaction-reason-option', 'บันทึกรายการซ้ำ').find('input').check()
    cy.contains('button', 'ตรวจสอบก่อนยืนยัน').click()
    cy.contains('button', 'ยืนยันการยกเลิก').click()
    cy.get('[role="alertdialog"]').contains('button', 'ยืนยันการยกเลิก').click()
    cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
    cy.api('GET', '/api/course-usages').its('body.0.voidReason').should('equal', 'บันทึกรายการซ้ำ')
  })

  it('says there is no cancellation history when nothing was cancelled', () => {
    cy.visitAs('manager', '/staff/transactions')
    cy.contains('.transaction-tabs button', 'ประวัติการยกเลิก').click()
    cy.contains('ยังไม่มีประวัติการยกเลิก').should('be.visible')
  })
})
