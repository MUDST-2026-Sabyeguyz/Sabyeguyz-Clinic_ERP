const at = (fx, time = '09:00') => `${fx.tomorrow}T${time}:00+07:00`

describe('appointment branch authorization (R1–R3)', () => {
  beforeEach(function () {
    cy.configureBookingWindows()
    cy.login('therapist', 'therapist123').its('body.token').as('staff01')
    cy.login('therapist2', 'therapist123').its('body.token').as('staff02')
    cy.registerPatient({ branchCode: '02' }).as('patient02').then((patient) => {
      for (const [branchCode, therapistId, alias] of [
        ['01', this.fixtures.therapistId, 'appointment01'],
        ['02', this.fixtures.therapist2Id, 'appointment02'],
      ]) {
        cy.api('POST', '/api/appointments', {
          branchCode, therapistId, patientId: patient.id, serviceId: this.fixtures.serviceId,
          startAt: at(this.fixtures), endAt: at(this.fixtures, '10:00'),
        }).then(({ status, body }) => {
          expect(status).to.equal(201)
          cy.wrap(body).as(alias)
        })
      }
    })
  })

  for (const [tokenAlias, branchCode, appointmentAlias] of [
    ['staff01', '01', 'appointment01'], ['staff02', '02', 'appointment02'],
  ]) {
    it(`limits ${branchCode} lists, counts and reads to the home branch even with legacy multi-branch grants`, function () {
      cy.apiAs(this[tokenAlias], 'GET', '/api/appointments?size=1').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.totalItems).to.equal(1)
        expect(body.totalPages).to.equal(1)
        expect(body.items.map((row) => row.id)).to.deep.equal([this[appointmentAlias].id])
        expect(body.items[0].branchCode).to.equal(branchCode)
      })
      cy.apiAs(this[tokenAlias], 'GET', `/api/appointments/${this[appointmentAlias].id}`)
        .its('status').should('equal', 200)
    })
  }

  it('rejects explicit cross-branch queries and cannot bypass the implicit filter via therapist/date/status/page', function () {
    cy.apiAs(this.staff01, 'GET', '/api/appointments?branchCode=02').its('status').should('equal', 403)
    cy.apiAs(this.staff01, 'GET',
      `/api/appointments?therapistId=${this.fixtures.therapist2Id}&date=${this.fixtures.tomorrow}&status=CONFIRMED`)
      .then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.totalItems).to.equal(0)
        expect(body.items).to.deep.equal([])
      })
    cy.apiAs(this.staff01, 'GET', '/api/appointments?page=1&size=1').its('body.items').should('have.length', 0)
    cy.apiAs(this.staff02, 'GET', '/api/appointments?branchCode=01').its('status').should('equal', 403)
  })

  it('rejects a foreign appointment by ID in both directions', function () {
    cy.apiAs(this.staff01, 'GET', `/api/appointments/${this.appointment02.id}`).its('status').should('equal', 403)
    cy.apiAs(this.staff02, 'GET', `/api/appointments/${this.appointment01.id}`).its('status').should('equal', 403)
  })

  it('rejects cross-branch CURRENT and NEW creation before accepting patient or therapist payloads', function () {
    const payload = {
      branchCode: '02', therapistId: this.fixtures.therapist2Id, patientId: this.patient02.id,
      serviceId: this.fixtures.serviceId, startAt: at(this.fixtures, '10:00'), endAt: at(this.fixtures, '11:00'),
    }
    cy.apiAs(this.staff01, 'POST', '/api/appointments', payload).its('status').should('equal', 403)
    cy.apiAs(this.staff01, 'POST', '/api/appointments', {
      ...payload, therapistId: this.fixtures.therapistId, patientId: 999999,
    }).its('status').should('equal', 403)
    cy.apiAs(this.staff01, 'POST', '/api/appointments', {
      ...payload, patientType: 'NEW', patientId: null, contactName: 'นัดใหม่ สอง', contactPhone: '0899999999',
    }).its('status').should('equal', 403)
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 2)
  })

  for (const action of ['confirm', 'link-patient', 'check-in', 'start-service', 'complete', 'no-show', 'cancel', 'reschedule']) {
    it(`rejects cross-branch ${action} before state/payload processing and leaves the appointment unchanged`, function () {
      const id = this.appointment02.id
      cy.api('GET', `/api/appointments/${id}`).then(({ body: before }) => {
        const method = action === 'reschedule' ? 'PATCH' : 'POST'
        const path = action === 'reschedule' ? `/api/appointments/${id}` : `/api/appointments/${id}/${action}`
        cy.apiAs(this.staff01, method, path, {
          patientId: this.patient02.id, therapistId: this.fixtures.therapistId,
          startAt: at(this.fixtures, '14:00'), endAt: at(this.fixtures, '15:00'), reason: 'cross-branch attempt',
        }).its('status').should('equal', 403)
        cy.api('GET', `/api/appointments/${id}`).its('body').should('deep.equal', before)
      })
    })
  }

  it('lets a therapist book their own time and confirms only after successful paid service', function () {
    cy.apiAs(this.staff01, 'POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.therapistId,
      patientId: this.patient02.id,
      serviceId: this.fixtures.serviceId,
      startAt: at(this.fixtures, '10:00'),
      endAt: at(this.fixtures, '11:00'),
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      expect(body.status).to.equal('UNCONFIRMED')

      for (const action of ['check-in', 'start-service', 'complete']) {
        cy.apiAs(this.staff01, 'POST', `/api/appointments/${body.id}/${action}`).its('status').should('equal', 403)
      }

      cy.api('POST', '/api/payments', {
        patientId: this.patient02.id,
        branchCode: '01',
        appointmentId: body.id,
        treatingStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
        items: [{type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1,}],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
      }).its('status').should('equal', 201)

      cy.api('GET', `/api/appointments/${body.id}`).its('body.status').should('equal', 'CONFIRMED')
    })
    cy.apiAs(this.staff01, 'GET', `/api/patients/${this.patient02.id}`).its('status').should('equal', 200)
    cy.apiAs(this.staff01, 'GET', `/api/patients?query=${this.patient02.hn}`).its('body.items')
        .should('satisfy', (items) => items.some((patient) => patient.id === this.patient02.id))
  })

  it('allows a therapist to move and cancel a home-branch appointment but not move it to another branch', function () {
    cy.apiAs(this.staff01, 'POST', '/api/appointments', {
      branchCode: '01',
      therapistId: this.fixtures.managerStaffId,
      patientId: this.patient02.id,
      serviceId: this.fixtures.serviceId,
      startAt: at(this.fixtures, '13:00'),
      endAt: at(this.fixtures, '14:00'),
    }).its('status').should('equal', 403)

    const id = this.appointment01.id

    for (const action of ['confirm', 'no-show']) {
      cy.apiAs(this.staff01, 'POST', `/api/appointments/${id}/${action}`, {
        reason: 'therapist',
      }).its('status').should('equal', 403)
    }

    cy.apiAs(this.staff01, 'PATCH', `/api/appointments/${id}`, {
      branchCode: '02',
      therapistId: this.fixtures.therapist2Id,
      startAt: at(this.fixtures, '15:00'),
      endAt: at(this.fixtures, '16:00'),
    }).its('status').should('equal', 403)

    cy.apiAs(this.staff01, 'PATCH', `/api/appointments/${id}`, {
      startAt: at(this.fixtures, '15:00'),
      endAt: at(this.fixtures, '16:00'),
    }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.branchCode).to.equal('01')
      expect(body.status).to.equal('UNCONFIRMED')
      expect(new Date(body.startAt).toISOString())
          .to.equal(new Date(at(this.fixtures, '15:00')).toISOString())
    })

    cy.apiAs(this.staff01, 'POST', `/api/appointments/${id}/cancel`, {
      reason: 'ผู้ป่วยขอยกเลิก',
    }).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body.status).to.equal('CANCELLED')
    })
  })

  it('allows Manager reads, filtering and writes across both branches', function () {
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 2)
    cy.api('GET', '/api/appointments?branchCode=02').its('body.items.0.id').should('equal', this.appointment02.id)
    cy.api('PATCH', `/api/appointments/${this.appointment02.id}`, { startAt: at(this.fixtures, '14:00'), endAt: at(this.fixtures, '15:00') })
      .its('status').should('equal', 200)
    cy.api('POST', `/api/appointments/${this.appointment02.id}/cancel`, { reason: 'Manager cross-branch' })
      .its('status').should('equal', 200)
  })

  it('rejects a therapist from another branch for CURRENT and NEW bookings, including Manager requests', function () {
    const payload = {
      branchCode: '01', therapistId: this.fixtures.therapist2Id, patientId: this.patient02.id,
      serviceId: this.fixtures.serviceId, startAt: at(this.fixtures, '11:00'), endAt: at(this.fixtures, '12:00'),
    }
    cy.api('POST', '/api/appointments', payload).its('status').should('equal', 400)
    cy.apiAs(this.staff01, 'POST', '/api/appointments', payload).its('status').should('equal', 400)
    cy.api('POST', '/api/appointments', {
      ...payload, patientType: 'NEW', patientId: null, contactName: 'ใหม่ หนึ่ง', contactPhone: '0811111111',
    }).its('status').should('equal', 400)
    cy.api('GET', '/api/appointments').its('body.totalItems').should('equal', 2)
  })

  it('requires authentication and denies a user without a linked staff branch', function () {
    cy.apiAs(null, 'GET', '/api/appointments').its('status').should('equal', 401)
    cy.apiAs(null, 'GET', `/api/appointments/${this.appointment02.id}`).its('status').should('equal', 401)
    cy.api('POST', '/api/auth/register', {
      username: 'branchless',
      email: 'branchless@example.com',
<<<<<<< HEAD
      password: 'password123',
=======
      password: 'password12345',
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
    })
      .then(({ status, body }) => {
        expect(status).to.equal(201)
        cy.apiAs(body.token, 'GET', '/api/appointments').its('status').should('equal', 403)
        cy.apiAs(body.token, 'GET', `/api/appointments/${this.appointment01.id}`).its('status').should('equal', 403)
      })
  })

  it('uses current database roles and home branch rather than stale token privileges or old branch grants', function () {
    const profile = {
<<<<<<< HEAD
      username: 'branch-policy-user', email: 'branch-policy-user@example.com', password: 'password123', role: 'MANAGER', employeeCode: 'POLICY-01',
=======
      username: 'branch-policy-user', email: 'branch-policy-user@example.com', password: 'password12345', role: 'MANAGER', employeeCode: 'POLICY-01',
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
      fullName: 'Policy Actor', branchCode: '01',
    }
    cy.api('POST', '/api/users', profile).then(({ status, body: user }) => {
      expect(status).to.equal(201)
      cy.login(profile.username, profile.password).then(({ body: auth }) => {
        cy.apiAs(auth.token, 'GET', `/api/appointments/${this.appointment02.id}`).its('status').should('equal', 200)
        cy.api('PUT', `/api/users/${user.id}`, { ...profile, role: 'THERAPIST', branchCode: '02' })
          .its('status').should('equal', 200)
        cy.apiAs(auth.token, 'GET', `/api/appointments/${this.appointment01.id}`).its('status').should('equal', 403)
        cy.apiAs(auth.token, 'GET', '/api/appointments').its('body.items.0.branchCode').should('equal', '02')
        cy.api('DELETE', `/api/users/${user.id}`).its('status').should('equal', 204)
        cy.apiAs(auth.token, 'GET', '/api/appointments').its('status').should('equal', 401)
      })
    })
  })
})
