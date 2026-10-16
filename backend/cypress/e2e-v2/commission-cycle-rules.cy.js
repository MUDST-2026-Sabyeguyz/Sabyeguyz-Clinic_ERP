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

describe("v2 how the pay is worked out can be read by a therapist, the rules and money screens are the Manager's", () => {
  it('lets a therapist read the tiers and the treatment fee in force, nothing else', () => {
    cy.login('therapist', 'therapist123').its('body.token').then((token) => {
      cy.apiAs(token, 'GET', '/api/commission/formula').its('status').should('equal', 200)
    })
  })

  it('keeps them closed to a therapist', () => {
    cy.login('therapist', 'therapist123').its('body.token').then((token) => {
      for (const url of ['/api/commission/schemes', '/api/commission/treatment-fees',
        '/api/commission/report?from=2026-01-01&to=2026-12-31', `/api/commission/closings?month=${month}`]) {
        cy.apiAs(token, 'GET', url).its('status').should('equal', 403)
      }
    })
  })
})

describe('v2 a therapist reads the lines behind their own pay', () => {
  const sessions = (fx, count) => cy.registerPatient().then((patient) => buy(fx, patient).then((course) => {
    for (let i = 0; i < count; i++) cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id }).its('status').should('equal', 200)
    return cy.wrap(course)
  }))

  it('lists each session with its base, rate and amount once the sale month is closed', function () {
    sessions(this.fixtures, 2)
    closeMonth()
    cy.login('therapist', 'therapist123').its('body.token').then((token) => {
      cy.apiAs(token, 'GET', `/api/commission/my-lines?month=${month}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        const commission = body.lines.filter((row) => row.kind === 'COMMISSION')
        expect(commission).to.have.length(2)
        commission.forEach((row) => {
          expect(row.role).to.equal('เจ้าของคอร์ส + ผู้รักษา')
          expect(row.rate).to.be.a('number')
          expect(row.tier).to.match(/^Tier \d$/)
          expect(row.base).to.equal(row.grossAmount - row.lineDiscount)
          expect(row.patientHn).to.be.a('string')
        })
        const sum = commission.reduce((total, row) => total + row.amount, 0)
        expect(Math.round(sum * 100)).to.equal(Math.round(body.courseCommission * 100))
      })
      cy.apiAs(token, 'GET', '/api/commission/my-adjustments').its('status').should('equal', 200)
    })
  })

  it('shows a therapist only their own lines', function () {
    sessions(this.fixtures, 1)
    closeMonth()
    cy.login('therapist2', 'therapist123').its('body.token').then((token) => {
      cy.apiAs(token, 'GET', `/api/commission/my-lines?month=${month}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.lines).to.have.length(0)
      })
    })
  })

  it('keeps the sessions of an unclosed sale month out of the commission', function () {
    sessions(this.fixtures, 1)
    cy.login('therapist', 'therapist123').its('body.token').then((token) => {
      cy.apiAs(token, 'GET', `/api/commission/my-lines?month=${month}`).then(({ body }) => {
        expect(body.courseCommission).to.equal(null)
        expect(body.pendingSessions).to.equal(1)
        expect(body.lines.filter((row) => row.kind === 'COMMISSION')).to.have.length(0)
      })
    })
  })
})
