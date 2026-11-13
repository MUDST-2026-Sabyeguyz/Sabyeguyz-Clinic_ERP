/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const username = 'input[autocomplete="username"]'
const password = 'input[autocomplete="current-password"]'

describe('sign in, roles and sign out', () => {
  it('signs a manager in and lands on the dashboard', () => {
    cy.visit('/')
    cy.get(username).type('manager')
    cy.get(password).type('manager123')
    cy.get('button[type="submit"]').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.get('header.staff-header').should('contain.text', 'Manager')
  })

  it('signs a therapist in and lands on the patient list', () => {
    cy.visit('/')
    cy.get(username).type('therapist')
    cy.get(password).type('therapist123')
    cy.get('button[type="submit"]').click()
    cy.location('pathname').should('eq', '/staff/patients')
    cy.get('header.staff-header').should('contain.text', 'นักกายภาพบำบัด')
  })

  it('shows the real error for a wrong password and stays on the login page', () => {
    cy.visit('/')
    cy.get(username).type('manager')
    cy.get(password).type('not-the-password')
    cy.get('button[type="submit"]').click()
    cy.get('[role="alert"]').should('be.visible').and('contain.text', 'ไม่ถูกต้อง')
    cy.location('pathname').should('eq', '/')
  })

  it('keeps the submit button disabled until both fields are filled', () => {
    cy.visit('/')
    cy.get('button[type="submit"]').should('be.disabled')
    cy.get(username).type('manager')
    cy.get('button[type="submit"]').should('be.disabled')
    cy.get(password).type('manager123')
    cy.get('button[type="submit"]').should('be.enabled')
  })

  it('gives the manager the full menu', () => {
    cy.visitAs('manager', '/staff/patients')
    cy.get('nav[aria-label="เมนูเจ้าหน้าที่คลินิก"]').within(() => {
      for (const label of ['แดชบอร์ด', 'ผู้ป่วย', 'นัดหมาย', 'บริการ', 'ธุรกรรม', 'บุคลากร', 'รายงาน', 'ตั้งค่า']) {
        cy.contains('a', label).should('exist')
      }
    })
  })

  it('gives the therapist only the daily-work menu', () => {
    cy.visitAs('therapist', '/staff/patients')
    cy.get('nav[aria-label="เมนูเจ้าหน้าที่คลินิก"]').within(() => {
      for (const label of ['ผู้ป่วย', 'นัดหมาย', 'บริการ', 'ธุรกรรม']) cy.contains('a', label).should('exist')
      for (const label of ['แดชบอร์ด', 'บุคลากร', 'รายงาน', 'ตั้งค่า']) cy.contains('a', label).should('not.exist')
    })
  })

  it('sends a therapist who opens a manager-only page back to the patient list', () => {
    for (const path of ['/staff/dashboard', '/staff/reports', '/staff/commission/tiers', '/staff/commission/closing']) {
      cy.visitAs('therapist', path)
      cy.location('pathname').should('eq', '/staff/patients')
    }
  })

  it('sends a visitor without a session from a staff page to the login page', () => {
    cy.visit('/staff/patients')
    cy.location('pathname').should('eq', '/')
    cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
  })

  it('signs out through the profile menu and the staff pages are closed again', () => {
    cy.visitAs('manager', '/staff/patients')
    cy.get('button[aria-label="เปิดเมนูโปรไฟล์"]').click()
    cy.contains('a', 'ออกจากระบบ').click()
    cy.location('pathname').should('eq', '/')
    cy.visit('/staff/patients')
    cy.location('pathname').should('eq', '/')
  })

  it('signs the user out when the session token is no longer accepted', () => {
    cy.visitAs('manager', '/staff/patients')
    cy.window().then((win) => win.localStorage.setItem('authToken', 'not-a-valid-token'))
    cy.visit('/staff/patients')
    cy.location('pathname').should('eq', '/')
  })
})
