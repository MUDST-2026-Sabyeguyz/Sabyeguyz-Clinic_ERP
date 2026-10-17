/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
import { api, visit } from '../support-therapist/helpers'

describe('Therapist authentication and access boundaries', () => {
  it('AUTH-01 logs in through the real form and retains session on reload', () => {
    visit('/', 'anonymous')
    cy.get('button[type="submit"]').should('be.disabled')
    cy.get('input[autocomplete="username"]').type('therapist')
    cy.get('input[autocomplete="current-password"]').type('therapist123', { log: false })
    cy.get('button[type="submit"]').click()
    cy.location('pathname').should('equal', '/staff/patients')
    cy.get('h1').should('contain.text', 'ค้นหาผู้ป่วย')
    cy.reload()
    cy.get('h1').should('contain.text', 'ค้นหาผู้ป่วย')
    cy.get('nav[aria-label="เมนูเจ้าหน้าที่คลินิก"]').within(() => {
      cy.contains('a', 'นัดหมาย').should('be.visible')
      cy.contains('a', 'บริการ').should('be.visible')
      cy.contains('a', 'ธุรกรรม').should('be.visible')
      cy.contains('a', 'คอมมิชชัน').should('be.visible')
      cy.contains('a', 'การตั้งค่า').should('not.exist')
    })
  })

  it('AUTH-02 rejects a wrong password without creating a session', () => {
    visit('/', 'anonymous')
    cy.get('input[autocomplete="username"]').type('therapist')
    cy.get('input[autocomplete="current-password"]').type('wrong-password', { log: false })
    cy.get('button[type="submit"]').click()
    cy.get('[role="alert"]').should('be.visible')
    cy.location('pathname').should('equal', '/')
    cy.window().then((win) => expect(win.localStorage.getItem('authToken')).to.equal(null))
  })

  it('AUTH-03 logout removes the token and prevents returning to staff pages', () => {
    visit('/staff/patients')
    cy.get('h1').should('contain.text', 'ค้นหาผู้ป่วย')
    cy.get('button[aria-label="เปิดเมนูโปรไฟล์"]').click()
    cy.contains('a', 'ออกจากระบบ').click()
    cy.location('pathname').should('equal', '/')
    cy.window().then((win) => {
      expect(win.localStorage.getItem('authToken')).to.equal(null)
      // The current header leaves the cached display profile in localStorage;
      // authorization is revoked by removing the token.
      expect(win.localStorage.getItem('authUser')).not.to.equal(null)
    })
    cy.visit('/staff/services')
    cy.location('pathname').should('equal', '/')
  })

  for (const path of ['/staff/patients', '/staff/appointments', '/staff/appointments/calendar',
    '/staff/services', '/staff/courses', '/staff/transactions', '/staff/compensation']) {
    it(`AUTH-04 anonymous cannot access ${path}`, () => {
      visit(path, 'anonymous')
      cy.location('pathname').should('equal', '/')
      cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
    })
  }

  const forbidden = ['/staff/select-branch', '/staff/dashboard', '/staff/reports',
    '/staff/reports/transaction/tier-round', '/staff/settings', '/staff/commission',
    '/staff/commission/tiers', '/staff/commission/closing', '/staff/commission/compensation-settings',
    '/staff/commission/courses/1', '/staff/commission/staff/1', '/staff/commission/adjustments',
    '/staff/commission/adjustments/new', ...['success', 'schedule', 'select-schedule', 'closed',
      'finalized', 'reopen', 'reopen-success', 'reopen-history', 'review', 'payment-success']
      .map((suffix) => `/staff/commission/adjustments/1/${suffix}`), '/staff/commission/adjustments/1']
  forbidden.forEach((path) => {
    it(`AUTH-05 therapist is redirected from ${path}`, () => {
      visit(path)
      cy.location('pathname').should('equal', '/staff/patients')
      cy.get('h1').should('contain.text', 'ค้นหาผู้ป่วย')
    })
  })

  it('AUTH-06 backend independently rejects manager-only operations', () => {
    api('POST', '/api/branches', { branchCode: '99', name: 'Forbidden', address: '-', phone: '-' })
      .its('status').should('equal', 403)
    api('POST', '/api/users', { username: 'forbidden', password: 'Password123!', role: 'MANAGER',
      employeeCode: 'PT-FORBIDDEN', fullName: 'ทดสอบ สิทธิ์', branchCode: '01', phone: '0812345678' })
      .its('status').should('equal', 403)
    api('GET', '/api/patients', undefined, 'anonymous').its('status').should('equal', 401)
  })

  it('AUTH-07 therapist can read compensation formula without manager controls', () => {
    visit('/staff/commission/formula')
    cy.get('h1').should('contain.text', 'สูตรคำนวณค่าตอบแทน')
    cy.get('main').should('contain.text', '45.00')
    cy.get('main button[type="submit"]').should('not.exist')
  })

  it('AUTH-08 legacy billing and login URLs resolve to their supported pages', () => {
    visit('/staff/billing')
    cy.location('pathname').should('equal', '/staff/services')
    visit('/staff/login', 'anonymous')
    cy.location('pathname').should('equal', '/')
    cy.get('h1').should('contain.text', 'เข้าสู่ระบบ')
  })
})
