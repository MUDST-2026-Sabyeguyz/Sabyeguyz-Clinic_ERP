<script setup lang="ts">
/**
 * หน้า Manager สำหรับพิจารณาคำขอต่ออายุคอร์ส
 * วันหมดอายุเปลี่ยนหลัง API ยืนยันการอนุมัติ
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { vModalFocus } from '@/features/appointments/modalFocus'
import { courseApi, patientApi, type CourseExtensionRequest } from '@/api/client'

// เติมชื่อผู้ป่วยและคอร์สเพื่อแสดงแทน ID
type ExtensionRow = CourseExtensionRequest & {
  patientName?: string
  patientHn?: string
  courseName?: string
  courseCode?: string
}
const route = useRoute()
const patientId = computed(() => {
  const id = Number(route.query.patientId)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
})
const search = ref('')
const page = ref(1)
const pageSize = 10

// เก็บสถานะหน้าจอและคำขอ
const filter = ref<'Pending' | 'Approved' | 'Rejected'>('Pending')
const statusLabels = {
  Pending: 'รอพิจารณา',
  Approved: 'อนุมัติแล้ว',
  Rejected: 'ไม่อนุมัติ',
} as const
const statusOptions = Object.entries(statusLabels).map(([value, label]) => ({ value, label }))
function selectStatus(value: string) {
  if (value === 'Pending' || value === 'Approved' || value === 'Rejected') filter.value = value
}
const rows = ref<ExtensionRow[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)
const confirmation = ref<{ row: ExtensionRow; decision: 'approve' | 'reject' }>()
function openConfirmation(row: ExtensionRow, decision: 'approve' | 'reject') {
  if (busyId.value === null && row.status === 'Pending') confirmation.value = { row, decision }
}
function closeConfirmation() {
  if (busyId.value === null) confirmation.value = undefined
}
async function confirmDecision() {
  if (!confirmation.value || busyId.value !== null) return
  const { row, decision } = confirmation.value
  await decide(row, decision)
  confirmation.value = undefined
}
const error = ref('')
const decisionNotes = reactive<Record<number, string>>({})
const matchingRows = computed(() => {
  const query = search.value.trim().toLocaleLowerCase('th')
  return rows.value.filter((row) =>
    row.status === filter.value &&
    (!query || [row.patientName, row.patientHn, row.courseName, row.courseCode, row.courseId]
      .some((text) => String(text ?? '').toLocaleLowerCase('th').includes(query))),
  )
})
const totalPages = computed(() => Math.max(1, Math.ceil(matchingRows.value.length / pageSize)))
const displayedRows = computed(() => matchingRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(search, () => { page.value = 1 })
watch(totalPages, (total) => { page.value = Math.min(page.value, total) })
let loadVersion = 0

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
  const version = ++loadVersion
  const requestedPatientId = patientId.value
  const status = filter.value
  loading.value = true
  error.value = ''
  try {
    // โหลดคำขอตามสถานะ แล้วเติมข้อมูลผู้ป่วยและคอร์ส
    const allRequests = await courseApi.extensions({ status })
    const requests = requestedPatientId
      ? allRequests.filter((request) => request.requestedPatientId === requestedPatientId)
      : allRequests
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
          courseCode: purchase?.course?.code,
        }
      }),
    )
    if (version === loadVersion) {
      rows.value = enriched
      page.value = 1
    }
  } catch (err) {
    if (version !== loadVersion) return
    rows.value = []
    error.value = err instanceof Error ? err.message : 'โหลดคำขอต่ออายุไม่สำเร็จ'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

// โหลดครั้งแรกและโหลดใหม่เมื่อเปลี่ยนตัวกรอง
watch([filter, patientId], () => void load(), { immediate: true })

async function decide(row: ExtensionRow, decision: 'approve' | 'reject') {
  // ตัดสินได้เฉพาะคำขอที่รอพิจารณา และส่งทีละรายการ
  if (busyId.value !== null || row.status !== 'Pending') return
  busyId.value = row.id
  error.value = ''
  try {
    // ส่งหมายเหตุถ้ามี โดย backend บันทึกผลและวันหมดอายุ
    const note = decisionNotes[row.id]?.trim() || undefined
    const updated =
      decision === 'approve'
        ? await courseApi.approveExtension(row.id, note)
        : await courseApi.rejectExtension(row.id, note)
    rows.value = rows.value.map((item) => (item.id === row.id ? { ...item, ...updated } : item))
    // อัปเดตเฉพาะรายการที่ตัดสินแล้ว
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
      <RouterLink :to="patientId ? { path: '/staff/courses', query: { patientId } } : { path: '/staff/patients' }">
        {{ patientId ? '← กลับหน้าคอร์ส' : '← กลับหน้าผู้ป่วย' }}
      </RouterLink>
      <!-- กรองรายการและแสดงปุ่มเฉพาะคำขอที่รอพิจารณา -->
      <div class="page-heading">
        <div>
          <h1>{{ patientId ? 'คำขอของผู้ป่วยรายนี้' : 'คำขอต่ออายุคอร์สทั้งหมด' }}</h1>
          <p>{{ patientId ? 'แสดงเฉพาะคำขอของผู้ป่วยที่เลือก' : 'ตรวจและพิจารณาคำขอของผู้ป่วยทุกคน' }}</p>
        </div>
        <div class="request-filter-actions">
          <div class="status-filter">
          <span>กรองตามสถานะคำขอ</span>
          <FilterDropdown
            :model-value="filter"
            :options="statusOptions"
            label="กรองสถานะคำขอ"
            default-label="รอพิจารณา"
            hide-default-option
            :clear-on-reselect="false"
            :disabled="busyId !== null"
            @update:model-value="selectStatus"
          />
          </div>
          <button type="button" :disabled="loading || busyId !== null" @click="load">รีเฟรช ↻</button>
        </div>
      </div>
      <label class="request-search">
        ค้นหาคำขอ
        <input v-model="search" type="search" placeholder="ชื่อผู้ป่วย / HN / ชื่อหรือรหัสคอร์ส" />
      </label>

      <!-- แยกข้อความผิดพลาดจากผลการทำรายการ -->
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="loading" role="status">กำลังโหลดคำขอ…</p>
      <section v-else-if="!matchingRows.length" class="empty-state">
        <h2>ไม่พบคำขอ</h2>
      </section>
      <section v-else class="request-list" aria-label="รายการคำขอต่ออายุคอร์ส">
        <p>ทั้งหมด {{ matchingRows.length }} รายการ</p>
        <article v-for="row in displayedRows" :key="row.id" class="request-card">
          <header>
            <div>
              <h2>{{ row.courseName || `คอร์ส #${row.courseId}` }}</h2>
              <p v-if="row.courseCode">รหัสคอร์ส {{ row.courseCode }}</p>
              <p>
                ผู้ป่วย {{ row.patientName || `#${row.requestedPatientId}` }}
                <span v-if="row.patientHn">· HN {{ row.patientHn }}</span>
              </p>
            </div>
            <span class="request-status" :class="row.status.toLowerCase()">{{ statusLabels[row.status] }}</span>
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
            หมายเหตุผู้พิจารณา (ถ้ามี)
            <textarea v-model="decisionNotes[row.id]" rows="2" maxlength="255" :disabled="busyId !== null" />
          </label>
          <footer v-if="row.status === 'Pending'">
            <button type="button" :disabled="busyId !== null" @click="openConfirmation(row, 'reject')">
              {{ busyId === row.id ? 'กำลังบันทึก…' : 'ปฏิเสธ' }}
            </button>
            <button class="approve" type="button" :disabled="busyId !== null" @click="openConfirmation(row, 'approve')">
              {{ busyId === row.id ? 'กำลังบันทึก…' : 'อนุมัติ' }}
            </button>
          </footer>
        </article>
      </section>
      <nav v-if="!loading && matchingRows.length" class="request-pagination" aria-label="เปลี่ยนหน้าคำขอ">
        <button type="button" :disabled="page === 1" @click="page--">← ก่อนหน้า</button>
        <span>หน้า {{ page }} จาก {{ totalPages }}</span>
        <button type="button" :disabled="page === totalPages" @click="page++">ถัดไป →</button>
      </nav>
    </main>
    <Teleport to="body">
      <div v-if="confirmation" class="decision-backdrop" @click.self="closeConfirmation">
        <section
          v-modal-focus="{ close: closeConfirmation, busy: busyId !== null }"
          class="decision-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="extension-decision-title"
        >
          <h2 id="extension-decision-title">
            {{ confirmation.decision === 'approve' ? 'ยืนยันอนุมัติคำขอ?' : 'ยืนยันปฏิเสธคำขอ?' }}
          </h2>
          <footer>
            <button type="button" :disabled="busyId !== null" @click="closeConfirmation">ยกเลิก</button>
            <button class="approve decision-confirm" type="button" :disabled="busyId !== null" @click="confirmDecision">
              {{ busyId !== null ? 'กำลังบันทึก…' : 'ยืนยัน' }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.decision-backdrop {
  position: fixed; inset: 0; z-index: 1100; display: grid; place-items: center;
  padding: 20px; background: #173f4459;
}
.decision-dialog {
  box-sizing: border-box; width: min(440px, 100%); max-height: calc(100dvh - 40px);
  overflow-y: auto; padding: 24px; border-radius: 16px; background: #fff; color: #23382f;
  box-shadow: 0 18px 45px #173f4438;
}
.decision-dialog h2 { margin: 0 0 18px; font-size: 22px; }
.decision-dialog footer { margin-top: 20px; }
.decision-dialog { text-align: center; }
.decision-dialog footer { justify-content: center; }
.request-search { display: grid; gap: 8px; margin: 0 0 20px; font-size: 14px; font-weight: 600; }
.request-search input { width: 100%; box-sizing: border-box; padding: 12px 14px; border: 1px solid #b7cecc; border-radius: 8px; background: #fff; font: inherit; }
.request-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 20px; }
.course-extensions-page { min-height: 100vh; background: #f4f7f5; color: #23382f; }
main { width: min(1000px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 56px; }
a { color: var(--color-primary-strong); font-weight: 600; text-decoration: none; }
.page-heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; margin: 28px 0; }
h1 { margin: 0 0 8px; color: var(--color-primary-strong); }
.page-heading p { margin: 0; color: #52665e; }
.status-filter { width: min(100%, 220px); display: grid; gap: 6px; }
.request-filter-actions { display: flex; align-items: flex-end; flex-wrap: nowrap; gap: 10px; flex: 0 0 auto; }
.request-filter-actions .status-filter { flex: 0 1 220px; width: 220px; min-width: 0; }
.request-filter-actions > button { min-height: 46px; }
.status-filter > span, .decision-note { font-size: 14px; font-weight: 600; }
textarea, button { font: inherit; }
textarea { border: 1px solid #b7cecc; border-radius: 8px; padding: 10px 12px; background: #f8faf9; resize: vertical; }
.request-list { display: grid; gap: 16px; }
.request-card, .empty-state { padding: clamp(16px, 3vw, 24px); border: 1px solid #d7e2dc; border-radius: 14px; background: white; }
.request-card header, footer { display: flex; align-items: center; gap: 12px; }
.request-card header { justify-content: space-between; }
.request-card h2 { margin: 0 0 4px; font-size: 19px; }
.request-card p { margin: 12px 0; overflow-wrap: anywhere; }
.request-card header p { margin: 0; color: #52665e; }
.request-status { padding: 5px 12px; border-radius: 999px; font-size: 13px; font-weight: 700; white-space: nowrap; }
.request-status.pending { background: #fff2cc; color: #76500a; }
.request-status.approved { background: #def4e9; color: #205e46; }
.request-status.rejected { background: #fce7e5; color: #952e29; }
dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr)); gap: 10px; margin: 18px 0; }
dl div { padding: 12px 14px; border-radius: 8px; background: #edf5f1; }
dt { color: #65766e; font-size: 13px; }
dd { margin: 4px 0 0; font-weight: 600; }
.decision-note { display: grid; gap: 8px; margin: 18px 0 14px; }
footer { justify-content: flex-end; flex-wrap: wrap; }
button { min-height: 44px; padding: 8px 18px; border: 1px solid #b7cecc; border-radius: 8px; background: white; color: var(--color-primary-strong); cursor: pointer; font-weight: 600; }
button.approve { border-color: var(--color-primary-strong); background: var(--color-primary-strong); color: white; }
button:focus-visible, textarea:focus-visible, a:focus-visible { outline: 2px solid var(--color-primary-strong); outline-offset: 2px; }
button:disabled { opacity: .6; cursor: not-allowed; }
.error { color: #b42318; }
.notice { color: var(--color-primary-strong); font-weight: 600; }
.empty-state { text-align: center; }
.empty-state h2 { margin: 0 0 12px; font-size: 20px; }
@media (max-width: 640px) {
  .request-filter-actions { flex-wrap: wrap; }
  main { width: calc(100% - 32px); }
  .page-heading { align-items: stretch; flex-direction: column; }
  .request-card header { align-items: flex-start; flex-wrap: wrap; }
}
</style>
