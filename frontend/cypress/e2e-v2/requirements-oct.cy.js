/* oxlint-disable vitest/valid-expect -- Cypress assertions use Chai, not Vitest. */
// October requirements: branch and service settings, free times, course extension, transactions of another branch,
// Google Calendar for a therapist.
const modal = () => cy.get('.drawer')
const sectionButton = (name) => cy.contains('.settings-nav button', name)
const openEditor = (label) => cy.contains('button.settings-add', label).click()
const save = () => {
  cy.get('.drawer').contains('button', 'บันทึก').click()
  cy.noAlert()
}
const branch01 = () => cy.api('GET', '/api/branches').then(({ body }) => body.find((row) => row.branchCode === '01'))

describe('branch settings', () => {
  it('saves the email, licence, booking window and default appointment length of a branch', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('tbody tr td', /^01$/).closest('tr').contains('button', 'แก้ไข').click()
    modal().contains('label', 'อีเมลสาขา').find('input').clear().type('rama9@clinic.test')
    modal().contains('label', 'เลขใบอนุญาต').find('input').clear().type('PT-1234')
    modal().contains('label', 'เปิดรับนัดตั้งแต่').find('input').type('09:00')
    modal().contains('label', 'ถึงเวลา').find('input').type('18:00')
    modal().contains('label', 'ระยะเวลานัดเริ่มต้น').find('input').clear().type('60')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    branch01().should((branch) => {
      expect(branch).to.include({
        email: 'rama9@clinic.test', licenseNo: 'PT-1234', bookingOpenTime: '09:00', bookingCloseTime: '18:00',
        defaultAppointmentMinutes: 60,
      })
    })
  })

  it('refuses a booking window with only one end', () => {
    cy.visitAs('manager', '/staff/settings')
    cy.contains('tbody tr td', /^01$/).closest('tr').contains('button', 'แก้ไข').click()
    modal().contains('label', 'เปิดรับนัดตั้งแต่').find('input').type('09:00')
    modal().contains('button', 'บันทึก').click()
    cy.get('.drawer').should('contain.text', 'ช่วงเปิดรับนัด')
    branch01().should((branch) => expect(branch.bookingOpenTime).to.equal(null))
  })
})

describe('service and course settings', () => {
  it('keeps the optional appointment length of a single visit, which has no sessions or expiry', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('บริการและคอร์ส').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อคอร์ส').find('input').type('นวดสั้น')
    cy.chooseDropdown('ประเภท', 'Single Visit')
    modal().contains('label', 'ราคา').find('input').type('700')
    modal().should('not.contain.text', 'อายุคอร์ส').and('not.contain.text', 'จำนวนครั้งรวม')
    modal().contains('label', 'ระยะเวลานัดเริ่มต้น').find('input').type('45')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.api('GET', '/api/services').its('body').should((rows) => {
      expect(rows.find((row) => row.name === 'นวดสั้น').defaultDurationMinutes).to.equal(45)
    })
  })

  it('saves a course with its total sessions and a validity in months', () => {
    cy.visitAs('manager', '/staff/settings')
    sectionButton('บริการและคอร์ส').click()
    openEditor('+ เพิ่มรายการ')
    modal().contains('label', 'ชื่อคอร์ส').find('input').type('คอร์สครึ่งปี')
    cy.chooseDropdown('ประเภท', 'Course')
    modal().contains('label', 'ราคา').find('input').type('6000')
    modal().contains('label', 'จำนวนครั้งรวม').find('input').clear().type('6')
    modal().contains('label', 'อายุคอร์ส').find('input').clear().type('6')
    modal().find('select[aria-label="หน่วยอายุคอร์ส"]').select('Month')
    save()
    cy.contains('บันทึกเรียบร้อยแล้ว').should('be.visible')
    cy.api('GET', '/api/courses').its('body').should((rows) => {
      expect(rows.find((row) => row.name === 'คอร์สครึ่งปี')).to.include({ sessions: 6, validityValue: 6, validityUnit: 'Month' })
    })
  })
})

describe('appointments: free times and no service yet', () => {
  it('offers only the times the therapist is free, inside the branch booking window', function () {
    branch01().then((branch) => cy.api('PUT', `/api/branches/${branch.id}`, {
      branchCode: '01', name: branch.name, address: branch.address, phone: branch.phone,
      bookingOpenTime: '09:00', bookingCloseTime: '12:00', defaultAppointmentMinutes: 60,
    }).its('status').should('equal', 200))
    cy.registerPatient().then((patient) => cy.api('POST', '/api/appointments', {
      branchCode: '01', patientId: patient.id, therapistId: this.fixtures.therapistId,
      startAt: `${this.fixtures.tomorrow}T10:00:00+07:00`,
    }).then(({ status, body: booked }) => {
      expect(status).to.equal(201)
      cy.visitAs('manager', '/staff/appointments')
      cy.contains('button', 'สร้างนัดหมาย').first().click()
      cy.contains('label', 'วันที่').pickDate(this.fixtures.tomorrow)
      cy.chooseBranch('เลือกสาขา', '01')
      cy.chooseDropdown('เลือกนักกายภาพ', booked.therapist.fullName)
      cy.contains('label', 'ประเภทบริการ (ไม่บังคับ)').should('be.visible')
      cy.get('button[aria-label="เลือกเวลา"]').click()
      cy.get('[role="listbox"][aria-label="เลือกเวลา"] [role="option"]').then(($options) => {
        // the chosen time carries a check mark and the first option is the placeholder
        const times = [...$options].map((option) => option.textContent.replace('✓', '').trim())
        expect(times).to.include('09:00').and.to.include('11:00')
        expect(times).to.not.include('09:30').and.to.not.include('10:00').and.to.not.include('10:30')
        expect(times).to.not.include('13:00')
      })
    }))
  })

  it('does not ask for a first and last name of a new patient, only the nickname and the phone', () => {
    cy.visitAs('manager', '/staff/appointments')
    cy.contains('button', 'สร้างนัดหมาย').click()
    cy.get('input[type=radio][value=NEW]').check()
    cy.contains('label', 'ชื่อเล่น').should('contain.text', '*')
    cy.contains('label', 'เบอร์โทร').should('contain.text', '*')
    cy.contains('label', 'ชื่อจริง').should('not.exist')
    cy.contains('label', 'นามสกุล').should('not.exist')
  })
})

