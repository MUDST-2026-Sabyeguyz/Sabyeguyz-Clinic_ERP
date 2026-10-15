/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai. */
const profile = {
  id: 42, hn: '2601080022', title: 'นาย', fullName: 'สมชาย ใจดี',
  phone: '089-555-1234', active: true, birthDate: '1991-01-10', gender: 'ชาย',
  nickname: 'ชาย', email: 'somchai@example.com', bloodGroup: 'A', nationality: 'ไทย',
  identityDocumentType: 'NATIONAL_ID', identityNumber: '1234567890123',
  address: '99 ถนนราชพฤกษ์ กรุงเทพมหานคร 10170', customerType: 'ทั่วไป',
  customerSource: 'ลูกค้าแนะนำ', insuranceCompany: '',
  branch: { id: 1, code: 'BEARING', name: 'สาขาแบริ่ง', active: true },
}

describe('patient profile from the patient list', () => {
  it('opens the selected patient, shows grouped data, and keeps edit and tab navigation working', () => {
    cy.viewport(1440, 1244)
    cy.intercept('GET', '**/api/branches', [profile.branch])
    cy.intercept('GET', '**/api/patients?*', {
      items: [profile], totalPages: 1, totalItems: 1, page: 0, size: 20,
    })
    cy.intercept('GET', '**/api/patients/42', profile).as('profile')
    cy.visit('/staff/patients', {
      onBeforeLoad(win) {
        win.localStorage.setItem('authUser', JSON.stringify({ role: 'THERAPIST', branchName: 'สาขาแบริ่ง' }))
        win.localStorage.setItem('authToken', 'test-token')
      },
    })
    cy.contains('a', 'ดูข้อมูล').click()
    cy.location('pathname').should('eq', '/staff/patients/42')
    cy.get('.patient-profile-summary').should('contain', 'นายสมชาย ใจดี').and('contain', profile.hn)
    cy.get('.patient-profile-section').should('have.length', 3)
    cy.get('.patient-profile-details').should('contain', '10 มกราคม 2534').and('contain', 'สาขาแบริ่ง')
    cy.get('.patient-profile-tabs a').eq(1).should('have.attr', 'href', '/staff/patients/42/history')
    cy.get('.patient-profile-tabs a').eq(2).should('have.attr', 'href', '/staff/patients/42/history?tab=courses')
    cy.screenshot('patient-profile-desktop', { capture: 'fullPage' })
    cy.contains('button', 'แก้ไขข้อมูล').click()
    cy.get('.patient-edit').should('be.visible')
    cy.get('.patient-edit input').should('have.value', 'somchai@example.com')
    cy.contains('button', 'ยกเลิก').click()
    cy.get('.patient-profile-details').should('be.visible')
    cy.viewport(390, 844)
    cy.get('.patient-profile-details').should('be.visible')
    cy.document().then((doc) => {
      expect(doc.documentElement.scrollWidth).to.be.at.most(390)
    })
    cy.screenshot('patient-profile-mobile', { capture: 'fullPage' })
  })
})
