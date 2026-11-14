/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued helpers. */
import { api, fixtures, patient, slot, visit } from '../support-therapist/helpers'

describe('Manager appointment confirmation checkout', () => {
  it('opens the selected patient sale, then confirms the appointment after payment is saved', () => {
    patient().then((p) => {
      api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: p.id,
        therapistId: fixtures.therapistId,
        serviceId: fixtures.singleVisitServiceId,
        ...slot(fixtures.tomorrow, '09:00'),
        status: 'Unconfirmed',
      }, 'manager').then(({ status, body: appointment }) => {
        expect(status).to.equal(201)
        expect(appointment.status).to.equal('UNCONFIRMED')

        visit('/staff/appointments', 'manager')
        cy.get('.appointment-table-scroll tbody tr').contains(p.fullName).should('be.visible')
        const statusLabel = `สถานะนัดหมายของ ${p.fullName}`
        cy.get(`button[aria-label="${statusLabel}"]`).click()
        cy.get(`[role="listbox"][aria-label="${statusLabel}"]`)
          .contains('[role="option"]', /^ยืนยัน$/).click()

        cy.location('pathname').should('eq', '/staff/services')
        cy.location('search').should('contain', `patientId=${p.id}`)
          .and('contain', `appointmentId=${appointment.id}`)
        cy.get('.service-menu-patient').should('contain.text', p.fullName)
        api('GET', `/api/appointments/${appointment.id}`, undefined, 'manager').then((result) => {
          expect(result.status).to.equal(200)
          expect(result.body.status).to.equal('UNCONFIRMED')
        })
        cy.get('button.service-menu-action--sale').click()

        cy.contains('.payment-group', 'ลักษณะการชำระ').contains('button', 'ชำระเต็ม').should('be.enabled').click()
        cy.get('.payment-options').contains('button', 'พร้อมเพย์').should('be.enabled').click()
        cy.intercept('POST', '**/api/payments').as('savePayment')

        // checkout saves directly from the primary button (the review step and confirmation modal were removed)
        cy.contains('button.primary', 'ชำระเงิน').click()

        cy.wait('@savePayment').then(({ response }) => {
          expect(response.statusCode).to.equal(201)
          return api('GET', `/api/receipts/${response.body.id}`, undefined, 'manager')
        }).then((receipt) => {
          expect(receipt.status).to.equal(200)
          expect(receipt.body.patient.id).to.equal(p.id)
        })
        // the backend confirms the appointment together with the saved payment and the page opens the receipt
        cy.location('pathname').should('match', /^\/staff\/receipts?\//)

        api('GET', `/api/appointments/${appointment.id}`, undefined, 'manager').then((result) => {
          expect(result.status).to.equal(200)
          expect(result.body.status).to.equal('CONFIRMED')
          expect(result.body.patient.id).to.equal(p.id)
        })
        visit('/staff/appointments', 'manager')
        // a confirmed appointment shows its status as read-only text instead of the status dropdown
        cy.contains('tr', p.fullName).find('.appointment-status-readonly').should('contain.text', 'ยืนยัน')
      })
    })
  })
})
