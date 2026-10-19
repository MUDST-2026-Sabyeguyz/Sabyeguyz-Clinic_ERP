/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued tests. */
import { api, visit, patient, fixtures, sale } from '../support-therapist/helpers'

function openSale(saved) {
  visit('/staff/transactions')
  cy.get('input[aria-label="ค้นหารายการ"]').type(saved.receiptNo)
  cy.get('.transaction-table tbody tr').should('have.length', 1)
  cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
}

describe('Therapist transactions', () => {
  it('filters sales by receipt and patient, opens details and preserves a sale when backing out', () => {
    patient().then((p) => sale(p.id).then((saved) => {
      visit('/staff/transactions')
      cy.get('input[aria-label="ค้นหารายการ"]').type('NO-SUCH-RECEIPT')
      cy.contains('ไม่พบรายการที่ตรงกับเงื่อนไข').should('be.visible')
      cy.get('input[aria-label="ค้นหารายการ"]').clear().type(p.hn)
      cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', saved.receiptNo)
      cy.get('input[aria-label="ค้นหารายการ"]').clear().type(saved.receiptNo)
      cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
      cy.contains('h1', 'ตรวจสอบรายการที่ต้องการยกเลิก').should('be.visible')
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
      cy.contains('button', 'ดำเนินการยกเลิก').click()
      cy.get('input[name="void-reason"][value="ลงรายการหรือเลือกบริการผิด"]').should('be.checked')
      cy.get('input[name="void-reason"][value="อื่น ๆ"]').check()
      cy.contains('button', 'ตรวจสอบก่อนยืนยัน').should('be.disabled')
      cy.get('#transaction-other-reason').type('short')
      cy.contains('button', 'ตรวจสอบก่อนยืนยัน').should('be.disabled')
      cy.get('#transaction-other-reason').clear().type(reason)
      cy.contains('button', 'ตรวจสอบก่อนยืนยัน').click()
      cy.get('.transaction-confirm-summary').should('contain', reason)
      cy.contains('button', 'ยืนยันการยกเลิก').click()
      cy.get('[role="alertdialog"]').contains('button', 'แก้ไข').click()
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).not.to.eq('VOIDED')
      })
      cy.contains('button', 'ยืนยันการยกเลิก').click()
      cy.intercept('POST', `**/api/payments/${saved.id}/reverse`).as('reverseSale')
      cy.get('[role="alertdialog"]').contains('button', 'ยืนยันการยกเลิก').click()
      cy.wait('@reverseSale').its('response.statusCode').should('eq', 200)
      cy.contains('h1', 'ยกเลิกรายการสำเร็จ').should('be.visible')
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).to.eq('VOIDED')
        expect(r.body.voidReason).to.contain(reason)
        expect(r.body.voidedAt).to.be.a('string')
      })
      // A fresh visit proves history survives reloading the application.
      visit('/staff/transactions')
      cy.get('.transaction-tabs').contains('button', 'ประวัติการยกเลิก').click()
      cy.get('input[aria-label="ค้นหารายการ"]').type(p.hn)
      cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', reason)
      cy.get('.transaction-table tbody tr').contains('button', 'ดูรายละเอียด').click()
      cy.contains('h1', 'รายละเอียดประวัติการยกเลิก').should('be.visible')
      cy.get('.transaction-audit-card').should('contain', saved.receiptNo).and('contain', reason)
      cy.contains('button', 'ดำเนินการยกเลิก').should('not.exist')
    }))
  })

  it('API: rejects blank cancellation reason and duplicate cancellation', () => {
    patient().then((p) => sale(p.id).then((saved) => {
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: ' ' })
        .then((r) => expect(r.status).to.eq(400))
      api('GET', `/api/receipts/${saved.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).not.to.eq('VOIDED')
      })
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: 'บันทึกรายการซ้ำ' })
        .then((r) => expect(r.status).to.eq(200))
      api('POST', `/api/payments/${saved.id}/reverse`, { reason: 'Duplicate cancellation attempt' })
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
      api('POST', '/api/course-purchases', { patientId: p.id, courseId: fixtures.courseId, branchCode: '01' }).then((r) => {
        expect(r.status).to.eq(201)
        const course = r.body
        api('POST', '/api/course-usages', { coursePurchaseId: course.id })
          .then((used) => expect(used.status).to.eq(200))
        visit('/staff/transactions')
        cy.get('.transaction-tabs').contains('button', 'รายการใช้บริการ').click()
        cy.get('input[aria-label="ค้นหารายการ"]').type(p.hn)
        cy.get('.transaction-table tbody tr').should('have.length', 1).and('contain', course.course.name)
        cy.get('.transaction-table tbody tr').contains('button', 'ยกเลิก').click()
        cy.contains('h1', 'ตรวจสอบรายการที่ต้องการยกเลิก').should('be.visible')
        cy.get('.transaction-review-patient').should('contain', p.fullName)
        api('GET', `/api/patients/${p.id}/courses`).then((courses) => {
          expect(courses.status).to.eq(200)
          expect(courses.body[0].remaining).to.eq(course.remaining - 1)
        })
      })
    })
  })
})
