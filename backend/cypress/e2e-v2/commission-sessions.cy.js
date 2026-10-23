// Course sessions and what they earn (Data Dictionary + LA BALANCE commission document): Owner PT = seller, tier rate locked at the
// close of the sale month, rate per session by position with the remainder on the last one, substitute PT paid the full
// treatment fee, Owner commission unknown (NULL) until the month is closed, single-visit fee.
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const month = today.slice(0, 7)
const money = (value) => Math.round(Number(value) * 100) / 100

// one flat tier, so the documents' 7% examples can be reproduced exactly (the seed scheme is not in use yet)
const flatRate = (rate) =>
    closeMonth()
        .then(() => cy.api('GET', '/api/commission/schemes'))
        .then(({ body }) => {
          const scheme = body.find(
              (entry) => entry.name === 'Default Tier Scheme',
          )

          return cy.api('PUT', `/api/commission/schemes/${scheme.id}`, {
            branchCode: '01',
            name: scheme.name,
            effectiveFrom: scheme.effectiveFrom,
            tiers: [
              { minimum: 0, maximum: null, rate, order: 1 },
            ],
          }).its('status').should('equal', 200)
        })
        .then(() =>
            cy.api(
                'POST',
                `/api/commission/branch-closings/${month}/reopen?branchCode=01`,
                { reason: 'เตรียมอัตราสำหรับข้อมูลทดสอบก่อนขายคอร์ส' },
            ).its('status').should('equal', 200),
        )
const setFeeAvailable = (available) =>
    cy.api('POST', '/__cypress/treatment-fee-availability-fixture', {
      available,
    }).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(200)
      expect(body.updated).to.equal(1)
    })
const setFee = (feeType, feeValue) =>
    cy.api('POST', '/__cypress/treatment-fee-fixture', {feeType, feeValue,}).then(({ status, body }) => {
      expect(status, JSON.stringify(body)).to.equal(200)
      expect(body.updated).to.equal(1)
    })
const template = (code, price, sessions, bonus = 0) => cy.api('POST', '/api/courses',
  { code, name: `คอร์ส ${code}`, price, sessions, bonusSessions: bonus, validityDays: 365 }).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body.id
})
const buy = (fx, patient, courseId, ownerId) => cy.api('POST', '/api/course-purchases', {
  patientId: patient.id, courseId, branchCode: '01', caseOwnerStaffId: ownerId, paymentLabelCode: 'FULL',
}).then(({ status, body }) => {
  expect(status, JSON.stringify(body)).to.equal(201)
  return body
})
const use = (courseId, extra = {}) => cy.api('POST', '/api/course-usages', { coursePurchaseId: courseId, ...extra })
const closeMonth = (m = month) => cy.api('POST', `/api/commission/closings/${m}?branchCode=01`,).its('status').should('equal', 200)
const usages = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/usages`).its('body')
const allocations = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/allocations`).its('body')
const totals = (courseId) => cy.api('GET', `/__cypress/commission/courses/${courseId}/totals`).its('body')

