<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

type Appointment = {
  id: number
  date: string
  time: string
  name: string
  service: string
  therapist: string
  branch: string
  room: string
  status: string
}

type ClinicBranch = {
  name: string
  status: string
}

type ClinicRoom = ClinicBranch & {
  branch: string
}

const DEFAULT_APPOINTMENT: Appointment = {
  id: 0,
  date: '2026-08-20',
  time: '14:00',
  name: 'สมชาย ใจดี',
  service: 'Manual Therapy',
  therapist: 'กฤษดา น.',
  branch: 'สาขาราชพฤกษ์',
  room: 'ห้องรักษา 2',
  status: 'ยืนยันแล้ว',
}

const PATIENT_OPTIONS = [
  { hn: '2601080001', name: 'สมชาย ใจดี' },
  { hn: '2601080012', name: 'กนกวรรณ พูนใจ' },
  { hn: '2601080007', name: 'วรรณา มั่นคง' },
]
const SERVICE_OPTIONS = ['Manual Therapy', 'Exercise Therapy', 'Shockwave Therapy']
const THERAPIST_OPTIONS = ['กฤษดา น.', 'ศิริพร ว.']
const TIME_OPTIONS = [
  { value: '09:00', label: '09:00 – 10:00' },
  { value: '10:00', label: '10:00 – 11:00' },
  { value: '11:00', label: '11:00 – 12:00' },
  { value: '14:00', label: '14:00 – 15:00' },
]
const STATUS_OPTIONS = [
  'ยืนยันแล้ว',
  'มาถึงคลินิก',
  'กำลังรับบริการ',
  'เสร็จสิ้น',
  'ยกเลิก',
  'เลื่อนนัด',
  'ไม่มาตามนัด',
]

const configuredBranches = JSON.parse(
  localStorage.getItem('clinicBranches') || '[]',
) as ClinicBranch[]
const activeBranches = configuredBranches.filter((item) => item.status === 'Active')
const branchOptions = activeBranches.length
  ? activeBranches.map((item) => item.name)
  : ['สาขาราชพฤกษ์', 'สาขาสุขุมวิท']
const configuredRooms = JSON.parse(localStorage.getItem('clinicRooms') || '[]') as ClinicRoom[]

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const query = ref('')
const selectedBranch = ref('ทั้งหมด')
const sortOrder = ref<'asc' | 'desc'>('asc')
const currentPage = ref(1)
const perPage = 10
const editingId = ref<number | null>(null)
const defaultAppointments: Appointment[] = [
  {
    id: 1,
    date: '2026-08-20',
    time: '09:00',
    name: 'สมชาย ใจดี',
    service: 'Manual Therapy',
    therapist: 'กฤษดา น.',
    branch: 'สาขาราชพฤกษ์',
    room: 'ห้องรักษา 1',
    status: 'ยืนยันแล้ว',
  },
  {
    id: 2,
    date: '2026-08-20',
    time: '10:00',
    name: 'กนกวรรณ พูนใจ',
    service: 'Exercise Therapy',
    therapist: 'กฤษดา น.',
    branch: 'สาขาราชพฤกษ์',
    room: 'ห้องรักษา 2',
    status: 'กำลังรับบริการ',
  },
  {
    id: 3,
    date: '2026-08-20',
    time: '11:00',
    name: 'วรรณา มั่นคง',
    service: 'Shockwave Therapy',
    therapist: 'ศิริพร ว.',
    branch: 'สาขาสุขุมวิท',
    room: 'ห้องรักษา 3',
    status: 'เสร็จสิ้น',
  },
]
const paginationDemoAppointments: Appointment[] = [
  {
    id: 11,
    date: '2026-08-21',
    time: '09:00',
    name: 'กนกวรรณ พูนใจ',
    service: 'Manual Therapy',
    therapist: 'กฤษดา น.',
    branch: 'สาขาราชพฤกษ์',
    room: 'ห้องรักษา 1',
    status: 'ยืนยันแล้ว',
  },
  {
    id: 12,
    date: '2026-08-21',
    time: '10:00',
    name: 'วรรณา มั่นคง',
    service: 'Exercise Therapy',
    therapist: 'ศิริพร ว.',
    branch: 'สาขาสุขุมวิท',
    room: 'ห้องรักษา 3',
    status: 'ยืนยันแล้ว',
  },
]
const legacyStatusLabels: Record<string, string> = {
  'ยืนยันแล้ว (Confirmed)': 'ยืนยันแล้ว',
  'มาถึงคลินิก (Arrived)': 'มาถึงคลินิก',
  'กำลังรับบริการ (In Service)': 'กำลังรับบริการ',
  'เสร็จสิ้น (Completed)': 'เสร็จสิ้น',
  'ยกเลิก (Cancelled)': 'ยกเลิก',
  'เลื่อนนัด (Rescheduled)': 'เลื่อนนัด',
  'ไม่มาตามนัด (No Show)': 'ไม่มาตามนัด',
}
const savedAppointments = JSON.parse(localStorage.getItem('clinicAppointments') || 'null') as
  Appointment[] | null
