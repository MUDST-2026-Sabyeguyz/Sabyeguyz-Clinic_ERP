describe('Reports & Dashboard API and UI Flow', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  let authUser = null

  before(() => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: { username: 'admin', password: 'Admin@123456' },
    }).then((res) => {
      token = res.body.token
      authUser = res.body
    })
  })

  it('queries dashboard summary and reports endpoints successfully', () => {
    // 1. Dashboard summary
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/dashboard/summary?from=2026-01-01&to=2026-12-31&branchCode=01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('uniquePatients')
      expect(res.body).to.have.property('visits')
      expect(res.body).to.have.property('totalRevenue')
      expect(res.body).to.have.property('receiptsIssued')
    })

    // 2. Appointments report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/reports/appointments?from=2026-01-01&to=2026-12-31&branchCode=01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('appointments')
      expect(res.body).to.have.property('visits')
    })

    // 3. Revenue report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/reports/revenue?from=2026-01-01&to=2026-12-31&branchCode=01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('totalRevenue')
      expect(res.body).to.have.property('byCategory')
    })

    // 4. Course balances report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/reports/course-balances?from=2026-01-01&to=2026-12-31&branchCode=01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('purchases')
      expect(res.body).to.have.property('totalPurchasedSessions')
    })

    // 5. Staff sales report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/reports/staff-sales?from=2026-01-01&to=2026-12-31&branchCode=01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      expect(res.status).to.eq(200)
      expect(res.body).to.have.property('branchTotal')
      expect(res.body).to.have.property('receipts')
      expect(res.body).to.have.property('rows')
    })
  })

  it('navigates to dashboard UI with authenticated session', () => {
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('authToken', token)
        win.localStorage.setItem('authUser', JSON.stringify(authUser))
      },
    })

    cy.visit('/staff/dashboard')
    cy.url().should('include', '/staff/dashboard')
  })
})
