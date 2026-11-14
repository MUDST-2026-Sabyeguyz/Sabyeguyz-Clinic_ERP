/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai assertions. */
import { api } from '../support-therapist/helpers'

let sequence = 0
const asManager = (method, path, body) => api(method, path, body, 'manager')
const newPatient = (overrides = {}) => ({
  branchCode: '01', customerType: 'Thai', title: 'นาย', firstName: 'สมชาย', lastName: 'ผู้จัดการ',
  gender: 'Male', phone: '0812345678', identityDocumentType: 'NATIONAL_ID',
  identityNumber: String(1100000300000 + ++sequence), birthDate: '1990-01-01', nationality: 'ไทย',
  address: 'ที่อยู่ทดสอบ Manager', ...overrides,
})

describe('Manager patients (API)', () => {
  it('registers a patient and returns an HN in branch 01', () => {
    asManager('POST', '/api/patients', newPatient()).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.hn).to.match(/^\d{10}$/)
      expect(body.branchCode).to.equal('01')
      expect(body.fullName).to.equal('สมชาย ผู้จัดการ')
    })
  })

  it('finds the patient by HN and opens the profile', () => {
    asManager('POST', '/api/patients', newPatient()).then(({ body: created }) => {
      asManager('GET', `/api/patients?query=${created.hn}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.totalItems).to.equal(1)
        expect(body.items[0].id).to.equal(created.id)
      })
      asManager('GET', `/api/patients/${created.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.hn).to.equal(created.hn)
      })
    })
  })

  it('rejects a second patient with the same identity number (409) without creating it', () => {
    const first = newPatient()
    asManager('POST', '/api/patients', first).its('status').should('equal', 201)
    asManager('POST', '/api/patients', first).its('status').should('equal', 409)
    asManager('GET', '/api/patients?size=10').its('body.totalItems').should('equal', 1)
  })

  it('rejects a phone number that is not 10 digits (400)', () => {
    asManager('POST', '/api/patients', newPatient({ phone: '123' })).then(({ status, body }) => {
      expect(status).to.equal(400)
      expect(body.message).to.contain('เบอร์โทร')
    })
    asManager('GET', '/api/patients?size=10').its('body.totalItems').should('equal', 0)
  })

  it('returns nothing for an HN that does not exist', () => {
    asManager('GET', '/api/patients?query=9999999999').its('body.totalItems').should('equal', 0)
  })
})
