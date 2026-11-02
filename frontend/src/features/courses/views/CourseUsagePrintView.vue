<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { courseApi, type CourseUsagePrintDocument } from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

type Snapshot = {
  courseNo?: string; courseName?: string; branchName?: string; branchCode?: string
  purchaseDate?: string; expiryDate?: string; totalVisits?: number; buyerName?: string; buyerHn?: string
  visitsUsed?: number; remainingVisits?: number
  history?: Array<{ sequence: number; visitDate: string; patientName?: string; therapistName?: string; quantity?: number }>
}
const route = useRoute()
const router = useRouter()
const versions = ref<CourseUsagePrintDocument[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(true)
const working = ref(false)
const error = ref('')
const notice = ref('')
const selected = computed(() => versions.value.find((item) => item.id === selectedId.value))
const snapshot = computed<Snapshot>(() => {
  try { return JSON.parse(selected.value?.documentSnapshot || '{}') as Snapshot }
  catch { return {} }
})
const date = (value?: string) => value ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeZone: 'Asia/Bangkok' }).format(new Date(value)) : '—'
const dateTime = (value: string) => new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Bangkok' }).format(new Date(value))

async function load(preferredId?: number) {
  loading.value = true
  error.value = ''
  try {
    const courseId = Number(route.params.courseId)
    if (!Number.isInteger(courseId) || courseId < 1) throw new Error('รหัสคอร์สไม่ถูกต้อง')
    versions.value = await courseApi.usagePrints(courseId)
    selectedId.value = versions.value.find((item) => item.id === preferredId)?.id ?? versions.value[0]?.id ?? null
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'โหลดใบตัดคอร์สไม่สำเร็จ'
  } finally { loading.value = false }
}

async function printCopy() {
  if (!selected.value || working.value) return
  working.value = true
  error.value = ''
  notice.value = ''
  try {
    window.print()
    const updated = await courseApi.reprintUsage(selected.value.id)
    versions.value = versions.value.map((item) => item.id === updated.id ? updated : item)
    notice.value = 'บันทึกคำสั่งพิมพ์ฉบับเดิมในประวัติแล้ว'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'ส่งพิมพ์ไม่สำเร็จ กรุณาลองจากเอกสารฉบับเดิม'
  } finally { working.value = false }
}

async function issueRevision() {
  if (!selected.value || working.value) return
  working.value = true
  error.value = ''
  notice.value = ''
  try {
    const revised = await courseApi.reviseUsagePrint(selected.value.usageId)
    await load(revised.id)
    notice.value = 'ออกใบตัดคอร์สฉบับแก้ไขแล้ว โดยเก็บฉบับเดิมไว้'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'ออกฉบับแก้ไขไม่สำเร็จ'
  } finally { working.value = false }
}
onMounted(() => void load())
</script>

