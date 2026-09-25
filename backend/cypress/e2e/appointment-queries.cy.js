import { appointmentBody } from '../support/commands'

describe('Appointment query API', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => {
      cy.api('GET', '/api/catalog/therapists').then(({ status, body }) => {
        expect(status).to.equal(200)
        const secondTherapist = body.find(({ code }) => code === 'RP-T02')
        expect(secondTherapist).to.exist
        for (const booking of [
          appointmentBody(this.fixtures, patient.id),
          appointmentBody(this.fixtures, patient.id, '11:00'),
          { ...appointmentBody(this.fixtures, patient.id), therapistId: secondTherapist.id },
        ]) {
          cy.api('POST', '/api/appointments', booking).its('status').should('equal', 201)
        }
      })
    })
  })

  it('returns paged appointments with patient details', function () {
    cy.api('GET', '/api/appointments', undefined, { qs: { branchCode: 'RP', date: this.fixtures.tomorrow } })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.items).to.have.length(3)
        expect(body.totalItems).to.equal(3)
        expect(body.items[0].status).to.equal('CONFIRMED')
        expect(body.items[0].patient.hn).not.to.be.empty
      })
  })

  it('returns an empty page when no appointments match', function () {
    const date = new Date(`${this.fixtures.tomorrow}T00:00:00Z`)
    date.setUTCDate(date.getUTCDate() + 30)
    cy.api('GET', '/api/appointments', undefined, { qs: { date: date.toISOString().slice(0, 10) } })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.items).to.deep.equal([])
        expect(body.totalItems).to.equal(0)
      })
  })

  it('rejects an invalid status value', () => {
    cy.api('GET', '/api/appointments', undefined, { qs: { status: 'NOT_A_STATUS' } }).then(({ status, body }) => {
      expect(status).to.equal(400)
      expect(body.message).to.be.a('string').and.not.be.empty
    })
  })

  it('rejects an invalid page size', () => {
    cy.api('GET', '/api/appointments', undefined, { qs: { size: 0 } }).its('status').should('equal', 400)
  })

  it('returns 404 for a missing appointment', () => {
    cy.api('GET', '/api/appointments/999999').then(({ status, body }) => {
      expect(status).to.equal(404)
      expect(body.message).to.equal('ไม่พบการนัดหมาย id 999999')
    })
  })
})
