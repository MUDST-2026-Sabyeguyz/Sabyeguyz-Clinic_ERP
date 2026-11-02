<script setup lang="ts">
import { clinicToday } from '@/utils/clinicDate'
import { readAppointmentRange } from '../appointmentList'
import { vModalFocus } from '../modalFocus'
import { loadAppointmentTherapists } from '../therapists'
import GoogleCalendarPanel from '../components/GoogleCalendarPanel.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import AppointmentDateFilter from '../components/AppointmentDateFilter.vue'
import {
  appointmentApi,
  branchApi,
  catalogApi,
  courseApi,
  patientApi,
  staffApi,
  type Appointment,
  type AppointmentAvailability,
  type AppointmentStatus,
  type Branch,
  type CoursePurchase,
  type Patient,
  type ServiceOption,
  type Therapist,
} from '@/api/client'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'
import { isContactPhoneValid, limitPhoneDigits } from '@/utils/phone'
import {
  appointmentEndFields,
  appointmentInterval,
  appointmentLocalFields,
} from '../appointmentInterval'

const authUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
  id?: number
  role?: string
  branchCode?: string
} | null
const isManager = authUser?.role === 'MANAGER'

const ownStaffId = ref<number | null>(null)
let identityPromise: Promise<void> | null = null
function loadStaffIdentity() {
  if (isManager || identityPromise) return identityPromise ?? Promise.resolve()
  identityPromise = staffApi
    .list()
    .then((items) => {
      ownStaffId.value = items.find((item) => item.userId === authUser?.id)?.id ?? null
    })
    .catch(() => {
      ownStaffId.value = null
      identityPromise = null
    })
  return identityPromise
}
function canManageOwnAppointment(item: Appointment) {
  return (
    isManager ||
    (authUser?.role === 'THERAPIST' &&
      ownStaffId.value === item.therapist.id &&
      authUser.branchCode === item.branchCode)
  )
}

const route = useRoute()
const router = useRouter()

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const createSource = ref<'standard' | 'calendar'>('standard')
const appointmentPatientType = ref<'CURRENT' | 'NEW'>('CURRENT')
const serviceMode = ref<'single' | 'course'>('course')
const patientCourses = ref<CoursePurchase[]>([])
const selectedCourseId = ref(0)
const timeDrawerOpen = ref(false)
const availabilitySlots = ref<AppointmentAvailability['slots']>([])
const availabilityLoading = ref(false)
const availabilityError = ref('')
let availabilityRequest = 0
const scheduleView = ref<'list' | 'day' | 'week'>('list')
const listServiceMode = ref<'all' | 'single' | 'course'>(
  route.query.type === 'single' || route.query.type === 'course' ? route.query.type : 'all',
)
const calendarSyncOpen = ref(false)
const selectedBranch = ref('ทั้งหมด')
const selectedDate = ref('')
const startDate = ref(typeof route.query.startDate === 'string' ? route.query.startDate : '')
const endDate = ref(typeof route.query.endDate === 'string' ? route.query.endDate : '')
const yesterday = new Date()
yesterday.setDate(yesterday.getDate() - 1)
const minimumAppointmentDate = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
type AppointmentUiStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'
const selectedTherapistId = ref('ทั้งหมด')
const sortOrder = ref<'newest' | 'oldest'>('oldest')
function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'oldest' ? 'newest' : 'oldest'
  page.value = 0
}

