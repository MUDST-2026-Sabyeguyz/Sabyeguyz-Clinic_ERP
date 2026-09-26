const master = { code: 'FINANCIAL', name: 'คอร์สกายภาพ 10 ครั้ง', price: 15000, sessions: 10, bonusSessions: 3, validityDays: 90 }
const ok = (status) => (response) => { expect(response.status).to.equal(status); return response.body }
const error = (status, message) => (response) => {
  expect(response.status).to.equal(status)
  if (message) expect(response.body.message).to.contain(message)
}

describe('SaleService rules migrated from JUnit', () => {
  let patientId, courseId, assessmentId
  beforeEach(() => {
    cy.registerPatient().then((p) => { patientId = p.id })
    cy.api('POST', '/api/courses', master).then(ok(201)).then((c) => { courseId = c.id })
    cy.api('GET', '/api/catalog/services').then(ok(200)).then((services) => {
      assessmentId = services.find((s) => s.code === 'ASSESSMENT').id
    })
  })
  const assessment = (quantity = 1) => ({ type: 'ASSESSMENT', referenceId: assessmentId, quantity })
  const course = (quantity = 1) => ({ type: 'COURSE', referenceId: courseId, quantity })
  const payment = (paymentMethodCode, amount) => ({ paymentMethodCode, amount })
  const sell = (items = [course()], payments = [payment('CASH', 15000)], overrides = {}) =>
    cy.api('POST', '/api/payments', { patientId, branchCode: '01', items, payments, ...overrides })
  const paid = (...args) => sell(...args).then(ok(201))
  const voidSale = (id, reason = 'ยกเลิก') => cy.api('POST', `/api/payments/${id}/reverse`, { reason })
  const ledger = (id) => cy.api('GET', `/api/course-purchases/${id}`).then(ok(200))
  const use = (id, reason = null) => cy.api('POST', '/api/course-usages', { coursePurchaseId: id, appointmentId: null, note: reason }).then(ok(201))
  const cancel = (id) => cy.api('POST', `/api/course-purchases/${id}/cancel`, { reason: 'ยกเลิกคอร์สก่อน' }).then(ok(200))
  const list = (filter = `?patientId=${patientId}`) => cy.api('GET', `/api/payments${filter}`).then(ok(200))

  it('pricesEveryLineFromTheMasterAndIssuesAReceipt', () => {
    paid([assessment()], [payment('CASH', 1200)]).then((s) => {
      expect(s.total).to.equal(1200); expect(s.paid).to.equal(1200); expect(s.status).to.equal('PAID')
      expect(s.items).to.have.length(1); expect(s.items[0].unitPrice).to.equal(1200)
    })
  })
  it('receiptNumberCarriesYearBranchAndMonth', () => {
    paid([assessment()], [payment('CASH', 1200)]).its('receiptNo').should('match', /^RC\d{10}$/)
  })
  it('multipliesUnitPriceByQuantity', () => {
    paid([assessment(3)], [payment('CASH', 3600)]).then((s) => {
      expect(s.total).to.equal(3600); expect(s.items[0].lineTotal).to.equal(3600)
    })
  })
  it('acceptsPaymentSplitAcrossSeveralMethods', () => {
    paid([course()], [payment('CASH', 5000), payment('QR', 10000)]).then((s) => {
      expect(s.paid).to.equal(15000); expect(s.payments.map((p) => p.paymentMethodCode)).to.deep.equal(['CASH', 'QR'])
    })
  })
  it('rejectsPaymentsThatDoNotAddUpToTheTotal', () => {
    sell([course()], [payment('CASH', 14000)]).then(error(400, 'ไม่ตรงกับยอดที่ต้องชำระ'))
  })
  it('rejectsOverpayment', () => { sell([assessment()], [payment('CASH', 2000)]).then(error(400)) })
  it('sellingACourseCreditsItsSessionsImmediately', () => {
    paid().then((s) => {
      expect(s.items[0].coursePurchaseId).not.to.be.null
      ledger(s.items[0].coursePurchaseId).its('purchase.remaining').should('equal', 13)
    })
  })
  it('sellingACourseAndUsingItOnTheSameDayWorks', () => {
    paid().then((s) => {
      const id = s.items[0].coursePurchaseId
      use(id, 'ใช้วันเดียวกับที่ซื้อ'); ledger(id).its('purchase.used').should('equal', 1)
    })
  })
  it('sellsAServiceAndACourseOnTheSameReceipt', () => {
    paid([assessment(), course()], [payment('CASH', 16200)]).then((s) => {
      expect(s.total).to.equal(16200); expect(s.items).to.have.length(2)
      expect(s.items[0].coursePurchaseId).to.be.null; expect(s.items[1].coursePurchaseId).not.to.be.null
    })
  })
  it('storesTheSalespersonSeparatelyFromTheTreatingStaff', () => {
    cy.api('POST', '/__cypress/financial-fixtures/salesperson').then(ok(200)).then(({ id }) => {
      paid([assessment()], [payment('CASH', 1200)], { salespersonStaffId: id }).then((s) => {
        expect(s.salesperson).not.to.be.null; expect(s.salesperson.id).to.equal(id)
      })
    })
  })
  it('rejectsSellingMoreThanOneCourseOnASingleLine', () => {
    sell([course(2)], [payment('CASH', 30000)]).then(error(400, 'ทีละ 1 ใบ'))
  })
  it('rejectsAnInactiveCourse', () => {
    cy.api('DELETE', `/api/courses/${courseId}`).then(ok(204))
    sell().then(error(400, 'ปิดการใช้งาน'))
  })
  it('rejectsAServiceThatHasNoPriceYet', () => {
    cy.api('POST', '/__cypress/financial-fixtures/unpriced-service').then(ok(200)).then(({ id }) => {
      sell([{ type: 'SINGLE_VISIT', referenceId: id, quantity: 1 }], [payment('CASH', 500)]).then(error(400, 'ยังไม่ได้ตั้งราคา'))
    })
  })
  it('rejectsAnUnknownPaymentMethod', () => {
    sell([assessment()], [payment('BITCOIN', 1200)]).then(error(400, 'ไม่พบช่องทางชำระเงิน'))
  })
  it('voidingReversesTheRevenueAndCancelsTheCourseItSold', () => {
    paid().then((s) => {
      voidSale(s.id, 'ลูกค้าขอยกเลิก').then(ok(200)).then((v) => {
        expect(v.status).to.equal('VOIDED'); expect(v.paid).to.equal(0)
        expect(v.voidReason).to.equal('ลูกค้าขอยกเลิก'); expect(v.voidedAt).not.to.be.null
      })
      ledger(s.items[0].coursePurchaseId).its('purchase.status').should('equal', 'CANCELLED')
    })
  })
  it('voidingKeepsSessionsThePatientAlreadyUsed', () => {
    paid().then((s) => {
      use(s.items[0].coursePurchaseId); voidSale(s.id, 'ยกเลิกหลังใช้ไปแล้ว 1 ครั้ง').then(ok(200))
      ledger(s.items[0].coursePurchaseId).then(({ purchase }) => {
        expect(purchase.used).to.equal(1); expect(purchase.remaining).to.equal(0)
      })
    })
  })
  it('voidingDeletesNothing', () => {
    paid([assessment(), course()], [payment('CASH', 10000), payment('QR', 6200)]).then((s) => {
      voidSale(s.id, 'บันทึกผิดคน').then(ok(200))
      cy.api('GET', `/__cypress/financial-fixtures/sales/${s.id}/rows`).then(ok(200)).then((rows) => {
        expect(rows.itemIds).to.have.length(2); expect(rows.payments).to.have.length(4)
        expect(rows.payments.map((p) => p.type)).to.deep.equal(['PAYMENT', 'PAYMENT', 'REFUND', 'REFUND'])
        expect(rows.payments[2].reversesPaymentId).to.equal(rows.payments[0].id)
      })
    })
  })
  it('rejectsVoidingTheSameReceiptTwice', () => {
    paid([assessment()], [payment('CASH', 1200)]).then((s) => {
      voidSale(s.id, 'ครั้งแรก').then(ok(200)); voidSale(s.id, 'ครั้งที่สอง').then(error(409, 'ถูกยกเลิกไปแล้ว'))
    })
  })
  it('voidingSkipsACourseThatWasAlreadyCancelledOnItsOwn', () => {
    paid().then((s) => {
      cancel(s.items[0].coursePurchaseId)
      voidSale(s.id, 'แล้วค่อยยกเลิกใบเสร็จ').then(ok(200)).its('status').should('equal', 'VOIDED')
      ledger(s.items[0].coursePurchaseId).its('movements').should('have.length', 2)
    })
  })
  it('listsReceiptsOfThePatientIncludingVoidedOnes', () => {
    paid([assessment()], [payment('CASH', 1200)]).then((first) => {
      paid([assessment()], [payment('CASH', 1200)]); voidSale(first.id, 'ยกเลิกใบแรก').then(ok(200))
      list().then((page) => {
        expect(page.items).to.have.length(2); expect(page.totalItems).to.equal(2)
        expect(page.items.map((s) => s.status)).to.include.members(['VOIDED', 'PAID'])
      })
    })
  })
  it('listShowsMoneyActuallyKeptSoAVoidedReceiptReadsZero', () => {
    paid().then((s) => {
      voidSale(s.id).then(ok(200)); list().then((page) => {
        expect(page.items[0].total).to.equal(15000); expect(page.items[0].paid).to.equal(0)
      })
    })
  })
  it('listsEveryPatientWhenNoFilterIsGiven', () => {
    cy.registerPatient({ username: 'other-patient', email: 'other@example.com', identityNumber: '1100000000102' }).then((other) => {
      paid([assessment()], [payment('CASH', 1200)])
      paid([assessment()], [payment('CASH', 1200)], { patientId: other.id })
      list('').its('totalItems').should('be.at.least', 2)
      list(`?patientId=${other.id}`).its('totalItems').should('equal', 1)
    })
  })
  it('rejectsAnOversizedPage', () => { cy.api('GET', '/api/payments?size=500').then(error(400, 'size')) })
  it('rejectsAnUnknownPatientFilter', () => { cy.api('GET', '/api/payments?patientId=999999').then(error(404)) })
})

