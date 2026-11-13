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

Cypress.Commands.add('configureBookingWindows', () => {
  return cy.api('GET', '/api/branches').then(({ body }) => {
    return cy.wrap(
        body.filter((branch) => ['01', '02'].includes(branch.branchCode))
    ).each((branch) => {
      return cy.api('PUT', `/api/branches/${branch.id}`, {
        branchCode: branch.branchCode,
        name: branch.name,
        address: branch.address,
        phone: branch.phone,
        bookingOpenTime: '09:00',
        bookingCloseTime: '18:00',
        defaultAppointmentMinutes: 60,
      }).its('status').should('equal', 200)
    })
  })
})

let serviceBookingSequence = 0

beforeEach(() => {
  serviceBookingSequence = 0
})

Cypress.Commands.add('createServiceAppointment', (fx, patientId, options = {}) => {
  const sequence = serviceBookingSequence++
  const day = Math.floor(sequence / 18)
  const slot = sequence % 18
  const start = new Date(
      new Date(`${fx.tomorrow}T09:00:00+07:00`).getTime()
      + day * 24 * 60 * 60 * 1000
      + slot * 30 * 60 * 1000
  )
  const end = new Date(start.getTime() + 30 * 60 * 1000)

  return cy.configureBookingWindows().then(() => {
    return cy.api('POST', '/api/appointments', {
      patientId,
      branchCode: options.branchCode ?? '01',
      therapistId: options.therapistId ?? fx.therapistId,
      startAt: start.toISOString(),
      endAt: end.toISOString(),
    })
  }).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    expect(body.status).to.equal('UNCONFIRMED')
    return body
  })
})

Cypress.Commands.add('useCourseSession', (courseId, extra = {}) => {
  if (extra.appointmentId != null) {
    return cy.api('POST', '/api/course-usages', {
      coursePurchaseId: courseId,
      ...extra,
    })
  }

  return cy.get('@fixtures').then((fx) => {
    return cy.api('GET', `/api/course-purchases/${courseId}`)
        .then(({ status, body }) => {
          expect(status).to.equal(200)

          const patientId = extra.patientId ?? body.purchase.purchaser.id

          return cy.createServiceAppointment(fx, patientId, {
            branchCode: body.purchase.branchCode,
          }).then((appointment) => {
            return cy.api('POST', '/api/course-usages', {
              coursePurchaseId: courseId,
              patientId,
              appointmentId: appointment.id,
              ...extra,
            })
          })
        })
  })
})
