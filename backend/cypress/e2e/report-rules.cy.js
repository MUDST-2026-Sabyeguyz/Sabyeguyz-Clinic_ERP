const DAY = '2031-05-12'
const sum = (rows, key) => rows.reduce((total, row) => total + row[key], 0)

// Historical report boundaries need date fixtures because receipts use the server clock.
// Sales, usage, reversals, appointments and the reports themselves use production APIs.
describe('ReportServiceTest migration', () => {
  let patient, catalog, fixtures
  beforeEach(function () {
    fixtures = this.fixtures
    cy.api('POST', '/__cypress/report-fixtures').then(({ status, body }) => {
      expect(status).to.equal(200)
      catalog = body
    })
    cy.registerPatient().then((body) => { patient = body })
  })

  const course = () => ({ type: 'COURSE', referenceId: catalog.courseId, quantity: 1 })
  const assessment = (quantity = 1) => ({ type: 'ASSESSMENT', referenceId: catalog.assessmentId, quantity })
  function sell(items, amount, options = {}) {
    return cy.api('POST', '/api/payments', {
      patientId: options.patientId ?? patient.id, branchCode: options.branchCode ?? 'RP',
      salespersonStaffId: options.staffId ?? null, items,
      payments: [{ paymentMethodCode: 'CASH', amount }],
    }).then(({ status, body }) => {
      expect(status).to.equal(201)
      return cy.api('POST', `/__cypress/report-sales/${body.id}/date`, {
        soldAt: options.soldAt ?? `${options.day ?? DAY}T10:00:00+07:00`,
      }).then(({ status }) => {
        expect(status).to.equal(200)
        return body
      })
    })
  }
  function report(type = 'dashboard', query = {}) {
    const url = type === 'dashboard' ? '/api/dashboard/summary' : `/api/reports/${type}`
    return cy.api('GET', url, undefined, { qs: { from: DAY, to: DAY, branchCode: 'RP', ...query } })
      .then(({ status, body }) => { expect(status).to.equal(200); return body })
  }
  function otherPatient(overrides = {}) {
    return cy.registerPatient({ username: 'report-other', email: 'report-other@example.com',
      identityNumber: '1100000000102', gender: 'หญิง', ...overrides })
  }
  function visit(patientId, hour, status = 'COMPLETED') {
    return cy.api('POST', '/api/appointments', {
      branchCode: 'RP', patientId, therapistId: fixtures.therapistId,
      serviceId: catalog.assessmentId, startAt: `${DAY}T${String(hour).padStart(2, '0')}:00:00+07:00`,
    }).then(({ status: responseStatus, body }) => {
      expect(responseStatus).to.equal(201)
      const steps = status === 'COMPLETED' ? ['check-in', 'start-service', 'complete']
        : status === 'NO_SHOW' ? ['no-show'] : []
      steps.forEach((step) => cy.api('POST', `/api/appointments/${body.id}/${step}`).its('status').should('equal', 200))
    })
  }
  const reverse = (sale) => cy.api('POST', `/api/payments/${sale.id}/reverse`, { reason: 'ยกเลิก' }).its('status').should('equal', 200)
  const use = (sale) => cy.api('POST', '/api/course-usages', { coursePurchaseId: sale.items[0].coursePurchaseId }).its('status').should('equal', 201)
  function staff(number) {
    return cy.api('POST', `/__cypress/report-staff/${number}`).then(({ status, body }) => {
      expect(status).to.equal(200); return body.id
    })
  }

  it('totalRevenueEqualsTheSumOfTheCategoryBreakdown', () => {
    sell([course(), assessment(2)], 17400)
    report('revenue').then((r) => {
      expect(sum(r.byCategory, 'amount')).to.equal(r.totalRevenue)
      expect(r.totalRevenue).to.equal(17400)
    })
  })
  it('splitsRevenueBetweenCourseAndSingleVisit', () => {
    sell([course()], 15000)
    sell([assessment()], 1200)
    report().should('include', { courseRevenue: 15000, assessmentRevenue: 1200, totalRevenue: 16200 })
  })
  it('aVoidedReceiptContributesNothingToRevenue', () => {
    sell([assessment()], 1200).then((kept) => {
      sell([course()], 15000).then((cancelled) => {
        expect(kept.id).not.to.equal(cancelled.id)
        reverse(cancelled)
      })
    })
    report().should('include', { totalRevenue: 1200, courseRevenue: 0, receiptsIssued: 1, receiptsVoided: 1 })
  })
  it('reportsZeroRatherThanNullWhenThereIsNoActivity', () => {
    report().then((r) => {
      expect(r).to.include({ totalRevenue: 0, uniquePatients: 0, visits: 0 })
      expect(r.patientsByGender).to.deep.equal([])
    })
  })
  it('categorySharesAddUpToOne', () => {
    sell([course(), assessment()], 16200)
    report('revenue').then((r) => expect(sum(r.byCategory, 'share')).to.equal(1))
  })
  it('dailySeriesCoversEveryDayIncludingDaysWithNoSales', () => {
    sell([assessment()], 1200, { day: '2031-05-14' })
    report('revenue', { to: '2031-05-18' }).then((r) => {
      expect(r.daily).to.have.length(7)
      expect(r.daily[0].amount).to.equal(0)
      expect(r.daily[2].amount).to.equal(1200)
    })
  })
  it('bucketsALateEveningSaleOnTheClinicDayNotTheUtcDay', () => {
    sell([assessment()], 1200, { soldAt: `${DAY}T00:30:00+07:00` })
    report('revenue').then((r) => {
      expect(r.daily).to.have.length(1)
      expect(r.daily[0].date).to.equal(DAY)
      expect(r.daily[0].amount).to.equal(r.totalRevenue)
      expect(r.totalRevenue).to.equal(1200)
    })
  })
  it('excludesSalesOutsideTheRequestedPeriod', () => {
    sell([assessment()], 1200, { day: '2031-05-11' })
    sell([assessment()], 1200, { day: '2031-05-13' })
    report().its('totalRevenue').should('equal', 0)
  })
  it('includesSalesOnBothEdgesOfTheRange', () => {
    sell([assessment()], 1200)
    sell([assessment()], 1200, { day: '2031-05-18' })
    report('dashboard', { to: '2031-05-18' }).its('totalRevenue').should('equal', 2400)
  })
  for (const [name, query, message] of [
    ['rejectsAnInvertedDateRange', { to: '2031-05-11' }, 'ไม่มาก่อน'],
    ['rejectsARangeLongerThanTwoYears', { to: '2034-05-12' }, 'ไม่เกิน 2 ปี'],
    ['rejectsAnUnknownBranch', { branchCode: 'ZZ' }, 'ไม่พบสาขา'],
  ]) {
    it(name, () => {
      cy.api('GET', '/api/dashboard/summary', undefined, { qs: { from: DAY, to: DAY, ...query } })
        .then(({ status, body }) => {
          expect(status).to.equal(400)
          expect(body.message).to.contain(message)
        })
    })
  }
  function branchSales(quantity) {
    cy.api('POST', '/__cypress/report-branch').its('status').should('equal', 200)
    sell([assessment()], 1200)
    otherPatient({ branchCode: 'CM' }).then((other) => sell([assessment(quantity)], 1200 * quantity,
      { patientId: other.id, branchCode: 'CM' }))
  }
  it('filtersRevenueByBranch', () => {
    branchSales(2)
    report().its('totalRevenue').should('equal', 1200)
    report('dashboard', { branchCode: 'CM' }).its('totalRevenue').should('equal', 2400)
    report('dashboard', { branchCode: undefined }).its('totalRevenue').should('equal', 3600)
  })
  it('breaksRevenueDownByBranch', () => {
    branchSales(1)
    report('revenue', { branchCode: undefined }).then((r) => {
      expect(r.byBranch).to.have.length(2)
      expect(r.byBranch.map((b) => b.branchCode)).to.deep.equal(['CM', 'RP'])
    })
  })
  it('countsOnlyAppointmentsThePatientActuallyAttended', () => {
    visit(patient.id, 9)
    visit(patient.id, 11)
    otherPatient().then((other) => {
      visit(other.id, 13)
      visit(other.id, 15, 'NO_SHOW')
      visit(other.id, 17, 'CONFIRMED')
    })
    report().should('include', { visits: 3, uniquePatients: 2 })
  })
  it('splitsAttendingPatientsByGender', () => {
    visit(patient.id, 9)
    otherPatient().then((female) => { visit(female.id, 11); visit(female.id, 13) })
    report().then((r) => {
      expect(r.patientsByGender).to.have.deep.members([{ gender: 'ชาย', patients: 1 }, { gender: 'หญิง', patients: 1 }])
      expect(sum(r.patientsByGender, 'patients')).to.equal(r.uniquePatients)
    })
  })
  it('countsAppointmentsByStatus', () => {
    visit(patient.id, 9)
    visit(patient.id, 11)
    visit(patient.id, 13, 'NO_SHOW')
    visit(patient.id, 15, 'CONFIRMED')
    report('appointments').then((r) => {
      expect(r).to.include({ appointments: 4, visits: 2 })
      expect(r.byStatus).to.have.deep.members([
        { status: 'COMPLETED', appointments: 2 }, { status: 'NO_SHOW', appointments: 1 }, { status: 'CONFIRMED', appointments: 1 },
      ])
    })
  })
  it('measuresNoShowRateAgainstAppointmentsThatCameDueOnly', () => {
    ;[9, 11, 13].forEach((hour) => visit(patient.id, hour))
    visit(patient.id, 15, 'NO_SHOW')
    ;[17, 19].forEach((hour) => visit(patient.id, hour, 'CONFIRMED'))
    report('appointments').its('noShowRate').should('equal', 0.25)
  })
  it('reportsZeroNoShowRateWhenNothingHasComeDue', () => {
    visit(patient.id, 9, 'CONFIRMED')
    report('appointments').its('noShowRate').should('equal', 0)
  })
  it('breaksAppointmentsDownByTherapistWithTheirVisitCount', () => {
    visit(patient.id, 9)
    visit(patient.id, 11, 'NO_SHOW')
    report('appointments').then((r) => {
      expect(r.byTherapist).to.have.length(1)
      expect(r.byTherapist[0]).to.include({ appointments: 2, visits: 1 })
      expect(r.byTherapist[0].code.trim()).not.to.equal('')
    })
  })
  it('fillsEveryDayOfTheAppointmentSeries', () => {
    visit(patient.id, 9)
    report('appointments', { to: '2031-05-15' }).then((r) => {
      expect(r.daily).to.have.length(4)
      expect(r.daily[0]).to.include({ appointments: 1, visits: 1 })
      expect(r.daily[1].appointments).to.equal(0)
      expect(sum(r.daily, 'appointments')).to.equal(r.appointments)
    })
  })
  it('keepsReceiptsWithoutASalespersonInTheirOwnBucket', () => {
    staff(1).then((staffId) => sell([assessment()], 1200, { staffId }))
    sell([assessment(2)], 2400)
    report('staff-sales').then((r) => {
      expect(r.rows).to.have.length(1)
      expect(r.rows[0].amount).to.equal(1200)
      expect(r.unassigned).to.include({ amount: 2400, receipts: 1 })
    })
  })
  it('perPersonRowsPlusUnassignedEqualTheBranchTotal', () => {
    staff(1).then((staffId) => sell([course()], 15000, { staffId }))
    staff(2).then((staffId) => sell([assessment()], 1200, { staffId }))
    sell([assessment()], 1200)
    report('staff-sales').then((r) => {
      expect(sum(r.rows, 'amount') + r.unassigned.amount).to.equal(r.branchTotal)
      expect(r).to.include({ branchTotal: 17400, receipts: 3 })
    })
  })
  it('ranksSalespeopleByAmountSoldDescending', () => {
    let small, big
    staff(1).then((staffId) => { small = staffId; return sell([assessment()], 1200, { staffId }) })
    staff(2).then((staffId) => { big = staffId; return sell([course()], 15000, { staffId }) })
    report('staff-sales').then((r) => expect(r.rows.map((row) => row.staffId)).to.deep.equal([big, small]))
  })
  it('leavesVoidedReceiptsOutOfStaffSales', () => {
    staff(1).then((staffId) => sell([course()], 15000, { staffId }).then(reverse))
    report('staff-sales').then((r) => {
      expect(r.branchTotal).to.equal(0)
      expect(r.rows).to.deep.equal([])
    })
  })
  it('reportsPurchasedUsedAndRemainingPerCourse', () => {
    sell([course()], 15000).then((sale) => { use(sale); use(sale) })
    report('course-balances').then((r) => {
      expect(r.rows).to.have.length(1)
      expect(r.rows[0]).to.include({ purchased: 13, used: 2, remaining: 11 })
      expect(r.rows[0].hn.trim()).not.to.equal('')
      expect(r).to.include({ totalPurchasedSessions: 13, totalUsedSessions: 2, totalRemainingSessions: 11 })
    })
  })
  it('purchasedAlwaysEqualsUsedPlusRemainingForAnActiveCourse', () => {
    sell([course()], 15000).then(use)
    report('course-balances').then((r) => expect(r.rows[0].used + r.rows[0].remaining).to.equal(r.rows[0].purchased))
  })
  it('keepsACancelledCourseVisibleButOutOfTheSoldValue', () => {
    sell([course()], 15000).then((sale) => {
      use(sale)
      cy.api('POST', `/api/course-purchases/${sale.items[0].coursePurchaseId}/cancel`, { reason: 'ยกเลิก' })
        .its('status').should('equal', 200)
    })
    report('course-balances').then((r) => {
      expect(r.rows).to.have.length(1)
      expect(r.rows[0]).to.include({ used: 1, remaining: 0 })
      expect(r.totalValue).to.equal(0)
    })
  })
  it('filtersCourseBalancesByPatient', () => {
    sell([course()], 15000)
    otherPatient().then((other) => sell([course()], 15000, { patientId: other.id }))
    report('course-balances', { patientId: patient.id }).its('rows').should('have.length', 1)
    report('course-balances').its('rows').should('have.length', 2)
  })
})
