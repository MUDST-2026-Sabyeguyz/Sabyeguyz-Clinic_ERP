describe('FAST01 appointment ordinary login', () => {
  const backend = Cypress.env('BACKEND_URL')
  const suffix = Date.now().toString()
  let token, patient, therapistId, serviceId, roomId, appointment
  const request = (method, path, body, status) => cy.then(() => cy.request({
    method, url: `${backend}${path}`, body, log: false, failOnStatusCode: false,
    headers: { Authorization: `Bearer ${token}` },
  })).then((response) => { expect(response.status, `${method} ${path}`).to.eq(status); return response.body })
  it('creates, rejects overlap, reloads, pages past twenty, then performs the existing visit actions', () => {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('admin')
    cy.get('input[autocomplete="current-password"]').type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.window().then((window) => { token = window.localStorage.getItem('authToken') })
    request('POST', '/api/users', { username: `appt_${suffix}`, password: Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'),
      role: 'THERAPIST', employeeCode: `AP-${suffix}`, fullName: `BrowserDoctor ${suffix}`, branchCode: '01' }, 201)
    request('GET', '/api/catalog/therapists', undefined, 200).then((items) => {
      therapistId = items.find((item) => item.fullName === `BrowserDoctor ${suffix}`).id
    })
    request('POST', '/api/services', { code: `AP-${suffix}`, name: `BrowserService ${suffix}`, defaultDurationMinutes: 30, price: 100 }, 201)
      .then((body) => { serviceId = body.id })
    request('POST', '/api/resources', { branchCode: '01', name: `BrowserRoom ${suffix}`, type: 'ROOM' }, 201)
      .then((body) => { roomId = body.id })
    request('POST', '/api/patients', { branchCode: '01', patientType: 'Foreigner', title: 'นาย',
      firstName: 'AppointmentBrowser', lastName: suffix, gender: 'Male', phone: '0891234567' }, 201)
      .then(() => request('POST', '/api/patients', { branchCode: '01', patientType: 'Foreigner', title: 'นาย',
        firstName: 'AppointmentBrowser', lastName: suffix, gender: 'Male', phone: '0891234568' }, 201))
      .then((body) => { patient = body })
    cy.visit('/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    // The backend searches individual name columns, and each test run has a unique last name.
    // The two records created in this run share the same rendered full name and differ by HN.
    cy.get('input[placeholder="กรอก HN หรือชื่อผู้ป่วย"]').type(suffix)
    cy.get('.patient-search-results button').should('have.length', 2)
    cy.then(() => cy.contains('.patient-search-results button', patient.hn).click())
    cy.contains('label', 'ประเภทบริการ').find('select').select(`BrowserService ${suffix}`)
    cy.contains('label', 'นักกายภาพ').find('select').select(`BrowserDoctor ${suffix}`)
    cy.contains('label', 'ห้อง').find('select').select(`BrowserRoom ${suffix}`)
    cy.intercept('POST', '**/api/appointments').as('createAppointment')
    cy.contains('button', /^บันทึก$/).click()
    cy.wait('@createAppointment').then(({ response }) => {
      expect(response.statusCode).to.eq(201); appointment = response.body
      expect(appointment.patient.id).to.eq(patient.id)
      expect(appointment.patient.hn).to.eq(patient.hn)
      expect(appointment.service.id).to.eq(serviceId)
      expect(appointment.room.id).to.eq(roomId)
      expect(new Date(appointment.endAt) - new Date(appointment.startAt)).to.eq(30 * 60000)
    })
    cy.contains('tbody tr', `AppointmentBrowser ${suffix}`).contains('a', 'ส่งต่อบริการ').click()
    cy.location('pathname').should('eq', '/staff/transactions')
    cy.location('search').should((search) => expect(search).to.contain(`appointmentId=${appointment.id}`))
    cy.get('.appointment-service-handoff').should(($handoff) => {
      expect($handoff.text()).to.contain(patient.fullName)
      expect($handoff.text()).to.contain(`HN ${patient.hn}`)
      expect($handoff.text()).to.contain(`BrowserService ${suffix}`)
    })
    cy.get('.appointment-service-handoff a').click()
    cy.location('pathname').should('eq', '/staff/services')
    cy.get('.card h2').should(($heading) => expect($heading.text()).to.contain(patient.fullName))
    cy.get('.card select').first().should('have.value', 'single')
    cy.get('.fields select').eq(1).should(($select) => expect($select.val()).to.eq(String(serviceId)))
    cy.reload()
    cy.get('.card h2').should(($heading) => expect($heading.text()).to.contain(patient.fullName))
    cy.get('.fields select').eq(1).should(($select) => expect($select.val()).to.eq(String(serviceId)))
    cy.then(() => request('POST', '/api/appointments', { branchCode: '01', patientId: patient.id,
      therapistId, serviceId, startAt: new Date(new Date(appointment.startAt).getTime() + 15 * 60000).toISOString(), roomId }, 409))
    cy.visit('/staff/appointments')
    cy.get('input[placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"]').type(suffix)
    cy.contains('tbody tr', `AppointmentBrowser ${suffix}`).should('contain', `BrowserRoom ${suffix}`)
    cy.then(() => {
      for (let i = 1; i <= 22; i++) request('POST', '/api/appointments', { branchCode: '01', patientId: patient.id,
        therapistId, serviceId, startAt: new Date(new Date(appointment.startAt).getTime() + i * 3600000).toISOString() }, 201)
    })
    cy.get('input[placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"]').clear().type(suffix)
    cy.contains('ทั้งหมด 23 นัดหมาย').should('be.visible')
    cy.get('tbody tr').should('have.length', 20)
    cy.contains('button', 'ถัดไป').click()
    cy.get('tbody tr').should('have.length', 3)
    cy.contains('หน้า 2 จาก 2').should('be.visible')
    cy.get('tbody tr').filter(`:contains("BrowserRoom ${suffix}")`).as('initialRow')
    cy.intercept('POST', '**/api/appointments/*/check-in').as('arrive')
    cy.get('@initialRow').find('select').select('ARRIVED')
    cy.wait('@arrive').its('response.statusCode').should('eq', 200)
    cy.intercept('POST', '**/api/appointments/*/start-service').as('start')
    cy.get('@initialRow').find('select').select('IN_SERVICE')
    cy.wait('@start').its('response.statusCode').should('eq', 200)
    cy.intercept('POST', '**/api/appointments/*/complete').as('complete')
    cy.get('@initialRow').find('select').select('COMPLETED')
    cy.wait('@complete').its('response.statusCode').should('eq', 200)
    cy.get('@initialRow').find('select option').should('have.length', 1)
    cy.get('@initialRow').contains('button', 'แก้ไข').should('be.disabled')
    cy.then(() => request('POST', `/api/appointments/${appointment.id}/check-in`, {}, 409))
    cy.then(() => request('GET', `/api/appointments/${appointment.id}`, undefined, 200)).then((body) => {
      expect(body.status).to.eq('COMPLETED')
      expect(body.patient.id).to.eq(patient.id)
      expect(body.patient.hn).to.eq(patient.hn)
      const evidence = Cypress.env('EVIDENCE_DIR')
      if (evidence) cy.writeFile(`${evidence}/browser-appointment-readback.json`, body, { log: false })
    })
  })
})
