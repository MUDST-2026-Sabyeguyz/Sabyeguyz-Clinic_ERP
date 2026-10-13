Cypress.Commands.add('api', (method, url, body, options = {}) => {
  return cy.get('@token').then((token) => cy.request({
    method, url, body,
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
    ...options,
  }))
})

// A request made with another user's token (or none), for permission checks.
Cypress.Commands.add('apiAs', (token, method, url, body, options = {}) => {
  return cy.request({
    method, url, body,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
    ...options,
  })
})

Cypress.Commands.add('login', (username, password) => {
  return cy.request({ method: 'POST', url: '/api/auth/login', body: { username, password }, failOnStatusCode: false })
})

Cypress.Commands.add('parallelApi', (requests) => {
  return cy.get('@token').then((token) => cy.task('parallelRequests', { token, requests }, { log: false }))
})

let patientSequence = 1000
export function patientPayload(overrides = {}) {
  return {
    branchCode: '01', customerType: 'Thai', title: 'นาย', firstName: 'สมชาย', lastName: 'ใจดี', gender: 'Male',
    phone: '0811111111', identityDocumentType: 'NATIONAL_ID', identityNumber: String(1100000000000 + patientSequence++),
    birthDate: '1990-01-01', address: 'กรุงเทพฯ', ...overrides,
  }
}

Cypress.Commands.add('registerPatient', (overrides = {}) => {
  return cy.api('POST', '/api/patients', patientPayload(overrides)).then(({ status, body }) => {
    expect(status).to.equal(201)
    return body
  })
})
