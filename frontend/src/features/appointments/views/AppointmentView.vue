<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clinicToday } from '@/utils/clinicDate'

import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  appointmentApi,
  ApiError,
  branchApi,
  calendarApi,
  catalogApi,
  courseApi,
  patientApi,
  type Appointment,
  type AppointmentAvailability,
  type AppointmentStatus,
  type Branch,
  type CalendarStatus,
  type CoursePurchase,
  type Patient,
  type ServiceOption,
  type Therapist,
} from '@/api/client'

const route = useRoute()
const router = useRouter()

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const sessionUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string; branchCode?: string } | null
const isManager = sessionUser?.role === 'MANAGER'
const homeBranch = sessionUser?.branchCode ?? ''
const contactPhone = ref('')
const requestedServiceType = ref<'SingleVisit' | 'Course'>('SingleVisit')
const createSource = ref<'standard' | 'calendar'>('standard')
const appointmentPatientType = ref<'CURRENT' | 'NEW'>('CURRENT')
const serviceMode = ref<'single' | 'course'>('course')
const patientCourses = ref<CoursePurchase[]>([])
const selectedCourseId = ref(0)
const timeDrawerOpen = ref(false)
const scheduleView = ref<'list' | 'day' | 'week'>('list')
const listServiceMode = ref<'single' | 'course'>('single')
const calendarSyncOpen = ref(false)
const googleStatus = ref<CalendarStatus | null>(null)
const googleMessage = ref('')
const googleBusy = ref(false)
const isManagerUser = (() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
  } catch {
    return false
  }
})()
const query = ref('')
const selectedBranch = ref('ทั้งหมด')
const selectedDate = ref('')
const startDate = ref('')
const endDate = ref('')
type AppointmentUiStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
const selectedStatus = ref<'ทั้งหมด' | AppointmentUiStatus>('ทั้งหมด')
const selectedPatientType = ref<'ทั้งหมด' | 'NEW' | 'CURRENT'>('ทั้งหมด')