describe('v2 commission: sessions, rounding, substitute PT and the unknown rate before the close', () => {
  it('test 01: a 10,000 THB / 13-session course at 7% has a pool of 700 and 53.85 per session', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        totals(course.id).then((before) => {
          expect(before.commission_status).to.equal('Provisional')
          expect(before.total_course_commission_pool).to.equal(null)
          expect(before.commission_outstanding, 'unknown, not 0').to.equal(null)
        })
        closeMonth()
        totals(course.id).then((after) => {
          expect(after.commission_status).to.equal('Locked')
          expect(Number(after.total_course_commission_pool)).to.equal(700)
          expect(Number(after.commission_allocation_per_visit)).to.equal(53.85)
          expect(Number(after.commission_outstanding)).to.equal(700)
        })
      }))
    })
  })

  it('test 02 + remainder: the Owner treating all 13 sessions earns 53.85 twelve times and 53.80 on the last', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        for (let i = 0; i < 13; i += 1) use(course.id).its('status').should('equal', 200)
        use(course.id).its('status').should('equal', 400)
        // the rate is not known before the close: NULL, no allocation row, no money yet
        usages(course.id).then((rows) => {
          expect(rows).to.have.length(13)
          rows.forEach((row, index) => {
            expect(row.usage_sequence).to.equal(index + 1)
            expect(row.owner_net_commission).to.equal(null)
            expect(row.commission_allocation_per_visit).to.equal(null)
          })
        })
        allocations(course.id).should('have.length', 0)
        totals(course.id).then((row) => expect(row.commission_outstanding).to.equal(null))
        cy.api('GET', '/api/commission/treatment-report?from=2020-01-01&to=2099-12-31').then(({ body }) => {
          expect(body).to.have.length(13)
          body.forEach((entry) => { expect(entry.ownerNet).to.equal(null); expect(entry.ownerNetPending).to.equal(true) })
        })

        closeMonth()
        usages(course.id).then((rows) => {
          const earned = rows.map((row) => money(row.owner_net_commission))
          expect(earned.slice(0, 12).every((value) => value === 53.85)).to.equal(true)
          expect(earned[12]).to.equal(53.8)
          expect(money(earned.reduce((sum, value) => sum + value, 0))).to.equal(700)
        })
        allocations(course.id).then((rows) => {
          expect(rows).to.have.length(13)
          rows.forEach((row) => {
            expect(row.entry_type).to.equal('Allocation')
            expect(row.allocation_version).to.equal(1)
            expect(row.owner_staff_id).to.equal(this.fixtures.therapistId)
            expect(row.treating_staff_id).to.equal(this.fixtures.therapistId)
            expect(money(row.treatment_fee_amount)).to.equal(0)
          })
        })
        totals(course.id).then((row) => {
          expect(money(row.owner_net_commission_released_total)).to.equal(700)
          expect(money(row.commission_outstanding)).to.equal(0)
        })
        // closing again changes nothing
        closeMonth()
        allocations(course.id).should('have.length', 13)
      }))
    })
  })

  it('prints a usage slip for every Completed session with the history of all users up to it', function () {
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
        cy.api('GET', `/__cypress/commission/courses/${course.id}/prints`).then(({ body }) => {
          expect(body).to.have.length(2)
          const second = JSON.parse(body[1].snapshot)
          expect(second.history.map((line) => line.sequence)).to.deep.equal([1, 2])
          expect(second.history.map((line) => line.treatingStaffId)).to.deep.equal([this.fixtures.therapistId, this.fixtures.managerStaffId])
          expect(JSON.parse(body[0].snapshot).history).to.have.length(1)
        })
      }))
    })
  })

  it('links a session to a treatment of the same visit, PT and service, and refuses any other', function () {
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.managerStaffId).then((course) => {
        cy.configureBookingWindows()
        cy.api('POST', '/api/appointments', {
          branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId, serviceId: this.fixtures.serviceId,
          startAt: `${this.fixtures.tomorrow}T09:00:00+07:00`, endAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
        }).then(({ body: appointment }) => {
          cy.api('POST', `/api/appointments/${appointment.id}/check-in`).its('status').should('equal', 200)
          const treatment = (extra) => cy.api('POST', '/api/treatments', {
            patientId: patient.id, appointmentId: appointment.id, staffId: this.fixtures.therapistId,
            serviceId: courseId, note: 'ใช้คอร์ส', ...extra,
          }).then(({ status, body }) => {
            expect(status, JSON.stringify(body)).to.equal(201)
            return body
          })
          treatment({ serviceId: this.fixtures.serviceId }).then((other) => {
            // a treatment of another service is not this course's
            use(course.id, { appointmentId: appointment.id, treatingStaffId: this.fixtures.therapistId, treatmentId: other.id })
              .its('status').should('equal', 400)
          })
          treatment({ staffId: this.fixtures.managerStaffId }).then((wrongPt) => {
            use(course.id, { appointmentId: appointment.id, treatingStaffId: this.fixtures.therapistId, treatmentId: wrongPt.id })
              .its('status').should('equal', 400)
          })
          usages(course.id).should('have.length', 0)
          treatment({}).then((ok) => {
            use(course.id, { appointmentId: appointment.id, treatingStaffId: this.fixtures.therapistId, treatmentId: ok.id })
              .its('status').should('equal', 200)
            usages(course.id).then((rows) => expect(rows[0].treatment_id).to.equal(ok.id))
          })
        })
      }))
    })
  })

  it('a session used after the close takes the locked rate of its position at once', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200) // used before the close: pending
        closeMonth()
        use(course.id).its('status').should('equal', 200) // used after the close
        usages(course.id).then((rows) => {
          expect(money(rows[0].owner_net_commission)).to.equal(53.85)
          expect(money(rows[1].owner_net_commission)).to.equal(53.85)
        })
        allocations(course.id).should('have.length', 2)
        totals(course.id).then((row) => expect(money(row.commission_outstanding)).to.equal(money(700 - 53.85 * 2)))
      }))
    })
  })

  it('test 03: another PT treating earns the 10% treatment fee (76.92), the Owner earns 0, and it needs no close', function () {
    flatRate(0.07)
    setFee('Percentage', 10)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
        usages(course.id).then((rows) => {
          expect(rows[0].treating_staff_id).to.equal(this.fixtures.managerStaffId)
          expect(money(rows[0].substitute_treatment_fee_amount)).to.equal(76.92)
          expect(money(rows[0].owner_net_commission)).to.equal(0)
        })
        allocations(course.id).then((rows) => {
          expect(rows).to.have.length(1)
          expect(rows[0].owner_staff_id).to.equal(this.fixtures.therapistId)
          expect(rows[0].treating_staff_id).to.equal(this.fixtures.managerStaffId)
          expect(money(rows[0].treatment_fee_amount)).to.equal(76.92)
          expect(money(rows[0].owner_net_commission)).to.equal(0)
          expect(Number(rows[0].treatment_fee_calculation_base)).to.equal(769.2308) // 10,000 / 13, before rounding the fee
        })
        // the session still takes one right and one position; the Owner's commission is not moved to another session
        totals(course.id).then((row) => {
          expect(row.visits_used).to.equal(1)
          expect(row.remaining_visits).to.equal(12)
          expect(money(row.substitute_treatment_fee_total)).to.equal(76.92)
          expect(money(row.owner_net_commission_released_total)).to.equal(0)
        })
        closeMonth()
        usages(course.id).then((rows) => {
          expect(money(rows[0].owner_net_commission)).to.equal(0)
          expect(money(rows[0].commission_allocation_per_visit)).to.equal(53.85) // the rate of the position, for audit only
        })
        allocations(course.id).should('have.length', 1)
        // the Owner then treats the next session: position 2 earns its own rate
        use(course.id).its('status').should('equal', 200)
        usages(course.id).then((rows) => expect(money(rows[1].owner_net_commission)).to.equal(53.85))
      }))
    })
  })

  it('test 07: a substitute fee of 100 THB is paid in full although the commission of the session is only 70', function () {
    flatRate(0.07)
    setFee('Fixed', 100)
    cy.api('GET', '/api/courses').then(({ body }) => body.find((course) => course.code === 'COURSE_10').id).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        closeMonth()
        totals(course.id).then((row) => expect(money(row.commission_allocation_per_visit)).to.equal(70))
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
        allocations(course.id).then((rows) => {
          expect(money(rows[0].treatment_fee_amount)).to.equal(100)
          expect(money(rows[0].owner_net_commission)).to.equal(0)
        })
      }))
    })
  })

  it('test 04: a course sold in an earlier month keeps that month\'s rate when used in a later month', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        cy.api('POST', `/__cypress/commission/courses/${course.id}/shift-sale-month?months=1`).its('status').should('equal', 200)
        const previous = new Date(`${month}-01T00:00:00Z`)
        previous.setUTCMonth(previous.getUTCMonth() - 1)
        closeMonth(previous.toISOString().slice(0, 7))
        closeMonth()
        // a newer version of the scheme (a higher rate) starting later does not touch a locked course
        cy.api('GET', '/api/commission/schemes').then(({ body }) => {
          const scheme = body.find((entry) => entry.name === 'Default Tier Scheme')
          cy.api('POST', `/api/commission/schemes/${scheme.id}/versions`, {
            branchCode: '01', effectiveFrom: '2040-01-01', tiers: [{ minimum: 0, maximum: null, rate: 0.1, order: 1 }],
          }).its('status').should('equal', 201)
        })
        for (let i = 0; i < 3; i += 1) use(course.id).its('status').should('equal', 200)
        usages(course.id).then((rows) => {
          expect(money(rows.reduce((sum, row) => sum + Number(row.owner_net_commission), 0))).to.equal(161.55)
        })
        totals(course.id).then((row) => expect(Number(row.total_course_commission_pool)).to.equal(700))
      }))
    })
  })

  it('does not let the rate of a locked course move the money of a session the Owner did not treat', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        closeMonth()
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
        use(course.id).its('status').should('equal', 200)
        usages(course.id).then((rows) => {
          expect(money(rows[0].owner_net_commission)).to.equal(0)
          expect(money(rows[1].owner_net_commission)).to.equal(53.85)
        })
      }))
    })
  })

  it('refuses a substitute when no Treatment Fee Rule applies (never 0), and a PT who cannot treat', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        setFeeAvailable(false)
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 409)
        use(course.id, { treatingStaffId: 99999999 }).its('status').should('equal', 400)
        // a PT of another branch cannot treat a session given at this branch
        use(course.id, { treatingStaffId: this.fixtures.therapist2Id }).its('status').should('equal', 400)
        usages(course.id).should('have.length', 0)
        totals(course.id).then((row) => expect(row.visits_used).to.equal(0))
        use(course.id).its('status').should('equal', 200) // the Owner needs no rule
      }))
    })
  })

  it('an Owner who leaves still earns the sessions of the last day and a substitute is paid after that', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        cy.api('POST', `/api/staff/${this.fixtures.therapistId}/terminate`, { terminationDate: today }).its('status').should('equal', 200)
        use(course.id).its('status').should('equal', 200) // the day of leaving still counts
        use(course.id, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
        closeMonth()
        usages(course.id).then((rows) => {
          expect(money(rows[0].owner_net_commission)).to.equal(53.85)
          expect(money(rows[1].owner_net_commission)).to.equal(0)
          expect(money(rows[1].substitute_treatment_fee_amount)).to.be.greaterThan(0)
        })
        // nothing more is expected from the Owner once gone: the outstanding commission is 0
        totals(course.id).then((row) => expect(money(row.commission_outstanding)).to.equal(0))
      }))
    })
  })

  it('reverses a session before and after the close, and keeps its position for the replacement', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        use(course.id).its('status').should('equal', 200)
        cy.api('GET', `/api/course-purchases/${course.id}`).then(({ body }) => {
          const usagesOnLedger = body.movements.filter((movement) => movement.type === 'USAGE')
          // reverse position 1 before the close: nothing was allocated, so there is nothing to reverse
          const first = usagesOnLedger[usagesOnLedger.length - 1]
          cy.api('POST', `/api/course-movements/${first.id}/reverse`, { reason: 'บันทึกผิดคน' }).its('status').should('equal', 200)
        })
        allocations(course.id).should('have.length', 0)
        use(course.id).its('status').should('equal', 200) // takes the free position 1 again
        usages(course.id).then((rows) => {
          expect(rows.map((row) => row.usage_sequence)).to.deep.equal([1, 1, 2])
          expect(rows.map((row) => row.usage_status)).to.deep.equal(['Reversed', 'Completed', 'Completed'])
          expect(rows[1].replaces_usage_id).to.equal(rows[0].visit_usage_id)
        })
        closeMonth()
        allocations(course.id).should('have.length', 2)
        // now reverse the newest session (the replacement of position 1) after the close: a Reversal of its allocation
        cy.api('GET', `/api/course-purchases/${course.id}`).then(({ body }) => {
          const usagesOnLedger = body.movements.filter((movement) => movement.type === 'USAGE')
          cy.api('POST', `/api/course-movements/${usagesOnLedger[0].id}/reverse`, { reason: 'บันทึกซ้ำ' }).its('status').should('equal', 200)
        })
        allocations(course.id).then((rows) => {
          expect(rows.map((row) => row.entry_type)).to.deep.equal(['Allocation', 'Allocation', 'Reversal'])
          expect(money(rows[2].owner_net_commission)).to.equal(-53.85)
          expect(rows[2].reverses_allocation_id).to.equal(rows[0].allocation_id)
          expect(rows[2].allocation_version).to.equal(rows[0].allocation_version)
        })
        totals(course.id).then((row) => expect(money(row.owner_net_commission_released_total)).to.equal(53.85))
      }))
    })
  })
})

