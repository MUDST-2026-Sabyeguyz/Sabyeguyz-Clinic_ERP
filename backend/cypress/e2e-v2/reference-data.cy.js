describe('v2 reference data: branches, staff, payment methods, master data, rooms', () => {
  describe('branches', () => {
    it('creates a branch, reads it back and lists it', () => {
      cy.api('POST', '/api/branches', { branchCode: '03', name: 'สาขาทดสอบ', address: '1 ถนนทดสอบ', phone: '02-000-0003' })
        .then(({ status, body }) => {
          expect(status).to.equal(201)
          expect(body.branchCode).to.equal('03')
          expect(body.active).to.equal(true)
          cy.api('GET', `/api/branches/${body.id}`).its('body.name').should('equal', 'สาขาทดสอบ')
          cy.api('GET', '/api/branches').its('body').should('have.length', 3)
        })
    })

    it('updates name, address and phone but never the branch code (it is part of every HN)', () => {
      cy.api('POST', '/api/branches', { branchCode: '04', name: 'เดิม', address: 'ที่อยู่เดิม', phone: '02-000-0004' })
        .then(({ body }) => {
          cy.api('PUT', `/api/branches/${body.id}`, { branchCode: '04', name: 'ใหม่', address: 'ที่อยู่ใหม่', phone: '02-111-1111' })
            .then((updated) => {
              expect(updated.status).to.equal(200)
              expect(updated.body.name).to.equal('ใหม่')
              expect(updated.body.phone).to.equal('02-111-1111')
            })
          cy.api('PUT', `/api/branches/${body.id}`, { branchCode: '05', name: 'x', address: 'x', phone: 'x' })
            .its('status').should('equal', 400)
        })
    })

    it('rejects a branch code that is not 2 digits or is already used', () => {
      const branch = { name: 'x', address: 'x', phone: '02-000-0000' }
      cy.api('POST', '/api/branches', { ...branch, branchCode: '3' }).its('status').should('equal', 400)
      cy.api('POST', '/api/branches', { ...branch, branchCode: 'AB' }).its('status').should('equal', 400)
      cy.api('POST', '/api/branches', { ...branch, branchCode: '01' }).its('status').should('be.within', 400, 409)
    })

    it('deactivates a branch and answers 404 for an unknown one', () => {
      cy.api('POST', '/api/branches', { branchCode: '06', name: 'ปิดทีหลัง', address: 'x', phone: '02-000-0006' })
        .then(({ body }) => {
          cy.api('DELETE', `/api/branches/${body.id}`).its('status').should('equal', 204)
          cy.api('GET', `/api/branches/${body.id}`).its('body.active').should('equal', false)
        })
      cy.api('GET', '/api/branches/999999').its('status').should('be.within', 400, 404)
    })

    it('is manager-only for changes but readable by a therapist', () => {
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        cy.apiAs(auth.token, 'GET', '/api/branches').its('status').should('equal', 200)
        cy.apiAs(auth.token, 'POST', '/api/branches',
          { branchCode: '07', name: 'x', address: 'x', phone: '02-000-0007' }).its('status').should('equal', 403)
      })
    })
  })

  describe('staff', () => {
    const staff = { employeeCode: 'THP-777', fullName: 'พนักงาน ทดสอบ', phone: '0812345678', branchCode: '01', position: 'Therapist' }

    it('creates a staff member, lists and reads them', () => {
      cy.api('POST', '/api/staff', staff).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.employeeCode).to.equal('THP-777')
        expect(body.position).to.equal('Therapist')
        expect(body.branchCode).to.equal('01')
        expect(body.active).to.equal(true)
        cy.api('GET', '/api/staff').its('body').should('satisfy', (rows) => rows.some((row) => row.id === body.id))
        cy.api('GET', `/api/staff/${body.id}`).its('body.fullName').should('contain', 'พนักงาน')
      })
    })

    it('updates name and branch of a staff member', () => {
      cy.api('POST', '/api/staff', staff).then(({ body }) => {
        cy.api('PUT', `/api/staff/${body.id}`, { ...staff, fullName: 'ชื่อ ใหม่', branchCode: '02', position: 'Manager' })
          .then((updated) => {
            expect(updated.status).to.equal(200)
            expect(updated.body.fullName).to.equal('ชื่อ ใหม่')
            expect(updated.body.branchCode).to.equal('02')
            expect(updated.body.position).to.equal('Manager')
          })
      })
    })

    it('rejects a duplicate employee code, an unknown branch and a missing name', () => {
      cy.api('POST', '/api/staff', staff).its('status').should('equal', 201)
      cy.api('POST', '/api/staff', staff).its('status').should('be.within', 400, 409)
      cy.api('POST', '/api/staff', { ...staff, employeeCode: 'THP-778', branchCode: '99' }).its('status').should('equal', 400)
      cy.api('POST', '/api/staff', { ...staff, employeeCode: 'THP-779', fullName: '' }).its('status').should('equal', 400)
    })
  })

  describe('payment methods', () => {
    it('creates, renames, deactivates and re-activates a payment method', () => {
      cy.api('POST', '/api/payment-methods', { code: 'GIFT', name: 'บัตรของขวัญ' }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.name).to.equal('บัตรของขวัญ')
        expect(body.active).to.equal(true)
        cy.api('PUT', `/api/payment-methods/${body.id}`, { code: 'GIFT', name: 'บัตรกำนัล' }).its('body.name').should('equal', 'บัตรกำนัล')
        cy.api('DELETE', `/api/payment-methods/${body.id}`).its('status').should('equal', 204)
        cy.api('GET', '/api/payment-methods').its('body').then((rows) => {
          expect(rows.find((row) => row.id === body.id).active).to.equal(false)
        })
        cy.api('PATCH', `/api/payment-methods/${body.id}/activate`).its('body.active').should('equal', true)
      })
    })

    it('a new payment method can be used to pay a sale', function () {
      cy.api('POST', '/api/payment-methods', { code: 'VOUCHER', name: 'บัตรกำนัลพิเศษ' }).its('status').should('equal', 201)
      cy.registerPatient().then((patient) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id, branchCode: '01',
          items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'บัตรกำนัลพิเศษ', amount: 800 }],
        }).its('status').should('equal', 201)
      })
    })

    it('rejects a payment method without a name', () => {
      cy.api('POST', '/api/payment-methods', { code: 'X', name: '' }).its('status').should('equal', 400)
    })
  })

  describe('master data', () => {
    it('creates a value in a new category, lists it, renames, deactivates and re-activates it', () => {
      cy.api('POST', '/api/master-data', { category: 'Hobby', code: 'RUN', name: 'วิ่ง' }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.category).to.equal('Hobby')
        expect(body.name).to.equal('วิ่ง')
        expect(body.active).to.equal(true)
        cy.api('PUT', `/api/master-data/${body.id}`, { category: 'Hobby', code: 'RUN', name: 'วิ่งมาราธอน' })
          .its('body.name').should('equal', 'วิ่งมาราธอน')
        cy.api('DELETE', `/api/master-data/${body.id}`).its('status').should('equal', 204)
        cy.api('GET', '/api/master-data').its('body').then((rows) => {
          expect(rows.find((row) => row.id === body.id).active).to.equal(false)
        })
        cy.api('PATCH', `/api/master-data/${body.id}/activate`).its('body.active').should('equal', true)
      })
    })

    it('lists the seeded customer groups and referral channels', () => {
      cy.api('GET', '/api/master-data').then(({ status, body }) => {
        expect(status).to.equal(200)
        const names = body.map((row) => `${row.category}:${row.name}`)
        expect(names).to.include('Customer Group:VIP')
        expect(names).to.include('Referral Channel:Facebook')
      })
    })

    it('rejects a value without a category or a name', () => {
      cy.api('POST', '/api/master-data', { category: '', name: 'x' }).its('status').should('equal', 400)
      cy.api('POST', '/api/master-data', { category: 'Hobby', name: '' }).its('status').should('equal', 400)
    })
  })

  describe('permissions', () => {
    it('a therapist can read every reference list but change none of them', function () {
      cy.login('therapist', 'therapist123').then(({ body: auth }) => {
        const as = (method, url, body) => cy.apiAs(auth.token, method, url, body).its('status')
        for (const url of ['/api/branches', '/api/staff', '/api/payment-methods', '/api/master-data', '/api/resources']) {
          as('GET', url).should('equal', 200)
        }
        as('POST', '/api/staff', { employeeCode: 'X-1', fullName: 'x y', branchCode: '01', position: 'Therapist' }).should('equal', 403)
        as('PUT', `/api/staff/${this.fixtures.therapistId}`, { employeeCode: 'THP-001', fullName: 'x y', branchCode: '01', position: 'Therapist' }).should('equal', 403)
        as('POST', '/api/payment-methods', { code: 'X', name: 'x' }).should('equal', 403)
        as('DELETE', '/api/payment-methods/1').should('equal', 403)
        as('POST', '/api/master-data', { category: 'Hobby', name: 'x' }).should('equal', 403)
        as('DELETE', '/api/master-data/1').should('equal', 403)
        as('POST', '/api/resources', { branchCode: '01', name: 'x', type: 'Treatment' }).should('equal', 403)
        as('DELETE', '/api/resources/1').should('equal', 403)
        as('DELETE', '/api/branches/1').should('equal', 403)
      })
    })
  })

  describe('rooms and resources', () => {
    it('creates a room in a branch, renames, deactivates and re-activates it', () => {
      cy.api('POST', '/api/resources', { branchCode: '01', code: 'R-9', name: 'ห้องใหม่', type: 'Treatment' }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body.branchCode).to.equal('01')
        expect(body.name).to.equal('ห้องใหม่')
        expect(body.active).to.equal(true)
        cy.api('PUT', `/api/resources/${body.id}`, { branchCode: '01', code: 'R-9', name: 'ห้องเปลี่ยนชื่อ', type: 'Exercise' })
          .then((updated) => {
            expect(updated.body.name).to.equal('ห้องเปลี่ยนชื่อ')
            expect(updated.body.type).to.equal('Exercise')
          })
        cy.api('DELETE', `/api/resources/${body.id}`).its('status').should('equal', 204)
        cy.api('PATCH', `/api/resources/${body.id}/activate`).its('body.active').should('equal', true)
        cy.api('GET', '/api/resources').its('body').should('satisfy', (rows) => rows.some((row) => row.id === body.id))
      })
    })

    it('rejects an unknown branch and lists the seeded rooms', () => {
      cy.api('POST', '/api/resources', { branchCode: '99', code: 'X', name: 'x', type: 'Treatment' }).its('status').should('equal', 400)
      cy.api('GET', '/api/resources').its('body.length').should('be.at.least', 4)
    })
  })
})
