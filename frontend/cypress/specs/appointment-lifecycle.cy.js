describe('Appointment Lifecycle & State Machine', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://localhost:8080'
  let token = ''
  let patientId = null
  let staffId = null
  let serviceId = null
  let roomId = null
  const uniqueSuffix = Date.now().toString().slice(-6)

  before(() => {
    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      body: { username: 'admin', password: 'Admin@123456' },
    }).then((res) => {
      token = res.body.token

      // Register a patient for appointment
      return cy.request({
        method: 'POST',
        url: `${backendUrl}/api/patients`,
        headers: { Authorization: `Bearer ${token}` },
        body: {
          branchCode: '01',
          customerType: 'Thai',
          title: 'นาย',
          firstName: `ApptPatient${uniqueSuffix}`,
          lastName: `Test${uniqueSuffix}`,
          gender: 'Male',
          bloodGroup: 'B',
          birthDate: '1992-03-15',
          phone: `08${uniqueSuffix.padStart(8, '1')}`,
          identityDocumentType: 'NATIONAL_ID',
          identityNumber: `1200000${uniqueSuffix.padStart(6, '1')}`,
          nationalId: `1200000${uniqueSuffix.padStart(6, '1')}`,
          address: '456 Clinic Lane',
          emergencyContactName: 'Emergency Person',
          emergencyContactPhone: '0891234567',
          emergencyContactRelationship: 'Spouse',
          allergies: 'None',
          chronicDiseases: 'None',
          privacyConsent: true,
        },
      })
    }).then((res) => {
      patientId = res.body.id

      // Fetch lookups
      return cy.request({
        method: 'GET',
        url: `${backendUrl}/api/lookups/therapists?branchCode=01`,
        headers: { Authorization: `Bearer ${token}` },
      })
    }).then((staffRes) => {
      expect(staffRes.body).to.be.an('array').that.is.not.empty
      staffId = staffRes.body[0].id

      return cy.request({
        method: 'GET',
        url: `${backendUrl}/api/lookups/services`,
        headers: { Authorization: `Bearer ${token}` },
      })
    }).then((srvRes) => {
      expect(srvRes.body).to.be.an('array').that.is.not.empty
      serviceId = srvRes.body[0].id
    })
  })

  it('books appointment and transitions through CheckIn, StartService and Complete', () => {
    // 1. Create appointment
    const dynamicDay = ((Number(uniqueSuffix) % 28) + 1).toString().padStart(2, '0')
    const dynamicHour = ((Number(uniqueSuffix) % 8) + 9).toString().padStart(2, '0')
    const appointmentBody = {
      branchCode: '01',
      patientId: patientId,
      therapistId: staffId,
      serviceId: serviceId,
      startAt: `2027-05-${dynamicDay}T${dynamicHour}:00:00+07:00`,
      note: 'Routine Physical Therapy',
    }

    cy.request({
      method: 'POST',
      url: `${backendUrl}/api/appointments`,
      headers: { Authorization: `Bearer ${token}` },
      body: appointmentBody,
    }).then((res) => {
      expect(res.status).to.be.oneOf([200, 201])
      expect(res.body).to.have.property('id')
      expect(res.body.status).to.eq('CONFIRMED')

      const apptId = res.body.id

      // 2. Check-in
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/appointments/${apptId}/check-in`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((checkInRes) => {
        expect(checkInRes.status).to.eq(200)
        expect(checkInRes.body.status).to.eq('ARRIVED')
      })

      // 3. Start service
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/appointments/${apptId}/start-service`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((startRes) => {
        expect(startRes.status).to.eq(200)
        expect(startRes.body.status).to.eq('IN_SERVICE')
      })

      // 4. Complete
      cy.request({
        method: 'POST',
        url: `${backendUrl}/api/appointments/${apptId}/complete`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((compRes) => {
        expect(compRes.status).to.eq(200)
        expect(compRes.body.status).to.eq('COMPLETED')
      })
    })
  })
})
