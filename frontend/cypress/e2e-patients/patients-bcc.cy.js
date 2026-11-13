/* oxlint-disable vitest/valid-expect, vitest/no-standalone-expect -- Cypress uses Chai assertions in hooks and queued helpers. */
// BCC model and case mapping: docs/testing/patients-bcc.md.
// Real browser -> Vite proxy -> cypress-v2 backend -> disposable PostgreSQL.
const API = 'http://127.0.0.1:18080'
const rows = () => cy.get('.directory tbody tr').not(':has(td[colspan])')
let manager
let user
let patients
let branches

function api(method, path, body, token) {
  return cy.then(() => cy.request({ method, url: `${API}${path}`, body,
    headers: { Authorization: `Bearer ${typeof token === 'function' ? token() : token ?? manager.token}` }, log: false }))
}

function login(username, password) {
  return cy.request({ method: 'POST', url: `${API}/api/auth/login`,
    body: { username, password }, log: false }).its('body')
}

function openDirectory(role = 'therapist', options = {}) {
  const authenticate = role === 'manager'
    ? cy.wrap(manager, { log: false }) : login('therapist', 'therapist123')
  authenticate.then((auth) => {
    user = auth
    cy.visit('/staff/patients', {
      onBeforeLoad(win) {
        win.localStorage.clear()
        win.localStorage.setItem('authToken', auth.token)
        win.localStorage.setItem('authUser', JSON.stringify(auth))
        if (role === 'manager') win.localStorage.setItem('managerSelectedBranch', '01')
        if (options.creation) win.localStorage.setItem('patientCreationHn', patients[0].hn)
      },
    })
  })
  cy.get('h1').should('have.text', 'ค้นหาผู้ป่วย')
}

// Independent fixture oracle: never copy the search response into the expected rows.
function expected({ query = '', branch = '', sort = 'nameAsc', page = 0 } = {}) {
  let selected = patients.filter((p) => (!branch || p.branchCode === branch)
    && (!query || [p.fullName, p.hn, p.phone].some((value) => value.includes(query))))
  selected = [...selected].sort((a, b) => sort === 'newest' ? b.id - a.id
    : new Intl.Collator('th').compare(a.fullName, b.fullName) * (sort === 'nameDesc' ? -1 : 1))
  return { total: selected.length, items: selected.slice(page * 20, page * 20 + 20) }
}

function assertDirectory(choice = {}) {
  const result = expected(choice)
  const { query = '', branch = '', sort = 'nameAsc', page = 0 } = choice
  // Also check persisted backend results under the current real user's permissions.
  api('GET', `/api/patients?${new URLSearchParams({ query, branchCode: branch,
    sort, page: String(page), size: '20' })}`, undefined, () => user.token).then(({ body }) => {
    expect(body.totalItems, 'backend total').to.equal(result.total)
    expect(body.items.map((p) => p.id), 'backend global order/page').to.deep.equal(result.items.map((p) => p.id))
  })
  cy.get('.directory').should('not.contain.text', 'กำลังโหลดข้อมูลผู้ป่วย')
  cy.get('.result-count').should('contain.text', `พบผู้ป่วยทั้งหมด ${result.total} ราย`)
  if (result.items.length === 0) {
    cy.get('.directory tbody').should('contain.text', 'ไม่พบผู้ป่วย')
    rows().should('have.length', 0)
  } else {
    rows().should('have.length', result.items.length)
    rows().each(($row, index) => {
      const patient = result.items[index]
      cy.wrap($row).find('td').eq(0).should('contain.text', patient.hn)
      cy.wrap($row).find('td').eq(1).should('have.text', patient.fullName)
      cy.wrap($row).find('td').eq(2).should('have.text', patient.phone)
      cy.wrap($row).find('td').eq(4).should('have.text', patient.branchName)
    })
  }
}

