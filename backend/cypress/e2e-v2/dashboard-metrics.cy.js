const day = '2030-10-01'
const summary = (from = day, to = from, branch = '') =>
  cy.api('GET', `/api/dashboard/summary?from=${from}&to=${to}${branch ? `&branchCode=${branch}` : ''}`)
const dates = (patientId, values) => cy.api('POST', '/__cypress/dashboard/dates', { patientId, ...values }).its('status').should('equal', 200)
const sale = (fx, patient) => cy.api('POST', '/api/payments', {
  patientId: patient.id, branchCode: '01',
  paymentLabelCode: 'FULL',
  items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
  payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
}).then(({ status, body }) => { expect(status).to.equal(201); return body })

describe('dashboard registration-based customers and recorded activity', () => {
  it('returns zeros and a daily sales entry for every selected day', () => {
    summary(day, '2030-10-03').then(({ status, body }) => {
      expect(status).to.equal(200)
      for (const field of ['newCustomers', 'returningCustomers', 'courseRenewals', 'packageUpdates', 'salesTotal', 'cashReceived']) {
        expect(Number(body[field]), field).to.equal(0)
      }
      expect(body.dailySales.map(row => [row.date, Number(row.amount)])).to.deep.equal([
        [day, 0], ['2030-10-02', 0], ['2030-10-03', 0],
      ])
    })
  })

  it('counts registration without a visit on the Bangkok registration day, with an exclusive next-day boundary', () => {
    cy.registerPatient().then(patient => dates(patient.id, { registeredAt: '2030-09-30T17:00:00Z' }))
    cy.registerPatient().then(patient => dates(patient.id, { registeredAt: '2030-10-01T17:00:00Z' }))
    summary().then(({ body }) => {
      expect(body.newCustomers).to.equal(1)
      expect(body.returningCustomers).to.equal(0)
      expect(body.visits).to.equal(0)
    })
  })

  it('counts only older registrations that attended during the selected period as returning customers', function () {
    cy.registerPatient().then(patient => {
      cy.configureBookingWindows()
      for (const hour of ['10', '13']) {
        cy.api('POST', '/api/appointments', {
          branchCode: '01',
          patientId: patient.id,
          therapistId: this.fixtures.therapistId,
          serviceId: this.fixtures.serviceId,
          startAt: `${day}T${hour}:00:00+07:00`,
          endAt: `${day}T${String(Number(hour) + 1).padStart(2, '0')}:00:00+07:00`,
        }).then(({ status, body }) => {
          expect(status).to.equal(201)
          cy.api('POST', `/api/appointments/${body.id}/check-in`)
              .its('status').should('equal', 200)
        })
      }
      dates(patient.id, { registeredAt: '2030-09-30T16:59:59Z', visitAt: '2030-10-01T03:00:00Z' })
    })
    cy.registerPatient().then(patient => dates(patient.id, { registeredAt: '2030-09-01T00:00:00Z' }))
    summary().then(({ body }) => {
      expect(body.returningCustomers).to.equal(1)
      expect(body.newCustomers).to.equal(0)
      expect(body.visits).to.equal(2)
    })
    summary(day, day, '02').its('body.returningCustomers').should('equal', 0)
  })

  it('filters registrations by their registration branch', () => {
    for (const branchCode of ['01', '02']) {
      cy.registerPatient({ branchCode }).then(patient => dates(patient.id, { registeredAt: `${day}T03:00:00Z` }))
    }
    summary().its('body.newCustomers').should('equal', 2)
    summary(day, day, '01').its('body.newCustomers').should('equal', 1)
    summary(day, day, '02').its('body.newCustomers').should('equal', 1)
  })

  it('separates sale-date net sales from payment-date cash and excludes the next Bangkok midnight', function () {
    cy.registerPatient().then(patient => {
      sale(this.fixtures, patient)
      dates(patient.id, { saleDate: day, paymentAt: '2030-10-02T03:00:00Z' })
    })
    summary().then(({ body }) => {
      expect(Number(body.salesTotal)).to.equal(800)
      expect(Number(body.cashReceived)).to.equal(0)
      expect(Number(body.dailySales[0].amount)).to.equal(800)
    })
    summary('2030-10-02').then(({ body }) => {
      expect(Number(body.salesTotal)).to.equal(0)
      expect(Number(body.cashReceived)).to.equal(800)
    })
    cy.registerPatient().then(patient => {
      sale(this.fixtures, patient)
      dates(patient.id, { saleDate: '2030-10-03', paymentAt: '2030-10-02T17:00:00Z' })
    })
    summary('2030-10-02').its('body.cashReceived').then(amount => expect(Number(amount)).to.equal(800))
  })

  it('excludes voided receipts from sales totals and daily sales', function () {
    cy.registerPatient().then(patient => {
      sale(this.fixtures, patient).then(s => {
        cy.api('POST', `/api/payments/${s.id}/reverse`, { reason: 'dashboard test' }).its('status').should('equal', 200)
      })
      dates(patient.id, { saleDate: day })
    })
    summary().then(({ body }) => {
      expect(Number(body.salesTotal)).to.equal(0)
      expect(Number(body.dailySales[0].amount)).to.equal(0)
    })
  })

  it('counts recorded course extensions by event date and course branch', function () {
    cy.registerPatient().then(patient => {
      cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', paymentLabelCode: 'FULL',})
        .then(({ status, body }) => {
          expect(status).to.equal(201)
          cy.api('POST', '/__cypress/dashboard/extension', { courseId: body.id, extendedAt: `${day}T03:00:00Z` })
            .its('status').should('equal', 200)
        })
    })
    summary().its('body.courseRenewals').should('equal', 1)
    summary(day, day, '02').its('body.courseRenewals').should('equal', 0)
    summary('2030-10-02').its('body.courseRenewals').should('equal', 0)
  })

  it('counts actual template edits as events, ignores no-op saves, and keeps clinic-wide scope explicit', function () {
    const query = '?from=2020-01-01&to=2099-12-31'
    cy.api('GET', `/api/courses/${this.fixtures.courseId}`).then(({ body: template }) => {
      const payload = { code: template.code, name: 'เปลี่ยนครั้งที่หนึ่ง', price: template.price, sessions: template.sessions,
        bonusSessions: template.bonusSessions, validityDays: 365 }
      cy.api('PUT', `/api/courses/${template.id}`, payload).its('status').should('equal', 200)
      cy.api('PUT', `/api/courses/${template.id}`, { ...payload, name: 'เปลี่ยนครั้งที่สอง' }).its('status').should('equal', 200)
      cy.api('PUT', `/api/courses/${template.id}`, { ...payload, name: 'เปลี่ยนครั้งที่สอง' }).its('status').should('equal', 200)
    })
    cy.api('GET', `/api/dashboard/summary${query}`).then(({ body }) => {
      expect(body.packageUpdates).to.equal(2)
      expect(body.packageUpdatesScope).to.equal('CLINIC')
    })
    cy.api('GET', `/api/dashboard/summary${query}&branchCode=02`).its('body.packageUpdates').should('equal', 2)
  })

  it('rejects reversed date ranges', () => {
    summary('2030-10-03', day).its('status').should('equal', 400)
  })
})
