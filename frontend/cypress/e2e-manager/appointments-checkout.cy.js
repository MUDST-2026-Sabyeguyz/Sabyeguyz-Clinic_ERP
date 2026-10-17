/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued helpers. */
import { api, fixtures, patient, visit } from '../support-therapist/helpers'

describe('Manager appointment confirmation checkout', () => {
  it('opens the selected patient sale, then confirms the appointment after payment is saved', () => {
    patient().then((p) => {
      api('POST', '/api/appointments', {
        branchCode: '01',
        patientId: p.id,
        therapistId: fixtures.therapistId,
        serviceId: fixtures.singleVisitServiceId,
        startAt: `${fixtures.tomorrow}T09:00:00+07:00`,
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
        cy.contains('button', 'เริ่มขายบริการ').click()

        cy.get('.payment-status-group').contains('button', 'ชำระเต็มจำนวน').should('be.enabled')
        cy.get('.payment-options').contains('button', 'Cash').should('be.enabled').click()
        cy.intercept('POST', '**/api/payments').as('savePayment')
        cy.intercept('POST', `**/api/appointments/${appointment.id}/confirm`).as('confirmAppointment')

        cy.contains('button', 'ตรวจสอบและชำระเงิน').click()
        cy.contains('button', 'ยืนยันรับชำระเงิน').click()
        cy.get('.payment-modal').contains('button', 'ยืนยันการชำระเงิน').click()

        cy.wait('@savePayment').then(({ response }) => {
          expect(response.statusCode).to.equal(201)
          return api('GET', `/api/receipts/${response.body.id}`, undefined, 'manager')
        }).then((receipt) => {
          expect(receipt.status).to.equal(200)
          expect(receipt.body.patient.id).to.equal(p.id)
        })
        cy.wait('@confirmAppointment').then(({ response }) => {
          expect(response.statusCode).to.equal(200)
          expect(response.body.status).to.equal('CONFIRMED')
        })
        cy.contains('.sale-success-card', 'อัปเดตสถานะนัดหมายเป็นยืนยันแล้ว').should('be.visible')

        api('GET', `/api/appointments/${appointment.id}`, undefined, 'manager').then((result) => {
          expect(result.status).to.equal(200)
          expect(result.body.status).to.equal('CONFIRMED')
          expect(result.body.patient.id).to.equal(p.id)
        })
        cy.contains('button', 'กลับหน้านัดหมาย').click()
        cy.location('pathname').should('eq', '/staff/appointments')
        cy.get(`button[aria-label="${statusLabel}"]`).should('contain.text', 'ยืนยัน')
      })
    })
  })
})
