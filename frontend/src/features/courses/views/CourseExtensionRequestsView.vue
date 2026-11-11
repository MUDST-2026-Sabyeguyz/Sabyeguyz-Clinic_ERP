<script setup lang="ts">
/**
 * หน้า Manager สำหรับพิจารณาคำขอต่ออายุคอร์ส
 * วันหมดอายุเปลี่ยนหลัง API ยืนยันการอนุมัติ
 */
import { onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi, patientApi, type CourseExtensionRequest } from '@/api/client'

// เติมชื่อผู้ป่วยและคอร์สเพื่อแสดงแทน ID
type ExtensionRow = CourseExtensionRequest & {
  patientName?: string
  patientHn?: string
  courseName?: string
}

// เก็บสถานะหน้าจอและคำขอ
const filter = ref<'Pending' | 'Approved' | 'Rejected'>('Pending')
const rows = ref<ExtensionRow[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)
const error = ref('')
const decisionNotes = reactive<Record<number, string>>({})
const notice = ref('')

function displayDate(value: string | null) {
  if (!value) return '—'
  // แสดงเวลาตามเขตเวลาไทย
  return new Intl.DateTimeFormat('th-TH', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Bangkok',
  }).format(new Date(value))
}

function displayExpiry(value: string | null) {
  if (!value) return '—'
  // ยึดเที่ยงเวลาไทยเพื่อป้องกันวันที่เลื่อนจาก UTC
  return new Intl.DateTimeFormat('th-TH', {
    dateStyle: 'medium',
    timeZone: 'Asia/Bangkok',
  }).format(new Date(`${value}T12:00:00+07:00`))
}

function unitLabel(unit: CourseExtensionRequest['unit']) {
  // แปลงหน่วยจาก API เป็นคำไทย
  return ({ Day: 'วัน', Month: 'เดือน', Year: 'ปี' })[unit]
}

