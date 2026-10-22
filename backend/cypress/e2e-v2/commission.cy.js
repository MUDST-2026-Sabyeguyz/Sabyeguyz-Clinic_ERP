const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)
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
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
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
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
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
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
      cy.api('POST', `/api/commission/closings/${month}-01`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.have.length(1)
      })
    })
  })

  it('lists a visit in the treatment report; the Owner commission is unknown (null) until the month is closed', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
      }).then(({ body: purchase }) => {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: purchase.id }).its('status').should('equal', 200)
        cy.api('GET', `/api/commission/treatment-report${range}`).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body).to.have.length(1)
          expect(body[0].patientHn).to.equal(patient.hn)
          expect(body[0].ownerNet).to.equal(null)
          expect(body[0].gross).to.equal(null)
          expect(body[0].ownerNetPending).to.equal(true)
          expect(Number(body[0].treatmentFee)).to.equal(0)
        })
        cy.api('POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 200)
        cy.api('GET', `/api/commission/treatment-report${range}`).then(({ body }) => {
          // 9,000 THB at 5% = 450 over 6 sessions: 75.00 for the first session
          expect(Number(body[0].ownerNet)).to.equal(75)
          expect(Number(body[0].gross)).to.equal(75)
          expect(body[0].ownerNetPending).to.equal(false)
        })
      })
    })
  })

  it('manages the clinic-wide treatment fee rule: validation, no overlap, take-over, enable/disable', () => {
    const rule = (body) => cy.api('POST', '/api/commission/treatment-fees', body)
    rule({ feeType: 'Percentage', feeValue: 101, effectiveFrom: '2026-01-01' }).its('status').should('equal', 400)
    rule({ feeType: 'Fixed', feeValue: -1, effectiveFrom: '2026-01-01' }).its('status').should('equal', 400)
    rule({ feeType: 'Bogus', feeValue: 5, effectiveFrom: '2026-01-01' }).its('status').should('equal', 400)
    rule({ feeType: 'Fixed', feeValue: 50, effectiveFrom: '2026-03-01', effectiveTo: '2026-03-31' }).its('status').should('equal', 400)
    rule({ feeType: 'Percentage', feeValue: 10, effectiveFrom: '2030-01-01' }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      expect(body.enabled).to.equal(true)
      expect(body.used).to.equal(false)
      cy.api('GET', '/api/commission/treatment-fees').then(({ body: rules }) => {
        const seeded = rules.find((entry) => entry.feeType === 'Fixed')
        expect(seeded.effectiveTo).to.equal('2029-12-31')
      })
      cy.api('PUT', `/api/commission/treatment-fees/${body.id}`, { feeValue: 12.5 }).then((updated) => {
        expect(updated.status).to.equal(200)
        expect(Number(updated.body.feeValue)).to.equal(12.5)
      })
      cy.api('PATCH', `/api/commission/treatment-fees/${body.id}/disable`).its('body.enabled').should('equal', false)
      cy.api('PATCH', `/api/commission/treatment-fees/${body.id}/enable`).its('body.enabled').should('equal', true)
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
        { minimum: 0, maximum: 99999.99, rate: 0.04, order: 1 },
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

  const defaultScheme = () => cy.api('GET', '/api/commission/schemes')
    .then(({ body }) => body.find((scheme) => scheme.name === 'Default Tier Scheme'))

  it('keeps tier ranges contiguous at two decimals (50,000.00 then 50,000.01)', () => {
    cy.api('POST', '/api/commission/schemes', {
      name: 'Satang scheme', effectiveFrom: '2032-01-01',
      tiers: [
        { minimum: 0, maximum: 50000, rate: 0.05, order: 1 },
        { minimum: 50000.01, maximum: null, rate: 0.06, order: 2 },
      ],
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      cy.api('GET', `/api/commission/schemes/${body.id}/tiers`).then((tiers) => {
        expect(Number(tiers.body[0].maximum)).to.equal(50000)
        expect(Number(tiers.body[1].minimum)).to.equal(50000.01)
      })
    })
  })

  it('rejects whole-baht tiers that leave satang in no tier, and other malformed tiers', () => {
    const scheme = (tiers) => ({ name: 'Bad scheme', effectiveFrom: '2034-01-01', tiers })
    const bad = [
      [{ minimum: 0, maximum: 59999, rate: 0.05, order: 1 }, { minimum: 60000, maximum: null, rate: 0.1, order: 2 }],
      [{ minimum: 0, maximum: 100.005, rate: 0.05, order: 1 }, { minimum: 100.01, maximum: null, rate: 0.1, order: 2 }],
      [{ minimum: 0, maximum: 100, rate: 0.05, order: 1 }, { minimum: 100.01, maximum: null, rate: 0.1, order: 1 }],
      [{ minimum: 0, maximum: 100, rate: 0.05, order: 1 }, { minimum: 100.01, maximum: 200, rate: 0.1, order: 2 }],
      [{ minimum: 1, maximum: null, rate: 0.05, order: 1 }],
      [{ minimum: 0, maximum: null, rate: 1.5, order: 1 }],
    ]
    bad.forEach((tiers) => cy.api('POST', '/api/commission/schemes', scheme(tiers)).its('status').should('equal', 400))
  })

  it('rejects a scheme whose dates overlap another scheme without taking over from it', () => {
    cy.api('POST', '/api/commission/schemes', {
      name: 'Inside', effectiveFrom: '2026-06-01', effectiveTo: '2026-12-31',
      tiers: [{ minimum: 0, maximum: null, rate: 0.05, order: 1 }],
    }).its('status').should('equal', 400)
  })

  it('ends the open-ended scheme the day before a newer version starts', () => {
    cy.api('POST', '/api/commission/schemes', {
      name: 'Takeover', effectiveFrom: '2035-01-01',
      tiers: [{ minimum: 0, maximum: null, rate: 0.05, order: 1 }],
    }).its('status').should('equal', 201)
    cy.api('GET', '/api/commission/schemes').then(({ body }) => {
      const previous = body.find((scheme) => scheme.name === 'Default Tier Scheme')
      expect(previous.effectiveTo).to.equal('2034-12-31')
    })
  })

  it('creates a new version of a scheme and keeps the old one untouched', () => {
    defaultScheme().then((scheme) => {
      cy.api('POST', `/api/commission/schemes/${scheme.id}/versions`, {
        effectiveFrom: '2036-01-01',
        tiers: [{ minimum: 0, maximum: 70000, rate: 0.04, order: 1 }, { minimum: 70000.01, maximum: null, rate: 0.09, order: 2 }],
      }).then(({ status, body }) => {
        expect(status, JSON.stringify(body)).to.equal(201)
        expect(body.name).to.equal('Default Tier Scheme')
        expect(body.version).to.equal(scheme.version + 1)
        cy.api('GET', `/api/commission/schemes/${scheme.id}/tiers`).its('body').should('have.length', 5)
      })
    })
  })

  it('refuses to change the tiers or disable a scheme a course already uses, but allows a rename', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      defaultScheme().then((scheme) => {
        expect(scheme.locked).to.equal(true)
        cy.api('PUT', `/api/commission/schemes/${scheme.id}`, {
          name: 'Default Tier Scheme', effectiveFrom: scheme.effectiveFrom, tiers: [{ minimum: 0, maximum: null, rate: 0.01, order: 1 }],
        }).its('status').should('equal', 409)
        cy.api('DELETE', `/api/commission/schemes/${scheme.id}`).its('status').should('equal', 405)
        cy.api('PUT', `/api/commission/schemes/${scheme.id}`, { name: 'Default Tier Scheme', effectiveFrom: scheme.effectiveFrom })
          .its('status').should('equal', 200)
        cy.api('GET', `/api/commission/schemes/${scheme.id}/tiers`).its('body').should('have.length', 5)
      })
    })
  })

  it('counts only valid course sales towards the monthly tier sales (a voided sale is left out)', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', caseOwnerStaffId: this.fixtures.therapistId, paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', caseOwnerStaffId: this.fixtures.therapistId, paymentLabelCode: 'FULL',
      }).then(({ body: second }) => {
        cy.api('POST', `/api/payments/${second.id}/reverse`, { reason: 'ยกเลิกการขาย' }).its('status').should('equal', 200)
      })
      cy.api('GET', `/api/commission/closings?month=${month}`).then(({ body }) => {
        const row = body.find((entry) => entry.employeeId === this.fixtures.therapistId)
        expect(Number(row.sales)).to.equal(9000)
      })
    })
  })

  it('the sales report leaves out voided sales and takes off Completed refunds, like the monthly tier', function () {
    cy.registerPatient().then((patient) => {
      const sell = () => cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        salespersonStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 9000 }],
      }).then(({ body }) => body)
      sell().then((first) => sell().then((second) => sell().then((third) => {
        cy.api('POST', `/api/payments/${second.id}/reverse`, { reason: 'ยกเลิกการขาย' }).its('status').should('equal', 200)
        cy.api('GET', `/api/receipts/${third.id}`).then(({ body: detail }) => cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
          const course = courses.find((row) => row.receiptId === detail.receiptId)
          cy.api('POST', `/api/payments/${detail.payments[0].id}/refunds`, {
            amount: 2000, paymentMethodCode: 'BankTransfer', reason: 'คืนบางส่วน',
            items: [{ courseId: course.id, refundAmount: 2000, cancelledVisits: 3 }],
          }).then(({ body: refund }) => cy.api('POST', `/api/refunds/${refund.id}/complete`).its('status').should('equal', 200))
        }))
        cy.api('GET', `/api/commission/report${range}`).then(({ status, body }) => {
          expect(status).to.equal(200)
          const row = body.find((entry) => entry.staffId === this.fixtures.therapistId)
          expect(row.saleCount).to.equal(2) // the voided sale is out
          expect(Number(row.base)).to.equal(16000) // 9,000 + 9,000 less the 2,000 refunded
        })
        expect(first.id).to.be.a('number')
      })))
    })
  })

  it('rejects a gap or overlap in the tiers', () => {
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
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',
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
