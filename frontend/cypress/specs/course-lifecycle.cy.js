describe('Course Usages, Ledger & Transfer Lifecycle', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  let sourcePatientId = null
  let destPatientId = null
  let courseCatalogId = null
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

      // Register Source Patient
      return cy.request({
        method: 'POST',
        url: `${backendUrl}/api/patients`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          branchCode: '01',
          customerType: 'Thai',
          title: 'นาย',
          firstName: `CourseSource${uniqueSuffix}`,
          lastName: `Test${uniqueSuffix}`,
          gender: 'Male',
          bloodGroup: 'AB',
          birthDate: '1990-01-01',
          phone: `08${uniqueSuffix.padStart(8, '3')}`,
          identityDocumentType: 'NATIONAL_ID',
          identityNumber: `1400000${uniqueSuffix.padStart(6, '3')}`,
          nationalId: `1400000${uniqueSuffix.padStart(6, '3')}`,
          address: '101 Course Ave',
          emergencyContactName: 'Person A',
          emergencyContactPhone: '0891112222',
          emergencyContactRelationship: 'Brother',
          allergies: 'None',
          chronicDiseases: 'None',
          privacyConsent: true,
        },
      })
    }).then((res) => {
      sourcePatientId = res.body.id

      // Register Dest Patient
      return cy.request({
        method: 'POST',
        url: `${backendUrl}/api/patients`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          branchCode: '01',
          customerType: 'Thai',
          title: 'นางสาว',
          firstName: `CourseDest${uniqueSuffix}`,
          lastName: `Test${uniqueSuffix}`,
          gender: 'Female',
          bloodGroup: 'O',
          birthDate: '1993-02-02',
          phone: `08${uniqueSuffix.padStart(8, '4')}`,
          identityDocumentType: 'NATIONAL_ID',
          identityNumber: `1500000${uniqueSuffix.padStart(6, '4')}`,
          nationalId: `1500000${uniqueSuffix.padStart(6, '4')}`,
          address: '102 Course Ave',
          emergencyContactName: 'Person B',
          emergencyContactPhone: '0893334444',
          emergencyContactRelationship: 'Sister',
          allergies: 'None',
          chronicDiseases: 'None',
          privacyConsent: true,
        },
      })
    }).then((res) => {
      destPatientId = res.body.id

      return cy.request({
        method: 'GET',
        url: `${backendUrl}/api/catalog/courses`,
        headers: { Authorization: `Bearer ${token}` },
      })
    }).then((crsRes) => {
      expect(crsRes.body).to.be.an('array').that.is.not.empty
      courseCatalogId = crsRes.body[0].id
    })
  })

  it('manages course session usage, ledger tracking and transfer', () => {
    // 1. Purchase course
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/course-purchases`,
      headers: { Authorization: `Bearer ${token}` },
      body: {
        patientId: sourcePatientId,
        courseId: courseCatalogId,
        branchCode: '01',
      },
    }).then((res) => {
      expect(res.status).to.be.oneOf([200, 201])
      expect(res.body).to.have.property('id')
      expect(res.body.remaining).to.be.greaterThan(0)

      const purchaseId = res.body.id
      const initialRemaining = res.body.remaining

      // 2. Use a session
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/course-usages`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          coursePurchaseId: purchaseId,
          note: 'First usage session',
        },
      }).then((useRes) => {
        expect(useRes.status).to.be.oneOf([200, 201])
        expect(useRes.body.remaining).to.eq(initialRemaining - 1)
      })

      // 3. Transfer 1 session to destination patient
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/course-transfers`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          sourcePurchaseId: purchaseId,
          destinationPatientId: destPatientId,
          quantity: 1,
          reason: 'Gifted to family member',
        },
      }).then((transferRes) => {
        expect(transferRes.status).to.be.oneOf([200, 201])
      })

      // 4. Verify transfer history
      cy.request({
        method: 'GET',
        url: `${backendUrl}/api/course-transfers?patientId=${sourcePatientId}`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((histRes) => {
        expect(histRes.status).to.eq(200)
        expect(histRes.body).to.be.an('array').that.is.not.empty
      })
    })
  })
})
