<script setup lang="ts">
import { computed, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

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
  status: 'ยืนยันแล้ว (Confirmed)',
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
  'ยืนยันแล้ว (Confirmed)',
  'มาถึงคลินิก (Arrived)',
  'กำลังรับบริการ (In Service)',
  'เสร็จสิ้น (Completed)',
  'ยกเลิก (Cancelled)',
  'เลื่อนนัด (Rescheduled)',
  'ไม่มาตามนัด (No Show)',
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
    status: 'ยืนยันแล้ว (Confirmed)',
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
    status: 'กำลังรับบริการ (In Service)',
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
    status: 'เสร็จสิ้น (Completed)',
  },
]
const appointments = ref<Appointment[]>(
  JSON.parse(localStorage.getItem('clinicAppointments') || 'null') ?? defaultAppointments,
)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const pendingCancellation = ref<{ item: Appointment; status: string } | null>(null)
const cancellationReason = ref('')

const form = ref<Appointment>({ ...DEFAULT_APPOINTMENT })

const isEditing = computed(() => viewMode.value === 'edit')
const filteredAppointments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return appointments.value.filter((item) => {
    const matchesKeyword =
      !keyword ||
      [item.name, item.service, item.therapist].some((value) =>
        value.toLowerCase().includes(keyword),
      )
    const matchesBranch = selectedBranch.value === 'ทั้งหมด' || item.branch === selectedBranch.value
    return matchesKeyword && matchesBranch
  })
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
  if (isEditing.value && editingId.value !== null) {
    const index = appointments.value.findIndex((item) => item.id === editingId.value)
    if (index !== -1) appointments.value[index] = { ...form.value, id: editingId.value }
  } else {
    appointments.value.push({ ...form.value, id: Date.now() })
  }
  localStorage.setItem('clinicAppointments', JSON.stringify(appointments.value))
  viewMode.value = 'list'
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
  if (status === 'ยกเลิก (Cancelled)') {
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
        <small>Appointment & Patient Visit</small>
        <h1>นัดหมาย</h1>
        <p>ตรวจสอบตารางนัด สร้าง แก้ไข และอัปเดตสถานะการเข้ารับบริการ</p>
        <button class="primary hero-action" @click="openCreate">+ สร้างนัดหมาย</button>
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
              <select v-model="form.name">
                <option v-for="patient in PATIENT_OPTIONS" :key="patient.hn" :value="patient.name">
                  {{ patient.name }} HN {{ patient.hn }}
                </option>
              </select>
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
            <label>วันที่<input v-model="form.date" type="date" /></label>
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
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>วันที่</th>
              <th>เวลา</th>
              <th>ผู้ป่วย</th>
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
            <tr v-for="item in filteredAppointments" :key="item.id">
              <td>{{ formatThaiDate(item.date) }}</td>
              <td>
                <b>{{ item.time }}</b>
              </td>
              <td>
                <b>{{ item.name }}</b>
              </td>
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
              <td class="appointment-empty" colspan="9">ไม่พบข้อมูลนัดหมาย</td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
    <div
      v-if="pendingCancellation"
      class="status-modal-backdrop"
      @click.self="pendingCancellation = null"
    >
      <form class="status-modal" @submit.prevent="confirmCancellation">
        <h2>ยืนยันการยกเลิกนัดหมาย</h2>
        <p>{{ pendingCancellation.item.name }} เวลา {{ pendingCancellation.item.time }}</p>
        <label
          >เหตุผลที่ยกเลิก <em>*</em>
          <textarea v-model.trim="cancellationReason" rows="3" required></textarea>
        </label>
        <div>
          <button type="button" class="secondary" @click="pendingCancellation = null">
            กลับ
          </button>
          <button type="submit" class="danger">ยืนยันการยกเลิก</button>
        </div>
      </form>
    </div>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