const appointments = ref<Appointment[]>([])
const formDataLoading = ref(false)
const formDataError = ref('')
let formDataScope: string | undefined
let formDataPromise: Promise<void> | null = null
async function ensureFormData() {
  const scope = managerBranchCode.value
  if (formDataPromise && formDataScope === scope) return formDataPromise
  formDataScope = scope
  patients.value = []
  services.value = []
  therapists.value = []
  formDataLoading.value = true
  formDataError.value = ''
  const pending = (async () => {
    try {
      const [branchList, patientList, serviceList, therapistList] = await Promise.all([
        branchApi.list(),
        patientApi.searchAll('', scope),
        catalogApi.services(),
        loadAppointmentTherapists(),
        loadStaffIdentity(),
      ])
      if (scope !== managerBranchCode.value) return
      branches.value = branchList
      patients.value = patientList
      services.value = serviceList
      therapists.value = therapistList.filter(
        (item) =>
          (!scope || item.branchCode === scope) && (isManager || item.id === ownStaffId.value),
      )
      if (!form.value.branchCode)
        form.value.branchCode = scope || branchOptions.value[0]?.code || ''
      // Leave the therapist unselected until explicitly chosen.
      // Loading options must preserve an empty selection until the user chooses a program.
    } catch {
      if (scope !== managerBranchCode.value) return
      formDataError.value = 'โหลดข้อมูลสำหรับนัดหมายไม่สำเร็จ'
      formDataPromise = null
    } finally {
      if (scope === managerBranchCode.value) formDataLoading.value = false
    }
  })()
  formDataPromise = pending
  return pending
}
let appointmentLoadRequest = 0
const page = ref(0)
const totalPages = computed(() => Math.ceil(filteredAppointments.value.length / 20))
const visiblePageNumbers = computed(() => {
  const last = totalPages.value - 1
  const pages = new Set([0, last])
  for (let index = Math.max(0, page.value - 1); index <= Math.min(last, page.value + 1); index++)
    pages.add(index)
  if (page.value < 2) for (let index = 0; index <= Math.min(last, 3); index++) pages.add(index)
  if (page.value > last - 2)
    for (let index = Math.max(0, last - 3); index <= last; index++) pages.add(index)
  return [...pages].filter((index) => index >= 0).sort((a, b) => a - b)
})
const listLoading = ref(false)
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
const originalEditingAppointment = ref<Appointment | null>(null)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const errorMessage = ref('')
const showRequiredValidation = ref(false)
const appointmentSuccessMessage = ref('')
const savingAppointment = ref(false)
const checkingAvailability = ref(false)
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
  endDate: new Date().toISOString().slice(0, 10),
  endTime: '',
  note: '',
})
const customEndAt = ref(false)
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
const editingAppointment = computed(
  () =>
    appointments.value.find((appointment) => appointment.id === editingId.value) ??
    (originalEditingAppointment.value?.id === editingId.value
      ? originalEditingAppointment.value
      : undefined),
)
const originalAppointmentSummary = computed(() => {
  const appointment = editingAppointment.value
  if (!appointment) return ''
  const time = formatTime(appointment.startAt).replace(/^0/, '')
  const name = branchName(appointment.branchCode).trim()
  const branch = name.startsWith('สาขา') ? name : `${name}`
  return `นัดเดิม ${formatThaiDate(appointment.startAt)} เวลา ${time} น. ${branch}`
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
  () => showRequiredValidation.value && !form.value.therapistId,
)
const missingCurrentDate = computed(() => showRequiredValidation.value && !form.value.date)
const missingCurrentTime = computed(() => showRequiredValidation.value && !form.value.time)
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
  availabilitySlots.value.filter((slot) => Number(slot.time.slice(0, 2)) < 12),
)
const afternoonTimeOptions = computed(() =>
  availabilitySlots.value.filter((slot) => Number(slot.time.slice(0, 2)) >= 12),
)
const filteredAppointments = computed(() => {
  const direction = sortOrder.value === 'newest' ? -1 : 1
  return appointments.value
    .filter(
      (item) =>
        listServiceMode.value === 'all' ||
        (listServiceMode.value === 'course'
          ? isCourseAppointment(item)
          : !isCourseAppointment(item)),
    )
    .sort(
      (left, right) =>
        direction *
        (new Date(left.startAt).getTime() - new Date(right.startAt).getTime() ||
          left.id - right.id),
    )
})
const pagedAppointments = computed(() =>
  filteredAppointments.value.slice(page.value * 20, (page.value + 1) * 20),
)
const listDateCaption = computed(() => {
  if (!startDate.value && !endDate.value) {
    if (listServiceMode.value === 'single') return 'นัดหมายรายครั้งทั้งหมด'
    if (listServiceMode.value === 'course') return 'นัดหมายรายคอร์สทั้งหมด'
    return 'นัดหมายทั้งหมด'
  }
  const format = (value: string) =>
    value
      ? new Intl.DateTimeFormat('th-TH', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        }).format(new Date(value + 'T12:00:00'))
      : 'ไม่จำกัดวันที่'
  return startDate.value === endDate.value
    ? format(startDate.value)
    : format(startDate.value) + ' – ' + format(endDate.value)
})
function applyDateRange(from: string, to: string) {
  const unchanged = from === startDate.value && to === endDate.value
  startDate.value = from
  endDate.value = to
  if (unchanged) void load()
}
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
    /คอร์ส|course/i.test(item.service.name ?? ''),
  )
}
watch(totalPages, (count) => {
  page.value = Math.min(page.value, Math.max(0, count - 1))
})
const patientPhone = (item: Appointment) =>
  patients.value.find((patient) => patient.id === item.patient?.id)?.phone ||
  item.contactPhone ||
  '-'
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
// the rows of the calendar grid: the usual times plus the time of every appointment (for example one moved in Google Calendar)
const calendarTimes = computed(() =>
  [
    ...new Set([...timeOptions, ...appointments.value.map((item) => formatTime(item.startAt))]),
  ].sort(),
)
const appointmentStatusOptions: [AppointmentUiStatus, string][] = [
  ['CONFIRMED', 'ยืนยัน'],
  ['CANCELLED', 'ยกเลิก'],
]
const appointmentStatusLabels: Record<AppointmentUiStatus, string> = {
  PENDING: 'ยังไม่ยืนยัน',
  CONFIRMED: 'ยืนยัน',
  CANCELLED: 'ยกเลิก',
}
function uiStatusFor(status: AppointmentStatus): AppointmentUiStatus {
  if (status === 'UNCONFIRMED') return 'PENDING'
  return status
}
const formServiceModel = computed({
  get: () => String(form.value.serviceId || ''),
  set: (value: string) => {
    form.value.serviceId = Number(value) || 0
    customEndAt.value = false
    setDefaultAppointmentEnd()
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
const dateInBangkok = (value: string) =>
  new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Bangkok',
  }).format(new Date(value))
const normalizeHn = (value: string) =>
  value
    .trim()
    .replace(/^hn[\s:#-]*/i, '')
    .replace(/[\s-]/g, '')
    .toLowerCase()
const formatTimeRange = (time: string) => {
  if (!time) return 'ยังไม่ได้เลือกเวลา'
  if (form.value.endTime) return `${time}–${form.value.endTime}`
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
      dateInBangkok(item.startAt) === calendarDate.value &&
      formatTime(item.startAt) === time &&
      (selectedBranch.value === 'ทั้งหมด' || item.branchCode === selectedBranch.value),
  )
const calendarSlotState = (item?: Appointment) => {
  if (!item) return 'empty'
  if (item.status === 'CANCELLED') return 'cancelled'
  if (item.status === 'CONFIRMED') return 'confirmed'
  return 'pending'
}
const calendarPatientName = (therapistId: number, time: string) =>
  calendarAppointment(therapistId, time)?.patient?.fullName ||
  calendarAppointment(therapistId, time)?.contactName ||
  ''
const calendarTherapistName = (therapistId: number, time: string) =>
  calendarAppointment(therapistId, time)?.therapist.fullName || ''
function moveCalendarDate(days: number) {
  const date = new Date(`${calendarDate.value}T00:00:00`)
  date.setDate(date.getDate() + days)
  selectedDate.value = date.toISOString().slice(0, 10)
}
const appointmentFormInterval = () =>
  appointmentInterval(form.value.date, form.value.time, form.value.endDate, form.value.endTime)
async function load() {
  const requestId = ++appointmentLoadRequest
  if (startDate.value && endDate.value && startDate.value > endDate.value) {
    listLoading.value = false
    errorMessage.value = 'วันเริ่มต้นต้องไม่อยู่หลังวันสิ้นสุด'
    return
  }
  listLoading.value = true
  errorMessage.value = ''
  try {
    const initialBranches = isManager && !managerBranchCode.value ? await branchApi.list() : null
    if (requestId !== appointmentLoadRequest) return
    if (initialBranches) {
      setManagerBranches(initialBranches.filter((branch) => branch.active))
      if (!managerBranchCode.value) throw new Error('ไม่พบสาขาที่เปิดใช้งาน')
    }
    void loadStaffIdentity()
    const result = await readAppointmentRange(
      {
        branchCode: managerBranchCode.value,
        startDate: startDate.value || undefined,
        endDate: endDate.value || undefined,
        sortOrder: sortOrder.value,
      },
      () => requestId === appointmentLoadRequest,
    )
    if (requestId !== appointmentLoadRequest) return
    appointments.value = result ?? []
    if (initialBranches) branches.value = initialBranches
  } catch {
    if (requestId !== appointmentLoadRequest) return
    appointments.value = []
    errorMessage.value = 'โหลดนัดหมายไม่สำเร็จ กรุณาลองใหม่'
  } finally {
    if (requestId === appointmentLoadRequest) listLoading.value = false
  }
}
onMounted(async () => {
  await load()
  await loadStaffIdentity()
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
watch(listServiceMode, () => {
  page.value = 0
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
  }
}
function openCreate(source: 'standard' | 'calendar' = 'standard') {
  void ensureFormData()
  editingId.value = null
  originalEditingAppointment.value = null
  createSource.value = source
  appointmentPatientType.value = 'CURRENT'
  serviceMode.value = 'single'
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
  const today = clinicToday()
  customEndAt.value = false
  form.value = {
    patientId: 0,
    therapistId: 0,
    serviceId: 0,
    branchCode,
    date: today,
    time: '',
    endDate: today,
    endTime: '',
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
  form.value.endDate = calendarDate.value
  form.value.time = time
  setDefaultAppointmentEnd()
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
  if (!canManageOwnAppointment(item)) {
    errorMessage.value = 'จัดการได้เฉพาะนัดหมายของตัวเอง'
    return
  }
  void ensureFormData()
  editingId.value = item.id
  originalEditingAppointment.value = item
  createSource.value = 'standard'
  appointmentPatientType.value = item.patientType === 'NEW' || !item.patient ? 'NEW' : 'CURRENT'
  showRequiredValidation.value = false
  patientQuery.value = item.patient?.fullName ?? item.contactName ?? ''
  showPatientResults.value = false
  const startFields = appointmentLocalFields(item.startAt)
  const endFields = appointmentLocalFields(item.endAt)
  customEndAt.value = true
  form.value = {
    patientId: item.patient?.id ?? 0,
    therapistId: item.therapist.id ?? 0,
    serviceId: item.service.id ?? 0,
    branchCode: item.branchCode,
    date: startFields.date,
    time: startFields.time,
    endDate: endFields.date,
    endTime: endFields.time,
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
function changeAppointmentDate(date: string) {
  if (form.value.date !== date) {
    form.value.time = ''
    form.value.endDate = date
    form.value.endTime = ''
    customEndAt.value = false
  }
  form.value.date = date
  changeSchedule()
}
function setDefaultAppointmentEnd() {
  if (!form.value.date || !form.value.time || customEndAt.value) return
  const end = appointmentEndFields(
    form.value.date,
    form.value.time,
    selectedService.value?.durationMinutes || 60,
  )
  form.value.endDate = end.date
  form.value.endTime = end.time
}
function changeAppointmentEndDate(date: string) {
  form.value.endDate = date
  customEndAt.value = true
}
function selectAppointmentTime(time: string) {
  form.value.time = time
  customEndAt.value = false
  setDefaultAppointmentEnd()
  changeSchedule()
}
async function loadTimeAvailability() {
  const requestId = ++availabilityRequest
  availabilitySlots.value = []
  availabilityError.value = ''
  if (!form.value.branchCode || !form.value.therapistId || !form.value.date) {
    availabilityError.value = 'เลือกสาขา นักกายภาพ และวันที่ก่อน'
    return
  }
  availabilityLoading.value = true
  try {
    const result = await appointmentApi.availability({
      branchCode: form.value.branchCode,
      therapistId: form.value.therapistId,
      date: form.value.date,
      serviceId: form.value.serviceId || undefined,
      excludeAppointmentId: editingId.value ?? undefined,
    })
    if (requestId === availabilityRequest) availabilitySlots.value = result.slots
  } catch (error) {
    if (requestId === availabilityRequest) {
      availabilityError.value = error instanceof Error ? error.message : 'โหลดเวลาว่างไม่สำเร็จ'
    }
  } finally {
    if (requestId === availabilityRequest) availabilityLoading.value = false
  }
}
function openTimeDrawer() {
  timeDrawerOpen.value = true
  void loadTimeAvailability()
}
function confirmAppointmentTime() {
  timeDrawerOpen.value = false
}
watch(
  [
    () => form.value.date,
    () => form.value.therapistId,
    () => form.value.serviceId,
    () => form.value.branchCode,
  ],
  () => {
    if (timeDrawerOpen.value) void loadTimeAvailability()
  },
)
async function requestSaveConfirmation() {
  if (formDataLoading.value || formDataError.value) return
  showRequiredValidation.value = true
  const newPatientName =
    [newPatientForm.value.firstName, newPatientForm.value.lastName]
      .filter(Boolean)
      .join(' ')
      .trim() || newPatientForm.value.nickname.trim()
  if (
    appointmentPatientType.value === 'NEW' &&
    !editingId.value &&
    (!newPatientName || !isContactPhoneValid(newPatientForm.value.phone))
  ) {
    errorMessage.value = 'กรุณากรอกชื่อหรือชื่อเล่น และเบอร์โทรศัพท์ของผู้ป่วยใหม่'
    return
  }
  if (appointmentPatientType.value === 'CURRENT' && !form.value.patientId) {
    errorMessage.value = 'กรุณาเลือกผู้ป่วย'
    return
  }
  if (!form.value.therapistId || !form.value.date || !form.value.time) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ'
    return
  }
  const today = new Date()
  const todayValue = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  if (isEditing.value && form.value.date < todayValue) {
    errorMessage.value = 'ไม่สามารถเลื่อนนัดหมายไปวันย้อนหลังได้'
    return
  }
  if (
    !editingId.value &&
    appointmentPatientType.value === 'CURRENT' &&
    serviceMode.value === 'course' &&
    createSource.value !== 'calendar'
  ) {
    errorMessage.value = 'ยังไม่รองรับการผูกนัดหมายกับสิทธิ์คอร์ส กรุณาใช้บริการรายครั้ง'
    return
  }
  if (savingAppointment.value || checkingAvailability.value) return
  const original = originalEditingAppointment.value
  const scheduleChanged =
    !original ||
    form.value.date !== original.startAt.slice(0, 10) ||
    form.value.time !== formatTime(original.startAt) ||
    form.value.therapistId !== original.therapist.id ||
    form.value.serviceId !== (original.service.id ?? 0) ||
    form.value.branchCode !== original.branchCode
  if (!scheduleChanged) {
    pendingSaveConfirmation.value = true
    return
  }
  checkingAvailability.value = true
  try {
    const availability = await appointmentApi.availability({
      branchCode: form.value.branchCode,
      therapistId: form.value.therapistId,
      date: form.value.date,
      serviceId: form.value.serviceId || undefined,
      excludeAppointmentId: editingId.value ?? undefined,
    })
    const slot = availability.slots.find((item) => item.time === form.value.time)
    if (!slot?.available) {
      errorMessage.value = slot?.reason
        ? `เลือกเวลานี้ไม่ได้: ${slot.reason}`
        : 'เวลานี้ไม่อยู่ในช่วงเปิดรับนัด กรุณาเลือกเวลาใหม่'
      return
    }
    pendingSaveConfirmation.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ตรวจสอบเวลานัดไม่สำเร็จ'
  } finally {
    checkingAvailability.value = false
  }
}
function confirmSave() {
  pendingSaveConfirmation.value = false
  void save()
}
async function save() {
  errorMessage.value = ''
  savingAppointment.value = true
  try {
    const isNewPatient = appointmentPatientType.value === 'NEW'
    const isNewBooking = isNewPatient && !editingId.value
    const normalized = patientQuery.value.trim().toLowerCase()
    const normalizedHn = normalizeHn(patientQuery.value)
    const patient = isNewPatient
      ? undefined
      : patients.value.find(
          (item) =>
            normalizeHn(item.hn) === normalizedHn ||
            item.fullName.toLowerCase() === normalized ||
            String(item.id) === normalized ||
            `${item.fullName} hn ${item.hn}`.toLowerCase() === normalized,
        )
    if (!isNewPatient && !editingId.value && !patient)
      throw new Error('กรุณากรอก HN หรือชื่อผู้ป่วยให้ตรงกับรายการในระบบ')
    if (!isNewPatient) form.value.patientId = patient?.id ?? form.value.patientId
    if (!isNewPatient && !form.value.patientId) throw new Error('ไม่พบผู้ป่วยที่ระบุ')
    const contactName =
      [newPatientForm.value.firstName, newPatientForm.value.lastName]
        .filter(Boolean)
        .join(' ')
        .trim() || newPatientForm.value.nickname.trim()
    if (isNewBooking && (!contactName || !isContactPhoneValid(newPatientForm.value.phone)))
      throw new Error('กรุณากรอกชื่อหรือชื่อเล่น และเบอร์โทรศัพท์ของผู้ป่วยใหม่')
    if (!form.value.therapistId) throw new Error('กรุณาเลือกนักกายภาพ')
    if (!form.value.date || !form.value.time || !form.value.endDate || !form.value.endTime)
      throw new Error('กรุณาเลือกวันและเวลาเริ่มกับจบนัดให้ครบถ้วน')
    if (new Date(`${form.value.date}T00:00:00+07:00`).getUTCDay() === 0)
      throw new Error('ไม่สามารถนัดหมายในวันอาทิตย์ได้')
    const interval = appointmentFormInterval()
    if (Date.parse(interval.startAt) < Date.now()) throw new Error('ไม่สามารถจองเวลาย้อนหลังได้')

    const wasEditing = editingId.value !== null
    let savedAppointment: Appointment
    if (editingId.value) {
      savedAppointment = await appointmentApi.reschedule(editingId.value, {
        startAt: interval.startAt,
        endAt: interval.endAt,
        therapistId: form.value.therapistId,
        serviceId: form.value.serviceId || undefined,
        branchCode: form.value.branchCode,
        note: form.value.note,
      })
    } else {
      savedAppointment = await appointmentApi.create({
        branchCode: form.value.branchCode,
        patientId: isNewPatient ? undefined : form.value.patientId,
        therapistId: form.value.therapistId,
        serviceId: form.value.serviceId || undefined,
        startAt: interval.startAt,
        endAt: interval.endAt,
        note: form.value.note,
        ...(isNewBooking
          ? {
              patientType: 'NEW' as const,
              contactName,
              contactPhone: newPatientForm.value.phone,
              requestedServiceType: serviceMode.value === 'course' ? 'Course' : 'SingleVisit',
            }
          : {}),
      })
    }

    selectedBranch.value = 'ทั้งหมด'
    selectedDate.value = ''
    startDate.value = ''
    endDate.value = ''
    sortOrder.value = 'oldest'
    page.value = 0
    viewMode.value = 'list'
    await load()
    appointments.value = [
      savedAppointment,
      ...appointments.value.filter((item) => item.id !== savedAppointment.id),
    ]
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
async function saveCancellation(item: Appointment, reason: string) {
  savingStatusId.value = item.id
  statusNotice.value = 'กำลังบันทึก...'
  try {
    const result = await appointmentApi.cancel(item.id, reason)
    Object.assign(item, result)
    statusNotice.value = 'บันทึกแล้ว'
    return true
  } catch (error) {
    statusNotice.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ'
    return false
  } finally {
    savingStatusId.value = null
    if (statusNotice.value === 'บันทึกแล้ว')
      window.setTimeout(() => {
        if (statusNotice.value === 'บันทึกแล้ว') statusNotice.value = ''
      }, 2200)
  }
}
async function confirmCancellation() {
  if (!pendingCancellation.value || !cancellationReason.value.trim()) return
  const { item } = pendingCancellation.value
  if (savingStatusId.value) return
  if (await saveCancellation(item, cancellationReason.value.trim()))
    pendingCancellation.value = null
}

async function requestStatusChange(item: Appointment, nextStatus: string) {
  if (item.status !== 'UNCONFIRMED' || !canManageOwnAppointment(item) || savingStatusId.value)
    return
  if (nextStatus === 'CONFIRMED') {
    if (item.patientType === 'NEW') {
      void router.push({
        name: 'patient-registration',
        query: {
          appointmentId: String(item.id),
          returnStartDate: startDate.value,
          returnEndDate: endDate.value,
          returnType: listServiceMode.value,
        },
      })
      return
    }
    const patientId = item.patient?.id
    if (!patientId) {
      statusNotice.value = 'ไม่พบ patientId ของนัดหมาย กรุณาตรวจสอบข้อมูลผู้ป่วย'
      return
    }
    void router.push({
      name: 'staff-services',
      query: { patientId: String(patientId), appointmentId: String(item.id) },
    })
    return
  }
  if (nextStatus === 'CANCELLED') {
    cancellationReason.value = ''
    pendingCancellation.value = { item }
    return
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
        <p>ตรวจตารางและจัดการนัดหมาย</p>
        <div class="appointment-hero-actions">
          <button class="primary hero-action" @click="goToCreate">+ สร้างนัดหมาย</button>
          <button
            class="calendar-entry"
            type="button"
            @click="router.push({ name: 'staff-appointment-calendar' })"
          >
            <svg
              class="appointment-calendar-icon"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              aria-hidden="true"
            >
              <rect x="3" y="5" width="18" height="16" rx="3" />
              <path d="M7 3v4m10-4v4M3 11h18" />
            </svg>
            ดูปฏิทินนัดหมาย
          </button>
        </div>
      </section>
      <div
        v-if="viewMode !== 'list'"
        class="appointment-form-page"
        :class="{ 'appointment-reschedule-modal': isEditing }"
        :role="isEditing ? 'dialog' : undefined"
        :aria-modal="isEditing ? true : undefined"
        :aria-label="isEditing ? 'เลื่อนนัดหมาย' : undefined"
        v-modal-focus="isEditing ? { close: goToAppointmentList, busy: savingAppointment } : null"
      >
        <div class="appointment-back-row">
          <button class="appointment-back" type="button" @click="goToAppointmentList">
            {{ isCalendarCreate ? '← กลับไปหน้าปฏิทิน' : '← กลับไปหน้านัดหมาย' }}
          </button>
        </div>
        <section
          class="staff-panel create-card appointment-create-card"
          :class="{ 'is-editing': isEditing }"
        >
          <p v-if="formDataLoading" role="status">กำลังโหลดข้อมูลนัดหมาย…</p>
          <p v-if="formDataError" role="alert">
            {{ formDataError }} <button type="button" @click="ensureFormData">ลองใหม่</button>
          </p>
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
              {{ currentBranchLabel }}
            </template>
            <template v-else>
              {{ currentBranchLabel }}
            </template>
          </p>
          <p v-if="isEditing" class="appointment-original-summary">
            {{ originalAppointmentSummary }}
          </p>
          <p
            v-if="errorMessage && !patientError && !scheduleError"
            class="appointment-form-error"
            role="alert"
          >
            {{ errorMessage }}
          </p>
          <div v-if="isEditing" class="form-grid appointment-reschedule-grid">
            <label class="reschedule-patient-field">
              ผู้ป่วย
              <input
                :value="patientQuery"
                :class="{ 'is-invalid': missingCurrentPatient }"
                readonly
                placeholder="กรอกชื่อผู้ป่วยหรือรหัส HN"
              />
              <span class="reschedule-patient-meta">
                HN
                {{
                  selectedFormPatient?.hn ||
                  editingAppointment?.patient?.hn ||
                  (appointmentPatientType === 'NEW' ? 'ผู้ป่วยไม่ประจำ' : '-')
                }}
                ·
                {{ selectedFormPatient?.phone || editingPatientPhone }}
              </span>
            </label>
            <label class="reschedule-service-field">
              ประเภทบริการ
              <FilterDropdown
                v-model="formServiceModel"
                searchable
                empty-search-label="ไม่พบโปรแกรม"
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
                searchable
                empty-search-label="ไม่พบนักกายภาพ"
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
            <label class="reschedule-date-field">
              วันที่
              <ThaiDatePicker
                :model-value="form.date"
                :min-date="minimumAppointmentDate"
                disable-sunday
                :invalid="scheduleError || missingCurrentDate"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeAppointmentDate"
              />
            </label>
            <label class="reschedule-time-field">
              เวลา
              <button
                class="reschedule-time-trigger"
                :class="{ 'is-invalid': missingCurrentTime }"
                :aria-invalid="missingCurrentTime"
                type="button"
                @click="openTimeDrawer"
              >
                <span>{{ formatTimeRange(form.time) }}</span>
                <strong>ดูตารางเวลาว่าง</strong>
              </button>
            </label>
            <label class="reschedule-end-date-field">
              วันที่จบ
              <ThaiDatePicker
                :model-value="form.endDate"
                :min-date="form.date"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeAppointmentEndDate"
              />
            </label>
            <label class="reschedule-end-time-field">
              เวลาจบ
              <input v-model="form.endTime" type="time" required @input="customEndAt = true" />
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
                  <p v-if="!patientResults.length">ไม่พบผู้ป่วยที่ค้นหา</p>
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
                <span>ชื่อ <small></small></span>
                <input
                  v-model.trim="newPatientForm.firstName"
                  autocomplete="given-name"
                  placeholder="กรอกชื่อ"
                />
              </label>
              <label>
                <span>นามสกุล <small></small></span>
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
                  @input="newPatientForm.phone = limitPhoneDigits(newPatientForm.phone)"
                />
              </label>
            </div>
            <fieldset class="appointment-service-mode-field">
              <legend>ประเภทบริการ <small></small></legend>
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
              {{ appointmentPatientType === 'NEW' ? 'บริการ / คอร์ส' : 'โปรแกรมการรักษา' }}
              <FilterDropdown
                v-model="formServiceModel"
                searchable
                empty-search-label="ไม่พบโปรแกรม"
                class="appointment-form-filter"
                label="เลือกบริการ"
                default-label="เลือกโปรแกรม"
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
              {{ appointmentPatientType === 'NEW' ? 'บริการ / คอร์ส' : 'รายชื่อคอร์ส' }}
              <FilterDropdown
                v-model="selectedCourseModel"
                searchable
                empty-search-label="ไม่พบโปรแกรม"
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
                ไม่พบคอร์สที่ใช้งานได้
              </span>
            </label>
            <label class="appointment-therapist-field">
              <span class="appointment-field-label">นักกายภาพ <em>*</em></span>
              <FilterDropdown
                v-model="formTherapistModel"
                searchable
                empty-search-label="ไม่พบนักกายภาพ"
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
              <span class="appointment-field-label">วันที่ <em>*</em></span>
              <ThaiDatePicker
                :model-value="form.date"
                :min-date="minimumAppointmentDate"
                disable-sunday
                :invalid="scheduleError || missingCurrentDate"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeAppointmentDate"
              />
              <p v-if="scheduleError" class="appointment-input-error" role="alert">
                {{ errorMessage }}
              </p>
            </label>
            <label class="appointment-time-field">
              <span class="appointment-field-label">เวลา <em>*</em></span>
              <span class="appointment-time-picker" :class="{ 'is-invalid': missingCurrentTime }">
                <span>{{ formatTimeRange(form.time) }}</span>
                <button type="button" @click="openTimeDrawer">ดูตารางเวลาว่าง</button>
              </span>
            </label>
            <label class="appointment-end-date-field">
              <span class="appointment-field-label">วันที่จบ <em>*</em></span>
              <ThaiDatePicker
                :model-value="form.endDate"
                :min-date="form.date"
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeAppointmentEndDate"
              />
            </label>
            <label class="appointment-end-time-field">
              <span class="appointment-field-label">เวลาจบ <em>*</em></span>
              <input v-model="form.endTime" type="time" required @input="customEndAt = true" />
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
                คงเหลือ {{ selectedPatientCourse.remaining }} ครั้ง　หมดอายุ
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
            <button
              class="primary"
              :disabled="savingAppointment || checkingAvailability"
              @click="requestSaveConfirmation"
            >
              {{
                savingAppointment
                  ? 'กำลังบันทึก...'
                  : checkingAvailability
                    ? 'กำลังตรวจเวลาว่าง...'
                    : isEditing
                      ? 'บันทึก'
                      : 'บันทึก'
              }}
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
          v-modal-focus="{
            close: () => {
              timeDrawerOpen = false
            },
          }"
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
                :model-value="form.date"
                :min-date="minimumAppointmentDate"
                disable-sunday
                show-calendar-icon
                numeric-month
                hide-caret
                @update:model-value="changeAppointmentDate"
              />
            </label>
            <section>
              <h3>ช่วงเช้า</h3>
              <div class="appointment-time-slots">
                <button
                  v-for="slot in morningTimeOptions"
                  :key="slot.time"
                  type="button"
                  :disabled="availabilityLoading || !slot.available"
                  :title="slot.reason || ''"
                  :class="{ selected: form.time === slot.time, unavailable: !slot.available }"
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
                  v-for="slot in afternoonTimeOptions"
                  :key="slot.time"
                  type="button"
                  :disabled="availabilityLoading || !slot.available"
                  :title="slot.reason || ''"
                  :class="{ selected: form.time === slot.time, unavailable: !slot.available }"
                  @click="selectAppointmentTime(slot.time)"
                >
                  {{ formatTimeRange(slot.time) }}
                </button>
              </div>
            </section>
            <p v-if="availabilityLoading" class="appointment-time-help" aria-live="polite">
              กำลังตรวจสอบเวลาว่าง...
            </p>
            <p v-else-if="availabilityError" class="appointment-time-help" role="alert">
              {{ availabilityError }}
            </p>
            <p v-else-if="availabilitySlots.length === 0" class="appointment-time-help">
              ไม่มีช่วงเวลาว่าง
            </p>
            <p class="appointment-time-help">ช่วงสีเทา = มีนัดหมายแล้ว ไม่สามารถเลือกได้</p>
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
        <p v-if="errorMessage" class="error" role="alert">
          {{ errorMessage }}
          <button class="appointment-retry" type="button" @click="load" :disabled="listLoading">
            ลองใหม่
          </button>
        </p>
        <div v-show="scheduleView === 'list'" class="appointment-list-controls">
          <div class="appointment-list-mode-toggle" role="group" aria-label="เลือกรูปแบบรายการ">
            <button
              type="button"
              :class="{ active: listServiceMode === 'all' }"
              :aria-pressed="listServiceMode === 'all'"
              @click="listServiceMode = 'all'"
            >
              ทั้งหมด
            </button>
            <button
              type="button"
              :class="{ active: listServiceMode === 'single' }"
              :aria-pressed="listServiceMode === 'single'"
              @click="listServiceMode = 'single'"
            >
              รายครั้ง
            </button>
            <button
              type="button"
              :class="{ active: listServiceMode === 'course' }"
              :aria-pressed="listServiceMode === 'course'"
              @click="listServiceMode = 'course'"
            >
              รายคอร์ส
            </button>
          </div>
          <header class="panel-head appointment-list-filters">
            <AppointmentDateFilter
              :from="startDate"
              :to="endDate"
            @apply="applyDateRange"
            />
          </header>
        </div>
        <div v-if="scheduleView === 'list'" class="appointment-list-summary">
          <p class="appointment-summary-date">{{ listDateCaption }}</p>
          <div class="appointment-summary-actions">
            <button
              class="appointment-sort"
              type="button"
              :title="
                sortOrder === 'oldest'
                  ? 'เก่าไปใหม่ — กดเพื่อเรียงใหม่ไปเก่า'
                  : 'ใหม่ไปเก่า — กดเพื่อเรียงเก่าไปใหม่'
              "
              :aria-label="
                sortOrder === 'oldest'
                  ? 'เรียงเก่าไปใหม่ กดเพื่อสลับเป็นใหม่ไปเก่า'
                  : 'เรียงใหม่ไปเก่า กดเพื่อสลับเป็นเก่าไปใหม่'
              "
              @click="toggleSortOrder"
            >
              {{ sortOrder === 'oldest' ? 'ล่าสุด ↓' : 'ล่าสุด ↑' }}
            </button>
            <span v-if="!listLoading && !errorMessage" class="appointment-summary-count">
              {{ filteredAppointments.length.toLocaleString('th-TH') }} นัดหมาย
            </span>
            <span v-if="listLoading" role="status">กำลังโหลดนัดหมาย…</span>
          </div>
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
                <b>เวลา</b><span v-for="time in calendarTimes" :key="time">{{ time }}</span>
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
                  v-for="time in calendarTimes"
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
        <div v-else class="appointment-table-scroll" :aria-busy="listLoading">
          <p v-if="statusNotice" class="appointment-list-notice" role="status" aria-live="polite">
            {{ statusNotice }}
          </p>
          <p v-if="listLoading" class="appointment-list-feedback" aria-hidden="true">
            กำลังโหลดนัดหมาย…
          </p>
          <p
            v-else-if="!errorMessage && filteredAppointments.length === 0"
            class="appointment-list-feedback"
            role="status"
          >
            {{
              startDate === clinicToday() && endDate === clinicToday()
                ? 'วันนี้ไม่มีนัดหมาย'
                : !startDate && !endDate
                  ? 'ยังไม่มีนัดหมาย'
                  : 'ไม่พบนัดหมายในช่วงวันที่เลือก'
            }}
          </p>
          <table
            v-if="!listLoading && !errorMessage && filteredAppointments.length > 0"
            class="staff-table"
          >
            <thead>
              <tr>
                <th scope="col" :aria-sort="sortOrder === 'oldest' ? 'ascending' : 'descending'">
                  วันที่
                </th>
                <th>เวลา</th>
                <th>ผู้ป่วย</th>
                <th>เบอร์โทรศัพท์</th>
                <th>บริการ</th>
                <th>นักกายภาพ</th>
                <th>สถานะ</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in listLoading || errorMessage ? [] : pagedAppointments"
                :key="item.id"
              >
                <td>{{ formatThaiDate(item.startAt) }}</td>
                <td>
                  <b>{{ formatTime(item.startAt) }}</b>
                </td>
                <td class="appointment-patient-cell">
                  <b>{{ item.patient?.fullName || item.contactName || 'ผู้ป่วยไม่ประจำ' }}</b>
                  <span v-if="item.patient?.hn" class="appointment-patient-hn"
                    >HN {{ item.patient.hn }}</span
                  >
                  <RouterLink
                    v-if="
                      item.status === 'UNCONFIRMED' &&
                      item.patientType === 'NEW' &&
                      canManageOwnAppointment(item)
                    "
                    class="appointment-register"
                    :to="{
                      name: 'patient-registration',
                      query: {
                        appointmentId: String(item.id),
                        returnStartDate: startDate,
                        returnEndDate: endDate,
                        returnType: listServiceMode,
                      },
                    }"
                    >ลงทะเบียนผู้ป่วย</RouterLink
                  >
                  <span
                    v-if="
                      !canManageOwnAppointment(item) &&
                      item.status === 'UNCONFIRMED' &&
                      item.patientType === 'NEW'
                    "
                    class="appointment-cancel-reason"
                    >ให้ผู้จัดการหรือเจ้าของนัดลงทะเบียน</span
                  >
                </td>
                <td>{{ patientPhone(item) }}</td>
                <td>{{ item.service.name || 'ยังไม่ระบุบริการ' }}</td>
                <td>{{ item.therapist.fullName }}</td>
                <td>
                  <div
                    v-if="item.status !== 'UNCONFIRMED' || !canManageOwnAppointment(item)"
                    class="appointment-status-filter appointment-status-readonly"
                    :data-status="uiStatusFor(item.status)"
                  >
                    {{ appointmentStatusLabels[uiStatusFor(item.status)] }}
                  </div>
                  <div
                    v-else
                    class="appointment-status-filter"
                    :data-status="uiStatusFor(item.status)"
                  >
                    <FilterDropdown
                      :model-value="uiStatusFor(item.status)"
                      :options="
                        appointmentStatusOptions
                          .filter(
                            ([value]) =>
                              value !== 'PENDING' || uiStatusFor(item.status) === 'PENDING',
                          )
                          .map(([value, label]) => ({
                            value,
                            label,
                          }))
                      "
                      :default-label="appointmentStatusLabels[uiStatusFor(item.status)]"
                      :label="`สถานะนัดหมายของ ${item.patient?.fullName || item.contactName || 'ผู้ป่วยไม่ประจำ'}`"
                      :clear-on-reselect="false"
                      :disabled="savingStatusId === item.id"
                      hide-default-option
                      @update:model-value="requestStatusChange(item, $event)"
                    />
                  </div>
                  <span
                    v-if="item.status === 'CANCELLED' && item.cancelReason"
                    class="appointment-cancel-reason"
                    >เหตุผล: {{ item.cancelReason }}</span
                  >
                  <button
                    v-if="canManageOwnAppointment(item) && uiStatusFor(item.status) === 'PENDING'"
                    class="appointment-edit"
                    @click="goToEdit(item)"
                  >
                    แก้ไข
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          v-if="
            scheduleView === 'list' &&
            !listLoading &&
            !errorMessage &&
            filteredAppointments.length > 0
          "
          class="appointment-pagination"
          aria-label="เปลี่ยนหน้ารายการนัดหมาย"
        >
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">
            ← ก่อนหน้า
          </button>
          <div class="appointment-page-choices">
            <span class="appointment-page-caption" aria-live="polite"
              >หน้า {{ page + 1 }} จาก {{ Math.max(totalPages, 1) }}</span
            >
            <div class="appointment-page-numbers" role="group" aria-label="เลือกหน้า">
              <template v-for="(number, index) in visiblePageNumbers" :key="number">
                <span
                  v-if="index > 0 && number - visiblePageNumbers[index - 1]! > 1"
                  aria-hidden="true"
                  >…</span
                >
                <button
                  type="button"
                  :aria-label="`หน้า ${number + 1}`"
                  :aria-current="page === number ? 'page' : undefined"
                  @click="changePage(number)"
                >
                  {{ number + 1 }}
                </button>
              </template>
            </div>
          </div>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
            ถัดไป →
          </button>
        </nav>
      </section>
    </main>
    <div
      v-if="pendingCancellation"
      class="status-modal-backdrop"
      @click.self="savingStatusId === null && (pendingCancellation = null)"
    >
      <form
        class="status-modal"
        role="dialog"
        aria-modal="true"
        aria-label="ยืนยันการยกเลิกนัดหมาย"
        v-modal-focus="{
          close: () => {
            pendingCancellation = null
          },
          busy: savingStatusId !== null,
        }"
        @submit.prevent="confirmCancellation"
      >
        <p v-if="statusNotice" role="status">{{ statusNotice }}</p>
        <h2>ยืนยันการยกเลิกนัดหมาย</h2>
        <p>
          {{
            pendingCancellation.item.patient?.fullName ||
            pendingCancellation.item.contactName ||
            'ผู้ป่วยไม่ประจำ'
          }}
          เวลา
          {{ formatTime(pendingCancellation.item.startAt) }}
        </p>
        <label
          >เหตุผลที่ยกเลิก <em>*</em>
          <textarea v-model.trim="cancellationReason" rows="3" required></textarea>
        </label>
        <div>
          <button
            type="button"
            class="secondary"
            :disabled="savingStatusId !== null"
            @click="pendingCancellation = null"
          >
            กลับ
          </button>
          <button
            type="submit"
            class="danger"
            :disabled="!cancellationReason.trim() || savingStatusId !== null"
          >
            ยืนยันการยกเลิก
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="pendingSaveConfirmation"
      class="status-modal-backdrop"
      @click.self="!savingAppointment && (pendingSaveConfirmation = false)"
    >
      <section
        class="status-modal appointment-confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="appointment-confirmation-title"
        v-modal-focus="{
          close: () => {
            pendingSaveConfirmation = false
          },
          busy: savingAppointment,
        }"
      >
        <h2 id="appointment-confirmation-title">
          {{ isEditing ? 'ยืนยันการเลื่อนนัดหมายหรือไม่?' : 'ยืนยันการสร้างนัดหมายหรือไม่?' }}
        </h2>
        <div>
          <button
            type="button"
            class="secondary"
            :disabled="savingAppointment"
            @click="pendingSaveConfirmation = false"
          >
            กลับไปแก้ไข
          </button>
          <button type="button" class="primary" :disabled="savingAppointment" @click="confirmSave">
            {{ savingAppointment ? 'กำลังบันทึก…' : 'ยืนยันนัดหมาย' }}
          </button>
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
        <GoogleCalendarPanel />
        <div class="calendar-sync-actions">
          <button type="button" class="secondary" @click="calendarSyncOpen = false">ปิด</button>
        </div>
      </section>
    </div>
  </div>
</template>
<style scoped src="../styles/manager-appointments.css"></style>
<style src="../styles/therapist.css"></style>

<style src="../styles/appointment-list.css"></style>
<style src="../styles/appointment-create-layout.css"></style>
