/* oxlint-disable vitest/valid-expect -- Cypress uses Chai assertions, not Vitest matchers. */
import { api, visit, patient, fixtures, book } from '../support-therapist/helpers'

// Calendar and list status controls expose a subset of the backend lifecycle.
// API-only lifecycle cases below are deliberately labelled; no core API is stubbed.
const row = (name) => cy.contains('.appointment-table-scroll tbody tr', name)
const dropdown = (label, text) => {
  cy.get(`button[aria-label="${label}"]`).click()
  cy.contains(`[role="listbox"][aria-label="${label}"] [role="option"]`, text).click()
}
const chooseFixtureTherapist = () => api('GET', '/api/catalog/therapists').then((r) => {
  expect(r.status).to.eq(200)
  const therapist = r.body.find((item) => item.id === fixtures.therapistId)
  expect(therapist, 'seeded appointment therapist').not.to.eq(undefined)
  dropdown('เลือกนักกายภาพ', therapist.fullName)
})
const chooseDate = (selector, value) => {
  const [year, month, day] = value.split('-').map(Number)
  cy.get(selector).find('.date-trigger').click()
  cy.get('.thai-date-picker [role="dialog"]').within(() => {
    cy.get('select[aria-label="เลือกปี"]').select(String(year))
    cy.get('select[aria-label="เลือกเดือน"]').select(String(month - 1))
    cy.contains('.days button', new RegExp(`^${day}$`)).click()
  })
}
const chooseTime = (time) => {
  cy.contains('button', 'ดูตารางเวลาว่าง').click()
  cy.get('.appointment-time-drawer').within(() => {
    cy.contains('.appointment-time-slots button', time).click()
    cy.contains('button', 'ยืนยันเวลา').click()
  })
}
const choosePatient = (p) => {
  cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type(p.hn)
  cy.contains('.patient-search-results button', p.hn).trigger('mousedown')
  cy.get('.appointment-phone-field input').should('have.value', p.phone)
}
const confirmSave = (editing = false) => {
  cy.contains('.appointment-save button.primary', editing ? /^บันทึก$/ : 'บันทึกนัดหมาย').click()
  cy.contains('.appointment-confirm-modal button', /^ใช่$/).click()
  cy.contains('.appointment-success-modal', editing ? 'เลื่อนนัดหมายสำเร็จ' : 'สร้างนัดหมายสำเร็จ').should('be.visible')
  cy.contains('.appointment-success-modal button', 'ตกลง').click()
}
const persisted = (id, expected) => api('GET', `/api/appointments/${id}`).then((r) => {
  expect(r.status).to.eq(200)
  expect(r.body).to.include(expected)
  return r.body
})
const dayTomorrow = () => {
  cy.contains('.calendar-view-toggle button', 'รายวัน').click()
  cy.get('button[aria-label="ถัดไป"]').click()
  cy.get('.schedule-column').should('exist')
}

