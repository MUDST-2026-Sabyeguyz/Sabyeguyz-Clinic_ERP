describe('FAST01 ordinary login and patient field persistence', () => {
  const backend = Cypress.env('BACKEND_URL') || 'http://127.0.0.1:18083'
  const evidence = Cypress.env('EVIDENCE_DIR')
  const suffix = `${Date.now()}`
  let token
  let patientId
  let patientHn
  const groupName = `Browser Group ${suffix}`

  function login(username = 'admin') {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type(username)
    cy.get('input[autocomplete="current-password"]').type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
  }

  it('creates Foreigner with no DOB, edits and reloads through ordinary login and real CORS', () => {
    cy.clearLocalStorage()
    cy.intercept('POST', '**/api/auth/login').as('login')
    login()
    cy.wait('@login').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.role).to.eq('MANAGER')
      expect(response.headers['access-control-allow-origin']).to.eq('http://localhost:5173')
      token = response.body.token
      cy.request({ method: 'POST', url: `${backend}/api/master-data`, log: false, failOnStatusCode: false,
        headers: { Authorization: `Bearer ${token}` }, body: { category: 'Customer Group', name: groupName, active: true } }).then(response => { expect(response.status).to.eq(201) })
    })
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.visit('/staff/patients/new')
    cy.get('input[placeholder="ชื่อ"]').type('Browser')
    cy.get('input[placeholder="นามสกุล"]').type(`Foreign ${suffix}`)
    cy.get('input[placeholder="เบอร์โทร"]').type('0891234567')
    cy.get('select[aria-label="ประเภทผู้ป่วย"]').select('Foreigner')
    cy.get('input[aria-label="สัญชาติ"]').clear().type('Canada')
    cy.get('input[placeholder="หนังสือเดินทาง (ถ้ามี)"]').type(`P-${suffix}`)
    cy.contains('label', 'กลุ่มลูกค้า').find('select').select(groupName)
    cy.intercept('POST', '**/api/patients').as('create')
    cy.get('form.form-card').submit()
    cy.wait('@create').then(({ request, response }) => {
      expect(request.body.birthDate).to.eq(null)
      expect(request.body.patientType).to.eq('Foreigner')
      expect(response.statusCode).to.eq(201)
      patientId = response.body.id
      patientHn = response.body.hn
      cy.visit(`/staff/patients/${patientId}`)
    })
    cy.contains('h1', `Browser Foreign ${suffix}`).should('be.visible')
    cy.contains(groupName).should('be.visible')
    cy.contains('button', 'แก้ไขข้อมูล').click()
    cy.contains('label', /^ชื่อ\s*\*/).find('input').clear().type('Edited')
    cy.contains('label', /^นามสกุล/).find('input').clear().type(`Multiple Word ${suffix}`)
    cy.contains('label', 'ชื่อเล่น').find('input').type('Nickname')
    cy.contains('label', 'ที่อยู่').find('textarea').type('Stored browser address')
    cy.contains('label', 'เพศ').find('select').select('Female')
    cy.intercept('PUT', '**/api/patients/*').as('update')
    cy.contains('button', 'บันทึกข้อมูล').click()
    cy.wait('@update').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.id).to.eq(patientId)
      expect(response.body.hn).to.eq(patientHn)
      expect(response.body.branch.code).to.eq('01')
      expect(response.body.patientType).to.eq('Foreigner')
      expect(response.body.birthDate).to.eq(null)
    })
    cy.reload()
    cy.contains('h1', `Edited Multiple Word ${suffix}`).should('be.visible')
    cy.contains('Stored browser address').should('be.visible')
    cy.contains(`P-${suffix}`).should('be.visible')
    cy.contains('Nickname').should('be.visible')
    cy.contains(groupName).should('be.visible')
    cy.then(() => cy.request({ url: `${backend}/api/patients/${patientId}`, log: false,
      headers: { Authorization: `Bearer ${token}` } })).then(({ body }) => {
      expect(body.firstName).to.eq('Edited')
      expect(body.lastName).to.eq(`Multiple Word ${suffix}`)
      expect(body.birthDate).to.eq(null)
      expect(body.nationality).to.eq('Canada')
      if (evidence) cy.writeFile(`${evidence}/browser-field-readback.json`, {
        id: body.id, hn: body.hn, branchId: body.branchId, branchCode: body.branch.code,
        firstName: body.firstName, lastName: body.lastName, birthDate: body.birthDate,
        patientType: body.patientType, passport: body.identityNumber, address: body.address,
        nickname: body.nickname, customerGroup: body.customerGroup, nationality: body.nationality,
      }, { log: false })
    })
  })

  it('rejects bad login, clears the session on logout and guards manager routes for a real therapist', () => {
    cy.clearLocalStorage()
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('admin')
    cy.get('input[autocomplete="current-password"]').type('wrong', { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.get('[role="alert"]').should('contain', 'ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง')
    cy.get('input[autocomplete="current-password"]').clear().type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.window().then((window) => { token = window.localStorage.getItem('authToken') })
    cy.then(() => cy.request({ method: 'POST', url: `${backend}/api/users`, log: false, failOnStatusCode: false,
      headers: { Authorization: `Bearer ${token}` }, body: { username: `therapist_${suffix}`,
        password: Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), role: 'THERAPIST', employeeCode: `TH-${suffix}`,
        fullName: 'Browser Therapist', branchCode: '01', active: true } }).then(response => { expect(response.status).to.eq(201) }))
    cy.get('button.profile-trigger').click()
    cy.contains('a', 'ออกจากระบบ').click()
    cy.window().then((window) => {
      expect(window.localStorage.getItem('authToken') === null).to.eq(true)
      expect(window.localStorage.getItem('authUser') === null).to.eq(true)
    })
    cy.visit('/staff/settings')
    cy.location('pathname').should('eq', '/')
    login(`therapist_${suffix}`)
    cy.location('pathname').should('eq', '/staff/patients')
    cy.visit('/staff/settings')
    cy.location('pathname').should('eq', '/staff/patients')
    cy.window().then((window) => {
      expect(JSON.parse(window.localStorage.getItem('authUser')).role).to.eq('THERAPIST')
    })
  })
})
