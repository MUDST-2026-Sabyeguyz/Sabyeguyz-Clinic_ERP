/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
const apiUrl = () => Cypress.expose('apiUrl')

// Talks to the backend directly (not through the web app) to arrange test data as the manager.
Cypress.Commands.add('api', (method, url, body, options = {}) => {
  return cy.get('@managerToken').then((token) => cy.request({
    method, url: `${apiUrl()}${url}`, body,
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
    ...options,
  }))
})

Cypress.Commands.add('apiLogin', (username, password) => {
  return cy.request({ method: 'POST', url: `${apiUrl()}/api/auth/login`, body: { username, password }, failOnStatusCode: false })
})

// Opens a page of the staff app already signed in as the given demo user (the app keeps its session in localStorage).
Cypress.Commands.add('visitAs', (username, path = '/') => {
  const passwords = { manager: 'manager123', therapist: 'therapist123', therapist2: 'therapist123' }
  return cy.apiLogin(username, passwords[username]).then(({ status, body }) => {
    expect(status, `login of ${username}`).to.equal(200)
    cy.visit(path, {
      onBeforeLoad(win) {
        win.localStorage.setItem('authToken', body.token)
        win.localStorage.setItem('authUser', JSON.stringify(body))
      },
    })
  })
})

// Picks a date in the app's Thai date picker (calendar year is sent, Buddhist year is shown).
Cypress.Commands.add('pickDate', { prevSubject: 'element' }, (subject, iso) => {
  const [year, month, day] = iso.split('-').map(Number)
  cy.wrap(subject).find('.date-trigger').click()
  cy.wrap(subject).find('select[aria-label="เลือกปี"]').select(String(year))
  cy.wrap(subject).find('select[aria-label="เลือกเดือน"]').select(String(month - 1))
  cy.wrap(subject).find('.days button').contains(new RegExp(`^\\s*${day}\\s*$`)).click()
})

let patientSequence = 1000
export function patientPayload(overrides = {}) {
  return {
    branchCode: '01', customerType: 'Thai', title: 'นาย', firstName: 'สมชาย', lastName: 'ใจดี', gender: 'Male',
    phone: '0811111111', identityDocumentType: 'NATIONAL_ID', identityNumber: String(1100000000000 + patientSequence++),
    birthDate: '1990-01-01', address: 'กรุงเทพฯ', ...overrides,
  }
}

Cypress.Commands.add('registerPatient', (overrides = {}) => {
  return cy.api('POST', '/api/patients', patientPayload(overrides)).then(({ status, body }) => {
    expect(status, JSON.stringify(body)).to.equal(201)
    return body
  })
})

// Fails with the text of any error the page is showing (used right after a save, so a rejected save explains itself).
Cypress.Commands.add('noAlert', () => {
  cy.wait(1500)
  cy.get('body').then(($body) => {
    const shown = [...$body.find('[role="alert"], .validation-error, .settings-error, .form-error, .error-message, .error, .drawer-error, .validation-message, .transaction-error, .report-error')]
      .map((element) => element.textContent.trim()).filter(Boolean)
    expect(shown.join(' | '), 'error shown on the page').to.equal('')
  })
})

// Use the actual accessible FilterDropdown UI rather than assuming native select elements.
Cypress.Commands.add('chooseDropdown', (label, text) => {
  cy.get(`button.filter-dropdown__button[aria-label="${label}"]`).should('be.visible').click()
  cy.get(`[role="listbox"][aria-label="${label}"] [role="option"]`).filter((_, option) =>
    option.querySelector('span:last-child').textContent.trim() === text,
  ).should('have.length', 1).click()
})
Cypress.Commands.add('chooseBranch', (label, code) => {
  cy.api('GET', '/api/branches').then(({ status, body }) => {
    expect(status).to.equal(200)
    const branch = body.find((item) => item.branchCode === code)
    expect(branch, `branch ${code}`).to.exist
    cy.get(`button.filter-dropdown__button[aria-label="${label}"]`).click()
    cy.get(`[role="listbox"][aria-label="${label}"] [role="option"]`).filter((_, option) => {
      const text = option.querySelector('span:last-child').textContent.trim()
      return text === branch.name || text === `${branch.name} (${code})`
    }).should('have.length', 1).click()
  })
})
