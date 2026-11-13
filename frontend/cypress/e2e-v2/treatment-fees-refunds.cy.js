/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const nextMonthStart = () => {
  const [year, month] = today.split('-').map(Number)
  return new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 10)
}

describe('treatment fee rule (settings)', () => {
  it('shows the rule in force and saves a change as a new version for the next month', () => {
    cy.visitAs('manager', '/staff/settings?section=commission')
    cy.contains('.settings-panel-head h2', 'กฎค่ามือ').should('be.visible')
    cy.get('.fee-version').should('have.length', 1).and('contain.text', '45 บาท / ครั้ง').and('contain.text', 'ใช้อยู่')
    // the form opens with the current values: nothing starts from an empty form
    cy.get('.settings-panel-head').contains('button', 'แก้กฎค่ามือ').click()
    cy.get('.drawer input[type="number"]').should('have.value', '45').clear().type('50')
    cy.get('.drawer input[type="month"]').invoke('val').should('match', /^[0-9]{4}-[0-9]{2}$/)
    cy.get('.drawer .save').click()
    cy.noAlert()
    cy.get('.fee-version').should('have.length', 2).and('contain.text', 'รอเริ่มใช้').and('contain.text', '50 บาท / ครั้ง')
    cy.api('GET', '/api/commission/treatment-fees').then(({ body }) => {
      expect(body).to.have.length(2)
      const next = body.find((rule) => Number(rule.feeValue) === 50)
      expect(next.effectiveFrom).to.equal(nextMonthStart())
      expect(body.find((rule) => Number(rule.feeValue) === 45).effectiveTo, 'the rule in force ends the day before').to.be.a('string')
    })
  })

  it('edits the rule that waits to start instead of adding another one', () => {
    cy.api('POST', '/api/commission/treatment-fees', { feeType: 'Percentage', feeValue: 10, effectiveFrom: nextMonthStart() })
      .its('status').should('equal', 201)
    cy.visitAs('manager', '/staff/settings?section=commission')
    cy.get('.fee-version').should('have.length', 2)
    cy.get('.settings-panel-head').contains('button', 'แก้กฎค่ามือ').click()
    cy.get('.drawer h2').should('contain.text', 'แก้ไขเวอร์ชันที่รอเริ่มใช้')
    cy.get('.drawer input[type="number"]').should('have.value', '10').clear().type('12')
    cy.get('.drawer .save').click()
    cy.noAlert()
    cy.get('.fee-version').should('have.length', 2).and('contain.text', '12%')
    cy.api('GET', '/api/commission/treatment-fees').its('body').should('have.length', 2)
  })

  it('rejects 150% before saving', () => {
    cy.visitAs('manager', '/staff/settings?section=commission')
    cy.get('.settings-panel-head').contains('button', 'แก้กฎค่ามือ').click()
    cy.get('.drawer input[type="radio"][value="Percentage"]').check()
    cy.get('.drawer input[type="number"]').clear().type('150')
    cy.get('.drawer .save').click()
    cy.get('.validation-message').should('contain.text', '0 ถึง 100')
    cy.api('GET', '/api/commission/treatment-fees').its('body').should('have.length', 1)
  })

  it('lists the versions of the rule on the history page, read only', () => {
    cy.visitAs('manager', '/staff/settings/fee-history')
    cy.contains('h1', 'ประวัติเวอร์ชันกฎค่ามือ').should('be.visible')
    cy.get('table.versions tbody tr').should('have.length', 1).and('contain.text', 'V1').and('contain.text', 'ใช้อยู่')
    cy.get('.detail').should('contain.text', 'อ่านอย่างเดียว').and('contain.text', '45 บาท / ครั้ง')
    cy.get('.detail').find('input, select, textarea').should('not.exist')
  })
})

describe('treatment fee backfill page', () => {
  it('backfills the single-visit fee that was missing when no rule applied', function () {
    cy.api('GET', '/api/commission/treatment-fees').then(({ body }) =>
      cy.api('PATCH', `/api/commission/treatment-fees/${body[0].id}/disable`, {}).its('status').should('equal', 200))
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId,
        treatingStaffId: this.fixtures.therapistId,
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
      }).its('status').should('equal', 201)
    })
    cy.api('GET', '/api/commission/treatment-fees').then(({ body }) =>
      cy.api('PATCH', `/api/commission/treatment-fees/${body[0].id}/enable`, {}).its('status').should('equal', 200))
    cy.visitAs('manager', '/staff/commission/compensation-settings')
    cy.contains('button', 'บันทึกย้อนหลัง').click()
    cy.get('.success').should('contain.text', 'เพิ่ม 1 รายการ')
  })
})

