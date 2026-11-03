import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'

const base = new URL(process.env.TASK5_TEST_API ?? 'http://127.0.0.1:18084')
assert(['127.0.0.1', 'localhost'].includes(base.hostname) && base.port, 'Use an isolated localhost backend port')
const username = process.env.TASK5_MANAGER_USERNAME
const password = process.env.TASK5_MANAGER_PASSWORD
assert(username && password, 'Set synthetic manager credentials through TASK5_MANAGER_USERNAME/PASSWORD')
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
  const text = await response.text()
  const value = text ? JSON.parse(text) : null
  return { status: response.status, value }
}

const login = await request('/api/auth/login', { username, password })
assert.equal(login.status, 200, 'Synthetic Manager must log in')
token = login.value.token

const patientPage = await request('/api/patients?query=&page=0&size=100&sort=nameAsc')
const patient = patientPage.value.items.find((row) => row.active && row.branchCode === '01')
assert(patient, 'A synthetic patient in branch 01 must exist')
const branches = await request('/api/branches')
const branch = branches.value.find((row) => row.branchCode === '01')
assert(branch, 'Synthetic branch 01 must exist')
const configuredBranch = await request(`/api/branches/${branch.id}`, {
  branchCode: '01',
  name: branch.name,
  address: branch.address,
  phone: branch.phone,
  bookingOpenTime: '09:00',
  bookingCloseTime: '18:00',
  defaultAppointmentMinutes: 30,
}, 'PUT')
assert.equal(configuredBranch.status, 200, 'Could not configure synthetic branch booking hours')
const staff = await request('/api/staff')
const therapist = staff.value.find((row) => row.active && row.branchCode === '01' && row.position === 'Therapist')
assert(therapist, 'An active synthetic therapist in branch 01 must exist')
const services = await request('/api/catalog/services')
const service = services.value[0]
assert(service, 'An appointment service must exist')

const clinicDate = (daysAhead) => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date(Date.now() + daysAhead * 86400000))
async function availableSlot(excludeAppointmentId) {
  for (let days = 3; days <= 45; days++) {
    const date = clinicDate(days)
    const query = new URLSearchParams({
      branchCode: '01', therapistId: String(therapist.id), serviceId: String(service.id), date,
      ...(excludeAppointmentId ? { excludeAppointmentId: String(excludeAppointmentId) } : {}),
    })
    const response = await request(`/api/appointments/availability?${query}`)
    const slot = response.value.slots?.find((item) => item.available)
    if (slot) return { date, time: slot.time, duration: response.value.durationMinutes }
  }
  throw new Error('No available synthetic appointment slot in the next 45 days')
}
const addMinutes = (time, duration) => {
  const [hours, minutes] = time.split(':').map(Number)
  const total = hours * 60 + minutes + duration
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}
const timestamp = (slot) => `${slot.date}T${slot.time}:00+07:00`
const receiptCount = async () => {
  const result = await request(`/api/payments?${new URLSearchParams({
    patientId: String(patient.id), branchCode: '01', receiptsOnly: 'true', page: '0', size: '100',
  })}`)
  assert.equal(result.status, 200)
  return result.value.totalItems
}
const usageCount = async () => {
  const result = await request('/api/course-usages')
  assert.equal(result.status, 200)
  return result.value.length
}

const beforeReceipts = await receiptCount()
const beforeUsages = await usageCount()
const first = await availableSlot()
const create = await request('/api/appointments', {
  patientId: patient.id,
  branchCode: '01',
  therapistId: therapist.id,
  serviceId: service.id,
  patientType: 'CURRENT',
  startAt: timestamp(first),
  endAt: `${first.date}T${addMinutes(first.time, first.duration)}:00+07:00`,
})
assert.equal(create.status, 201, `Synthetic appointment create failed: ${JSON.stringify(create.value)}`)
assert.equal(create.value.status, 'UNCONFIRMED')

const second = await availableSlot(create.value.id)
const reschedule = await request(`/api/appointments/${create.value.id}`, {
  branchCode: '01',
  therapistId: therapist.id,
  serviceId: service.id,
  startAt: timestamp(second),
  endAt: `${second.date}T${addMinutes(second.time, second.duration)}:00+07:00`,
}, 'PATCH')
assert.equal(reschedule.status, 200, `Synthetic reschedule failed: ${JSON.stringify(reschedule.value)}`)
assert.equal(new Date(reschedule.value.endAt).toISOString(), new Date(`${second.date}T${addMinutes(second.time, second.duration)}:00+07:00`).toISOString())

const cancelled = await request(`/api/appointments/${create.value.id}/cancel`, { reason: 'synthetic calendar cancellation' })
assert.equal(cancelled.status, 200, `Synthetic cancellation failed: ${JSON.stringify(cancelled.value)}`)
assert.equal(cancelled.value.status, 'CANCELLED')
const progress = await request(`/api/appointments/${create.value.id}/progress`)
assert.equal(progress.status, 200)
assert.equal(progress.value.state, 'NO_LINKED_RECORD')
assert.equal(progress.value.sales.length, 0)
assert.equal(progress.value.usages.length, 0)
assert.equal(await receiptCount(), beforeReceipts, 'Calendar actions changed receipt count')
assert.equal(await usageCount(), beforeUsages, 'Calendar actions changed course usage count')

const report = {
  baseline: 'b73f8cf762657ae95093a7d4025ee6e7b8d77bc8',
  appointmentId: create.value.id,
  passed: 1,
  checks: ['reschedule and cancel preserve endAt and appointment-only progress; no sale, receipt, or course usage created'],
}
const reportPath = process.env.TASK5_TEST_REPORT
if (reportPath) await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`)
console.log(JSON.stringify(report, null, 2))
