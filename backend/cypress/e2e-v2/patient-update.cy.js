import { patientPayload } from '../support-v2/commands'

// The web form sends one fullName and Thai words for gender and group; the API accepts both shapes.
describe('v2 patient registration from the web form and profile editing', () => {
  it('registers a patient from a payload that carries fullName instead of firstName and lastName', () => {
    const { firstName, lastName, ...rest } = patientPayload()
    cy.api('POST', '/api/patients', { ...rest, fullName: `${firstName} ${lastName}`, gender: 'ชาย' }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      expect(body.fullName).to.equal('สมชาย ใจดี')
    })
  })

  it('treats a passport holder as a foreigner and does not demand a national id', () => {
    const { firstName, lastName, ...rest } = patientPayload()
    cy.api('POST', '/api/patients', {
      ...rest, fullName: `${firstName} ${lastName}`, identityDocumentType: 'PASSPORT', identityNumber: '1234567890',
      phone: '', nationality: 'ต่างชาติ',
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(201)
      cy.api('GET', `/api/patients/${body.id}`).then(({ body: profile }) => {
        expect(profile.customerTypeCode).to.equal('Foreigner')
        expect(profile.identityDocumentType).to.equal('PASSPORT')
        expect(profile.identityNumber).to.equal('1234567890')
      })
    })
  })

  it('edits name, title, gender, birth date, phone, address and the other profile fields', () => {
    cy.registerPatient().then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, {
        title: 'นางสาว', fullName: 'สมหญิง รักดี', gender: 'หญิง', birthDate: '1992-02-02', phone: '0899999999',
        identityDocumentType: 'NATIONAL_ID', identityNumber: '1100000000102', address: 'ที่อยู่ใหม่',
        nickname: 'หญิง', bloodGroup: 'AB', nationality: 'ไทย',
      }).then(({ status, body }) => {
        expect(status, JSON.stringify(body)).to.equal(200)
        expect(body.title).to.equal('นางสาว')
        expect(body.fullName).to.equal('สมหญิง รักดี')
        expect(body.gender).to.equal('Female')
        expect(body.birthDate).to.equal('1992-02-02')
        expect(body.phone).to.equal('0899999999')
        expect(body.identityNumber).to.equal('1100000000102')
        expect(body.address).to.equal('ที่อยู่ใหม่')
        expect(body.nickname).to.equal('หญิง')
        expect(body.bloodGroup).to.equal('AB')
      })
      cy.api('GET', `/api/patients/${patient.id}`).its('body.fullName').should('equal', 'สมหญิง รักดี')
    })
  })

  it('never changes the HN when a patient is edited', () => {
    cy.registerPatient().then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { fullName: 'ชื่อ ใหม่', phone: '0877777777' }).then(({ body }) => {
        expect(body.hn).to.equal(patient.hn)
      })
    })
  })

  it('keeps a Thai patient\'s phone and national id and validates the national id', () => {
    cy.registerPatient().then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { phone: '' }).its('status').should('equal', 400)
      cy.api('PUT', `/api/patients/${patient.id}`, { identityDocumentType: 'NATIONAL_ID', identityNumber: '123' })
        .its('status').should('equal', 400)
    })
  })

  it('shows the edit in the patient search too', () => {
    cy.registerPatient().then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { fullName: 'ไพบูลย์ เปลี่ยนชื่อ', phone: '0866666666' }).its('status').should('equal', 200)
      cy.api('GET', `/api/patients?query=${encodeURIComponent('ไพบูลย์')}`).its('body.items').should('have.length', 1)
    })
  })

  it('answers 404 for an unknown patient', () => {
    cy.api('PUT', '/api/patients/999999', { phone: '0811111111' }).its('status').should('equal', 404)
  })
})
