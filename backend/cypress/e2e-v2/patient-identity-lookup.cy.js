import { patientPayload } from '../support-v2/commands'

describe('P2 patient fields and exact national identity lookup', () => {
  it('rejects ambiguous fullName-only requests when introducing a middle name', () => {
    const { firstName, lastName, ...legacy } = patientPayload()
    cy.api('POST', '/api/patients', { ...legacy, fullName: 'John Middle Smith', middleName: 'Middle' })
      .its('status').should('equal', 400)
    cy.registerPatient({ firstName: 'John', lastName: 'Smith' }).then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { fullName: 'John Middle Smith', middleName: 'Middle' })
        .its('status').should('equal', 400)
      cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
        expect(body.fullName).to.equal('John Smith')
        expect(body.middleName).to.equal(null)
      })
    })
  })
  it('preserves structured names for legacy fullName-only profile saves and rejects ambiguous renames', () => {
    cy.registerPatient({ firstName: 'John', lastName: 'Smith', middleName: 'Middle' }).then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { fullName: 'John Middle Smith', phone: '0899999999' })
        .its('status').should('equal', 200)
      cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
        expect(body.firstName).to.equal('John')
        expect(body.middleName).to.equal('Middle')
        expect(body.lastName).to.equal('Smith')
        expect(body.fullName).to.equal('John Middle Smith')
        expect(body.phone).to.equal('0899999999')
      })
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: '', fullName: 'John Middle Smith' })
        .its('body.lastName').should('equal', 'Smith')
      for (const firstName of [null, '', '  ']) {
        cy.api('PUT', `/api/patients/${patient.id}`, { firstName, fullName: 'Jane Middle Smith' })
          .its('status').should('equal', 400)
      }
      cy.api('GET', `/api/patients/${patient.id}`).its('body.fullName').should('equal', 'John Middle Smith')
      cy.api('PUT', `/api/patients/${patient.id}`, { lastName: 'Jones', fullName: 'John Middle Smith' })
        .its('body.fullName').should('equal', 'John Middle Jones')
    })
  })
  it('persists, updates, preserves omitted and clears middleName without changing first/last names', () => {
    cy.registerPatient({ firstName: 'John Paul', lastName: 'Smith Jones', middleName: ' Middle ' }).then((patient) => {
      cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
        expect(body.firstName).to.equal('John Paul')
        expect(body.lastName).to.equal('Smith Jones')
        expect(body.middleName).to.equal('Middle')
        expect(body.fullName).to.equal('John Paul Middle Smith Jones')
      })
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'John Paul', lastName: 'Smith Jones', middleName: 'New' })
        .its('body.middleName').should('equal', 'New')
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'John Paul', lastName: 'Smith Jones', address: '' })
        .its('status').should('equal', 200)
      cy.api('GET', `/api/patients/${patient.id}`).its('body.middleName').should('equal', 'New')
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'John Paul', lastName: 'Smith Jones', middleName: null })
        .its('status').should('equal', 200)
      cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
        expect(body.middleName).to.equal(null)
        expect(body.firstName).to.equal('John Paul')
        expect(body.lastName).to.equal('Smith Jones')
        expect(body.fullName).to.equal('John Paul Smith Jones')
      })
    })
  })

  it('normalizes blank middleName to null and rejects overlength or non-string values without losing the previous name', () => {
    cy.registerPatient({ middleName: 'Existing' }).then((patient) => {
      for (const middleName of ['x'.repeat(101), { name: 'object' }, 123]) {
        cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'สมชาย', lastName: 'ใจดี', middleName })
          .its('status').should('equal', 400)
        cy.api('GET', `/api/patients/${patient.id}`).its('body.middleName').should('equal', 'Existing')
      }
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'สมชาย', lastName: 'ใจดี', middleName: '  ' })
        .its('body.middleName').should('be.null')
    })
    cy.registerPatient({ middleName: '' }).then((patient) => {
      cy.api('GET', `/api/patients/${patient.id}`).its('body.middleName').should('be.null')
    })
  })

  for (const [thai, english] of [['นาย', 'Mr.'], ['นางสาว', 'Miss'], ['นาง', 'Mrs.'], ['เด็กชาย', 'Boy'], ['เด็กหญิง', 'Girl']]) {
    it(`stores canonical ${thai} for Thai/English title inputs and supports profile updates`, () => {
      cy.registerPatient({ title: english }).then((patient) => {
        cy.api('GET', `/api/patients/${patient.id}`).its('body.title').should('equal', thai)
        cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'สมชาย', lastName: 'ใจดี', title: thai })
          .its('body.title').should('equal', thai)
      })
    })
  }

  for (const [input, canonical] of [['ชาย', 'Male'], ['หญิง', 'Female'], ['ไม่ระบุ', 'NotSpecified']]) {
    it(`round-trips gender ${input} as ${canonical} independently from the title`, () => {
      cy.registerPatient({ gender: input }).then((patient) => {
        cy.api('GET', `/api/patients/${patient.id}`).its('body.gender').should('equal', canonical)
        cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'สมชาย', lastName: 'ใจดี', gender: input })
          .its('body.gender').should('equal', canonical)
      })
    })
  }

  it('preserves foreigner optional fields, optional address and existing Thai phone validation', () => {
    cy.registerPatient({ customerType: 'Foreigner', identityNumber: null, identityDocumentType: null,
      phone: null, birthDate: null, address: '', middleName: 'Foreign' }).then((patient) => {
      cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
        expect(body.customerTypeCode).to.equal('Foreigner')
        expect(body.middleName).to.equal('Foreign')
        expect(body.phone).to.equal(null)
        expect(body.birthDate).to.equal(null)
        expect(body.identityNumber).to.equal(null)
        expect(body.address).to.equal('')
      })
    })
    cy.registerPatient({ address: '' }).then((patient) => {
      cy.api('PUT', `/api/patients/${patient.id}`, { firstName: 'สมชาย', lastName: 'ใจดี', phone: '' })
        .its('status').should('equal', 400)
    })
  })

  it('finds only an exact national ID globally and returns a minimal summary, never passport/phone matches', () => {
    cy.registerPatient({ branchCode: '02', identityNumber: '1100000000931', middleName: 'กลาง' }).then((patient) => {
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        cy.apiAs(auth.token, 'GET', '/api/patients/identity-lookup?nationalId=1100000000931').then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body).to.deep.equal({ exists: true, patient: { id: patient.id, hn: patient.hn, fullName: 'สมชาย กลาง ใจดี' } })
        })
      })
      cy.api('GET', '/api/patients/identity-lookup?nationalId=1100000000932').its('body')
        .should('deep.equal', { exists: false, patient: null })
    })
    cy.registerPatient({ customerType: 'Foreigner', identityDocumentType: 'PASSPORT', identityNumber: '1100000000933' })
    cy.api('GET', '/api/patients/identity-lookup?nationalId=1100000000933').its('body')
      .should('deep.equal', { exists: false, patient: null })
  })

  it('rejects partial, malformed and unauthenticated identity lookups', () => {
    for (const nationalId of ['110000000093', '11000000009311', '110000000093x', '11000000009%25_', '']) {
      cy.api('GET', `/api/patients/identity-lookup?nationalId=${nationalId}`).its('status').should('equal', 400)
    }
    cy.apiAs(null, 'GET', '/api/patients/identity-lookup?nationalId=1100000000931').its('status').should('equal', 401)
  })
})
