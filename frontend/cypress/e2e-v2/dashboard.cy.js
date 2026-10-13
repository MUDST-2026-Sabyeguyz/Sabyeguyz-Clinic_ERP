/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const card = (title) => cy.contains('.summary-grid article', title)

describe('manager dashboard', () => {
  it('shows zeros when nothing has happened', () => {
    cy.visitAs('manager', '/staff/dashboard')
    cy.contains('h1', 'ภาพรวมแดชบอร์ด').should('be.visible')
    card('รายได้รวม').should('contain.text', '0 บาท')
    card('คอร์สที่กำลังใช้งาน').should('contain.text', '0 คอร์ส')
    cy.contains('ยังไม่มีข้อมูลผู้ป่วยในช่วงนี้').should('be.visible')
  })

  it('lists every appointment status, with the English labels', () => {
    cy.visitAs('manager', '/staff/dashboard')
    for (const label of ['Confirmed', 'Arrived', 'In service', 'Complete', 'Canceled', 'Rescheduled', 'No Show']) {
      cy.contains('.status-row span', label).should('exist')
    }
  })

  it('adds up the revenue of a single visit and a course', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        salespersonStaffId: this.fixtures.managerStaffId,
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 9000 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('รายได้รวม').should('contain.text', '9,800 บาท')
    card('คอร์สที่กำลังใช้งาน').should('contain.text', '1 คอร์ส')
    cy.get('.monthly-chart .month-group').should('have.length.at.least', 1)
    cy.get('.monthly-chart .bar-area i.course').first().invoke('attr', 'title').should('contain', '9,000')
    cy.get('.monthly-chart .bar-area i.visit').first().invoke('attr', 'title').should('contain', '800')
  })

  it('counts the patients seen by their gender', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/dashboard')
    card('ผู้ป่วยทั้งหมด').should('contain.text', '1 คน')
  })

  it('narrows the figures to one branch', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
    })
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date())
    cy.api('GET', `/api/dashboard/summary?from=${today.slice(0, 8)}01&to=${today}`).then(({ body }) => expect(JSON.stringify(body), 'what the backend reports for the month').to.contain('"totalRevenue":800'))
    cy.visitAs('manager', '/staff/dashboard')
    card('รายได้รวม').should('contain.text', '800 บาท')
    cy.contains('label', 'สาขา').find('select').select('02')
    card('รายได้รวม').should('contain.text', '0 บาท')
    cy.contains('label', 'สาขา').find('select').select('01')
    card('รายได้รวม').should('contain.text', '800 บาท')
  })
})
