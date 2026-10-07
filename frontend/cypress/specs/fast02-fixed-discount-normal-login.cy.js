describe('FAST02 confirmed fixed-amount discount checkout', () => {
  const backend = Cypress.env('BACKEND_URL')
  const suffix = Date.now().toString()
  let token, patient, service, paymentMethod, baselineTotal, baselineSingle, baselineDashboard
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date())
  const dayFilter = `from=${day}&to=${day}&branchCode=01`
  const dashboardFilter = `from=${day.slice(0, 7)}-01&to=${day}`

  const createFixture = (method, path, body) => cy.request({
    method, url: `${backend}${path}`, body, log: false,
    headers: { Authorization: `Bearer ${token}` },
  })

  const checkout = (kind, expectedBase, failReceiptReadback = false) => {
    cy.visit('/staff/services')
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type(patient.hn)
    cy.get('.results button').contains(patient.hn).click()
    cy.get('select[aria-label="สาขาที่ทำรายการ"]').select('01')
    cy.get('.fields select').eq(1).select(String(service.id))
    cy.get('select[aria-label="ส่วนลดแบบบาท"]').select(kind)
    cy.get('input[aria-label="จำนวนส่วนลดบาท"]').type('500')
    let submissions = 0
    let allowReceiptRetry = !failReceiptReadback
    let failedReceiptReads = 0
    let successfulReceiptReads = 0
    cy.intercept('GET', '**/api/receipts/*', (request) => {
      if (failReceiptReadback && !allowReceiptRetry) {
        failedReceiptReads += 1
        request.destroy()
        return
      }
      successfulReceiptReads += 1
      request.continue()
    }).as(`receipt-read-${kind}`)
    cy.intercept('POST', '**/api/payments', (request) => {
      submissions += 1
      request.continue((response) => { response.setDelay(300) })
    }).as(`sale-${kind}`)
    cy.contains('button', 'ยืนยันรายการ').dblclick()
    cy.wait(`@sale-${kind}`).then(({ request, response }) => {
      expect(response.statusCode).to.eq(201)
      expect(submissions, 'one in-flight checkout despite a double click').to.eq(1)
      expect(request.body.branchCode).to.eq('01')
      expect(request.body.items[0].lineDiscountAmount ?? 0).to.eq(kind === 'line' ? 500 : 0)
      expect(request.body.billDiscountAmount).to.eq(kind === 'bill' ? 500 : 0)
      expect(request.body.payments[0].amount).to.eq(1500)
      paymentMethod = request.body.payments[0].paymentMethodCode
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
      if (failReceiptReadback) {
        cy.wait(`@receipt-read-${kind}`).then(({ error }) => { expect(error, 'the injected receipt GET failure').to.exist })
        cy.contains('button', 'โหลดใบเสร็จอีกครั้ง').should('be.visible').then(() => {
          expect(failedReceiptReads, 'at least one failed readback before retry').to.be.greaterThan(0)
          allowReceiptRetry = true
          cy.contains('button', 'โหลดใบเสร็จอีกครั้ง').click()
        })
        const waitForSuccessfulReceiptRead = () => cy.wait(`@receipt-read-${kind}`).then(({ response: retry, error }) => {
          if (error) {
            expect(error, 'failed automatic readback before the explicit retry completes').to.exist
            return waitForSuccessfulReceiptRead()
          }
          expect(retry.statusCode).to.eq(200)
          expect(retry.body.id).to.eq(receiptId)
        })
        waitForSuccessfulReceiptRead()
        cy.then(() => expect(successfulReceiptReads, 'successful retry readback').to.be.greaterThan(0))
      }
      cy.reload()
      cy.wait(`@receipt-read-${kind}`).then(({ response: reload }) => { expect(reload.statusCode).to.eq(200); expect(reload.body.id).to.eq(receiptId) })
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
    cy.then(() => createFixture('GET', `/api/reports/revenue?${dayFilter}`)).then(({ body }) => {
      baselineTotal = body.totalRevenue
      baselineSingle = body.byCategory.find((row) => row.type === 'SINGLE_VISIT').amount
      expect(body.byCategory.reduce((total, row) => total + row.amount, 0)).to.eq(baselineTotal)
    })
    cy.then(() => createFixture('GET', `/api/dashboard/summary?${dashboardFilter}`))
      .then(({ body }) => { baselineDashboard = body.totalRevenue })
    cy.then(() => checkout('line', 1500, true))
      .then(() => checkout('bill', 2000))
    cy.then(() => cy.request({
      method: 'POST', url: `${backend}/api/payments`, log: false, failOnStatusCode: false,
      headers: { Authorization: `Bearer ${token}` },
      body: {
        patientId: patient.id, branchCode: '01',
        items: [{ type: 'SINGLE_VISIT', referenceId: service.id, quantity: 1, lineDiscountAmount: 100 }],
        billDiscountAmount: 100, payments: [{ paymentMethodCode: paymentMethod, amount: 1800 }],
      },
    })).then((response) => {
      expect(response.status).to.eq(400)
      expect(JSON.stringify(response.body)).to.include('ส่วนลดรายการร่วมกับส่วนลดท้ายบิล')
    })
    cy.visit('/staff/reports')
    cy.contains('.report-filters label', 'ตั้งแต่').find('input').clear().type(day)
    cy.contains('.report-filters label', 'ถึง').find('input').clear().type(day)
    cy.intercept('GET', '**/api/reports/revenue*branchCode=01*').as('revenueByBranch')
    cy.contains('.report-filters label', /^สาขา/).find('select').select('01')
    cy.wait('@revenueByBranch').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.totalRevenue).to.eq(baselineTotal + 3000)
      const single = response.body.byCategory.find((row) => row.type === 'SINGLE_VISIT')
      expect(single.amount).to.eq(baselineSingle + 3000)
      expect(response.body.byCategory.reduce((total, row) => total + row.amount, 0)).to.eq(response.body.totalRevenue)
      expect(single.share).to.be.at.most(1)
      cy.contains('.report-table tbody tr', 'SINGLE_VISIT')
        .should('contain', `฿${(baselineSingle + 3000).toLocaleString()}`)
        .and('contain', `${single.share * 100}%`)
    })
    cy.reload()
    cy.get('.report-table tbody tr').should('exist')
    cy.intercept('GET', '**/api/dashboard/summary*').as('dashboardSummary')
    cy.visit('/staff/dashboard')
    cy.wait('@dashboardSummary').then(({ response }) => {
      expect(response.statusCode).to.eq(200)
      expect(response.body.totalRevenue).to.eq(baselineDashboard + 3000)
      cy.contains('.metrics article', 'รายได้รวม').find('b')
        .should('have.text', `฿${(baselineDashboard + 3000).toLocaleString()}`)
    })
  })
})
