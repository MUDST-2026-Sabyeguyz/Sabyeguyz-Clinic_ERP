/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
function fillRegistrationForm({ first = 'สมชาย', last = 'ใจดี', nick = 'ชาย', birth = '1990-01-15', email = 'somchai@gmail.com',
  phone = '0811111111', identity = '1103700000011' } = {}) {
  cy.get('input[placeholder="ชื่อ"]').type(first)
  cy.get('input[placeholder="นามสกุล"]').type(last)
  cy.get('input[placeholder="ชื่อเล่น"]').type(nick)
  cy.contains('label', 'วันเกิด').pickDate(birth)
  cy.contains('label', 'อีเมล').find('input').type(email)
  cy.get('input[placeholder="xxx-xxx-xxxx"]').type(phone)
  cy.contains('label', 'ที่อยู่').find('textarea').type('99 ถนนทดสอบ กรุงเทพฯ')
  cy.get('button[aria-label="ประเภทเอกสารยืนยันตัวตน"]').should('contain.text', 'บัตรประชาชน')
  cy.get('input[placeholder="x-xxxx-xxxxx-xx-x"]').type(identity)
}

describe('patient registration', () => {
  it('registers a foreigner without phone, birthday, passport or address', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.get('input[type=radio][value=ต่างชาติ]').check()
    cy.get('input[placeholder="ชื่อ"]').type('Alex')
    cy.get('input[placeholder="นามสกุล"]').type('NoDocuments')
    cy.contains('button', 'บันทึก').click()
    cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
    cy.contains('.registration-success-modal button', 'เปิดข้อมูลผู้ป่วย').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.api('GET', '/api/patients').then(({ body }) => {
      expect(body.totalItems).to.equal(1)
      cy.api('GET', `/api/patients/${body.items[0].id}`).then(({ body: profile }) => {
        expect(profile.customerTypeCode).to.equal('Foreigner')
        expect(profile.phone).to.equal(null)
        expect(profile.birthDate).to.equal(null)
        expect(profile.identityNumber).to.be.oneOf([null, ''])
      })
    })
  })
  it('persists middle name, bilingual child title and unspecified gender without requiring an address', () => {
    cy.visitAs('manager', '/staff/patients/new')
    fillRegistrationForm()
    cy.get('input[placeholder="ชื่อกลาง"]').type('อนันต์')
    cy.chooseDropdown('คำนำหน้า', 'เด็กชาย / Boy')
    cy.chooseDropdown('เพศ', 'ไม่ระบุ')
    cy.contains('label', 'ที่อยู่').find('textarea').clear()
    cy.intercept('POST', '**/api/patients').as('registration')
    cy.contains('button', 'บันทึก').click()
    cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
    cy.wait('@registration').then(({ request, response }) => {
      expect(request.body.middleName).to.equal('อนันต์')
      expect(request.body.firstName).to.equal('สมชาย')
      expect(response.statusCode).to.equal(201)
      cy.api('GET', `/api/patients/${response.body.id}`).then(({ body }) => {
        expect(body.middleName).to.equal('อนันต์')
        expect(body.fullName).to.equal('สมชาย อนันต์ ใจดี')
        expect(body.title).to.equal('เด็กชาย')
        expect(body.gender).to.equal('NotSpecified')
        expect(body.address).to.equal('ไม่ระบุ')
      })
    })
    cy.contains('.registration-success-modal button', 'เปิดข้อมูลผู้ป่วย').click()
    cy.get('.patient-profile-summary').should('contain.text', 'สมชาย อนันต์ ใจดี')
  })

  it('uses exact identity lookup and never treats a matching phone as an existing national ID', () => {
    cy.registerPatient({ firstName: 'บุคคลอื่น', identityNumber: '1100000000939', phone: '1103700000011' })
    cy.visitAs('manager', '/staff/patients/new')
    fillRegistrationForm()
    cy.intercept('GET', '**/api/patients/identity-lookup?*').as('identityLookup')
    cy.contains('button', 'บันทึก').click()
    cy.wait('@identityLookup').then(({ response }) => {
      expect(response.statusCode).to.equal(200)
      expect(response.body).to.deep.equal({ exists: false, patient: null })
    })
    cy.get('.confirmation-modal').should('be.visible')
  })

  it('registers a Thai patient through the form and shows them in the list', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.contains('h1', 'ลงทะเบียนผู้ป่วยใหม่')
    fillRegistrationForm()
    cy.contains('button', 'บันทึก').click()
    cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
    cy.contains('.registration-success-modal button', 'เปิดข้อมูลผู้ป่วย').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.visit('/staff/patients')
    cy.contains('td', 'สมชาย ใจดี').should('be.visible')
    cy.contains('td', '0811111111').should('be.visible')
    cy.get('td.patient-id b').first().invoke('text').should('match', /^\d{10}$/)
  })

  it('shows what is missing when the empty form is submitted and does not create anything', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.contains('button', 'บันทึก').click()
    cy.contains('small.required-error', 'กรุณากรอกข้อมูล').should('be.visible')
    cy.location('pathname').should('eq', '/staff/patients/new')
    cy.api('GET', '/api/patients').its('body.totalItems').should('equal', 0)
  })

  it('accepts Thai letters only in the name fields and digits only in the phone', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.get('input[placeholder="ชื่อ"]').type('abc123สมชาย').should('have.value', 'สมชาย')
    cy.get('input[placeholder="xxx-xxx-xxxx"]').type('08-1x234').should('have.value', '081234')
  })

  it('asks for a 13-digit national id', () => {
    cy.visitAs('manager', '/staff/patients/new')
    fillRegistrationForm({ identity: '12345' })
    cy.contains('button', 'บันทึก').click()
    cy.contains('small.required-error', 'กรุณากรอกตัวเลข 13 หลัก').should('be.visible')
    cy.location('pathname').should('eq', '/staff/patients/new')
  })

  it('rejects an e-mail that is not a gmail address', () => {
    cy.visitAs('manager', '/staff/patients/new')
    fillRegistrationForm({ email: 'somchai@example.com' })
    cy.contains('button', 'บันทึก').click()
    cy.contains('small.required-error', 'รูปแบบอีเมลไม่ถูกต้อง').should('be.visible')
  })

  it('switches a foreign patient to a passport', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.get('input[type=radio][value=ต่างชาติ]').check()
    cy.get('button[aria-label=ประเภทเอกสารยืนยันตัวตน]').should('be.disabled').and('contain.text', 'หนังสือเดินทาง')
    cy.get('input[placeholder="กรอกเลขหนังสือเดินทาง 10 หลัก"]').should('be.visible')
  })

  it('registers a foreign patient with a passport', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.get('input[placeholder="ชื่อ"]').type('จอห์น')
    cy.get('input[placeholder="นามสกุล"]').type('สมิธ')
    cy.get('input[placeholder="ชื่อเล่น"]').type('จอห์น')
    cy.contains('label', 'วันเกิด').pickDate('1985-02-03')
    cy.get('input[type=radio][value=ต่างชาติ]').check()
    cy.contains('label', 'อีเมล').find('input').type('john@gmail.com')
    cy.get('input[placeholder="xxx-xxx-xxxx"]').type('0899999999')
    cy.contains('label', 'ที่อยู่').find('textarea').type('London')
    cy.get('input[placeholder="กรอกเลขพาสปอร์ต"]').type('1234567890')
    cy.contains('button', 'บันทึก').click()
    cy.get('.confirmation-modal').contains('button', /^ใช่$/).click()
    cy.contains('.registration-success-modal button', 'เปิดข้อมูลผู้ป่วย').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.api('GET', '/api/patients').its('body.items.0').then((patient) => {
      cy.api('GET', `/api/patients/${patient.id}`).its('body.customerTypeCode').should('equal', 'Foreigner')
    })
  })

  it('keeps title independent from gender and supports unspecified gender', () => {
    cy.visitAs('manager', '/staff/patients/new')
    cy.chooseDropdown('คำนำหน้า', 'เด็กหญิง / Girl')
    cy.chooseDropdown('เพศ', 'ไม่ระบุ')
    cy.get('button[aria-label=เพศ]').should('contain.text', 'ไม่ระบุ')
    cy.get('button[aria-label=คำนำหน้า]').should('contain.text', 'เด็กหญิง / Girl')
  })
})