const appointments = ref<Appointment[]>(
  [
    ...(savedAppointments ?? defaultAppointments),
    ...paginationDemoAppointments.filter(
      (demo) => !(savedAppointments ?? defaultAppointments).some((item) => item.id === demo.id),
    ),
  ].map((item) => ({
    ...item,
    status: legacyStatusLabels[item.status] ?? item.status,
  })),
)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const saveNotice = ref('')
const pendingCancellation = ref<{ item: Appointment; status: string } | null>(null)
const cancellationReason = ref('')

const form = ref<Appointment>({ ...DEFAULT_APPOINTMENT })

const isEditing = computed(() => viewMode.value === 'edit')
const selectedPatientHn = computed(
  () => PATIENT_OPTIONS.find((patient) => patient.name === form.value.name)?.hn ?? '',
)
function patientHn(name: string) {
  return PATIENT_OPTIONS.find((patient) => patient.name === name)?.hn ?? '-'
}
function timeLabel(value: string) {
  return TIME_OPTIONS.find((time) => time.value === value)?.label ?? value
}
const filteredAppointments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return appointments.value
    .filter((item) => {
      const matchesKeyword =
        !keyword ||
        [item.name, patientHn(item.name), item.service, item.therapist].some((value) =>
          value.toLowerCase().includes(keyword),
        )
      const matchesBranch =
        selectedBranch.value === 'ทั้งหมด' || item.branch === selectedBranch.value
      return matchesKeyword && matchesBranch
    })
    .sort((left, right) => {
      const comparison = `${left.date}T${left.time}`.localeCompare(`${right.date}T${right.time}`)
      return sortOrder.value === 'asc' ? comparison : -comparison
    })
})
const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredAppointments.value.length / perPage)),
)
const paginatedAppointments = computed(() => {
  const start = (currentPage.value - 1) * perPage
  return filteredAppointments.value.slice(start, start + perPage)
})
const paginationStart = computed(() =>
  filteredAppointments.value.length ? (currentPage.value - 1) * perPage + 1 : 0,
)
const paginationEnd = computed(() =>
  Math.min(currentPage.value * perPage, filteredAppointments.value.length),
)
watch([query, selectedBranch, sortOrder], () => {
  currentPage.value = 1
})
function formatThaiDate(value: string) {
  return new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}
const roomOptions = computed(() => {
  const branchName = form.value.branch.replace('สาขา', '')
  const rooms = configuredRooms.filter(
    (item) => item.branch === branchName && item.status !== 'ซ่อม',
  )
  return rooms.length ? rooms : [{ name: 'ห้องรักษา 1', branch: branchName, status: 'ว่าง' }]
})

function openCreate() {
  editingId.value = null
  form.value = { ...DEFAULT_APPOINTMENT }
  viewMode.value = 'create'
}

function openEdit(item: Appointment) {
  editingId.value = item.id
  form.value = { ...item }
  viewMode.value = 'edit'
}

function save() {
  const message = isEditing.value ? 'แก้ไขนัดหมายเรียบร้อยแล้ว' : 'บันทึกการนัดหมายเรียบร้อยแล้ว'
  if (isEditing.value && editingId.value !== null) {
    const index = appointments.value.findIndex((item) => item.id === editingId.value)
    if (index !== -1) appointments.value[index] = { ...form.value, id: editingId.value }
  } else {
    appointments.value.push({ ...form.value, id: Date.now() })
  }
  localStorage.setItem('clinicAppointments', JSON.stringify(appointments.value))
  viewMode.value = 'list'
  saveNotice.value = message
  window.setTimeout(() => (saveNotice.value = ''), 3500)
}

