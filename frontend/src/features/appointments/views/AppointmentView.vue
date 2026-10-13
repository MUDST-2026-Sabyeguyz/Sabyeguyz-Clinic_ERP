<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  appointmentApi,
  branchApi,
  catalogApi,
  patientApi,
  resourceApi,
  type Appointment,
  type AppointmentStatus,
  type Branch,
  type ClinicResource,
  type Patient,
  type ServiceOption,
  type Therapist,
} from '@/api/client'

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const createSource = ref<'standard' | 'calendar'>('standard')
const appointmentPatientType = ref<'CURRENT' | 'NEW'>('CURRENT')
const scheduleView = ref<'list' | 'day' | 'week'>('list')
const calendarSyncOpen = ref(false)
const query = ref('')
const selectedBranch = ref('ทั้งหมด')
const selectedDate = ref('')
type AppointmentUiStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
const selectedStatus = ref<'ทั้งหมด' | AppointmentUiStatus>('ทั้งหมด')
const selectedPatientType = ref<'ทั้งหมด' | 'NEW' | 'CURRENT'>('ทั้งหมด')
const selectedServiceId = ref<number | 'ทั้งหมด'>('ทั้งหมด')
const selectedTherapistId = ref('ทั้งหมด')
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
const appointmentSuccessMessage = ref('')
const savingAppointment = ref(false)
const pendingSaveConfirmation = ref(false)
const pendingCancellation = ref<{ item: Appointment } | null>(null)
const cancellationReason = ref('')
const form = ref({
  patientId: 0,
  therapistId: 0,
  serviceId: 0,
  roomId: 0,
  branchCode: 'RP',
  date: clinicToday(),
  time: '09:00',
  note: '',
})
const branchOptions = computed(() => branches.value.filter((item) => item.active))
const roomOptions = computed(() =>
  rooms.value.filter(
    (item) => item.active && item.type === 'ROOM' && item.branchCode === form.value.branchCode,
  ),
)
const isEditing = computed(() => viewMode.value === 'edit')
const isCalendarCreate = computed(
  () => viewMode.value === 'create' && createSource.value === 'calendar',
)
const editingAppointment = computed(() =>
  appointments.value.find((appointment) => appointment.id === editingId.value),
)
const originalAppointmentSummary = computed(() => {
  const appointment = editingAppointment.value
  if (!appointment) return ''
  const time = formatTime(appointment.startAt).replace(/^0/, '')
  return `นัดเดิม: ${formatThaiDate(appointment.startAt)} · ${time} น. · สาขา${branchName(appointment.branchCode)} · ${roomName(appointment)}`
})
const patientError = computed(
  () =>
    errorMessage.value.startsWith('กรุณากรอก HN') || errorMessage.value.startsWith('ไม่พบผู้ป่วย'),
)
const scheduleError = computed(() => errorMessage.value.includes('ไม่สามารถจองเวลาย้อนหลัง'))
const patientResults = computed(() => {
  const term = patientQuery.value.trim().toLowerCase()
  if (!term || isEditing.value) return []
  return patients.value
    .filter((item) => `${item.hn} ${item.fullName} ${item.phone}`.toLowerCase().includes(term))
    .slice(0, 8)
})
const selectedFormPatient = computed(() =>
  patients.value.find((patient) => patient.id === form.value.patientId),
)
const filteredAppointments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  const direction = sortOrder.value === 'newest' ? -1 : 1
  return appointments.value
    .filter(
      (item) =>
        (!keyword ||
          `${item.patient.hn} ${item.patient.fullName} ${item.service.name} ${item.therapist.fullName}`
            .toLowerCase()
            .includes(keyword)) &&
        (selectedPatientType.value === 'ทั้งหมด' ||
          patientTypeFor(item) === selectedPatientType.value) &&
        (selectedServiceId.value === 'ทั้งหมด' || item.service.id === selectedServiceId.value) &&
        (selectedStatus.value === 'ทั้งหมด' || uiStatusFor(item.status) === selectedStatus.value),
    )
    .sort(
      (left, right) =>
        direction * (new Date(left.startAt).getTime() - new Date(right.startAt).getTime()),
    )
})
function patientTypeFor(item: Appointment): 'NEW' | 'CURRENT' {
  return appointments.value.filter((appointment) => appointment.patient.id === item.patient.id)
    .length > 1
    ? 'CURRENT'
    : 'NEW'
}
const patientPhone = (item: Appointment) =>
  patients.value.find((patient) => patient.id === item.patient.id)?.phone || '-'
