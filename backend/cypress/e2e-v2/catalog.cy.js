describe('v2 catalog and reference data', () => {
  it('lists single-visit services with price and code', () => {
    cy.api('GET', '/api/catalog/services').then(({ status, body }) => {
      expect(status).to.equal(200)
      const physio = body.find((service) => service.code === 'PHYSIO_30')
      expect(physio).to.exist
      expect(Number(physio.price)).to.equal(800)
    })
  })

  it('lists courses including the fixture course with its visits and validity', () => {
    cy.api('GET', '/api/catalog/courses').then(({ status, body }) => {
      expect(status).to.equal(200)
      const course = body.find((item) => item.code === 'CYPRESS')
      expect(course).to.exist
      expect(Number(course.price)).to.equal(9000)
      expect(course.sessions).to.equal(5)
      expect(course.bonusSessions).to.equal(1)
      expect(course.validityDays).to.equal(60)
    })
  })

  it('lists therapists with their branch', () => {
    cy.api('GET', '/api/catalog/therapists').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.map((therapist) => therapist.code)).to.include.members(['THP-001', 'THP-002'])
    })
  })

  it('lists branches, payment methods and commission schemes', () => {
    cy.api('GET', '/api/branches').its('body').should('have.length', 2)
    cy.api('GET', '/api/payment-methods').its('body').should('have.length', 4)
    cy.api('GET', '/api/commission/schemes').its('body.length').should('be.at.least', 1)
  })

  it('protects the catalog behind authentication', () => {
    cy.apiAs(null, 'GET', '/api/catalog/services').its('status').should('equal', 401)
  })
})
