/* oxlint-disable vitest/valid-expect -- Cypress uses Chai. */
import { api, visit, patient, sale, datePicker, fixtures } from '../support-therapist/helpers'

function fillThai() {
  cy.get('input[placeholder="ชื่อ"]').type('อรุณ')
  cy.get('input[placeholder="นามสกุล"]').type('ทดสอบ')
  cy.get('input[placeholder="x-xxxx-xxxxx-xx-x"]').type('1100000999999')
  cy.get('input[placeholder="xxx-xxx-xxxx"]').type('0898765432')
  datePicker('.thai-date-picker')
}

describe('Therapist patient registration, profile and history', () => {
  it('PAT-01 registers Thai patient through UI and persists identity and birth date', () => {
    visit('/staff/patients/new')
    cy.get('button[aria-label="สาขาที่ลงทะเบียน"]').should('not.contain.text', 'เลือกสาขา')
    fillThai()
    cy.intercept('POST', '**/api/patients').as('created')
    cy.get('button[type="submit"]').click()
    cy.get('[aria-labelledby="registration-confirmation-title"]').contains('button', /^ใช่$/).click()
    cy.wait('@created').then(({ response }) => {
      expect(response.statusCode).to.equal(201)
      api('GET', `/api/patients/${response.body.id}`).then(({ body, status }) => {
        expect(status).to.equal(200)
        expect(body.fullName).to.equal('อรุณ ทดสอบ')
        expect(body.birthDate).to.equal('1990-01-01')
        expect(body.identityNumber).to.equal('1100000999999')
      })
      cy.get('[aria-labelledby="registration-success-title"]').should('contain.text', response.body.hn)
    })
  })

  it('PAT-02 required fields prevent an empty registration from creating records', () => {
    visit('/staff/patients/new')
    cy.get('button[type="submit"]').click()
    cy.get('.required-error').should('have.length.at.least', 4)
    cy.get('[aria-labelledby="registration-confirmation-title"]').should('not.exist')
    api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
  })

  for (const [field, value, message] of [
    ['xxx-xxx-xxxx', '123456789', 'กรุณากรอกตัวเลข 10 หลัก'],
    ['x-xxxx-xxxxx-xx-x', '123456789012', 'กรุณากรอกตัวเลข 13 หลัก'],
    ['example@gmail.com', 'invalid-email', 'รูปแบบอีเมลไม่ถูกต้อง'],
  ]) {
    it(`PAT-03 boundary/invalid ${field}: ${value}`, () => {
      visit('/staff/patients/new')
      fillThai()
      cy.get(`input[placeholder="${field}"]`).clear().type(value).blur()
      cy.get('button[type="submit"]').click()
      cy.contains('.required-error', message).should('be.visible')
      cy.get('[aria-labelledby="registration-confirmation-title"]').should('not.exist')
      api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
    })
  }

  it('PAT-04 duplicate identity submission cannot create a second patient', () => {
    patient({ identityNumber: '1100000999999' }).then(() => {
      visit('/staff/patients/new')
      fillThai()
      cy.intercept('POST', '**/api/patients').as('duplicate')
      cy.get('button[type="submit"]').click()
      cy.get('[role="alertdialog"]').then(($dialog) => {
        if ($dialog.hasClass('duplicate-modal')) return
        cy.wrap($dialog).contains('button', /^ใช่$/).click()
        cy.wait('@duplicate').its('response.statusCode').should('equal', 409)
        cy.get('[role="alert"], .duplicate-modal').should('be.visible')
      })
      api('GET', '/api/patients').its('body.totalItems').should('equal', 1)
    })
  })

  for (const [label, source] of [['unset referral', undefined], ['valid referral', 'Friend']]) {
  it(`PAT-05 edits phone/address with ${label} and persists after reload`, () => {
    patient(source ? { customerSource: source } : {}).then((p) => {
      visit(`/staff/patients/${p.id}`)
      cy.contains('button', 'แก้ไขข้อมูล').click()
      cy.get('input[type="tel"]').clear().type('0891112222')
      cy.get('textarea').clear().type('เชียงใหม่')
      cy.intercept('PUT', `**/api/patients/${p.id}`).as('updated')
      cy.get('form').contains('button', /^บันทึก$/).click()
      cy.get('[role="alertdialog"]').contains('button', /^ใช่$/).click()
      cy.wait('@updated').then(({ response }) => expect(response.statusCode, JSON.stringify(response.body)).to.equal(200))
      cy.get('[role="status"]').should('contain.text', 'แก้ไขข้อมูลสำเร็จ')
      api('GET', `/api/patients/${p.id}`).then(({ body }) => {
        expect(body.phone).to.equal('0891112222')
        expect(body.address).to.equal('เชียงใหม่')
        expect(body.hn).to.equal(p.hn)
      })
      cy.reload()
      cy.get('main').should('contain.text', '0891112222').and('contain.text', 'เชียงใหม่')
    })
  })
  }

  it('PAT-06 cancelling edit leaves persisted data unchanged', () => {
    patient().then((p) => {
      visit(`/staff/patients/${p.id}`)
      cy.contains('button', 'แก้ไขข้อมูล').click()
      cy.get('input[type="tel"]').clear().type('0891112222')
      cy.contains('button', /^ยกเลิก$/).click()
      cy.get('main').should('contain.text', p.phone)
      api('GET', `/api/patients/${p.id}`).its('body.phone').should('equal', p.phone)
    })
  })

  it('PAT-07 history has distinct empty payment and course states', () => {
    patient().then((p) => {
      visit(`/staff/patients/${p.id}/history`)
      cy.get('main').should('contain.text', 'ไม่พบประวัติการชำระเงิน')
      cy.get('.history-tabs').contains('button', 'คอร์สรักษา').click()
      cy.get('main').should('contain.text', 'ไม่พบประวัติคอร์ส')
    })
  })

  it('PAT-08 displays real paid receipt, filters history and previews/prints it', () => {
    patient().then((p) => sale(p.id).then((receipt) => {
      visit(`/staff/patients/${p.id}/history`)
      cy.get('main').should('contain.text', receipt.receiptNo).and('contain.text', '800.00')
      cy.get('input[placeholder="ค้นหาเลขที่ใบเสร็จหรือชื่อคอร์ส"]').type('absent')
      cy.get('main').should('contain.text', 'ไม่พบประวัติการชำระเงิน')
      cy.get('input[placeholder="ค้นหาเลขที่ใบเสร็จหรือชื่อคอร์ส"]').clear().type(receipt.receiptNo)
      cy.contains('button', 'ดูใบเสร็จ').click()
      cy.get('main').should('contain.text', 'ใบเสร็จรับเงิน').and('contain.text', receipt.receiptNo)
      cy.window().then((win) => cy.stub(win, 'print').as('print'))
      cy.get('.receipt-page-main').contains('button', 'พิมพ์เอกสาร').click()
      cy.get('[role="dialog"]').contains('button', 'ยืนยันและพิมพ์').click()
      cy.get('@print').should('have.been.calledOnce')
    }))
  })

  for (const id of ['not-a-number', '99999999']) {
    it(`PAT-09 invalid/missing profile ${id} displays error`, () => {
      visit(`/staff/patients/${id}`)
      cy.get('[role="alert"]').should('be.visible')
      cy.contains('button', 'แก้ไขข้อมูล').should('not.exist')
    })
  }

  it('PAT-10 rejects a future birth date before sending a registration', () => {
    visit('/staff/patients/new')
    fillThai()
    datePicker('.thai-date-picker', fixtures.tomorrow)
    cy.get('button[type="submit"]').click()
    cy.contains('.required-error', 'วันเกิดต้องเป็นวันในอดีต').should('be.visible')
    api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
  })

  it('PAT-11 cancelling registration confirmation preserves the form and creates nothing', () => {
    visit('/staff/patients/new')
    fillThai()
    cy.get('button[type="submit"]').click()
    cy.get('[aria-labelledby="registration-confirmation-title"]').contains('button', /^ไม่ใช่$/).click()
    cy.get('input[placeholder="ชื่อ"]').should('have.value', 'อรุณ')
    cy.get('[aria-labelledby="registration-confirmation-title"]').should('not.exist')
    api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
  })
})
