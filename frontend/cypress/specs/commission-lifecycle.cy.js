describe('Commission Schemes, Tiers & Closing Lifecycle', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  const uniqueSuffix = Date.now().toString().slice(-4)

  before(() => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: { username: 'admin', password: 'Admin@123456' },
    }).then((res) => {
      token = res.body.token
    })
  })

  it('queries commission schemes, tiers and manages closing reports', () => {
    // 1. List schemes
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/commission/schemes`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((schemesRes) => {
      expect(schemesRes.status).to.eq(200)
      expect(schemesRes.body).to.be.an('array')

      // 2. Create new commission scheme with tiers
      const schemePayload = {
        name: `Commission Plan ${uniqueSuffix}`,
        version: `v${uniqueSuffix}`,
        effectiveFrom: '2026-01-01',
        effectiveTo: '2026-12-31',
        tiers: [
          {
            minimum: 0.0,
            maximum: 50000.0,
            rate: 0.05,
            order: 1,
          },
          {
            minimum: 50000.01,
            maximum: 100000.0,
            rate: 0.08,
            order: 2,
          },
        ],
        active: true,
      }

      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/commission/schemes`,
        headers: { Authorization: `Bearer ${token}` },
        body: schemePayload,
      }).then((createRes) => {
        expect(createRes.status).to.eq(201)
        expect(createRes.body).to.have.property('id')
        const createdSchemeId = createRes.body.id

        // 3. Query tiers for the created scheme
        cy.request({
          method: 'GET',
          url: `${backendUrl}/api/commission/schemes/${createdSchemeId}/tiers`,
          headers: { Authorization: `Bearer ${token}` },
        }).then((tiersRes) => {
          expect(tiersRes.status).to.eq(200)
          expect(tiersRes.body).to.be.an('array').with.lengthOf(2)
        })
      })
    })

    // 4. Query commission report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/commission/report?from=2026-01-01&to=2026-12-31`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((reportRes) => {
      expect(reportRes.status).to.eq(200)
      expect(reportRes.body).to.be.an('array')
    })

    // 5. Query treatment report
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/commission/treatment-report?from=2026-01-01&to=2026-12-31`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((treatmentRes) => {
      expect(treatmentRes.status).to.eq(200)
      expect(treatmentRes.body).to.be.an('array')
    })

    // 6. Query closings for month
    cy.request({
      method: 'GET',
      url: `${backendUrl}/api/commission/closings?month=2026-06-01`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((closingRes) => {
      expect(closingRes.status).to.eq(200)
      expect(closingRes.body).to.be.an('array')
    })
  })
})
