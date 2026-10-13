const month = new Date().toISOString().slice(0, 7)
const range = '?from=2020-01-01&to=2099-12-31'

describe('v2 commission', () => {
  it('previews the pool of the 9,000 THB course at the first tier (5%)', function () {
    cy.api('GET', `/api/commission/preview?courseId=${this.fixtures.courseId}&sellerStaffId=${this.fixtures.managerStaffId}`)
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(Number(body.netPrice)).to.equal(9000)
        expect(Number(body.commissionRate)).to.equal(0.05)
        expect(Number(body.commissionPool)).to.equal(450)
        expect(Number(body.commissionPerVisit)).to.be.greaterThan(0)
      })
  })

  it('bases the preview on the price after the line discount', function () {
    cy.api('GET', `/api/commission/preview?courseId=${this.fixtures.courseId}&sellerStaffId=${this.fixtures.managerStaffId}`
      + '&discountType=BAHT&discountValue=1000').then(({ body }) => {
      expect(Number(body.netPrice)).to.equal(8000)
      expect(Number(body.commissionPool)).to.equal(400)
    })
  })

  it('moves to the next tier as the monthly sales grow', function () {
    const rateAt = (fx, price) => cy.api('GET',
      `/api/commission/preview?courseId=${fx.courseId}&sellerStaffId=${fx.managerStaffId}&price=${price}`)
      .then(({ body }) => Number(body.commissionRate))
    rateAt(this.fixtures, 59999).should('equal', 0.05)
    rateAt(this.fixtures, 60000).should('equal', 0.06)
    rateAt(this.fixtures, 70000).should('equal', 0.07)
    rateAt(this.fixtures, 90000).should('equal', 0.1)
  })

  it('counts sales already made this month towards the tier', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
        discountType: 'BAHT', discountValue: 0,
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/commission/preview?courseId=${this.fixtures.courseId}&sellerStaffId=${this.fixtures.managerStaffId}`)
        .then(({ body }) => {
          expect(Number(body.monthlySalesToDate)).to.be.at.least(9000)
        })
    })
  })

  it('closes the month: locks the rate and the commission pool of each course', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).then(({ body: purchase }) => {
        expect(purchase.commissionStatus).to.equal('PROVISIONAL')
        cy.api('POST', `/api/commission/closings/${month}-01`).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body).to.have.length(1)
          expect(body[0].status).to.equal('CLOSED')
          expect(Number(body[0].sales)).to.equal(9000)
          expect(Number(body[0].rate)).to.equal(0.05)
        })
        cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body }) => {
          expect(body[0].commissionStatus).to.equal('LOCKED')
          expect(Number(body[0].lockedCommissionRate)).to.equal(0.05)
          expect(Number(body[0].totalCourseCommissionPool)).to.equal(450)
          expect(Number(body[0].commissionAllocationPerVisit)).to.be.greaterThan(0)
        })
        cy.api('GET', `/api/commission/closings?month=${month}`).its('body').should('have.length', 1)
      })
    })
  })

  it('closing a month twice does not close it again', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).its('status').should('equal', 201)
      cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
      cy.api('POST', `/api/commission/closings/${month}-01`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.have.length(1)
      })
    })
  })

  it('lists a visit in the treatment report with its commission allocation', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).then(({ body: purchase }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)
        cy.api('GET', `/api/commission/treatment-report${range}`).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body).to.have.length(1)
          expect(body[0].patientHn).to.equal(patient.hn)
          expect(Number(body[0].gross)).to.be.greaterThan(0)
          expect(Number(body[0].treatmentFee)).to.equal(0)
          expect(Number(body[0].ownerNet)).to.equal(Number(body[0].gross))
        })
      })
    })
  })

  it('shows the seeded substitute treatment fee rule (45 THB fixed)', () => {
    cy.api('GET', '/api/commission/treatment-fees').then(({ status, body }) => {
      expect(status).to.equal(200)
      const fixed = body.find((rule) => rule.feeType === 'Fixed')
      expect(fixed).to.exist
      expect(Number(fixed.feeValue)).to.equal(45)
    })
  })

  it('creates a commission scheme with tiers and lists them', () => {
    cy.api('POST', '/api/commission/schemes', {
      name: 'Scheme 2030', effectiveFrom: '2030-01-01',
      tiers: [
        { minimum: 0, maximum: 99999, rate: 0.04, order: 1 },
        { minimum: 100000, maximum: null, rate: 0.12, order: 2 },
      ],
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      cy.api('GET', `/api/commission/schemes/${body.id}/tiers`).then((tiers) => {
        expect(tiers.status).to.equal(200)
        expect(tiers.body).to.have.length(2)
        expect(Number(tiers.body[1].rate)).to.equal(0.12)
      })
    })
  })

  it('rejects a scheme whose tiers leave a gap or overlap', () => {
    const scheme = (tiers) => ({ name: 'Bad scheme', effectiveFrom: '2031-01-01', tiers })
    cy.api('POST', '/api/commission/schemes', scheme([
      { minimum: 0, maximum: 50000, rate: 0.05, order: 1 },
      { minimum: 60000, maximum: null, rate: 0.1, order: 2 },
    ])).its('status').should('equal', 400)
    cy.api('POST', '/api/commission/schemes', scheme([
      { minimum: 0, maximum: 60000, rate: 0.05, order: 1 },
      { minimum: 50000, maximum: null, rate: 0.1, order: 2 },
    ])).its('status').should('equal', 400)
  })

  it('is manager-only', () => {
    cy.login('therapist', 'therapist123').then(({ body: auth }) => {
      cy.apiAs(auth.token, 'GET', '/api/commission/schemes').its('status').should('equal', 403)
      cy.apiAs(auth.token, 'POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 403)
    })
  })

  it('lists a seller with course sales as OPEN before the month is closed, then as CLOSED', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
      }).its('status').should('equal', 201)
    })
    cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => {
      expect(body).to.have.length(1)
      expect(body[0].status).to.equal('OPEN')
      expect(Number(body[0].sales)).to.equal(9000)
      expect(Number(body[0].rate)).to.equal(0.05)
    })
    cy.api('POST', `/api/commission/closings/${month}-01`).its('body.0.status').should('equal', 'CLOSED')
    cy.api('GET', `/api/commission/closings?month=${month}`).its('body').should('have.length', 1)
  })
})