const selectedTherapistId = ref('ทั้งหมด')
const sortOrder = ref<'newest' | 'oldest'>('newest')
const appointments = ref<Appointment[]>([])
const page = ref(0)
const totalPages = ref(0)
const branches = ref<Branch[]>([])
const patients = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const therapists = ref<Therapist[]>([])
const appointmentMasterReady = computed(
  () => branches.value.length > 0 && services.value.length > 0 && therapists.value.length > 0,
)

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
const appointmentSuccessMessage = ref('')
const savingAppointment = ref(false)
const pendingSaveConfirmation = ref(false)
const pendingCancellation = ref<{ item: Appointment } | null>(null)
const cancellationReason = ref('')
const form = ref({
  patientId: 0,
  therapistId: 0,
  serviceId: 0,
  branchCode: 'RP',
  date: clinicToday(),

  time: '09:00',
  note: '',
})
const branchOptions = computed(() => branches.value.filter((item) => item.active))
const bookingTherapists = computed(() => therapists.value.filter((item) => item.branchCode === form.value.branchCode))

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
  return `นัดเดิม: ${formatThaiDate(appointment.startAt)} · ${time} น. · สาขา${branchName(appointment.branchCode)}`

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
type AppointmentCourse = CoursePurchase & {
  shared?: boolean
  sourcePatientName?: string
  originalTherapistName?: string
}
const availablePatientCourses = computed(() =>
  patientCourses.value.filter((course) => course.usable && !course.expired && course.remaining > 0),
)
const selectedPatientCourse = computed(() =>
  availablePatientCourses.value.find((course) => course.id === selectedCourseId.value) as
    | AppointmentCourse
    | undefined,
)
const selectedService = computed(() =>
  services.value.find((service) => service.id === form.value.serviceId),
)
const selectedTherapist = computed(() =>
  therapists.value.find((therapist) => therapist.id === form.value.therapistId),
)
const currentBranchName = computed(() => branchName(form.value.branchCode))
const currentBranchLabel = computed(() =>
  currentBranchName.value.startsWith('สาขา') ? currentBranchName.value : `สาขา${currentBranchName.value}`,
)
// Free times come from the server (branch booking window, the therapist's other appointments); the fixed list
// is only the fallback when the lookup is not available.
const availability = ref<AppointmentAvailability | null>(null)
const slotOptions = computed(() =>
  availability.value
    ? availability.value.slots
    : timeOptions.map((time) => ({ time, available: true, reason: null })),
)
const appointmentMinutes = computed(
  () => availability.value?.durationMinutes || selectedService.value?.durationMinutes || 60,
)
const drawerServiceLabel = computed(() => {
  const label = selectedService.value?.name || selectedPatientCourse.value?.course.name
  return label
    ? /นาที/.test(label) ? label : `${label} ${appointmentMinutes.value} นาที`
    : `ยังไม่ระบุบริการ ${appointmentMinutes.value} นาที`
})
const morningSlots = computed(() => slotOptions.value.filter((slot) => Number(slot.time.slice(0, 2)) < 12))
const afternoonSlots = computed(() => slotOptions.value.filter((slot) => Number(slot.time.slice(0, 2)) >= 12))
// The dropdown offers only the free times (plus the time already chosen, so an edited appointment keeps its own).
const timeChoices = computed(() => {
  const free = slotOptions.value.filter((slot) => slot.available).map((slot) => slot.time)
  return form.value.time && !free.includes(form.value.time) ? [form.value.time, ...free] : free
})
const filteredAppointments = computed(() => {
  const direction = sortOrder.value === 'newest' ? -1 : 1
  return appointments.value
    .filter(
      (item) =>
        (selectedPatientType.value === 'ทั้งหมด' ||
          patientTypeFor(item) === selectedPatientType.value) &&
        (selectedStatus.value === 'ทั้งหมด' || uiStatusFor(item.status) === selectedStatus.value) &&
        (listServiceMode.value === 'course' ? isCourseAppointment(item) : !isCourseAppointment(item)) &&
        (!startDate.value || item.startAt.slice(0, 10) >= startDate.value) &&
        (!endDate.value || item.startAt.slice(0, 10) <= endDate.value),

    )
    .sort(
      (left, right) =>
        direction * (new Date(left.startAt).getTime() - new Date(right.startAt).getTime()),
    )
})
function patientTypeFor(item: Appointment): 'NEW' | 'CURRENT' {
  return item.patientType ?? (item.patient.id == null ? 'NEW' : 'CURRENT')
}
function isCourseAppointment(item: Appointment) {
  return /คอร์ส|course/i.test(item.service.name)
}
const patientPhone = (item: Appointment) =>
  item.patient.phone || item.contactPhone || patients.value.find((patient) => patient.id === item.patient.id)?.phone || '-'
const editingPatientPhone = computed(() =>
  editingAppointment.value ? patientPhone(editingAppointment.value) : '-',
)
const branchName = (code: string) =>
  branches.value.find((branch) => branch.branchCode === code)?.name || code

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
    form.value.therapistId = bookingTherapists.value[0]?.id ?? 0

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
const formatTimeRange = (time: string) => {
  if (!time) return 'ยังไม่ได้เลือกเวลา'
  const [hour = 0, minute = 0] = time.split(':').map(Number)
  const endMinutes = hour * 60 + minute + appointmentMinutes.value
  return `${time}–${String(Math.floor(endMinutes / 60) % 24).padStart(2, '0')}:${String(endMinutes % 60).padStart(2, '0')}`
}
const calendarDate = computed(() => selectedDate.value || clinicToday())

