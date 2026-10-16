<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  appointmentApi,
  branchApi,
  catalogApi,
  courseApi,
  patientApi,
  type Appointment,
  type AppointmentStatus,
  type Branch,
  type CoursePurchase,
  type Patient,
  type ServiceOption,
  type Therapist,
} from '@/api/client'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'

const authUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
  role?: string
  branchCode?: string
} | null
const isManager = authUser?.role === 'MANAGER'

const route = useRoute()
const router = useRouter()

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const createSource = ref<'standard' | 'calendar'>('standard')
const appointmentPatientType = ref<'CURRENT' | 'NEW'>('CURRENT')
const serviceMode = ref<'single' | 'course'>('course')
const patientCourses = ref<CoursePurchase[]>([])
const selectedCourseId = ref(0)
const timeDrawerOpen = ref(false)
const scheduleView = ref<'list' | 'day' | 'week'>('list')
const listServiceMode = ref<'single' | 'course'>('single')
const calendarSyncOpen = ref(false)
const selectedBranch = ref('ทั้งหมด')
const selectedDate = ref('')
const startDate = ref('')
const endDate = ref('')
type AppointmentUiStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'
const selectedTherapistId = ref('ทั้งหมด')
const sortOrder = ref<'newest' | 'oldest'>('newest')
const appointments = ref<Appointment[]>([])
const page = ref(0)
const totalPages = ref(0)
const branches = ref<Branch[]>([])
const patients = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const therapists = ref<Therapist[]>([])
const patientQuery = ref('')
const newPatientForm = ref({
  firstName: '',
  lastName: '',
  nickname: '',
  phone: '',
})
const showPatientResults = ref(false)
const editingId = ref<number | null>(null)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const errorMessage = ref('')
const showRequiredValidation = ref(false)
const appointmentSuccessMessage = ref('')
const savingAppointment = ref(false)
const pendingSaveConfirmation = ref(false)
const pendingCancellation = ref<{ item: Appointment } | null>(null)
const cancellationReason = ref('')
const form = ref({
  patientId: 0,
  therapistId: 0,
  serviceId: 0,
  branchCode: '',
  date: new Date().toISOString().slice(0, 10),
  time: '09:00',
  note: '',
})
const managerBranchCode = computed(() =>
  isManager ? selectedManagerBranch()?.code : authUser?.branchCode,
)
const branchOptions = computed(() =>
  branches.value.filter(
    (item) => item.active && (!managerBranchCode.value || item.code === managerBranchCode.value),
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
  return `นัดเดิม: ${formatThaiDate(appointment.startAt)} · ${time} น. · สาขา${branchName(appointment.branchCode)} · ${appointmentRoom(appointment)}`
})
const patientError = computed(
  () =>
    errorMessage.value.startsWith('กรุณากรอก HN') || errorMessage.value.startsWith('ไม่พบผู้ป่วย'),
)
const isCurrentAppointment = computed(() => appointmentPatientType.value === 'CURRENT')
const missingCurrentPatient = computed(
  () => showRequiredValidation.value && isCurrentAppointment.value && !form.value.patientId,
)
const missingCurrentTherapist = computed(
  () => showRequiredValidation.value && isCurrentAppointment.value && !form.value.therapistId,
)
const missingCurrentDate = computed(
  () => showRequiredValidation.value && isCurrentAppointment.value && !form.value.date,
)
const missingCurrentTime = computed(
  () => showRequiredValidation.value && isCurrentAppointment.value && !form.value.time,
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
type AppointmentCourse = CoursePurchase & {
  shared?: boolean
  sourcePatientName?: string
  originalTherapistName?: string
}
const availablePatientCourses = computed(() =>
  patientCourses.value.filter((course) => course.usable && !course.expired && course.remaining > 0),
)
const selectedPatientCourse = computed(
  () =>
    availablePatientCourses.value.find((course) => course.id === selectedCourseId.value) as
      AppointmentCourse | undefined,
)
const selectedService = computed(() =>
  services.value.find((service) => service.id === form.value.serviceId),
)
const selectedTherapist = computed(() =>
  therapists.value.find((therapist) => therapist.id === form.value.therapistId),
)
const currentBranchName = computed(() => branchName(form.value.branchCode))
const currentBranchLabel = computed(() =>
  currentBranchName.value.startsWith('สาขา')
    ? currentBranchName.value
    : `สาขา${currentBranchName.value}`,
)
const drawerServiceLabel = computed(() => {
  const label = selectedService.value?.name || selectedPatientCourse.value?.course.name || 'บริการ'
  return /นาที/.test(label)
    ? label
    : `${label} ${selectedService.value?.durationMinutes || 60} นาที`
})
const morningTimeOptions = computed(() =>
  timeOptions.filter((time) => Number(time.slice(0, 2)) < 12),
)
const afternoonTimeOptions = computed(() =>
  timeOptions.filter((time) => Number(time.slice(0, 2)) >= 12),
)
const filteredAppointments = computed(() => {
  const direction = sortOrder.value === 'newest' ? -1 : 1
  return appointments.value
    .filter(
      (item) =>
        (listServiceMode.value === 'course'
          ? isCourseAppointment(item)
          : !isCourseAppointment(item)) &&
        (!startDate.value || item.startAt.slice(0, 10) >= startDate.value) &&
        (!endDate.value || item.startAt.slice(0, 10) <= endDate.value),
    )
    .sort(
      (left, right) =>
        direction * (new Date(left.startAt).getTime() - new Date(right.startAt).getTime()),
    )
})
const isCourseAppointment = (item: Appointment) => {
  const courseAppointment = item as Appointment & {
    courseId?: number | null
    coursePurchaseId?: number | null
    course?: { id?: number; name?: string } | null
  }
  return Boolean(
    courseAppointment.courseId ||
    courseAppointment.coursePurchaseId ||
    courseAppointment.course?.id ||
    /คอร์ส|course/i.test(item.service.name),
  )
}
const patientPhone = (item: Appointment) =>
  patients.value.find((patient) => patient.id === item.patient.id)?.phone || '-'
const appointmentRoom = (item: Appointment) => {
  const appointmentWithRoom = item as Appointment & {
    roomName?: string
    room?: { name?: string }
  }
  return appointmentWithRoom.roomName || appointmentWithRoom.room?.name || '—'
}
const editingPatientPhone = computed(() =>
  editingAppointment.value ? patientPhone(editingAppointment.value) : '-',
)
const branchName = (code: string) =>
  branches.value.find((branch) => branch.code === code)?.name || code
const timeOptions = ['09:00', '10:30', '13:00', '14:30', '16:00']
const appointmentStatusOptions: [AppointmentUiStatus, string][] = [
  ['PENDING', 'ยังไม่ยืนยัน'],
  ['CONFIRMED', 'ยืนยัน'],
  ['CANCELLED', 'ยกเลิก'],
]
function uiStatusFor(status: AppointmentStatus): AppointmentUiStatus {
  if (status === 'RESCHEDULED') return 'PENDING'
  if (status === 'CANCELLED' || status === 'NO_SHOW') return 'CANCELLED'
  return 'CONFIRMED'
}
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
const formBranchModel = computed({
  get: () => form.value.branchCode,
  set: (value: string) => {
    form.value.branchCode = value
  },
})
const selectedCourseModel = computed({
  get: () => String(selectedCourseId.value || ''),
  set: (value: string) => {
    selectedCourseId.value = Number(value) || 0
  },
})
const formatThaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(value))
const formatTime = (value: string) =>
  new Date(value).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Bangkok',
  })
