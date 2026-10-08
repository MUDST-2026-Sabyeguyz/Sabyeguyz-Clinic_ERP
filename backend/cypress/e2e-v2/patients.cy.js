import { patientPayload } from '../support-v2/commands'

describe('v2 patients', () => {
  it('registers a Thai patient and generates an HN of YY + branch + MM + running number', () => {
    cy.registerPatient().then((patient) => {
      expect(patient.hn).to.match(/^\d{2}01\d{2}\d{4}$/)
      expect(patient.fullName).to.contain('สมชาย')
      expect(patient.branchCode).to.equal('01')
      expect(patient.active).to.equal(true)
    })
  })

  it('gives consecutive running numbers to consecutive patients', () => {
    cy.registerPatient({ firstName: 'หนึ่ง' }).then((first) => {
      cy.registerPatient({ firstName: 'สอง', phone: '0822222222' }).then((second) => {
        expect(Number(second.hn.slice(-4))).to.equal(Number(first.hn.slice(-4)) + 1)
      })
    })
  })

  it('requires phone and national id for a Thai patient', () => {
    cy.api('POST', '/api/patients', patientPayload({ phone: '' })).its('status').should('equal', 400)
    cy.api('POST', '/api/patients', patientPayload({ identityNumber: '' })).its('status').should('equal', 400)
  })

  it('rejects a national id that is not 13 digits', () => {
    cy.api('POST', '/api/patients', patientPayload({ identityNumber: '12345' })).its('status').should('equal', 400)
  })

  it('accepts a foreigner without phone and national id', () => {
    cy.api('POST', '/api/patients', patientPayload({
      customerType: 'Foreigner', phone: '', identityNumber: '', identityDocumentType: 'PASSPORT',
    })).its('status').should('equal', 201)
  })

  it('rejects an unknown branch', () => {
    cy.api('POST', '/api/patients', patientPayload({ branchCode: '99' })).its('status').should('equal', 400)
  })

  it('searches by first name, last name, phone and HN', () => {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', phone: '0899990000' }).then((patient) => {
      for (const query of ['อรุณ', 'แสงทอง', '0899990000', patient.hn]) {
        cy.api('GET', `/api/patients?query=${encodeURIComponent(query)}`).then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.items.map((item) => item.id)).to.include(patient.id)
        })
      }
    })
  })

  it('paginates and rejects invalid paging', () => {
    cy.registerPatient({ firstName: 'ก' })
    cy.registerPatient({ firstName: 'ข', phone: '0822222222' })
    cy.api('GET', '/api/patients?size=1&page=0').then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.items).to.have.length(1)
      expect(body.totalItems).to.equal(2)
      expect(body.totalPages).to.equal(2)
    })
    cy.api('GET', '/api/patients?page=-1').its('status').should('equal', 400)
    cy.api('GET', '/api/patients?size=0').its('status').should('equal', 400)
    cy.api('GET', '/api/patients?size=101').its('status').should('equal', 400)
  })

  it('reads a patient profile and 404s for an unknown id', () => {
    cy.registerPatient().then((patient) => {
      cy.api('GET', `/api/patients/${patient.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.hn).to.equal(patient.hn)
        expect(body.customerTypeCode).to.equal('Thai')
        expect(body.identityDocumentType).to.equal('NATIONAL_ID')
        expect(body.identityNumber).to.equal('1100000000101')
      })
    })
    cy.api('GET', '/api/patients/999999').its('status').should('equal', 404)
  })

  it('requires authentication', () => {
    cy.apiAs(null, 'GET', '/api/patients').its('status').should('equal', 401)
  })
})
