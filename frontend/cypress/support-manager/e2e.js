/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued helpers. */
import { api, authenticate, fixtures, sessions } from '../support-therapist/helpers'

before(() => {
  expect(Cypress.config('baseUrl')).to.equal('http://localhost:5173')
})

beforeEach(() => {
  authenticate('manager')
  api('POST', '/__cypress/reset', undefined, 'manager').then(({ status, body }) => {
    expect(status, 'disposable Cypress v2 database reset').to.equal(200)
    Object.assign(fixtures, body)
  })
  cy.then(() => cy.request({
    method: 'POST',
    url: '/api/auth/login',
    log: false,
    body: { username: 'manager', password: 'manager123' },
  })).then(({ body }) => {
    expect(body.id).to.equal(sessions.manager.id)
    expect(body.token, 'Vite proxy reaches isolated backend').to.be.a('string')
    return cy.request({
      url: '/api/patients?size=1',
      headers: { Authorization: `Bearer ${sessions.manager.token}` },
      log: false,
    })
  }).its('body.totalItems').should('equal', 0)
})
