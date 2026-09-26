describe('HnGeneratorTest migrated domain operations', () => {
  it('formatsYearBranchMonthAndRunningNumber', () => {
    cy.api('POST', '/__cypress/hn/format', { date: '2026-12-01', branchCode: '01', runningNumber: 1 })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.hn).to.equal('2601120001')
      })
  })

  it('monthIsPartOfHnAndNaturallyResetsCounterScope', () => {
    for (const [date, hn] of [['2026-11-30', '2602110001'], ['2026-12-01', '2602120001']]) {
      cy.api('POST', '/__cypress/hn/format', { date, branchCode: '02', runningNumber: 1 })
        .then(({ status, body }) => {
          expect(status).to.equal(200)
          expect(body.hn).to.equal(hn)
        })
    }
  })

  it('patientHnIsImmutableAfterFirstAssignment', () => {
    cy.api('POST', '/__cypress/hn/assign', { first: '2601120001', second: '2602120001' })
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.hn).to.equal('2601120001')
      })
  })

  it('rejectsUnresolvedOrNonNumericBranchCode', () => {
    for (const branchCode of ['AB', null]) {
      cy.api('POST', '/__cypress/hn/next', { date: '2026-12-01', branchCode, runningNumber: 1 })
        .then(({ status, body }) => {
          expect(status).to.equal(400)
          expect(body.message).to.include('Numeric Branch Code')
        })
    }
  })
})

describe('PatientRegistrationServiceTest migrated API behavior', () => {
  beforeEach(() => {
    cy.api('POST', '/__cypress/hn/branch-code', { numericCode: '07' }).its('status').should('equal', 200)
  })

  it('persistsPatientAndGeneratesBranchHn', () => {
    cy.registerPatient({ branchCode: '07' }).then((patient) => {
      expect(patient.hn).to.match(/^\d{2}07\d{2}\d{4}$/)
      expect(patient.active).to.equal(true)
      cy.api('GET', `/api/patients/${patient.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.hn).to.equal(patient.hn)
      })
    })
  })

  it('incrementsRunningNumberForSameBranchAndMonth', () => {
    cy.registerPatient({ branchCode: '07' }).then((first) => {
      cy.registerPatient({ branchCode: '07', username: 'second', email: 'second@example.com', identityNumber: '1100000000102' })
        .then((second) => {
          expect(first.hn).to.have.length(10).and.match(/0001$/)
          expect(second.hn).to.match(/0002$/)
          expect(first.hn).not.to.equal(second.hn)
        })
    })
  })
})

describe('AuthServiceTest migrated real authentication', () => {
  function register(body) {
    return cy.api('POST', '/api/auth/register', body).then(({ status, body: user }) => {
      expect(status).to.equal(200)
      return user
    })
  }

  function login(username, password) {
    return cy.request({ method: 'POST', url: '/api/auth/login', body: { username, password }, failOnStatusCode: false })
  }

  it('registerHashesPasswordAndDefaultsRole', () => {
    register({ username: 'alice', password: 'password1' }).then((user) => {
      expect(user.role).to.equal('MANAGER')
      cy.api('GET', `/__cypress/auth/users/${user.id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.passwordHash).to.be.a('string').and.not.equal('password1')
        expect(body.passwordHash).to.match(/^\$2[aby]\$/)
      })
      login('alice', 'password1').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.id).to.equal(user.id)
      })
    })
  })

  it('loginRejectsWrongPassword', () => {
    register({ username: 'alice', password: 'password1' })
    login('alice', 'wrong').its('status').should('equal', 400)
  })

  it('loginChecksUsernameBeforeEmail', () => {
    register({ username: 'alice@example.com', password: 'alice-password', email: 'first@example.com' }).as('alice')
    register({ username: 'bob', password: 'bob-password', email: 'alice@example.com' })
    login('alice@example.com', 'bob-password').its('status').should('equal', 400)
    cy.get('@alice').then((alice) => {
      login('alice@example.com', 'alice-password').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.id).to.equal(alice.id)
      })
    })
  })

  it('loginFallsBackToEmail', () => {
    register({ username: 'alice', email: 'alice@example.com', password: 'password1' }).then((alice) => {
      login('alice@example.com', 'password1').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.id).to.equal(alice.id)
      })
    })
  })
})

describe('CourseRepositoryTest migrated catalog behavior', () => {
  it('courseKeepsConfiguredPackageValues', () => {
    cy.api('POST', '/api/courses', { code: 'P10', name: 'Package', price: 2500, sessions: 10, bonusSessions: 1, validityDays: 90 })
      .then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body).to.include({ sessions: 10, bonusSessions: 1, price: 2500 })
        cy.api('GET', `/api/courses/${body.id}`).then(({ status: readStatus, body: stored }) => {
          expect(readStatus).to.equal(200)
          expect(stored).to.include({ sessions: 10, bonusSessions: 1, price: 2500 })
        })
      })
  })
})