describe('v2 commission: single-visit treatment fee', () => {
  const sale = (fx, patient, items, extra = {}) => cy.api('POST', '/api/payments', {
    patientId: patient.id, branchCode: '01', salespersonStaffId: fx.therapistId, treatingStaffId: fx.therapistId, paymentLabelCode: 'FULL', items, ...extra,
  })
  const fees = () => cy.api('GET', '/__cypress/commission/single-visit-fees').its('body')

  it('pays a Fixed fee once per visit, whatever the quantity, only when the receipt is paid', function () {
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }])
        .its('status').should('equal', 201) // not paid: no fee
      fees().should('have.length', 0)
      sale(this.fixtures, patient, [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 2 }],
        { payments: [{ paymentMethodCode: 'BankTransfer', amount: 1600 }] }).its('status').should('equal', 201)
      fees().then((rows) => {
        expect(rows).to.have.length(1)
        expect(rows[0].fee_type).to.equal('Fixed')
        expect(money(rows[0].fee_amount)).to.equal(45)
        expect(rows[0].staff_id).to.equal(this.fixtures.therapistId)
        expect(rows[0].entry_type).to.equal('Allocation')
        expect(rows[0].fee_version).to.equal(1)
      })
    })
  })

  it('pays a Percentage fee on the net amount of the single visit, not on a course sold on the same bill', function () {
    setFee('Percentage', 10)
    cy.registerPatient().then((patient) => {
      // 800 single visit + 9,000 course, 100 off the whole bill (shared by the lines); paid in full
      sale(this.fixtures, patient, [
        { type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 },
        { type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 },
      ], { billDiscountType: 'BAHT', billDiscountValue: 98, payments: [{ paymentMethodCode: 'BankTransfer', amount: 9702 }] })
        .its('status').should('equal', 201)
      fees().then((rows) => {
        expect(rows).to.have.length(1)
        // the visit's share of the 98 THB discount: 98 x 800 / 9,800 = 8.00; base 792.00; 10% = 79.20
        expect(money(rows[0].calculation_base)).to.equal(792)
        expect(money(rows[0].fee_amount)).to.equal(79.2)
      })
    })
  })

  it('reverses the fee when the sale is voided, once', function () {
    setFee('Percentage', 10)
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        { payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }] }).then(({ body }) => {
        cy.api('POST', `/api/payments/${body.id}/reverse`, { reason: 'ลูกค้ายกเลิก' }).its('status').should('equal', 200)
        cy.api('POST', `/api/payments/${body.id}/reverse`, { reason: 'ซ้ำ' }).its('status').should('be.within', 400, 409)
      })
      fees().then((rows) => {
        expect(rows.map((row) => row.entry_type)).to.deep.equal(['Allocation', 'Reversal'])
        expect(money(rows[1].fee_amount)).to.equal(-80)
        expect(rows[1].reverses_fee_id).to.equal(rows[0].fee_id)
        expect(rows[1].fee_version).to.equal(rows[0].fee_version)
      })
    })
  })

  it('sells without a fee when no Treatment Fee Rule applies (never 0) and records it once a rule exists', function () {
    setFeeAvailable(false)
    cy.registerPatient().then((patient) => {
      sale(this.fixtures, patient, [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
        { payments: [{ paymentMethodCode: 'BankTransfer', amount: 800 }] }).its('status').should('equal', 201)
      fees().should('have.length', 0)
      setFeeAvailable(true)
      cy.api('POST', '/api/commission/single-visit-fees/backfill?from=2020-01-01&to=2099-12-31').its('body.recorded').should('equal', 1)
      cy.api('POST', '/api/commission/single-visit-fees/backfill?from=2020-01-01&to=2099-12-31').its('body.recorded').should('equal', 0)
      fees().then((rows) => {
        expect(rows).to.have.length(1)
        expect(money(rows[0].fee_amount)).to.equal(45)
      })
    })
  })
})