describe('patient search', () => {
  beforeEach(() => {
    cy.registerPatient({ firstName: 'อรุณ', lastName: 'แสงทอง', phone: '0899990000' })
    cy.registerPatient({ firstName: 'บุญเรือง', lastName: 'ศรีสุข', phone: '0822222222', identityNumber: '1100000000102' })
    cy.registerPatient({ firstName: 'จันทร์', lastName: 'เพ็ญ', phone: '0833333333', identityNumber: '1100000000103', branchCode: '02' })
  })

  it('lists every patient with HN, name, phone and branch', () => {
    cy.visitAs('therapist', '/staff/patients')
    cy.contains('พบผู้ป่วยทั้งหมด 3 ราย').should('be.visible')
    cy.get('tbody tr').should('have.length', 3)
    cy.contains('td', 'อรุณ แสงทอง').parent().should('contain.text', '0899990000')
  })

  it('searches by first name, last name, phone and HN', () => {
    cy.api('GET', `/api/patients?query=${encodeURIComponent('อรุณ')}`).its('body.items.0.hn').then((hn) => {
      cy.visitAs('therapist', '/staff/patients')
      for (const term of ['อรุณ', 'แสงทอง', '0899990000', hn]) {
        cy.get('input[aria-label="ค้นหาผู้ป่วย"]').clear().type(term)
        cy.contains('พบผู้ป่วยทั้งหมด 1 ราย').should('be.visible')
        cy.contains('td', 'อรุณ แสงทอง').should('be.visible')
      }
    })
  })

  it('says so when nobody matches', () => {
    cy.visitAs('therapist', '/staff/patients')
    cy.get('input[aria-label="ค้นหาผู้ป่วย"]').type('ไม่มีใครชื่อนี้')
    cy.contains('td', 'ไม่พบผู้ป่วย').should('be.visible')
  })

  it('filters by branch', () => {
    cy.visitAs('manager', '/staff/patients')
    cy.chooseBranch('กรองตามสาขา', '01')
    cy.contains('พบผู้ป่วยทั้งหมด 2 ราย').should('be.visible')
    cy.chooseBranch('กรองตามสาขา', '02')
    cy.contains('พบผู้ป่วยทั้งหมด 1 ราย').should('be.visible')
    cy.contains('td', 'จันทร์ เพ็ญ').should('be.visible')
  })

  it('sorts by newest registration first', () => {
    cy.visitAs('manager', '/staff/patients')
    cy.chooseDropdown('เรียงลำดับผู้ป่วย', 'เพิ่มล่าสุด')
    cy.get('tbody tr').first().should('contain.text', 'จันทร์ เพ็ญ')
  })

  it('opens a patient profile from the list', () => {
    cy.visitAs('therapist', '/staff/patients')
    cy.contains('td', 'อรุณ แสงทอง').parent().contains('a', 'ดูข้อมูล').click()
    cy.location('pathname').should('match', /^\/staff\/patients\/\d+$/)
    cy.get('.patient-profile-summary').should('contain.text', 'อรุณ แสงทอง')
  })
})