async function saveStatus(item: Appointment, status: string, reason = '') {
  const previousStatus = item.status
  savingStatusId.value = item.id
  statusNotice.value = 'กำลังบันทึก...'
  try {
    await new Promise((resolve) => setTimeout(resolve, 300))
    item.status = status
    localStorage.setItem('clinicAppointments', JSON.stringify(appointments.value))
    if (reason) {
      const reasons = JSON.parse(
        localStorage.getItem('appointmentCancellationReasons') || '{}',
      ) as Record<number, string>
      reasons[item.id] = reason
      localStorage.setItem('appointmentCancellationReasons', JSON.stringify(reasons))
    }
    statusNotice.value = 'บันทึกแล้ว'
  } catch {
    item.status = previousStatus
    statusNotice.value = 'บันทึกไม่สำเร็จ ระบบคืนค่าเดิมแล้ว'
  } finally {
    savingStatusId.value = null
    window.setTimeout(() => (statusNotice.value = ''), 1800)
  }
}

function changeStatus(event: Event, item: Appointment) {
  const status = (event.target as HTMLSelectElement).value
  if (status === 'ยกเลิก') {
    pendingCancellation.value = { item, status }
    cancellationReason.value = ''
    return
  }
  void saveStatus(item, status)
}

function confirmCancellation() {
  if (!pendingCancellation.value || !cancellationReason.value.trim()) return
  const { item, status } = pendingCancellation.value
  pendingCancellation.value = null
  void saveStatus(item, status, cancellationReason.value.trim())
}
</script>
<template>
  <div
    class="staff-page appointment-page"
    :class="{ 'appointment-form-open': viewMode !== 'list' }"
  >
    <StaffHeader />
    <main class="staff-main">
      <section v-if="viewMode === 'list'" class="staff-hero">
        <div>
          <h1>นัดหมาย</h1>
          <p>ตรวจสอบตารางนัด สร้าง แก้ไข และอัปเดตสถานะการเข้ารับบริการ</p>
          <button class="primary hero-action" @click="openCreate">+ สร้างนัดหมาย</button>
        </div>
      </section>
      <div v-if="viewMode !== 'list'" class="appointment-form-page">
        <button class="appointment-back" type="button" @click="viewMode = 'list'">
          ← กลับไปหน้านัดหมาย
        </button>
        <section class="staff-panel create-card">
          <h1>{{ isEditing ? 'แก้ไขนัดหมาย' : 'สร้างนัดหมายใหม่' }}</h1>
          <div class="form-grid">
            <label>
              ผู้ป่วย
              <select v-if="!isEditing" v-model="form.name">
                <option v-for="patient in PATIENT_OPTIONS" :key="patient.hn" :value="patient.name">
                  {{ patient.name }} HN {{ patient.hn }}
                </option>
              </select>
              <input
                v-else
                :value="`${form.name}${selectedPatientHn ? ` HN ${selectedPatientHn}` : ''}`"
                readonly
              />
            </label>
            <label>
              ประเภทบริการ
              <select v-model="form.service">
                <option v-for="service in SERVICE_OPTIONS" :key="service">{{ service }}</option>
              </select>
            </label>
            <label>
              นักกายภาพ
              <select v-model="form.therapist">
                <option v-for="therapist in THERAPIST_OPTIONS" :key="therapist">
                  {{ therapist }}
                </option>
              </select>
            </label>
            <label>วันที่<ThaiDatePicker v-model="form.date" /></label>
            <label>
              เวลา
              <select v-model="form.time">
                <option v-for="time in TIME_OPTIONS" :key="time.value" :value="time.value">
                  {{ time.label }}
                </option>
              </select>
            </label>
            <label>
              สาขา
              <select v-model="form.branch">
                <option v-for="item in branchOptions" :key="item">{{ item }}</option>
              </select>
            </label>
            <label>
              ห้อง
              <select v-model="form.room">
                <option v-for="item in roomOptions" :key="item.name" :value="item.name">
                  {{ item.name }} {{ item.status }}
                </option>
              </select>
            </label>
          </div>
          <div class="appointment-save">
            <button class="primary" @click="save">บันทึก</button>
          </div>
        </section>
      </div>
      <section v-else class="staff-panel appointment-panel">
        <header class="panel-head">
          <div class="search-box">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input v-model="query" placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย" />
          </div>
          <select v-model="selectedBranch" class="branch-filter" aria-label="กรองตามสาขา">
            <option value="ทั้งหมด">สาขาทั้งหมด</option>
            <option v-for="item in branchOptions" :key="item">{{ item }}</option>
          </select>
          <select v-model="sortOrder" class="sort-filter" aria-label="เรียงลำดับนัดหมาย">
            <option value="asc">เก่า - ใหม่</option>
            <option value="desc">ใหม่ - เก่า</option>
          </select>
        </header>
        <div class="appointment-table-scroll">
          <table class="staff-table">
            <thead>
              <tr>
                <th>วันที่</th>
                <th>เวลา</th>
                <th>ผู้ป่วย</th>
                <th>เลขประจำตัวผู้ป่วย</th>
                <th>บริการ</th>
                <th>ผู้ให้บริการ</th>
                <th>สาขา</th>
                <th>ห้อง</th>
                <th>สถานะ</th>
                <th class="status-notice-cell">
                  <span v-if="statusNotice" class="status-feedback" aria-live="polite">{{
                    statusNotice
                  }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in paginatedAppointments" :key="item.id">
                <td>{{ formatThaiDate(item.date) }}</td>
                <td>
                  <b>{{ timeLabel(item.time) }}</b>
                </td>
                <td>
                  <b>{{ item.name }}</b>
                </td>
                <td>{{ patientHn(item.name) }}</td>
                <td>{{ item.service }}</td>
                <td>{{ item.therapist }}</td>
                <td>
                  <span class="branch-label">{{ item.branch }}</span>
                </td>
                <td>{{ item.room }}</td>
                <td>
                  <select
                    :value="item.status"
                    class="status-select"
                    :disabled="savingStatusId !== null"
                    @change="changeStatus($event, item)"
                  >
                    <option v-for="status in STATUS_OPTIONS" :key="status">{{ status }}</option>
                  </select>
                </td>
                <td><button class="secondary" @click="openEdit(item)">แก้ไข</button></td>
              </tr>
              <tr v-if="filteredAppointments.length === 0">
                <td class="appointment-empty" colspan="10">ไม่พบข้อมูลนัดหมาย</td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer v-if="filteredAppointments.length" class="appointment-pagination">
          <span
            >แสดง {{ paginationStart }}–{{ paginationEnd }} จาก
            {{ filteredAppointments.length }} รายการ</span
          >
          <div>
            <button :disabled="currentPage === 1" @click="currentPage -= 1">ก่อนหน้า</button>
            <b>หน้า {{ currentPage }} / {{ totalPages }}</b>
            <button :disabled="currentPage === totalPages" @click="currentPage += 1">ถัดไป</button>
          </div>
        </footer>
      </section>
    </main>
    <div
      v-if="pendingCancellation"
      class="status-modal-backdrop"
      @click.self="pendingCancellation = null"
    >
      <form class="status-modal" @submit.prevent="confirmCancellation">
        <h2>ยืนยันการยกเลิกนัดหมาย</h2>
        <p>
          {{ pendingCancellation.item.name }} เวลา {{ timeLabel(pendingCancellation.item.time) }}
        </p>
        <label
          >เหตุผลที่ยกเลิก <em>*</em>
          <textarea v-model.trim="cancellationReason" rows="3" required></textarea>
        </label>
        <div>
          <button type="button" class="secondary" @click="pendingCancellation = null">
            ยกเลิก
          </button>
          <button type="submit" class="danger">ยืนยัน</button>
        </div>
      </form>
    </div>
    <Transition name="snackbar">
      <p v-if="saveNotice" class="appointment-snackbar" role="status" aria-live="polite">
        {{ saveNotice }}
      </p>
    </Transition>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