describe('v2 commission: cancelling or voiding a course keeps what was already earned', () => {
  const ledgerOf = (courseId) => cy.api('GET', `/api/course-purchases/${courseId}`).its('body')

  it('cancels the unused rights, keeps the session given and its commission, and drops the sale from the tier sales', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((course) => {
        use(course.id).its('status').should('equal', 200)
        cy.api('POST', `/api/course-purchases/${course.id}/cancel`, { reason: ' ' }).its('status').should('equal', 400)
        cy.api('POST', `/api/course-purchases/${course.id}/cancel`, { reason: 'ผู้ป่วยย้ายที่อยู่' }).then(({ status, body }) => {
          expect(status, JSON.stringify(body)).to.equal(200)
          expect(body.status).to.equal('CANCELLED')
        })
        cy.api('POST', `/api/course-purchases/${course.id}/cancel`, { reason: 'ซ้ำ' }).its('status').should('equal', 409)
        ledgerOf(course.id).then((body) => {
          expect(body.purchase.remaining).to.equal(0)
          expect(body.purchase.used).to.equal(1)
          const cancellation = body.movements.find((movement) => movement.type === 'CANCELLATION')
          expect(cancellation, 'a Cancellation entry cuts the 12 unused rights').to.exist
          expect(cancellation.quantity).to.equal(-12)
        })
        use(course.id).its('status').should('equal', 400) // nothing left to use
        // the sale is void: it no longer counts towards the tier, so the month has nothing to close for the seller
        cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`).then(({ body }) => {
          expect(body.find((row) => row.employeeId === this.fixtures.therapistId)).to.equal(undefined)
        })
        totals(course.id).then((row) => {
          expect(row.course_status).to.equal('Cancelled')
          expect(row.visits_used).to.equal(1)
          expect(row.remaining_visits).to.equal(0)
          expect(money(row.commission_outstanding ?? 0)).to.equal(0)
        })
      }))
    })
  })

  it('voiding a paid course sale cuts the rights the same way and keeps the treatment fee of a substitute session', function () {
    flatRate(0.07)
    setFee('Percentage', 10)
    cy.registerPatient().then((patient) => {
      cy.api('POST', '/api/payments', {
        patientId: patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId,
        paymentLabelCode: 'FULL',
        items: [{ type: 'COURSE', referenceId: this.fixtures.courseId, quantity: 1 }],
        payments: [{ paymentMethodCode: 'BankTransfer', amount: 9000 }],
      }).then(({ body: sale }) => {
        cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
          const courseId = courses[0].id
          use(courseId, { treatingStaffId: this.fixtures.managerStaffId }).its('status').should('equal', 200)
          cy.api('POST', `/api/payments/${sale.id}/reverse`, { reason: 'ยกเลิกใบเสร็จ' }).its('status').should('equal', 200)
          totals(courseId).then((row) => {
            expect(row.course_status).to.equal('Cancelled')
            expect(row.remaining_visits).to.equal(0)
            expect(money(row.substitute_treatment_fee_total)).to.equal(150) // 9,000 x 10% / 6 sessions, kept
          })
          allocations(courseId).then((rows) => {
            expect(rows).to.have.length(1)
            expect(rows[0].entry_type).to.equal('Allocation')
          })
        })
      })
    })
  })

  it('a refund that is Completed leaves the earned commission alone and the tier sales are reduced by it', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => {
        cy.api('POST', '/api/payments', {
          patientId: patient.id, branchCode: '01', salespersonStaffId: this.fixtures.therapistId,
          paymentLabelCode: 'FULL',
          items: [{ type: 'COURSE', referenceId: courseId, quantity: 1 }],
          payments: [{ paymentMethodCode: 'BankTransfer', amount: 10000 }],
        }).then(({ body: sale }) => cy.api('GET', `/api/receipts/${sale.id}`).then(({ body: detail }) => {
          cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: courses }) => {
            const course = courses[0]
            use(course.id).its('status').should('equal', 200)
            closeMonth()
            allocations(course.id).should('have.length', 1)
            cy.api('POST', `/api/payments/${detail.payments[0].id}/refunds`, {
              amount: 4000, paymentMethodCode: 'BankTransfer', reason: 'คืนสิทธิ์ที่เหลือบางส่วน',
              items: [{ courseId: course.id, refundAmount: 4000, cancelledVisits: 6 }],
            }).then(({ body: refund }) => {
              // a Pending refund changes nothing
              cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`).then(({ body }) => {
                expect(Number(body.find((row) => row.employeeId === this.fixtures.therapistId).sales)).to.equal(10000)
              })
              cy.api('POST', `/api/refunds/${refund.id}/complete`).its('status').should('equal', 200)
            })
            allocations(course.id).should('have.length', 1) // the session already given keeps its commission
            totals(course.id).then((row) => {
              expect(row.remaining_visits).to.equal(6)
              expect(row.course_status).to.equal('Active')
              expect(money(row.owner_net_commission_released_total)).to.equal(53.85)
            })
          })
        }))
      })
    })
  })
})

