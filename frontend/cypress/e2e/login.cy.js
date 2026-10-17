/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
describe('staff login', () => {
  it('requires credentials before submission', () => {
    cy.visit('/')

    cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
    cy.get('input[autocomplete="username"]').should('be.visible')
    cy.get('input[autocomplete="current-password"]').should('be.visible')
    cy.get('button[type="submit"]').should('be.disabled')
  })

  it('shows the real API error for invalid credentials', () => {
    const username = `cypress-invalid-${Date.now()}`
    cy.intercept('POST', '/api/auth/login').as('loginRequest')
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type(username)
    cy.get('input[autocomplete="current-password"]').type('invalid-password')
    cy.get('button[type="submit"]').click()

    cy.wait('@loginRequest').then(({ request, response }) => {
      expect(request.body).to.deep.equal({
        username,
        password: 'invalid-password',
      })
      expect(response?.statusCode).to.equal(400)
      expect(response?.body.message).to.equal('ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง')
    })

    cy.get('[role="alert"]')
      .should('be.visible')
      .and('contain.text', 'ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง')
  })
})
