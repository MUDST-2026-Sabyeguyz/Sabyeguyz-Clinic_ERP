describe('Sales, Receipt Issuance & Cancellation Lifecycle', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  let patientId = null
  let courseServiceId = null
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

      return cy.request({
        method: 'POST',
        url: `${backendUrl}/api/patients`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          branchCode: '01',
          patientType: 'Thai',
          title: 'นาง',
          firstName: `SalePatient${uniqueSuffix}`,
          lastName: `Test${uniqueSuffix}`,
          gender: 'Female',
          bloodGroup: 'A',
          birthDate: '1988-08-18',
          phone: `08${uniqueSuffix.padStart(8, '2')}`,
          identityDocumentType: 'NATIONAL_ID',
          identityNumber: `1300000${uniqueSuffix.padStart(6, '2')}`,
          nationalId: `1300000${uniqueSuffix.padStart(6, '2')}`,
          address: '789 Commercial St',
          emergencyContactName: 'Family Member',
          emergencyContactPhone: '0891234567',
          emergencyContactRelationship: 'Parent',
          allergies: 'None',
          chronicDiseases: 'None',
          privacyConsent: true,
        },
      })
    }).then((res) => {
      patientId = res.body.id

      return cy.request({
        method: 'GET',
        url: `${backendUrl}/api/catalog/courses`,
        headers: { Authorization: `Bearer ${token}` },
      })
    }).then((crsRes) => {
      expect(crsRes.body).to.be.an('array').that.is.not.empty
      courseServiceId = crsRes.body[0].id
    })
  })

  it('issues a receipt with valid RE format and permits voiding', () => {
    // 1. Issue sale/receipt
    const saleRequest = {
      patientId: patientId,
      branchCode: '01',
      items: [
        {
          type: 'COURSE',
          referenceId: courseServiceId,
          quantity: 1,
        },
      ],
      payments: [
        {
          paymentMethodCode: 'Cash',
          amount: 10000.0,
        },
      ],
    }

    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/payments`,
      headers: { Authorization: `Bearer ${token}` },
      body: saleRequest,
    }).then((res) => {
      expect(res.status).to.be.oneOf([200, 201])
      expect(res.body).to.have.property('id')
      expect(res.body).to.have.property('receiptNo')
      expect(res.body.receiptNo).to.match(/^RE-[0-9]{7}$/)

      const receiptId = res.body.id

      // 2. Fetch receipt details
      cy.request({
        method: 'GET',
        url: `${backendUrl}/api/receipts/${receiptId}`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((rcptRes) => {
        expect(rcptRes.status).to.eq(200)
        expect(rcptRes.body.receiptNo).to.eq(res.body.receiptNo)
      })

      // 3. Void receipt
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/payments/${receiptId}/reverse`,
        headers: { Authorization: `Bearer ${token}` },
        body: { reason: 'Customer requested refund within policy' },
      }).then((voidRes) => {
        expect(voidRes.status).to.eq(200)
      })
    })
  })
})
