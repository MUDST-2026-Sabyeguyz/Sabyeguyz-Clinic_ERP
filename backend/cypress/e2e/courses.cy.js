describe('Course purchase and ledger API', () => {
  beforeEach(function () {
    cy.registerPatient().then((patient) => {
      cy.wrap({ patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01' }).as('purchaseBody')
    })
  })

  function purchase(body) {
    return cy.api('POST', '/api/course-purchases', body).then(({ status, body: purchase }) => {
      expect(status).to.equal(201)
      return purchase
    })
  }

  it('purchases a course with Location and six usable sessions', function () {
    cy.api('POST', '/api/course-purchases', this.purchaseBody).then(({ status, headers, body }) => {
      expect(status).to.equal(201)
      expect(headers.location).to.include(`/api/course-purchases/${body.id}`)
      expect(body).to.include({ purchased: 6, remaining: 6, used: 0, usable: true, status: 'ACTIVE' })
      expect(body.patient.hn).not.to.be.empty
    })
  })

  it('rejects a purchase without a patient ID', function () {
    cy.api('POST', '/api/course-purchases', { courseId: this.fixtures.courseId, branchCode: '01' })
      .then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.fields).to.have.property('patientId')
      })
  })

  it('returns 404 for an unknown patient', function () {
    cy.api('POST', '/api/course-purchases', { ...this.purchaseBody, patientId: 999999 })
      .its('status').should('equal', 404)
  })

  it('consumes a session and exposes the purchase and usage ledger', function () {
    purchase(this.purchaseBody).then(({ id }) => {
      cy.api('POST', '/api/course-usages', { coursePurchaseId: id, note: 'ครั้งที่ 1' }).then(({ status, body }) => {
        expect(status).to.equal(201)
        expect(body).to.include({ used: 1, remaining: 5 })
      })
      cy.api('GET', `/api/course-purchases/${id}`).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body.movements).to.have.length(2)
        expect(body.movements[0]).to.include({ type: 'PURCHASE', balanceAfter: 6 })
        expect(body.movements[1]).to.include({ type: 'USAGE', balanceAfter: 5 })
      })
    })
  })

  it('rejects usage after all six sessions have been consumed', function () {
    purchase(this.purchaseBody).then(({ id }) => {
      for (let session = 0; session < 6; session++) {
        cy.api('POST', '/api/course-usages', { coursePurchaseId: id }).its('status').should('equal', 201)
      }
      cy.api('POST', '/api/course-usages', { coursePurchaseId: id }).then(({ status, body }) => {
        expect(status).to.equal(409)
        expect(body.message).to.be.a('string').and.not.be.empty
      })
    })
  })

  it('lists the courses owned by a patient', function () {
    purchase(this.purchaseBody)
    cy.api('GET', `/api/patients/${this.purchaseBody.patientId}/courses`).then(({ status, body }) => {
      expect(status).to.equal(200)
      expect(body).to.have.length(1)
      expect(body[0].course.name).to.equal('คอร์สทดสอบ API')
    })
  })

  it('cancels a course with a reason and clears its balance', function () {
    purchase(this.purchaseBody).then(({ id }) => {
      cy.api('POST', `/api/course-purchases/${id}/cancel`, { reason: 'ผู้ป่วยขอยกเลิก' }).then(({ status, body }) => {
        expect(status).to.equal(200)
        expect(body).to.include({ status: 'CANCELLED', remaining: 0 })
      })
    })
  })

  it('rejects cancellation without a reason', function () {
    purchase(this.purchaseBody).then(({ id }) => {
      cy.api('POST', `/api/course-purchases/${id}/cancel`, { reason: '   ' }).then(({ status, body }) => {
        expect(status).to.equal(400)
        expect(body.fields).to.have.property('reason')
      })
    })
  })

  it('returns 404 for a missing purchase', () => {
    cy.api('GET', '/api/course-purchases/999999').its('status').should('equal', 404)
  })
})