const formatTimeRange = (time: string) => {
  if (!time) return 'ยังไม่ได้เลือกเวลา'
  const [hour = 0, minute = 0] = time.split(':').map(Number)
  const duration = selectedService.value?.durationMinutes || 60
  const endMinutes = hour * 60 + minute + duration
  const endHour = Math.floor(endMinutes / 60) % 24
  const endMinute = endMinutes % 60
  return `${time}–${String(endHour).padStart(2, '0')}:${String(endMinute).padStart(2, '0')}`
}
const calendarDate = computed(() => selectedDate.value || new Date().toISOString().slice(0, 10))
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
  const date = new Date(`${calendarDate.value}T00:00:00`)
  date.setDate(date.getDate() + days)
  selectedDate.value = date.toISOString().slice(0, 10)
}
const toStartAt = () => `${form.value.date}T${form.value.time}:00+07:00`
async function load() {
  try {
    const [a, b, p, s, t] = await Promise.all([
      appointmentApi.search({
        page: page.value,
        size: 20,
        branchCode: managerBranchCode.value,
        startDate: startDate.value || undefined,
        endDate: endDate.value || undefined,
        sortOrder: sortOrder.value,
      }),
      branchApi.list(),
      patientApi.searchAll('', managerBranchCode.value),
      catalogApi.services(),
      catalogApi.therapists(),
    ])
    appointments.value = a.items
    totalPages.value = a.totalPages
    branches.value = b
    if (isManager) setManagerBranches(b.filter((branch) => branch.active))
    patients.value = p
    services.value = s
    therapists.value = managerBranchCode.value
      ? t.filter((therapist) => therapist.branchCode === managerBranchCode.value)
      : t
    if (branches.value[0] && !form.value.branchCode) form.value.branchCode = branches.value[0].code
  } catch (error) {
    appointments.value = []
    totalPages.value = 0
    branches.value = []
    patients.value = []
    services.value = []
    therapists.value = []
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลนัดหมายไม่สำเร็จ'
  }
}
onMounted(async () => {
  await load()
  window.addEventListener('manager-branch-changed', applyManagerBranch)
  const editId = Number(route.params.id || route.query.edit)
  if (editId) {
    try {
      const appointment =
        appointments.value.find((item) => item.id === editId) ?? (await appointmentApi.get(editId))
      openEdit(appointment)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'โหลดนัดหมายไม่สำเร็จ'
    }
  } else if (route.name === 'staff-appointment-create' || route.query.create === 'calendar') {
    openCreate(
      route.query.source === 'calendar' || route.query.create === 'calendar'
        ? 'calendar'
        : 'standard',
    )
    if (typeof route.query.date === 'string') form.value.date = route.query.date
    if (typeof route.query.time === 'string') form.value.time = route.query.time
    const therapistId = Number(route.query.therapistId)
    if (therapistId) form.value.therapistId = therapistId
  }
})
onBeforeUnmount(() => window.removeEventListener('manager-branch-changed', applyManagerBranch))
watch([startDate, endDate], () => {
  page.value = 0
  void load()
})
function applyManagerBranch() {
  if (!isManager) return
  selectedBranch.value = selectedManagerBranch()?.code ?? 'ทั้งหมด'
  page.value = 0
  void load()
}
async function changePage(nextPage: number) {
  if (nextPage >= 0 && nextPage < totalPages.value) {
    page.value = nextPage
    await load()
  }
}
function openCreate(source: 'standard' | 'calendar' = 'standard') {
  editingId.value = null
  createSource.value = source
  appointmentPatientType.value = 'CURRENT'
  serviceMode.value = source === 'calendar' ? 'single' : 'course'
  patientCourses.value = []
  selectedCourseId.value = 0
  timeDrawerOpen.value = false
  showRequiredValidation.value = false
  patientQuery.value = ''
  newPatientForm.value = {
    firstName: '',
    lastName: '',
    nickname: '',
    phone: '',
  }
  showPatientResults.value = false
  const branch = branchOptions.value[0]
  const branchCode = branch?.code ?? ''
  form.value = {
    patientId: 0,
    therapistId: therapists.value[0]?.id ?? 0,
    serviceId: services.value[0]?.id ?? 0,
    branchCode,
    date: new Date().toISOString().slice(0, 10),
    time: '',
    note: '',
  }
  viewMode.value = 'create'
  window.scrollTo({ top: 0, behavior: 'auto' })
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
}
async function loadPatientCourses(patientId: number) {
  patientCourses.value = []
  selectedCourseId.value = 0
  if (!patientId) return
  try {
    patientCourses.value = await courseApi.patientCourses(patientId)
    selectedCourseId.value = availablePatientCourses.value[0]?.id ?? 0
  } catch {
    patientCourses.value = []
  }
}
function openEdit(item: Appointment) {
  editingId.value = item.id
  createSource.value = 'standard'
  appointmentPatientType.value = 'CURRENT'
  showRequiredValidation.value = false
  patientQuery.value = item.patient.fullName
  showPatientResults.value = false
  form.value = {
    patientId: item.patient.id,
    therapistId: item.therapist.id,
    serviceId: item.service.id,
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
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function goToCreate() {
  openCreate()
  void router.push({ name: 'staff-appointment-create' })
}

function goToEdit(item: Appointment) {
  openEdit(item)
  void router.push({ name: 'staff-appointment-edit', params: { id: item.id } })
}

function goToAppointmentList() {
  timeDrawerOpen.value = false
  viewMode.value = 'list'
  if (createSource.value === 'calendar') {
    void router.push({ name: 'staff-appointment-calendar' })
  } else {
    void router.push({ name: 'staff-appointments' })
  }
  window.scrollTo({ top: 0, behavior: 'auto' })
}
function choosePatient(patient: Patient) {
  form.value.patientId = patient.id
  patientQuery.value = patient.fullName
  showPatientResults.value = false
  void loadPatientCourses(patient.id)
}
function changePatientQuery() {
  form.value.patientId = 0
  patientCourses.value = []
  selectedCourseId.value = 0
  if (patientError.value) errorMessage.value = ''
  showPatientResults.value = true
}
function hidePatientResults() {
  window.setTimeout(() => (showPatientResults.value = false), 160)
}
function changeAppointmentPatientType() {
  errorMessage.value = ''
  showRequiredValidation.value = false
  form.value.patientId = 0
  patientQuery.value = ''
  patientCourses.value = []
  selectedCourseId.value = 0
  showPatientResults.value = false
}
function changeSchedule() {
  if (scheduleError.value) errorMessage.value = ''
}
function selectAppointmentTime(time: string) {
  form.value.time = time
  changeSchedule()
}
function confirmAppointmentTime() {
  timeDrawerOpen.value = false
}
function requestSaveConfirmation() {
  showRequiredValidation.value = true
  if (appointmentPatientType.value === 'NEW') {
    errorMessage.value = 'ยังไม่รองรับการบันทึกผู้ป่วย NEW จนกว่าจะเชื่อมต่อ backend'
    return
  }
  if (
    appointmentPatientType.value === 'CURRENT' &&
    (!form.value.patientId || !form.value.therapistId || !form.value.date || !form.value.time)
  ) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ'
    return
  }
  if (!editingId.value && serviceMode.value === 'course' && createSource.value !== 'calendar') {
    errorMessage.value = 'ยังไม่รองรับการผูกนัดหมายกับสิทธิ์คอร์ส กรุณาใช้บริการรายครั้ง'
    return
  }
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
    if (!form.value.therapistId) throw new Error('กรุณาเลือกนักกายภาพ')
    if (!form.value.date || !form.value.time) throw new Error('กรุณาเลือกวันที่และเวลา')

    const wasEditing = editingId.value !== null
    let savedAppointment: Appointment
    if (editingId.value) {
      savedAppointment = await appointmentApi.reschedule(editingId.value, {
        startAt: toStartAt(),
        therapistId: form.value.therapistId,
        serviceId: form.value.serviceId || undefined,
        branchCode: form.value.branchCode,
        note: form.value.note,
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

    selectedBranch.value = 'ทั้งหมด'
    selectedDate.value = ''
    startDate.value = ''
    endDate.value = ''
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
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกนัดหมายไม่สำเร็จ'
  } finally {
    savingAppointment.value = false
  }
}
function dismissAppointmentSuccess() {
  appointmentSuccessMessage.value = ''
  void router.replace({ name: 'staff-appointments' })
}
async function saveStatus(item: Appointment, status: AppointmentStatus, reason = '') {
  savingStatusId.value = item.id
  statusNotice.value = 'กำลังบันทึก...'
  try {
    const result =
      status === 'ARRIVED'
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

function requestStatusChange(item: Appointment, nextStatus: string) {
  if (nextStatus === uiStatusFor(item.status)) return
  if (nextStatus === 'CANCELLED') {
    cancellationReason.value = ''
    pendingCancellation.value = { item }
    return
  }
  statusNotice.value = 'รอเชื่อมต่อการเปลี่ยนสถานะกับ backend'
  window.setTimeout(() => (statusNotice.value = ''), 2200)
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
          <button class="primary hero-action" @click="goToCreate">+ สร้างนัดหมาย</button>
          <button
            class="calendar-entry"
            type="button"
            @click="router.push({ name: 'staff-appointment-calendar' })"
          >
            <img src="/calendar-appointment.svg" alt="" /> ดูปฏิทินนัดหมาย
          </button>
        </div>
      </section>
      <div v-if="viewMode !== 'list'" class="appointment-form-page">
        <div class="appointment-back-row">
          <button class="appointment-back" type="button" @click="goToAppointmentList">
            {{ isCalendarCreate ? '← กลับไปหน้าปฏิทิน' : '← กลับไปหน้านัดหมาย' }}
          </button>
        </div>
        <section
          class="staff-panel create-card appointment-create-card"
          :class="{ 'is-editing': isEditing }"
        >
          <h1>
            {{
              isEditing
                ? 'เลื่อนนัดหมาย'
                : isCalendarCreate
                  ? 'สร้างนัดหมายจากปฏิทิน'
                  : 'สร้างนัดหมายใหม่'
            }}
          </h1>
          <p v-if="!isEditing" class="appointment-form-context">
            <template v-if="appointmentPatientType === 'NEW'">
              {{ currentBranchLabel }} • เลือกนักกายภาพและช่วงเวลาที่เหมาะสม
            </template>
            <template v-else>
              {{ currentBranchLabel }} • บัญชี Therapist ถูกจำกัดเฉพาะสาขานี้
            </template>
          </p>
          <p v-if="isEditing" class="appointment-original-summary">
            {{ originalAppointmentSummary }}
          </p>
          <p v-if="errorMessage && !patientError && !scheduleError" class="error" role="alert">
            {{ errorMessage }}
          </p>
          <div v-if="isEditing" class="form-grid appointment-reschedule-grid">
            <label class="reschedule-patient-field">
              ผู้ป่วย
              <input :value="patientQuery" readonly placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN" />
              <span class="reschedule-patient-meta">
                HN
                {{ selectedFormPatient?.hn || editingAppointment?.patient.hn || '-' }}
                ·
                {{ selectedFormPatient?.phone || editingPatientPhone }}
              </span>
            </label>
            <label class="reschedule-service-field">
              ประเภทบริการ
              <FilterDropdown
                v-model="formServiceModel"
                class="appointment-form-filter"
                label="เลือกประเภทบริการ"
                default-label="เลือกประเภทบริการ"
                :clear-on-reselect="false"
                :options="
                  services.map((service) => ({
                    value: String(service.id),
                    label: service.name,
                  }))
                "
              />
            </label>
            <label class="reschedule-therapist-field">
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
            <label class="reschedule-date-field">
              วันที่
              <ThaiDatePicker
                v-model="form.date"
                :invalid="scheduleError"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeSchedule"
              />
            </label>
            <label class="reschedule-time-field">
              เวลา
              <button class="reschedule-time-trigger" type="button" @click="timeDrawerOpen = true">
                <span>{{ formatTimeRange(form.time) }}</span>
                <strong>ดูตารางเวลาว่าง</strong>
              </button>
            </label>
            <label v-if="!isManager" class="reschedule-branch-field">
              สาขา
              <FilterDropdown
                v-model="formBranchModel"
                class="appointment-form-filter"
                label="เลือกสาขา"
                default-label="เลือกสาขา"
                :clear-on-reselect="false"
                :options="
                  branchOptions.map((branch) => ({
                    value: branch.code,
                    label: branch.name,
                  }))
                "
              />
            </label>
            <label class="reschedule-note-field">
              หมายเหตุ
              <textarea v-model.trim="form.note" maxlength="500" rows="2" />
            </label>
          </div>
          <div
            v-else
            class="form-grid appointment-create-grid"
            :class="{ 'is-new-patient': appointmentPatientType === 'NEW' }"
          >
            <fieldset v-if="!isEditing" class="appointment-patient-type-field">
              <legend>ประเภทผู้ป่วย</legend>
              <div class="appointment-patient-type-options">
                <label>
                  <input
                    v-model="appointmentPatientType"
                    type="radio"
                    value="CURRENT"
                    @change="changeAppointmentPatientType"
                  />
                  Current
                </label>
                <label>
                  <input
                    v-model="appointmentPatientType"
                    type="radio"
                    value="NEW"
                    @change="changeAppointmentPatientType"
                  />
                  NEW
                </label>
              </div>
            </fieldset>
            <label v-if="appointmentPatientType === 'CURRENT'" class="appointment-patient-field">
              ผู้ป่วย
              <div class="appointment-patient-picker">
                <input
                  v-model.trim="patientQuery"
                  :readonly="isEditing"
                  :class="{
                    'is-invalid': patientError || missingCurrentPatient,
                  }"
                  placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"
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
            <label v-if="appointmentPatientType === 'CURRENT'" class="appointment-phone-field">
              เบอร์โทรศัพท์
              <input
                :value="selectedFormPatient?.phone || ''"
                readonly
                placeholder="เบอร์โทรศัพท์"
              />
            </label>
            <div v-else class="appointment-new-patient-fields">
              <label>
                <span>ชื่อ <small>(ไม่บังคับ)</small></span>
                <input
                  v-model.trim="newPatientForm.firstName"
                  autocomplete="given-name"
                  placeholder="กรอกชื่อ"
                />
              </label>
              <label>
                <span>นามสกุล <small>(ไม่บังคับ)</small></span>
                <input
                  v-model.trim="newPatientForm.lastName"
                  autocomplete="family-name"
                  placeholder="กรอกนามสกุล"
                />
              </label>
              <label>
                <span>ชื่อเล่น <em>*</em></span>
                <input v-model.trim="newPatientForm.nickname" placeholder="กรอกชื่อเล่น" />
              </label>
              <label>
                <span>เบอร์โทรศัพท์ <em>*</em></span>
                <input
                  v-model.trim="newPatientForm.phone"
                  type="tel"
                  inputmode="numeric"
                  autocomplete="tel"
                  pattern="[0-9]*"
                  maxlength="10"
                  placeholder="กรอกเบอร์โทรศัพท์"
                  @input="newPatientForm.phone = newPatientForm.phone.replace(/\D/g, '').slice(0, 10)"
                />
              </label>
              <p>ผู้จอง NEW ยังไม่มี HN และยังไม่เกิดข้อมูลของการชำระล่วงหน้า</p>
            </div>
            <fieldset class="appointment-service-mode-field">
              <legend>ประเภทบริการ <small>(ไม่บังคับ)</small></legend>
              <div class="appointment-service-mode-options">
                <button
                  type="button"
                  :class="{ active: serviceMode === 'single' }"
                  @click="serviceMode = 'single'"
                >
                  รายครั้ง
                </button>
                <button
                  type="button"
                  :class="{ active: serviceMode === 'course' }"
                  @click="serviceMode = 'course'"
                >
                  รายคอร์ส
                </button>
              </div>
            </fieldset>
            <label v-if="serviceMode === 'single'" class="appointment-service-field">
              {{
                appointmentPatientType === 'NEW' ? 'บริการ / คอร์ส (ไม่บังคับ)' : 'รายชื่อบริการ'
              }}
              <FilterDropdown
                v-model="formServiceModel"
                class="appointment-form-filter"
                label="เลือกบริการ"
                default-label="เลือกบริการ"
                :clear-on-reselect="false"
                :options="
                  services.map((service) => ({
                    value: String(service.id),
                    label: service.name,
                  }))
                "
              />
            </label>
            <label v-else class="appointment-course-field">
              {{ appointmentPatientType === 'NEW' ? 'บริการ / คอร์ส (ไม่บังคับ)' : 'รายชื่อคอร์ส' }}
              <FilterDropdown
                v-model="selectedCourseModel"
                class="appointment-form-filter"
                label="เลือกคอร์ส"
                default-label="เลือกคอร์ส"
                :clear-on-reselect="false"
                :options="
                  availablePatientCourses.map((course) => ({
                    value: String(course.id),
                    label: course.course.name,
                  }))
                "
              />
              <span
                v-if="selectedFormPatient && !availablePatientCourses.length"
                class="appointment-course-empty"
              >
                ไม่พบคอร์สที่ยังใช้งานได้สำหรับผู้ป่วยรายนี้
              </span>
            </label>
            <label class="appointment-therapist-field">
              นักกายภาพ <em>*</em>
              <FilterDropdown
                v-model="formTherapistModel"
                class="appointment-form-filter"
                :class="{ 'is-invalid': missingCurrentTherapist }"
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
              วันที่ <em>*</em>
              <ThaiDatePicker
                v-model="form.date"
                :invalid="scheduleError || missingCurrentDate"
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
              เวลา <em>*</em>
              <span class="appointment-time-picker" :class="{ 'is-invalid': missingCurrentTime }">
                <span>{{ formatTimeRange(form.time) }}</span>
                <button type="button" @click="timeDrawerOpen = true">ดูตารางเวลาว่าง</button>
              </span>
            </label>
            <label class="wide appointment-note-field"
              >หมายเหตุ<textarea
                v-model.trim="form.note"
                maxlength="500"
                rows="3"
                placeholder="ระบุรายละเอียดเพิ่มเติม"
              />
            </label>
            <article
              v-if="serviceMode === 'course' && selectedPatientCourse"
              class="appointment-course-summary"
            >
              <header>
                <h2>{{ selectedPatientCourse.course.name }}</h2>
                <span :class="{ shared: selectedPatientCourse.shared }">
                  {{ selectedPatientCourse.shared ? 'แชร์คอร์ส' : 'คอร์สปกติ' }}
                </span>
              </header>
              <p>
                คงเหลือ {{ selectedPatientCourse.remaining }} ครั้ง　•　หมดอายุ
                {{ formatThaiDate(selectedPatientCourse.expiresAt) }}
              </p>
              <strong v-if="selectedPatientCourse.shared">
                ผู้ให้สิทธิ์:
                {{ selectedPatientCourse.sourcePatientName || 'รอข้อมูลจากระบบ' }}
              </strong>
              <strong v-else>
                นักกายภาพประจำคอร์ส:
                {{ selectedTherapist?.fullName || 'กรุณาเลือกนักกายภาพ' }}
              </strong>
              <p v-if="selectedPatientCourse.shared">
                นักกายภาพเดิม:
                {{ selectedPatientCourse.originalTherapistName || 'รอข้อมูลจากระบบ' }}
              </p>
              <small>{{
                selectedPatientCourse.shared
                  ? 'เลือกนักกายภาพเดิมหรือคนอื่นได้'
                  : 'เลือกเวลาว่างของนักกายภาพในขั้นตอนถัดไป'
              }}</small>
            </article>
          </div>
          <div class="appointment-save">
            <button v-if="!isEditing" class="secondary" type="button" @click="goToAppointmentList">
              ยกเลิก
            </button>
            <button class="primary" :disabled="savingAppointment" @click="requestSaveConfirmation">
              {{ savingAppointment ? 'กำลังบันทึก...' : isEditing ? 'บันทึก' : 'บันทึกนัดหมาย' }}
            </button>
          </div>
        </section>
      </div>
      <div
        v-if="timeDrawerOpen"
        class="appointment-time-drawer-backdrop"
        @click.self="timeDrawerOpen = false"
      >
        <aside
          class="appointment-time-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="time-drawer-title"
        >
          <div class="appointment-time-drawer-content">
            <h2 id="time-drawer-title">เลือกเวลาว่าง</h2>
            <p>{{ drawerServiceLabel }}</p>
            <strong
              >นักกายภาพ: {{ selectedTherapist?.fullName || 'ยังไม่ได้เลือก' }}　|　{{
                currentBranchLabel
              }}</strong
            >
            <label>
              วันที่
              <ThaiDatePicker
                v-model="form.date"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeSchedule"
              />
            </label>
            <section>
              <h3>ช่วงเช้า</h3>
              <div class="appointment-time-slots">
                <button
                  v-for="time in morningTimeOptions"
                  :key="time"
                  type="button"
                  :class="{ selected: form.time === time }"
                  @click="selectAppointmentTime(time)"
                >
                  {{ formatTimeRange(time) }}
                </button>
              </div>
            </section>
            <section>
              <h3>ช่วงบ่าย</h3>
              <div class="appointment-time-slots">
                <button
                  v-for="time in afternoonTimeOptions"
                  :key="time"
                  type="button"
                  :class="{ selected: form.time === time }"
                  @click="selectAppointmentTime(time)"
                >
                  {{ formatTimeRange(time) }}
                </button>
              </div>
            </section>
            <p class="appointment-time-help">
              ช่วงสีเทาไม่สามารถเลือกได้: นัดคลินิก, Google Calendar, เวลาพัก หรือเวลานอกกะ
            </p>
          </div>
          <footer>
            <button type="button" class="secondary" @click="timeDrawerOpen = false">ยกเลิก</button>
            <button type="button" class="primary" @click="confirmAppointmentTime">
              ยืนยันเวลา
            </button>
          </footer>
        </aside>
      </div>
      <section v-if="viewMode === 'list'" class="staff-panel appointment-panel">
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <div v-show="scheduleView === 'list'" class="appointment-list-controls">
          <div class="appointment-list-mode-toggle" aria-label="เลือกรูปแบบรายการ">
            <button
              type="button"
              :class="{ active: listServiceMode === 'single' }"
              @click="listServiceMode = 'single'"
            >
              รายครั้ง
            </button>
            <button
              type="button"
              :class="{ active: listServiceMode === 'course' }"
              @click="listServiceMode = 'course'"
            >
              รายคอร์ส
            </button>
          </div>
          <header class="panel-head appointment-list-filters">
            <label class="appointment-date-range">
              <span>วันที่เริ่มต้น</span>
              <ThaiDatePicker
                v-model="startDate"
                numeric-month
                show-calendar-icon
                hide-caret
                placeholder="วว/ดด/ปปปป"
              />
            </label>
            <label class="appointment-date-range">
              <span>วันที่สิ้นสุด</span>
              <ThaiDatePicker
                v-model="endDate"
                numeric-month
                show-calendar-icon
                hide-caret
                placeholder="วว/ดด/ปปปป"
              />
            </label>
          </header>
        </div>
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
                therapists.map((item) => ({
                  value: String(item.id),
                  label: item.fullName,
                }))
              "
            />
            <FilterDropdown
              v-if="!isManager"
              v-model="selectedBranch"
              label="กรองสาขา"
              default-value="ทั้งหมด"
              default-label="สาขา: ทั้งหมด"
              :options="
                branchOptions.map((item) => ({
                  value: item.code,
                  label: item.name,
                }))
              "
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
                <td>
                  <div class="appointment-status-filter" :data-status="uiStatusFor(item.status)">
                    <FilterDropdown
                      :model-value="uiStatusFor(item.status)"
                      :options="
                        appointmentStatusOptions.map(([value, label]) => ({
                          value,
                          label,
                        }))
                      "
                      :default-label="uiStatusFor(item.status)"
                      :label="`สถานะนัดหมายของ ${item.patient.fullName}`"
                      :clear-on-reselect="false"
                      hide-default-option
                      @update:model-value="requestStatusChange(item, $event)"
                    />
                  </div>
                </td>
                <td>
                  <button class="appointment-edit" @click="goToEdit(item)">แก้ไข</button>
                </td>
              </tr>
              <tr v-if="filteredAppointments.length === 0">
                <td class="appointment-empty" colspan="8">ไม่พบข้อมูลนัดหมาย</td>
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
    <div
      v-if="appointmentSuccessMessage"
      class="status-modal-backdrop"
      @click.self="dismissAppointmentSuccess"
    >
      <section class="status-modal appointment-success-modal" role="status" aria-live="polite">
        <h2>{{ appointmentSuccessMessage }}</h2>
        <button type="button" class="primary" @click="dismissAppointmentSuccess">ตกลง</button>
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