describe('Therapist appointments — real backend', () => {
  it('creates a current-patient appointment from list, searches by HN and persists date/time/note', () => {
    patient().then((p) => {
      visit('/staff/appointments')
      cy.contains('button', '+ สร้างนัดหมาย').click()
      choosePatient(p)
      chooseFixtureTherapist()
      chooseDate('.appointment-date-field', fixtures.tomorrow)
      chooseTime('09:00')
      cy.get('.appointment-note-field textarea').type('นัดติดตามอาการ')
      confirmSave()
      row(p.fullName).should('contain.text', '09:00')
      api('GET', `/api/appointments?query=${encodeURIComponent(p.hn)}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(1)
        const a = r.body.items[0]
        expect(a.patient.id).to.eq(p.id)
        expect(a.therapist.id).to.eq(fixtures.therapistId)
        expect(a.note).to.eq('นัดติดตามอาการ')
        expect(new Date(a.startAt).getTime()).to.eq(new Date(`${fixtures.tomorrow}T09:00:00+07:00`).getTime())
        persisted(a.id, { status: 'CONFIRMED' })
      })
    })
  })

  it('requires a patient and a time, rejects unmatched patient search, and saves nothing', () => {
    patient().then((p) => {
      visit('/staff/appointments/new')
      cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
      cy.contains('[role="alert"]', 'กรุณาเลือกผู้ป่วย').should('be.visible')
      cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').type('ไม่มีผู้ป่วยนี้แน่นอน')
      cy.contains('.patient-search-results', 'ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา').should('be.visible')
      cy.get('input[placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"]').clear()
      choosePatient(p)
      cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
      cy.contains('[role="alert"]', 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ').should('be.visible')
      cy.get('.appointment-confirm-modal').should('not.exist')
      api('GET', `/api/appointments?query=${encodeURIComponent(p.hn)}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.totalItems).to.eq(0)
      })
    })
  })

  it('edits a listed appointment and retains patient identity after rescheduling', () => {
    patient().then((p) => book(p.id).then((a) => {
      visit('/staff/appointments')
      row(p.fullName).contains('button', 'แก้ไข').click()
      cy.get('.reschedule-patient-field input').should('have.value', p.fullName).and('have.attr', 'readonly')
      chooseTime('13:00')
      cy.get('.reschedule-note-field textarea').clear().type('เลื่อนไปช่วงบ่าย')
      confirmSave(true)
      row(p.fullName).should('contain.text', '13:00')
      persisted(a.id, { note: 'เลื่อนไปช่วงบ่าย', status: 'CONFIRMED' }).then((saved) => {
        expect(saved.patient.id).to.eq(p.id)
        expect(new Date(saved.startAt).getTime()).to.eq(new Date(`${fixtures.tomorrow}T13:00:00+07:00`).getTime())
      })
    }))
  })

  it('requires a cancellation reason and persists cancellation from the list', () => {
    patient().then((p) => book(p.id).then((a) => {
      visit('/staff/appointments')
      dropdown(`สถานะนัดหมายของ ${p.fullName}`, 'ยกเลิก')
      cy.contains('.status-modal h2', 'ยืนยันการยกเลิกนัดหมาย').should('be.visible')
      cy.get('.status-modal textarea').should('have.attr', 'required')
      cy.get('.status-modal textarea').then(($field) => {
        expect($field[0].checkValidity()).to.eq(false)
      })
      cy.contains('.status-modal button', 'ยืนยันการยกเลิก').click()
      cy.get('.status-modal textarea').should('be.visible')
      persisted(a.id, { status: 'CONFIRMED' })
      cy.get('.status-modal textarea').type('ผู้ป่วยติดธุระ')
      cy.contains('.status-modal button', 'ยืนยันการยกเลิก').click()
      row(p.fullName).find('.appointment-status-filter').should('have.attr', 'data-status', 'CANCELLED')
      persisted(a.id, { status: 'CANCELLED', cancelReason: 'ผู้ป่วยติดธุระ' })
      cy.reload()
      row(p.fullName).find('.appointment-status-filter').should('have.attr', 'data-status', 'CANCELLED')
    }))
  })

  it('opens occupied calendar slots for editing and filters the day by therapist', () => {
    patient().then((p) => book(p.id).then((a) => {
      visit('/staff/appointments/calendar')
      dayTomorrow()
      dropdown('กรองนักกายภาพ', a.therapist.fullName)
      cy.get('.schedule-column').should('have.length', 1)
      cy.contains('.schedule-slot', p.fullName).should('have.attr', 'data-state', 'confirmed').click()
      cy.location('pathname').should('eq', `/staff/appointments/${a.id}/edit`)
      cy.get('.reschedule-patient-field input').should('have.value', p.fullName)
    }))
  })

  it('creates from an empty day-calendar slot with date/time prefilled', () => {
    patient().then((p) => {
      visit('/staff/appointments/calendar')
      dayTomorrow()
      cy.get('.schedule-column').first().find('.schedule-slot').eq(2).click()
      cy.location('search').should('include', `date=${fixtures.tomorrow}`).and('include', 'time=13')
      cy.contains('h1', 'สร้างนัดหมายจากปฏิทิน').should('be.visible')
      choosePatient(p)
      confirmSave()
      api('GET', `/api/appointments?query=${encodeURIComponent(p.hn)}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(1)
        expect(new Date(r.body.items[0].startAt).getTime()).to.eq(new Date(`${fixtures.tomorrow}T13:00:00+07:00`).getTime())
      })
    })
  })

  it('navigates weekly calendar forward and back while retaining therapist filtering', () => {
    const monday = new Date()
    const day = monday.getDay()
    monday.setDate(monday.getDate() + (day === 0 ? 1 : 8 - day))
    const date = `${monday.getFullYear()}-${String(monday.getMonth() + 1).padStart(2, '0')}-${String(monday.getDate()).padStart(2, '0')}`
    patient().then((p) => book(p.id, { startAt: `${date}T09:00:00+07:00` }).then((a) => {
      visit('/staff/appointments/calendar')
      cy.contains('.calendar-view-toggle button', 'รายสัปดาห์').should('have.class', 'active')
      dropdown('กรองนักกายภาพ', a.therapist.fullName)
      cy.get('.schedule-column').should('have.length', 5)
      cy.get('.schedule-slot').should('not.contain.text', p.fullName)
      cy.get('button[aria-label="ถัดไป"]').click()
      cy.contains('.schedule-slot', p.fullName).should('have.attr', 'data-state', 'confirmed')
      cy.get('button[aria-label="กรองนักกายภาพ"]').should('contain.text', a.therapist.fullName)
      cy.get('button[aria-label="ก่อนหน้า"]').click()
      cy.get('.schedule-slot').should('not.contain.text', p.fullName)
      persisted(a.id, { status: 'CONFIRMED' })
    }))
  })

  it('filters appointment list using the real date-range controls', () => {
    patient().then((p) => book(p.id).then(() => {
      visit('/staff/appointments')
      row(p.fullName).should('exist')
      const next = new Date(`${fixtures.tomorrow}T12:00:00`)
      next.setDate(next.getDate() + 1)
      const date = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`
      chooseDate('.appointment-date-range:first', date)
      cy.contains('.appointment-empty', 'ไม่พบข้อมูลนัดหมาย').should('be.visible')
      chooseDate('.appointment-date-range:first', fixtures.tomorrow)
      row(p.fullName).should('exist')
      // Move the lower bound back first, keeping the range valid while
      // testing that the upper bound independently excludes tomorrow.
      const yesterday = new Date(`${fixtures.tomorrow}T12:00:00`)
      yesterday.setDate(yesterday.getDate() - 1)
      const previous = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
      chooseDate('.appointment-date-range:first', previous)
      chooseDate('.appointment-date-range:last', previous)
      cy.contains('.appointment-empty', 'ไม่พบข้อมูลนัดหมาย').should('be.visible')
      chooseDate('.appointment-date-range:last', fixtures.tomorrow)
      row(p.fullName).should('exist')
    }))
  })

  it('surfaces a real booking conflict and leaves the original appointment unchanged', () => {
    patient().then((p) => book(p.id).then((a) => {
      visit('/staff/appointments/new')
      choosePatient(p)
      dropdown('เลือกนักกายภาพ', a.therapist.fullName)
      chooseDate('.appointment-date-field', fixtures.tomorrow)
      chooseTime('09:00')
      cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
      cy.contains('.appointment-confirm-modal button', /^ใช่$/).click()
      cy.contains('[role="alert"]', 'ผู้ให้บริการไม่ว่าง').should('be.visible')
      cy.get('.appointment-success-modal').should('not.exist')
      api('GET', `/api/appointments?query=${encodeURIComponent(p.hn)}`).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.items).to.have.length(1)
        expect(r.body.items[0].id).to.eq(a.id)
      })
      persisted(a.id, { status: 'CONFIRMED' })
    }))
  })

  it('validates new-patient contact details before creating an unregistered appointment', () => {
    visit('/staff/appointments/new')
    cy.get('input[type="radio"][value="NEW"]').check()
    cy.contains('.appointment-save button', 'บันทึกนัดหมาย').click()
    cy.contains('[role="alert"]', 'กรุณากรอกชื่อหรือชื่อเล่น และเบอร์โทรศัพท์ของผู้ป่วยใหม่').should('be.visible')
    cy.get('input[placeholder="กรอกชื่อเล่น"]').type('จองใหม่')
    cy.get('input[placeholder="กรอกเบอร์โทรศัพท์"]').type('081abc2345678')
    cy.get('input[placeholder="กรอกเบอร์โทรศัพท์"]').should('have.value', '0812345678')
    chooseFixtureTherapist()
    chooseDate('.appointment-date-field', fixtures.tomorrow)
    chooseTime('09:00')
    confirmSave()
    api('GET', '/api/appointments?size=100').then((r) => {
      expect(r.status).to.eq(200)
      expect(r.body.items).to.have.length(1)
      const a = r.body.items[0]
      expect(a).to.include({ patientType: 'NEW', contactName: 'จองใหม่', contactPhone: '0812345678' })
      expect(a.patient.id).to.eq(null)
      persisted(a.id, { patientType: 'NEW', contactName: 'จองใหม่' })
    })
  })
  it('API-only: progresses check-in/start/complete and rejects editing a completed appointment', () => {
    patient().then((p) => book(p.id).then((a) => {
      for (const [action, status] of [['check-in', 'ARRIVED'], ['start-service', 'IN_SERVICE'], ['complete', 'COMPLETED']]) {
        api('POST', `/api/appointments/${a.id}/${action}`, {}).then((r) => {
          expect(r.status, action).to.eq(200)
          expect(r.body.status).to.eq(status)
        })
      }
      persisted(a.id, { status: 'COMPLETED' })
      api('PATCH', `/api/appointments/${a.id}`, { startAt: `${fixtures.tomorrow}T13:00:00+07:00` }).then((r) => expect(r.status).to.eq(400))
      persisted(a.id, { status: 'COMPLETED' })
    }))
  })

  it('API-only no-show action persists and renders cancelled calendar styling; terminal actions are rejected', () => {
    patient().then((p) => book(p.id).then((a) => {
      api('POST', `/api/appointments/${a.id}/no-show`, {}).then((r) => {
        expect(r.status).to.eq(200)
        expect(r.body.status).to.eq('NO_SHOW')
      })
      persisted(a.id, { status: 'NO_SHOW' })
      api('POST', `/api/appointments/${a.id}/cancel`, { reason: 'ยกเลิกซ้ำ' }).then((r) => expect(r.status).to.eq(400))
      visit('/staff/appointments/calendar')
      dayTomorrow()
      cy.contains('.schedule-slot', p.fullName).should('have.attr', 'data-state', 'cancelled')
    }))
  })
})