describe('patient list paging', () => {
  it('pages through more than one page of patients', () => {
    for (let n = 1; n <= 21; n += 1) {
      cy.registerPatient({ firstName: `ผู้ป่วย${n}`.replace(/\d+/g, ''), lastName: 'ทดสอบ', phone: `08${String(10000000 + n)}`, identityNumber: `11000000${String(1000 + n).padStart(5, '0')}` })
    }
    cy.visitAs('manager', '/staff/patients')
    cy.contains('พบผู้ป่วยทั้งหมด 21 ราย').should('be.visible')
    cy.get('tbody tr').should('have.length', 20)
    cy.contains('หน้า 1 จาก 2').should('be.visible')
    cy.contains('button', 'ถัดไป →').click()
    cy.contains('หน้า 2 จาก 2').should('be.visible')
    cy.get('tbody tr').should('have.length', 1)
    cy.contains('button', '← ก่อนหน้า').click()
    cy.contains('หน้า 1 จาก 2').should('be.visible')
  })
})

describe('patient profile', () => {
  it('edits and clears middleName without splitting compound first and last names', () => {
    cy.registerPatient({ customerType: 'Foreigner', firstName: 'John Paul', lastName: 'Smith Jones',
      middleName: 'Existing', title: 'Mrs.', nationality: 'ต่างชาติ', identityDocumentType: 'PASSPORT', identityNumber: '1234567890' })
      .then((patient) => {
        cy.visitAs('manager', `/staff/patients/${patient.id}`)
        cy.contains('button', 'แก้ไขข้อมูล').click()
        const field = (pattern) => cy.get('.patient-edit label').filter((_, el) => pattern.test(el.textContent.trim())).find('input')
        field(/^ชื่อ\s*\*/).should('have.value', 'John Paul')
        field(/^นามสกุล/).should('have.value', 'Smith Jones')
        field(/^ชื่อกลาง/).should('have.value', 'Existing').clear().type('New Middle')
        cy.chooseDropdown('เพศ', 'ไม่ระบุ')
        cy.get('button[aria-label=คำนำหน้า]').should('contain.text', 'นาง / Mrs.')
        cy.contains('.patient-edit button', 'บันทึก').click()
        cy.get('.patient-confirmation').contains('button', /^ใช่$/).click()
        cy.get('.patient-profile-summary').should('contain.text', 'John Paul New Middle Smith Jones')
        cy.contains('button', 'แก้ไขข้อมูล').click()
        field(/^ชื่อกลาง/).clear()
        cy.contains('.patient-edit button', 'บันทึก').click()
        cy.get('.patient-confirmation').contains('button', /^ใช่$/).click()
        cy.get('.patient-profile-summary').should('contain.text', 'John Paul Smith Jones')
        cy.api('GET', `/api/patients/${patient.id}`).then(({ body }) => {
          expect(body.firstName).to.equal('John Paul')
          expect(body.lastName).to.equal('Smith Jones')
          expect(body.middleName).to.equal(null)
          expect(body.title).to.equal('นาง')
          expect(body.gender).to.equal('NotSpecified')
        })
      })
  })
  it('shows the saved details of a patient', () => {
    cy.registerPatient({ nickname: 'ชาย', bloodGroup: 'O', identityNumber: '1100000000101' }).then((patient) => {
      cy.visitAs('therapist', `/staff/patients/${patient.id}`)
      cy.get('.patient-profile-summary').should('contain.text', 'สมชาย ใจดี')
      cy.contains('HN ' + patient.hn).should('be.visible')
      cy.contains('dt', 'เบอร์โทรศัพท์').parent().should('contain.text', '0811111111')
      cy.contains('dt', 'เลขบัตรประชาชน').parent().should('contain.text', '1100000000101')
      cy.contains('dt', 'ที่อยู่').parent().should('contain.text', 'กรุงเทพฯ')
    })
  })

  it('edits the name, phone and address and shows the saved values', () => {
    cy.registerPatient().then((patient) => {
      cy.visitAs('manager', `/staff/patients/${patient.id}`)
      cy.contains('button', 'แก้ไขข้อมูล').click()
      const field = (pattern) => cy.get('.patient-edit label').filter((_, el) => pattern.test(el.textContent.trim()))
      field(/^ชื่อ\s*\*/).find('input').clear().type('สมหญิง')
      field(/^เบอร์โทร/).find('input').clear().type('0877777777')
      field(/^ที่อยู่/).find('textarea').clear().type('ที่อยู่ใหม่ เชียงใหม่')
      cy.contains('.patient-edit button', 'บันทึก').click()
      cy.get('.patient-confirmation').contains('button', /^ใช่$/).click()
      cy.contains('แก้ไขข้อมูลสำเร็จ').should('be.visible')
      cy.get('.patient-profile-summary').should('contain.text', 'สมหญิง ใจดี')
      cy.contains('dt', 'เบอร์โทรศัพท์').parent().should('contain.text', '0877777777')
      cy.contains('dt', 'ที่อยู่').parent().should('contain.text', 'ที่อยู่ใหม่ เชียงใหม่')
      cy.api('GET', `/api/patients/${patient.id}`).its('body.fullName').should('equal', 'สมหญิง ใจดี')
    })
  })

  it('leaves the patient unchanged when the edit is cancelled', () => {
    cy.registerPatient().then((patient) => {
      cy.visitAs('manager', `/staff/patients/${patient.id}`)
      cy.contains('button', 'แก้ไขข้อมูล').click()
      cy.get('.patient-edit label').filter((_, el) => /^ชื่อ\s*\*/.test(el.textContent.trim())).find('input').clear().type('ไม่บันทึก')
      cy.contains('button', 'ยกเลิก').click()
      cy.get('.patient-profile-summary').should('contain.text', 'สมชาย ใจดี')
    })
  })

  it('shows an error for an unknown patient id', () => {
    cy.visitAs('manager', '/staff/patients/999999')
    cy.get('[role="alert"]').should('be.visible')
  })
})