describe('v2 commission: closing the month while sessions are being recorded', () => {
  const race = (a, b) => cy.parallelApi([a, b])
  const closeRequest = {method: 'POST', url: `/api/commission/closings/${month}?branchCode=01`, body: {},}

  it('settles every session exactly once whatever the order of a close and the use that races it', function () {
    flatRate(0.07)
    template('ACC13', 10000, 13).then((courseId) => {
      cy.registerPatient().then((patient) => {
        const courses = []
        // two closes at once: one closing row, no duplicate allocation
        buy(this.fixtures, patient, courseId, this.fixtures.therapistId).then((first) => {
          courses.push(first)
          use(first.id).its('status').should('equal', 200)
          race(closeRequest, closeRequest).then((responses) => {
            responses.forEach((response) => expect(response.status, JSON.stringify(response.body)).to.be.oneOf([200, 409]))
            expect(responses.some((response) => response.status === 200)).to.equal(true)
          })
        })
        // a close racing a use, again and again: the month is reopened before each round
        for (let round = 0; round < 5; round += 1) {
          cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`,).then(({ status, body }) => {
            expect(status).to.equal(200)

            const closing = body.find(
                (row) => row.employeeId === this.fixtures.therapistId,
            )

            if (closing && closing.status === 'CLOSED') {
              return cy.api('POST', `/api/commission/closings/${closing.id}/reopen`, { reason: 'รอบทดสอบ' },
              ).its('status').should('equal', 200)
            }
          }).then(() =>
              buy(this.fixtures, patient, courseId, this.fixtures.therapistId),
          ).then((course) => {
            courses.push(course)

            return race(closeRequest, {
              method: 'POST',
              url: '/api/course-usages',
              body: { coursePurchaseId: course.id },
            }).then((responses) => {
              responses.forEach((response) => expect(response.status, JSON.stringify(response.body)).to.equal(200),
              )
            })
          })
        }
        cy.then(() => {
          // whatever order they ran in, one more close leaves nothing unsettled and nothing doubled
          cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`).then(({ body }) => {
            const closing = body.find((row) => row.employeeId === this.fixtures.therapistId)
            if (closing.status !== 'CLOSED') closeMonth()
          })
          courses.forEach((course) => {
            usages(course.id).then((rows) => {
              expect(rows).to.have.length(1)
              expect(money(rows[0].owner_net_commission), 'no session is left without its Owner commission').to.equal(53.85)
            })
            allocations(course.id).then((rows) => {
              const live = rows.filter((row) => row.entry_type === 'Allocation').length - rows.filter((row) => row.entry_type === 'Reversal').length
              expect(live, 'exactly one live allocation').to.equal(1)
            })
            totals(course.id).then((row) => expect(row.commission_status).to.equal('Locked'))
          })
          cy.api('GET', `/api/commission/closings?month=${month}&branchCode=01`).its('body').should('have.length', 1)
        })
      })
    })
  })
})
