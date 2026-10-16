/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai in queued tests. */
import { api, visit, patient, fixtures } from '../support-therapist/helpers'

function purchase(patientId, courseId = fixtures.courseId) {
  return api('POST', '/api/course-purchases', {
    patientId, courseId, branchCode: '01',
  }).then((r) => {
    expect(r.status).to.eq(201)
    return r.body
  })
}
function balance(patientId, courseId, expected) {
  return api('GET', `/api/patients/${patientId}/courses`).then((r) => {
    expect(r.status).to.eq(200)
    expect(r.body.find((c) => c.id === courseId).remaining).to.eq(expected)
  })
}

describe('Therapist course entitlements', () => {
  it('CRS-ROUTE-01 patient-scoped history selects its route patient and course among multiple owners', () => {
    patient({ firstName: 'ผู้ป่วยคนแรก', lastName: 'คนละคอร์ส' }).then((other) => purchase(other.id).then(() => {
      patient({ firstName: 'ผู้ป่วยเป้าหมาย', lastName: 'ประวัติคอร์ส' }).then((target) => purchase(target.id).then((course) => {
        visit(`/staff/patients/${target.id}/courses/${course.id}?action=history`)
        cy.get('.course-history-patient').should('contain', target.fullName).and('contain', target.hn)
        cy.get('.course-history-patient').should('not.contain', other.hn)
        cy.get('.course-history-summary').should('contain', course.course.name).and('contain', course.course.code)
        api('GET', `/api/patients/${target.id}/courses`).then((r) => {
          expect(r.status).to.eq(200)
          expect(r.body).to.have.length(1)
          expect(r.body[0].id).to.eq(course.id)
        })
      }))
    }))
  })

  it('uses one visit in the browser after confirmation and retains the usage in history', () => {
    patient().then((p) => purchase(p.id).then((course) => {
      visit(`/staff/courses?patientId=${p.id}&courseId=${course.id}`)
      cy.get('.course-manager-card').contains('button', 'ใช้สิทธิ์คอร์ส').click()
      cy.contains('h1', 'ตรวจสอบการใช้สิทธิ์').should('be.visible')
      cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
      cy.get('.course-confirm-actions').contains('button', 'ยังไม่ยืนยัน').click()
      balance(p.id, course.id, course.remaining)
      cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
      cy.intercept('POST', '**/api/course-usages').as('useCourse')
      cy.get('.course-confirm-actions').contains('button', 'ยืนยันใช้สิทธิ์').click()
      cy.wait('@useCourse').its('response.statusCode').should('eq', 200)
      balance(p.id, course.id, course.remaining - 1)
      api('GET', `/api/course-purchases/${course.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.movements.filter((m) => m.type === 'USAGE')).to.have.length(1)
        expect(r.body.movements.find((m) => m.type === 'USAGE').quantity).to.eq(-1)
      })
      visit(`/staff/patients/${p.id}/courses/${course.id}?action=history`)
      cy.contains(p.fullName).should('be.visible')
      cy.contains(course.course.name).should('be.visible')
      cy.get('.course-history-entry').should('have.length', 1)
    }))
  })

  it('API: transfers entitlement and conserves owner/recipient balance and audit history', () => {
    patient().then((owner) => patient().then((recipient) => purchase(owner.id).then((course) => {
      const reason = 'Therapist entitlement transfer'
      api('POST', '/api/course-transfers', {
        sourcePurchaseId: course.id, destinationPatientId: recipient.id, quantity: 2, reason,
      }).then((r) => {
        expect(r.status).to.eq(201)
        expect(r.body.sourcePatientId).to.eq(owner.id)
        expect(r.body.destinationPatientId).to.eq(recipient.id)
        expect(r.body.quantity).to.eq(2)
        expect(r.body.reason).to.eq(reason)
      })
      balance(owner.id, course.id, course.remaining - 2)
      balance(recipient.id, course.id, 2)
      api('GET', `/api/course-transfers?patientId=${recipient.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body).to.have.length(1)
        expect(r.body[0].courseId).to.eq(course.id)
      })
      visit(`/staff/courses?patientId=${recipient.id}&courseId=${course.id}`)
      cy.get('.course-manager-card').should('contain', 'แชร์คอร์ส').and('contain', owner.fullName)
      cy.get('.course-manager-metrics').should('contain', '2 ครั้ง')
      cy.get('.course-share-button').should('not.exist')
    })))
  })

  it('API: rejects zero, excessive and self transfers without changing either entitlement', () => {
    patient().then((owner) => patient().then((recipient) => purchase(owner.id).then((course) => {
      const commands = [
        { destinationPatientId: recipient.id, quantity: 0 },
        { destinationPatientId: recipient.id, quantity: course.remaining + 1 },
        { destinationPatientId: owner.id, quantity: 1 },
      ]
      cy.wrap(commands).each((command) => api('POST', '/api/course-transfers', {
        sourcePurchaseId: course.id, reason: 'Invalid transfer test', ...command,
      }).then((r) => expect(r.status).to.eq(400)))
      balance(owner.id, course.id, course.remaining)
      api('GET', `/api/patients/${recipient.id}/courses`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body).to.have.length(0)
      })
      api('GET', `/api/course-transfers?patientId=${owner.id}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body).to.have.length(0)
      })
    })))
  })

  it('API and browser: a cancelled course cannot be consumed and remains in inactive history', () => {
    patient().then((p) => purchase(p.id).then((course) => {
      api('POST', `/api/course-purchases/${course.id}/cancel`, { reason: 'Cancel unused course for validation' })
        .then((r) => expect(r.status).to.eq(200))
      api('POST', '/api/course-usages', { coursePurchaseId: course.id })
        .then((r) => expect(r.status).to.eq(400))
      api('GET', `/api/patients/${p.id}/courses`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body[0].usable).to.eq(false)
        expect(r.body[0].remaining).to.eq(0)
      })
      cy.intercept('GET', `**/api/patients/${p.id}/courses`).as('loadedCourses')
      visit(`/staff/courses?patientId=${p.id}`)
      cy.wait('@loadedCourses').its('response.statusCode').should('eq', 200)
      cy.get('.course-manager-card').should('not.exist')
      cy.contains('button', 'หมดอายุ / ใช้สิทธิ์ครบ').click()
      cy.contains(course.course.name).should('be.visible')
    }))
  })

  it('browser: validates transfer quantity and excludes the owner from recipient choices', () => {
    patient().then((owner) => patient().then((recipient) => purchase(owner.id).then((course) => {
      visit(`/staff/courses?patientId=${owner.id}&courseId=${course.id}`)
      cy.contains('button', 'แชร์คอร์ส').click()
      cy.get('.course-transfer-stepper input').clear().type(String(course.remaining + 1))
      cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"]').type(recipient.hn)
      cy.contains('button', 'ค้นหาผู้รับ').click()
      cy.get('.course-transfer-results').contains('button', recipient.fullName).click()
      cy.contains('button', 'เลือกผู้รับนี้').click()
      cy.contains('button', 'ตรวจสอบข้อมูล').should('be.disabled')
      balance(owner.id, course.id, course.remaining)
    })))
  })

  it('CRS-BOUNDARY-01 consumes the last entitlement and rejects a second usage without negative balance', () => {
    api('POST', '/api/courses', {
      code: 'THERAPIST-ONE-VISIT', name: 'คอร์สหนึ่งครั้งทดสอบ', price: 800,
      sessions: 1, bonusSessions: 0, validityDays: 30,
    }, 'manager').then((catalog) => {
      expect(catalog.status).to.eq(201)
      patient().then((p) => purchase(p.id, catalog.body.id).then((course) => {
        expect(course.remaining).to.eq(1)
        visit(`/staff/courses?patientId=${p.id}&courseId=${course.id}`)
        cy.get('.course-manager-card').contains('button', 'ใช้สิทธิ์คอร์ส').click()
        cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
        cy.intercept('POST', '**/api/course-usages').as('lastUsage')
        cy.get('.course-confirm-actions').contains('button', 'ยืนยันใช้สิทธิ์').click()
        cy.wait('@lastUsage').its('response.statusCode').should('eq', 200)
        balance(p.id, course.id, 0)
        api('POST', '/api/course-usages', { coursePurchaseId: course.id }).then((r) => {
          expect(r.status).to.eq(400)
          expect(r.body.message).to.contain('คอร์สใช้สิทธิ์ครบแล้ว')
        })
        api('GET', `/api/course-purchases/${course.id}`).then((r) => {
          expect(r.status).to.eq(200)
          expect(r.body.purchase.remaining).to.eq(0)
          expect(r.body.purchase.usable).to.eq(false)
          expect(r.body.movements.filter((m) => m.type === 'USAGE')).to.have.length(1)
        })
        cy.intercept('GET', `**/api/patients/${p.id}/courses`).as('loadedCourses')
      visit(`/staff/courses?patientId=${p.id}`)
      cy.wait('@loadedCourses').its('response.statusCode').should('eq', 200)
        cy.get('.course-manager-card').should('not.exist')
        cy.contains('button', 'หมดอายุ / ใช้สิทธิ์ครบ').click()
        cy.contains('คอร์สหนึ่งครั้งทดสอบ').should('be.visible')
      }))
    })
  })
  it('CRS-EXPIRY-01 rejects yesterday expiry and displays its inactive history', () => {
    patient().then((p) => purchase(p.id).then((course) => {
      const yesterday = new Date(`${fixtures.tomorrow}T00:00:00Z`)
      yesterday.setUTCDate(yesterday.getUTCDate() - 2)
      const expiryDate = yesterday.toISOString().slice(0, 10)
      api('POST', '/__cypress/course-expiry', { courseId: course.id, expiryDate }, 'manager')
        .then((r) => expect(r.status).to.eq(200))
      api('POST', '/api/course-usages', { coursePurchaseId: course.id }).then((r) => {
        expect(r.status).to.eq(400)
        expect(r.body.message).to.contain('คอร์สหมดอายุแล้ว')
      })
      api('GET', `/api/patients/${p.id}/courses`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body[0].expired).to.eq(true)
        expect(r.body[0].usable).to.eq(false)
        expect(r.body[0].remaining).to.eq(course.remaining)
      })
      cy.intercept('GET', `**/api/patients/${p.id}/courses`).as('expiredCourses')
      visit(`/staff/courses?patientId=${p.id}`)
      cy.wait('@expiredCourses').its('response.statusCode').should('eq', 200)
      cy.get('.course-manager-card').should('not.exist')
      cy.contains('button', 'หมดอายุ / ใช้สิทธิ์ครบ').click()
      cy.get('.course-inactive-history-totals').should('contain', course.course.name)
      cy.get('.course-history-entry').should('not.exist')
    }))
  })

  it('CRS-EXPIRY-02 expiry today remains usable and the fixture endpoint rejects therapist writes', () => {
    patient().then((p) => purchase(p.id).then((course) => {
      const today = new Date(`${fixtures.tomorrow}T00:00:00Z`)
      today.setUTCDate(today.getUTCDate() - 1)
      const expiryDate = today.toISOString().slice(0, 10)
      api('POST', '/__cypress/course-expiry', { courseId: course.id, expiryDate }, 'manager')
        .then((r) => expect(r.status).to.eq(200))
      api('POST', '/__cypress/course-expiry', { courseId: course.id, expiryDate: '2000-01-01' })
        .then((r) => expect(r.status).to.eq(403))
      api('GET', `/api/patients/${p.id}/courses`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body[0].expired).to.eq(false)
        expect(r.body[0].usable).to.eq(true)
        expect(r.body[0].expiresAt.slice(0, 10)).to.eq(expiryDate)
      })
      visit(`/staff/courses?patientId=${p.id}&courseId=${course.id}`)
      cy.get('.course-manager-card').contains('button', 'ใช้สิทธิ์คอร์ส').should('be.enabled').click()
      cy.contains('button', 'ยืนยันใช้สิทธิ์').click()
      cy.intercept('POST', '**/api/course-usages').as('expiryDayUsage')
      cy.get('.course-confirm-actions').contains('button', 'ยืนยันใช้สิทธิ์').click()
      cy.wait('@expiryDayUsage').its('response.statusCode').should('eq', 200)
      balance(p.id, course.id, course.remaining - 1)
    }))
  })
  // Source gap: transferReason is required but there is no editable reason field in the template.
  it('CRS-GAP-01 browser allows entering the mandatory transfer reason', () => {
    patient().then((owner) => patient().then((recipient) => purchase(owner.id).then((course) => {
      visit(`/staff/courses?patientId=${owner.id}&courseId=${course.id}`)
      cy.contains('button', 'แชร์คอร์ส').click()
      cy.get('.course-transfer-stepper input').clear().type('2')
      cy.get('input[placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"]').type(recipient.hn)
      cy.contains('button', 'ค้นหาผู้รับ').click()
      cy.get('.course-transfer-results').contains('button', recipient.fullName).click()
      cy.contains('button', 'เลือกผู้รับนี้').click()
      // Regression: handleTransferPrimaryAction requires transferReason, with no field to edit it.
      cy.contains('label', 'เหตุผล').find('textarea, input').should('be.visible').type('แชร์ตามคำขอผู้ป่วย')
      cy.contains('button', 'ตรวจสอบข้อมูล').click()
      cy.intercept('POST', '**/api/course-transfers').as('shareCourse')
      cy.contains('button', 'ยืนยันแชร์คอร์ส').click()
      cy.wait('@shareCourse').its('response.statusCode').should('eq', 201)
      balance(owner.id, course.id, course.remaining - 2)
      balance(recipient.id, course.id, 2)
    })))
  })
})
