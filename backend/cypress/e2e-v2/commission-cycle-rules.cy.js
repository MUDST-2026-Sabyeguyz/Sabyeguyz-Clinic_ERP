/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
// Rules of the commission cycle: new tiers wait for the next cycle, a Reopen keeps its reason, a payment keeps its
// payment date and reference.
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const month = today.slice(0, 7)
const nextMonthStart = () => {
  const [year, number] = month.split('-').map(Number)
  return new Date(Date.UTC(year, number, 1)).toISOString().slice(0, 10)
}
const tiers = [{ minimum: 0, maximum: null, rate: 0.06, order: 1 }]
const defaultScheme = () => cy.api('GET', '/api/commission/schemes').then(({ body }) => body.find((row) => row.name === 'Default Tier Scheme'))
const version = (scheme, effectiveFrom) => cy.api('POST', `/api/commission/schemes/${scheme.id}/versions`, { effectiveFrom, tiers })
const buy = (fx, patient) => cy.api('POST', '/api/course-purchases', {
  patientId: patient.id, courseId: fx.courseId, branchCode: '01', caseOwnerStaffId: fx.therapistId,
}).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body
})
const closeMonth = () => cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)

describe('v2 new commission tiers: after the current cycle is closed, from the next cycle', () => {
  it('starts only on the first day of a month', () => {
    defaultScheme().then((scheme) => {
      const [year, number] = month.split('-').map(Number)
      const mid = new Date(Date.UTC(year, number + 1, 15)).toISOString().slice(0, 10)
      version(scheme, mid).its('status').should('equal', 400)
    })
  })

  it('cannot start in the current month or earlier', () => {
    defaultScheme().then((scheme) => {
      version(scheme, `${month}-01`).its('status').should('equal', 400)
      version(scheme, '2026-01-01').its('status').should('equal', 400)
    })
  })

  it('waits for the commission of the current cycle to be closed', function () {
    cy.registerPatient().then((patient) => buy(this.fixtures, patient).then(() => {
      defaultScheme().then((scheme) => {
        version(scheme, nextMonthStart()).then(({ status, body }) => {
          expect(status).to.equal(409)
          expect(JSON.stringify(body)).to.not.contain('"status":201')
        })
        closeMonth()
        version(scheme, nextMonthStart()).its('status').should('equal', 201)
      })
    }))
  })

  it('is accepted from the next cycle when nothing is waiting to be closed', () => {
    defaultScheme().then((scheme) => version(scheme, nextMonthStart()).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      expect(body.effectiveFrom).to.equal(nextMonthStart())
    }))
  })
})

describe('v2 reopening a closed month keeps the reason', () => {
  it('stores the latest reason, who reopened and when on the closing', function () {
    cy.registerPatient().then((patient) => buy(this.fixtures, patient).then(() => {
      closeMonth()
      cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => {
        const closing = body.find((row) => row.status === 'CLOSED')
        expect(closing.reopenReason).to.equal(null)
        cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'บันทึกผู้ขายผิดคน' }).its('status').should('equal', 200)
        cy.api('GET', `/api/commission/closings?month=${month}`).its('body').should((rows) => {
          const reopened = rows.find((row) => row.id === closing.id)
          expect(reopened.status).to.equal('OPEN')
          expect(reopened.reopenReason).to.equal('บันทึกผู้ขายผิดคน')
          expect(reopened.reopenedAt).to.be.a('string')
        })
      })
    }))
  })

  it('refuses a Reopen without a reason', function () {
    cy.registerPatient().then((patient) => buy(this.fixtures, patient).then(() => {
      closeMonth()
      cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => {
        const closing = body.find((row) => row.status === 'CLOSED')
        cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: '  ' }).its('status').should('equal', 400)
      })
    }))
  })
})

describe('v2 a monthly payment keeps its payment date and reference', () => {
  const closedPayment = (fx) => cy.registerPatient().then((patient) => buy(fx, patient).then((course) => {
    cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id }).its('status').should('equal', 200)
    closeMonth()
    cy.api('POST', `/api/commission/payments/prepare?month=${month}`).its('status').should('equal', 200)
    return cy.api('GET', `/api/commission/payments?month=${month}`).then(({ body }) => {
      const payment = body.find((row) => row.staffId === fx.therapistId)
      return cy.api('POST', `/api/commission/payments/${payment.id}/close`).then(({ status }) => {
        expect(status).to.equal(200)
        return payment
      })
    })
  }))

  it('records the date and reference the Manager enters', function () {
    closedPayment(this.fixtures).then((payment) => {
      cy.api('POST', `/api/commission/payments/${payment.id}/pay`, { paidDate: today, reference: 'TRF-2026-001' })
        .then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)
          expect(body.status).to.equal('PAID')
          expect(body.paidDate).to.equal(today)
          expect(body.paymentReference).to.equal('TRF-2026-001')
        })
      cy.api('GET', `/api/commission/payments/${payment.id}`).its('body.paymentReference').should('equal', 'TRF-2026-001')
    })
  })

  it('still pays with no body, taking today as the payment date', function () {
    closedPayment(this.fixtures).then((payment) => {
      cy.api('POST', `/api/commission/payments/${payment.id}/pay`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.paidDate).to.equal(today)
        expect(body.paymentReference).to.equal(null)
      })
    })
  })

  it('refuses a payment date in the future', function () {
    closedPayment(this.fixtures).then((payment) => {
      const future = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10)
      cy.api('POST', `/api/commission/payments/${payment.id}/pay`, { paidDate: future }).its('status').should('equal', 400)
      cy.api('GET', `/api/commission/payments/${payment.id}`).its('body.status').should('equal', 'CLOSED')
    })
  })
})