const branchName = (code: string) =>
  branches.value.find((branch) => branch.branchCode === code)?.name || code
const roomName = (item: Appointment) =>
  rooms.value.find((room) => room.branchCode === item.branchCode && room.type === 'ROOM')?.name ||
  '-'
const timeOptions = ['09:00', '10:30', '13:00', '14:30', '16:00']
const uiStatusLabels: Record<AppointmentUiStatus, string> = {
  PENDING: 'ยังไม่ยืนยันนัด',
  CONFIRMED: 'ยืนยันนัดหมาย',
  COMPLETED: 'ใช้บริการแล้ว',
  CANCELLED: 'ยกเลิก',
}
const patientFilterOptions = [
  ['NEW', 'คนไข้ใหม่'],
  ['CURRENT', 'คนไข้ปัจจุบัน'],
] as const
// Keep the appointment filter limited to the four UI states from the design.
// Backend-only workflow states are grouped into these values by uiStatusFor().
const statusFilterOptions: [AppointmentUiStatus, string][] = [
  ['PENDING', 'ยังไม่ยืนยันนัด'],
  ['CONFIRMED', 'ยืนยันนัดหมาย'],
  ['COMPLETED', 'ใช้บริการแล้ว'],
  ['CANCELLED', 'ยกเลิก'],
]
function uiStatusFor(status: AppointmentStatus): AppointmentUiStatus {
  if (status === 'UNCONFIRMED' || status === 'RESCHEDULED') return 'PENDING'
  if (status === 'COMPLETED') return 'COMPLETED'
  if (status === 'CANCELLED' || status === 'NO_SHOW') return 'CANCELLED'
  return 'CONFIRMED'
}
const formatStatus = (status: AppointmentStatus) => uiStatusLabels[uiStatusFor(status)]
const actionStatusLabels: Record<AppointmentStatus, string> = {
  UNCONFIRMED: 'รอยืนยัน',
  CONFIRMED: 'ยืนยันแล้ว',
  ARRIVED: 'มาถึงคลินิก',
  IN_SERVICE: 'กำลังรับบริการ',
  COMPLETED: 'เสร็จสิ้น',
  CANCELLED: 'ยกเลิก',
  RESCHEDULED: 'เลื่อนนัด',
  NO_SHOW: 'ไม่มาตามนัด',
}
const statusOptionsFor = (item: Appointment): AppointmentStatus[] => [
  ...new Set<AppointmentStatus>([
    item.status,
    ...(item.status === 'UNCONFIRMED' ? ['CONFIRMED' as const] : []),
    'ARRIVED',
    'IN_SERVICE',
    'COMPLETED',
    'CANCELLED',
    'NO_SHOW',
  ]),
]
function changeStatus(event: Event, item: Appointment) {
  const select = event.target as HTMLSelectElement
  const status = select.value as AppointmentStatus
  select.value = item.status
  if (status === item.status) return
  if (status === 'CANCELLED') {
    pendingCancellation.value = { item }
    cancellationReason.value = ''
    return
  }
  void saveStatus(item, status)
}
const patientFilterModel = computed({
  get: () => selectedPatientType.value,
  set: (value: string) => {
    selectedPatientType.value = value as 'ทั้งหมด' | 'NEW' | 'CURRENT'
  },
})
const serviceFilterModel = computed({
  get: () => String(selectedServiceId.value),
  set: (value: string) => {
    selectedServiceId.value = value === 'ทั้งหมด' ? 'ทั้งหมด' : Number(value)
  },
})
const statusFilterModel = computed({
  get: () => selectedStatus.value,
  set: (value: string) => {
    selectedStatus.value = value as 'ทั้งหมด' | AppointmentUiStatus
  },
})
const formServiceModel = computed({
  get: () => String(form.value.serviceId || ''),
  set: (value: string) => {
    form.value.serviceId = Number(value) || 0
  },
})
const formTherapistModel = computed({
  get: () => String(form.value.therapistId || ''),
  set: (value: string) => {
    form.value.therapistId = Number(value) || 0
  },
})
const formTimeModel = computed({
  get: () => form.value.time,
  set: (value: string) => {
    form.value.time = value
    changeSchedule()
  },
})
const formBranchModel = computed({
  get: () => form.value.branchCode,
  set: (value: string) => {
    form.value.branchCode = value
    syncRoomToBranch()
  },
})
const formRoomModel = computed({
  get: () => String(form.value.roomId || ''),
  set: (value: string) => {
    form.value.roomId = Number(value) || 0
  },
})
const formatThaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(value),
  )
