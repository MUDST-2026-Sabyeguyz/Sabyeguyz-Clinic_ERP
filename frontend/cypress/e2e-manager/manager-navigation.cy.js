/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai assertions. */
import { visit } from '../support-therapist/helpers'

// Pages a Manager uses. Each must open for the signed-in Manager (no redirect to login or to another page) and show the
// staff header.
// [name, path the Manager opens, path the app ends on]. Two legacy commission paths are redirects by design.
const pages = [
  ['dashboard', '/staff/dashboard', '/staff/dashboard'],
  ['patient search', '/staff/patients', '/staff/patients'],
  ['patient registration', '/staff/patients/new', '/staff/patients/new'],
  ['appointment list', '/staff/appointments', '/staff/appointments'],
  ['appointment calendar', '/staff/appointments/calendar', '/staff/appointments/calendar'],
  ['new appointment', '/staff/appointments/new', '/staff/appointments/new'],
  ['services menu', '/staff/services', '/staff/services'],
  ['transactions', '/staff/transactions', '/staff/transactions'],
  ['course extension requests', '/staff/courses/extensions', '/staff/courses/extensions'],
  ['commission overview', '/staff/commission', '/staff/commission'],
  ['commission tiers (redirects to settings)', '/staff/commission/tiers', '/staff/settings/commission-tiers'],
  ['commission closing (redirects to the overview)', '/staff/commission/closing', '/staff/commission'],
  ['reports', '/staff/reports', '/staff/reports'],
]

describe('Manager navigation', () => {
  pages.forEach(([name, path, expected]) => {
    it(`opens ${name} (${path})`, () => {
      visit(path, 'manager')
      cy.location('pathname').should('eq', expected)
      cy.get('header.staff-header').should('be.visible')
      // a late redirect (for example to login) would change the path after the page settles
      cy.wait(400)
      cy.location('pathname').should('eq', expected)
    })
  })

  it('keeps the Manager out of the therapist-only compensation page', () => {
    visit('/staff/compensation', 'manager')
    cy.location('pathname').should('not.eq', '/staff/compensation')
    cy.location('pathname').should('match', /^\/staff\//)
    cy.get('header.staff-header').should('be.visible')
  })

  it('sends an anonymous visitor away from a staff page', () => {
    cy.visit('/staff/dashboard', { onBeforeLoad: (win) => win.localStorage.clear() })
    cy.location('pathname').should('not.eq', '/staff/dashboard')
  })
})
