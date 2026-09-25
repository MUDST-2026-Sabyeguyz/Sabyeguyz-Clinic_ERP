Cypress.Commands.add('api', (method, url, body, options = {}) => {
  return cy.get('@token').then((token) => cy.request({
    method, url, body,
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
    ...options,
  }))
})

Cypress.Commands.add('parallelApi', (requests) => {
  return cy.get('@token').then((token) => cy.task('parallelRequests', { token, requests }, { log: false }))
})

export function patientPayload(overrides = {}) {
  return {
    branchCode: 'RP', username: 'cypress-patient', email: 'cypress-patient@example.com',
    password: 'password123', title: 'นาย', fullName: 'สมชาย ใจดี', birthDate: '1990-01-01',
    gender: 'ชาย', phone: '0811111111', identityDocumentType: 'NATIONAL_ID',
    identityNumber: '1100000000101', address: 'กรุงเทพฯ', drugAllergy: false,
    foodAllergy: false, chronicDisease: '', emergencyContactName: 'ผู้ติดต่อ',
    emergencyContactRelationship: 'ญาติ', emergencyContactPhone: '0899999999',
    privacyConsent: true, ...overrides,
  }
}

Cypress.Commands.add('registerPatient', (overrides = {}) => {
  return cy.api('POST', '/api/patients', patientPayload(overrides)).then(({ status, body }) => {
    expect(status).to.equal(201)
    return body
  })
})

export function appointmentBody(fixtures, patientId, time = '09:00') {
  return {
    branchCode: 'RP', patientId, therapistId: fixtures.therapistId,
    serviceId: fixtures.serviceId, startAt: `${fixtures.tomorrow}T${time}:00+07:00`, note: 'นัดทดสอบ',
  }
}

export function saleBody(courseId, patientId, amount = 9000) {
  return {
    patientId, branchCode: 'RP', items: [{ type: 'COURSE', referenceId: courseId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'CASH', amount }],
  }
}