function search(text, normalized = text) {
  cy.intercept('GET', '**/api/patients?*', (request) => {
    if (new URL(request.url).searchParams.get('query') === normalized) request.alias = 'queryChange'
  }) // spy only; let the real backend respond
  cy.get('input[aria-label="ค้นหาผู้ป่วย"]').clear()
  if (text) cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type(text)
  cy.wait('@queryChange').its('request.url').should('satisfy', (url) =>
    new URL(url).searchParams.get('query') === normalized)
}

function pick(label, text) {
  cy.get(`button[aria-label="${label}"]`).click()
  cy.get(`[role="listbox"][aria-label="${label}"]`).contains('[role="option"]', text).click()
}

describe('Patients — BCC against real backend', () => {
  before(() => {
    expect(Cypress.config('specPattern'), 'use cypress.patients.config.ts to authorize resets')
      .to.equal('cypress/e2e-patients/patients-bcc.cy.js')
  })
  beforeEach(() => {
    login('manager', 'manager123').then((auth) => {
      manager = auth
      api('POST', '/__cypress/reset').its('status').should('equal', 200)
    })
    patients = []
    api('GET', '/api/branches').then(({ body }) => { branches = body })
    // 45 records: 3 pages overall, 2 pages per branch. Names deliberately disagree with ID order.
    cy.wrap(Array.from({ length: 45 }, (_, i) => i), { log: false }).each((i) => {
      api('POST', '/api/patients', {
        branchCode: i % 2 ? '02' : '01', customerType: 'Thai', title: 'นาย',
        firstName: `${['กานต์', 'จันทร์', 'ขจร'][i % 3]}${String.fromCharCode(0x0e01 + Math.floor(i / 3))}`,
        lastName: 'ทดสอบ', gender: 'Male', phone: `08${String(i).padStart(8, '0')}`,
        identityDocumentType: 'NATIONAL_ID', identityNumber: String(1100000001000 + i),
        birthDate: '1990-01-01', address: 'ข้อมูลทดสอบ BCC',
      }).then(({ status, body }) => {
        expect(status).to.equal(201)
        patients.push(body)
      })
    })
    // Fail before browser actions if Vite accidentally proxies to another database/backend.
    cy.then(() => cy.request({ url: `/api/patients/${patients[0].id}`,
      headers: { Authorization: `Bearer ${manager.token}` }, log: false }))
      .its('body').then((body) => {
        expect(body.hn).to.equal(patients[0].hn)
        expect(body.identityNumber).to.equal('1100000001000')
      })
  })

  it('BCC-00 base: therapist, empty query, all branches, name ascending, first page', () => {
    openDirectory()
    assertDirectory()
    rows().first().find('td').eq(3).should('have.text', '1 มกราคม 2533')
    cy.contains('button', 'ก่อนหน้า').should('be.disabled')
    cy.contains('button', 'ถัดไป').should('be.enabled')
  })

  const queries = [
    ['01', 'first name', () => patients[0].fullName.split(' ')[0]],
    ['02', 'last name', () => 'ทดสอบ'],
    ['03', 'HN', () => patients[0].hn],
    ['04', 'phone', () => patients[0].phone],
    ['05', 'no match', () => 'ไม่มีรายชื่อนี้'],
    ['06', 'whitespace only', () => '   ', () => ''],
    ['07', 'trimmed name', () => `  ${patients[0].fullName.split(' ')[0]}  `,
      () => patients[0].fullName.split(' ')[0]],
    ['08', 'formatted HN', () => `HN: ${patients[0].hn}`, () => patients[0].hn],
  ]
  queries.forEach(([id, label, input, normalized]) => {
    it(`BCC-${id} query: ${label}`, () => {
      openDirectory()
      assertDirectory()
      const query = normalized ? normalized() : input()
      search(input(), query)
      assertDirectory({ query })
    })
  })

  ;['01', '02'].forEach((branch, i) => {
    it(`BCC-${9 + i} branch: ${branch}`, () => {
      openDirectory()
      assertDirectory()
      pick('กรองตามสาขา', branches.find((b) => b.branchCode === branch).name)
      assertDirectory({ branch })
    })
  })

  ;[['nameDesc', 'ชื่อ ฮ-ก'], ['newest', 'เพิ่มล่าสุด']].forEach(([sort, label], i) => {
    it(`BCC-${11 + i} sort: ${sort}`, () => {
      openDirectory()
      assertDirectory()
      pick('เรียงลำดับผู้ป่วย', label)
      assertDirectory({ sort })
    })
  })

  ;[1, 2].forEach((page) => {
    it(`BCC-${12 + page} page: ${page === 1 ? 'middle' : 'last'}`, () => {
      openDirectory()
      assertDirectory()
      for (let i = 0; i < page; i++) {
        cy.contains('button', 'ถัดไป').click()
        cy.get('.patient-pagination').should('contain.text', `หน้า ${i + 2} จาก 3`)
        assertDirectory({ page: i + 1 })
      }
      cy.contains('button', 'ก่อนหน้า').should('be.enabled')
      cy.contains('button', 'ถัดไป').should(page === 2 ? 'be.disabled' : 'be.enabled')
      cy.contains('button', 'ก่อนหน้า').click()
      assertDirectory({ page: page - 1 })
    })
  })

  it('BCC-15 role: manager uses header branch selection (constraint: no all-branch option)', () => {
    openDirectory('manager')
    cy.get('button[aria-label="กรองตามสาขา"]').should('not.exist')
    assertDirectory({ branch: '01' })
    pick('เลือกสาขา', branches.find((b) => b.branchCode === '02').name.replace(/^สาขา\s*/, ''))
    assertDirectory({ branch: '02' })
  })

  it('SUP-01 clearing query restores all records', () => {
    openDirectory()
    assertDirectory()
    search(patients[0].hn)
    assertDirectory({ query: patients[0].hn })
    search('')
    assertDirectory()
  })

  it('SUP-02 query, branch and sort changes each reset pagination', () => {
    openDirectory()
    assertDirectory()
    cy.contains('button', 'ถัดไป').click()
    assertDirectory({ page: 1 })
    search('ทดสอบ')
    assertDirectory({ query: 'ทดสอบ' })
    cy.contains('button', 'ก่อนหน้า').should('be.disabled')
    cy.contains('button', 'ถัดไป').click()
    assertDirectory({ query: 'ทดสอบ', page: 1 })
    pick('กรองตามสาขา', branches.find((b) => b.branchCode === '01').name)
    assertDirectory({ query: 'ทดสอบ', branch: '01' })
    cy.contains('button', 'ก่อนหน้า').should('be.disabled')
    cy.contains('button', 'ถัดไป').click()
    assertDirectory({ query: 'ทดสอบ', branch: '01', page: 1 })
    pick('เรียงลำดับผู้ป่วย', 'ชื่อ ฮ-ก')
    assertDirectory({ query: 'ทดสอบ', branch: '01', sort: 'nameDesc' })
    cy.contains('button', 'ก่อนหน้า').should('be.disabled')
  })

  it('SUP-03 opens the selected patient profile with persisted data', () => {
    openDirectory()
    assertDirectory()
    search(patients[0].hn)
    assertDirectory({ query: patients[0].hn })
    rows().contains('a', 'ดูข้อมูล').click()
    cy.location('pathname').should('equal', `/staff/patients/${patients[0].id}`)
    cy.get('main').should('contain.text', patients[0].hn).and('contain.text', patients[0].phone)
  })

  it('SUP-04 registration link opens the real registration form', () => {
    openDirectory()
    assertDirectory()
    cy.contains('a', '+ ลงทะเบียนผู้ป่วยใหม่').click()
    cy.location('pathname').should('equal', '/staff/patients/new')
    cy.get('input[placeholder="ชื่อ"]').should('be.visible')
    cy.get('button[type="submit"]').should('contain.text', 'บันทึก')
  })

  it('SUP-05 unauthenticated users return to login', () => {
    cy.visit('/staff/patients', { onBeforeLoad(win) { win.localStorage.clear() } })
    cy.location('pathname').should('equal', '/')
    cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
  })

  it('SUP-06 creation notice is shown once and consumed', () => {
    openDirectory('therapist', { creation: true })
    cy.get('[role="status"]').should('contain.text', 'ลงทะเบียนผู้ป่วยแล้ว')
    cy.window().its('localStorage').invoke('getItem', 'patientCreationHn').should('be.null')
    cy.get('[role="status"]').should('not.exist')
    cy.reload()
    cy.get('h1').should('have.text', 'ค้นหาผู้ป่วย')
    cy.get('[role="status"]').should('not.exist')
  })

  it('SUP-07 loading indicator persists until the real backend response arrives', () => {
    openDirectory()
    assertDirectory()
    cy.intercept('GET', '**/api/patients?*', (request) => {
      if (new URL(request.url).searchParams.get('query') === patients[0].hn) {
        request.continue((response) => response.setDelay(1000))
      }
    })
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type(patients[0].hn)
    cy.get('.directory').should('contain.text', 'กำลังโหลดข้อมูลผู้ป่วย')
    assertDirectory({ query: patients[0].hn })
  })

  it('SUP-08 search error is visible and a subsequent real search recovers', () => {
    openDirectory()
    assertDirectory()
    cy.intercept({ method: 'GET', pathname: '/api/patients', query: { query: 'ทดสอบ' } },
      { statusCode: 503, body: { message: 'ระบบค้นหาขัดข้องชั่วคราว' } }).as('unavailable')
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type('ทดสอบ')
    cy.wait('@unavailable')
    cy.get('.directory').should('contain.text', 'ระบบค้นหาขัดข้องชั่วคราว')
    search(patients[0].hn)
    assertDirectory({ query: patients[0].hn })
    cy.get('.directory').should('not.contain.text', 'ระบบค้นหาขัดข้องชั่วคราว')
  })

  it('SUP-09 invalid token rejected by real backend clears session and returns to login', () => {
    openDirectory()
    assertDirectory()
    cy.window().then((win) => win.localStorage.setItem('authToken', 'invalid-token'))
    cy.reload()
    cy.location('pathname').should('equal', '/')
    cy.get('h1').should('have.text', 'เข้าสู่ระบบ')
    cy.window().then((win) => {
      expect(win.localStorage.getItem('authToken')).to.equal(null)
      expect(win.localStorage.getItem('authUser')).to.equal(null)
    })
  })

  it('SUP-10 registers a foreign patient through UI and verifies persisted backend/profile data', () => {
    openDirectory()
    assertDirectory()
    cy.contains('a', '+ ลงทะเบียนผู้ป่วยใหม่').click()
    cy.get('input[type="radio"][value="ต่างชาติ"]').check()
    cy.get('input[placeholder="ชื่อ"]').type('Alice')
    cy.get('input[placeholder="นามสกุล"]').type('Coverage')
    cy.get('input[placeholder="กรอกเลขพาสปอร์ต"]').type('BCCPASSPORT01')
    cy.get('button[aria-label="สาขาที่ลงทะเบียน"]').should('not.contain.text', 'เลือกสาขา')
    cy.intercept('POST', '**/api/patients').as('registration') // real POST
    cy.get('button[type="submit"]').click()
    cy.get('[aria-labelledby="registration-confirmation-title"]').contains('button', /^ใช่$/).click()
    cy.wait('@registration').then(({ response }) => {
      expect(response.statusCode).to.equal(201)
      const created = response.body
      expect(created.hn).to.match(/^\d{2}01\d{2}\d{4}$/)
      cy.get('[aria-labelledby="registration-success-title"]').should('contain.text', created.hn)
      api('GET', `/api/patients/${created.id}`).then(({ body }) => {
        expect(body.fullName).to.equal('Alice Coverage')
        expect(body.identityNumber).to.equal('BCCPASSPORT01')
        expect(body.customerTypeCode).to.equal('Foreigner')
        expect(body.branch.code).to.equal('01')
      })
      cy.contains('button', 'เปิดข้อมูลผู้ป่วย').click()
      cy.location('pathname').should('equal', `/staff/patients/${created.id}`)
      cy.get('main').should('contain.text', 'Alice').and('contain.text', 'BCCPASSPORT01')
    })
  })
})
