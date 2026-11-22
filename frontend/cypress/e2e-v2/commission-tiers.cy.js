/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const currentMonth = today.slice(0, 7)

describe('commission tiers', () => {
  it('lists the five tiers of the current scheme with their rates', () => {
    cy.visitAs('manager', '/staff/settings/commission-tiers')
    cy.contains('h1', 'กฎค่าคอมมิชชัน').should('be.visible')
    cy.get('tbody tr').should('have.length', 5)
    cy.get('tbody tr').eq(0).should('contain.text', 'Tier 1').and('contain.text', '5%')
    cy.get('tbody tr').eq(2).should('contain.text', '7%')
    cy.get('tbody tr').eq(4).should('contain.text', '10%').and('contain.text', 'ไม่จำกัด')
  })

  it('saves a changed rate as a new scheme version and keeps it after a reload', () => {
    cy.api('POST', `/api/commission/closings/${currentMonth}-01?branchCode=01`).its('status').should('equal', 200)
    cy.visitAs('manager', '/staff/settings/commission-tiers')
    cy.contains('button', '+ เพิ่มเวอร์ชันใหม่').click()
    cy.get('input[aria-label="อัตรา Tier 1"]').clear().type('5.5')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก Scheme').click()
    cy.noAlert()
    cy.get('tbody tr').eq(0).should('contain.text', '5.5%')
    cy.reload()
    cy.get('tbody tr').eq(0).should('contain.text', '5.5%')
    cy.api('GET', '/api/commission/schemes').then(({ body }) => {
      expect(body, 'the old version is kept').to.have.length(2)
      const latest = body.find((s) => s.version === 2) || body[0]
      return cy.api('GET', `/api/commission/schemes/${latest.id}/tiers`)
    })
      .its('body.0.rate').should('equal', 0.055)
  })

  it('refuses a tier whose range overlaps another', () => {
    cy.visitAs('manager', '/staff/settings/commission-tiers')
    cy.contains('button', '+ เพิ่มเวอร์ชันใหม่').click()
    cy.get('input[aria-label="ยอดขายสูงสุด Tier 1"]').clear().type('65000')
    cy.get('input[aria-label="ยอดขายสูงสุด Tier 2"]').clear().type('62000')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก Scheme').click()
    cy.contains('.drawer-error', 'ตรวจช่วงยอดขายและอัตราของ Tier').should('be.visible')
    cy.get('tbody tr').should('have.length', 5)
  })

  it('refuses a minimum above the maximum', () => {
    cy.visitAs('manager', '/staff/settings/commission-tiers')
    cy.contains('button', '+ เพิ่มเวอร์ชันใหม่').click()
    cy.get('input[aria-label="ยอดขายขั้นต่ำ Tier 1"]').clear().type('200000')
    cy.get('input[aria-label="ยอดขายสูงสุด Tier 1"]').clear().type('100000')
    cy.get('.tier-drawer footer').contains('button', 'บันทึก Scheme').click()
    cy.get('.drawer-error').should('be.visible')
  })
})
