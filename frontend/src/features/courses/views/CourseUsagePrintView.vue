<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { courseApi, type CourseUsagePrintDocument } from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CourseCutDocument from '../components/CourseCutDocument.vue'

type Snapshot = {
  courseNo?: string; courseName?: string; branchName?: string; branchCode?: string
  purchaseDate?: string; expiryDate?: string; totalVisits?: number; buyerName?: string; buyerHn?: string
  visitsUsed?: number; remainingVisits?: number
  history?: Array<{ sequence: number; visitDate: string; patientName?: string; therapistName?: string; branchName?: string; branchCode?: string; quantity?: number }>
}
const route = useRoute()
const router = useRouter()
const selected = ref<CourseUsagePrintDocument>()
const loading = ref(true)
const working = ref(false)
const error = ref('')
const notice = ref('')
const snapshot = computed<Snapshot>(() => {
  try { return JSON.parse(selected.value?.documentSnapshot || '{}') as Snapshot }
  catch { return {} }
})
const dateTime = (value: string) => new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Bangkok' }).format(new Date(value))

function goBack() {
  const usage = route.query.usage
  if (typeof usage === 'string' && /^usage-\d+$/.test(usage)) {
    void router.replace({ name: 'staff-transactions', query: { usage } })
    return
  }
  router.back()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const courseId = Number(route.params.courseId)
    if (!Number.isInteger(courseId) || courseId < 1) throw new Error('รหัสคอร์สไม่ถูกต้อง')
    const documents = await courseApi.usagePrints(courseId)
    selected.value = [...documents].sort(
      (a, b) => Date.parse(b.issuedAt) - Date.parse(a.issuedAt) || b.id - a.id,
    )[0]
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
    selected.value = await courseApi.reprintUsage(selected.value.id)
    notice.value = 'บันทึกคำสั่งพิมพ์ในประวัติแล้ว'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'ส่งพิมพ์ไม่สำเร็จ กรุณาลองจากเอกสารฉบับเดิม'
  } finally { working.value = false }
}

onMounted(() => void load())
</script>

<template>
  <div class="print-page">
    <div class="screen-only">
      <StaffHeader />
    </div>
    <main>
      <div class="page-tools screen-only">
        <div>
          <button class="back-link" type="button" @click="goBack">← กลับ</button>
          <h1>ใบตัดคอร์ส</h1>
          <p v-if="selected && !loading" class="document-note">ข้อมูล ณ วันที่ออกเอกสาร</p>
        </div>
        <button
          v-if="selected && !loading"
          class="print-button"
          type="button"
          :disabled="working"
          @click="printCopy"
        >
          พิมพ์ใบตัดคอร์สซ้ำ
        </button>
      </div>
      <p v-if="error && selected" class="feedback screen-only" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback screen-only" role="status">{{ notice }}</p>
      <p v-if="loading" role="status">กำลังโหลดเอกสาร…</p>
      <p v-else-if="error && !selected" role="alert">{{ error }} <button type="button" @click="load()">ลองใหม่</button></p>
      <p v-else-if="!selected">ยังไม่มีใบตัดคอร์สที่ออกจากการใช้สิทธิ์สำเร็จ</p>
        <div v-else class="print-content">
          <CourseCutDocument
            :course-no="snapshot.courseNo"
            :course-name="snapshot.courseName"
            :branch-name="snapshot.branchName || snapshot.branchCode"
            :buyer="[snapshot.buyerName, snapshot.buyerHn].filter(Boolean).join(' · ')"
            :issued-at="selected.issuedAt"
            :purchased-at="snapshot.purchaseDate"
            :expires-at="snapshot.expiryDate"
            :total="snapshot.totalVisits"
            :used="snapshot.visitsUsed"
            :remaining="snapshot.remainingVisits"
            :entries="(snapshot.history ?? []).map((entry) => ({
              date: entry.visitDate,
              patient: entry.patientName,
              therapist: entry.therapistName,
              branch: entry.branchName || entry.branchCode,
              quantity: entry.quantity,
            }))"
            show-patient
          />
          <section class="print-history screen-only" aria-labelledby="print-history-title">
            <h2 id="print-history-title">ประวัติการพิมพ์</h2>
            <ul v-if="selected.events.length">
              <li v-for="event in selected.events" :key="event.id">
                <div>
                  <strong>{{ event.type === 'Issued' ? 'ออกเอกสาร' : 'พิมพ์ซ้ำ' }}</strong>
                  <span>{{ event.printedBy }}</span>
                </div>
                <time :datetime="event.printedAt">{{ dateTime(event.printedAt) }}</time>
              </li>
            </ul>
            <p v-else>ยังไม่มีประวัติการพิมพ์</p>
          </section>
        </div>
    </main>
  </div>
</template>

<style scoped>
.print-page { min-height: 100vh; background: #f8f4ed; color: #173c3b; }
main { max-width: 1060px; margin: auto; padding: 32px 20px 72px; }
.page-tools {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin-bottom: 20px;
}
h1 { font-size: 28px; margin: 6px 0 0; }
.document-note { margin-top: 4px; color: #526f6c; font-size: 14px; }
h2 { font-size: 19px; margin: 0 0 12px; }
button { border: 1px solid #a4c5c0; border-radius: 8px; background: #fff; color: #146b67; padding: 10px 16px; font: inherit; cursor: pointer; }
button:focus-visible { outline: 2px solid #146b67; outline-offset: 2px; }
button:disabled { opacity: .5; cursor: not-allowed; }
.back-link { padding: 4px 0; border: 0; background: transparent; }
.print-button { background: #1f8883; border-color: #1f8883; color: #fff; font-weight: 700; }
.feedback { margin-bottom: 16px; }
.print-history {
  margin-top: 20px;
  padding: 18px 20px;
  border: 1px solid #d7e5df;
  border-radius: 12px;
  background: #fff;
}
.print-history ul { padding: 0; list-style: none; }
.print-history li {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 16px;
  padding: 12px 0;
  border-top: 1px solid #e1ebe7;
}
.print-history li div { display: flex; flex-wrap: wrap; gap: 4px 12px; }
.print-history span, .print-history time { color: #526f6c; }
@media print {
  @page course-usage-sheet {
    size: A4 portrait;
    margin: 0;
  }

  .screen-only {
    display: none !important;
  }

  .print-page {
    page: course-usage-sheet;
    min-height: 0;
    background: #fff;
  }

  main {
    max-width: none;
    padding: 0;
  }

  .print-content {
    max-width: none;
    overflow: visible;
  }

  .print-content > :deep(.course-cut-document) {
    padding: 16mm;
    box-decoration-break: clone;
    -webkit-box-decoration-break: clone;
  }
}
</style>
