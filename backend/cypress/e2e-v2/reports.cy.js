const range = '?from=2020-01-01&to=2099-12-31'

describe('v2 reports', () => {
  it('answers every report on an empty clinic', () => {
    for (const path of ['/api/dashboard/summary', '/api/reports/revenue', '/api/reports/appointments',
      '/api/reports/course-balances', '/api/reports/staff-sales', '/api/commission/report']) {
      cy.api('GET', `${path}${range}`).then(({ status, body }) => {
        expect(status, path).to.equal(200)
        expect(body, path).to.exist
      })
    }
  })

  it('counts a paid sale in revenue', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        paymentLabelCode: 'FULL',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/revenue${range}`).then(({ body }) => {
        expect(Number(body.totalRevenue)).to.equal(800)
      })
    })
  })

  it('shows a bought course in the balance report', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
        paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/course-balances${range}`).then(({ body }) => {
        expect(body.purchases).to.equal(1)
        expect(body.rows[0].hn).to.equal(patient.hn)
      })
    })
  })

  it('counts appointments by status', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/appointments', {
        branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId,
        serviceId: this.fixtures.serviceId, startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`,
      }).its('status').should('equal', 201)
      cy.api('GET', `/api/reports/appointments${range}`).then(({ body }) => {
        expect(body.appointments).to.equal(1)
      })
    })
  })

  it('requires authentication', () => {
    cy.apiAs(null, 'GET', `/api/reports/revenue${range}`).its('status').should('equal', 401)
  })
})
