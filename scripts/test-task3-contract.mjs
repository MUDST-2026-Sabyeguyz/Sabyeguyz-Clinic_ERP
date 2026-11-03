// Run only against a disposable backend and synthetic database.
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'

const base = new URL(process.env.TASK3_TEST_API || 'http://invalid')
assert(['127.0.0.1', 'localhost'].includes(base.hostname) && base.port && !['80', '443', '8080'].includes(base.port), 'Use an isolated localhost test port')
assert.equal(process.env.TASK3_TEST_CONFIRM, 'isolated', 'Explicitly select the isolated test database')
assert(process.env.TASK3_TEST_PASSWORD, 'Set the synthetic manager test password')
const checks = []
let token

async function request(path, body, method = body ? 'POST' : 'GET', expected = [200, 201]) {
  const response = await fetch(new URL(path, base), {
    method,
    signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  const text = await response.text()
  let value
  try { value = text ? JSON.parse(text) : null } catch { value = text }
  assert(expected.includes(response.status), `${method} ${path}: ${response.status} ${JSON.stringify(value)}`)
  return value
}

const login = await request('/api/auth/login', {
  username: process.env.TASK3_TEST_USERNAME || 'manager',
  password: process.env.TASK3_TEST_PASSWORD,
})
token = login.token
const current = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())
const month = current.slice(0, 7)
const branchCode = '01'

const closingsBefore = await request(`/api/commission/closings?month=${month}&branchCode=${branchCode}`)
assert(closingsBefore.length > 0, 'Synthetic setup must have one seller closing to test')
const closedRows = await request(`/api/commission/closings/${month}?branchCode=${branchCode}`, {}, 'POST')
assert(Array.isArray(closedRows) && closedRows.every(row => row.status === 'CLOSED'))
const closedBranch = await request(`/api/commission/branch-closings?month=${month}&branchCode=${branchCode}`)
assert.equal(closedBranch.status, 'CLOSED')
checks.push('month YYYY-MM closes branch and returns individual closing array')

const individual = closedRows.find(row => row.id > 0)
assert(individual, 'Expected a persisted individual closing')
await request(`/api/commission/closings/${individual.id}/reopen`, { reason: '' }, 'POST', [400])
const reopened = await request(`/api/commission/closings/${individual.id}/reopen`, { reason: 'synthetic Task 3 verification' })
assert.equal(reopened.status, 'OPEN')
assert.equal((await request(`/api/commission/branch-closings?month=${month}&branchCode=${branchCode}`)).status, 'OPEN')
const reclosed = await request(`/api/commission/closings/${month}?branchCode=${branchCode}`, {}, 'POST')
assert(reclosed.every(row => row.status === 'CLOSED'))
checks.push('individual reopen requires a reason and reopens the branch state')

const prepared = await request(`/api/commission/payments/prepare?month=${month}&branchCode=${branchCode}`, {}, 'POST')
assert(Array.isArray(prepared), 'prepare response must be an array')
const closedPayments = await request(`/api/commission/payments/close?month=${month}&branchCode=${branchCode}`, {}, 'POST')
assert(Array.isArray(closedPayments), 'close payment response must be an array')
const toPay = closedPayments.find(row => row.status === 'CLOSED' && row.net != null && row.pendingSessions === 0)
assert(toPay, 'Need one fully calculated synthetic payment to pay')
const paidDate = current
const reference = `TASK3-${Date.now()}`
const paid = await request(`/api/commission/payments/${toPay.id}/pay`, { paidDate, reference }, 'POST')
assert.equal(paid.status, 'PAID')
assert.equal(paid.paidDate, paidDate)
assert.equal(paid.paymentReference, reference)
await request(`/api/commission/payments/${toPay.id}/pay`, { paidDate, reference }, 'POST', [400, 409])
await request(`/api/commission/payments/${toPay.id}/reopen`, {}, 'POST', [400, 409])
const afterPay = await request(`/api/commission/payments?month=${month}&branchCode=${branchCode}`)
assert.equal(afterPay.find(row => row.id === toPay.id)?.status, 'PAID')
assert.equal(afterPay.find(row => row.id === toPay.id)?.net, paid.net)
checks.push('branch payment array, nullable pending/net, paidDate/reference, immutable PAID state')

const emptyMonth = '2026-09'
const emptyBefore = await request(`/api/commission/closings?month=${emptyMonth}&branchCode=${branchCode}`)
assert.equal(emptyBefore.length, 0, 'Chosen month must be empty in the synthetic DB')
const emptyClosed = await request(`/api/commission/closings/${emptyMonth}?branchCode=${branchCode}`, {}, 'POST')
assert.deepEqual(emptyClosed, [])
assert.equal((await request(`/api/commission/branch-closings?month=${emptyMonth}&branchCode=${branchCode}`)).status, 'CLOSED')
const emptyReopened = await request(`/api/commission/branch-closings/${emptyMonth}/reopen?branchCode=${branchCode}`, { reason: 'empty month test' })
assert.equal(emptyReopened.status, 'OPEN')
checks.push('empty branch month can close with zero individual rows and reopens with reason')

const sales = await request(`/api/payments?branchCode=${branchCode}&page=0&size=100`)
const sourceSale = sales.items.find(row => row.itemType === 'CoursePurchase' && row.salesperson?.id === toPay.staffId)
if (sourceSale) {
  const adjustment = await request('/api/commission/adjustments', {
    staffId: toPay.staffId,
    originalMonth: month,
    appliedMonth: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit' }).format(new Date(Date.now() + 35 * 86400000)),
    amount: 25,
    reason: 'synthetic source-branch application test',
    saleTransactionId: sourceSale.id,
  })
  await request(`/api/commission/adjustments/${adjustment.id}/approve`, {}, 'POST')
  const appliedMonth = adjustment.appliedMonth
  await request(`/api/commission/adjustments/${adjustment.id}/applications`, {
    month: appliedMonth, amount: 25, branchCode: '02',
  }, 'POST', [400])
  const application = await request(`/api/commission/adjustments/${adjustment.id}/applications`, {
    month: appliedMonth, amount: 25, branchCode,
  })
  assert(application.id)
  checks.push('adjustment application rejects a different branch and accepts the source branch')
} else {
  checks.push('adjustment source-branch runtime scenario skipped: synthetic source sale not found')
}

const report = {
  baseline: 'b73f8cf762657ae95093a7d4025ee6e7b8d77bc8',
  database: 'clinic_alignment_20261031 (disposable; synthetic fixtures)',
  completedAt: new Date().toISOString(),
  checks,
  passed: checks.length,
}
await writeFile(new URL('../state/task2-runtime/task3-contract-report.json', import.meta.url), `${JSON.stringify(report, null, 2)}\n`)
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
