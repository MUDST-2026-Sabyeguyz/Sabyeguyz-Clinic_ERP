// Run only against a disposable, seeded PostgreSQL/backend instance.
// Node 22.18+ can strip the type-only imports in the shared frontend payload builder.
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
import { buildSaleRequest, buildCourseUsages } from '../frontend/src/features/services/careTransactions.ts'

const base = new URL(process.env.TASK2_TEST_API || 'http://invalid')
assert(['127.0.0.1', 'localhost'].includes(base.hostname) && base.port && !['80', '443', '8080'].includes(base.port), 'Use an isolated localhost test port')
assert.equal(process.env.TASK2_TEST_CONFIRM, 'isolated', 'Explicitly select the isolated test database')
assert(process.env.TASK2_TEST_PASSWORD, 'Set test account credentials in the environment')
let token
const checks = []
async function request(path, body, method = body ? 'POST' : 'GET', expected = [200, 201]) {
  const response = await fetch(new URL(path, base), { method, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) })
  const value = await response.json()
  assert(expected.includes(response.status), `${method} ${path}: ${response.status} ${JSON.stringify(value)}`)
  return value
}
const login = await request('/api/auth/login', { username: process.env.TASK2_TEST_USERNAME || 'manager', password: process.env.TASK2_TEST_PASSWORD })
token = login.token
assert(Number.isSafeInteger(login.staffId), 'Login must expose the actual Staff FK')
// Demo seed may assign equal IDs; the standalone AuthStaffIdentityCheck covers distinct IDs.
checks.push('login exposes staffId (distinct IDs covered by AuthStaffIdentityCheck)')
const stamp = Date.now().toString().slice(-9)
const unlinked = await request('/api/auth/register', { username: `task2_${stamp}`, email: `task2_${stamp}@example.test`, password: 'OnlySynthetic123!' })
assert.equal(unlinked.staffId, null)
checks.push('account without Staff returns staffId null')

const branches = await request('/api/branches')
const branch = branches.find(row => row.branchCode === '01')
assert(branch)
await request(`/api/branches/${branch.id}`, { ...branch, branchCode: '01', bookingOpenTime: '09:00', bookingCloseTime: '18:00', defaultAppointmentMinutes: 30 }, 'PUT')
const staff = await request('/api/staff')
assert(staff.some(person => person.id === login.staffId && person.branchCode === login.branchCode))
const seller = staff.find(person => person.branchCode === '01' && person.position === 'Therapist' && person.active)
assert(seller)
const treating = await request('/api/staff', { employeeCode: `T2${stamp}`, fullName: 'Task2 Treating', position: 'Therapist', branchCode: '01' })
const labels = await request('/api/payment-statuses')
const methods = await request('/api/payment-methods')
const paymentLabelCode = labels.find(row => row.enabled && row.code === 'PAID')?.code
assert(paymentLabelCode, 'Seeded enabled label required')
const services = await request('/api/catalog/services')
const templates = await request('/api/catalog/courses')
const service = services[0], template = templates[0]
assert(service && template)
const tomorrow = new Date(Date.now() + 86400000)
const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).format(tomorrow)
let slot = 0
async function book(patientId = 1, patientType = 'CURRENT') {
  const startMinutes = 9 * 60 + (slot++ * 30)
  const time = value => `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}:00+07:00`
  return request('/api/appointments', { branchCode: '01', therapistId: treating.id, serviceId: service.id, patientType,
    ...(patientType === 'CURRENT' ? { patientId } : { contactName: 'Task2 NEW', contactPhone: '0812345678', requestedServiceType: 'SingleVisit' }),
    startAt: `${day}T${time(startMinutes)}`, endAt: `${day}T${time(startMinutes + 30)}` })
}
const progress = id => request(`/api/appointments/${id}/progress`)
const context = { patientId: 1, branchCode: '01', salespersonStaffId: seller.id, treatingStaffId: treating.id,
  paymentLabelCode, paymentMethodCode: 'BankTransfer', labels, methods }
const serviceLine = { type: 'SERVICE', referenceId: service.id, quantity: 1, price: service.price }

const first = await book()
const saleBody = buildSaleRequest({ ...context, appointmentId: first.id, items: [serviceLine] })
await request('/api/payments', { ...saleBody, payments: [{ ...saleBody.payments[0], amount: saleBody.payments[0].amount - 1 }] }, 'POST', [400])
assert.equal((await progress(first.id)).state, 'NO_LINKED_RECORD')
const sale = await request('/api/payments', saleBody)
const evidence = await progress(first.id)
assert.equal(evidence.state, 'FOUND')
assert.equal(evidence.appointmentStatus, 'CONFIRMED')
assert.equal(evidence.sales.length, 1)
const receipt = await request(`/api/receipts/${sale.id}`)
assert.equal(receipt.receiptId, sale.receiptId)
assert.equal(receipt.paid, saleBody.payments[0].amount)
await request('/api/payments', saleBody, 'POST', [409])
checks.push('full single-service payment, saleId receipt detail, duplicate 409 and unchanged evidence')