const formatTime = (value: string) =>
  new Date(value).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Bangkok',
  })
const calendarDate = computed(() => selectedDate.value || clinicToday())
const calendarTherapists = computed(() =>
  therapists.value
    .filter(
      (therapist) =>
        selectedTherapistId.value === 'ทั้งหมด' ||
        String(therapist.id) === selectedTherapistId.value,
    )
    .slice(0, 3),
)
const calendarAppointment = (therapistId: number, time: string) =>
  appointments.value.find(
    (item) =>
      item.therapist.id === therapistId &&
      item.startAt.slice(0, 10) === calendarDate.value &&
      formatTime(item.startAt) === time &&
      (selectedBranch.value === 'ทั้งหมด' || item.branchCode === selectedBranch.value),
  )
const calendarSlotState = (item?: Appointment) => {
  if (!item) return 'empty'
  if (item.status === 'COMPLETED') return 'completed'
  if (item.status === 'CANCELLED' || item.status === 'NO_SHOW') return 'cancelled'
  if (item.status === 'CONFIRMED') return 'confirmed'
  return 'pending'
}
const calendarPatientName = (therapistId: number, time: string) =>
  calendarAppointment(therapistId, time)?.patient.fullName || ''
const calendarTherapistName = (therapistId: number, time: string) =>
  calendarAppointment(therapistId, time)?.therapist.fullName || ''
