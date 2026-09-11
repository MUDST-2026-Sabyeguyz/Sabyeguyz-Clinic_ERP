<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { patientCourses, courseLedger, useCourse, reverseUsage, cancelPurchase, type CoursePurchase, type CourseLedger } from '@/api/courseLedger'
const route = useRoute()
const patientId = ref(String(route.query.patientId || ''))
const rows = ref<CoursePurchase[]>([]), ledger = ref<CourseLedger | null>(null)
const busy = ref(false), loaded = ref(false), error = ref(''), success = ref('')
const appointmentId = ref(''), note = ref(''), reason = ref('')
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { await action() } catch (e) { error.value = e instanceof Error ? e.message : 'ดำเนินการไม่สำเร็จ' } finally { busy.value = false }
}
async function refresh() { rows.value = await patientCourses(Number(patientId.value)); loaded.value = true }
function search() { return run(async () => { rows.value = []; ledger.value = null; loaded.value = false; await refresh() }) }
function select(id: number) { return run(async () => { ledger.value = null; ledger.value = await courseLedger(id) }) }
async function refreshSelected(id: number) { ledger.value = await courseLedger(id); await refresh() }
function consume() { return run(async () => {
  if (!ledger.value?.purchase.usable) throw new Error('Course นี้ไม่สามารถใช้งานได้')
  const id = ledger.value.purchase.id
  await useCourse({ coursePurchaseId: id, ...(appointmentId.value ? { appointmentId: Number(appointmentId.value) } : {}), ...(note.value.trim() ? { note: note.value.trim() } : {}) })
  success.value = 'ใช้สิทธิ์ 1 ครั้งแล้ว'; await refreshSelected(id)
}) }
function reverse(id: number) { return run(async () => {
  if (!reason.value.trim()) throw new Error('กรุณาระบุเหตุผลการกลับรายการ')
  const purchase = await reverseUsage(id, reason.value.trim()); success.value = 'กลับรายการแล้ว'; await refreshSelected(purchase.id)
}) }
function cancel() { return run(async () => {
  if (!ledger.value || !reason.value.trim()) throw new Error('กรุณาระบุเหตุผลการยกเลิก')
  const id = ledger.value.purchase.id; await cancelPurchase(id, reason.value.trim()); success.value = 'ยกเลิกสิทธิ์แล้ว (ไม่ใช่การคืนเงิน)'; await refreshSelected(id)
}) }
</script>
<template>
  <section class="patient-page"><header class="patient-page-header"><h1>สิทธิ์ Course และบัญชีเคลื่อนไหว</h1><RouterLink to="/payments">ซื้อ Course / ชำระเงิน</RouterLink></header>
    <form class="form-card" @submit.prevent="search"><label>Patient ID<input v-model="patientId" type="number" min="1" required :disabled="busy" /></label><button :disabled="busy">ค้นหา</button></form>
    <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="success" class="success" role="status">{{ success }}</p><p v-if="busy" role="status">กำลังดำเนินการ...</p>
    <p v-else-if="loaded && !rows.length">ผู้ป่วยยังไม่มี Course</p>
    <article v-for="row in rows" :key="row.id" class="patient-result-card"><div><h2>{{ row.course.name }}</h2><p>{{ row.patient.hn }} · {{ row.patient.fullName }} · {{ row.branchCode }}</p><p>ซื้อ {{ row.purchased }} · ใช้ {{ row.used }} · คงเหลือ {{ row.remaining }} ครั้ง</p><p>ราคา ณ วันที่ซื้อ {{ row.price }} บาท · หมดอายุ {{ row.expiresAt }} · {{ row.status }}</p></div><button :disabled="busy" @click="select(row.id)">ดูบัญชี #{{ row.id }}</button></article>
    <section v-if="ledger" class="form-card"><h2>บัญชี Course #{{ ledger.purchase.id }}</h2>
      <form @submit.prevent="consume"><fieldset :disabled="busy || !ledger.purchase.usable"><label>Appointment ID (ถ้ามี)<input v-model="appointmentId" type="number" min="1" /></label><label>หมายเหตุ<input v-model="note" maxlength="500" /></label><button class="primary">ใช้สิทธิ์ 1 ครั้ง</button></fieldset></form>
      <p v-if="!ledger.purchase.usable">ไม่สามารถใช้สิทธิ์ได้: ตรวจสอบยอด วันหมดอายุ และสถานะ</p>
      <label>เหตุผลการกลับรายการ / ยกเลิก<input v-model="reason" maxlength="500" :disabled="busy" /></label>
      <table><thead><tr><th>เวลา</th><th>ประเภท</th><th>จำนวน</th><th>คงเหลือ</th><th>เหตุผล / อ้างอิง</th><th>การดำเนินการ</th></tr></thead><tbody><tr v-for="movement in ledger.movements" :key="movement.id"><td>{{ movement.createdAt }}</td><td>{{ movement.type }}</td><td>{{ movement.quantity }}</td><td>{{ movement.balanceAfter }}</td><td>{{ movement.reason }} <span v-if="movement.reversesMovementId">กลับรายการ #{{ movement.reversesMovementId }}</span></td><td><button v-if="movement.type === 'USAGE' && !ledger.movements.some(m => m.reversesMovementId === movement.id)" :disabled="busy || !reason.trim()" @click="reverse(movement.id)">กลับรายการ</button></td></tr></tbody></table>
      <button v-if="ledger.purchase.status === 'ACTIVE'" :disabled="busy || !reason.trim()" @click="cancel">ยกเลิกสิทธิ์คงเหลือ (ไม่คืนเงิน)</button>
    </section>
  </section>
</template>