describe('course extension on the course page', () => {
  it('lets the owner ask for an extension, shows it as waiting and lets the Manager approve it', function () {
    cy.registerPatient().then((patient) => cy.api('POST', '/api/course-purchases', {
      patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', caseOwnerStaffId: this.fixtures.therapistId,
    }).then(({ status, body: course }) => {
      expect(status).to.equal(201)
      cy.visitAs('manager', `/staff/courses?patientId=${patient.id}`)
      cy.get('.course-extension-panel').should('contain.text', 'ยังไม่มีประวัติการต่ออายุ')
      cy.get('.course-extension-panel').contains('button', 'ขอต่ออายุคอร์ส').click()
      cy.get('.course-extension-form input[type=number]').clear().type('2')
      cy.get('select[aria-label="หน่วยการต่ออายุ"]').select('Month')
      cy.get('.course-extension-form').contains('button', 'ส่งคำขอ').click()
      cy.contains('.course-extension-ok', 'ส่งคำขอต่ออายุแล้ว').should('be.visible')
      cy.get('.course-extension-pending').should('contain.text', 'ต่ออายุ 2 เดือน')
      cy.api('GET', `/api/patients/${patient.id}/courses`).then(({ body: before }) => {
        const expiryBefore = before[0].expiresAt
        // the approve button comes before "ไม่อนุมัติ" in the panel
        cy.contains('.course-extension-pending button', 'อนุมัติ').click()
        cy.contains('.course-extension-ok', 'อนุมัติการต่ออายุแล้ว').should('be.visible')
        cy.get('.course-extension-history').should('contain.text', 'อนุมัติแล้ว')
        cy.api('GET', `/api/patients/${patient.id}/courses`).its('body.0.expiresAt').should('not.equal', expiryBefore)
      })
      cy.api('GET', `/api/course-extensions?courseId=${course.id}`).its('body.0.status').should('equal', 'Approved')
    }))
  })
})

describe('transactions of another branch', () => {
  it('lets a therapist read a transaction of the other branch but not cancel it', function () {
    cy.registerPatient({ branchCode: '02' }).then((patient) => cy.api('POST', '/api/payments', {
      patientId: patient.id, branchCode: '02', salespersonStaffId: this.fixtures.therapist2Id,
      treatingStaffId: this.fixtures.therapist2Id,
      items: [{ type: 'SERVICE', referenceId: this.fixtures.singleVisitServiceId, quantity: 1 }],
      payments: [{ paymentMethodCode: 'Cash', amount: 800 }],
    }).its('status').should('equal', 201))
    cy.visitAs('therapist', '/staff/transactions')
    cy.get('tbody tr').should('have.length', 1)
    cy.get('tbody tr').contains('button', 'ดูรายละเอียด').click()
    cy.get('.transaction-readonly-note').should('contain.text', 'ดูรายละเอียดได้อย่างเดียว')
    cy.contains('button', 'ดำเนินการยกเลิก').should('not.exist')
  })

  it('shows sales and course usage in one list with a type filter', function () {
    cy.registerPatient().then((patient) => cy.api('POST', '/api/course-purchases', {
      patientId: patient.id, courseId: this.fixtures.courseId, branchCode: '01', caseOwnerStaffId: this.fixtures.therapistId,
    }).then(({ body: course }) => cy.api('POST', '/api/course-usages', { coursePurchaseId: course.id })))
    cy.visitAs('manager', '/staff/transactions')
    cy.get('.transaction-tabs button').should('have.length', 2)
    cy.get('tbody tr').should('have.length', 2)
    cy.chooseDropdown('ประเภท', 'ขายคอร์ส')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'ขายคอร์ส')
    cy.chooseDropdown('ประเภท', 'ใช้สิทธิ์คอร์ส')
    cy.get('tbody tr').should('have.length', 1).and('contain.text', 'ใช้สิทธิ์คอร์ส 1 ครั้ง')
  })
})

describe('Google Calendar for a therapist', () => {
  it('shows the state of the own branch and no way to set it up', () => {
    cy.visitAs('therapist', '/staff/appointments')
    cy.contains('button', 'ดูปฏิทินนัดหมาย').click()
    cy.contains('button', 'สถานะ Google Calendar').click()
    cy.get('[data-cy="google-sync-info"]').should('contain.text', 'ผู้จัดการเป็นผู้เชื่อมบัญชี Google กลาง')
    cy.get('[data-cy="google-sync-now"]').should('not.exist')
    cy.contains('a', 'ตั้งค่าปฏิทิน').should('not.exist')
  })
})
