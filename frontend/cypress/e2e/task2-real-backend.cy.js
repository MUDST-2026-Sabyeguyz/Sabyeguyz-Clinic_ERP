// Explicit opt-in: run against a fresh disposable seeded backend; writes synthetic transactions.
describe('Task 2 real backend checkout', () => {
  before(function () {
    cy.env(['TASK2_ISOLATED']).then(env => {
      if (env.TASK2_ISOLATED !== 'yes') this.skip()
    })
    const base = new URL(Cypress.config('baseUrl'))
    expect(base.hostname).to.equal('127.0.0.1')
    expect(['55173', '55174']).to.include(base.port)
  })
  for (const [index, username] of ['manager', 'therapist'].entries()) {
    it(`${username}: logs in, purchases a course, sells a service and opens its receipt`, () => {
      cy.visit('/')
      cy.get('input[autocomplete="username"]').type(username)
      cy.get('input[autocomplete="current-password"]').type(username === 'manager' ? 'manager123' : 'therapist123', { log: false })
      cy.intercept('POST', '/api/auth/login').as('login')
      cy.get('button[type="submit"]').click()
      cy.wait('@login').then(({ response }) => {
        expect(response.statusCode).to.equal(200)
        const user = response.body
        expect(user.staffId).to.be.a('number')
        const headers = { Authorization: `Bearer ${user.token}` }
        cy.request({ url: '/api/staff', headers }).then(({ body: staff }) => {
          const therapist = staff.find(person => person.branchCode === '01' && person.position === 'Therapist' && person.id === (username === 'therapist' ? user.staffId : 1))
          expect(therapist).to.exist
          cy.request({ url: '/api/catalog/services', headers }).then(({ body: services }) => {
            const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(Date.now() + 4 * 86400000))
            cy.request({ method: 'POST', url: '/api/appointments', headers, body: {
              branchCode: '01', therapistId: therapist.id, serviceId: services[0].id,
              patientType: 'CURRENT', patientId: 1,
              startAt: `${day}T${index ? '17:30' : '17:00'}:00+07:00`,
              endAt: `${day}T${index ? '18:00' : '17:30'}:00+07:00`,
            } }).then(({ body: booking }) => {
              for (const category of ['course', 'service']) {
                cy.visit(`/staff/services/sale?patientId=1${category === 'service' ? `&appointmentId=${booking.id}` : ''}`, {
                  onBeforeLoad(win) { win.localStorage.setItem('managerSelectedBranch', '01') },
                })
                cy.contains('h1', 'ขายบริการและคอร์ส', { timeout: 20000 }).should('be.visible')
                cy.get('.line-row select').first().select(category)
                cy.get('[aria-label="ค้นหารายการ"]').filter('input').click()
                cy.get('[role="listbox"] [role="option"]').eq(1).click()
                cy.get('[aria-label="ค้นหานักกายภาพ"]').filter('input').click()
                cy.contains('[role="option"]', therapist.fullName).click()
                if (category === 'service') {
                  cy.get('button[aria-label="ผู้รักษาจริง"]').click()
                  cy.contains('[role="option"]', therapist.fullName).click()
                }
                cy.get('.payment-group').first().find('button').first().click()
                cy.get('.payment-group').eq(1).find('button').first().click()
                cy.contains('button', 'ตรวจสอบรายการ').click()
                cy.contains('h1', 'ตรวจสอบและชำระเงิน').should('be.visible')
                cy.intercept('POST', '/api/payments').as('payment')
                cy.contains('button', 'ยืนยันรับชำระ').click()
                cy.wait('@payment').then(({ request, response: payment }) => {
                  expect(payment.statusCode).to.equal(201)
                  expect(request.body.items[0].type).to.equal(category === 'service' ? 'SERVICE' : 'COURSE')
                  expect(request.body).not.to.have.property('requestKey')
                  if (category === 'service') expect(request.body.treatingStaffId).to.equal(therapist.id)
                })
                cy.contains('h1', 'บันทึกการขายสำเร็จ').should('be.visible')
                cy.contains('button', 'ดูและพิมพ์ใบเสร็จ').click()
                cy.location('pathname').should('include', 'receipt')
                cy.get('body').should('not.contain.text', 'NaN')
              }
            })
          })
        })
      })
    })
  }
})
