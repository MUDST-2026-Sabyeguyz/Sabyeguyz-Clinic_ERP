<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { appointmentApi, branchApi, catalogApi, patientApi, resourceApi, type Appointment, type AppointmentStatus, type Branch, type ClinicResource, type Patient, type ServiceOption, type Therapist } from '@/api/client'

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const query = ref('')
const selectedBranch = ref('ทั้งหมด')
const selectedDate = ref('')
const selectedStatus = ref<'ทั้งหมด' | AppointmentStatus>('ทั้งหมด')
const sortOrder = ref<'newest' | 'oldest'>('newest')
const appointments = ref<Appointment[]>([])
const page = ref(0)
const totalPages = ref(0)
const branches = ref<Branch[]>([])
const patients = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const therapists = ref<Therapist[]>([])
const rooms = ref<ClinicResource[]>([])
const patientQuery = ref('')
const showPatientResults = ref(false)
const editingId = ref<number | null>(null)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const errorMessage = ref('')
const saveNotice = ref('')
const savingAppointment = ref(false)
const pendingSaveConfirmation = ref(false)
const pendingCancellation = ref<{ item: Appointment } | null>(null)
const cancellationReason = ref('')
const form = ref({ patientId: 0, therapistId: 0, serviceId: 0, roomId: 0, branchCode: 'RP', date: new Date().toISOString().slice(0, 10), time: '09:00', note: '' })
const branchOptions = computed(() => branches.value.filter((item) => item.active))
const roomOptions = computed(() => rooms.value.filter((item) => item.active && item.type === 'ROOM' && item.branchCode === form.value.branchCode))
const isEditing = computed(() => viewMode.value === 'edit')
const patientError = computed(() => errorMessage.value.startsWith('กรุณากรอก HN') || errorMessage.value.startsWith('ไม่พบผู้ป่วย'))
const scheduleError = computed(() => errorMessage.value.includes('ไม่สามารถจองเวลาย้อนหลัง'))
const patientResults = computed(() => {
  const term = patientQuery.value.trim().toLowerCase()
  if (!term || isEditing.value) return []
  return patients.value.filter((item) => `${item.hn} ${item.fullName} ${item.phone}`.toLowerCase().includes(term)).slice(0, 8)
})
const filteredAppointments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  const direction = sortOrder.value === 'newest' ? -1 : 1
  return appointments.value
    .filter((item) => (!keyword || `${item.patient.hn} ${item.patient.fullName} ${item.service.name} ${item.therapist.fullName}`.toLowerCase().includes(keyword)) && (selectedBranch.value === 'ทั้งหมด' || item.branchCode === selectedBranch.value) && (!selectedDate.value || item.startAt.slice(0, 10) === selectedDate.value) && (selectedStatus.value === 'ทั้งหมด' || item.status === selectedStatus.value))
    .sort((left, right) => direction * (new Date(left.startAt).getTime() - new Date(right.startAt).getTime()))
})
// The backend changes appointment status through these actions only. Confirmed
// and rescheduled are displayed when current, but cannot be selected directly.
const statusActionOptions: AppointmentStatus[] = ['ARRIVED', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW']
const timeOptions = ['09:00', '10:00', '11:00', '14:00']
const statusLabels: Record<AppointmentStatus, string> = { UNCONFIRMED: 'รอยืนยัน', CONFIRMED: 'ยืนยันแล้ว', ARRIVED: 'มาถึงคลินิก', IN_SERVICE: 'กำลังรับบริการ', COMPLETED: 'เสร็จสิ้น', CANCELLED: 'ยกเลิก', RESCHEDULED: 'เลื่อนนัด', NO_SHOW: 'ไม่มาตามนัด' }
const formatStatus = (status: AppointmentStatus) => statusLabels[status]
const statusOptionsFor = (item: Appointment): AppointmentStatus[] => [...new Set<AppointmentStatus>([item.status, ...(item.status === 'UNCONFIRMED' ? ['CONFIRMED' as const] : []), ...statusActionOptions])]
const formatThaiDate = (value: string) => new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value))
const formatTime = (value: string) => new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' })
const toStartAt = () => `${form.value.date}T${form.value.time}:00+07:00`
async function load() {
  try {
    const [a, b, p, s, t, r] = await Promise.all([appointmentApi.search({ page: page.value, size: 20 }), branchApi.list(), patientApi.searchAll(), catalogApi.services(), catalogApi.therapists(), resourceApi.list()])
    appointments.value = a.items; totalPages.value = a.totalPages; branches.value = b; patients.value = p; services.value = s; therapists.value = t; rooms.value = r
    if (branches.value[0] && !form.value.branchCode) form.value.branchCode = branches.value[0].branchCode
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลนัดหมายไม่สำเร็จ' }
}
onMounted(load)
async function changePage(nextPage: number) { if (nextPage >= 0 && nextPage < totalPages.value) { page.value = nextPage; await load() } }
function firstRoomId(branchCode: string) { return rooms.value.find((item) => item.active && item.type === 'ROOM' && item.branchCode === branchCode)?.id ?? 0 }
function syncRoomToBranch() { if (!roomOptions.value.some((item) => item.id === form.value.roomId)) form.value.roomId = firstRoomId(form.value.branchCode) }
function openCreate() { editingId.value = null; patientQuery.value = ''; showPatientResults.value = false; const branch = branchOptions.value[0]; const branchCode = branch?.branchCode ?? '01'; form.value = { patientId: 0, therapistId: therapists.value[0]?.id ?? 0, serviceId: services.value[0]?.id ?? 0, roomId: firstRoomId(branchCode), branchCode, date: new Date().toISOString().slice(0, 10), time: '09:00', note: '' }; viewMode.value = 'create' }
function openEdit(item: Appointment) { editingId.value = item.id; patientQuery.value = item.patient.fullName; showPatientResults.value = false; form.value = { patientId: item.patient.id, therapistId: item.therapist.id, serviceId: item.service.id, roomId: firstRoomId(item.branchCode), branchCode: item.branchCode, date: item.startAt.slice(0, 10), time: new Date(item.startAt).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' }), note: item.note ?? '' }; viewMode.value = 'edit' }
function choosePatient(patient: Patient) { form.value.patientId = patient.id; patientQuery.value = patient.fullName; showPatientResults.value = false }
function changePatientQuery() { form.value.patientId = 0; if (patientError.value) errorMessage.value = ''; showPatientResults.value = true }
function hidePatientResults() { window.setTimeout(() => (showPatientResults.value = false), 160) }
function changeSchedule() { if (scheduleError.value) errorMessage.value = '' }
function requestSaveConfirmation() {
  if (!savingAppointment.value) pendingSaveConfirmation.value = true
}
function confirmSave() {
  pendingSaveConfirmation.value = false
  void save()
}
async function save() {
  errorMessage.value = ''
  savingAppointment.value = true
  try {
    const normalized = patientQuery.value.trim().toLowerCase()
    const patient = patients.value.find((item) => item.hn.toLowerCase() === normalized || item.fullName.toLowerCase() === normalized || String(item.id) === normalized || `${item.fullName} hn ${item.hn}`.toLowerCase() === normalized)
    if (!editingId.value && !patient) throw new Error('กรุณากรอก HN หรือชื่อผู้ป่วยให้ตรงกับรายการในระบบ')
    form.value.patientId = patient?.id ?? form.value.patientId
    if (!form.value.patientId) throw new Error('ไม่พบผู้ป่วยที่ระบุ')

    const wasEditing = editingId.value !== null
    let savedAppointment: Appointment
    if (editingId.value) {
      savedAppointment = await appointmentApi.reschedule(editingId.value, { startAt: toStartAt(), therapistId: form.value.therapistId })
    } else {
      savedAppointment = await appointmentApi.create({ branchCode: form.value.branchCode, patientId: form.value.patientId, therapistId: form.value.therapistId, serviceId: form.value.serviceId, startAt: toStartAt(), note: form.value.note })
    }

    query.value = ''
    selectedBranch.value = 'ทั้งหมด'
    selectedDate.value = ''
    selectedStatus.value = 'ทั้งหมด'
    sortOrder.value = 'newest'
    page.value = 0
    viewMode.value = 'list'
    await load()
    appointments.value = [savedAppointment, ...appointments.value.filter((item) => item.id !== savedAppointment.id)].slice(0, 20)
    saveNotice.value = wasEditing ? 'แก้ไขนัดหมายเรียบร้อยแล้ว' : 'สร้างนัดหมายเรียบร้อยแล้ว'
    window.setTimeout(() => (saveNotice.value = ''), 3000)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกนัดหมายไม่สำเร็จ'
  } finally {
    savingAppointment.value = false
  }
}
async function saveStatus(item: Appointment, status: AppointmentStatus, reason = '') { savingStatusId.value = item.id; statusNotice.value = 'กำลังบันทึก...'; try { const result = status === 'CONFIRMED' ? await appointmentApi.confirm(item.id) : status === 'ARRIVED' ? await appointmentApi.checkIn(item.id) : status === 'IN_SERVICE' ? await appointmentApi.startService(item.id) : status === 'COMPLETED' ? await appointmentApi.complete(item.id) : status === 'NO_SHOW' ? await appointmentApi.noShow(item.id) : status === 'CANCELLED' ? await appointmentApi.cancel(item.id, reason) : null; if (!result) throw new Error('backend รองรับการเปลี่ยนสถานะผ่าน action ที่กำหนดเท่านั้น'); Object.assign(item, result); statusNotice.value = 'บันทึกแล้ว' } catch (error) { statusNotice.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ' } finally { savingStatusId.value = null; window.setTimeout(() => (statusNotice.value = ''), 2200) } }
function changeStatus(event: Event, item: Appointment) { const status = (event.target as HTMLSelectElement).value as AppointmentStatus; if (status === item.status) return; if (status === 'CANCELLED') { pendingCancellation.value = { item }; cancellationReason.value = ''; return } void saveStatus(item, status) }
function confirmCancellation() { if (!pendingCancellation.value || !cancellationReason.value.trim()) return; const { item } = pendingCancellation.value; pendingCancellation.value = null; void saveStatus(item, 'CANCELLED', cancellationReason.value.trim()) }
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
        <div class="appointment-back-row">
          <button class="appointment-back" type="button" @click="viewMode = 'list'">
            ← กลับไปหน้านัดหมาย
          </button>
        </div>
        <section class="staff-panel create-card">
          <h1>{{ isEditing ? 'แก้ไขนัดหมาย' : 'สร้างนัดหมายใหม่' }}</h1>
          <p v-if="errorMessage && !patientError && !scheduleError" class="error" role="alert">{{ errorMessage }}</p>
          <div class="form-grid">
            <label>
              ผู้ป่วย
              <div class="appointment-patient-picker">
                <input
                  v-model.trim="patientQuery"
                  :readonly="isEditing"
                  :class="{ 'is-invalid': patientError }"
                  placeholder="กรอก HN หรือชื่อผู้ป่วย"
                  required
                  @focus="showPatientResults = true"
                  @input="changePatientQuery"
                  @blur="hidePatientResults"
                />
                <div v-if="showPatientResults && patientQuery && !isEditing" class="patient-search-results">
                  <button v-for="patient in patientResults" :key="patient.id" type="button" @mousedown.prevent="choosePatient(patient)">
                    <b>{{ patient.fullName }}</b><span>{{ patient.hn }} {{ patient.phone }}</span>
                  </button>
                  <p v-if="!patientResults.length">ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา</p>
                </div>
                <p v-if="patientError" class="patient-input-error" role="alert">{{ errorMessage }}</p>
              </div>
            </label>
            <label>
              บริการ
              <select v-model="form.serviceId" :disabled="isEditing">
                <option v-for="service in services" :key="service.id" :value="service.id">{{ service.name }}</option>
              </select>
            </label>
            <label>
              นักกายภาพ
              <select v-model="form.therapistId">
                <option v-for="therapist in therapists" :key="therapist.id" :value="therapist.id">
                  {{ therapist.fullName }}
                </option>
              </select>
            </label>
            <label>
              วันที่
              <ThaiDatePicker v-model="form.date" :invalid="scheduleError" show-calendar-icon numeric-month hide-caret @update:model-value="changeSchedule" />
              <p v-if="scheduleError" class="appointment-input-error" role="alert">{{ errorMessage }}</p>
            </label>
            <label>
              เวลา
              <select v-model="form.time" @change="changeSchedule">
                <option v-for="time in timeOptions" :key="time" :value="time">
                  {{ time }}
                </option>
              </select>
            </label>
            <label>
              สาขา
              <select v-model="form.branchCode" :disabled="isEditing" @change="syncRoomToBranch">
                <option v-for="item in branchOptions" :key="item.branchCode" :value="item.branchCode">{{ item.name }} ({{ item.branchCode }})</option>
              </select>
            </label>
            <label>
              ห้อง
              <select v-model="form.roomId" :disabled="!roomOptions.length">
                <option v-if="!roomOptions.length" :value="0">ไม่มีห้องรักษาที่เปิดใช้งาน</option>
                <option v-for="room in roomOptions" :key="room.id" :value="room.id">{{ room.name }}</option>
              </select>
            </label>
            <label class="wide appointment-note-field">หมายเหตุ (ถ้ามี)<textarea v-model.trim="form.note" maxlength="500" rows="3" placeholder="ระบุรายละเอียดเพิ่มเติม" :disabled="isEditing" /></label>
          </div>
          <div class="appointment-save">
            <button class="primary" :disabled="savingAppointment" @click="requestSaveConfirmation">
              {{ savingAppointment ? 'กำลังบันทึก...' : (isEditing ? 'บันทึกการแก้ไข' : 'บันทึกนัดหมาย') }}
            </button>
          </div>
        </section>
      </div>
      <section v-else class="staff-panel appointment-panel">
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <Transition name="snackbar">
          <p v-if="saveNotice" class="appointment-snackbar" role="status">{{ saveNotice }}</p>
        </Transition>
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
            <option v-for="item in branchOptions" :key="item.branchCode" :value="item.branchCode">{{ item.name }}</option>
          </select>
          <ThaiDatePicker v-model="selectedDate" class="appointment-date-filter" show-calendar-icon numeric-month hide-caret aria-label="กรองตามวันที่นัด" />
          <select v-model="selectedStatus" class="appointment-status-filter" aria-label="กรองตามสถานะนัดหมาย">
            <option value="ทั้งหมด">สถานะทั้งหมด</option>
            <option value="UNCONFIRMED">รอยืนยัน</option>
            <option value="CONFIRMED">ยืนยันแล้ว</option>
            <option value="ARRIVED">มาถึงคลินิก</option>
            <option value="IN_SERVICE">กำลังรับบริการ</option>
            <option value="COMPLETED">เสร็จสิ้น</option>
            <option value="CANCELLED">ยกเลิก</option>
            <option value="RESCHEDULED">เลื่อนนัด</option>
            <option value="NO_SHOW">ไม่มาตามนัด</option>
          </select>
          <select v-model="sortOrder" class="appointment-sort" aria-label="เรียงลำดับวันที่นัด">
            <option value="newest">ล่าสุด</option>
            <option value="oldest">เก่าสุด</option>
          </select>
        </header>
        <div class="appointment-table-scroll">
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
              <td>{{ formatThaiDate(item.startAt) }}</td>
              <td>
                <b>{{ formatTime(item.startAt) }}</b>
              </td>
              <td>
                <b>{{ item.patient.fullName }}</b>
              </td>
              <td>{{ item.service.name }}</td>
              <td>{{ item.therapist.fullName }}</td>
              <td>
                <span class="branch-label">{{ item.branchCode }}</span>
              </td>
              <td>—</td>
              <td>
                <select
                  :value="item.status"
                  class="status-select"
                  :disabled="savingStatusId !== null"
                  @change="changeStatus($event, item)"
                >
                  <option v-for="status in statusOptionsFor(item)" :key="status" :value="status">{{ formatStatus(status) }}</option>
                </select>
              </td>
              <td><button class="secondary" @click="openEdit(item)">แก้ไข</button></td>
            </tr>
            <tr v-if="filteredAppointments.length === 0">
              <td class="appointment-empty" colspan="9">ไม่พบข้อมูลนัดหมาย</td>
            </tr>
          </tbody>
        </table>
        </div>
        <nav v-if="totalPages > 1" class="appointment-pagination" aria-label="เปลี่ยนหน้ารายการนัดหมาย">
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">← ก่อนหน้า</button>
          <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">ถัดไป →</button>
        </nav>
      </section>
    </main>
    <div
      v-if="pendingCancellation"
      class="status-modal-backdrop"
      @click.self="pendingCancellation = null"
    >
      <form class="status-modal" @submit.prevent="confirmCancellation">
        <h2>ยืนยันการยกเลิกนัดหมาย</h2>
        <p>{{ pendingCancellation.item.patient.fullName }} เวลา {{ formatTime(pendingCancellation.item.startAt) }}</p>
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
    <div
      v-if="pendingSaveConfirmation"
      class="status-modal-backdrop"
      @click.self="pendingSaveConfirmation = false"
    >
      <section class="status-modal appointment-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="appointment-confirmation-title">
        <h2 id="appointment-confirmation-title">{{ isEditing ? 'ยืนยันการแก้ไขนัดหมาย' : 'ยืนยันการสร้างนัดหมาย' }}</h2>
        <p>{{ isEditing ? 'ต้องการบันทึกการแก้ไขนัดหมายนี้ใช่หรือไม่' : 'ต้องการสร้างนัดหมายนี้ใช่หรือไม่' }}</p>
        <div>
          <button type="button" class="secondary" @click="pendingSaveConfirmation = false">ยกเลิก</button>
          <button type="button" class="primary" @click="confirmSave">{{ isEditing ? 'ยืนยันการแก้ไข' : 'ยืนยันการสร้าง' }}</button>
        </div>
      </section>
    </div>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
