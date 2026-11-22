/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const card = (title) => cy.contains('.summary-card', title)

describe('manager dashboard', () => {
  it('shows registrations without pretending that the patient attended', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/dashboard')
    card('ลูกค้าใหม่').should('contain.text', '1 คน')
    card('ลูกค้าเก่า').should('contain.text', '0 คน')
    card('ผู้ป่วยที่มาใช้บริการ').should('contain.text', '0 คน')
    card('ต่ออายุคอร์ส').should('contain.text', '0 รายการ')
  })

  it('shows template update event counts and labels their clinic-wide scope', function () {
    cy.api('GET', `/api/courses/${this.fixtures.courseId}`).then(({ body: template }) => {
      cy.api('PUT', `/api/courses/${template.id}`, { ...template, name: 'แม่แบบที่แก้แล้ว' }).its('status').should('equal', 200)
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('อัปเดตแม่แบบคอร์ส').should('contain.text', '1 รายการ').and('contain.text', 'ทั้งคลินิก')
  })

  it('shows an unpaid sale separately from cash received', function () {
    cy.viewport(1280, 900)
    cy.registerPatient().then(patient => {
      cy.api('POST', '/api/payments', { patientId: patient.id, branchCode: '01',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }] })
        .its('status').should('equal', 201)
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('ยอดขายสุทธิ').should('contain.text', '800.00 บาท')
    card('เงินรับรวม').should('contain.text', '0.00 บาท')
    cy.get('.daily-sales-chart').should('contain.text', '800')
    // Global smooth scrolling otherwise makes stitched/element screenshots crop mid-scroll.
    cy.document().then(doc => doc.documentElement.style.setProperty('scroll-behavior', 'auto', 'important'))
    cy.get('.activity-summary').scrollIntoView({ duration: 0 }).screenshot('p8-dashboard-metrics-cards')
    cy.get('.daily-sales-chart').scrollIntoView({ duration: 0 }).screenshot('p8-dashboard-daily-sales')
  })

  it('shows zeros when nothing has happened', () => {
    cy.visitAs('manager', '/staff/dashboard')
    cy.contains('h1', 'ภาพรวมแดชบอร์ด').should('be.visible')
    card('เงินรับรวม').should('contain.text', '0.00 บาท')
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
    card('เงินรับรวม').should('contain.text', '9,800.00 บาท')
    card('คอร์สที่กำลังใช้งาน').should('contain.text', '1 คอร์ส')
    cy.get('.monthly-chart .month-group').should('have.length.at.least', 1)
    cy.get('.monthly-chart .bar-area i.course').first().invoke('attr', 'title').should('contain', '9,000')
    cy.get('.monthly-chart .bar-area i.visit').first().invoke('attr', 'title').should('contain', '800')
  })

  it('keeps a new registration separate from the patients who actually attended', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/dashboard')
    card('ลูกค้าใหม่').should('contain.text', '1 คน')
    card('ผู้ป่วยที่มาใช้บริการ').should('contain.text', '0 คน')
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
    card('เงินรับรวม').should('contain.text', '800.00 บาท')
    cy.chooseBranch('กรองตามสาขา', '02')
    card('เงินรับรวม').should('contain.text', '0.00 บาท')
    cy.chooseBranch('กรองตามสาขา', '01')
    card('เงินรับรวม').should('contain.text', '800.00 บาท')
  })
})
