<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { courseApi, type CourseUsagePrintDocument } from '@/api/client'

const props = defineProps<{ courseId: number }>()
const documents = ref<CourseUsagePrintDocument[]>([])
const selectedId = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const selected = computed(() => documents.value.find(document => document.id === selectedId.value))
type Snapshot = {
  courseNo?: string; courseName?: string; issuedAt?: string; purchaseDate?: string; totalVisits?: number;
  visitsUsed?: number; remainingVisits?: number; expiryDate?: string;
  buyer?: { id: number; hn: string; name: string };
  branch?: { code: string; name: string; address: string; phone: string; email: string; licenseNo: string };
  history?: Array<{ sequence: number; visitDate: string; patientName: string; patientHn: string; treatingStaffName: string; quantity: number; status: string }>;
  movements?: Array<{ id: number; type: string; quantity: number; balanceAfter: number; patientName: string; patientHn: string; occurredAt: string; reason: string; reversesLedgerId: number | null }>;
}
const snapshot = computed<Snapshot>(() => {
  try { return selected.value ? JSON.parse(selected.value.documentSnapshot) as Snapshot : {} }
  catch { return {} }
})
async function load() {
  loading.value = true; error.value = ''
  try {
    documents.value = await courseApi.usagePrints(props.courseId)
    if (!documents.value.some(document => document.id === selectedId.value)) selectedId.value = documents.value[0]?.id ?? 0
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'โหลดฉบับใบตัดคอร์สไม่สำเร็จ' }
  finally { loading.value = false }
}
async function reprint() {
  if (!selected.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    const original = selected.value
    const updated = await courseApi.reprintUsage(original.id)
    documents.value = documents.value.map(document => document.id === updated.id ? updated : document)
    await nextTick()
    window.print()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'บันทึกหรือเปิดกล่องพิมพ์ไม่สำเร็จ' }
  finally { busy.value = false }
}
async function revise() {
  if (!selected.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    const revised = await courseApi.reviseUsagePrint(selected.value.usageId)
    documents.value = [revised, ...documents.value]
    selectedId.value = revised.id
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'ออกฉบับแก้ไขไม่สำเร็จ' }
  finally { busy.value = false }
}
watch(() => props.courseId, load)
onMounted(load)
</script>
<template>
  <section class="frozen-print-library" aria-label="ฉบับใบตัดคอร์สและประวัติพิมพ์">
    <h2>ใบตัดคอร์สที่ออกแล้ว</h2>
    <p>เลือกฉบับเดิมเพื่อดูหรือพิมพ์ซ้ำ ข้อมูลในฉบับเดิมจะไม่เปลี่ยนตามข้อมูลปัจจุบัน</p>
    <p v-if="loading" role="status">กำลังโหลดฉบับเอกสาร…</p><p v-if="error" role="alert">{{ error }}</p>
    <p v-if="!loading && !documents.length">ยังไม่มีใบตัดคอร์สจากการใช้สิทธิ์ที่สำเร็จ</p>
    <label v-if="documents.length" class="print-controls">ฉบับเอกสาร
      <select v-model="selectedId"><option v-for="document in documents" :key="document.id" :value="document.id">รายการใช้ #{{ document.usageId }} · ฉบับ {{ document.version }} · {{ document.issuedAt }}</option></select>
    </label>
    <article v-if="selected" class="frozen-course-document">
      <h2>ใบตัดคอร์ส {{ snapshot.courseNo || selected.courseId }} · ฉบับ {{ selected.version }}</h2>
      <p>{{ snapshot.branch?.name || 'ไม่ระบุสาขาในฉบับนี้' }} · {{ snapshot.branch?.address }} · {{ snapshot.branch?.phone }}</p>
      <p>วันออก {{ snapshot.issuedAt || selected.issuedAt }} · ผู้ซื้อ {{ snapshot.buyer?.name || 'ไม่ระบุในฉบับนี้' }} · HN {{ snapshot.buyer?.hn || '—' }}</p>
      <p>{{ snapshot.courseName }} · ซื้อ {{ snapshot.purchaseDate || '—' }} · หมดอายุ {{ snapshot.expiryDate || '—' }}</p>
      <p>จำนวนรวม {{ snapshot.totalVisits ?? '—' }} · ใช้แล้ว {{ snapshot.visitsUsed }} · คงเหลือ {{ snapshot.remainingVisits }}</p>
      <h3>ประวัติการรักษาถึงวันออกฉบับนี้</h3>
      <table><thead><tr><th>ครั้ง</th><th>วันรักษา</th><th>ผู้ใช้จริง / HN</th><th>ผู้รักษา</th><th>จำนวน</th><th>สถานะ</th></tr></thead>
        <tbody><tr v-for="(entry, index) in snapshot.history || []" :key="index"><td>{{ entry.sequence }}</td><td>{{ entry.visitDate }}</td><td>{{ entry.patientName }} / {{ entry.patientHn }}</td><td>{{ entry.treatingStaffName }}</td><td>{{ entry.quantity }}</td><td>{{ entry.status }}</td></tr></tbody>
      </table>
      <h3>ประวัติสิทธิ์ที่อธิบายยอด</h3>
      <table><thead><tr><th>วันเวลา</th><th>ประเภท</th><th>ผู้ถือสิทธิ์</th><th>จำนวนเปลี่ยน</th><th>คงเหลือ</th><th>เหตุผล / ต้นทางย้อน</th></tr></thead>
        <tbody><tr v-for="entry in snapshot.movements || []" :key="entry.id"><td>{{ entry.occurredAt }}</td><td>{{ entry.type }}</td><td>{{ entry.patientName }} / {{ entry.patientHn }}</td><td>{{ entry.quantity }}</td><td>{{ entry.balanceAfter }}</td><td>{{ entry.reason }} {{ entry.reversesLedgerId ? `ย้อน #${entry.reversesLedgerId}` : '' }}</td></tr></tbody>
      </table>
    </article>
    <div v-if="selected" class="print-controls"><button :disabled="busy" @click="reprint">บันทึกและพิมพ์ฉบับนี้ซ้ำ</button><button :disabled="busy" @click="revise">ออกฉบับแก้ไขจากรายการใช้นี้</button>
      <h3>ประวัติการออกและพิมพ์ฉบับนี้</h3><ul><li v-for="event in selected.events" :key="event.id">{{ event.type === 'Issued' ? 'ออกฉบับ' : 'พิมพ์ซ้ำ' }} · {{ event.printedAt }} · ผู้ดำเนินการ {{ event.printedBy }}</li></ul>
      <p>การเปิดกล่องพิมพ์ไม่ยืนยันว่าเครื่องพิมพ์พิมพ์สำเร็จ</p>
    </div>
  </section>
</template>
<style scoped>
table { border-collapse: collapse; width: 100%; } th,td { border-bottom: 1px solid #ddd; padding: 8px; text-align: left; }
.frozen-course-document { padding: 20px; background: white; margin: 16px 0; overflow-x: auto; }
button,select { padding: 10px; margin: 8px; } [role='alert'] { color: #a21d28; }
@media print { .print-controls,.frozen-print-library>h2,.frozen-print-library>p { display:none; } }
</style>
