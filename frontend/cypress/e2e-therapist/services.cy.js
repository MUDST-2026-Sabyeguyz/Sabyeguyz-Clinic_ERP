/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued tests. */
import { api, visit, patient, fixtures, sale } from '../support-therapist/helpers'

// Browser coverage exercises the actual checkout; direct API cases are explicitly named.
describe('Therapist services and receipts', () => {
  for (const [method, label] of [
    ['Cash', 'เงินสด'], ['BankTransfer', 'โอนเงินผ่านธนาคาร'],
    ['PromptPay', 'พร้อมเพย์'], ['CreditCard', 'บัตรเครดิต/เดบิต'],
  ]) {
  it(`SVC-PAY-${method} sells a single visit and persists the matching payment and totals`, () => {
    patient().then((p) => {
      visit(`/staff/services?patientId=${p.id}`)
      cy.contains('button', 'เริ่มขายบริการ').click()
      cy.contains('button', 'ผ่อนชำระ').should('be.disabled')
      cy.contains('button', 'มัดจำ').should('be.disabled')
      cy.get('.payment-options').contains('button', label).click()
      cy.get('textarea[placeholder="เพิ่มรายละเอียดเกี่ยวกับรายการขาย"]').type('Therapist browser sale')
      cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
      cy.contains('button', 'ยืนยันรับชำระเงิน').click()
      cy.get('.payment-modal').within(() => cy.contains('button', 'ยกเลิก').click())
      api('GET', `/api/payments?patientId=${p.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(0)
      })
      cy.contains('button', 'ยืนยันรับชำระเงิน').click()
      cy.intercept('POST', '**/api/payments').as('saveSale')
      cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
      cy.wait('@saveSale').then(({ response }) => {
        expect(response.statusCode).to.eq(201)
        const saved = response.body
        cy.contains('h1', 'บันทึกการขายสำเร็จ').should('be.visible')
        cy.contains(saved.receiptNo).should('be.visible')
        api('GET', `/api/receipts/${saved.id}`).then((r) => {
          expect(r.status).to.eq(200)
          expect(r.body.patient.id).to.eq(p.id)
          expect(r.body.items[0].type).to.eq('SingleVisit')
          expect(r.body.items[0].quantity).to.eq(1)
          expect(Number(r.body.items[0].lineTotal)).to.eq(Number(r.body.subtotalBeforeDiscount))
          expect(Number(r.body.billDiscountAmount)).to.eq(0)
          expect(Number(r.body.total)).to.eq(Number(r.body.subtotalBeforeDiscount))
          expect(Number(r.body.paid)).to.eq(Number(r.body.total))
          expect(r.body.payments).to.have.length(1)
          expect(r.body.payments[0].paymentMethodCode).to.eq(method)
          expect(Number(r.body.payments[0].amount)).to.eq(Number(r.body.total))
        })
        cy.contains('button', 'ดูและพิมพ์ใบเสร็จ').click()
        cy.contains(saved.receiptNo).should('be.visible')
        cy.contains(p.fullName).should('be.visible')
        cy.contains('button', 'พิมพ์เอกสาร').click()
        cy.contains('ยืนยันการพิมพ์เอกสาร').should('be.visible')
        cy.contains('button', 'ยกเลิก').click()
      })
    })
  })
  }

  it('SVC-NOTE-01 retains the browser checkout note in the persisted receipt', () => {
    patient().then((p) => {
      const note = 'Therapist note persistence regression'
      visit(`/staff/services?patientId=${p.id}`)
      cy.contains('button', 'เริ่มขายบริการ').click()
      cy.contains('button', 'พร้อมเพย์').click()
      cy.get('textarea[placeholder="เพิ่มรายละเอียดเกี่ยวกับรายการขาย"]').type(note)
      cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
      cy.contains('button', 'ยืนยันรับชำระเงิน').click()
      cy.intercept('POST', '**/api/payments').as('saleWithNote')
      cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()
      cy.wait('@saleWithNote').then(({ request, response }) => {
        expect(request.body.note).to.eq(note)
        expect(response.statusCode).to.eq(201)
        api('GET', `/api/receipts/${response.body.id}`).then((r) => {
          expect(r.status).to.eq(200)
          expect(r.body.note).to.eq(note)
        })
      })
    })
  })

  it('API: sells a course and persists its entitlement and receipt', () => {
    patient().then((p) => {
      api('GET', `/api/courses/${fixtures.courseId}`).then((catalog) => {
        expect(catalog.status).to.eq(200)
        sale(p.id, {
          salespersonStaffId: fixtures.therapistId,
          items: [{ type: 'COURSE', referenceId: fixtures.courseId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'CASH', amount: catalog.body.price }],
        }).then((saved) => {
          api('GET', `/api/receipts/${saved.id}`).then((r) => {
            expect(r.status).to.eq(200)
            expect(r.body.items[0].type).to.eq('CoursePurchase')
          })
          api('GET', `/api/patients/${p.id}/courses`).then((r) => {
            expect(r.status).to.eq(200)
            expect(r.body).to.have.length(1)
            expect(r.body[0].remaining).to.eq(catalog.body.sessions + catalog.body.bonusSessions)
            expect(r.body[0].usable).to.eq(true)
          })
        })
      })
    })
  })

  it('API: rejects mixed payment methods without persisting a sale', () => {
    patient().then((p) => {
      api('POST', '/api/payments', {
        patientId: p.id, branchCode: '01', salespersonStaffId: fixtures.therapistId,
        items: [{ type: 'SERVICE', referenceId: fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'CASH', amount: 400 }, { paymentMethodCode: 'PROMPTPAY', amount: 400 }],
      }).then((r) => {
        expect(r.status).to.eq(400)
        expect(r.body.message).to.contain('หนึ่งใบเสร็จรับชำระได้เพียงช่องทางเดียว')
      })
      api('GET', `/api/payments?patientId=${p.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(0)
      })
    })
  })

  // Source gap: startSale fixes mode='single'; template exposes no course selector.
  it('SVC-GAP-01 browser exposes a course selection control before course checkout', () => {
    patient().then((p) => {
      visit(`/staff/services?patientId=${p.id}`)
      cy.contains('button', 'เริ่มขายบริการ').click()
      // Regression: the sale mode exists in script but no interactive course choice is rendered.
      cy.contains('button, [role="option"], option, label', /^คอร์ส$/).should('be.visible')
    })
  })
})
