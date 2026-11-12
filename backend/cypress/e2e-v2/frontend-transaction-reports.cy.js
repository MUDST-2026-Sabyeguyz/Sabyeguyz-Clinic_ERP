const frontendUrl = Cypress.env('frontendUrl') || 'http://127.0.0.1:5173'
const fullRange = '?from=2000-01-01&to=2099-12-31&branchCode=01'

function signInThroughUi(username = 'manager', password = 'manager123') {
  cy.visit(frontendUrl, { onBeforeLoad: (window) => window.localStorage.clear() })
  cy.get('input').first().type(username)
  cy.get('input[type="password"]').type(password)
  cy.contains('button', /เข้าสู่ระบบ|sign in/i).click()
}

function createCollection(patientId, courseId, key, amount = '10.00') {
  return cy.get('@token').then((token) => cy.request({
    method: 'POST',
    url: '/api/course-payment-collections',
    body: {
      patientId,
      branchCode: '01',
      items: [{ courseId, amount: Number(amount) }],
      paymentLabelCode: 'INSTALLMENT',
      paymentMethodCode: 'PromptPay',
      paymentAmount: Number(amount),
    },
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': key },
    failOnStatusCode: false,
  })).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
}

describe('authenticated frontend transaction reports and collections', () => {
  it('renders backend results, applies filters, totals every page, and exports the complete CSV', function () {
    cy.registerPatient({ firstName: 'รายงาน', lastName: 'ทดสอบหลายหน้า' }).then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        paymentLabelCode: 'FULL',
      }).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)
        let chain = cy.wrap(null)
        for (let index = 0; index < 13; index++) {
          chain = chain.then(() => createCollection(patient.id, purchase.id,
            `ui-report-${patient.id}-${index}`, '10.00'))
        }
        return chain.then(() => patient)
      })
    }).then((patient) => {
      cy.api('GET', `/api/reports/payments-detail${fullRange}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        const rows = body.rows.filter((row) => row.patientId === patient.id)
        expect(rows).to.have.length(13)
        expect(rows.reduce((sum, row) => sum + row.amount, 0)).to.equal(130)
        expect(body.total).to.be.at.least(130)
      })
      signInThroughUi()
      cy.visit(`${frontendUrl}/staff/reports?report=payment`)
      cy.get('input[placeholder="ค้นหาชื่อผู้ป่วยหรือ HN"]').clear().type(patient.hn)
      cy.contains('button', 'แสดงรายงาน').click()
      cy.contains('13 รายการ')
      cy.contains('หน้า 1 จาก 2')
      cy.get('[data-testid="report-total"]').should('contain', '130.00')
      cy.contains('button', 'ส่งออกรายงาน CSV').click()
      const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date())
      cy.readFile(`cypress/downloads/sabyeguyz-payment-${today}.csv`, { timeout: 15000 })
        .should('contain', patient.hn)
        .and('contain', 'รวมทั้งหมด')
        .and('contain', '130.00')
        .and('contain', 'RE')
        .then((csv) => {
          const lines = csv.replace(/^\uFEFF/, '').split(/\r?\n/)
          // The filter metadata also contains HN: restrict to data rows by receipt prefix.
          const dataRows = lines.filter((line) => /^RE[^,]*,/.test(line))
          expect(dataRows, 'CSV includes every UI page').to.have.length(13)
          expect(new Set(dataRows.map((line) => line.split(',')[0])).size).to.equal(13)
          dataRows.forEach((line) => {
            expect(line).to.contain(patient.hn)
            expect(line).to.match(/,10\.00$/)
          })
          expect(lines.filter((line) => line.startsWith('รวมทั้งหมด,'))).to.deep.equal([
            'รวมทั้งหมด,,,,,,,,130.00',
          ])
        })
      cy.get('input[placeholder="ค้นหาชื่อผู้ป่วยหรือ HN"]').clear().type('HN-NOT-FOUND')
      cy.contains('button', 'แสดงรายงาน').click()
      cy.contains('ไม่พบข้อมูลตามเงื่อนไขที่เลือก')
      cy.contains('button', 'ส่งออกรายงาน CSV').should('be.disabled')
    })
  })

  it('shows a backend filter error and enforces role restrictions on both UI and API', function () {
    signInThroughUi()
    cy.visit(`${frontendUrl}/staff/reports?report=payment`)
    cy.get('form[aria-label="ตัวกรองรายงาน"] select').first().then(($select) => {
      const option = document.createElement('option')
      option.value = 'NO-SUCH-BRANCH'
      option.textContent = 'สาขาที่ไม่มีจริง'
      $select[0].appendChild(option)
    })
    cy.get('form[aria-label="ตัวกรองรายงาน"] select').first().select('NO-SUCH-BRANCH')
    cy.contains('button', 'แสดงรายงาน').click()
    cy.get('[role="alert"]').should('be.visible')
    cy.login('therapist', 'therapist123').then(({ status, body }) => {
      expect(status).to.equal(200)
      cy.apiAs(body.token, 'GET', `/api/reports/payments-detail${fullRange}`).its('status').should('equal', 403)
    })
    signInThroughUi('therapist', 'therapist123')
    cy.visit(`${frontendUrl}/staff/reports?report=payment`)
    cy.location('pathname').should('not.equal', '/staff/reports')
  })

  it('prevents duplicate confirmation clicks and preserves collection invariants', function () {
    cy.registerPatient({ firstName: 'รับเงิน', lastName: 'คลิกซ้ำ' }).then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id,
        courseId: this.fixtures.courseId,
        branchCode: '01',
        paymentLabelCode: 'FULL',
      }).then(({ status, body: purchase }) => {
        expect(status).to.equal(201)
        cy.api('GET', `/api/reports/sales-detail${fullRange}&patient=${encodeURIComponent(patient.hn)}`).then(({ status: beforeStatus, body: beforeSales }) => {
          expect(beforeStatus).to.equal(200)
          const initialSales = beforeSales.rows.length
          cy.api('GET', `/api/reports/payments-detail${fullRange}&patient=${encodeURIComponent(patient.hn)}`).then(({ status: beforePaymentStatus, body: beforePayments }) => {
          expect(beforePaymentStatus).to.equal(200)
          const initialPayments = beforePayments.rows.length
          signInThroughUi()
          cy.visit(`${frontendUrl}/staff/patients/${patient.id}/course-payments`)
          cy.get('input[type="checkbox"]').first().check()
          cy.get('input[type="number"]').first().clear().type('25.00')
          cy.contains('button', 'ตรวจสอบก่อนยืนยัน').click()
          cy.intercept('POST', '**/api/course-payment-collections', (request) => {
            request.continue((response) => { response.setDelay(800) })
          }).as('collection')
          cy.contains('button', 'ยืนยันรับเงินและออกใบเสร็จ').click()
          cy.contains('button', 'ยืนยันรับเงินและออกใบเสร็จ').click({ force: true })
          cy.wait('@collection').then(({ response }) => expect(response?.statusCode).to.equal(201))
          cy.get('@collection.all').should('have.length', 1)
          cy.contains('รับเงินสำเร็จ')
          cy.get('button').contains('ยืนยันรับเงินและออกใบเสร็จ').should('not.exist')
          cy.api('GET', `/api/reports/sales-detail${fullRange}&patient=${encodeURIComponent(patient.hn)}`).then(({ status: afterSalesStatus, body: afterSales }) => {
            expect(afterSalesStatus).to.equal(200)
            expect(afterSales.rows).to.have.length(initialSales)
          })
          cy.api('GET', `/api/reports/payments-detail${fullRange}&patient=${encodeURIComponent(patient.hn)}`)
            .then(({ status: reportStatus, body }) => {
              expect(reportStatus).to.equal(200)
              expect(body.rows).to.have.length(initialPayments + 1)
              const collection = body.rows.filter((row) => row.source === 'COURSE_COLLECTION')
              expect(collection).to.have.length(1)
              expect(collection[0].amount).to.equal(25)
              expect(body.total).to.equal(beforePayments.total + 25)
            })
          })
        })
      })
    })
  })
})
