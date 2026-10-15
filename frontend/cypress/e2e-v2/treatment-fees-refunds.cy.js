/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
describe('treatment fee rules page', () => {
  it('shows the seeded rule and edits it while it is unused', () => {
    cy.visitAs('manager', '/staff/commission/compensation-settings')
    cy.contains('h1', 'ตั้งค่าค่ารักษาแทน').should('be.visible')
    cy.get('.rule-card').should('have.length', 1).and('contain.text', '฿45').and('contain.text', 'ใช้งาน')
    cy.get('.rule-card').contains('button', 'แก้ไข').click()
    cy.get('.rule-drawer select').should('not.be.disabled')
    cy.get('.rule-drawer').contains('label', 'จำนวนเงินต่อครั้ง').find('input').clear().type('50')
    cy.get('.rule-drawer').contains('button', 'บันทึกกฎ').click()
    cy.noAlert()
    cy.get('.rule-card').should('contain.text', '฿50')
    cy.api('GET', '/api/commission/treatment-fees').its('body.0.feeValue').should('equal', 50)
  })

  it('adds a percentage rule that takes over from today, and rejects 150%', () => {
    cy.visitAs('manager', '/staff/commission/compensation-settings')
    cy.contains('button', '+ เพิ่มกฎค่ามือ').click()
    cy.get('.rule-drawer select').select('Percentage')
    cy.get('.rule-drawer').contains('label', 'อัตรา (%)').find('input').clear().type('150')
    cy.get('.rule-drawer').contains('button', 'บันทึกกฎ').click()
    cy.get('.drawer-error').should('contain.text', '0 ถึง 100')
    cy.get('.rule-drawer').contains('label', 'อัตรา (%)').find('input').clear().type('10')
    cy.get('.rule-drawer').contains('button', 'บันทึกกฎ').click()
    cy.noAlert()
    cy.get('.rule-card').should('have.length', 2).and('contain.text', '10%')
    cy.api('GET', '/api/commission/treatment-fees').then(({ body }) => {
      expect(body.map((rule) => rule.feeType).sort()).to.deep.equal(['Fixed', 'Percentage'])
    })
  })

  it('disables and enables a rule', () => {
    cy.visitAs('manager', '/staff/commission/compensation-settings')
    cy.get('.rule-card').contains('button', 'ปิดใช้งาน').click()
    cy.get('.rule-card').should('contain.text', 'ปิดใช้งาน').contains('button', 'เปิดใช้งาน').click()
    cy.get('.rule-card').should('contain.text', 'ใช้งาน')
    cy.api('GET', '/api/commission/treatment-fees').its('body.0.enabled').should('equal', true)
  })

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
