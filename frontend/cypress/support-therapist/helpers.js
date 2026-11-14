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

// The reset leaves the branch without booking hours, and the API now refuses appointments until the branch has a window.
export function openBookingHours() {
  return api('GET', '/api/branches', undefined, 'manager').then(({ body }) => {
    const branch = body.find((item) => item.branchCode === '01')
    return api('PUT', `/api/branches/${branch.id}`, {
      branchCode: branch.branchCode, name: branch.name, address: branch.address, phone: branch.phone,
      bookingOpenTime: '08:00', bookingCloseTime: '20:00', defaultAppointmentMinutes: 30,
    }, 'manager').then(({ status }) => expect(status, 'open branch booking hours').to.equal(200))
  })
}

// The reset also clears every service duration. Production services carry one, so give the single visit services theirs
// (the appointment forms read it to size a booking).
export function setServiceDurations() {
  return api('GET', '/api/services', undefined, 'manager').then(({ body }) => {
    for (const service of body) {
      const minutes = /60/.test(service.code) ? 60 : 30
      api('PUT', `/api/services/${service.id}`, {
        code: service.code, name: service.name, price: service.price, defaultDurationMinutes: minutes,
      }, 'manager').its('status').should('equal', 200)
    }
  })
}

// The checkout starts with an empty line; pick a single visit service (type "รายครั้ง", then the 30 minute service priced 800).
export function chooseSingleVisit() {
  pick('ประเภทการชำระ', 'รายครั้ง')
  cy.get('input[aria-label="ค้นหารายการ"]').click().type('30 นาที')
  cy.get('[role="listbox"][aria-label="ค้นหารายการ"]').contains('[role="option"]', '30 นาที').click()
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

// A single visit sale belongs to an appointment (selling one without it is refused by the database), so book the next
// free half hour slot tomorrow for the patient and sell through that appointment, like the checkout page does.
// One counter hands out the next free half hour tomorrow (08:00, 08:30 ... 18:30) so appointments booked by different helpers
// in the same test never collide for the therapist.
let nextSlotIndex = 0
export function nextSlot() {
  const index = nextSlotIndex++ % 22
  const time = `${String(8 + Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`
  return slot(fixtures.tomorrow, time)
}

export function sale(patientId, overrides = {}) {
  return book(patientId, nextSlot()).then((appointment) =>
    api('POST', '/api/payments', {
      patientId, branchCode: '01', appointmentId: appointment.id,
      items: [{ type: 'SERVICE', referenceId: fixtures.singleVisitServiceId, quantity: 1 }],
      paymentLabelCode: 'PAID', payments: [{ paymentMethodCode: 'PromptPay', amount: 800 }], ...overrides,
    }).then(({ status, body }) => { expect(status, JSON.stringify(body)).to.equal(201); return body }))
}

// The API requires both ends of an appointment: a half hour slot starting at `time` (clinic time, +07:00).
export function slot(date, time, minutes = 30) {
  const startAt = `${date}T${time}:00+07:00`
  const clock = new Date(new Date(startAt).getTime() + (minutes + 7 * 60) * 60000)
  const two = (n) => String(n).padStart(2, '0')
  const endAt = `${clock.getUTCFullYear()}-${two(clock.getUTCMonth() + 1)}-${two(clock.getUTCDate())}T${two(clock.getUTCHours())}:${two(clock.getUTCMinutes())}:00+07:00`
  return { startAt, endAt }
}

export function book(patientId, overrides = {}) {
  return cy.then(() => api('POST', '/api/appointments', {
    patientId, branchCode: '01', therapistId: fixtures.therapistId, serviceId: fixtures.serviceId,
    ...slot(fixtures.tomorrow, '09:00'), ...overrides,
  }).then(({ status, body }) => { expect(status).to.equal(201); return body }))
}

// Course usage now needs an appointment of the patient (the API rejects a usage without `appointmentId`): book the next
// half hour slot tomorrow for the patient, then use the course through that appointment.
export function useCourse(role, patientId, body) {
  return book(patientId, nextSlot()).then((appointment) =>
    api('POST', '/api/course-usages', { ...body, appointmentId: appointment.id }, role))
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
