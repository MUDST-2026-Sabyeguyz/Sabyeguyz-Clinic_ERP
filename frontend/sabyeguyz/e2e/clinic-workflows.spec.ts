import { expect, Locator, Page, test } from '@playwright/test'

type Patient = {
  id: number
  hn: string
  fullName: string
  phone: string
  active: boolean
  branchCode: string
  branchName: string
}

const branch = { id: 1, code: 'RP', numericCode: '01', name: 'ราชพฤกษ์', active: true }
const patient: Patient = {
  id: 1,
  hn: 'HN-000001',
  fullName: 'สมชาย ใจดี',
  phone: '0812345678',
  active: true,
  branchCode: 'RP',
  branchName: 'ราชพฤกษ์',
}
const recipient: Patient = {
  id: 2,
  hn: 'HN-000002',
  fullName: 'สุดา สุขใจ',
  phone: '0898765432',
  active: true,
  branchCode: 'RP',
  branchName: 'ราชพฤกษ์',
}

const byTest = (scope: Page | Locator, id: string) => scope.locator(`[data-test="${id}"]`)

function profile(item: Patient = patient) {
  return {
    ...item,
    branch,
    title: 'นาย',
    birthDate: '1990-01-15',
    gender: 'ชาย',
    identityDocumentType: 'NATIONAL_ID',
    identityNumber: '1101700203456',
    address: 'กรุงเทพมหานคร',
    drugAllergy: false,
    foodAllergy: false,
    chronicDisease: '',
    emergencyContactName: item.fullName,
    emergencyContactRelationship: 'ญาติ',
    emergencyContactPhone: item.phone,
    privacyConsent: true,
    nickname: 'ชาย',
    email: 'somchai@example.com',
    bloodGroup: 'ไม่ทราบ',
    nationality: 'ไทย',
    customerType: 'ลูกค้าทั่วไป',
    customerSource: 'หน้าสาขา',
    insuranceCompany: '',
  }
}

function appointment(id: number, status: string = 'CONFIRMED') {
  return {
    id,
    branchCode: 'RP',
    patient: { id: patient.id, hn: patient.hn, fullName: patient.fullName },
    therapist: { id: 10, fullName: 'กายภาพ ใจดี' },
    service: { id: 20, name: 'Assessment', durationMinutes: 60 },
    startAt: '2026-09-20T09:00:00+07:00',
    endAt: '2026-09-20T10:00:00+07:00',
    status,
    note: '',
  }
}

