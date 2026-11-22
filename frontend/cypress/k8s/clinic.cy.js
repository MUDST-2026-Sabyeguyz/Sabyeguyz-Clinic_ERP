describe('production app through local Kubernetes Ingress', () => {
  it('serves Vue and reports healthy backend and database', () => {
    cy.request('/actuator/health').its('body.status').should('eq', 'UP')
    cy.visit('/')
    cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
    cy.get('button[type="submit"]').should('be.disabled')
  })

  it('rejects invalid credentials using the real API', () => {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('k8s-invalid-user')
    cy.get('input[autocomplete="current-password"]').type('invalid-password', { log: false })
    cy.get('button[type="submit"]').click()
    cy.get('[role="alert"]').should('contain.text', 'ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง')
  })

  it('logs in a real manager and opens persisted patient data after a route refresh', () => {
    cy.task('managerCredentials', null, { log: false })
      .then((credentials) => {
        cy.visit('/')
        cy.get('input[autocomplete="username"]').type(credentials.username)
        cy.get('input[autocomplete="current-password"]').type(credentials.password, { log: false })
        cy.get('button[type="submit"]').click()
      })
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.visit('/staff/patients')
    cy.contains('K8S').should('be.visible')
    cy.reload()
    cy.location('pathname').should('eq', '/staff/patients')
    cy.contains('K8S').should('be.visible')
  })
})