function moveCalendarDate(days: number) {
  const date = new Date(`${calendarDate.value}T12:00:00+07:00`)
  date.setDate(date.getDate() + days)
  selectedDate.value = date.toISOString().slice(0, 10)
}
const toStartAt = () => `${form.value.date}T${form.value.time}:00+07:00`
async function load() {
  try {
    const [a, b, p, s, t, r] = await Promise.all([
      appointmentApi.search({ page: page.value, size: 20 }),
      branchApi.list(),
      patientApi.searchAll(),
      catalogApi.services(),
      catalogApi.therapists(),
      resourceApi.list(),
    ])
    appointments.value = a.items
    totalPages.value = a.totalPages
    branches.value = b
    patients.value = p
    services.value = s
    therapists.value = t
    rooms.value = r
    if (branches.value[0] && !form.value.branchCode)
      form.value.branchCode = branches.value[0].branchCode
  } catch (error) {
    appointments.value = []
    totalPages.value = 0
    branches.value = []
    patients.value = []
    services.value = []
    therapists.value = []
    rooms.value = []
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลนัดหมายไม่สำเร็จ'
  }
}
onMounted(() => {
  void load()
})
async function changePage(nextPage: number) {
  if (nextPage >= 0 && nextPage < totalPages.value) {
    page.value = nextPage
    await load()
  }
}
function firstRoomId(branchCode: string) {
  return (
    rooms.value.find(
      (item) => item.active && item.type === 'ROOM' && item.branchCode === branchCode,
    )?.id ?? 0
  )
}
function syncRoomToBranch() {
  if (!roomOptions.value.some((item) => item.id === form.value.roomId))
    form.value.roomId = firstRoomId(form.value.branchCode)
}
function openCreate(source: 'standard' | 'calendar' = 'standard') {
  editingId.value = null
  createSource.value = source
  appointmentPatientType.value = 'CURRENT'
  patientQuery.value = ''
  showPatientResults.value = false
  const branch = branchOptions.value[0]
  const branchCode = branch?.branchCode ?? ''
  form.value = {
    patientId: 0,
    therapistId: therapists.value[0]?.id ?? 0,
    serviceId: services.value[0]?.id ?? 0,
    roomId: firstRoomId(branchCode),
    branchCode,
    date: clinicToday(),
    time: '09:00',
    note: '',
  }
  viewMode.value = 'create'
}
function openCalendarSlot(therapist: Therapist, time: string) {
  const item = calendarAppointment(therapist.id, time)
  if (item) {
    openEdit(item)
    return
  }
  openCreate('calendar')
  form.value.therapistId = therapist.id
  form.value.date = calendarDate.value
  form.value.time = time
  form.value.branchCode = therapist.branchCode || form.value.branchCode
  syncRoomToBranch()
}
function openEdit(item: Appointment) {
  editingId.value = item.id
  createSource.value = 'standard'
  appointmentPatientType.value = 'CURRENT'
  patientQuery.value = `HN ${item.patient.hn} · ${patientPhone(item)}`
  showPatientResults.value = false
  form.value = {
    patientId: item.patient.id,
    therapistId: item.therapist.id,
    serviceId: item.service.id,
    roomId: firstRoomId(item.branchCode),
    branchCode: item.branchCode,
    date: item.startAt.slice(0, 10),
    time: new Date(item.startAt).toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'Asia/Bangkok',
    }),
    note: item.note ?? '',
  }
  viewMode.value = 'edit'
}
function choosePatient(patient: Patient) {
  form.value.patientId = patient.id
  patientQuery.value = patient.fullName
  showPatientResults.value = false
}
function changePatientQuery() {
  form.value.patientId = 0
  if (patientError.value) errorMessage.value = ''
  showPatientResults.value = true
}
function hidePatientResults() {
  window.setTimeout(() => (showPatientResults.value = false), 160)
}
function changeSchedule() {
  if (scheduleError.value) errorMessage.value = ''
}
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
    const patient = patients.value.find(
      (item) =>
        item.hn.toLowerCase() === normalized ||
        item.fullName.toLowerCase() === normalized ||
        String(item.id) === normalized ||
        `${item.fullName} hn ${item.hn}`.toLowerCase() === normalized,
    )
    if (!editingId.value && !patient)
      throw new Error('กรุณากรอก HN หรือชื่อผู้ป่วยให้ตรงกับรายการในระบบ')
    form.value.patientId = patient?.id ?? form.value.patientId
    if (!form.value.patientId) throw new Error('ไม่พบผู้ป่วยที่ระบุ')

    const wasEditing = editingId.value !== null
    let savedAppointment: Appointment
    if (editingId.value) {
      savedAppointment = await appointmentApi.reschedule(editingId.value, {
        startAt: toStartAt(),
        therapistId: form.value.therapistId,
      })
    } else {
      savedAppointment = await appointmentApi.create({
        branchCode: form.value.branchCode,
        patientId: form.value.patientId,
        therapistId: form.value.therapistId,
        serviceId: form.value.serviceId,
        startAt: toStartAt(),
        note: form.value.note,
      })
    }

    query.value = ''
    selectedBranch.value = 'ทั้งหมด'
    selectedDate.value = ''
    selectedStatus.value = 'ทั้งหมด'
    sortOrder.value = 'newest'
    page.value = 0
    viewMode.value = 'list'
    await load()
    appointments.value = [
      savedAppointment,
      ...appointments.value.filter((item) => item.id !== savedAppointment.id),
    ].slice(0, 20)
    if (wasEditing) {
      appointmentSuccessMessage.value = 'เลื่อนนัดหมายสำเร็จ'
    } else {
      appointmentSuccessMessage.value = 'สร้างนัดหมายสำเร็จ'
    }
    window.setTimeout(() => (appointmentSuccessMessage.value = ''), 2600)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกนัดหมายไม่สำเร็จ'
  } finally {
    savingAppointment.value = false
  }
}
async function saveStatus(item: Appointment, status: AppointmentStatus, reason = '') {
  savingStatusId.value = item.id
  statusNotice.value = 'กำลังบันทึก...'
  try {
    const result =
      status === 'CONFIRMED'
        ? await appointmentApi.confirm(item.id)
        : status === 'ARRIVED'
          ? await appointmentApi.checkIn(item.id)
          : status === 'IN_SERVICE'
            ? await appointmentApi.startService(item.id)
            : status === 'COMPLETED'
              ? await appointmentApi.complete(item.id)
              : status === 'NO_SHOW'
                ? await appointmentApi.noShow(item.id)
                : status === 'CANCELLED'
                  ? await appointmentApi.cancel(item.id, reason)
                  : null
    if (!result) throw new Error('backend รองรับการเปลี่ยนสถานะผ่าน action ที่กำหนดเท่านั้น')
    Object.assign(item, result)
    statusNotice.value = 'บันทึกแล้ว'
  } catch (error) {
    statusNotice.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ'
  } finally {
    savingStatusId.value = null
    window.setTimeout(() => (statusNotice.value = ''), 2200)
  }
}
function confirmCancellation() {
  if (!pendingCancellation.value || !cancellationReason.value.trim()) return
  const { item } = pendingCancellation.value
  pendingCancellation.value = null
  void saveStatus(item, 'CANCELLED', cancellationReason.value.trim())
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
        <h1>นัดหมาย</h1>
        <p>ตรวจสอบตารางนัด สร้าง แก้ไข และอัปเดตสถานะการเข้ารับบริการ</p>
        <div class="appointment-hero-actions">
          <button class="primary hero-action" @click="openCreate()">+ สร้างนัดหมาย</button>
          <button class="calendar-entry" type="button" @click="scheduleView = 'day'">
            <img src="/calendar-appointment.svg" alt="" /> ดูปฏิทินนัดหมาย
          </button>
        </div>
      </section>
      <div v-if="viewMode !== 'list'" class="appointment-form-page">
        <div class="appointment-back-row">
          <button class="appointment-back" type="button" @click="viewMode = 'list'">
            ← กลับไปหน้านัดหมาย
          </button>
        </div>
        <section class="staff-panel create-card">
          <h1>
            {{
              isEditing
                ? 'เลื่อนนัดหมาย'
                : isCalendarCreate
                  ? 'สร้างนัดหมายจากปฏิทิน'
                  : 'สร้างนัดหมายใหม่'
            }}
          </h1>
          <p v-if="isEditing" class="appointment-original-summary">
            {{ originalAppointmentSummary }}
          </p>
          <p v-if="errorMessage && !patientError && !scheduleError" class="error" role="alert">
            {{ errorMessage }}
          </p>
          <div class="form-grid">
            <fieldset v-if="!isEditing" class="appointment-patient-type-field">
              <legend>ประเภทผู้ป่วย</legend>
              <div class="appointment-patient-type-options">
                <label>
                  <input v-model="appointmentPatientType" type="radio" value="CURRENT" />
                  Current
                </label>
                <label>
                  <input v-model="appointmentPatientType" type="radio" value="NEW" />
                  NEW
                </label>
              </div>
            </fieldset>
            <label class="appointment-patient-field">
              ผู้ป่วย
              <div class="appointment-patient-picker">
                <input
                  v-model.trim="patientQuery"
                  :readonly="isEditing"
                  :class="{ 'is-invalid': patientError }"
                  :placeholder="
                    appointmentPatientType === 'NEW'
                      ? 'กรอกชื่อผู้ป่วยใหม่'
                      : 'กรอกชื่อผู้ป่วยหรือรหัส HN'
                  "
                  required
                  @focus="showPatientResults = true"
                  @input="changePatientQuery"
                  @blur="hidePatientResults"
                />
                <div
                  v-if="showPatientResults && patientQuery && !isEditing"
                  class="patient-search-results"
                >
                  <button
                    v-for="patient in patientResults"
                    :key="patient.id"
                    type="button"
                    @mousedown.prevent="choosePatient(patient)"
                  >
                    <b>{{ patient.fullName }}</b
                    ><span>{{ patient.hn }} {{ patient.phone }}</span>
                  </button>
                  <p v-if="!patientResults.length">ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา</p>
                </div>
                <p v-if="patientError" class="patient-input-error" role="alert">
                  {{ errorMessage }}
                </p>
                <p v-else-if="selectedFormPatient && !isEditing" class="appointment-patient-meta">
                  {{ selectedFormPatient.hn }} · {{ selectedFormPatient.phone }}
                </p>
              </div>
            </label>
            <label class="appointment-service-field">
              ประเภทบริการ
              <FilterDropdown
                v-model="formServiceModel"
                class="appointment-form-filter"
                label="เลือกประเภทบริการ"
                default-label="เลือกประเภทบริการ"
                :clear-on-reselect="false"
                :options="
                  services.map((service) => ({ value: String(service.id), label: service.name }))
                "
              />
            </label>
            <label class="appointment-therapist-field">
              นักกายภาพ
              <FilterDropdown
                v-model="formTherapistModel"
                class="appointment-form-filter"
                label="เลือกนักกายภาพ"
                default-label="เลือกนักกายภาพ"
                :clear-on-reselect="false"
                :options="
                  therapists.map((therapist) => ({
                    value: String(therapist.id),
                    label: therapist.fullName,
                  }))
                "
              />
            </label>
            <label class="appointment-date-field">
              วันที่
              <ThaiDatePicker
                v-model="form.date"
                :invalid="scheduleError"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeSchedule"
              />
              <p v-if="scheduleError" class="appointment-input-error" role="alert">
                {{ errorMessage }}
              </p>
            </label>
            <label class="appointment-time-field">
              เวลา
              <FilterDropdown
                v-model="formTimeModel"
                class="appointment-form-filter"
                label="เลือกเวลา"
                default-label="เลือกเวลา"
                :clear-on-reselect="false"
                :options="timeOptions.map((time) => ({ value: time, label: time }))"
              />
            </label>
            <label class="appointment-branch-field">
              สาขา
              <FilterDropdown
                v-model="formBranchModel"
                class="appointment-form-filter"
                label="เลือกสาขา"
                default-label="เลือกสาขา"
                :clear-on-reselect="false"
                :options="
                  branchOptions.map((item) => ({
                    value: item.branchCode,
                    label: `${item.name} (${item.branchCode})`,
                  }))
                "
              />
            </label>
            <label class="appointment-room-field">
              ห้อง
              <FilterDropdown
                v-model="formRoomModel"
                class="appointment-form-filter"
                label="เลือกห้อง"
                :default-label="roomOptions.length ? 'เลือกห้อง' : 'ไม่มีห้องรักษาที่เปิดใช้งาน'"
                :disabled="!roomOptions.length"
                :clear-on-reselect="false"
                :options="roomOptions.map((room) => ({ value: String(room.id), label: room.name }))"
              />
            </label>
            <label class="wide appointment-note-field"
              >หมายเหตุ<textarea
                v-model.trim="form.note"
                maxlength="500"
                rows="3"
                placeholder="ระบุรายละเอียดเพิ่มเติม"
              />
            </label>
          </div>
          <div class="appointment-save">
            <button class="primary" :disabled="savingAppointment" @click="requestSaveConfirmation">
              {{ savingAppointment ? 'กำลังบันทึก...' : 'บันทึก' }}
            </button>
          </div>
        </section>
      </div>
      <section v-else class="staff-panel appointment-panel">
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <header v-show="scheduleView === 'list'" class="panel-head appointment-list-filters">
          <div class="search-box">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input v-model="query" placeholder="ค้นหาชื่อ เบอร์โทร หรือรหัส HN" />
          </div>
          <FilterDropdown
            v-model="patientFilterModel"
            label="กรองประเภทคนไข้"
            default-value="ทั้งหมด"
            default-label="คนไข้ทั้งหมด"
            :options="patientFilterOptions.map(([value, label]) => ({ value, label }))"
          />
          <FilterDropdown
            v-model="serviceFilterModel"
            label="กรองบริการ"
            default-value="ทั้งหมด"
            default-label="บริการทั้งหมด"
            :options="
              services.map((service) => ({ value: String(service.id), label: service.name }))
            "
          />
          <FilterDropdown
            v-model="statusFilterModel"
            label="กรองตามสถานะนัดหมาย"
            default-value="ทั้งหมด"
            default-label="สถานะทั้งหมด"
            :options="statusFilterOptions.map(([value, label]) => ({ value, label }))"
          />
        </header>
        <section v-if="scheduleView !== 'list'" class="appointment-calendar">
          <header class="calendar-heading">
            <button type="button" class="calendar-list-back" @click="scheduleView = 'list'">
              ← รายการนัดหมาย
            </button>
            <div>
              <h1>ปฏิทินนัดหมาย</h1>
              <p>ดูเวลาว่างและตารางนัดของนักกายภาพ</p>
            </div>
          </header>
          <div class="calendar-controls">
            <div class="calendar-date-control">
              <button type="button" aria-label="วันก่อนหน้า" @click="moveCalendarDate(-1)">
                ‹
              </button>
              <strong>{{ formatThaiDate(calendarDate) }}</strong>
              <button type="button" aria-label="วันถัดไป" @click="moveCalendarDate(1)">›</button>
            </div>
            <FilterDropdown
              v-model="selectedTherapistId"
              label="กรองนักกายภาพ"
              default-value="ทั้งหมด"
              default-label="นักกายภาพ: ทั้งหมด"
              :options="
                therapists.map((item) => ({ value: String(item.id), label: item.fullName }))
              "
            />
            <FilterDropdown
              v-model="selectedBranch"
              label="กรองสาขา"
              default-value="ทั้งหมด"
              default-label="สาขา: ทั้งหมด"
              :options="branchOptions.map((item) => ({ value: item.branchCode, label: item.name }))"
            />
            <div class="calendar-view-toggle">
              <button
                type="button"
                :class="{ active: scheduleView === 'day' }"
                @click="scheduleView = 'day'"
              >
                รายวัน
              </button>
              <button
                type="button"
                :class="{ active: scheduleView === 'week' }"
                @click="scheduleView = 'week'"
              >
                รายสัปดาห์
              </button>
            </div>
            <button class="calendar-google" type="button" @click="calendarSyncOpen = true">
              เชื่อมต่อ Google Calendar
            </button>
          </div>
          <div class="calendar-legend">
            <span data-status="empty">ว่าง</span><span data-status="pending">ยังไม่ยืนยันนัด</span
            ><span data-status="confirmed">ยืนยันนัดหมาย</span
            ><span data-status="completed">ใช้บริการแล้ว</span
            ><span data-status="cancelled">ยกเลิก</span>
          </div>
          <div class="calendar-grid-scroll">
            <div class="calendar-grid day">
              <div class="calendar-time">
                <b>เวลา</b><span v-for="time in timeOptions" :key="time">{{ time }}</span>
              </div>
              <div
                v-for="therapist in calendarTherapists"
                :key="therapist.id"
                class="calendar-column"
              >
                <header>
                  <b>{{ therapist.fullName }}</b
                  ><small>{{ branchName(therapist.branchCode) }}</small>
                </header>
                <button
                  v-for="time in timeOptions"
                  :key="time"
                  type="button"
                  class="calendar-slot"
                  :data-state="calendarSlotState(calendarAppointment(therapist.id, time))"
                  @click="openCalendarSlot(therapist, time)"
                >
                  <template v-if="calendarAppointment(therapist.id, time)">
                    <strong>{{ time }} {{ calendarPatientName(therapist.id, time) }}</strong>
                    <small>นก. {{ calendarTherapistName(therapist.id, time) }}</small>
                  </template>
                  <template v-else
                    ><strong>ว่าง</strong><small>เลือกช่องนี้เพื่อสร้างนัดหมาย</small></template
                  >
                </button>
              </div>
            </div>
          </div>
        </section>
        <div v-else class="appointment-table-scroll">
          <table class="staff-table">
            <thead>
              <tr>
                <th>วันที่</th>
                <th>เวลา</th>
                <th>ผู้ป่วย</th>
                <th>เบอร์โทรศัพท์</th>
                <th>บริการ</th>
                <th>นักกายภาพ</th>
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
                <td>{{ patientPhone(item) }}</td>
                <td>{{ item.service.name }}</td>
                <td>{{ item.therapist.fullName }}</td>
                <td>{{ branchName(item.branchCode) }}</td>
                <td>{{ roomName(item) }}</td>
                <td>
                  <span class="appointment-status-badge" :data-status="uiStatusFor(item.status)">{{
                    formatStatus(item.status)
                  }}</span>
                </td>
                <td>
                  <button
                    v-if="item.status === 'UNCONFIRMED'"
                    class="appointment-edit"
                    :disabled="savingStatusId === item.id"
                    @click="saveStatus(item, 'CONFIRMED')"
                  >
                    ยืนยันนัด
                  </button>
                  <select
                    class="status-select"
                    :value="item.status"
                    :disabled="savingStatusId === item.id"
                    :aria-label="`เปลี่ยนสถานะนัดหมายของ ${item.patient.fullName}`"
                    @change="changeStatus($event, item)"
                  >
                    <option v-for="status in statusOptionsFor(item)" :key="status" :value="status">
                      {{ actionStatusLabels[status] }}
                    </option>
                  </select>
                  <button class="appointment-edit" @click="openEdit(item)">แก้ไข</button>
                </td>
              </tr>
              <tr v-if="filteredAppointments.length === 0">
                <td class="appointment-empty" colspan="10">ไม่พบข้อมูลนัดหมาย</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          v-if="scheduleView === 'list'"
          class="appointment-pagination"
          aria-label="เปลี่ยนหน้ารายการนัดหมาย"
        >
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">
            ← ก่อนหน้า
          </button>
          <span>หน้า {{ page + 1 }} จาก {{ Math.max(totalPages, 1) }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
            ถัดไป →
          </button>
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
        <p>
          {{ pendingCancellation.item.patient.fullName }} เวลา
          {{ formatTime(pendingCancellation.item.startAt) }}
        </p>
        <label
          >เหตุผลที่ยกเลิก <em>*</em>
          <textarea v-model.trim="cancellationReason" rows="3" required></textarea>
        </label>
        <div>
          <button type="button" class="secondary" @click="pendingCancellation = null">กลับ</button>
          <button type="submit" class="danger">ยืนยันการยกเลิก</button>
        </div>
      </form>
    </div>
    <div
      v-if="pendingSaveConfirmation"
      class="status-modal-backdrop"
      @click.self="pendingSaveConfirmation = false"
    >
      <section
        class="status-modal appointment-confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="appointment-confirmation-title"
      >
        <h2 id="appointment-confirmation-title">
          {{ isEditing ? 'ยืนยันการเลื่อนนัดหมายหรือไม่?' : 'ยืนยันการสร้างนัดหมายหรือไม่?' }}
        </h2>
        <div>
          <button type="button" class="secondary" @click="pendingSaveConfirmation = false">
            ไม่ใช่
          </button>
          <button type="button" class="primary" @click="confirmSave">ใช่</button>
        </div>
      </section>
    </div>
    <div v-if="appointmentSuccessMessage" class="status-modal-backdrop">
      <section class="status-modal appointment-success-modal" role="status" aria-live="polite">
        <h2>{{ appointmentSuccessMessage }}</h2>
      </section>
    </div>
    <div
      v-if="calendarSyncOpen"
      class="status-modal-backdrop"
      @click.self="calendarSyncOpen = false"
    >
      <section class="status-modal calendar-sync-modal" role="dialog" aria-modal="true">
        <h2>เชื่อมต่อ Google Calendar</h2>
        <div class="calendar-sync-description">
          <p>เมื่อเชื่อมต่อ ระบบจะแสดงนัดหมายจากสบายกายใน Google Calendar</p>
        </div>
        <div class="calendar-sync-actions">
          <button type="button" class="secondary" @click="calendarSyncOpen = false">ยกเลิก</button
          ><button type="button" class="primary" @click="calendarSyncOpen = false">
            เชื่อมต่อ
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
