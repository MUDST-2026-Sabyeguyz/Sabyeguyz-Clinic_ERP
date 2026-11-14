/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued tests. */
import { api, visit, patient, fixtures, sale, useCourse } from '../support-therapist/helpers'

// Cancelling a sale is done by the Manager (a therapist gets 403 from the API), so the cancellation flows run as Manager.
function openSale(saved) {
  visit('/staff/transactions', 'manager')
  cy.get('input[aria-label="ค้นหารายการ"]').type(saved.receiptNo)
  cy.get('.transaction-table tbody tr').should('have.length', 1)
  cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
}

describe('Therapist transactions', () => {
  it('filters sales by receipt and patient, opens details and preserves a sale when backing out', () => {
    patient().then((p) => sale(p.id).then((saved) => {
      visit('/staff/transactions', 'manager')
      cy.get('input[aria-label="ค้นหารายการ"]').type('NO-SUCH-RECEIPT')
      cy.contains('ไม่พบรายการ').should('be.visible')
      cy.get('input[aria-label="ค้นหารายการ"]').clear().type(p.hn)
      cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', saved.receiptNo)
      cy.get('input[aria-label="ค้นหารายการ"]').clear().type(saved.receiptNo)
      cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
      cy.contains('h1', 'รายละเอียดรายการ').should('be.visible')
      cy.get('.transaction-review-card').should('contain', saved.receiptNo)
      cy.get('.transaction-review-patient').should('contain', p.fullName)
      cy.contains('button', 'ย้อนกลับ').click()
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).not.to.eq('VOIDED')
        expect(r.body.voidedAt).to.eq(null)
      })
    }))
  })

  it('requires a reason and sufficient other-reason detail, confirms cancellation and persists audit history', () => {
    patient().then((p) => sale(p.id).then((saved) => {
      const reason = 'บันทึกบริการผิดรายการเพื่อทดสอบการยกเลิก'
      openSale(saved)
      // no reason is preselected: the confirm button stays disabled until one is chosen
      cy.contains('button', 'ยืนยันการยกเลิก').should('be.disabled')
      cy.get('input[name="void-reason"][value="อื่น ๆ"]').check()
      cy.contains('button', 'ยืนยันการยกเลิก').should('be.disabled')
      cy.get('#transaction-other-reason').type(reason)
      cy.contains('button', 'ยืนยันการยกเลิก').click()
      cy.get('[role="alertdialog"]').should('contain', reason)
      // backing out of the confirmation keeps the sale
      cy.get('[role="alertdialog"]').contains('button', 'ยกเลิก').click()
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).not.to.eq('VOIDED')
      })
      cy.contains('button', 'ยืนยันการยกเลิก').click()
      cy.intercept('POST', `**/api/payments/${saved.id}/reverse`).as('reverseSale')
      cy.get('[role="alertdialog"]').contains('button', /^ยืนยัน$/).click()
      cy.wait('@reverseSale').its('response.statusCode').should('eq', 200)
      cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).to.eq('VOIDED')
        expect(r.body.voidReason).to.contain(reason)
        expect(r.body.voidedAt).to.be.a('string')
      })
      // A fresh visit proves history survives reloading the application.
      visit('/staff/transactions', 'manager')
      cy.get('.transaction-tabs').contains('button', 'ประวัติการยกเลิก').click()
      cy.get('input[aria-label="ค้นหารายการ"]').type(p.hn)
      cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', reason)
      cy.get('.transaction-table tbody tr').contains('button', 'รายละเอียด').click()
      cy.contains('h1', 'ประวัติการยกเลิก').should('be.visible')
      cy.get('.transaction-audit-card').should('contain', saved.receiptNo).and('contain', reason)
      cy.contains('button', 'ยืนยันการยกเลิก').should('not.exist')
    }))
  })

  it('API: rejects blank cancellation reason and duplicate cancellation', () => {
    patient().then((p) => sale(p.id).then((saved) => {
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: ' ' }, 'manager')
        .then((r) => expect(r.status).to.eq(400))
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).not.to.eq('VOIDED')
      })
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: 'บันทึกรายการซ้ำ' }, 'manager')
        .then((r) => expect(r.status).to.eq(200))
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: 'Duplicate cancellation attempt' }, 'manager')
        .then((r) => expect(r.status).to.be.oneOf([400, 409]))
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).to.eq('VOIDED')
        expect(r.body.voidReason).to.eq('บันทึกรายการซ้ำ')
      })
    }))
  })

  it('lists a real course usage under the usage tab and opens its details', () => {
    patient().then((p) => {
      api('POST', '/api/course-purchases', { patientId: p.id, courseId: fixtures.courseId, branchCode: '01', caseOwnerStaffId: fixtures.therapistId, paymentLabelCode: 'PAID' }).then((r) => {
        expect(r.status).to.eq(201)
        const course = r.body
        // using a course needs an appointment of the patient
        useCourse('therapist', p.id, { coursePurchaseId: course.id })
          .then((used) => expect(used.status, JSON.stringify(used.body)).to.eq(200))
        visit('/staff/transactions', 'manager')
        cy.get('.transaction-tabs').contains('button', 'รายการใช้บริการ').click()
        cy.get('input[aria-label="ค้นหารายการ"]').type(p.hn)
        cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', course.course.name)
        cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
        cy.contains('h1', 'รายละเอียดรายการ').should('be.visible')
        cy.get('.transaction-review-patient').should('contain', p.fullName)
        api('GET', `/api/patients/${p.id}/courses`).then((courses) => {
          expect(courses.status).to.eq(200)
          expect(courses.body[0].remaining).to.eq(course.remaining - 1)
        })
      })
    })
  })
})
