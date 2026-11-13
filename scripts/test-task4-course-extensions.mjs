import { writeFile } from 'node:fs/promises'

const base = (process.env.BACKEND_BASE_URL ?? 'http://127.0.0.1:18084').replace(/\/$/, '')
const managerCredentials = {
  username: process.env.TASK5_MANAGER_USERNAME,
  password: process.env.TASK5_MANAGER_PASSWORD,
}

if (!managerCredentials.username || !managerCredentials.password) {
  throw new Error('Set TASK5_MANAGER_USERNAME and TASK5_MANAGER_PASSWORD before running this test.')
}

const checks = []
async function call(path, { method = 'GET', token, body, expected = [200] } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = text
  }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${path}: expected ${expected.join('/')} but received ${response.status}: ${text}`)
  }
  return { status: response.status, body: payload }
}

const manager = (await call('/api/auth/login', { method: 'POST', body: managerCredentials })).body
const managerToken = manager.token
if (!managerToken) throw new Error('Synthetic Manager login did not return a token.')

const suffix = Date.now().toString().slice(-8)
const therapistUsername = `ext${suffix}`
await call('/api/users', {
  method: 'POST',
  token: managerToken,
  expected: [201],
  body: {
    username: therapistUsername,
    email: `${therapistUsername}@example.test`,
    password: 'ExtensionTest123!',
    role: 'THERAPIST',
    employeeCode: `EXT-${suffix}`,
    fullName: 'Extension Test Therapist',
    branchCode: '01',
  },
})
const therapist = (await call('/api/auth/login', {
  method: 'POST',
  body: { username: therapistUsername, password: 'ExtensionTest123!' },
})).body
const therapistToken = therapist.token
if (!therapistToken || therapist.branchCode !== '01') throw new Error('Created synthetic Therapist login has an unexpected branch.')

const [patientPage, templates, labels, staff] = await Promise.all([
  call('/api/patients?query=&page=0&size=100&sort=nameAsc', { token: therapistToken }),
  call('/api/catalog/courses', { token: therapistToken }),
  call('/api/payment-statuses', { token: therapistToken }),
  call('/api/staff', { token: therapistToken }),
])
const patient = patientPage.body.items.find((row) => row.active && row.branchCode === therapist.branchCode)
const template = templates.body.find((row) => row.active)
const paidLabel = labels.body.find((row) => row.code === 'PAID' && row.enabled)
const seller = staff.body.find((row) => row.active && row.branchCode === therapist.branchCode && row.position === 'Therapist')
if (!patient || !template || !paidLabel || !seller) throw new Error('Test DB lacks a patient, course template, seller, or enabled PAID label.')

async function purchaseCourse() {
  const result = await call('/api/course-purchases', {
    method: 'POST',
    token: therapistToken,
    expected: [201],
    body: {
      patientId: patient.id,
      courseId: template.id,
      branchCode: therapist.branchCode,
      caseOwnerStaffId: seller.id,
      paymentLabelCode: paidLabel.code,
    },
  })
  return result.body
}

const firstCourse = await purchaseCourse()
const otherPatient = patientPage.body.items.find((row) => row.id !== patient.id)
if (otherPatient) {
  await call('/api/course-extensions', {
    method: 'POST',
    token: therapistToken,
    expected: [403],
    body: {
      courseId: firstCourse.id,
      value: 1,
      unit: 'Month',
      reason: 'synthetic wrong-owner check',
      requestedByPatientId: otherPatient.id,
    },
  })
  checks.push('extension request rejects a non-owner patient ID')
}

const created = await call('/api/course-extensions', {
  method: 'POST',
  token: therapistToken,
  expected: [201],
  body: {
    courseId: firstCourse.id,
    value: 1,
    unit: 'Month',
    reason: 'synthetic approval flow',
    requestedByPatientId: patient.id,
  },
})
if (created.body.status !== 'Pending') throw new Error('New course extension request is not Pending.')
const duplicate = await call('/api/course-extensions', {
  method: 'POST',
  token: therapistToken,
  expected: [409],
  body: {
    courseId: firstCourse.id,
    value: 2,
    unit: 'Month',
    reason: 'synthetic duplicate pending check',
    requestedByPatientId: patient.id,
  },
})
void duplicate
const queue = await call('/api/course-extensions?status=Pending', { token: managerToken })
if (!queue.body.some((row) => row.id === created.body.id)) throw new Error('Manager pending queue did not return the request.')
const approved = await call(`/api/course-extensions/${created.body.id}/approve`, {
  method: 'POST',
  token: managerToken,
  body: { note: 'synthetic approval' },
})
if (approved.body.status !== 'Approved' || !approved.body.oldExpiry || !approved.body.newExpiry) {
  throw new Error('Manager approval did not record the expiry change.')
}
const coursesAfterApproval = await call(`/api/patients/${patient.id}/courses`, { token: therapistToken })
const approvedCourse = coursesAfterApproval.body.find((row) => row.id === firstCourse.id)
if (!approvedCourse || approvedCourse.expiresAt.slice(0, 10) !== approved.body.newExpiry) {
  throw new Error('Approved extension did not update the course expiry returned by the patient course API.')
}
checks.push('request, Manager pending list, approval, and expiry history match main API')

const secondCourse = await purchaseCourse()
const expiryBeforeRejection = secondCourse.expiresAt.slice(0, 10)
const rejectedRequest = await call('/api/course-extensions', {
  method: 'POST',
  token: therapistToken,
  expected: [201],
  body: {
    courseId: secondCourse.id,
    value: 2,
    unit: 'Day',
    reason: 'synthetic rejection flow',
    requestedByPatientId: patient.id,
  },
})
const rejected = await call(`/api/course-extensions/${rejectedRequest.body.id}/reject`, {
  method: 'POST',
  token: managerToken,
  body: { note: 'synthetic rejection' },
})
if (rejected.body.status !== 'Rejected' || rejected.body.newExpiry !== null) {
  throw new Error('Manager rejection changed expiry or failed to record Rejected status.')
}
const coursesAfterRejection = await call(`/api/patients/${patient.id}/courses`, { token: therapistToken })
const rejectedCourse = coursesAfterRejection.body.find((row) => row.id === secondCourse.id)
if (!rejectedCourse || rejectedCourse.expiresAt.slice(0, 10) !== expiryBeforeRejection) {
  throw new Error('Rejected extension changed the course expiry.')
}
checks.push('Manager rejection records a decision without changing course expiry')

const report = { baseline: 'b73f8cf762657ae95093a7d4025ee6e7b8d77bc8', base, passed: checks.length, checks }
const reportPath = process.env.TASK5_TEST_REPORT
if (reportPath) await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`)
console.log(JSON.stringify(report, null, 2))