describe('refund by course page', () => {
  // a 9,000 THB course (5 sessions + 1 bonus) paid in cash, with one session already used
  function paidCourse(fx) {
    return cy.registerPatient().then((patient) => cy.api('POST', '/api/payments', {
      patientId: patient.id, branchCode: '01', salespersonStaffId: fx.therapistId,
      items: [{ type: 'COURSE', referenceId: fx.courseId, quantity: 1 }],
      payments: [{ paymentMethodCode: 'Cash', amount: 9000 }],
    }).then(({ status, body: sale }) => {
      expect(status, JSON.stringify(sale)).to.equal(201)
      return cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: courses[0].id }).its('status').should('equal', 200)
        return cy.wrap({ sale, patient, courseId: courses[0].id })
      })
    }))
  }

  it('requests a refund for the unused sessions of a course, then completes it', function () {
    paidCourse(this.fixtures).then(({ sale, courseId }) => {
      cy.visitAs('manager', `/staff/refunds?receipt=${sale.id}`)
      cy.get('[aria-label="คำขอคืนเงิน"]').should('contain.text', 'ชำระแล้ว ฿9,000.00')
      cy.get('.courses tbody tr').should('have.length', 1).and('contain.text', '5') // 5 sessions still unused
      // nothing chosen yet
      cy.contains('button', 'บันทึกคำขอคืนเงิน').click()
      cy.get('.page-error').should('contain.text', 'ระบุเหตุผล')
      cy.get('.courses input[type="checkbox"]').first().check()
      cy.get('.courses tbody tr').find('input[type="number"]').eq(0).type('1500')
      cy.get('.courses tbody tr').find('input[type="number"]').eq(1).clear().type('5')
      cy.get('[aria-label="คำขอคืนเงิน"]').contains('label', 'เหตุผล').find('input').type('ผู้ป่วยขอคืนสิทธิ์ที่ไม่ได้ใช้')
      cy.get('.total').should('contain.text', '฿1,500.00')
      cy.contains('button', 'บันทึกคำขอคืนเงิน').click()
      cy.noAlert()
      cy.get('[aria-label="คำขอคืนเงิน"] table').last().find('tbody tr')
        .should('have.length', 1).and('contain.text', 'รอดำเนินการ').and('contain.text', 'ตัด 5 ครั้ง')
      // the rights are cut only when the refund is confirmed
      cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 5)
      cy.contains('button', 'ยืนยันคืนเงิน').click()
      cy.noAlert()
      cy.get('[aria-label="คำขอคืนเงิน"] table').last().should('contain.text', 'คืนเงินแล้ว')
      cy.api('GET', `/api/course-purchases/${courseId}`).then(({ body }) => {
        expect(body.purchase.remaining).to.equal(0)
        expect(body.purchase.used).to.equal(1)
        expect(body.purchase.status).to.equal('REFUNDED')
      })
    })
  })

  it('shows the server\'s refusal when more sessions than are left are cancelled', function () {
    paidCourse(this.fixtures).then(({ sale }) => {
      cy.visitAs('manager', `/staff/refunds?receipt=${sale.id}`)
      cy.get('.courses tbody tr input[type="checkbox"]').check()
      cy.get('.courses tbody tr').find('input[type="number"]').eq(0).type('1500')
      cy.get('.courses tbody tr').find('input[type="number"]').eq(1).clear().type('6')
      cy.get('[aria-label="คำขอคืนเงิน"]').contains('label', 'เหตุผล').find('input').type('ทดสอบ')
      cy.contains('button', 'บันทึกคำขอคืนเงิน').click()
      cy.get('.page-error').should('contain.text', 'ไม่พอ')
    })
  })

  it('marks a request as failed or withdraws it without cutting any right', function () {
    paidCourse(this.fixtures).then(({ sale, courseId }) => {
      cy.api('GET', `/api/receipts/${sale.id}`).then(({ body }) => {
        const paymentId = body.payments[0].id
        const request = () => cy.api('POST', `/api/payments/${paymentId}/refunds`, {
          amount: 1000, paymentMethodCode: 'Cash', reason: 'x', items: [{ courseId, refundAmount: 1000, cancelledVisits: 2 }],
        }).its('status').should('equal', 201)
        request()
        request()
        cy.visitAs('manager', `/staff/refunds?receipt=${sale.id}`)
        cy.contains('button', 'คืนเงินไม่สำเร็จ').click()
        cy.contains('button', 'ยกเลิกคำขอ').click()
        cy.get('[aria-label="คำขอคืนเงิน"] table').last().should('contain.text', 'คืนเงินไม่สำเร็จ').and('contain.text', 'ยกเลิกคำขอ')
        cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 5)
      })
    })
  })

  it('finds a receipt by its number', function () {
    paidCourse(this.fixtures).then(({ sale }) => {
      cy.visitAs('manager', '/staff/refunds')
      cy.get('input[aria-label="ค้นหาใบเสร็จ"]').type(sale.receiptNo)
      cy.get('tbody tr').should('have.length', 1)
      cy.get(`button[aria-label="เลือกใบเสร็จ ${sale.receiptNo}"]`).click()
      cy.get('[aria-label="คำขอคืนเงิน"]').should('be.visible')
    })
  })
})
