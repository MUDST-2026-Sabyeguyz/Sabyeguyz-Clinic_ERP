describe('Patient Lifecycle & HN Validation', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  const uniqueSuffix = Date.now().toString().slice(-6)

  before(() => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      log: false,
      failOnStatusCode: false,
      body: { username: 'admin', password: Cypress.env('BOOTSTRAP_ADMIN_PASSWORD') },
    }).then((res) => {
      expect(res.status).to.eq(200)
      token = res.body.token
    })
  })

  it('registers a patient, returns sequential HN and verifies profile', () => {
    const payload = {
      branchCode: '01',
      patientType: 'Thai',
      title: 'นาย',
      firstName: `CypressFirst${uniqueSuffix}`,
      lastName: `CypressLast${uniqueSuffix}`,
      gender: 'Male',
      bloodGroup: 'O',
      birthDate: '1995-05-20',
      phone: `08${uniqueSuffix.padStart(8, '0')}`,
      identityDocumentType: 'NATIONAL_ID',
      identityNumber: `1100000${uniqueSuffix.padStart(6, '0')}`,
      nationalId: `1100000${uniqueSuffix.padStart(6, '0')}`,
      address: '123 Bangkok Road',
      emergencyContactName: 'Emergency Person',
      emergencyContactPhone: '0891234567',
      emergencyContactRelationship: 'Friend',
      allergies: 'None',
      chronicDiseases: 'None',
      privacyConsent: true,
    }

    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/patients`,
      headers: { Authorization: `Bearer ${token}` },
      body: payload,
    }).then((res) => {
      expect(res.status).to.be.oneOf([200, 201])
      expect(res.body).to.have.property('id')
      expect(res.body).to.have.property('hn')
      expect(res.body.hn).to.match(/^[0-9]{10}$/)

      const patientId = res.body.id
      const patientHn = res.body.hn

      // 2. Fetch patient profile
      cy.request({
        method: 'GET',
        url: `${backendUrl}/api/patients/${patientId}`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((getRes) => {
        expect(getRes.status).to.eq(200)
        expect(getRes.body.hn).to.eq(patientHn)
        expect(getRes.body.fullName).to.include(payload.firstName)
        expect(getRes.body.patientType).to.eq('Thai')
        expect(getRes.body.gender).to.eq('Male')
      })

      // 3. Search patient by HN
      cy.request({
        method: 'GET',
        url: `${backendUrl}/api/patients?query=${patientHn}`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((searchRes) => {
        expect(searchRes.status).to.eq(200)
        expect(searchRes.body.items).to.be.an('array')
        const match = searchRes.body.items.find((p) => p.hn === patientHn)
        expect(match).to.exist
      })
    })
  })
})
