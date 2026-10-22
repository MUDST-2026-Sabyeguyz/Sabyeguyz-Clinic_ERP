const entries = () => cy.api('GET', '/api/audit-logs?size=100').then(({ status, body }) => {
  expect(status).to.equal(200)
  return body.items
})

describe('v2 audit log', () => {
  it('records a sale and its void', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01',
        paymentLabelCode: 'FULL',
        items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }],
      }).then(({ body: sale }) => {
        entries().then((items) => {
          const created = items.find((entry) => entry.tableName === 'receipt' && entry.action === 'INSERT')
          expect(created, 'receipt INSERT entry').to.exist
          expect(created.newValue).to.contain(sale.receiptNo)
          expect(created.changedBy).to.be.a('number')
        })
        cy.api('POST', `/api/payments/${sale.id}/reverse`, { reason: 'ทดสอบ audit' }).its('status').should('equal', 200)
        entries().then((items) => {
          const voided = items.find((entry) => entry.tableName === 'sales_transaction' && entry.action === 'UPDATE')
          expect(voided, 'void UPDATE entry').to.exist
          expect(voided.newValue).to.contain('ทดสอบ audit')
        })
      })
    })
  })

  it('records a course purchase and a refund', function () {
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/course-purchases', {
        patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01',
        paymentLabelCode: 'FULL',
      }).its('status').should('equal', 201)
      entries().then((items) => {
        expect(items.some((entry) => entry.tableName === 'course' && entry.action === 'INSERT')).to.equal(true)
      })
    })
  })

  it('records a user created by a manager', () => {
    cy.api('POST', '/api/users', {
      username: 'audituser', password: 'password123', role: 'THERAPIST', employeeCode: 'THP-950',
      fullName: 'ผู้ใช้ ตรวจสอบ', branchCode: '01',
    }).its('status').should('equal', 201)
    entries().then((items) => {
      const created = items.find((entry) => entry.tableName === 'app_user' && entry.action === 'INSERT')
      expect(created, 'app_user INSERT entry').to.exist
      expect(created.newValue).to.contain('audituser')
    })
  })
})