async function installApiMocks(page: Page) {
  const state = {
    patients: [patient, recipient] as Patient[],
    appointments: [appointment(7)],
    sales: [
      {
        id: 55,
        receiptNo: 'RCP-000055',
        patient,
        branchCode: 'RP',
        salesperson: { id: 10, employeeCode: 'PT001', fullName: 'กายภาพ ใจดี' },
        total: 800,
        paid: 800,
        status: 'PAID',
        soldAt: '2026-09-10T09:00:00+07:00',
      },
    ],
    purchases: [
      {
        id: 31,
        patient,
        branchCode: 'RP',
        course: { id: 30, code: 'COURSE-10', name: 'คอร์สกายภาพ 10 ครั้ง' },
        price: 5000,
        sessions: 10,
        bonusSessions: 2,
        purchased: 12,
        used: 4,
        remaining: 8,
        purchasedAt: '2026-08-01T09:00:00+07:00',
        expiresAt: '2027-08-01T09:00:00+07:00',
        expired: false,
        usable: true,
        status: 'ACTIVE',
      },
    ],
    transfers: [] as Array<Record<string, unknown>>,
    nextPatientId: 3,
    nextAppointmentId: 8,
    nextSaleId: 56,
  }

  const respond = (route: Parameters<Parameters<Page['route']>[1]>[0], body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const body = request.postDataJSON?.() as Record<string, any> | undefined

    if (method === 'POST' && path === '/api/auth/login') {
      return respond(route, { id: 99, username: 'manager', email: 'manager@example.com', role: 'MANAGER', token: 'e2e-token', branchName: branch.name })
    }
    if (method === 'GET' && path === '/api/branches') return respond(route, [branch])
    if (method === 'GET' && path === '/api/master-data') {
      return respond(route, [
        { id: 1, category: 'Customer Group', code: 'GENERAL', name: 'ลูกค้าทั่วไป', active: true },
        { id: 2, category: 'Referral Channel', code: 'WALK_IN', name: 'หน้าสาขา', active: true },
        { id: 3, category: 'Insurance Company', code: 'AIA', name: 'AIA', active: true },
      ])
    }
    if (method === 'GET' && path === '/api/catalog/services') {
      return respond(route, [
        { id: 20, code: 'ASSESSMENT', name: 'Assessment', durationMinutes: 60, price: 800 },
        { id: 21, code: 'MANUAL', name: 'Manual Therapy', durationMinutes: 60, price: 1000 },
      ])
    }
    if (method === 'GET' && path === '/api/catalog/courses') {
      return respond(route, [{ id: 30, code: 'COURSE-10', name: 'คอร์สกายภาพ 10 ครั้ง', price: 5000, sessions: 10, bonusSessions: 2, validityDays: 365, active: true }])
    }
    if (method === 'GET' && path === '/api/catalog/therapists') return respond(route, [{ id: 10, code: 'PT001', fullName: 'กายภาพ ใจดี', branchCode: 'RP' }])
    if (method === 'GET' && path === '/api/staff') return respond(route, [{ id: 10, userId: 10, username: 'therapist', role: 'THERAPIST', employeeCode: 'PT001', fullName: 'กายภาพ ใจดี', phone: '0800000000', active: true }])
    if (method === 'GET' && path === '/api/payment-methods') return respond(route, [{ id: 1, code: 'CASH', name: 'เงินสด', active: true }])

    if (path === '/api/patients' && method === 'GET') {
      const query = (url.searchParams.get('query') ?? '').toLowerCase()
      const items = state.patients.filter((item) => `${item.hn} ${item.fullName} ${item.phone}`.toLowerCase().includes(query))
      return respond(route, { items, page: 0, size: 100, totalItems: items.length, totalPages: 1 })
    }
    if (path === '/api/patients' && method === 'POST') {
      const newPatient: Patient = {
        id: state.nextPatientId++,
        hn: `HN-${String(state.nextPatientId - 1).padStart(6, '0')}`,
        fullName: body?.fullName ?? 'ผู้ป่วยใหม่',
        phone: body?.phone ?? '',
        active: true,
        branchCode: body?.branchCode ?? 'RP',
        branchName: branch.name,
      }
      state.patients.push(newPatient)
      return respond(route, newPatient, 201)
    }
    const patientMatch = path.match(/^\/api\/patients\/(\d+)$/)
    if (patientMatch && method === 'GET') return respond(route, profile(state.patients.find((item) => item.id === Number(patientMatch[1])) ?? patient))
    if (patientMatch && method === 'PUT') {
      const existing = state.patients.find((item) => item.id === Number(patientMatch[1])) ?? patient
      Object.assign(existing, { fullName: body?.fullName ?? existing.fullName, phone: body?.phone ?? existing.phone })
      return respond(route, profile(existing))
    }
    const patientCoursesMatch = path.match(/^\/api\/patients\/(\d+)\/courses$/)
    if (patientCoursesMatch && method === 'GET') return respond(route, state.purchases.filter((item) => item.patient.id === Number(patientCoursesMatch[1])))

    if (path === '/api/appointments' && method === 'GET') return respond(route, { items: state.appointments, page: 0, size: 20, totalItems: state.appointments.length, totalPages: 1 })
    if (path === '/api/appointments' && method === 'POST') {
      const created = { ...appointment(state.nextAppointmentId++), patient: { id: body?.patientId ?? 1, hn: patient.hn, fullName: patient.fullName }, startAt: body?.startAt ?? appointment(0).startAt, note: body?.note ?? '' }
      state.appointments.unshift(created)
      return respond(route, created, 201)
    }
    const appointmentAction = path.match(/^\/api\/appointments\/(\d+)\/(check-in|start-service|complete|no-show|cancel)$/)
    if (appointmentAction && method === 'POST') {
      const item = state.appointments.find((entry) => entry.id === Number(appointmentAction[1]))!
      const statusMap: Record<string, string> = { 'check-in': 'ARRIVED', 'start-service': 'IN_SERVICE', complete: 'COMPLETED', 'no-show': 'NO_SHOW', cancel: 'CANCELLED' }
      Object.assign(item, { status: statusMap[appointmentAction[2]!] })
      if (appointmentAction[2] === 'cancel') Object.assign(item, { cancelReason: body?.reason })
      return respond(route, item)
    }
    const appointmentPatch = path.match(/^\/api\/appointments\/(\d+)$/)
    if (appointmentPatch && method === 'PATCH') {
      const item = state.appointments.find((entry) => entry.id === Number(appointmentPatch[1]))!
      Object.assign(item, { startAt: body?.startAt ?? item.startAt, status: 'RESCHEDULED' })
      return respond(route, item)
    }

    if (path === '/api/payments' && method === 'GET') return respond(route, { items: state.sales, page: 0, size: 20, totalItems: state.sales.length, totalPages: 1 })
    if (path === '/api/payments' && method === 'POST') {
      const created = { id: state.nextSaleId++, receiptNo: `RCP-${state.nextSaleId}`, patient, branchCode: 'RP', salesperson: { id: 10, employeeCode: 'PT001', fullName: 'กายภาพ ใจดี' }, total: 800, paid: 800, status: 'PAID', soldAt: '2026-09-12T09:00:00+07:00' }
      state.sales.unshift(created)
      return respond(route, created, 201)
    }
    const reverseSale = path.match(/^\/api\/payments\/(\d+)\/reverse$/)
    if (reverseSale && method === 'POST') {
      const sale = state.sales.find((item) => item.id === Number(reverseSale[1]))!
      Object.assign(sale, { status: 'VOIDED' })
      return respond(route, sale)
    }

    const purchaseMatch = path.match(/^\/api\/course-purchases\/(\d+)$/)
    if (purchaseMatch && method === 'GET') return respond(route, { purchase: state.purchases[0], movements: [], allocations: [] })
    if (path === '/api/course-usages' && method === 'POST') {
      const purchase = state.purchases.find((item) => item.id === Number(body?.coursePurchaseId))!
      purchase.used += 1; purchase.remaining -= 1
      return respond(route, purchase)
    }
    if (path === '/api/course-transfers' && method === 'POST') {
      state.purchases[0]!.remaining -= Number(body?.quantity ?? 1)
      state.transfers.push({ id: 101, sourcePatientId: patient.id, sourcePatientHn: patient.hn, sourcePatientName: patient.fullName, destinationPatientId: recipient.id, destinationPatientHn: recipient.hn, destinationPatientName: recipient.fullName, branchCode: 'RP', courseId: 30, quantity: Number(body?.quantity ?? 1), reason: body?.reason, createdAt: '2026-09-12T09:00:00+07:00' })
      return respond(route, state.transfers[0])
    }
    if (path === '/api/course-transfers' && method === 'GET') return respond(route, state.transfers)

    if (path.startsWith('/api/dashboard/summary')) return respond(route, { uniquePatients: 2, visits: 5, totalRevenue: 5800, courseRevenue: 5000, singleVisitRevenue: 800, assessmentRevenue: 800, receiptsIssued: 3, receiptsVoided: 0 })
    if (path.startsWith('/api/reports/appointments')) return respond(route, { byStatus: [{ status: 'CONFIRMED', appointments: 1 }], appointments: 1, visits: 0, noShowRate: 0 })
    if (path.startsWith('/api/reports/revenue')) return respond(route, { totalRevenue: 5800, byCategory: [{ type: 'Assessment', amount: 800, share: 1 }] })
    if (path.startsWith('/api/reports/course-balances')) return respond(route, { purchases: 1, rows: [{ hn: patient.hn, patientFullName: patient.fullName, branchCode: 'RP', courseName: 'คอร์สกายภาพ 10 ครั้ง', purchased: 12, used: 4, remaining: 8, expired: false }] })
    if (path.startsWith('/api/reports/staff-sales')) return respond(route, { rows: [{ fullName: 'กายภาพ ใจดี', receipts: 1, amount: 800 }] })
    if (path.startsWith('/api/commission/report')) return respond(route, [{ staffId: 10, staffName: 'กายภาพ ใจดี', branchCode: 'RP', branchName: branch.name, saleCount: 1, base: 800, rate: 0.1, commission: 80 }])

    if (method === 'GET' && path === '/api/users') return respond(route, [{ id: 1, username: 'manager', email: 'manager@example.com', role: 'MANAGER', employeeCode: 'MG001', fullName: 'ผู้จัดการ คลินิก', phone: '0800000000', branchCode: 'RP', branchName: branch.name, active: true }])
    if (method === 'GET' && path === '/api/therapists') return respond(route, [{ id: 10, code: 'PT001', fullName: 'กายภาพ ใจดี', branchCode: 'RP', branchName: branch.name, active: true }])
    if (method === 'GET' && path === '/api/services') return respond(route, [{ id: 20, code: 'ASSESSMENT', name: 'Assessment', defaultDurationMinutes: 60, price: 800, active: true }])
    if (method === 'GET' && path === '/api/courses') return respond(route, [{ id: 30, code: 'COURSE-10', name: 'คอร์สกายภาพ 10 ครั้ง', price: 5000, sessions: 10, bonusSessions: 2, validityDays: 365, active: true }])
    if (method === 'GET' && path === '/api/resources') return respond(route, [])
    if (method === 'GET' && path === '/api/commission/schemes') return respond(route, [{ id: 1, name: 'Course Sales', version: '1', effectiveFrom: '2026-08-01', effectiveTo: null, active: true }])
    if (method === 'GET' && path === '/api/commission/schemes/1/tiers') return respond(route, [{ minimum: 0, maximum: null, rate: 0.1, order: 1 }])
    return respond(route, {})
  })
}

