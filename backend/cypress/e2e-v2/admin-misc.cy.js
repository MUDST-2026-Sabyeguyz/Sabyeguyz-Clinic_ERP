const month = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()).slice(0, 7)

describe('v2 users, commission schemes, course templates, treatments and lookups', () => {
  describe('users', () => {
    const user = {
      username: 'editme01', password: 'password123', role: 'THERAPIST', employeeCode: 'THP-810',
      fullName: 'ผู้ใช้ แก้ไขได้', branchCode: '01',
    }

    it('edits a user: new username, new password, new name and branch', () => {
      cy.api('POST', '/api/users', user).then(({ body }) => {
        cy.api('PUT', `/api/users/${body.id}`, {
          ...user, username: 'edited01', password: 'newpassword1', fullName: 'ชื่อ ที่เปลี่ยน', branchCode: '02',
        }).then((updated) => {
          expect(updated.status, JSON.stringify(updated.body)).to.equal(200)
          expect(updated.body.username).to.equal('edited01')
          expect(updated.body.branchCode).to.equal('02')
        })
        cy.login('edited01', 'newpassword1').its('status').should('equal', 200)
        cy.login('editme01', 'password123').its('status').should('be.within', 400, 403)
      })
    })

    it('keeps the old password when none is sent and refuses a taken username', () => {
      cy.api('POST', '/api/users', user).then(({ body }) => {
        cy.api('PUT', `/api/users/${body.id}`, { ...user, password: undefined, fullName: 'เปลี่ยนแต่ชื่อ' }).its('status').should('equal', 200)
        cy.login('editme01', 'password123').its('status').should('equal', 200)
        cy.api('PUT', `/api/users/${body.id}`, { ...user, username: 'manager' }).its('status').should('be.within', 400, 409)
      })
    })

    it('deactivates and re-activates a user', () => {
      cy.api('POST', '/api/users', user).then(({ body }) => {
        cy.api('DELETE', `/api/users/${body.id}`).its('status').should('equal', 204)
        cy.login('editme01', 'password123').its('status').should('be.within', 400, 403)
        cy.api('PATCH', `/api/users/${body.id}/activate`).its('body.active').should('equal', true)
        cy.login('editme01', 'password123').its('status').should('equal', 200)
      })
    })
  })

  describe('commission schemes', () => {
    const tiers = [
      { minimum: 0, maximum: 99999.99, rate: 0.04, order: 1 },
      { minimum: 100000, maximum: null, rate: 0.12, order: 2 },
    ]

    it('renames a scheme and moves its start month without touching its tiers; the system sets the end', () => {
      cy.api('POST', '/api/commission/schemes', { name: 'Scheme A', effectiveFrom: '2030-01-01', tiers }).then(({ body }) => {
        // the end of a version is never taken from a request
        cy.api('PUT', `/api/commission/schemes/${body.id}`, { name: 'Scheme A renamed', effectiveFrom: '2030-02-01', effectiveTo: '2030-12-31', tiers: [] })
          .then((updated) => {
            expect(updated.status, JSON.stringify(updated.body)).to.equal(200)
            expect(updated.body.name).to.equal('Scheme A renamed')
            expect(updated.body.effectiveFrom).to.equal('2030-02-01')
            expect(updated.body.effectiveTo).to.equal(null)
          })
        cy.api('GET', `/api/commission/schemes/${body.id}/tiers`).its('body').should('have.length', 2)
        // the version before it follows: it now ends the day before the new start
        cy.api('GET', '/api/commission/schemes').then(({ body: all }) => {
          expect(all.find((scheme) => scheme.name === 'Default Tier Scheme').effectiveTo).to.equal('2030-01-31')
        })
      })
    })

    it('keeps a tier free of any on/off switch: the old endpoints are gone', () => {
      cy.api('POST', '/api/commission/schemes', { name: 'Scheme B', effectiveFrom: '2031-01-01', tiers }).then(({ body }) => {
        cy.api('DELETE', `/api/commission/schemes/${body.id}`).its('status').should('be.within', 404, 405)
        cy.api('PATCH', `/api/commission/schemes/${body.id}/activate`).its('status').should('be.within', 404, 405)
        cy.api('GET', `/api/commission/schemes/${body.id}/tiers`).its('body').then((rows) => {
          expect(rows).to.have.length(2)
          rows.forEach((tier) => expect(tier).not.to.have.property('active'))
        })
      })
    })

    it('lets only one version wait to start, and the second is made by editing the first', () => {
      cy.api('POST', '/api/commission/schemes', { name: 'Scheme A', effectiveFrom: '2030-01-01', tiers }).its('status').should('equal', 201)
      cy.api('POST', '/api/commission/schemes', { name: 'Scheme A', effectiveFrom: '2031-01-01', tiers }).its('status').should('equal', 409)
    })

    it('answers an unknown scheme with an error', () => {
      cy.api('PUT', '/api/commission/schemes/999999', { name: 'x', effectiveFrom: '2030-01-01', tiers: [] })
        .its('status').should('be.within', 400, 404)
    })
  })

  describe('course templates', () => {
    it('edits the price, sessions, bonus and validity of a course template', () => {
      cy.api('POST', '/api/courses', { code: 'COURSE_EDIT', name: 'คอร์สแก้ไข', price: 6000, sessions: 6, bonusSessions: 0, validityDays: 90 })
        .then(({ body }) => {
          cy.api('PUT', `/api/courses/${body.id}`, { code: 'COURSE_EDIT', name: 'คอร์สแก้ไขแล้ว', price: 6500, sessions: 7, bonusSessions: 1, validityDays: 120 })
            .then((updated) => {
              expect(updated.status, JSON.stringify(updated.body)).to.equal(200)
              expect(updated.body.name).to.equal('คอร์สแก้ไขแล้ว')
              expect(Number(updated.body.price)).to.equal(6500)
              expect(updated.body.sessions).to.equal(7)
              expect(updated.body.bonusSessions).to.equal(1)
              expect(updated.body.validityDays).to.equal(120)
            })
        })
    })

    it('lists the course templates and the managed services', () => {
      cy.api('GET', '/api/courses').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.map((course) => course.code)).to.include.members(['COURSE_10', 'COURSE_5', 'CYPRESS'])
      })
      cy.api('GET', '/api/services').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.map((service) => service.code)).to.include.members(['PHYSIO_30', 'PHYSIO_60'])
      })
    })

    it('does not change the price of a course that was already sold', function () {
      cy.registerPatient().then((patient) => {
        cy.api('POST', '/api/course-purchases', { patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01' })
          .then(({ body: purchase }) => {
            expect(Number(purchase.price)).to.equal(9000)
            cy.api('PUT', `/api/courses/${this.fixtures.courseId}`,
              { code: 'CYPRESS', name: 'คอร์สทดสอบ API', price: 12000, sessions: 5, bonusSessions: 1, validityDays: 60 })
              .its('status').should('equal', 200)
            cy.api('GET', `/api/course-purchases/${purchase.id}`).its('body.purchase.price').then((price) => {
              expect(Number(price)).to.equal(9000)
            })
          })
      })
    })
  })

  describe('treatments', () => {
    function checkedInVisit(fx, patient) {
      return cy.api('POST', '/api/appointments', {
        branchCode: '01', patientId: patient.id, therapistId: fx.therapistId, serviceId: fx.serviceId,
        startAt: `${fx.tomorrow}T09:00:00+07:00`,
      }).then(({ body }) => {
        cy.api('POST', `/api/appointments/${body.id}/check-in`).its('status').should('equal', 200)
        return cy.wrap(body)
      })
    }

    it('records a treatment note for a checked-in appointment and lists it for the patient', function () {
      cy.registerPatient().then((patient) => {
        checkedInVisit(this.fixtures, patient).then((appointment) => {
          cy.api('POST', '/api/treatments', {
            patientId: patient.id, appointmentId: appointment.id, staffId: this.fixtures.therapistId,
            serviceId: this.fixtures.serviceId, note: 'ปวดหลังดีขึ้น',
          }).then(({ status, body }) => {
            expect(status, JSON.stringify(body)).to.equal(201)
            expect(body.patientId).to.equal(patient.id)
          })
          cy.api('GET', `/api/treatments/patients/${patient.id}`).then(({ status, body }) => {
            expect(status).to.equal(200)
            expect(body).to.have.length(1)
          })
        })
      })
    })

    it('refuses a treatment before check-in, without an appointment, or for the wrong patient', function () {
      cy.registerPatient().then((patient) => {
        cy.registerPatient({ firstName: 'คนอื่น', phone: '0822222222', identityNumber: '1100000000102' }).then((other) => {
          cy.api('POST', '/api/appointments', {
            branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId,
            serviceId: this.fixtures.serviceId, startAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
          }).then(({ body: appointment }) => {
            const treatment = (overrides) => cy.api('POST', '/api/treatments', {
              patientId: patient.id, appointmentId: appointment.id, staffId: this.fixtures.therapistId,
              serviceId: this.fixtures.serviceId, note: 'x', ...overrides,
            })
            treatment({}).its('status').should('equal', 400)
            treatment({ appointmentId: null }).its('status').should('equal', 400)
            cy.api('POST', `/api/appointments/${appointment.id}/check-in`).its('status').should('equal', 200)
            treatment({ patientId: other.id }).its('status').should('equal', 400)
            treatment({}).its('status').should('equal', 201)
          })
        })
      })
    })

    it('lists the single-visit services', () => {
      cy.api('GET', '/api/treatments/services').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.map((service) => service.code)).to.include.members(['PHYSIO_30', 'PHYSIO_60'])
      })
    })
  })

  describe('lookups and course catalog', () => {
    it('lists services and therapists for dropdowns, filtered by branch', () => {
      cy.api('GET', '/api/lookups/services').then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.map((service) => service.code)).to.include('PHYSIO_30')
      })
      cy.api('GET', '/api/lookups/therapists').its('body.length').should('be.at.least', 3)
      cy.api('GET', '/api/lookups/therapists?branchCode=02').then(({ body }) => {
        expect(body.every((row) => row.branchCode === '02')).to.equal(true)
        expect(body.map((row) => row.code)).to.include('THP-002')
      })
    })

    it('closing a month is allowed only for a manager', () => {
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        cy.apiAs(auth.token, 'POST', `/api/commission/closings/${month}-01`).its('status').should('equal', 403)
      })
    })
  })
})
