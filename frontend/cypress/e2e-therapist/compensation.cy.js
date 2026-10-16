/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai assertions. */
import { api, visit, patient, fixtures } from '../support-therapist/helpers'

const month = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date()).slice(0, 7)
const shiftMonth = (offset) => {
  const date = new Date(`${month}-01T12:00:00Z`)
  date.setUTCMonth(date.getUTCMonth() + offset)
  return date.toISOString().slice(0, 7)
}
const nextMonth = shiftMonth(1)
const money = (amount) => Math.round(Number(amount) * 100) / 100
const manager = (method, path, body, expected = 200) =>
  api(method, path, body, 'manager').then(({ status, body: result }) => {
    expect(status, JSON.stringify(result)).to.equal(expected)
    return result
  })
const prepare = (period = month) => manager('POST', `/api/commission/payments/prepare?month=${period}`)
const ownPayment = (period = month) => prepare(period).then((rows) => {
  const row = rows.find((entry) => entry.staffId === fixtures.therapistId)
  expect(row, 'nonempty therapist payroll fixture').not.to.equal(undefined)
  return row
})

// Manager creates actual sales/usages. Constants below are business oracles:
// 10,000 at the seeded 5% tier / 13 visits = 38.46; fixed treatment fee = 45.
const earnings = () => {
  let course
  let person
  manager('GET', '/api/commission/treatment-fees').then((rules) => {
    expect(rules).to.have.length.greaterThan(0)
    return manager('PUT', `/api/commission/treatment-fees/${rules[0].id}`, {
      feeType: 'Fixed', feeValue: 45,
    })
  })
  return manager('POST', '/api/courses', {
    code: 'PTCOMP13', name: 'Therapist compensation 13 visits',
    price: 10000, sessions: 13, bonusSessions: 0, validityDays: 365,
  }, 201).then((template) => patient().then((created) => {
    person = created
    return manager('POST', '/api/course-purchases', {
      patientId: person.id, courseId: template.id, branchCode: '01',
      caseOwnerStaffId: fixtures.therapistId,
    }, 201)
  })).then((purchased) => {
    course = purchased
    return manager('POST', '/api/course-usages', {
      coursePurchaseId: course.id, treatingStaffId: fixtures.therapistId,
    })
  }).then(() => manager('POST', '/api/payments', {
    patientId: person.id, branchCode: '01',
    salespersonStaffId: fixtures.therapistId, treatingStaffId: fixtures.therapistId,
    items: [{ type: 'SERVICE', referenceId: fixtures.singleVisitServiceId, quantity: 1 }],
    payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
  }, 201)).then(() => ({ course, person }))
}

const lockEarnings = () => earnings().then((context) =>
  manager('POST', `/api/commission/closings/${month}-01`).then(() =>
    ownPayment().then((payment) => {
      expect(money(payment.courseCommission)).to.equal(38.46)
      expect(money(payment.singleVisitFee)).to.equal(45)
      expect(money(payment.substituteFee)).to.equal(0)
      expect(money(payment.net)).to.equal(83.46)
      expect(payment.pendingSessions).to.equal(0)
      return { ...context, payment }
    })))
const pay = (payment, reference = 'PT-E2E-TRANSFER') =>
  manager('POST', `/api/commission/payments/${payment.id}/close`).then(() =>
    manager('POST', `/api/commission/payments/${payment.id}/pay`, {
      paidDate: `${payment.month}-15`, reference,
    }))

