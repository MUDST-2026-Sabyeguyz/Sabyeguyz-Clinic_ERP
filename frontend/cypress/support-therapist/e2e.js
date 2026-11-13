/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in hooks. */
import { api, authenticate, fixtures, sessions } from './helpers'

before(() => {
  expect(Cypress.config('baseUrl')).to.equal('http://localhost:5173')
})

beforeEach(() => {
  authenticate('manager')
  api('POST', '/__cypress/reset', undefined, 'manager').then(({ status, body }) => {
    expect(status, 'isolated cypress-v2 fixture endpoint').to.equal(200)
    Object.assign(fixtures, body)
  })
  authenticate('therapist')
  authenticate('therapist2')
  // Catch a Vite proxy pointing at another backend before a browser workflow runs.
  cy.then(() => cy.request({ url: '/api/auth/login', method: 'POST', log: false,
    body: { username: 'therapist', password: 'therapist123' },
  })).then(({ body }) => {
    expect(body.id).to.equal(sessions.therapist.id)
    expect(body.token, 'proxy backend uses same signing key').to.be.a('string')
    return cy.request({ url: '/api/patients?size=1',
      headers: { Authorization: `Bearer ${sessions.manager.token}` }, log: false })
  }).its('body.totalItems').should('equal', 0)
})