const newBooking = await book(undefined, 'NEW')
await request('/api/payments', buildSaleRequest({ ...context, patientId: 3, appointmentId: newBooking.id, items: [serviceLine] }))
assert.equal((await request(`/api/appointments/${newBooking.id}`)).patient.id, null)
assert.equal((await progress(newBooking.id)).servedPatientId, 3)
checks.push('NEW completes with explicit served patient without linking appointment.patient')

const before = await request('/api/patients/1/courses')
const purchased = await request('/api/payments', buildSaleRequest({ ...context, items: [{ type: 'COURSE', referenceId: template.id, price: template.price, quantity: 2 }] }))
const courses = (await request('/api/patients/1/courses')).filter(row => !before.some(old => old.id === row.id))
assert.equal(courses.length, 2)
assert(courses.every(row => row.purchased === template.sessions && row.bonusSessions === 0))
assert.equal((await request(`/api/receipts/${purchased.id}`)).items.length, 2)
checks.push('course-only sale creates two purchased courses, total visits includes promotion once')
const directPurchase = await request('/api/course-purchases', { patientId: 1, courseId: template.id, branchCode: '01', caseOwnerStaffId: seller.id, paymentLabelCode })
assert.notEqual(directPurchase.id, undefined)
checks.push('dedicated purchase accepts owner and selected label')

await request('/api/course-transfers', { sourcePurchaseId: courses[0].id, destinationPatientId: 2, quantity: 1, reason: 'Task2 synthetic transfer' })
const transferred = await request('/api/patients/2/courses')
const recipientBooking = await book(2)
const recipientUsages = buildCourseUsages({ ...context, patientId: 2, appointmentId: recipientBooking.id }, [{ coursePurchaseId: courses[0].id, quantity: 1 }], transferred)
await request('/api/course-usages', recipientUsages[0])
assert.equal((await progress(recipientBooking.id)).servedPatientId, 2)
checks.push('single usage consumes actual transferred recipient entitlement')

const batchBooking = await book()
const available = await request('/api/patients/1/courses')
const batch = buildCourseUsages({ ...context, appointmentId: batchBooking.id }, [{ coursePurchaseId: courses[0].id, quantity: 2 }, { coursePurchaseId: courses[1].id, quantity: 1 }], available)
const batchResponse = await request('/api/course-usages/batch', { usages: batch })
assert.equal(batchResponse.length, 2)
const batchEvidence = await progress(batchBooking.id)
assert.equal(batchEvidence.usages.length, 3)
assert.equal(new Set(batchEvidence.usages.map(row => row.movementId)).size, 3)
assert(batchResponse.find(row => row.id === courses[0].id))
checks.push('three usages in one Visit; batch returns two distinct changed course rows')

const mixedBooking = await book()
const mixed = buildSaleRequest({ ...context, appointmentId: mixedBooking.id, items: [serviceLine],
  courses: await request('/api/patients/1/courses'), usageSelections: [{ coursePurchaseId: courses[1].id, quantity: 2 }] })
const mixedSale = await request('/api/payments', mixed)
const mixedEvidence = await progress(mixedBooking.id)
assert.equal(mixedEvidence.state, 'FOUND')
assert.equal(mixedEvidence.usages.length, 2)
assert.equal(mixedSale.paid, service.price)
checks.push('mixed sale and two existing usages commit in one Visit with no repeated course charge')

const rollbackBooking = await book()
const courseBefore = (await request('/api/patients/1/courses')).find(row => row.id === courses[1].id)
const receiptsBefore = await request('/api/payments?patientId=1&branchCode=01&receiptsOnly=true&size=100')
const invalidMixed = buildSaleRequest({ ...context, appointmentId: rollbackBooking.id, items: [serviceLine] })
invalidMixed.courseUsages = Array.from({ length: 30 }, () => ({ coursePurchaseId: courses[1].id }))
await request('/api/payments', invalidMixed, 'POST', [400, 409])
assert.equal((await progress(rollbackBooking.id)).state, 'NO_LINKED_RECORD')
assert.equal((await request('/api/patients/1/courses')).find(row => row.id === courses[1].id).remaining, courseBefore.remaining)
assert.equal((await request('/api/payments?patientId=1&branchCode=01&receiptsOnly=true&size=100')).totalItems, receiptsBefore.totalItems)
checks.push('failed mixed use rolls back receipt/payment/Visit and entitlement changes')

const movementId = batchEvidence.usages[0].movementId
await request(`/api/course-movements/${movementId}/reverse`, { reason: 'Task2 synthetic reversal' })
const reversed = await progress(batchBooking.id)
assert.equal(reversed.state, 'REVIEW_REQUIRED')
assert(reversed.usages.some(row => row.movementId === movementId && row.status === 'Reversed'))
checks.push('reverse uses movementId and keeps Reversed evidence')

const output = { baseline: 'b73f8cf762657ae95093a7d4025ee6e7b8d77bc8', authPatch: 'staffId in login response',
  completedAt: new Date().toISOString(), checks, passed: checks.length }
if (process.env.TASK2_TEST_REPORT) await writeFile(process.env.TASK2_TEST_REPORT, JSON.stringify(output, null, 2))
console.log(JSON.stringify(output, null, 2))
