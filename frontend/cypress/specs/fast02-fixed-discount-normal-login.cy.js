describe('FAST02 confirmed fixed-amount discount checkout', () => {
  const backend = Cypress.env('BACKEND_URL')
  const suffix = Date.now().toString()
  let token, patient, service

  const createFixture = (method, path, body) => cy.request({
    method, url: `${backend}${path}`, body, log: false,
    headers: { Authorization: `Bearer ${token}` },
  })

  const checkout = (kind, expectedBase) => {
    cy.visit('/staff/services')
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type(patient.hn)
    cy.get('.results button').contains(patient.hn).click()
    cy.get('.fields select').eq(1).select(String(service.id))
    cy.get('select[aria-label="ส่วนลดแบบบาท"]').select(kind)
    cy.get('input[aria-label="จำนวนส่วนลดบาท"]').type('500')
    cy.intercept('POST', '**/api/payments').as(`sale-${kind}`)
    cy.contains('button', 'ยืนยันรายการ').click()
    cy.wait(`@sale-${kind}`).then(({ request, response }) => {
      expect(response.statusCode).to.eq(201)
      expect(request.body.items[0].lineDiscountAmount ?? 0).to.eq(kind === 'line' ? 500 : 0)
      expect(request.body.billDiscountAmount).to.eq(kind === 'bill' ? 500 : 0)
      expect(request.body.payments[0].amount).to.eq(1500)
      expect(response.body.grossTotal).to.eq(2000)
      expect(response.body.total).to.eq(1500)
      expect(response.body.paid).to.eq(1500)
      expect(response.body.commissionBase).to.eq(expectedBase)
      const receiptId = response.body.id
      cy.get('.receipt-preview').should('contain', response.body.receiptNo)
        .and('contain', 'gross ฿2,000')
        .and('contain', 'ยอดสุทธิ ฿1,500')
        .and('contain', `commission base ฿${expectedBase.toLocaleString()}`)
      cy.url().should('include', `receiptId=${receiptId}`)
      cy.reload()
      cy.get('.receipt-preview').should('contain', response.body.receiptNo)
        .and('contain', 'รับเงินจริง ฿1,500')
        .and('contain', `commission base ฿${expectedBase.toLocaleString()}`)
      cy.window().then((win) => cy.stub(win, 'print').as(`print-${kind}`))
      cy.contains('button', 'พิมพ์ใบเสร็จ').click()
      cy.get(`@print-${kind}`).should('have.been.calledOnce')
    })
  }

  it('sells both confirmed discount examples through normal login and reloadable receipt print view', () => {
    cy.visit('/')
    cy.get('input[autocomplete="username"]').type('admin')
    cy.get('input[autocomplete="current-password"]').type(Cypress.env('BOOTSTRAP_ADMIN_PASSWORD'), { log: false })
    cy.contains('button', 'เข้าสู่ระบบ').click()
    cy.location('pathname').should('eq', '/staff/dashboard')
    cy.window().then((win) => {
      token = win.localStorage.getItem('authToken')
      expect(token, 'ordinary login persists the bearer token for API fixture setup').to.be.a('string').and.not.be.empty
    })
    cy.then(() => createFixture('POST', '/api/services', {
      code: `DISC-${suffix}`, name: `DiscountService${suffix}`, defaultDurationMinutes: 60, price: 2000,
    })).then((response) => { expect(response.status).to.eq(201); service = response.body })
    cy.then(() => createFixture('POST', '/api/patients', {
      branchCode: '01', patientType: 'Foreigner', title: 'นาย', firstName: 'Discount', lastName: suffix,
      gender: 'Male', phone: '0891234567',
    })).then((response) => { expect(response.status).to.eq(201); patient = response.body })
    cy.then(() => checkout('line', 1500))
      .then(() => checkout('bill', 2000))
  })
})
