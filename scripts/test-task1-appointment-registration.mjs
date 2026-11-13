// Run only against the synthetic, isolated backend on localhost.
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
import { buildSaleRequest } from '../frontend/src/features/services/careTransactions.ts'

const base = new URL(process.env.TASK1_TEST_API || 'http://invalid')
assert(['127.0.0.1', 'localhost'].includes(base.hostname) && base.port, 'Use an isolated localhost test port')
assert.equal(process.env.TASK1_TEST_CONFIRM, 'isolated', 'Explicitly select the isolated test database')
const password = process.env.TASK1_TEST_PASSWORD
assert(password, 'Set the synthetic manager password')

let token
async function request(path, body, method = body ? 'POST' : 'GET') {
  const response = await fetch(new URL(path, base), {
    method,
    signal: AbortSignal.timeout(20000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const value = await response.json().catch(() => null)
  return { status: response.status, value }
}

const login = await request('/api/auth/login', { username: 'manager', password })
assert.equal(login.status, 200, 'Synthetic manager must log in')
token = login.value.token

const branches = await request('/api/branches')
const branch = branches.value.find((item) => item.branchCode === '01')
assert(branch, 'Synthetic branch 01 must exist')
const staff = await request('/api/staff')
const therapist = staff.value.find((item) => item.branchCode === '01' && item.position === 'Therapist' && item.active)
assert(therapist, 'Synthetic therapist must exist in branch 01')
const catalog = await request('/api/catalog/services')
const service = catalog.value[0]
assert(service, 'At least one active service must exist')

const stamp = Date.now().toString().slice(-8)
const formatClinicDate = (daysAhead) => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date(Date.now() + daysAhead * 86400000))
async function findAvailableSlot(excludeAppointmentId, notThisDateAndTime) {
  for (let daysAhead = 3; daysAhead <= 45; daysAhead++) {
    const date = formatClinicDate(daysAhead)
    const availability = await request(`/api/appointments/availability?${new URLSearchParams({
      branchCode: '01', therapistId: String(therapist.id), serviceId: String(service.id), date,
      ...(excludeAppointmentId ? { excludeAppointmentId: String(excludeAppointmentId) } : {}),
    })}`)
    const slot = availability.value.slots?.find((item) => item.available &&
      !(notThisDateAndTime?.date === date && notThisDateAndTime?.time === item.time))
    if (slot) return { date, time: slot.time, duration: availability.value.durationMinutes }
  }
  throw new Error('No open synthetic appointment slot found in the next 45 days')
}
const addMinutes = (time, duration) => {
  const [hours, minutes] = time.split(':').map(Number)
  const total = hours * 60 + minutes + duration
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}
const iso = (date, time) => `${date}T${time}:00+07:00`
const firstSlot = await findAvailableSlot()
const create = await request('/api/appointments', {
  branchCode: '01',
  therapistId: therapist.id,
  serviceId: service.id,
  patientType: 'NEW',
  contactName: `Task1 New ${stamp}`,
  contactPhone: `08${stamp.padStart(8, '0')}`,
  requestedServiceType: 'SingleVisit',
  startAt: iso(firstSlot.date, firstSlot.time),
  endAt: iso(firstSlot.date, addMinutes(firstSlot.time, firstSlot.duration)),
})
assert.equal(create.status, 201, `Create NEW appointment failed with ${create.status}: ${JSON.stringify(create.value)}`)
assert.equal(new Date(create.value.endAt).toISOString(), new Date(iso(firstSlot.date, addMinutes(firstSlot.time, firstSlot.duration))).toISOString())

const secondSlot = await findAvailableSlot(create.value.id, firstSlot)
const rescheduled = await request(`/api/appointments/${create.value.id}`, {
  branchCode: '01', therapistId: therapist.id, serviceId: service.id, note: null,
  startAt: iso(secondSlot.date, secondSlot.time),
  endAt: iso(secondSlot.date, addMinutes(secondSlot.time, secondSlot.duration)),
}, 'PATCH')
assert.equal(rescheduled.status, 200, `Reschedule failed with ${rescheduled.status}: ${JSON.stringify(rescheduled.value)}`)
assert.equal(new Date(rescheduled.value.endAt).toISOString(), new Date(iso(secondSlot.date, addMinutes(secondSlot.time, secondSlot.duration))).toISOString())

const beforeRegistration = await request(`/api/appointments/${create.value.id}`)
const progressBefore = await request(`/api/appointments/${create.value.id}/progress`)
assert.equal(beforeRegistration.value.status, 'UNCONFIRMED')
assert.equal(beforeRegistration.value.patient?.id ?? null, null)
assert.equal(progressBefore.value.state, 'NO_LINKED_RECORD')

const identityHead = `7${stamp.padStart(11, '0')}`.slice(0, 12).split('').map(Number)
const checksum = (11 - identityHead.reduce((sum, digit, index) => sum + digit * (13 - index), 0) % 11) % 10
const identityNumber = `${identityHead.join('')}${checksum}`
const registration = {
  branchCode: '01', customerType: 'Thai', title: 'นางสาว',
  firstName: 'Task1', middleName: null, lastName: `Runtime${stamp}`,
  gender: 'ไม่ระบุ', phone: `09${stamp.padStart(8, '0')}`,
  identityDocumentType: 'NATIONAL_ID', identityNumber,
  birthDate: '1990-01-01', bloodGroup: 'Unknown', nationality: 'ไทย',
  address: 'ที่อยู่ทดสอบ', email: `task1_${stamp}@example.test`,
  drugAllergy: true, foodAllergy: false, chronicDisease: 'ข้อมูลทดสอบ',
  emergencyContactName: 'ผู้ติดต่อทดสอบ', emergencyContactRelationship: 'ญาติ',
  emergencyContactPhone: `09${stamp.padStart(8, '0')}`, privacyConsent: true,
}
let registered = await request('/api/patients', registration)
let extraFieldsAccepted = registered.status === 201
if (registered.status !== 201) {
  // If Jackson rejects fields absent from the main DTO, retry the same synthetic record with only supported fields.
  const { drugAllergy, foodAllergy, chronicDisease, emergencyContactName,
    emergencyContactRelationship, emergencyContactPhone, privacyConsent, ...mainDto } = registration
  registered = await request('/api/patients', mainDto)
}
assert.equal(registered.status, 201, `Patient registration with the main DTO failed with ${registered.status}: ${JSON.stringify(registered.value)}`)
const patientId = registered.value.id
const profile = await request(`/api/patients/${patientId}`)
const extrasPersisted = extraFieldsAccepted &&
  profile.value.drugAllergy === true &&
  profile.value.chronicDisease === registration.chronicDisease &&
  profile.value.emergencyContactName === registration.emergencyContactName &&
  profile.value.privacyConsent === true
assert.equal((await request(`/api/appointments/${create.value.id}`)).value.patient?.id ?? null, null)
assert.equal((await request(`/api/appointments/${create.value.id}/progress`)).value.state, 'NO_LINKED_RECORD')

const labels = await request('/api/payment-statuses')
const methods = await request('/api/payment-methods')
const paymentLabelCode = labels.value.find((item) => item.enabled && item.code === 'PAID')?.code
const paymentMethodCode = methods.value.find((item) => item.active && item.name === 'BankTransfer')?.name
assert(paymentLabelCode && paymentMethodCode, 'Enabled payment label and method must exist')
const body = buildSaleRequest({
  appointmentId: create.value.id, patientId, branchCode: '01',
  salespersonStaffId: therapist.id, treatingStaffId: therapist.id,
  paymentLabelCode, paymentMethodCode, labels: labels.value, methods: methods.value,
  items: [{ type: 'SERVICE', referenceId: service.id, quantity: 1, price: service.price }],
})
const sale = await request('/api/payments', body)
assert.equal(sale.status, 201, `Service sale for newly registered patient failed with ${sale.status}`)
const progressAfter = await request(`/api/appointments/${create.value.id}/progress`)
assert.equal(progressAfter.value.state, 'FOUND')
assert.equal(progressAfter.value.servedPatientId, patientId)
assert.equal((await request(`/api/appointments/${create.value.id}`)).value.patient?.id ?? null, null)

const report = {
  base: base.origin,
  appointmentCreateAndRescheduleEndAt: 'passed with Asia/Bangkok +07:00 timestamps',
  newAppointmentStatusAndNoLinkBeforeService: 'passed',
  serviceUsesRegisteredPatientIdAndAppointmentIdWithoutLinkPatient: 'passed',
  patientRegistrationExtraFieldsAccepted: extraFieldsAccepted,
  patientRegistrationExtraFieldsPersisted: extrasPersisted,
}
const reportPath = process.env.TASK1_TEST_REPORT
if (reportPath) await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`)
console.log(JSON.stringify(report, null, 2))
