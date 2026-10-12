import { saleBody } from '../support/commands'

const period = { from: '2032-07-04', to: '2032-07-04' }

describe('Dashboard and report API', () => {
  function sellOnReportDate(fixtures) {
    return cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', saleBody(fixtures.courseId, patient.id)).then(({ status, body }) => {
        expect(status).to.equal(201)
        cy.api('POST', `/__cypress/sales/${body.id}/report-date`).its('status').should('equal', 200)
      })
    })
  }

  it('returns dashboard revenue for the requested period and branch', function () {
    sellOnReportDate(this.fixtures)
    cy.api('GET', '/api/dashboard/summary', undefined, { qs: { ...period, branchCode: '01' } })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({ from: '2032-07-04', branchCode: '01', totalRevenue: 9000,
          courseRevenue: 9000, singleVisitRevenue: 0, receiptsIssued: 1, receiptsVoided: 0 })
      })
  })

  it('returns a daily revenue series including empty days', function () {
    sellOnReportDate(this.fixtures)
    cy.api('GET', '/api/reports/revenue', undefined, { qs: { ...period, to: '2032-07-06' } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.totalRevenue).to.equal(9000)
      expect(body.daily).to.have.length(3)
      expect(body.daily[0]).to.include({ date: '2032-07-04', amount: 9000 })
      expect(body.daily[1].amount).to.equal(0)
      expect(body.daily[2].amount).to.equal(0)
      expect(body.byCategory.find(({ type }) => type === 'COURSE').amount).to.equal(9000)
    })
  })

  it('returns purchased and remaining course balances', function () {
    sellOnReportDate(this.fixtures)
    cy.api('GET', '/api/reports/course-balances', undefined, { qs: period }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.include({ purchases: 1, totalPurchasedSessions: 6, totalRemainingSessions: 6 })
      expect(body.rows[0]).to.include({ courseName: 'คอร์สทดสอบ API', status: 'ACTIVE' })
    })
  })

  it('puts sales without a salesperson in the unassigned bucket', function () {
    sellOnReportDate(this.fixtures)
    cy.api('GET', '/api/reports/staff-sales', undefined, { qs: { ...period, branchCode: '01' } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.branchTotal).to.equal(9000)
      expect(body.rows).to.have.length(0)
      expect(body.unassigned).to.include({ amount: 9000, fullName: 'ไม่ระบุผู้ขาย' })
    })
  })

  it('returns an empty appointment report', () => {
    cy.api('GET', '/api/reports/appointments', undefined, { qs: { ...period, branchCode: '01' } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.include({ appointments: 0, visits: 0, noShowRate: 0 })
      expect(body.daily).to.have.length(1)
    })
  })

  for (const [name, qs] of [
    ['inverted range', { ...period, to: '2032-07-03' }],
    ['unknown branch', { ...period, branchCode: 'ZZ' }],
    ['malformed date', { ...period, from: '4 กรกฎาคม' }],
    ['missing date', { to: '2032-07-04' }],
  ]) {
    it(`rejects an ${name}`, () => {
      cy.api('GET', '/api/dashboard/summary', undefined, { qs }).then(({ status, body }) => {
        expect(status).to.equal(400)
        if (name === 'inverted range') expect(body.message).to.be.a('string').and.not.be.empty
      })
    })
  }

  it('returns 404 for an unknown patient filter', () => {
    cy.api('GET', '/api/reports/course-balances', undefined, { qs: { ...period, patientId: 999999 } })
      .its('status').should('equal', 404)
  })
})
