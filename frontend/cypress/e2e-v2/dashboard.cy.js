/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const card = (title) => cy.contains('.summary-card', title)

function sellVisitWithAppointment(fx, patient, branchCode = '01') {
  return cy.api('POST', '/api/appointments', {
    branchCode,
    patientId: patient.id,
    therapistId: fx.therapistId,
    serviceId: fx.singleVisitServiceId,
    startAt: `${fx.tomorrow}T09:00:00+07:00`,
    endAt: `${fx.tomorrow}T10:00:00+07:00`,
  }).then(({ status: apptStatus, body: appointment }) => {
    expect(apptStatus, JSON.stringify(appointment)).to.equal(201)
    return cy.api('POST', '/api/payments', {
      patientId: patient.id,
      branchCode,
      appointmentId: appointment.id,
      treatingStaffId: fx.therapistId,
      paymentLabelCode: 'FULL',
      items: [{ type: 'SERVICE', referenceId: fx.singleVisitServiceId, quantity: 1 }],
      payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
    }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
  })
}

describe('manager dashboard', () => {
  it('shows registrations without pretending that the patient attended', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/dashboard')
    card('ลูกค้าใหม่').should('contain.text', '1 คน')
    card('ผู้ป่วยในช่วงนี้').should('contain.text', '1 คน')
    card('เข้ารับบริการ').should('contain.text', '0 ครั้ง')
    card('ปรับสิทธิ์คอร์ส (ทั้งคลินิก)').should('contain.text', '0 รายการ')
  })

  it('shows template update event counts and labels their clinic-wide scope', function () {
    cy.api('GET', `/api/courses/${this.fixtures.courseId}`).then(({ body: template }) => {
      cy.api('PUT', `/api/courses/${template.id}`, {
        code: template.code,
        name: 'แม่แบบที่แก้แล้ว',
        price: template.price,
        sessions: template.sessions,
        bonusSessions: template.bonusSessions,
        validityDays: template.validityDays,
      }).its('status').should('equal', 200)
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('ปรับสิทธิ์คอร์ส (ทั้งคลินิก)').should('contain.text', '1 รายการ')
  })

  it('shows an unpaid sale separately from cash received', function () {
    cy.viewport(1280, 900)
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id,
        branchCode: '01',
        salespersonStaffId: this.fixtures.managerStaffId,
        paymentLabelCode: 'DEPOSIT',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
      }).its('status').should('equal', 201)
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('ยอดขายตามวันที่ขาย').should('contain.text', '9,000 บาท')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '0 บาท')
  })

  it('shows zeros when nothing has happened', () => {
    cy.visitAs('manager', '/staff/dashboard')
    cy.contains('h1', 'ภาพรวมแดชบอร์ด').should('be.visible')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '0 บาท')
    card('คอร์สที่กำลังใช้งาน').should('not.exist')
    card('คอร์สที่ซื้อ').should('contain.text', '0 คอร์ส')
    cy.contains('ยังไม่มียอดรับชำระในช่วงนี้').should('be.visible')
  })

  it('lists appointment status summaries on the dashboard', () => {
    cy.visitAs('manager', '/staff/dashboard')
    for (const label of ['ยังไม่ยืนยัน', 'ยืนยันแล้ว', 'ยกเลิก']) {
      cy.contains('.status-row span', label).should('exist')
    }
  })

  it('adds up the revenue of a single visit and a course', function () {
    cy.openBookingHours('01')
    cy.registerPatient().then((patient) => {
      sellVisitWithAppointment(this.fixtures, patient)
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        salespersonStaffId: this.fixtures.managerStaffId,
        paymentLabelCode: 'FULL',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 9000 }],
      }).then(({ status, body }) => expect(status, JSON.stringify(body)).to.equal(201))
    })
    cy.visitAs('manager', '/staff/dashboard')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '9,800 บาท')
    card('คอร์สที่ซื้อ').should('contain.text', '1 คอร์ส')
    cy.get('.monthly-chart .month-group').should('have.length.at.least', 1)
    cy.get('.monthly-chart .bar-area i.course').first().invoke('attr', 'title').should('contain', '9,000')
    cy.get('.monthly-chart .bar-area i.visit').first().invoke('attr', 'title').should('contain', '800')
  })

  it('keeps a new registration separate from the patients who actually attended', () => {
    cy.registerPatient()
    cy.visitAs('manager', '/staff/dashboard')
    card('ลูกค้าใหม่').should('contain.text', '1 คน')
    card('ผู้ป่วยในช่วงนี้').should('contain.text', '1 คน')
    card('เข้ารับบริการ').should('contain.text', '0 ครั้ง')
  })

  it('narrows the figures to one branch', function () {
    cy.openBookingHours('01')
    cy.registerPatient().then((patient) => sellVisitWithAppointment(this.fixtures, patient))
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date())
    cy.api('GET', `/api/dashboard/summary?from=${today.slice(0, 8)}01&to=${today}`).then(({ body }) => expect(JSON.stringify(body), 'what the backend reports for the month').to.contain('"totalRevenue":800'))
    cy.visitAs('manager', '/staff/dashboard')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '800 บาท')
    cy.chooseBranch('เลือกสาขา', '02')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '0 บาท')
    cy.chooseBranch('เลือกสาขา', '01')
    card('ยอดรับชำระตามวันที่รับเงิน').should('contain.text', '800 บาท')
  })
})