describe('Therapist compensation with actual earnings', () => {
  it('renders missing detail and source IDs safely without source actions or editable fields', () => {
    for (const path of [
      'commission/c99999999', 'commission/c99999999/source',
      'treatment/v99999999', 'treatment/v99999999/source', 'adjustment/99999999',
    ]) {
      visit(`/staff/compensation/${path}?month=${month}`)
      cy.get('.therapist-compensation-main').within(() => {
        cy.contains('ยังไม่มีข้อมูลค่าตอบแทนสำหรับบัญชีนี้').should('be.visible')
        cy.get('.therapist-primary-action').should('not.exist')
        cy.get('input, textarea, button[type="submit"]').should('not.exist')
        cy.get('[role="alert"]').should('not.exist')
      })
    }
  })

  it('keeps unclosed course earnings unknown and displays the pending visit', () => {
    earnings().then(() => ownPayment()).then((payment) => {
      expect(payment.courseCommission).to.equal(null)
      expect(payment.net).to.equal(null)
      expect(payment.pendingSessions).to.equal(1)
      expect(money(payment.singleVisitFee)).to.equal(45)
    })
    visit(`/staff/compensation?month=${month}`)
    cy.get('[aria-label="สรุปค่าตอบแทน"]').within(() => {
      cy.contains('dt', 'Course Commission').next().should('contain', 'รอคำนวณ')
      cy.contains('dt', 'Net Payout').next().should('contain', 'รอคำนวณ')
      cy.contains('dt', 'ค่ามือ').next().should('contain', '฿45.00')
    })
    cy.get('.therapist-pending-note').should('contain', '1 ครั้ง')
  })

  it('shows independently calculated totals, switches month and preserves tab selection', () => {
    lockEarnings()
    visit(`/staff/compensation?month=${month}`)
    cy.get('[aria-label="สรุปค่าตอบแทน"]').within(() => {
      cy.contains('dt', 'Course Commission').next().should('contain', '฿38.46')
      cy.contains('dt', 'ค่ามือ').next().should('contain', '฿45.00')
      cy.contains('dt', 'Net Payout').next().should('contain', '฿83.46')
    })
    cy.get('[aria-label="เมนูค่าตอบแทน"]').contains('a', 'ค่ามือ').click()
    cy.get('button[aria-label="เดือนถัดไป"]').click()
    cy.location('search').should('contain', `month=${nextMonth}`).and('contain', 'tab=treatment')
    cy.contains('ยังไม่มีค่ามือในเดือนนี้').should('be.visible')
    cy.get('button[aria-label="เดือนก่อน"]').click()
    cy.location('search').should('contain', `month=${month}`)
  })

  ;[
    { tab: 'commission', amount: '฿38.46', detail: 'รายละเอียดค่าคอมมิชชัน', action: 'ดูรายการขายต้นทาง', source: 'รายละเอียดรายการขายต้นทาง' },
    { tab: 'treatment', amount: '฿45.00', detail: 'รายละเอียดค่ามือ', action: 'ดูรายการรักษาต้นทาง', source: 'รายละเอียดรายการรักษาต้นทาง' },
  ].forEach(({ tab, amount, detail, action, source }) => {
    it(`opens a real ${tab} line, calculation detail and source`, () => {
      lockEarnings()
      visit(`/staff/compensation?month=${month}&tab=${tab}`)
      // A nonempty row is required. Empty my-lines with nonzero totals is an app defect.
      cy.get('a.therapist-line-row').should('have.length', 1).and('contain', amount).click()
      cy.contains('h2', detail).should('be.visible')
      cy.get('.therapist-calculation-list').should('contain', amount)
      if (tab === 'commission') {
        cy.get('.therapist-calculation-list').should('contain', '฿10,000.00').and('contain', '5%')
      }
      cy.contains('a', action).click()
      cy.contains('h1', source).should('be.visible')
      cy.get('[aria-label="ข้อมูลผู้ป่วยและบริการ"]').should('contain', 'สมชาย').and('contain', 'ทดสอบ')
      cy.get('.therapist-source-compensation-card').should('contain', tab === 'commission' ? '฿10,000.00' : amount)
      cy.contains('a', tab === 'commission' ? 'กลับรายละเอียดค่าคอมมิชชัน' : 'กลับรายละเอียดค่าตอบแทน').click()
      cy.contains('h2', detail).should('be.visible')
    })
  })

  it('shows paid history and a later approved adjustment without changing the paid amount', () => {
    lockEarnings().then(({ course, payment }) => pay(payment).then(() =>
      manager('GET', `/__cypress/commission/courses/${course.id}/allocations`)).then((allocations) => {
      expect(allocations).to.have.length(1)
      return manager('POST', '/api/commission/adjustments', {
        staffId: fixtures.therapistId, allocationId: allocations[0].allocation_id,
        originalMonth: month, appliedMonth: nextMonth, amount: 50,
        reason: 'PT E2E approved additional compensation',
      }, 201)
    }).then((adjustment) => {
      manager('POST', `/api/commission/adjustments/${adjustment.id}/approve`)
      manager('POST', `/api/commission/adjustments/${adjustment.id}/applications`, { month: nextMonth }, 201)
        .then((application) => manager('POST', `/api/commission/adjustment-applications/${application.id}/approve`, {}))
      ownPayment(nextMonth).then((next) => {
        expect(money(next.adjustment)).to.equal(50)
        expect(money(next.net)).to.equal(50)
      })
      api('GET', `/api/commission/my-payments?month=${month}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.have.length(1)
        expect(body[0].status).to.equal('PAID')
        expect(money(body[0].net)).to.equal(83.46)
      })
      visit(`/staff/compensation/payments?month=${month}`)
      cy.get('.therapist-payment-row').should('have.length', 1)
        .and('contain', '฿83.46').and('contain', 'PT-E2E-TRANSFER').and('contain', 'Paid')
      cy.contains('a', 'ดู Adjustment เดือนนี้').click()
      cy.location('pathname').should('equal', `/staff/compensation/adjustment/${adjustment.id}`)
      cy.contains('PT E2E approved additional compensation').should('be.visible')
      cy.get('.therapist-adjustment-tiles').should('contain', '฿50.00')
      cy.get('[role="progressbar"]').should('have.attr', 'aria-valuenow', '0')
      cy.get('.therapist-adjustment-history [role="row"]').should('have.length', 2)
    }))
  })

  it('isolates another therapist from existing paid earnings and rejects manager payroll mutations', () => {
    lockEarnings().then(({ payment }) => pay(payment).then(() => {
      api('GET', `/api/commission/my-payments?month=${month}&staffId=${fixtures.therapistId}`, undefined, 'therapist2')
        .then(({ status, body }) => { expect(status).to.equal(200); expect(body).to.deep.equal([]) })
      api('GET', `/api/commission/my-lines?month=${month}&staffId=${fixtures.therapistId}`, undefined, 'therapist2')
        .then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.lines).to.deep.equal([])
          expect(money(body.singleVisitFee)).to.equal(0)
          expect(money(body.courseCommission)).to.equal(0)
        })
      api('POST', `/api/commission/payments/${payment.id}/pay`).its('status').should('equal', 403)
      api('POST', '/api/commission/adjustments', {}).its('status').should('equal', 403)
      visit(`/staff/compensation?month=${month}&tab=commission`, 'therapist2')
      cy.contains('ยังไม่มีค่าคอมมิชชันในเดือนนี้').should('be.visible')
      cy.get('a.therapist-line-row').should('not.exist')
      visit('/staff/compensation/payments', 'therapist2')
      cy.contains('ยังไม่มีงวดที่จ่ายแล้ว').should('be.visible')
      cy.get('.therapist-payment-row').should('not.exist')
      visit(`/staff/compensation/commission/c999999/source?month=${month}`, 'therapist2')
      cy.contains('ยังไม่มีข้อมูลค่าตอบแทนสำหรับบัญชีนี้').should('be.visible')
    }))
  })
})
