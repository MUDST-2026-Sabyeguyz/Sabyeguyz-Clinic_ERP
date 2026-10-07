describe('Patient profile update contract', () => {
  const backendUrl = Cypress.env('BACKEND_URL') || 'http://127.0.0.1:8080'
  let suffix
  let patientOrdinal = 0
  let token
  let patientId
  let patientHn

  before(() => {
    cy.request({ log: false, failOnStatusCode: false,
      method: 'POST',
      url: `${backendUrl}/api/auth/login`,
      log: false,
      body: { username: 'admin', password: Cypress.env('BOOTSTRAP_ADMIN_PASSWORD') },
    }).then((response) => {
      expect(response.status).to.eq(200)
      token = response.body.token
    })
  })

  beforeEach(() => {
    suffix = `${Date.now().toString().slice(-6)}${patientOrdinal++}`
    cy.request({ log: false, failOnStatusCode: false,
      method: 'POST',
      url: `${backendUrl}/api/patients`,
      headers: { Authorization: `Bearer ${token}` },
      body: {
        branchCode: '01',
        title: 'นาย',
        fullName: `E2E Patient ${suffix}`,
        birthDate: '1990-01-01',
        gender: 'ชาย',
        phone: `080${suffix.slice(-7).padStart(7, '0')}`,
        identityDocumentType: 'NATIONAL_ID',
        identityNumber: `9${suffix.padStart(12, '0')}`,
        address: 'Synthetic E2E data',
        patientType: 'Thai',
      },
    }).then((response) => {
      expect(response.status).to.be.oneOf([200, 201])
      patientId = response.body.id
      patientHn = response.body.hn
    })
  })

  it('edits, saves, reloads, and preserves patient id, HN, and branch', () => {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('admin')
    cy.get('input[autocomplete="current-password"]').type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.visit(`/staff/patients/${patientId}`)
    cy.contains('h1', 'E2E Patient').should('be.visible')

    cy.contains('button', 'แก้ไขข้อมูล').click()
    cy.intercept('PUT', `**/api/patients/${patientId}`).as('updatePatient')
    cy.get('form.patient-edit label').contains(/^ชื่อ \*$/).find('input').clear().type('E2EEdited')
    cy.get('form.patient-edit button.primary').click()

    cy.wait('@updatePatient').then(({ request, response }) => {
      expect(request.body.branchCode).to.eq('01')
      expect(response.statusCode).to.eq(200)
      expect(response.body.id).to.eq(patientId)
      expect(response.body.hn).to.eq(patientHn)
      expect(response.body.branch.code).to.eq('01')
    })
    cy.contains('[role="status"]', 'บันทึกข้อมูลผู้ป่วยแล้ว').should('be.visible')
    cy.reload()
    cy.contains('h1', 'E2EEdited Patient').should('be.visible')

    cy.request({ log: false, failOnStatusCode: false,
      method: 'GET',
      url: `${backendUrl}/api/patients/${patientId}`,
      headers: { Authorization: `Bearer ${token}` },
    }).then((response) => {
      expect(response.body.id).to.eq(patientId)
      expect(response.body.hn).to.eq(patientHn)
      expect(response.body.branch.code).to.eq('01')
      expect(response.body.fullName).to.eq('E2EEdited Patient ' + suffix)
    })
  })

  it('loads and pages real receipt, course, and treatment history without cross-patient records', () => {
    let otherPatientId
    cy.request({ log: false, failOnStatusCode: false,
      method: 'POST',
      url: `${backendUrl}/api/patients`,
      headers: { Authorization: `Bearer ${token}` },
      body: {
        branchCode: '01', title: 'นางสาว', fullName: `E2E Other ${suffix}`, firstName: 'E2EOther', lastName: suffix,
        birthDate: '1990-01-01', gender: 'หญิง', phone: `081${suffix.slice(-7).padStart(7, '0')}`,
        identityDocumentType: 'NATIONAL_ID', identityNumber: `8${suffix.padStart(12, '0')}`,
        address: 'Synthetic E2E data', patientType: 'Thai',
      },
    }).then((response) => {
      expect(response.status).to.eq(201)
      otherPatientId = response.body.id
    })

    cy.request({ log: false, failOnStatusCode: false, method: 'GET', url: `${backendUrl}/api/courses`, headers: { Authorization: `Bearer ${token}` } }).then((response) => {
      const course = response.body.find((item) => item.code === 'CRS-BASIC-10')
      expect(course, 'seeded course service').to.exist
      for (let index = 0; index < 11; index += 1) {
        cy.request({ log: false, failOnStatusCode: false,
          method: 'POST', url: `${backendUrl}/api/course-purchases`, headers: { Authorization: `Bearer ${token}` },
          body: { patientId, courseId: course.id, branchCode: '01' },
        }).its('status').should('eq', 201)
      }
    })
    cy.request({ log: false, failOnStatusCode: false, method: 'GET', url: `${backendUrl}/api/treatments/services`, headers: { Authorization: `Bearer ${token}` } }).then((response) => {
      const service = response.body.find((item) => item.code === 'SRV-ASSESS')
      expect(service, 'seeded treatment service').to.exist
      for (let index = 0; index < 11; index += 1) {
        cy.request({ log: false, failOnStatusCode: false,
          method: 'POST', url: `${backendUrl}/api/treatments`, headers: { Authorization: `Bearer ${token}` },
          body: { patientId, serviceId: service.id, note: `Synthetic care ${patientId}-${index}` },
        }).its('status').should('eq', 201)
      }
    })

    cy.intercept('GET', `**/api/payments?*`).as('receiptHistory')
    cy.intercept('GET', `**/api/patients/${patientId}/courses/history?*`).as('courseHistory')
    cy.intercept('GET', `**/api/treatments/patients/${patientId}/history?*`).as('treatmentHistory')

    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('admin')
    cy.get('input[autocomplete="current-password"]').type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.visit(`/staff/patients/${patientId}`)
    cy.wait('@receiptHistory').then(({ request, response }) => {
      expect(request.headers.authorization).to.match(/^Bearer /)
      expect(response.statusCode).to.eq(200)
      expect(response.body).to.include({ page: 0, size: 10, totalItems: 11, totalPages: 2 })
      expect(response.body.items).to.have.length(10)
      expect(response.body.items.every((item) => item.patient.id === patientId)).to.eq(true)
    })
    cy.wait('@courseHistory').then(({ request, response }) => {
      expect(request.headers.authorization).to.match(/^Bearer /)
      expect(new URL(request.url).searchParams.get('page')).to.eq('0')
      expect(response.statusCode).to.eq(200)
      expect(response.body).to.include({ page: 0, size: 10, totalItems: 11, totalPages: 2 })
      expect(response.body.items).to.have.length(10)
      expect(response.body.items.every((item) => item.patient.id === patientId)).to.eq(true)
    })
    cy.wait('@treatmentHistory').then(({ request, response }) => {
      expect(request.headers.authorization).to.match(/^Bearer /)
      expect(new URL(request.url).searchParams.get('page')).to.eq('0')
      expect(response.statusCode).to.eq(200)
      expect(response.body).to.include({ page: 0, size: 10, totalItems: 11, totalPages: 2 })
      expect(response.body.items).to.have.length(10)
      expect(response.body.items.every((item) => item.patientId === patientId)).to.eq(true)
    })
    cy.contains('h1', `E2E Patient ${suffix}`).should('be.visible')
    cy.contains('RE-').should('be.visible')
    cy.contains('Physical Therapy 10-Session Course').should('be.visible')
    cy.contains(`Synthetic care ${patientId}-10`).should('be.visible')

    cy.intercept({ method: 'GET', pathname: '/api/payments', query: { patientId: String(patientId), page: '1' } }).as('receiptPageTwo')
    cy.intercept({ method: 'GET', pathname: `/api/patients/${patientId}/courses/history`, query: { page: '1' } }).as('coursePageTwo')
    cy.intercept({ method: 'GET', pathname: `/api/treatments/patients/${patientId}/history`, query: { page: '1' } }).as('treatmentPageTwo')
    cy.get('[aria-labelledby="receipt-history-heading"] .history-pagination button:last-child').click()
    cy.get('[aria-labelledby="course-history-heading"] .history-pagination button:last-child').click()
    cy.get('[aria-labelledby="care-history-heading"] .history-pagination button:last-child').click()
    for (const alias of ['@receiptPageTwo', '@coursePageTwo', '@treatmentPageTwo']) {
      cy.wait(alias).then(({ response }) => {
        expect(response.statusCode).to.eq(200)
        expect(response.body).to.include({ page: 1, size: 10, totalItems: 11, totalPages: 2 })
        expect(response.body.items).to.have.length(1)
      })
    }
    cy.get('[aria-labelledby="receipt-history-heading"]').contains('หน้า 2 / 2').should('be.visible')
    cy.get('[aria-labelledby="course-history-heading"]').contains('หน้า 2 / 2').should('be.visible')
    cy.get('[aria-labelledby="care-history-heading"]').contains('หน้า 2 / 2').should('be.visible')

    cy.then(() => {
      for (const route of [
        `/api/payments?patientId=${otherPatientId}&page=0&size=10`,
        `/api/patients/${otherPatientId}/courses/history?page=0&size=10`,
        `/api/treatments/patients/${otherPatientId}/history?page=0&size=10`,
      ]) {
        cy.request({ log: false, failOnStatusCode: false, url: `${backendUrl}${route}`, headers: { Authorization: `Bearer ${token}` } }).then((response) => {
          expect(response.status).to.eq(200)
          expect(response.body).to.include({ totalItems: 0, totalPages: 0 })
          expect(response.body.items).to.be.empty
        })
      }
    })

    cy.reload()
    cy.wait('@receiptHistory')
    cy.wait('@courseHistory')
    cy.wait('@treatmentHistory')
    cy.contains('h1', `E2E Patient ${suffix}`).should('be.visible')
    cy.contains('Physical Therapy 10-Session Course').should('be.visible')
  })
})
