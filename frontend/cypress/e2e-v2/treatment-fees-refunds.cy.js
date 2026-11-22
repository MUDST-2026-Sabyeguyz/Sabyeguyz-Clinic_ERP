/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const currentMonth = today.slice(0, 7)
const nextMonthStart = () => {
  const [year, month] = today.split('-').map(Number)
  return new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 10)
}

describe('treatment fee rule (settings)', () => {
  it('shows the rule in force and saves a change as a new version for the next month', () => {
    cy.api('POST', `/api/commission/closings/${currentMonth}-01?branchCode=01`).its('status').should('equal', 200)
    cy.visitAs('manager', '/staff/settings/commission')
    cy.contains('.settings-panel-head h2', 'กฎค่ามือ').should('be.visible')
    cy.get('.fee-version').should('have.length', 1).and('contain.text', '45 บาท / ครั้ง').and('contain.text', 'ใช้อยู่')
    cy.get('.settings-panel-head').contains('button', 'ตั้งค่ากฎค่ามือ').click()
    cy.get('.treatment-fee-drawer input[maxlength="100"]').type('กฎค่ามือใหม่')
    cy.get('.treatment-fee-drawer input[type="number"]').clear().type('50')
    cy.get('.treatment-fee-drawer .treatment-fee-save').click()
    cy.noAlert()
    cy.get('.fee-version').should('have.length', 2).and('contain.text', 'รอเริ่มใช้').and('contain.text', '50 บาท / ครั้ง')
    cy.api('GET', '/api/commission/treatment-fees').then(({ body }) => {
      expect(body).to.have.length(2)
      const next = body.find((rule) => Number(rule.feeValue) === 50)
      expect(next.effectiveFrom).to.equal(nextMonthStart())
      expect(body.find((rule) => Number(rule.feeValue) === 45).effectiveTo, 'the rule in force ends the day before').to.be.a('string')
    })
  })

  it('rejects 150% before saving', () => {
    cy.visitAs('manager', '/staff/settings/commission')
    cy.get('.settings-panel-head').contains('button', 'ตั้งค่ากฎค่ามือ').click()
    cy.get('.treatment-fee-drawer input[maxlength="100"]').type('กฎค่ามือเกินร้อย')
    cy.get('.treatment-fee-drawer select').select('Percentage')
    cy.get('.treatment-fee-drawer input[type="number"]').clear().type('150')
    cy.get('.treatment-fee-drawer .treatment-fee-save').click()
    cy.get('.treatment-fee-drawer .field-hint').should('contain.text', '0 ถึง 100')
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

describe('treatment fee backfill API', () => {
  it('backfills the single-visit fee that was missing when no rule applied', function () {
    cy.openBookingHours('01')
    cy.api('POST', '/__cypress/treatment-fee-availability-fixture', { available: false }).its('status').should('equal', 200)
    cy.registerPatient().then((patient) =>
      cy.api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: patient.id,
        therapistId: this.fixtures.therapistId,
        serviceId: this.fixtures.singleVisitServiceId,
        startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
        endAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
      }).then(({ status, body: appt }) => {
        expect(status, JSON.stringify(appt)).to.equal(201)
        return cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          appointmentId: appt.id,
          salespersonStaffId: this.fixtures.therapistId,
          treatingStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
        }).its('status').should('equal', 201)
      }),
    )
    cy.api('POST', '/__cypress/treatment-fee-availability-fixture', { available: true }).its('status').should('equal', 200)
    cy.api('POST', `/api/commission/single-visit-fees/backfill?from=${today}&to=${this.fixtures.tomorrow}`, {}).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.recorded).to.equal(1)
    })
  })
})

describe('refund API and payment report integration', () => {
  function paidCourse(fx) {
    return cy.openBookingHours('01').then(() =>
      cy.registerPatient().then((patient) =>
        cy.api('POST', '/api/payments', {
          patientId: patient.id,
          branchCode: '01',
          salespersonStaffId: fx.therapistId,
          paymentLabelCode: 'FULL',
          items: [{ type: 'COURSE', referenceId: fx.courseId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'PromptPay', amount: 9000 }],
        }).then(({ status, body: sale }) => {
          expect(status, JSON.stringify(sale)).to.equal(201)
          return cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) =>
            cy.api('POST', '/api/appointments', {
              branchCode: '01',
              patientId: patient.id,
              therapistId: fx.therapistId,
              serviceId: fx.courseId,
              startAt: `${fx.tomorrow}T09:00:00+07:00`,
              endAt: `${fx.tomorrow}T10:00:00+07:00`,
            }).then(({ body: appt }) =>
              cy.api('POST', '/api/course-usages', { coursePurchaseId: courses[0].id, appointmentId: appt.id }).then(() =>
                cy.wrap({ sale, patient, courseId: courses[0].id }),
              ),
            ),
          )
        }),
      ),
    )
  }

  it('requests, validates, and completes a course refund and reflects it in reports', function () {
    paidCourse(this.fixtures).then(({ sale, courseId }) => {
      cy.api('GET', `/api/receipts/${sale.id}`).then(({ body }) => {
        const paymentId = body.payments[0].id
        cy.api('POST', `/api/payments/${paymentId}/refunds`, {
          amount: 1500,
          paymentMethodCode: 'PromptPay',
          reason: 'เกินสิทธิ์คงเหลือ',
          items: [{ courseId, refundAmount: 1500, cancelledVisits: 5 }],
        }).then(({ status }) => expect(status).to.equal(400))

        cy.api('POST', `/api/payments/${paymentId}/refunds`, {
          amount: 1500,
          paymentMethodCode: 'PromptPay',
          reason: 'ผู้ป่วยขอคืนสิทธิ์ที่ไม่ได้ใช้',
          items: [{ courseId, refundAmount: 1500, cancelledVisits: 4 }],
        }).then(({ status, body: refund }) => {
          expect(status).to.equal(201)
          cy.api('GET', `/api/course-purchases/${courseId}`).its('body.purchase.remaining').should('equal', 4)
          cy.api('POST', `/api/refunds/${refund.id}/complete`, {}).its('status').should('equal', 200)
          cy.api('GET', `/api/course-purchases/${courseId}`).then(({ body: updated }) => {
            expect(updated.purchase.remaining).to.equal(0)
            expect(updated.purchase.used).to.equal(1)
            expect(updated.purchase.status).to.equal('REFUNDED')
          })
          cy.visitAs('manager', '/staff/reports')
          cy.contains('label', 'เลือกรายงาน').find('select').select('payment')
          cy.get('.detail-report').should('contain.text', '1,500.00')
        })
      })
    })
  })
})