async function load() {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    // โหลดคำขอตามสถานะ แล้วเติมข้อมูลผู้ป่วยและคอร์ส
    const requests = await courseApi.extensions({ status: filter.value })
    const enriched = await Promise.all(
      requests.map(async (request): Promise<ExtensionRow> => {
        // ถ้าข้อมูลเสริมโหลดไม่ได้ ยังแสดงคำขอด้วย ID ได้
        const [patientResult, courseResult] = await Promise.allSettled([
          patientApi.get(request.requestedPatientId),
          courseApi.ledger(request.courseId),
        ])
        const patient = patientResult.status === 'fulfilled' ? patientResult.value : undefined
        const purchase = courseResult.status === 'fulfilled' ? courseResult.value.purchase : undefined
        return {
          ...request,
          patientName: patient?.fullName,
          patientHn: patient?.hn,
          courseName: purchase?.course?.name,
        }
      }),
    )
    rows.value = enriched
  } catch (err) {
    rows.value = []
    error.value = err instanceof Error ? err.message : 'โหลดคำขอต่ออายุไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

// โหลดครั้งแรกและโหลดใหม่เมื่อเปลี่ยนตัวกรอง
watch(filter, () => void load())
onMounted(() => void load())

async function decide(row: ExtensionRow, decision: 'approve' | 'reject') {
  // ตัดสินได้เฉพาะคำขอที่รอพิจารณา และส่งทีละรายการ
  if (busyId.value !== null || row.status !== 'Pending') return
  busyId.value = row.id
  error.value = ''
  notice.value = ''
  try {
    // ส่งหมายเหตุถ้ามี โดย backend บันทึกผลและวันหมดอายุ
    const note = decisionNotes[row.id]?.trim() || undefined
    const updated =
      decision === 'approve'
        ? await courseApi.approveExtension(row.id, note)
        : await courseApi.rejectExtension(row.id, note)
    rows.value = rows.value.map((item) => (item.id === row.id ? { ...item, ...updated } : item))
    // อัปเดตเฉพาะรายการที่ตัดสินแล้ว
    notice.value = decision === 'approve' ? 'อนุมัติคำขอแล้วและบันทึกวันหมดอายุใหม่แล้ว' : 'ปฏิเสธคำขอแล้ว'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'บันทึกผลพิจารณาไม่สำเร็จ'
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <div class="course-extensions-page">
    <StaffHeader />
    <main>
      <RouterLink to="/staff/courses">← กลับหน้าคอร์ส</RouterLink>
      <!-- กรองรายการและแสดงปุ่มเฉพาะคำขอที่รอพิจารณา -->
      <div class="page-heading">
        <div>
          <h1>คำขอต่ออายุคอร์ส</h1>
          <p>การอนุมัติจะเปลี่ยนวันหมดอายุของคอร์สและสิทธิ์ที่แชร์ตาม workflow ของระบบ</p>
        </div>
        <label>
          สถานะ
          <select v-model="filter" aria-label="กรองสถานะคำขอ">
            <option value="Pending">รอพิจารณา</option>
            <option value="Approved">อนุมัติแล้ว</option>
            <option value="Rejected">ไม่อนุมัติ</option>
          </select>
        </label>
      </div>

      <!-- แยกข้อความผิดพลาดจากผลการทำรายการ -->
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <p v-if="loading" role="status">กำลังโหลดคำขอ…</p>
      <section v-else-if="!rows.length" class="empty-state">
        <h2>ไม่มีคำขอ{{ filter === 'Pending' ? 'ที่รอพิจารณา' : 'ในสถานะนี้' }}</h2>
        <button type="button" @click="load">โหลดอีกครั้ง</button>
      </section>
      <section v-else class="request-list" aria-label="รายการคำขอต่ออายุคอร์ส">
        <article v-for="row in rows" :key="row.id" class="request-card">
          <header>
            <div>
              <h2>{{ row.courseName || `คอร์ส #${row.courseId}` }}</h2>
              <p>
                ผู้ป่วย {{ row.patientName || `#${row.requestedPatientId}` }}
                <span v-if="row.patientHn">· HN {{ row.patientHn }}</span>
              </p>
            </div>
            <span class="status">{{ row.status }}</span>
          </header>
          <dl>
            <div><dt>ระยะเวลาที่ขอ</dt><dd>เพิ่ม {{ row.value }} {{ unitLabel(row.unit) }}</dd></div>
            <div><dt>ส่งคำขอเมื่อ</dt><dd>{{ displayDate(row.requestedAt) }}</dd></div>
            <div v-if="row.oldExpiry || row.newExpiry">
              <dt>วันหมดอายุ</dt>
              <dd>{{ displayExpiry(row.oldExpiry) }} → {{ displayExpiry(row.newExpiry) }}</dd>
            </div>
          </dl>
          <p v-if="row.reason"><strong>เหตุผล:</strong> {{ row.reason }}</p>
          <p v-if="row.decisionNote"><strong>หมายเหตุการพิจารณา:</strong> {{ row.decisionNote }}</p>
          <!-- ปิดช่องกรอกและปุ่มระหว่างบันทึก เพื่อกันการส่งซ้ำ -->
          <label v-if="row.status === 'Pending'" class="decision-note">
            หมายเหตุ Manager (ถ้ามี)
            <textarea v-model="decisionNotes[row.id]" rows="2" maxlength="255" :disabled="busyId !== null" />
          </label>
          <footer v-if="row.status === 'Pending'">
            <button type="button" :disabled="busyId !== null" @click="decide(row, 'reject')">
              {{ busyId === row.id ? 'กำลังบันทึก…' : 'ปฏิเสธ' }}
            </button>
            <button class="approve" type="button" :disabled="busyId !== null" @click="decide(row, 'approve')">
              {{ busyId === row.id ? 'กำลังบันทึก…' : 'อนุมัติ' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.course-extensions-page { min-height: 100vh; background: #f4f7f5; color: #23382f; }
main { width: min(1000px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 56px; }
a { color: var(--color-primary-strong); font-weight: 600; text-decoration: none; }
.page-heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; margin: 28px 0; }
h1 { margin: 0 0 8px; color: var(--color-primary-strong); }
.page-heading p { margin: 0; color: #52665e; }
label { display: grid; gap: 8px; font-weight: 600; }
select, textarea, button { font: inherit; }
select, textarea { border: 1px solid #b7cecc; border-radius: 8px; padding: 10px 12px; background: white; }
.request-list { display: grid; gap: 16px; }
.request-card, .empty-state { padding: 22px; border: 1px solid #d7e2dc; border-radius: 12px; background: white; }
.request-card header, footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.request-card h2 { margin: 0 0 4px; font-size: 19px; }
.request-card p { margin: 12px 0; }
.request-card header p { margin: 0; color: #52665e; }
.status { padding: 5px 10px; border-radius: 999px; background: #edf5f1; }
dl { display: flex; flex-wrap: wrap; gap: 16px 28px; margin: 20px 0; }
dl div { min-width: 170px; }
dt { color: #65766e; font-size: 13px; }
dd { margin: 4px 0 0; font-weight: 600; }
.decision-note { margin: 18px 0; }
button { min-height: 42px; padding: 8px 14px; border: 1px solid #b7cecc; border-radius: 8px; background: white; color: var(--color-primary-strong); cursor: pointer; }
button.approve { background: var(--color-primary-strong); color: white; }
button:disabled { opacity: .6; cursor: not-allowed; }
.error { color: #b42318; }
.notice { color: var(--color-primary-strong); font-weight: 600; }
.empty-state { text-align: center; }
@media (max-width: 640px) { .page-heading { align-items: stretch; flex-direction: column; } .request-card header { align-items: flex-start; flex-direction: column; } }
</style>