const calendarTherapists = computed(() =>
  therapists.value
    .filter(
      (therapist) =>
        (isManager || therapist.branchCode === homeBranch) &&
        (selectedTherapistId.value === 'ทั้งหมด' ||
        String(therapist.id) === selectedTherapistId.value),
    )
    .slice(0, 3),
)
// An appointment sits in the last slot that starts at or before it, so one moved to an off-grid time
// (for example 09:45 from Google Calendar) still shows instead of disappearing.
const slotFor = (startAt: string) => {
  const time = formatTime(startAt)
  return [...timeOptions].reverse().find((slot) => slot <= time) ?? timeOptions[0]
}
const calendarAppointment = (therapistId: number, time: string) =>
  appointments.value.find(
    (item) =>
      item.therapist.id === therapistId &&
      item.startAt.slice(0, 10) === calendarDate.value &&
      slotFor(item.startAt) === time &&
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
const calendarPhone = (therapistId: number, time: string) => {
  const appointment = calendarAppointment(therapistId, time)
  return appointment ? patientPhone(appointment) : ''
}
function moveCalendarDate(days: number) {
  const date = new Date(`${calendarDate.value}T12:00:00+07:00`)
  date.setDate(date.getDate() + days)
  selectedDate.value = date.toISOString().slice(0, 10)
}
const toStartAt = () => `${form.value.date}T${form.value.time}:00+07:00`
async function load() {
  try {
    const [a, b, p, s, t] = await Promise.all([
      appointmentApi.search({ page: page.value, size: 20, query: query.value }),

      branchApi.list(),
      patientApi.searchAll(),
      catalogApi.services(),
      catalogApi.therapists(),
    ])
    appointments.value = a.items
    totalPages.value = a.totalPages
    branches.value = b
    patients.value = p
    services.value = s
    therapists.value = t
    if (branches.value[0] && !form.value.branchCode)
      form.value.branchCode = branches.value[0].branchCode

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
// Appointments can change outside this page (moved or cancelled in Google Calendar), so refresh the list quietly.
const refreshIntervalMs = 15000
let refreshTimer: ReturnType<typeof setInterval> | undefined
async function refreshAppointments() {
  if (document.hidden) return
  try {
    const a = await appointmentApi.search({ page: page.value, size: 20, query: query.value })
    appointments.value = a.items
    totalPages.value = a.totalPages
  } catch {
    // keep what is on screen; the next tick tries again
  }
}
const refreshWhenVisible = () => {
  if (!document.hidden) void refreshAppointments()
}
const googleCalendarUrl = (calendarId: string) =>
  `https://calendar.google.com/calendar/embed?src=${encodeURIComponent(calendarId)}&ctz=Asia%2FBangkok`
// The main button opens the calendar of the branch picked in the filter, or all connected branches side by side.
const googleTargets = computed(() =>
  (googleStatus.value?.branches ?? []).filter(
    (branch) =>
      branch.googleCalendarId &&
      (selectedBranch.value === 'ทั้งหมด' || branch.branchCode === selectedBranch.value),
  ),
)
const googleOpenUrl = computed(
  () =>
    'https://calendar.google.com/calendar/embed?' +
    googleTargets.value
      .map((branch) => `src=${encodeURIComponent(branch.googleCalendarId ?? '')}`)
      .join('&') +
    '&ctz=Asia%2FBangkok',
)
const googleOpenLabel = computed(() =>
  selectedBranch.value === 'ทั้งหมด' ? 'ทุกสาขา' : (googleTargets.value[0]?.branchName ?? 'ทุกสาขา'),
)
async function openGoogleCalendar() {
  calendarSyncOpen.value = true
  googleMessage.value = ''
  try {
    googleStatus.value = await calendarApi.status()
  } catch (error) {
    googleMessage.value = error instanceof ApiError ? error.message : 'ตรวจสถานะ Google Calendar ไม่สำเร็จ'
  }
}
async function syncGoogleNow() {
  googleBusy.value = true
  googleMessage.value = ''
  try {
    const result = await calendarApi.syncNow()
    googleMessage.value =
      `ซิงก์แล้ว: ส่งไป Google ${result.pushed}, เลื่อนจาก Google ${result.movedFromGoogle}, ` +
      `ยกเลิกจาก Google ${result.cancelledFromGoogle}, ล้มเหลว ${result.failed}`
    googleStatus.value = await calendarApi.status()
    await refreshAppointments()
  } catch (error) {
    googleMessage.value = error instanceof ApiError ? error.message : 'ซิงก์ไม่สำเร็จ'
  } finally {
    googleBusy.value = false
  }
}
async function loadGoogleStatus() {
  try {
    googleStatus.value = await calendarApi.status()
  } catch {
    // the main button simply stays hidden when the status cannot be read
  }
}
onMounted(async () => {
  await load()
  void loadGoogleStatus()
  refreshTimer = setInterval(() => void refreshAppointments(), refreshIntervalMs)
  document.addEventListener('visibilitychange', refreshWhenVisible)
  const editId = Number(route.params.id || route.query.edit)
  if (editId) {
    try {
      const appointment = appointments.value.find((item) => item.id === editId) ?? await appointmentApi.get(editId)
      openEdit(appointment)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'โหลดนัดหมายไม่สำเร็จ'
    }
  } else if (route.name === 'staff-appointment-create' || route.query.create === 'calendar') {
    openCreate(route.query.source === 'calendar' || route.query.create === 'calendar' ? 'calendar' : 'standard')
    if (typeof route.query.date === 'string') form.value.date = route.query.date
    if (typeof route.query.time === 'string') form.value.time = route.query.time
    const therapistId = Number(route.query.therapistId)
    if (therapistId) form.value.therapistId = therapistId
  }
})
onBeforeUnmount(() => {
  clearInterval(refreshTimer)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
})
let searchTimer: number | undefined
watch(query, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { page.value = 0; void load() }, 250)
})
onUnmounted(() => window.clearTimeout(searchTimer))
async function changePage(nextPage: number) {
  if (nextPage >= 0 && nextPage < totalPages.value) {
    page.value = nextPage
    await load()
  }
}
let availabilityRequest = 0
async function loadAvailability() {
  const { branchCode, therapistId, date, serviceId } = form.value
  if (viewMode.value === 'list' || !branchCode || !therapistId || !date) {
    availability.value = null
    return
  }
  const request = ++availabilityRequest
  try {
    const result = await appointmentApi.availability({
      branchCode,
      therapistId,
      date,
      serviceId: serviceId || undefined,
      excludeAppointmentId: editingId.value ?? undefined,
    })
    if (request !== availabilityRequest) return
    availability.value = result
    // A time that is no longer free is replaced by the first free one (an edited appointment keeps its own time).
    const chosen = result.slots.find((slot) => slot.time === form.value.time)
    if (!chosen?.available && !editingId.value) {
      form.value.time = result.slots.find((slot) => slot.available)?.time ?? ''
    }
  } catch {
    if (request === availabilityRequest) availability.value = null
  }
}
watch(
  () =>
    [
      viewMode.value,
      form.value.branchCode,
      form.value.therapistId,
      form.value.date,
      form.value.serviceId,
    ] as const,
  () => void loadAvailability(),
)
function openCreate(source: 'standard' | 'calendar' = 'standard') {
  if (!appointmentMasterReady.value) return
  editingId.value = null
  createSource.value = source
  appointmentPatientType.value = 'CURRENT'
  contactPhone.value = ''
  requestedServiceType.value = 'SingleVisit'

  patientQuery.value = ''
  newPatientForm.value = { firstName: '', lastName: '', nickname: '', phone: '' }
  showPatientResults.value = false
  const branch = isManager ? branchOptions.value[0] : branchOptions.value.find((item) => item.branchCode === homeBranch)
  const branchCode = branch?.branchCode ?? ''

  form.value = {
    patientId: 0,
    therapistId: therapists.value.find((item) => item.branchCode === branchCode)?.id ?? 0,
    serviceId: 0,
    branchCode,
    date: clinicToday(),
    time: '09:00',

    note: '',
  }
  viewMode.value = 'create'
  window.scrollTo({ top: 0, behavior: 'auto' })
}
function openCalendarSlot(therapist: Therapist, time: string) {
  const item = calendarAppointment(therapist.id, time)
  if (item) {
    if (item.status === 'CONFIRMED') continueAppointment(item)
    else if (uiStatusFor(item.status) === 'PENDING') openEdit(item)
    return
  }
  openCreate('calendar')
  form.value.therapistId = therapist.id
  form.value.date = calendarDate.value
  form.value.time = time
  form.value.branchCode = therapist.branchCode || form.value.branchCode

}
function openEdit(item: Appointment) {
  appointmentPatientType.value = patientTypeFor(item)
  editingId.value = item.id
  createSource.value = 'standard'
  contactPhone.value = item.contactPhone ?? ''
  patientQuery.value = patientTypeFor(item) === 'NEW'
    ? item.contactName ?? item.patient.fullName
    : `HN ${item.patient.hn} · ${patientPhone(item)}`

  showPatientResults.value = false
  form.value = {
    patientId: item.patient.id ?? 0,
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
    const creatingNew = !editingId.value && appointmentPatientType.value === 'NEW'
    if (!creatingNew && !editingId.value && !patient)
      throw new Error('กรุณากรอก HN หรือชื่อผู้ป่วยให้ตรงกับรายการในระบบ')
    form.value.patientId = patient?.id ?? form.value.patientId
    const editingNew = editingId.value !== null && editingAppointment.value?.patientType === 'NEW'
    if (!creatingNew && !editingNew && !form.value.patientId) throw new Error('ไม่พบผู้ป่วยที่ระบุ')
    if (creatingNew && (!patientQuery.value.trim() || !contactPhone.value.trim())) throw new Error('ต้องระบุชื่อเล่นและเบอร์โทรผู้ป่วยใหม่')
    if (!form.value.therapistId) throw new Error('กรุณาเลือกนักกายภาพ')
    if (!form.value.time) throw new Error('กรุณาเลือกเวลาที่ว่าง')


    const wasEditing = editingId.value !== null
    let savedAppointment: Appointment
    if (editingId.value) {
      savedAppointment = await appointmentApi.reschedule(editingId.value, {
        startAt: toStartAt(),
        therapistId: form.value.therapistId,
        note: form.value.note,
      })
    } else {
      savedAppointment = await appointmentApi.create({
        branchCode: form.value.branchCode,
        patientId: form.value.patientId,
        therapistId: form.value.therapistId,
        serviceId: form.value.serviceId || undefined,
        startAt: toStartAt(),
        note: form.value.note,
        ...(creatingNew ? { patientType: 'NEW' as const, contactName: patientQuery.value,
          contactPhone: contactPhone.value, requestedServiceType: requestedServiceType.value, status: 'Unconfirmed' }
          : { patientType: 'CURRENT' as const }),
      })
    }

    selectedBranch.value = 'ทั้งหมด'
    selectedDate.value = ''
    startDate.value = ''
    endDate.value = ''
    sortOrder.value = 'newest'
    page.value = 0
    viewMode.value = 'list'
    await router.replace({ name: 'staff-appointments' })
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
function continueAppointment(item: Appointment) {
  if (patientTypeFor(item) === 'NEW') {
    void router.push({ path: '/staff/patients/new', query: { appointmentId: item.id } })
  } else if (item.patient.id != null) {
    void router.push({ path: '/staff/services', query: { patientId: item.patient.id, appointmentId: item.id } })
  }

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
          <button
            class="primary hero-action"
            :disabled="!appointmentMasterReady"
            @click="goToCreate"
          >
            + สร้างนัดหมาย
          </button>
          <button class="calendar-entry" type="button" @click="scheduleView = 'day'">

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
            {{ currentBranchLabel }}<template v-if="!isCalendarCreate">
              • บัญชี Therapist ถูกจำกัดเฉพาะสาขานี้</template
            >
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
                HN {{ selectedFormPatient?.hn || editingAppointment?.patient.hn || '-' }} ·
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
                :options="services.map((service) => ({ value: String(service.id), label: service.name }))"
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
                :options="therapists.map((therapist) => ({ value: String(therapist.id), label: therapist.fullName }))"
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
            <label class="reschedule-branch-field">
              สาขา
              <FilterDropdown
                v-model="formBranchModel"
                class="appointment-form-filter"
                label="เลือกสาขา"
                default-label="เลือกสาขา"
                :clear-on-reselect="false"
                :options="branchOptions.map((branch) => ({ value: branch.branchCode, label: branch.name }))"
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
            <label class="appointment-patient-field">
              {{ appointmentPatientType === 'NEW' ? 'ชื่อเล่น' : 'ผู้ป่วย' }} <em>*</em>

              <div class="appointment-patient-picker">
                <input
                  v-model.trim="patientQuery"
                  :readonly="isEditing"
                  :class="{ 'is-invalid': patientError }"
                  :placeholder="
                    appointmentPatientType === 'NEW' ? 'กรอกชื่อเล่นผู้ป่วยใหม่' : 'กรอกชื่อผู้ป่วยหรือรหัส HN'
                  "
                  required
                  @focus="showPatientResults = true"
                  @input="changePatientQuery"
                  @blur="hidePatientResults"
                />
                <div
                  v-if="showPatientResults && patientQuery && !isEditing && appointmentPatientType === 'CURRENT'"
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
            <label v-if="appointmentPatientType === 'CURRENT'" class="appointment-service-field">
              ประเภทบริการ (ไม่บังคับ)

              <FilterDropdown
                v-model="formServiceModel"
                class="appointment-form-filter"
                label="เลือกบริการ"
                default-label="ยังไม่ระบุบริการ"
                :clear-on-reselect="false"
                :options="services.map((service) => ({ value: String(service.id), label: service.name }))"
              />
            </label>
            <label v-if="appointmentPatientType === 'NEW' && !isEditing">เบอร์โทร <em>*</em><input v-model.trim="contactPhone" placeholder="เบอร์โทรผู้ป่วยใหม่" /></label>
            <label v-if="appointmentPatientType === 'NEW' && !isEditing">ประเภทบริการ<FilterDropdown v-model="requestedServiceType" label="เลือกประเภทบริการ" default-label="เลือกประเภทบริการ" :options="[{value:'SingleVisit',label:'รายครั้ง'},{value:'Course',label:'รายคอร์ส'}]" :clear-on-reselect="false" /></label>

            <label class="appointment-therapist-field">
              นักกายภาพ <em>*</em>
              <FilterDropdown
                v-model="formTherapistModel"
                class="appointment-form-filter"
                label="เลือกนักกายภาพ"
                :disabled="isEditing"
                default-label="เลือกนักกายภาพ"
                :clear-on-reselect="false"
                :options="
                  bookingTherapists.map((therapist) => ({
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
              เวลา <em>*</em>
              <FilterDropdown
                v-model="formTimeModel"
                class="appointment-form-filter"
                label="เลือกเวลา"
                default-label="เลือกเวลา"
                :clear-on-reselect="false"
                :options="timeChoices.map((time) => ({ value: time, label: time }))"
              />
            </label>
            <label v-if="isManager" class="appointment-branch-field">
              สาขา
              <FilterDropdown
                v-model="formBranchModel"
                class="appointment-form-filter"
                label="เลือกสาขา"
                :disabled="isEditing"
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
            <label class="wide appointment-note-field"
              >หมายเหตุ<textarea
                v-model.trim="form.note"
                maxlength="500"
                rows="3"
                placeholder="ระบุรายละเอียดเพิ่มเติม"
              />
            </label>
            <article v-if="serviceMode === 'course' && selectedPatientCourse" class="appointment-course-summary">
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
                ผู้ให้สิทธิ์: {{ selectedPatientCourse.sourcePatientName || 'รอข้อมูลจากระบบ' }}
              </strong>
              <strong v-else>
                นักกายภาพประจำคอร์ส: {{ selectedTherapist?.fullName || 'กรุณาเลือกนักกายภาพ' }}
              </strong>
              <p v-if="selectedPatientCourse.shared">
                นักกายภาพเดิม: {{ selectedPatientCourse.originalTherapistName || 'รอข้อมูลจากระบบ' }}
              </p>
              <small>{{ selectedPatientCourse.shared ? 'เลือกนักกายภาพเดิมหรือคนอื่นได้' : 'เลือกเวลาว่างของนักกายภาพในขั้นตอนถัดไป' }}</small>
            </article>
          </div>
          <div class="appointment-save">
            <button v-if="!isEditing" class="secondary" type="button" @click="goToAppointmentList">ยกเลิก</button>
            <button class="primary" :disabled="savingAppointment" @click="requestSaveConfirmation">
              {{ savingAppointment ? 'กำลังบันทึก...' : isEditing ? 'บันทึก' : 'บันทึกนัดหมาย' }}
            </button>
          </div>
        </section>
      </div>
      <div v-if="timeDrawerOpen" class="appointment-time-drawer-backdrop" @click.self="timeDrawerOpen = false">
        <aside class="appointment-time-drawer" role="dialog" aria-modal="true" aria-labelledby="time-drawer-title">
          <div class="appointment-time-drawer-content">
            <h2 id="time-drawer-title">เลือกเวลาว่าง</h2>
            <p>{{ drawerServiceLabel }}</p>
            <strong>นักกายภาพ: {{ selectedTherapist?.fullName || 'ยังไม่ได้เลือก' }}　|　{{ currentBranchLabel }}</strong>
            <label>
              วันที่
              <ThaiDatePicker v-model="form.date" show-calendar-icon numeric-month hide-caret @update:model-value="changeSchedule" />
            </label>
            <section>
              <h3>ช่วงเช้า</h3>
              <div class="appointment-time-slots">
                <button
                  v-for="slot in morningSlots"
                  :key="slot.time"
                  type="button"
                  :disabled="!slot.available"
                  :title="slot.reason ?? undefined"
                  :class="{ selected: form.time === slot.time }"
                  @click="selectAppointmentTime(slot.time)"
                >
                  {{ formatTimeRange(slot.time) }}
                </button>
              </div>
            </section>
            <section>
              <h3>ช่วงบ่าย</h3>
              <div class="appointment-time-slots">
                <button
                  v-for="slot in afternoonSlots"
                  :key="slot.time"
                  type="button"
                  :disabled="!slot.available"
                  :title="slot.reason ?? undefined"
                  :class="{ selected: form.time === slot.time }"
                  @click="selectAppointmentTime(slot.time)"
                >
                  {{ formatTimeRange(slot.time) }}
                </button>
              </div>
            </section>
            <p class="appointment-time-help">
              ช่วงสีเทาเลือกไม่ได้: นักกายภาพมีนัดอื่นหรือติด Busy ใน Google Calendar, เลยเวลาแล้ว หรืออยู่นอกช่วงเปิดรับนัดของสาขา
              <template v-if="availability && !availability.windowConfigured">
                (สาขานี้ยังไม่ได้ตั้งช่วงเปิดรับนัด จึงแสดง {{ availability.openTime }}–{{ availability.closeTime }} เป็นค่าเริ่มต้น)
              </template>
            </p>
          </div>
          <footer>
            <button type="button" class="secondary" @click="timeDrawerOpen = false">ยกเลิก</button>
            <button type="button" class="primary" @click="confirmAppointmentTime">ยืนยันเวลา</button>
          </footer>
        </aside>
      </div>
      <section v-if="viewMode === 'list'" class="staff-panel appointment-panel">
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <label v-show="scheduleView === 'list'" class="appointment-list-search">
          ค้นหานัดหมาย
          <input v-model="query" type="search" placeholder="ค้นหาชื่อ เบอร์โทร หรือรหัส HN" />
        </label>
        <div v-show="scheduleView === 'list'" class="appointment-list-controls">
          <div class="appointment-list-mode-toggle" aria-label="เลือกรูปแบบรายการ">
            <button type="button" :class="{ active: listServiceMode === 'single' }" @click="listServiceMode = 'single'">รายครั้ง</button>
            <button type="button" :class="{ active: listServiceMode === 'course' }" @click="listServiceMode = 'course'">รายคอร์ส</button>
          </div>
          <header class="panel-head appointment-list-filters">
            <label class="appointment-date-range"><span>วันที่เริ่มต้น</span><ThaiDatePicker v-model="startDate" numeric-month show-calendar-icon hide-caret placeholder="วว/ดด/ปปปป" /></label>
            <label class="appointment-date-range"><span>วันที่สิ้นสุด</span><ThaiDatePicker v-model="endDate" numeric-month show-calendar-icon hide-caret placeholder="วว/ดด/ปปปป" /></label>
          <FilterDropdown
            v-model="patientFilterModel"
            label="กรองประเภทคนไข้"
            default-value="ทั้งหมด"
            default-label="คนไข้ทั้งหมด"
            :options="patientFilterOptions.map(([value, label]) => ({ value, label }))"
          />
          <FilterDropdown
            v-model="statusFilterModel"
            label="กรองตามสถานะนัดหมาย"
            default-value="ทั้งหมด"
            default-label="สถานะทั้งหมด"
            :options="statusFilterOptions.map(([value, label]) => ({ value, label }))"
          />
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
                therapists.map((item) => ({ value: String(item.id), label: item.fullName }))
              "
            />
            <FilterDropdown
              v-if="isManager"
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
            <a
              v-if="googleStatus?.enabled && googleTargets.length"
              class="calendar-google"
              :href="googleOpenUrl"
              target="_blank"
              rel="noopener noreferrer"
              data-cy="google-open-main"
              >เปิด Google Calendar ({{ googleOpenLabel }})</a
            >
            <button class="calendar-google" type="button" @click="openGoogleCalendar">
              สถานะ Google Calendar
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
                    <strong
                      >{{ formatTime(calendarAppointment(therapist.id, time)!.startAt) }}
                      {{ calendarPatientName(therapist.id, time) }}</strong
                    >
                    <small>{{ calendarPhone(therapist.id, time) }}</small>
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

                <td>
                  <span class="appointment-status-filter" :data-status="uiStatusFor(item.status)">{{ formatStatus(item.status) }}</span>
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
                  <button v-if="uiStatusFor(item.status) === 'PENDING'" class="appointment-edit" @click="goToEdit(item)">แก้ไข</button>
                  <button v-if="item.status === 'CONFIRMED'" class="appointment-edit" @click="continueAppointment(item)">{{ patientTypeFor(item) === 'NEW' ? 'ลงทะเบียน' : 'ใช้บริการ' }}</button>
                </td>
              </tr>
              <tr v-if="filteredAppointments.length === 0">
                <td class="appointment-empty" colspan="9">ไม่พบข้อมูลนัดหมาย</td>

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
        <h2>สถานะ Google Calendar</h2>
        <div class="calendar-sync-description" data-cy="google-sync-info">
          <p>ระบบซิงก์นัดหมายกับ Google Calendar 2 ทาง แยก 1 ปฏิทินต่อสาขา เลื่อนหรือยกเลิกนัดใน Google แล้วระบบจะตามให้เอง</p>
          <p v-if="!isManagerUser">
            ผู้จัดการเป็นผู้เชื่อมบัญชี Google กลางและตั้งปฏิทินของแต่ละสาขา คุณดูสถานะและเปิดปฏิทินของสาขาที่คลินิกแชร์ให้ได้
          </p>
          <template v-if="googleStatus">
            <p v-if="!googleStatus.enabled">
              ยังไม่ได้เปิดใช้งานการซิงก์บนเซิร์ฟเวอร์ ให้ผู้ดูแลระบบตั้งค่า APP_CALENDAR_ENABLED ก่อน
            </p>
            <ul v-else class="calendar-sync-branches">
              <li v-for="branch in googleStatus.branches" :key="branch.branchCode">
                {{ branch.branchName }}:
                {{ branch.googleCalendarId ? 'เชื่อมต่อแล้ว' : 'ยังไม่ได้ผูกปฏิทิน' }}
                <template v-if="branch.googleCalendarId">
                  (รอซิงก์ {{ branch.waiting }}, ล้มเหลว {{ branch.failed }})
                  <a
                    :href="googleCalendarUrl(branch.googleCalendarId)"
                    target="_blank"
                    rel="noopener noreferrer"
                    :data-cy="`google-open-${branch.branchCode}`"
                    >เปิดใน Google Calendar</a
                  >
                </template>
              </li>
            </ul>
          </template>
          <p v-if="googleMessage" data-cy="google-sync-message">{{ googleMessage }}</p>
        </div>
        <div class="calendar-sync-actions">
          <button type="button" class="secondary" @click="calendarSyncOpen = false">ปิด</button
          ><template v-if="isManagerUser"
            ><router-link class="secondary" to="/staff/calendar" @click="calendarSyncOpen = false"
              >ตั้งค่าปฏิทิน</router-link
            ><button
              type="button"
              class="primary"
              data-cy="google-sync-now"
              :disabled="googleBusy || !googleStatus?.enabled"
              @click="syncGoogleNow"
            >
              {{ googleBusy ? 'กำลังซิงก์...' : 'ซิงก์ทันที' }}
            </button></template
          >
        </div>
      </section>
    </div>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