<template>
  <div class="print-page">
    <StaffHeader class="screen-only" />
    <main>
      <div class="page-tools screen-only">
        <button type="button" @click="router.back()">← กลับ</button>
        <h1>ใบตัดคอร์ส</h1>
      </div>
      <p v-if="loading" role="status">กำลังโหลดเอกสาร…</p>
      <p v-else-if="error && !versions.length" role="alert">{{ error }} <button type="button" @click="load()">ลองใหม่</button></p>
      <p v-else-if="!versions.length">ยังไม่มีใบตัดคอร์สที่ออกจากการใช้สิทธิ์สำเร็จ</p>
      <div v-else class="print-layout">
        <aside class="versions screen-only">
          <h2>ฉบับเอกสาร</h2>
          <button v-for="document in versions" :key="document.id" type="button" :class="{ active: document.id === selectedId }" @click="selectedId = document.id">
            ครั้งใช้ #{{ document.usageId }} · ฉบับ {{ document.version }}
            <small>{{ dateTime(document.issuedAt) }}</small>
          </button>
          <button type="button" :disabled="working" @click="issueRevision">ออกฉบับแก้ไขหลังปรับข้อมูล</button>
        </aside>
        <div v-if="selected" class="print-content">
          <article class="print-paper">
            <header><div><h1>ใบตัดคอร์ส</h1><strong>{{ snapshot.branchName || 'สาขา ' + (snapshot.branchCode || '—') }}</strong></div>
              <div>ฉบับ {{ selected.version }}<br />ออกวันที่ {{ dateTime(selected.issuedAt) }}</div></header>
            <dl class="facts">
              <div><dt>เลขคอร์ส</dt><dd>{{ snapshot.courseNo || '—' }}</dd></div>
              <div><dt>ชื่อคอร์ส</dt><dd>{{ snapshot.courseName || '—' }}</dd></div>
              <div><dt>ผู้ซื้อ / HN</dt><dd>{{ snapshot.buyerName || '—' }} · {{ snapshot.buyerHn || '—' }}</dd></div>
              <div><dt>วันซื้อ</dt><dd>{{ date(snapshot.purchaseDate) }}</dd></div>
              <div><dt>วันหมดอายุ</dt><dd>{{ date(snapshot.expiryDate) }}</dd></div>
              <div><dt>สิทธิ์ทั้งหมด / ใช้แล้ว / คงเหลือ</dt><dd>{{ snapshot.totalVisits ?? '—' }} / {{ snapshot.visitsUsed ?? '—' }} / {{ snapshot.remainingVisits ?? '—' }}</dd></div>
            </dl>
            <h2>ประวัติการใช้สะสมถึงวันออกเอกสาร</h2>
            <div class="table-wrap"><table><thead><tr><th>ครั้งที่</th><th>วันรักษา</th><th>ผู้ใช้จริง</th><th>ผู้รักษา</th><th>จำนวน</th></tr></thead>
              <tbody><tr v-for="entry in snapshot.history || []" :key="entry.sequence"><td>{{ entry.sequence }}</td><td>{{ date(entry.visitDate) }}</td><td>{{ entry.patientName || '—' }}</td><td>{{ entry.therapistName || '—' }}</td><td>{{ entry.quantity ?? 1 }}</td></tr></tbody>
            </table></div>
          </article>
          <section class="print-history screen-only"><h2>ประวัติการพิมพ์ฉบับนี้</h2>
            <p v-for="event in selected.events" :key="event.id">{{ event.type === 'Issued' ? 'ออกฉบับ' : 'พิมพ์ซ้ำ' }} · {{ event.printedBy }} · {{ dateTime(event.printedAt) }}</p>
            <button type="button" :disabled="working" @click="printCopy">พิมพ์ฉบับนี้ซ้ำ</button>
          </section>
        </div>
      </div>
      <p v-if="error && versions.length" class="screen-only" role="alert">{{ error }}</p>
      <p v-if="notice" class="screen-only" role="status">{{ notice }}</p>
    </main>
  </div>
</template>

<style scoped>
.print-page { min-height: 100vh; background: #f8f4ed; color: #173c3b; }
main { max-width: 1200px; margin: auto; padding: 32px 20px 72px; }
.page-tools { display: flex; align-items: center; gap: 24px; margin-bottom: 24px; }
h1 { font-size: 28px; margin: 0; } h2 { font-size: 19px; margin: 0 0 16px; }
button { border: 1px solid #a4c5c0; border-radius: 8px; background: #fff; color: #146b67; padding: 10px 16px; font: inherit; cursor: pointer; }
button:focus-visible { outline: 2px solid #146b67; outline-offset: 2px; }
button:disabled { opacity: .5; cursor: not-allowed; }
.print-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 22px; }
.versions, .print-history { background: #fff; border: 1px solid #d7e5df; border-radius: 12px; padding: 18px; }
.versions button { display: block; width: 100%; text-align: left; margin-bottom: 10px; }
.versions button.active { background: #e3f2ed; border-color: #1d8580; }
.versions small { display: block; color: #526f6c; }
.print-paper { background: #fff; border: 1px solid #d7e5df; border-radius: 12px; padding: 32px; }
.print-paper header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 26px; margin-bottom: 28px; }
.facts div { border-bottom: 1px solid #e1ebe7; padding: 10px 0; } dt { color: #59736f; } dd { margin: 4px 0 0; font-weight: 600; }
.table-wrap { overflow-x: auto; } table { border-collapse: collapse; width: 100%; min-width: 560px; } th, td { border-bottom: 1px solid #e1ebe7; padding: 11px 8px; text-align: left; } th { background: #eaf3ef; }
.print-history { margin-top: 16px; } .print-history p { padding: 10px 0; border-bottom: 1px solid #e1ebe7; }
@media (max-width: 760px) { .print-layout { grid-template-columns: 1fr; } .facts { grid-template-columns: 1fr; } }
@media print { .screen-only { display: none !important; } .print-page { background: #fff; } main { max-width: none; padding: 0; } .print-layout { display: block; } .print-paper { border: 0; padding: 0; } @page { margin: 16mm; } }
</style>
