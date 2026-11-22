/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const sectionButton = (name) => cy.contains('.settings-nav button', name)
const openEditor = (label) => cy.contains('button.settings-add', label).click()
const modal = () => cy.get('.drawer')
const save = () => {
  cy.get('.drawer').contains('button', 'บันทึก').click()
  cy.noAlert()
}

describe('settings', () => {
  it('is closed to a therapist', () => {
    cy.visitAs('therapist', '/staff/settings')
    cy.location('pathname').should('eq', '/staff/patients')
  })

  it('lists the clinic branches', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('h1', 'การตั้งค่า').should('be.visible')
    cy.contains('h2', /สาขา/).should('be.visible')
    cy.contains('tbody tr', '01').should('contain.text', 'ใช้งาน')
    cy.contains('tbody tr', '02').should('exist')
  })

  it('adds a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    openEditor('+ เพิ่มสาขา')
    modal().contains('label', 'ชื่อสาขา').find('input').type('สาขาทดสอบ')
    modal().contains('label', 'ที่อยู่').find('input').type('1 ถนนทดสอบ')
    modal().contains('label', 'เบอร์โทรศัพท์').find('input').type('021234567')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', 'สาขาทดสอบ').should('be.visible')
    cy.api('GET', '/api/branches').its('body').should((branches) => {
      expect(branches.map((branch) => branch.name)).to.include('สาขาทดสอบ')
    })
  })

  it('renames a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('tbody tr td', /^02$/).closest('tr').contains('button', 'แก้ไข').click()
    modal().contains('label', 'ชื่อสาขา').find('input').clear().type('สาขาเปลี่ยนชื่อ')
    save()
    cy.contains('tbody tr td', /^02$/).closest('tr').should('contain.text', 'สาขาเปลี่ยนชื่อ')
    cy.api('GET', '/api/branches').its('body').should((branches) => {
      expect(branches.find((branch) => branch.branchCode === '02').name).to.equal('สาขาเปลี่ยนชื่อ')
    })
  })

  it('lists the users and their roles', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('ผู้ใช้และสิทธิ์').click()
    cy.contains('tbody tr', 'Manager Demo').should('contain.text', 'ใช้งาน')
    cy.get('tbody tr').should('have.length.at.least', 2)
  })

  it('adds a user who can then sign in', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('ผู้ใช้และสิทธิ์').click()
    openEditor('+ เพิ่มผู้ใช้งาน')
    modal().contains('label', 'ชื่อ-นามสกุล').find('input').type('ทดสอบ ระบบ')
    modal().contains('label', 'Username').find('input').type('newtherapist')
    modal().contains('label', 'อีเมล').find('input').type('newtherapist@example.com')
    modal().contains('label', 'รหัสผ่าน').find('input').type('Secret123456')
    cy.get('.drawer button.filter-dropdown__button[aria-label="บทบาท"]').click()
    cy.get('[role="listbox"][aria-label="บทบาท"] [role="option"]').contains('นักกายภาพ').click()
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.apiLogin('newtherapist', 'Secret123456').its('status').should('equal', 200)
  })

  it('sets the status of each payment method after a confirmation', () => {
    cy.intercept('GET', '**/api/payment-methods').as('paymentMethods')
    cy.intercept('DELETE', '**/api/payment-methods/*').as('deactivateMethod')
    cy.intercept('PATCH', '**/api/payment-methods/*/activate').as('activateMethod')
    cy.visitAs('manager', '/staff/settings')
    cy.wait('@paymentMethods')
    sectionButton('การชำระเงิน').click()
    cy.get('.payment-list').first().find('.payment-option').should('have.length', 3).and('not.contain.text', 'Cash')
    cy.get('.payment-list').first().find('.payment-option').eq(1).as('method')
    cy.get('@method').find('button.disable').click()
    cy.get('.confirm-modal').should('be.visible').and('contain.text', 'ต้องการปิดใช้งาน').contains('button', 'ยกเลิก').click()
    cy.get('.confirm-modal').should('not.exist')
    cy.get('@method').find('button.disable').click()
    cy.get('.confirm-modal').should('be.visible').find('button.confirm-ok').click()
    cy.wait('@deactivateMethod').its('response.statusCode').should('equal', 204)
    cy.get('@method').find('button.disable').should('have.class', 'on')
    cy.get('@method').find('button.enable').click()
    cy.get('.confirm-modal').should('be.visible').find('button.confirm-ok').click()
    cy.wait('@activateMethod').its('response.statusCode').should('equal', 200)
    cy.get('@method').find('button.enable').should('have.class', 'on')
  })

  it('adds a treatment room to a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('Room / Resource').click()
    openEditor('+ เพิ่มห้อง')
    modal().contains('label', 'ชื่อห้อง/ทรัพยากร').find('input').type('ห้องทดสอบ 9')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', 'ห้องทดสอบ 9').should('contain.text', 'ใช้งาน')
    cy.api('GET', '/api/resources').its('body').should((rooms) => {
      expect(rooms.map((room) => room.name)).to.include('ห้องทดสอบ 9')
    })
  })

  it('adds a service to the catalogue', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('คอร์สและบริการ').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อบริการ/คอร์ส').find('input').type('นวดทดสอบ')
    cy.get('.drawer button.filter-dropdown__button[aria-label="ประเภท"]').click()
    cy.get('[role="listbox"][aria-label="ประเภท"] [role="option"]').contains('บริการรายครั้ง').click()
    modal().contains('label', 'ราคา (บาท)').find('input').type('1200')
    modal().contains('label', 'ระยะเวลา (นาที)').find('input').type('45')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', 'นวดทดสอบ').should('contain.text', '1200')
  })
})
