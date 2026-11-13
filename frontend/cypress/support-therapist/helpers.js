/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued helpers. */
const API = 'http://127.0.0.1:18080'
// Cypress builds support and specs as separate bundles; module-local objects do not cross that boundary.
Cypress.therapistState ??= { fixtures: {}, sessions: {} }
export const fixtures = Cypress.therapistState.fixtures
export const sessions = Cypress.therapistState.sessions
let sequence = 0

export function api(method, path, body, role = 'therapist', options = {}) {
  return cy.then(() => cy.request({ method, url: `${API}${path}`, body,
    failOnStatusCode: false, log: false,
    headers: sessions[role] ? { Authorization: `Bearer ${sessions[role].token}` } : {}, ...options }))
}

export function authenticate(role) {
  return cy.request({ method: 'POST', url: `${API}/api/auth/login`, log: false,
    body: { username: role, password: role === 'manager' ? 'manager123' : 'therapist123' },
  }).then(({ status, body }) => {
    expect(status).to.equal(200)
    sessions[role] = body
  })
}

export function visit(path, role = 'therapist') {
  return cy.then(() => cy.visit(path, { onBeforeLoad(win) {
    win.localStorage.clear()
    if (sessions[role]) {
      win.localStorage.setItem('authToken', sessions[role].token)
      win.localStorage.setItem('authUser', JSON.stringify(sessions[role]))
      // These fixtures and THP-001 belong to branch 01; keep Manager E2E scoped to that branch.
      if (role === 'manager') win.localStorage.setItem('managerSelectedBranch', '01')
    }
  } }))
}

export function patient(overrides = {}) {
  return cy.then(() => api('POST', '/api/patients', {
    branchCode: '01', customerType: 'Thai', title: 'นาย', firstName: 'สมชาย', lastName: 'ทดสอบ',
    gender: 'Male', phone: '0812345678', identityDocumentType: 'NATIONAL_ID',
    identityNumber: String(1100000200000 + ++sequence), birthDate: '1990-01-01',
    nationality: 'ไทย', address: 'ที่อยู่ทดสอบ Therapist', ...overrides,
  }, 'manager').then(({ status, body }) => {
    expect(status).to.equal(201)
    return body
  }))
}

export function sale(patientId, overrides = {}) {
  return cy.then(() => api('POST', '/api/payments', {
    patientId, branchCode: '01',
    items: [{ type: 'SERVICE', referenceId: fixtures.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }], ...overrides,
  }).then(({ status, body }) => { expect(status).to.equal(201); return body }))
}

export function book(patientId, overrides = {}) {
  return cy.then(() => api('POST', '/api/appointments', {
    patientId, branchCode: '01', therapistId: fixtures.therapistId, serviceId: fixtures.serviceId,
    startAt: `${fixtures.tomorrow}T09:00:00+07:00`, ...overrides,
  }).then(({ status, body }) => { expect(status).to.equal(201); return body }))
}

export function pick(label, text) {
  cy.get(`button[aria-label="${label}"]`).click()
  return cy.get(`[role="listbox"][aria-label="${label}"]`).contains('[role="option"]', text).click()
}

export function datePicker(container, date = '1990-01-01') {
  const [year, month, day] = date.split('-').map(Number)
  cy.get(container).find('.date-trigger').click()
  cy.get('[role="dialog"][aria-label="เลือกวันที่"] select[aria-label="เลือกปี"]').select(String(year))
  cy.get('[role="dialog"][aria-label="เลือกวันที่"] select[aria-label="เลือกเดือน"]').select(String(month - 1))
  cy.get('[role="dialog"][aria-label="เลือกวันที่"] .days').contains('button', new RegExp(`^${day}$`)).click()
}
