import { patientPayload } from '../support/commands'

describe('Patient registration API', () => {
  it('registers a patient and returns a generated HN', () => {
    cy.api('POST', '/api/patients', patientPayload()).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.hn).to.match(/^\d{10}$/)
      expect(body.fullName).to.equal('สมชาย ใจดี')
      expect(body.id).to.be.a('number')
    })
  })

  it('rejects missing privacy consent', () => {
    cy.api('POST', '/api/patients', patientPayload({ privacyConsent: false })).then(({ status, body }) => {
      expect(status).to.equal(400)
      expect(body.fields).to.have.property('privacyConsent')
    })
  })
})

describe('Patient query API', () => {
  beforeEach(() => {
    cy.registerPatient().as('patient')
    cy.registerPatient({ username: 'somsri', email: 'somsri@example.com',
      identityNumber: '1100000000102', fullName: 'สมศรี สุขใจ', phone: '0822222222' })
  })

  for (const [name, query] of [['HN', null], ['name', 'ชาย ใจ'], ['phone', '081111'], ['identity number', '00000000101']]) {
    it(`searches by ${name}`, function () {
      cy.api('GET', '/api/patients', undefined, { qs: { query: query ?? this.patient.hn } })
        .then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.items).to.have.length(1)
          expect(body.items[0].fullName).to.equal('สมชาย ใจดี')
        })
    })
  }

  it('returns an empty page when no patient matches', () => {
    cy.api('GET', '/api/patients', undefined, { qs: { query: 'does-not-exist' } }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.items).to.deep.equal([])
      expect(body.totalItems).to.equal(0)
      expect(body.totalPages).to.equal(0)
    })
  })

  it('paginates search results in the database', () => {
    cy.api('GET', '/api/patients', undefined, { qs: { query: 'สม', page: 1, size: 1 } })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.items).to.have.length(1)
        expect(body).to.include({ page: 1, size: 1, totalItems: 2, totalPages: 2 })
      })
  })

  for (const qs of [{ page: -1 }, { size: 0 }]) {
    it(`rejects invalid pagination ${JSON.stringify(qs)}`, () => {
      cy.api('GET', '/api/patients', undefined, { qs }).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.message).to.be.a('string').and.not.be.empty
      })
    })
  }

  it('returns a patient profile without credentials', function () {
    cy.api('GET', `/api/patients/${this.patient.id}`).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.id).to.equal(this.patient.id)
      expect(body.branch.code).to.equal('RP')
      for (const key of ['password', 'passwordHash', 'username']) expect(body).not.to.have.property(key)
      expect(body.email).to.equal('cypress-patient@example.com')
    })
  })

  it('returns 404 for a missing patient', () => {
    cy.api('GET', '/api/patients/999999').then(({ status, body }) => {
      expect(status).to.equal(404)
      expect(body.message).to.equal('ไม่พบผู้ป่วย id 999999')
    })
  })
})