async function authenticate(page: Page) {
  await page.addInitScript(({ branchName }) => {
    localStorage.setItem('authToken', 'e2e-token')
    localStorage.setItem('authUser', JSON.stringify({ id: 99, username: 'manager', role: 'MANAGER', branchName }))
  }, { branchName: branch.name })
}

test.describe('SaByeGuyz major user workflows', () => {
  test('staff can log in and reach the manager dashboard', async ({ page }) => {
    await installApiMocks(page)
    await page.goto('/')
    await byTest(page, 'username-input').fill('manager')
    await byTest(page, 'password-input').fill('password')
    await byTest(page, 'login-button').click()
    await expect(page).toHaveURL(/\/staff\/dashboard$/)
    await expect(byTest(page, 'dashboard-revenue-metric')).toContainText('฿5,800')
  })

  test('staff can register a new patient and find the created HN', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/patients')
    await byTest(page, 'register-patient-link').click()
    await byTest(page, 'registration-first-name').fill('ผู้ป่วย')
    await byTest(page, 'registration-last-name').fill('ใหม่')
    await byTest(page, 'registration-nickname').fill('ใหม่')
    await byTest(page, 'registration-email').fill('new.patient@example.com')
    await byTest(page, 'registration-phone').fill('0823456789')
    await byTest(page, 'registration-identity-type').selectOption('NATIONAL_ID')
    await byTest(page, 'registration-identity-number').fill('1101700203456')
    await page.locator('.date-trigger').click()
    await page.getByRole('dialog').getByRole('button', { name: '15', exact: true }).click()
    await byTest(page, 'registration-address').fill('กรุงเทพมหานคร')
    await byTest(page, 'registration-submit').click()
    await expect(page).toHaveURL(/\/staff\/patients$/)
    await expect(page.getByRole('status')).toContainText('เพิ่มผู้ป่วยใหม่แล้ว')
    await expect(byTest(page, 'patient-row-3')).toBeVisible()
  })

  test('staff can search a patient and update the patient profile', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/patients')
    await byTest(page, 'patient-search-input').fill('สมชาย')
    await expect(byTest(page, 'patient-row-1')).toBeVisible()
    await byTest(byTest(page, 'patient-row-1'), 'patient-profile-link').click()
    await expect(page.getByRole('heading', { name: /สมชาย ใจดี/ })).toBeVisible()
    await byTest(page, 'edit-patient-button').click()
    await byTest(page, 'profile-email').fill('updated@example.com')
    await byTest(page, 'save-patient-button').click()
    await expect(page.getByRole('status')).toContainText('บันทึกข้อมูลผู้ป่วยแล้ว')
  })

  test('staff can create, reschedule, and progress an appointment', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/appointments')
    await byTest(page, 'create-appointment-button').click()
    await byTest(page, 'appointment-patient-picker').click()
    await byTest(page, 'appointment-patient-option-1').click()
    await byTest(page, 'appointment-time-select').selectOption('10:00')
    await byTest(page, 'save-appointment-button').click()
    await expect(byTest(page, 'appointment-row-8')).toBeVisible()
    await byTest(byTest(page, 'appointment-row-8'), 'edit-appointment-button').click()
    await byTest(page, 'appointment-time-select').selectOption('11:00')
    await byTest(page, 'save-appointment-button').click()
    await expect(byTest(byTest(page, 'appointment-row-8'), 'appointment-status-select')).toHaveValue('RESCHEDULED')
  })

  test('staff can check in, start, and complete a visit', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/appointments')
    const status = byTest(byTest(page, 'appointment-row-7'), 'appointment-status-select')
    await status.selectOption('ARRIVED')
    await expect(status).toHaveValue('ARRIVED')
    await status.selectOption('IN_SERVICE')
    await expect(status).toHaveValue('IN_SERVICE')
    await status.selectOption('COMPLETED')
    await expect(status).toHaveValue('COMPLETED')
  })

  test('staff can cancel an appointment with a required reason', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/appointments')
    const row = byTest(page, 'appointment-row-7')
    await byTest(row, 'appointment-status-select').selectOption('CANCELLED')
    await expect(byTest(page, 'cancellation-form')).toBeVisible()
    await byTest(page, 'cancellation-reason').fill('ผู้ป่วยขอยกเลิกนัด')
    await byTest(page, 'confirm-cancellation-button').click()
    await expect(byTest(row, 'appointment-status-select')).toHaveValue('CANCELLED')
  })

  test('staff can record a service and buy or use a course', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/services')
    await byTest(page, 'service-patient-search').fill('สมชาย')
    await byTest(page, 'service-patient-option-1').click()
    await byTest(page, 'confirm-service-button').click()
    await expect(byTest(page, 'service-success')).toBeVisible()
    await byTest(page, 'service-mode-select').selectOption('course')
    await byTest(page, 'buy-course-button').click()
    await byTest(page, 'confirm-service-button').click()
    await expect(byTest(page, 'service-success')).toBeVisible()
    await byTest(page, 'use-course-button').click()
    await byTest(page, 'confirm-service-button').click()
    await expect(byTest(page, 'service-success')).toBeVisible()
  })

  test('staff can inspect and void a transaction with a reason', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/transactions')
    await byTest(page, 'transaction-detail-toggle').click()
    await expect(page.getByText('รายละเอียดธุรกรรม')).toBeVisible()
    await byTest(page, 'void-transaction-button').click()
    await byTest(page, 'void-reason-input').fill('บันทึกรายการผิด')
    await byTest(page, 'confirm-void-button').click()
    await expect(byTest(page, 'transaction-row-55')).toContainText('ยกเลิกแล้ว')
  })

  test('staff can transfer course rights and view transfer history', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/courses')
    await byTest(page, 'start-course-transfer').click()
    await byTest(page, 'course-recipient-search').fill('สุดา')
    await byTest(page, 'course-recipient-option-2').click()
    await byTest(page, 'course-transfer-amount').fill('2')
    await byTest(page, 'confirm-course-transfer').click()
    await expect(byTest(page, 'course-transfer-success')).toBeVisible()
    await byTest(page, 'close-course-workspace').click()
    await byTest(page, 'view-course-history').click()
    await expect(page.getByText('ไม่พบประวัติการโอน')).not.toBeVisible()
    await expect(page.getByText('2 ครั้ง')).toBeVisible()
  })

  test('manager can open reports and back-office settings', async ({ page }) => {
    await installApiMocks(page); await authenticate(page)
    await page.goto('/staff/dashboard')
    await byTest(page, 'nav-staff-reports').click()
    await expect(page.getByRole('heading', { name: 'รายงาน', exact: true })).toBeVisible()
    await byTest(page, 'report-select').selectOption('balance')
    await expect(page.locator('.report-picker').getByRole('heading', { name: 'ยอดคงเหลือคอร์ส', exact: true })).toBeVisible()
    await byTest(page, 'nav-staff-settings').click()
    await expect(page.getByRole('heading', { name: 'ตั้งค่าระบบ' })).toBeVisible()
    await byTest(page, 'settings-section-branches').click()
    await byTest(page, 'open-settings-editor').click()
    await expect(byTest(page, 'settings-editor')).toBeVisible()
  })
})
