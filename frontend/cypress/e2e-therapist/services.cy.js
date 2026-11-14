/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued tests. */
import { api, visit, patient, fixtures, sale, book } from '../support-therapist/helpers'

// Browser coverage exercises the actual checkout; direct API cases are explicitly named.
describe('Therapist services and receipts', () => {
  for (const [method, label] of [
    ['BankTransfer', 'โอนเงินผ่านธนาคาร'],
    ['PromptPay', 'พร้อมเพย์'], ['CreditCard', 'บัตรเครดิต/เดบิต'],
  ]) {
  it(`SVC-PAY-${method} sells a single visit and persists the matching payment and totals`, () => {
    // A single visit is sold through an appointment (the checkout opens from the appointment, which fills the service).
    patient().then((p) => book(p.id).then((appointment) => {
      visit(`/staff/services?patientId=${p.id}&appointmentId=${appointment.id}`)
      cy.get('button.service-menu-action--sale').click()
      // This branch enables both payment statuses by default; availability comes from Settings.
      cy.contains('button', 'ผ่อน').should('be.enabled')
      cy.contains('button', 'มัดจำ').should('be.enabled')
      // the payment status must be chosen explicitly before the sale can be saved
      cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
      cy.get('.payment-options').contains('button', label).click()
      cy.contains('label', 'หมายเหตุ').find('textarea').type('Therapist browser sale')
      // checkout saves directly from the primary button (the review step and confirmation modal were removed)
      cy.intercept('POST', '**/api/payments').as('saveSale')
      cy.contains('button.primary', 'ชำระเงิน').click()
      cy.wait('@saveSale').then(({ response }) => {
        expect(response.statusCode).to.eq(201)
        const saved = response.body
        // saving the sale opens its receipt
        cy.location('pathname').should('equal', `/staff/receipts/${saved.id}`)
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
        cy.contains(p.fullName).should('be.visible')
        cy.window().then((win) => cy.stub(win, 'print').as('print'))
        cy.contains('button', 'พิมพ์ใบเสร็จ').click()
        cy.get('@print').should('have.been.calledOnce')
      })
    }))
  })
  }

  it('SVC-NOTE-01 sends the browser checkout note and verifies the receipt representation', () => {
    patient().then((p) => book(p.id).then((appointment) => {
      const note = 'Therapist note persistence regression'
      visit(`/staff/services?patientId=${p.id}&appointmentId=${appointment.id}`)
      cy.get('button.service-menu-action--sale').click()
      cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').click()
      cy.contains('button', 'พร้อมเพย์').click()
      cy.contains('label', 'หมายเหตุ').find('textarea').type(note)
      cy.intercept('POST', '**/api/payments').as('saleWithNote')
      cy.contains('button.primary', 'ชำระเงิน').click()
      cy.wait('@saleWithNote').then(({ request, response }) => {
        expect(request.body.note).to.eq(note)
        expect(response.statusCode).to.eq(201)
        api('GET', `/api/receipts/${response.body.id}`).then((r) => {
          expect(r.status).to.eq(200)
          expect(r.body.note).to.eq(null)
        })
      })
    }))
  })

  it('API: sells a course and persists its entitlement and receipt', () => {
    patient().then((p) => {
      api('GET', `/api/courses/${fixtures.courseId}`).then((catalog) => {
        expect(catalog.status).to.eq(200)
        sale(p.id, {
          salespersonStaffId: fixtures.therapistId,
          items: [{ type: 'COURSE', referenceId: fixtures.courseId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'PromptPay', amount: catalog.body.price }],
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
        expect(r.body.message).to.contain('หนึ่งใบเสร็จมีรายการชำระเงินได้ไม่เกินหนึ่งรายการ')
      })
      api('GET', `/api/payments?patientId=${p.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(0)
      })
    })
  })

  it('SVC-GAP-01 browser starts single-visit checkout without a course selector', () => {
    patient().then((p) => {
      visit(`/staff/services?patientId=${p.id}`)
      cy.get('button.service-menu-action--sale').click()
      cy.get('input.course-quantity, select.course-selection').should('not.exist')
      cy.get('.payment-options button').should('have.length.at.least', 1)
    })
  })
})
