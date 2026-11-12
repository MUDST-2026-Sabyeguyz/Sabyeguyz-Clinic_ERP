/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */

describe('commission tiers', () => {
  it('lists the five tiers of the current scheme with their rates', () => {
    cy.visitAs('manager', '/staff/commission/tiers')
    cy.contains('h1', 'ตั้งค่า Commission Tier').should('be.visible')
    cy.get('tbody tr').should('have.length', 5)
    cy.get('tbody tr').eq(0).should('contain.text', 'Tier 1').and('contain.text', '5%')
    cy.get('tbody tr').eq(2).should('contain.text', '7%')
    cy.get('tbody tr').eq(4).should('contain.text', '10%').and('contain.text', 'ไม่จำกัด')
  })

  it('saves a changed rate as a new scheme version and keeps it after a reload', () => {
    cy.visitAs('manager', '/staff/commission/tiers')
    cy.contains('button', 'แก้กฎคอมมิชชัน').click()
    cy.get('.scheme-tier-editor article').first().contains('label', 'อัตรา (%)').find('input').clear().type('5.5')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก').click()
    cy.noAlert()
    cy.get('tbody tr').eq(0).should('contain.text', '5.5%')
    cy.reload()
    cy.get('tbody tr').eq(0).should('contain.text', '5.5%')
    cy.api('GET', '/api/commission/schemes').then(({ body }) => {
      expect(body, 'the old version is kept').to.have.length(2)
      return cy.api('GET', `/api/commission/schemes/${body[0].id}/tiers`)
    })
      .its('body.0.rate').should('equal', 0.055)
  })

  it('refuses a tier whose range overlaps another', () => {
    cy.visitAs('manager', '/staff/commission/tiers')
    cy.contains('button', 'แก้กฎคอมมิชชัน').click()
    cy.get('.scheme-tier-editor article').first().contains('label', 'สูงสุด').find('input').clear().type('65000')
    cy.get('.scheme-tier-editor article').eq(1).contains('label', 'ขั้นต่ำ').find('input').should('have.value', '60000')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก').click()
    cy.contains('.drawer-error', 'ตรวจช่วงยอดขายและอัตราของ Tier').should('be.visible')
    cy.get('tbody tr').should('have.length', 5)
  })

  it('refuses a minimum above the maximum', () => {
    cy.visitAs('manager', '/staff/commission/tiers')
    cy.contains('button', 'แก้กฎคอมมิชชัน').click()
    cy.get('.scheme-tier-editor article').first().contains('label', 'ขั้นต่ำ').find('input').clear().type('200000')
    cy.get('.scheme-tier-editor article').first().contains('label', 'สูงสุด').find('input').clear().type('100000')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก').click()
    cy.get('.drawer-error').should('be.visible')
  })
})
