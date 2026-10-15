const B = 'http://127.0.0.1:18082'
let token = ''
const log = []
async function api(method, path, body, label) {
  const res = await fetch(B + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text(); let json; try { json = JSON.parse(text) } catch { json = text }
  console.log(String(res.status).padEnd(4), (label || method + ' ' + path).slice(0, 70), res.ok ? '' : JSON.stringify(json).slice(0, 140))
  return { status: res.status, body: json }
}
const login = await api('POST', '/api/auth/login', { username: 'manager', password: 'manager123' }, 'login')
token = login.body.token
const stamp = Date.now()
let n = 0
const patient = (name, extra = {}) => api('POST', '/api/patients', {
  branchCode: '01', username: `t1p${stamp}${n}`, email: `t1p${stamp}${n}@example.com`, password: 'password123', title: 'นาย',
  fullName: name, birthDate: '1990-01-01', gender: 'ชาย', phone: `08${String(10000000 + (++n))}`,
  identityDocumentType: 'NATIONAL_ID', identityNumber: '12' + String(stamp).slice(-9) + String(n).padStart(2, '0'), address: 'กรุงเทพฯ',
  drugAllergy: false, foodAllergy: false, chronicDisease: '', emergencyContactName: 'x', emergencyContactRelationship: 'ญาติ',
  emergencyContactPhone: '0899999999', privacyConsent: true, ...extra,
}, 'register ' + name).then((r) => r.body)
const A = await patient('ผู้ป่วย เอ'), Bp = await patient('ผู้ป่วย บี'), C = await patient('ผู้ป่วย ซี')
const buy = (p, courseId, extra = {}) => api('POST', '/api/course-purchases', { patientId: p.id, courseId, branchCode: '01', ...extra }, `buy course ${courseId} for ${p.id}`).then((r) => r.body)
const c1 = await buy(A, 3)
const c2 = await buy(A, 4, { caseOwnerStaffId: 1 })
const c3 = await buy(C, 3, { shares: [{ staffId: 2, percent: 60 }, { staffId: 1, percent: 40 }], caseOwnerStaffId: 1 })
await api('POST', '/api/course-transfers', { sourcePurchaseId: c1.id, destinationPatientId: Bp.id, quantity: 2, reason: 'ยกให้' }, 'transfer c1 A->B')
// usages: walk-in and via appointment+check-in
const u1 = await api('POST', '/api/course-usages', { coursePurchaseId: c1.id }, 'use c1 walk-in')
const u2 = await api('POST', '/api/course-usages', { coursePurchaseId: c2.id }, 'use c2 walk-in')
const d = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
const appt = await api('POST', '/api/appointments', { branchCode: '01', patientId: Bp.id, therapistId: 1, serviceId: 2, startAt: `${d}T09:00:00+07:00` }, 'appointment for B')
await api('POST', `/api/appointments/${appt.body.id}/check-in`, undefined, 'check-in B')
await api('POST', '/api/course-usages', { coursePurchaseId: c1.id, appointmentId: appt.body.id }, 'use c1 for B (appointment)')
// an arrived-only visit (check-in without usage)
const appt2 = await api('POST', '/api/appointments', { branchCode: '01', patientId: C.id, therapistId: 1, serviceId: 2, startAt: `${d}T10:30:00+07:00` }, 'appointment for C')
await api('POST', `/api/appointments/${appt2.body.id}/check-in`, undefined, 'check-in C (arrived only)')
// reverse one usage
const led = await api('GET', `/api/course-purchases/${c2.id}`, undefined, 'ledger c2')
const mv = (led.body.movements || []).find((m) => m.type === 'USAGE')
if (mv) await api('POST', `/api/course-movements/${mv.id}/reverse`, { reason: 'บันทึกผิด' }, 'reverse usage c2')
await api('POST', '/api/course-usages', { coursePurchaseId: c2.id }, 'use c2 again (replacement)')
// single visit sale + partial refund completed + another pending
const pay = await api('POST', '/api/payments', { patientId: A.id, branchCode: '01', items: [{ type: 'SERVICE', referenceId: 1, quantity: 1 }], payments: [{ paymentMethodCode: 'Cash', amount: 800 }] }, 'single visit payment')
const rc = await api('GET', `/api/receipts/${pay.body.id}`, undefined, 'receipt')
const payment = rc.body.payments?.[0]
if (payment) {
  const r1 = await api('POST', `/api/payments/${payment.id}/refunds`, { amount: 300, paymentMethodCode: 'Cash', reason: 'ลูกค้าไม่พอใจ' }, 'refund 300')
  if (r1.body?.id) await api('POST', `/api/refunds/${r1.body.id}/complete`, undefined, 'complete refund')
  await api('POST', `/api/payments/${payment.id}/refunds`, { amount: 100, paymentMethodCode: 'Cash', reason: 'รอดำเนินการ' }, 'refund 100 (pending)')
}
// override rate, cancel a course, monthly close
await api('POST', `/api/commission/courses/${c3.id}/override-rate`, { rate: 0.08, reason: 'ปรับตามตกลง' }, 'override rate c3')
const c4 = await buy(Bp, 4)
await api('POST', `/api/course-purchases/${c4.id}/cancel`, { reason: 'ยกเลิกทดสอบ' }, 'cancel c4')
const month = new Date().toISOString().slice(0, 7)
await api('POST', `/api/commission/closings/${month}-01`, undefined, 'close month ' + month)
await buy(A, 4) // one more left Provisional (after close)
console.log('done')
