const master = { code: 'FINANCIAL', name: 'คอร์สกายภาพ 10 ครั้ง', price: 15000, sessions: 10, bonusSessions: 3, validityDays: 90 }
const ok = (status) => (response) => { expect(response.status).to.equal(status); return response.body }
const conflict = (message) => (response) => {
  expect(response.status).to.equal(409)
  if (message) expect(response.body.message).to.contain(message)
}

describe('CourseLedgerService rules migrated from JUnit', () => {
  let patientId, courseId
  beforeEach(() => {
    cy.registerPatient().then((p) => { patientId = p.id })
    cy.api('POST', '/api/courses', master).then(ok(201)).then((c) => { courseId = c.id })
  })
  const purchase = (id = courseId) => cy.api('POST', '/api/course-purchases', { patientId, courseId: id, branchCode: '01' }).then(ok(201))
  const ledger = (id) => cy.api('GET', `/api/course-purchases/${id}`).then(ok(200))
  const useRequest = (id, reason = null) => cy.api('POST', '/api/course-usages', { coursePurchaseId: id, appointmentId: null, note: reason })
  const use = (id, reason) => useRequest(id, reason).then(ok(201))
  const reverse = (id, reason = 'บันทึกผิดคน') => cy.api('POST', `/api/course-movements/${id}/reverse`, { reason })
  const cancel = (id, reason = 'ผู้ป่วยขอยกเลิก') => cy.api('POST', `/api/course-purchases/${id}/cancel`, { reason })
  const usageId = (id) => ledger(id).then((l) => l.movements.find((m) => m.type === 'USAGE').id)

  it('purchaseCreditsSessionsPlusBonus', () => {
    purchase().then((p) => {
      expect(p.purchased).to.equal(13); expect(p.used).to.equal(0); expect(p.remaining).to.equal(13)
      expect(p.usable).to.equal(true); expect(p.status).to.equal('ACTIVE')
    })
  })
  it('purchaseSnapshotsCourseMasterSoLaterEditsDoNotChangeSoldPackages', () => {
    purchase().then((p) => {
      cy.api('PUT', `/api/courses/${courseId}`, { ...master, name: 'ชื่อใหม่', price: 99999, sessions: 99, bonusSessions: 99, validityDays: 1 }).then(ok(200))
      ledger(p.id).then(({ purchase: reloaded }) => {
        expect(reloaded.price).to.equal(15000); expect(reloaded.purchased).to.equal(13)
        expect(reloaded.course.name).to.equal('คอร์สกายภาพ 10 ครั้ง')
      })
    })
  })
  it('expiryIsCalculatedFromValidityDays', () => {
    purchase().then((p) => {
      expect(Date.parse(p.expiresAt) - Date.parse(p.purchasedAt)).to.equal(90 * 86400000)
      // Preserve fractional seconds too, rather than losing precision through Date.parse.
      expect(p.expiresAt.slice(10)).to.equal(p.purchasedAt.slice(10))
      expect(p.expired).to.equal(false)
    })
  })
  it('buyingAndUsingOnTheSameDayWorks', () => {
    purchase().then((p) => use(p.id, 'ใช้วันเดียวกับที่ซื้อ')).then((p) => {
      expect(p.used).to.equal(1); expect(p.remaining).to.equal(12)
    })
  })
  it('balanceIsTheSumOfMovementsNotAStoredCounter', () => {
    purchase().then((p) => {
      use(p.id); use(p.id)
      ledger(p.id).then((l) => {
        expect(l.movements.map((m) => m.quantity)).to.deep.equal([13, -1, -1])
        expect(l.movements.map((m) => m.balanceAfter)).to.deep.equal([13, 12, 11])
        expect(l.purchase.remaining).to.equal(11)
      })
    })
  })
  it('rejectsUsageWhenNoSessionsLeft', () => {
    purchase().then((p) => {
      for (let i = 0; i < 13; i += 1) use(p.id)
      useRequest(p.id).then(conflict('ครบแล้ว'))
      ledger(p.id).its('purchase.remaining').should('equal', 0)
    })
  })
  it('rejectsUsageOfExpiredCourse', () => {
    cy.api('POST', '/api/courses', { code: 'EXPIRED', name: 'คอร์สอายุสั้น', price: 1000, sessions: 5, bonusSessions: 0, validityDays: 1 }).then(ok(201)).then((c) => {
      purchase(c.id).then((p) => {
        cy.api('POST', `/__cypress/financial-fixtures/purchases/${p.id}/expire`).then(ok(200))
        useRequest(p.id).then(conflict('หมดอายุ'))
      })
    })
  })
  it('reversingUsageGivesTheSessionBackAndKeepsTheOriginalRow', () => {
    purchase().then((p) => {
      use(p.id)
      usageId(p.id).then((id) => {
        reverse(id).then(ok(200)).then((after) => {
          expect(after.remaining).to.equal(13); expect(after.used).to.equal(0)
        })
        ledger(p.id).then((l) => {
          expect(l.movements).to.have.length(3); expect(l.movements[1].type).to.equal('USAGE')
          expect(l.movements[2].reversesMovementId).to.equal(id)
        })
      })
    })
  })
  it('rejectsReversingTheSameMovementTwice', () => {
    purchase().then((p) => {
      use(p.id)
      usageId(p.id).then((id) => {
        reverse(id).then(ok(200)); reverse(id, 'กดซ้ำ').then(conflict('ถูกกลับรายการไปแล้ว'))
      })
    })
  })
  it('rejectsReversingAPurchaseMovement', () => {
    purchase().then((p) => ledger(p.id)).then((l) => {
      reverse(l.movements[0].id, 'ลองดู').then(conflict('เฉพาะรายการใช้สิทธิ์'))
    })
  })
  it('cancellingWritesOffTheRemainingBalanceButKeepsUsedSessions', () => {
    purchase().then((p) => {
      use(p.id); use(p.id)
      cancel(p.id).then(ok(200)).then((c) => {
        expect(c.status).to.equal('CANCELLED'); expect(c.remaining).to.equal(0)
        expect(c.used).to.equal(2); expect(c.usable).to.equal(false)
      })
    })
  })
  it('rejectsUsageAfterCancellation', () => {
    purchase().then((p) => {
      cancel(p.id).then(ok(200)); useRequest(p.id).then(conflict('ถูกยกเลิกแล้ว'))
    })
  })
  it('rejectsCancellingTwice', () => {
    purchase().then((p) => {
      cancel(p.id, 'ครั้งแรก').then(ok(200)); cancel(p.id, 'ครั้งที่สอง').then(conflict())
    })
  })
  it('nothingIsEverDeletedFromTheLedger', () => {
    purchase().then((p) => {
      use(p.id)
      usageId(p.id).then((id) => {
        reverse(id, 'แก้ไข').then(ok(200)); cancel(p.id, 'ยกเลิกคอร์ส').then(ok(200))
        cy.api('GET', `/__cypress/financial-fixtures/purchases/${p.id}/movement-ids`).then(ok(200)).should('have.length', 4)
      })
    })
  })
  it('listsCoursesOfThePatient', () => {
    purchase(); purchase()
    cy.api('GET', `/api/patients/${patientId}/courses`).then(ok(200)).should('have.length', 2)
  })
})

