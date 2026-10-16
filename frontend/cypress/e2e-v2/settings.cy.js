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
    modal().contains('label', 'รหัสสาขา').find('input').type('03')
    modal().contains('label', 'ชื่อสาขา').find('input').type('สาขาทดสอบ')
    modal().contains('label', 'ที่อยู่').find('input').type('1 ถนนทดสอบ')
    modal().contains('label', 'ข้อมูลติดต่อ').find('input').type('021234567')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', '03').should('contain.text', 'สาขาทดสอบ')
    cy.api('GET', '/api/branches').its('body').should((branches) => {
      expect(branches.map((branch) => branch.branchCode)).to.include('03')
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
    cy.contains('tbody tr', 'Manager').should('contain.text', 'ใช้งาน')
    cy.get('tbody tr').should('have.length.at.least', 2)
  })

  it('adds a user who can then sign in', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('ผู้ใช้และสิทธิ์').click()
    openEditor('+ เพิ่มผู้ใช้งาน')
    modal().contains('label', 'ชื่อผู้ใช้งาน').find('input').type('ทดสอบ ระบบ')
    modal().contains('label', 'Username').find('input').type('newtherapist')
    modal().contains('label', 'อีเมล').find('input').type('newtherapist@example.com')
    modal().contains('label', 'รหัสผ่าน').find('input').type('Secret123')
    modal().contains('label', 'รหัสพนักงาน').find('input').type('E900')
    modal().contains('label', 'โทรศัพท์').find('input').type('0800000000')
    cy.chooseDropdown('Role', 'Therapist')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.apiLogin('newtherapist', 'Secret123').its('status').should('equal', 200)
  })

  it('switches a payment method off and on again', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('การชำระเงิน').click()
    cy.get('button.payment-option').first().as('switch')
    cy.get('@switch').should('have.class', 'selected').click()
    cy.get('@switch').should('not.have.class', 'selected')
    cy.get('@switch').click()
    cy.get('@switch').should('have.class', 'selected')
  })

  it('adds a treatment room to a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('Room / Resource').click()
    openEditor('+ เพิ่มห้อง')
    modal().contains('label', 'ชื่อห้อง').find('input').type('ห้องทดสอบ 9')
    cy.chooseDropdown('ประเภท', 'Treatment Room')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', 'ห้องทดสอบ 9').should('contain.text', 'พร้อมใช้งาน')
    cy.api('GET', '/api/resources').its('body').should((rooms) => {
      expect(rooms.map((room) => room.name)).to.include('ห้องทดสอบ 9')
    })
  })

  it('adds a service to the catalogue', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('บริการและคอร์ส').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อคอร์ส').find('input').type('นวดทดสอบ')
    cy.chooseDropdown('ประเภท', 'Single Visit')
    modal().contains('label', 'ราคา').find('input').type('1200')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.contains('tbody tr', 'นวดทดสอบ').should('contain.text', '1200')
  })
})
