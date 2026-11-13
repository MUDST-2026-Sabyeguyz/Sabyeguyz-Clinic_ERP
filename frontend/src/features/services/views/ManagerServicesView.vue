<script setup lang="ts">
import { buildSaleRequest, allowedPaymentMethod } from '../careTransactions'
import { useAppointmentProcess } from '@/features/appointments/useAppointmentProcess'
import TodayAppointments from '../components/TodayAppointments.vue'
import AppointmentProcessPanel from '@/features/appointments/components/AppointmentProcessPanel.vue'
import { useCourseCommissionPreview } from '@/features/commission/useCourseCommissionPreview'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import PhoneIcon from '@/components/PhoneIcon.vue'
import paymentBankIcon from '@/assets/payment-bank.svg'
import paymentQrIcon from '@/assets/payment-qr.svg'
import paymentCardIcon from '@/assets/payment-card.svg'
import saleSuccessCheck from '@/assets/sale-success-check.svg'
import {
  ApiError,
  branchApi,
  catalogApi,
  courseApi,
  singleVisitEntitlementApi,
  patientApi,
  paymentMethodApi,
  paymentStatusApi,
  salesApi,
  staffApi,
  type Course,
  type CoursePurchase,
  type SingleVisitEntitlement,
  type Appointment,
  type Branch,
  type Patient,
  type PatientProfile,
  type PaymentMethod,
  type PaymentStatusOption,
  type ServiceOption,
  type Staff,
} from '@/api/client'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'
type Mode = 'assessment' | 'single' | 'course'
const isManager = JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
const route = useRoute()
const {
  appointmentId, sourceAppointment,
  appointmentProgress,
  processLoading,
  processError,
  refreshAppointmentProcess,
  canStartAppointment, processBlockReason, recoveryRequired,
  beginAppointmentWrite, finishAppointmentWrite, recoverAppointmentWrite,
} = useAppointmentProcess()
const router = useRouter()
const query = ref(''),
  patient = ref<Patient | null>(null),
  mode = ref<Mode>('single'),
  confirmed = ref(false),
  reviewing = ref(false),
  loading = ref(false),
  errorMessage = ref('')
const results = ref<Patient[]>([])
const allPatients = ref<Patient[]>([])
const patientProfile = ref<PatientProfile | null>(null)
const patientCoursePurchases = ref<CoursePurchase[]>([])
const patientSingleVisitEntitlements = ref<SingleVisitEntitlement[]>([])
const patientCourseLoading = ref(false)
const patientCourseError = ref(false)
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const paymentStatuses = ref<PaymentStatusOption[]>([])
const staff = ref<Staff[]>([])
const branches = ref<Branch[]>([])
const selectedServiceId = ref(0),
  selectedCourseId = ref(0),
  selectedTreatingStaffId = ref<number | undefined>(),
  selectedSalespersonStaffId = ref<number | undefined>()
const paymentCode = ref(''),
  note = ref('')
const paymentStatus = ref<PaymentStatusOption['code'] | ''>('')
const discountMode = ref<'NONE' | 'ITEM_FIXED' | 'ITEM_PERCENT' | 'BILL_FIXED' | 'BILL_PERCENT'>('NONE')
const discountValue = ref(0)
const searched = ref(false)
const receiptNo = ref('')
const receiptTransactionId = ref<number | null>(null)
const saleRequestKey = ref('')
const saleRequestStorageKey = () =>
  `sale-request:${patient.value?.id ?? 'none'}:${appointmentId.value ?? 'walk-in'}`
function ensureSaleRequestKey() {
  if (saleRequestKey.value) return saleRequestKey.value
  const stored = sessionStorage.getItem(saleRequestStorageKey())
  saleRequestKey.value = stored || crypto.randomUUID()
  sessionStorage.setItem(saleRequestStorageKey(), saleRequestKey.value)
  return saleRequestKey.value
}
const appointmentStatusMessage = ref('')
const patientLoading = ref(Boolean(route.query.patientId))
const patientLoadError = ref('')
const masterLoadError = ref('')
const paymentDialogOpen = ref(false)
const receiptPrintModal = ref<'confirm' | 'success' | 'error' | null>(null)
const screen = ref<'search' | 'menu' | 'sale' | 'receipt'>(
  route.query.patientId ? 'menu' : 'search',
)
const assessmentService = computed(
  () =>
    services.value.find(
      (service) =>
        service.code.includes('ASSESS') || service.name.toLowerCase().includes('assessment'),
    ) ?? services.value[0],
)
const patientCourseSummary = computed(() => {
  if (patientCourseLoading.value) return 'กำลังตรวจสอบสิทธิ์คอร์ส'
  if (patientCourseError.value) return 'ตรวจสอบสิทธิ์คอร์สไม่ได้'

  const usableCourses = patientCoursePurchases.value.filter(
    (course) => course.usable && course.remaining > 0,
  )
  if (!usableCourses.length) return 'ยังไม่มีสิทธิ์คอร์สที่ใช้งานได้'

  const remainingSessions = usableCourses.reduce((total, course) => total + course.remaining, 0)
  return `(คงเหลือ ${usableCourses.length} คอร์ส · ${remainingSessions} ครั้ง)`
})
const prepaidServiceBalance = computed(() =>
  patientSingleVisitEntitlements.value
    .filter((item) => !appointmentId.value || item.branchCode === sourceAppointment.value?.branchCode)
    .reduce((sum, item) => sum + item.remaining, 0),
)
const receiptBranch = computed(() => {
  const code = patient.value?.branchCode?.toLocaleLowerCase('th')
  const name = patient.value?.branchName?.replace(/^สาขา/, '').toLocaleLowerCase('th')
  return branches.value.find(
    (branch) =>
      (code && branch.code.toLocaleLowerCase('th') === code) ||
      (name && branch.name.replace(/^สาขา/, '').toLocaleLowerCase('th') === name),
  )
})
const receiptClinicAddress = computed(() => receiptBranch.value?.address?.trim() || '—')
const receiptClinicPhone = computed(() => receiptBranch.value?.phone?.trim() || '—')
const receiptClinicEmail = computed(() => receiptBranch.value?.email?.trim() || '—')
const receiptClinicLicense = computed(() => receiptBranch.value?.licenseNumber?.trim() || '—')
const receiptBranchLabel = computed(() => {
  const name = receiptBranch.value?.name || patient.value?.branchName
  return name ? (name.startsWith('สาขา') ? name : `สาขา ${name}`) : 'ไม่ระบุสาขา'
})
const managerBranchCode = computed(() => (isManager ? (selectedManagerBranch()?.code ?? '') : ''))
const appointmentBranchCode = computed(() =>
  isManager
    ? managerBranchCode.value
    : (JSON.parse(localStorage.getItem('authUser') ?? 'null')?.branchCode ?? ''),
)
async function chooseAppointment(item: Appointment) {
  // รับเฉพาะนัดของสาขาปัจจุบัน และส่ง NEW ไปลงทะเบียนก่อนเริ่มบริการ
  if (item.branchCode !== appointmentBranchCode.value || !['UNCONFIRMED', 'CONFIRMED'].includes(item.status)) return
  if (item.status === 'UNCONFIRMED' && item.patientType === 'NEW') {
    if (!isManager) {
      const user = JSON.parse(localStorage.getItem('authUser') ?? 'null')
      const staff = await staffApi.list()
      if (!staff.some(person => person.id === user?.staffId && person.id === item.therapist.id)) {
        errorMessage.value = 'ลงทะเบียนจากนัดหมายของตัวเองเท่านั้น'
        return
      }
    }
    await router.push({ name: 'patient-registration', query: { appointmentId: String(item.id) } })
    return
  }
  const selectedPatientId = item.patient?.id
  if (item.status === 'UNCONFIRMED' && !selectedPatientId) return
  await router.replace({
    path: '/staff/services',
    query: { ...(selectedPatientId ? { patientId: String(selectedPatientId) } : {}), appointmentId: String(item.id), from: 'services' },
  })
  await Promise.all([loadSelectedPatient(), refreshAppointmentProcess()])
}
const saleBranchCode = computed(
  () => sourceAppointment.value?.branchCode || managerBranchCode.value || patient.value?.branchCode || branches.value[0]?.code || '',
)
async function loadMaster() {
  const [s, c, p, methods, statuses, staffList] = await Promise.all([
    catalogApi.services(),
    catalogApi.courses(),
    patientApi.searchAll('', managerBranchCode.value),
    paymentMethodApi.list(),
    paymentStatusApi.list(),
    staffApi.list(),
  ])
  services.value = s
  courses.value = c
  allPatients.value = p
  results.value = p
  paymentMethods.value = methods
  paymentStatuses.value = statuses
  const enabledStatuses = statuses.filter((status) => status.enabled)
  if (!enabledStatuses.some((status) => status.code === paymentStatus.value))
    paymentStatus.value = ''
  staff.value = staffList.filter((person) => (person.position ?? person.role ?? '').toLowerCase() === 'therapist' && person.active)
  selectedServiceId.value = s[0]?.id ?? 0
  selectedCourseId.value = c[0]?.id ?? 0
  paymentCode.value =
    methods.find((method) => method.active !== false && method.name !== 'Cash')?.code ?? ''
  selectedTreatingStaffId.value = staff.value[0]?.id
  selectedSalespersonStaffId.value = staff.value[0]?.id
  try {
    branches.value = await branchApi.list()
    if (isManager) setManagerBranches(branches.value.filter((branch) => branch.active))
  } catch {
    branches.value = []
  }
}
async function loadSelectedPatient() {
  const patientId = Number(route.query.patientId)
  if (Number.isInteger(patientId) && patientId > 0) {
    try {
      const profile = await patientApi.get(patientId)
      patient.value = profile
      patientProfile.value = profile
      screen.value = 'menu'
      void loadPatientCourseSummary(profile.id)
    } catch {
      patientLoadError.value = 'ไม่สามารถโหลดข้อมูลผู้ป่วยได้ กรุณากลับไปหน้านัดหมายแล้วลองอีกครั้ง'
    } finally {
      patientLoading.value = false
    }
  } else if (route.query.patientId) {
    patientLoadError.value = 'ไม่พบข้อมูลผู้ป่วยที่ระบุ กรุณากลับไปหน้านัดหมายแล้วลองอีกครั้ง'
    patientLoading.value = false
  }
}
let patientCourseRequest = 0
async function loadPatientCourseSummary(patientId: number) {
  const requestId = ++patientCourseRequest
  patientCourseLoading.value = true
  patientCourseError.value = false
  try {
    const [purchases, entitlements] = await Promise.all([
      courseApi.patientCourses(patientId),
      singleVisitEntitlementApi.list(patientId),
    ])
    if (requestId !== patientCourseRequest || patient.value?.id !== patientId) return
    patientCoursePurchases.value = purchases
    patientSingleVisitEntitlements.value = entitlements
  } catch {
    if (requestId !== patientCourseRequest || patient.value?.id !== patientId) return
    patientCoursePurchases.value = []
    patientSingleVisitEntitlements.value = []
    patientCourseError.value = true
  } finally {
    if (requestId === patientCourseRequest && patient.value?.id === patientId)
      patientCourseLoading.value = false
  }
}
function openPrepaidServiceUse() {
  if (!patient.value || !appointmentId.value) return
  void router.push({ name: 'staff-single-visit-use', query: {
    patientId: String(patient.value.id),
    appointmentId: String(appointmentId.value),
  } })
}
onMounted(async () => {
  await Promise.all([
    loadSelectedPatient(),
    refreshAppointmentProcess(),
    loadMaster().catch(() => {
      masterLoadError.value = 'โหลดข้อมูลบริการบางส่วนไม่สำเร็จ กรุณาลองใหม่ก่อนทำรายการ'
    }),
  ])
  window.addEventListener('manager-branch-changed', applyManagerBranch)
})
onBeforeUnmount(() => window.removeEventListener('manager-branch-changed', applyManagerBranch))
function applyManagerBranch() {
  if (!isManager) return
  clearPatient()
  void loadMaster().catch(() => {
    masterLoadError.value = 'โหลดข้อมูลบริการบางส่วนไม่สำเร็จ กรุณาลองใหม่ก่อนทำรายการ'
  })
}
let searchTimer: number | undefined
let patientSearchRequest = 0
function normalizePatientSearch(value: string) {
  return value
    .trim()
    .replace(/^hn[\s:#-]*/i, '')
    .replace(/[\s-]/g, '')
    .toLowerCase()
}
async function searchPatientsByKeyword(keyword: string) {
  const requestId = ++patientSearchRequest
  const normalized = normalizePatientSearch(keyword)
  const digits = normalized.replace(/\D/g, '')
  const localMatches = allPatients.value.filter((item) => {
    const hn = normalizePatientSearch(item.hn)
    return (
      hn.includes(normalized) ||
      item.fullName.toLocaleLowerCase().includes(keyword.toLocaleLowerCase()) ||
      (digits.length > 0 && (item.phone ?? '').replace(/\D/g, '').includes(digits))
    )
  })
  try {
    const page = await patientApi.search(keyword, 0, 100, managerBranchCode.value)
    if (requestId !== patientSearchRequest || query.value.trim() !== keyword) return
    results.value = [
      ...new Map([...page.items, ...localMatches].map((item) => [item.id, item])).values(),
    ]
  } catch (error) {
    if (requestId !== patientSearchRequest || query.value.trim() !== keyword) return
    results.value = localMatches
    if (!localMatches.length)
      errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
  }
  if (requestId === patientSearchRequest && query.value.trim() === keyword) searched.value = true
}
watch(query, () => {
  window.clearTimeout(searchTimer)
  const keyword = query.value.trim()
  if (!keyword) {
    searched.value = false
    return
  }
  searchTimer = window.setTimeout(async () => {
    await searchPatientsByKeyword(keyword)
  }, 250)
})
const item = computed(() =>
  mode.value === 'assessment'
    ? (assessmentService.value?.name ?? 'Assessment')
    : mode.value === 'single'
      ? (services.value.find((s) => s.id === selectedServiceId.value)?.name ?? 'บริการรายครั้ง')
      : (courses.value.find((c) => c.id === selectedCourseId.value)?.name ?? 'คอร์ส'),
)
const rawPrice = computed(() =>
  mode.value === 'course'
    ? (courses.value.find((course) => course.id === selectedCourseId.value)?.price ?? 0)
    : mode.value === 'assessment'
      ? (assessmentService.value?.price ?? 0)
      : (services.value.find((service) => service.id === selectedServiceId.value)?.price ?? 0),
)
const discountAmount = computed(() => {
  const value = Number(discountValue.value)
  if (!Number.isFinite(value) || value <= 0) return 0
  const priceCents = Math.round(rawPrice.value * 100)
  return discountMode.value.endsWith('PERCENT')
    ? Math.round(priceCents * Math.round(value * 100) / 10000) / 100
    : Math.round(value * 100) / 100
})
const itemDiscount = computed(() => discountMode.value.startsWith('ITEM_') ? discountAmount.value : 0)
const afterItemDiscount = computed(() => Math.max(rawPrice.value - itemDiscount.value, 0))
const billDiscount = computed(() => discountMode.value.startsWith('BILL_') ? discountAmount.value : 0)
const netAmount = computed(() => Math.max(afterItemDiscount.value - billDiscount.value, 0))
const {
  preview: courseCommissionPreview,
  loading: commissionPreviewLoading,
  error: commissionPreviewError,
} = useCourseCommissionPreview(() => {
  if (mode.value !== 'course' || !selectedCourseId.value || !saleBranchCode.value) return null
  return {
    courseId: selectedCourseId.value,
    branchCode: saleBranchCode.value,
    sellerStaffId: selectedSalespersonStaffId.value,
    price: netAmount.value,
  }
})
const receivedAmount = computed(() => netAmount.value)
const remainingAmount = computed(() => 0)
const receiptPayable = computed(() => netAmount.value)
const money = (value: number) =>
  new Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' }).format(value)
const percentage = (rate: number) =>
  new Intl.NumberFormat('th-TH', { style: 'percent', maximumFractionDigits: 2 }).format(rate)
const printReceipt = () => {
  receiptPrintModal.value = 'confirm'
}
function confirmReceiptPrint() {
  receiptPrintModal.value = null
  const originalTitle = document.title
  const restoreTitle = () => {
    document.title = originalTitle
  }
  document.title = ''
  try {
    window.addEventListener('afterprint', restoreTitle, { once: true })
    window.setTimeout(() => {
      try {
        window.print()
        restoreTitle()
      } catch {
        restoreTitle()
        receiptPrintModal.value = 'error'
      }
    }, 0)
  } catch {
    restoreTitle()
    receiptPrintModal.value = 'error'
  }
}
function backToSaleSuccess() {
  receiptPrintModal.value = null
  screen.value = 'sale'
  confirmed.value = true
}
const printedAt = computed(() =>
  new Intl.DateTimeFormat('th-TH', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date()),
)
const selectedStaffName = computed(
  () =>
    staff.value.find((person) => person.id === selectedSalespersonStaffId.value)?.fullName || '-',
)
const staffFilterOptions = computed(() =>
  staff.value.map((person) => ({
    value: String(person.id),
    label: `กภ. ${person.fullName}`,
  })),
)
function paymentMethodCategory(code: string, name = '') {
  const value = `${code} ${name}`.toLocaleLowerCase('th')
  if (value.includes('bank') || value.includes('transfer') || value.includes('ธนาคาร'))
    return 'bank'
  if (value.includes('prompt') || value.includes('qr') || value.includes('พร้อมเพย์')) return 'qr'
  if (value.includes('card') || value.includes('บัตร')) return 'card'
  return 'other'
}
const salePaymentMethods = computed(() =>
  paymentMethods.value.filter(allowedPaymentMethod),
)
function paymentMethodIcon(code: string, name = '') {
  const category = paymentMethodCategory(code, name)
  if (category === 'bank') return paymentBankIcon
  if (category === 'qr') return paymentQrIcon
  if (category === 'card') return paymentCardIcon
  return ''
}
function paymentMethodLabel(code: string, name = '') {
  const category = paymentMethodCategory(code, name)
  if (category === 'bank') return 'โอนเงินผ่านธนาคาร'
  if (category === 'qr') return 'พร้อมเพย์'
  if (category === 'card') return 'บัตรเครดิต/เดบิต'
  return name
}
const selectedPaymentLabel = computed(() => {
  const method = salePaymentMethods.value.find(
    (item) => item.code === paymentCode.value && item.active !== false,
  )
  return method ? paymentMethodLabel(method.code, method.name) : '-'
})
const paymentStatusLabel = computed(() => {
  return paymentStatuses.value.find((status) => status.code === paymentStatus.value)?.label ?? '-'
})
const enabledPaymentStatuses = computed(() =>
  paymentStatuses.value.filter((status) => status.enabled),
)
function selectPaymentStatus(status: PaymentStatusOption['code']) {
  paymentStatus.value = status
}
const receiptIssuedAt = computed(() =>
  new Intl.DateTimeFormat('th-TH', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date()),
)
const initials = computed(
  () =>
    patient.value?.fullName
      .replace(/^นาย|^นางสาว|^นาง/, '')
      .trim()
      .slice(0, 2) ?? '',
)
async function choose(p: Patient) {
  patient.value = p
  patientProfile.value = null
  query.value = `${p.fullName} ${p.hn}`
  screen.value = 'menu'
  void loadPatientCourseSummary(p.id)
  try {
    patientProfile.value = await patientApi.get(p.id)
  } catch {
    patientProfile.value = null
  }
}
function searchPatients() {
  const keyword = query.value.trim()
  if (!keyword) return
  window.clearTimeout(searchTimer)
  searched.value = true
  void searchPatientsByKeyword(keyword)
}
function openPatientCourses() {
  if (patient.value)
    router.push({
      path: '/staff/courses',
      query: { patientId: patient.value.id, ...(appointmentId.value ? { appointmentId: String(appointmentId.value) } : {}), appointmentId: appointmentId.value ?? undefined },
    })
}
function clearPatient() {
  patientCourseRequest += 1
  patient.value = null
  patientProfile.value = null
  patientCoursePurchases.value = []
  patientSingleVisitEntitlements.value = []
  patientCourseLoading.value = false
  patientCourseError.value = false
  query.value = ''
  searched.value = false
  reviewing.value = false
  confirmed.value = false
  screen.value = 'search'
}
function backFromPaymentMenu() {
  if (appointmentId.value) {
    returnToAppointments()
    return
  }
  clearPatient()
}
async function backToPatientSelection() {
  clearPatient()
  appointmentProgress.value = null
  await router.replace({ name: 'staff-services' })
  await refreshAppointmentProcess()
}
function backToServiceMenu() {
  reviewing.value = false
  confirmed.value = false
  receiptPrintModal.value = null
  screen.value = 'menu'
}
function startSale() {
  if (patient.value) {
    void router.push({ name: 'staff-sale-checkout', query: {
      patientId: String(patient.value.id),
      appointmentId: appointmentId.value ? String(appointmentId.value) : undefined,
    } })
    return
  }
  ensureSaleRequestKey()
  screen.value = 'sale'
  mode.value = 'single'
  selectedServiceId.value =
    services.value.find((service) => service.code === 'MANUAL')?.id ?? services.value[0]?.id ?? 0
  reviewing.value = false
  confirmed.value = false
  paymentDialogOpen.value = false
  appointmentStatusMessage.value = ''
}
function returnToAppointments() {
  void router.push({ name: 'staff-appointments' })
}
async function submit() {
  if (!patient.value || loading.value) return
  const pendingKey = `sale-pending:${saleBranchCode.value}:${patient.value.id}:${appointmentId.value ?? 'no-appointment'}`
  if (sessionStorage.getItem(pendingKey)) { errorMessage.value = 'ยังยืนยันผลคำขอก่อนหน้าไม่ได้ กรุณาตรวจประวัติขายก่อนทำรายการใหม่'; return }
  loading.value = true
  errorMessage.value = ''
  confirmed.value = false
  try {
    if (route.query.appointmentId) {
      // ตรวจ progress ล่าสุดและ patientId ก่อนสร้างรายการขายจากนัด
      await refreshAppointmentProcess()
      if (!canStartAppointment.value) throw new Error(processBlockReason.value)
      if (patient.value.id !== Number(route.query.patientId)) throw new Error('ผู้ป่วยในหน้าจอไม่ตรงกับนัดหมาย กรุณาเปิดหน้าใหม่')
    }
    if (
      !enabledPaymentStatuses.value.some(
        (status) => status.code === paymentStatus.value && status.enabled,
      )
    )
      throw new Error('สถานะการชำระเงินนี้ถูกปิดใช้งาน กรุณาเลือกสถานะอื่น')
    const availablePaymentMethod = salePaymentMethods.value.find(
      (method) => method.code === paymentCode.value && method.active !== false,
    ) ?? salePaymentMethods.value.find((method) => method.active !== false)
    if (!availablePaymentMethod)
      throw new Error('ไม่มีช่องทางชำระเงินที่เปิดใช้งาน กรุณาติดต่อ Manager')
    if (availablePaymentMethod) paymentCode.value = availablePaymentMethod.code
    if (mode.value === 'assessment') throw new Error('ไม่เปิดขายบริการประเมินใหม่')
    const referenceId = mode.value === 'course' ? selectedCourseId.value : selectedServiceId.value
    if (!referenceId) throw new Error('ไม่พบรายการบริการที่เลือก')
    if (!Number.isFinite(discountValue.value) || discountValue.value < 0)
      throw new Error('ส่วนลดต้องเป็นจำนวนไม่ติดลบ')
    if (Math.abs(discountValue.value * 100 - Math.round(discountValue.value * 100)) > 0.000001)
      throw new Error('ส่วนลดระบุได้ไม่เกิน 2 ตำแหน่งทศนิยม')
    if (discountMode.value.endsWith('PERCENT') && discountValue.value > 100)
      throw new Error('ส่วนลดเปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100')
    if (discountMode.value.endsWith('FIXED') && discountAmount.value > rawPrice.value)
      throw new Error('ส่วนลดต้องไม่เกินราคา')
    if (netAmount.value <= 0) throw new Error('ยอดสุทธิต้องมากกว่า 0 จึงรับชำระและออกใบเสร็จได้')
    if (!selectedSalespersonStaffId.value) throw new Error('กรุณาเลือกผู้ขายก่อนบันทึกการขาย')
    // สร้าง payload สำหรับบริการ/ซื้อคอร์ส โดยให้ helper ตรวจยอดและเงื่อนไขร่วม
    const request = buildSaleRequest({ patientId: patient.value.id, branchCode: saleBranchCode.value,
      appointmentId: appointmentId.value ?? undefined, salespersonStaffId: selectedSalespersonStaffId.value,
      treatingStaffId: selectedTreatingStaffId.value, note: note.value, paymentLabelCode: paymentStatus.value,
      paymentMethodCode: paymentCode.value, labels: paymentStatuses.value, methods: paymentMethods.value,
      items: [{ type: mode.value === 'course' ? 'COURSE' : 'SERVICE', referenceId, quantity: 1, price: rawPrice.value,
        discountType: discountMode.value.startsWith('ITEM_') ? (discountMode.value.endsWith('PERCENT') ? 'PERCENT' : 'FIXED') : undefined,
        discountValue: discountMode.value.startsWith('ITEM_') ? discountValue.value : undefined }],
      billDiscountType: discountMode.value.startsWith('BILL_') ? (discountMode.value.endsWith('PERCENT') ? 'PERCENT' : 'FIXED') : undefined,
      billDiscountValue: discountMode.value.startsWith('BILL_') ? discountValue.value : undefined,
    })
    sessionStorage.setItem(pendingKey, 'pending')
    // บันทึก recovery marker ก่อน POST เพื่อป้องกันการส่งซ้ำเมื่อเครือข่ายขาด
    beginAppointmentWrite()
    const sale = await salesApi.create(request)
    sessionStorage.removeItem(pendingKey)
    finishAppointmentWrite(mode.value !== 'course')
    if (appointmentId.value) await refreshAppointmentProcess()
    receiptNo.value = sale.receiptNo
    receiptTransactionId.value = sale.id
    sessionStorage.removeItem(saleRequestStorageKey())
    saleRequestKey.value = ''
    confirmed.value = true
    reviewing.value = false
    paymentDialogOpen.value = false
  } catch (error) {
    if (error instanceof ApiError && [400, 401, 403, 422].includes(error.status)) sessionStorage.removeItem(pendingKey)
    await recoverAppointmentWrite(error)
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกรายการไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
// นัดที่ยืนยันและมีรายการแล้ว (เช่น กดกลับจากใบเสร็จ) ไม่ต้องแสดงแผงรายการ ให้กลับหน้านัดหมายเลย
const appointmentAlreadyProcessed = computed(
  () =>
    appointmentProgress.value?.state === 'FOUND' &&
    appointmentProgress.value.appointmentStatus === 'CONFIRMED',
)
watch(appointmentAlreadyProcessed, (done) => {
  if (done) void router.replace({ name: 'staff-appointments' })
})
</script>
<template>
  <div class="service-page">
    <div class="service-header-shell"><StaffHeader /></div>
    <main>
      <p v-if="processLoading" role="status">กำลังตรวจสอบรายการของนัดหมาย…</p>
      <div v-else-if="processError" role="alert">
        <p>{{ processError }}</p>
        <button type="button" @click="refreshAppointmentProcess">ลองใหม่</button
        ><button type="button" @click="returnToAppointments">กลับหน้านัดหมาย</button>
      </div>
      <AppointmentProcessPanel
        v-else-if="
          appointmentProgress && !canStartAppointment && !appointmentAlreadyProcessed
        "
        :progress="appointmentProgress"
        :block-reason="processBlockReason" :recovery-required="recoveryRequired"
        :from-services="route.query.from === 'services'"
        @back-to-patients="backToPatientSelection"
        @refresh="refreshAppointmentProcess"
      />
      <p v-else-if="patientLoading" role="status">กำลังโหลดข้อมูลผู้ป่วย...</p>
      <div v-else-if="patientLoadError" role="alert">
        <p class="error">{{ patientLoadError }}</p>
        <button type="button" @click="returnToAppointments">กลับหน้านัดหมาย</button>
      </div>
      <template v-else-if="screen === 'search'">
        <div class="payment-page-heading">
          <div>
            <h1>ชำระเงิน</h1>
            <p class="service-page-intro">ค้นหาและเลือกผู้ป่วยเพื่อขาย ใช้สิทธิ์ และจัดการคอร์ส</p>
          </div>
        </div>
        <section class="service-search">
          <div class="search-wrap">
            <label class="search">
              <span class="search-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="11" cy="11" r="6" />
                  <path d="m16 16 4 4" />
                </svg>
              </span>
              <input
                v-model="query"
                aria-label="ค้นหาผู้ป่วย"
                placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"
                @input="patient = null"
              />
            </label>
            <button class="service-search-button" type="button" @click="searchPatients">
              ค้นหา
            </button>
          </div>
          <section v-if="searched" class="service-results-card">
            <h2>ผลการค้นหา {{ results.length }} คน</h2>
            <p>ตรวจสอบชื่อ HN และเบอร์โทรศัพท์ก่อนเลือกผู้ป่วย</p>
            <button
              v-for="p in results"
              :key="p.hn"
              class="service-patient-result"
              type="button"
              @click="choose(p)"
            >
              <span
                ><b>{{ p.fullName }}</b
                ><small>HN {{ p.hn }} · โทร {{ p.phone || '-' }}</small></span
              >
              <strong>เลือกผู้ป่วย</strong>
            </button>
            <p v-if="!results.length" class="service-empty-result">ไม่พบผู้ป่วยที่ตรงกับคำค้น</p>
          </section>
          <TodayAppointments
            :branch-code="appointmentBranchCode"
            :keyword="query"
            @select="chooseAppointment"
          />
        </section>
      </template>
      <section v-else-if="screen === 'menu' && patient" class="service-menu">
        <button type="button" class="service-menu-back" @click="backFromPaymentMenu">
          ← กลับ
        </button>
        <h1>เลือกวิธีดำเนินรายการ</h1>
        <p v-if="appointmentId && !canStartAppointment" role="status">{{ processBlockReason }}</p>
        <section class="service-menu-patient" aria-label="ผู้ป่วยที่เลือก">
          <span class="service-menu-avatar" aria-hidden="true">{{ initials }}</span>
          <div class="service-menu-patient-info">
            <strong>{{ patientProfile?.title }}{{ patient.fullName }}</strong>
            <p>
              <span>HN {{ patient.hn }}</span
              ><span v-if="patient.phone" class="service-menu-phone"
                ><PhoneIcon aria-hidden="true" />{{ patient.phone }}</span
              ><span v-else>-</span>
            </p>
          </div>
          <span class="service-menu-branch">{{
            patientProfile?.branch?.name || patient.branchName || '—'
          }}</span>
        </section>
        <div class="service-menu-actions">
          <button
            v-if="!appointmentId || canStartAppointment"
            type="button"
            class="service-menu-action service-menu-action--sale"
            @click="startSale"
          >
            <span class="service-menu-action-title">
              <span class="service-menu-action-heading">ชำระเงิน</span>
            </span>
            <span class="service-menu-action-next">ถัดไป →</span>
          </button>
          <button
            v-if="appointmentId && canStartAppointment && prepaidServiceBalance > 0"
            type="button"
            class="service-menu-action service-menu-action--entitlement"
            @click="openPrepaidServiceUse"
          >
            <span class="service-menu-action-title">
              <span class="service-menu-action-heading">ใช้สิทธิ์บริการรายครั้ง</span>
              <span class="service-menu-course-status">คงเหลือ {{ prepaidServiceBalance }} ครั้ง</span>
            </span>
            <span class="service-menu-action-next">ถัดไป →</span>
          </button>
          <button
            v-if="appointmentId && canStartAppointment"
            type="button"
            class="service-menu-action service-menu-action--entitlement"
            @click="openPatientCourses"
          >
            <span class="service-menu-action-title">
              <span class="service-menu-action-heading">ใช้สิทธิ์คอร์สที่มีอยู่</span>
              <span
                class="service-menu-course-status"
                :class="{
                  'is-loading': patientCourseLoading,
                  'is-unavailable': patientCourseError,
                }"
                aria-live="polite"
              >
                {{ patientCourseSummary }}
              </span>
            </span>
            <span class="service-menu-action-next">ถัดไป →</span>
          </button>
        </div>
      </section>
      <section v-else-if="screen === 'receipt' && patient" class="service-receipt-page">
        <article class="printable-receipt">
          <header>
            <div class="receipt-clinic">
              <h1>สบายกาย คลินิกกายภาพบำบัด {{ receiptBranchLabel }}</h1>
              <p>ที่อยู่: {{ receiptClinicAddress }}</p>
              <p>โทร: {{ receiptClinicPhone }}</p>
              <p>
                เลขที่ใบอนุญาต: {{ receiptClinicLicense }}　 อีเมล:
                {{ receiptClinicEmail }}
              </p>
            </div>
            <div class="receipt-document">
              <h2>ใบเสร็จรับเงิน</h2>
              <div>
                <p>เลขที่เอกสาร: {{ receiptNo }}</p>
                <p>วันที่ออกเอกสาร: {{ receiptIssuedAt }}</p>
              </div>
            </div>
          </header>
          <section class="receipt-customer">
            <p><b>ผู้รับบริการ/ผู้บริโภค:</b> {{ patient.fullName }}</p>
            <p><b>รหัสลูกค้า:</b> HN {{ patient.hn }}</p>
            <p><b>เลขบัตรประชาชน:</b> {{ patientProfile?.identityNumber || '—' }}</p>
            <p><b>โทร:</b> {{ patient.phone }}</p>
            <p><b>ที่อยู่:</b> {{ patientProfile?.address || '—' }}</p>
            <p><b>อีเมล:</b> {{ patientProfile?.email || '—' }}</p>
          </section>
          <table>
            <thead>
              <tr>
                <th>รายการ</th>
                <th>จำนวน / หน่วย</th>
                <th>ราคา</th>
                <th>ยอดรวม</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>1. {{ item }}</td>
                <td>ครั้ง</td>
                <td>{{ money(rawPrice) }}</td>
                <td>{{ money(rawPrice) }}</td>
              </tr>
            </tbody>
          </table>
          <section class="receipt-totals">
            <div>
              <span>ยอดทั้งหมด</span><b>{{ money(rawPrice) }}</b
              ><span>ส่วนลดท้ายบิล</span><b>{{ money(billDiscount) }}</b
              ><strong>ยอดสุทธิที่ชำระ</strong><strong>{{ money(receiptPayable) }}</strong>
            </div>
            <div>
              <span>สถานะการชำระเงิน</span><b>{{ paymentStatusLabel }}</b
              ><span>ช่องทางการชำระ</span
              ><b>{{ selectedPaymentLabel }}</b
              ><span>รับชำระครั้งนี้</span><b>{{ money(receivedAmount) }}</b
              ><strong>ยอดคงเหลือ</strong><strong>{{ money(remainingAmount) }}</strong>
            </div>
          </section>
          <section class="receipt-balance">
            <p>
              <b>หมายเหตุ: {{ note || '-' }}</b>
            </p>
          </section>
          <section class="receipt-signatures">
            <div>
              <span>........................................</span
              ><span>(........................................)</span><b>ผู้รับบริการ</b>
            </div>
            <div>
              <span>........................................</span
              ><span>(........................................)</span><b>ผู้ทำรายการ</b>
            </div>
            <div>
              <span>........................................</span
              ><span>(........................................)</span
              ><b>ผู้มีอำนาจออกหลักฐานการรับเงิน</b>
            </div>
          </section>
          <p class="receipt-printed-at">พิมพ์เมื่อ {{ printedAt }}</p>
        </article>
        <div class="receipt-print-actions">
          <button type="button" @click="screen = 'sale'">← กลับ</button
          ><button type="button" @click="printReceipt">พิมพ์เอกสาร</button>
        </div>
        <div
          v-if="receiptPrintModal"
          class="receipt-print-modal-backdrop"
          @click.self="receiptPrintModal = null"
        >
          <section
            class="receipt-print-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="receipt-print-title"
          >
            <template v-if="receiptPrintModal === 'confirm'">
              <h2 id="receipt-print-title">ยืนยันการพิมพ์เอกสาร</h2>
              <p>ตรวจสอบชื่อผู้รับบริการ เลขที่เอกสาร รายการ และยอดสุทธิให้ครบถ้วนก่อนส่งพิมพ์</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="confirmReceiptPrint">ยืนยันและพิมพ์</button>
              </footer>
            </template>
            <template v-else-if="receiptPrintModal === 'success'">
              <h2 id="receipt-print-title">ส่งเอกสารไปยังเครื่องพิมพ์แล้ว</h2>
              <p>สถานะเอกสารถูกบันทึกเป็น “พิมพ์แล้ว” สามารถกลับไปดูรายการต้นทางได้</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="backToSaleSuccess">กลับหน้ารายการ</button>
              </footer>
            </template>
            <template v-else>
              <h2 id="receipt-print-title">ไม่สามารถพิมพ์เอกสารได้</h2>
              <p>กรุณาตรวจสอบการเชื่อมต่อเครื่องพิมพ์หรือข้อมูลที่จำเป็น แล้วลองอีกครั้ง</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="receiptPrintModal = 'confirm'">ลองอีกครั้ง</button>
              </footer>
            </template>
          </section>
        </div>
      </section>
      <section v-else-if="patient" class="sale-page">
        <button type="button" class="sale-back-button" @click="backToServiceMenu">
          <span aria-hidden="true">←</span> กลับไปหน้าบริการ
        </button>
        <ol class="sales-progress" aria-label="ขั้นตอนการขาย">
          <li
            :class="{
              active: !reviewing && !confirmed,
              done: reviewing || confirmed,
            }"
          >
            <i>1</i><span>สร้างรายการ</span>
          </li>
          <li :class="{ active: reviewing && !confirmed, done: confirmed }">
            <i>2</i><span>ตรวจสอบและชำระ</span>
          </li>
          <li :class="{ active: confirmed }"><i>3</i><span>สำเร็จ</span></li>
        </ol>

        <template v-if="!confirmed">
          <h1>{{ reviewing ? 'ตรวจสอบและชำระเงิน' : 'ขายบริการและคอร์ส' }}</h1>
          <p class="sale-intro">
            {{
              reviewing
                ? 'ตรวจสอบรายการ ส่วนลด และฐานค่าคอมมิชชันก่อนยืนยัน'
                : 'ระบุรายการ ส่วนลด และช่องทางชำระเงิน'
            }}
          </p>
          <section class="sale-patient-summary">
            <i>{{ initials }}</i>
            <div>
              <strong>{{ patient.fullName }}</strong
              ><span class="patient-contact-inline"
                >HN {{ patient.hn }} <PhoneIcon v-if="patient.phone" />
                {{ patient.phone || '-' }}</span
              >
            </div>
            <b>{{ patientProfile?.branch?.name || patient.branchName || '—' }}</b>
          </section>
          <div class="sales-workspace" :class="{ review: reviewing }">
            <section class="sale-line-card">
              <header>
                <h2>รายละเอียดรายการ</h2>
                <small>1 รายการ</small>
              </header>
              <div class="sale-line-head">
                <span>บริการ</span><span>ราคา/หน่วย</span><span>จำนวน</span><span>ส่วนลดรายการ</span
                ><span>ยอดหลังลด</span>
              </div>
              <div class="sale-line-values">
                <div>
                  <div class="sale-service-readonly" aria-label="บริการที่เลือก">
                    {{ item }}
                  </div>
                  <small>{{ mode === 'course' ? 'คอร์ส' : 'บริการรายครั้ง' }}</small>
                </div>
                <span>{{ money(rawPrice) }}</span
                ><span>1</span><span>− {{ money(itemDiscount) }}</span
                ><b>{{ money(afterItemDiscount) }}</b>
              </div>
              <div class="sale-discount-controls" v-if="!reviewing">
                <label>ส่วนลด
                  <select v-model="discountMode" @change="discountValue = 0">
                    <option value="NONE">ไม่มีส่วนลด</option>
                    <option value="ITEM_FIXED">ส่วนลดรายการ (บาท)</option>
                    <option value="ITEM_PERCENT">ส่วนลดรายการ (%)</option>
                    <option value="BILL_FIXED">ส่วนลดท้ายบิล (บาท)</option>
                    <option value="BILL_PERCENT">ส่วนลดท้ายบิล (%)</option>
                  </select>
                </label>
                <label v-if="discountMode !== 'NONE'">จำนวนส่วนลด
                  <input v-model.number="discountValue" type="number" min="0" :max="discountMode.endsWith('PERCENT') ? 100 : rawPrice" step="0.01" />
                </label>
              </div>
              <div class="sale-people">
                <label v-if="mode !== 'course'"
                  >นักกายภาพผู้ให้บริการ<FilterDropdown
                    :model-value="String(selectedTreatingStaffId ?? '')"
                    :options="staffFilterOptions"
                    default-label="เลือกนักกายภาพ"
                    label="นักกายภาพผู้ให้บริการ"
                    :clear-on-reselect="false"
                    @update:model-value="selectedTreatingStaffId = Number($event)" /></label
                ><label
                  >{{ mode === 'course' ? 'นักกายภาพเจ้าของยอด' : 'ผู้ขาย/เจ้าของยอด'
                  }}<FilterDropdown
                    :model-value="String(selectedSalespersonStaffId ?? '')"
                    :options="staffFilterOptions"
                    :default-label="
                      mode === 'course' ? 'เลือกนักกายภาพเจ้าของยอด' : 'เลือกผู้ขาย/เจ้าของยอด'
                    "
                    :label="mode === 'course' ? 'นักกายภาพเจ้าของยอด' : 'ผู้ขายหรือเจ้าของยอด'"
                    :clear-on-reselect="false"
                    @update:model-value="selectedSalespersonStaffId = Number($event)"
                /></label>
              </div>
              <fieldset class="payment-status-group">
                <legend>สถานะการชำระเงิน</legend>
                <div>
                  <button
                    v-for="status in enabledPaymentStatuses"
                    :key="status.code"
                    type="button"
                    :class="{ active: paymentStatus === status.code }"
                    @click="selectPaymentStatus(status.code)"
                  >
                    {{ status.label }}
                  </button>
                </div>
                <small>ป้ายกำกับนี้ใช้อธิบายการขาย รับชำระเต็มยอดสุทธิในครั้งเดียว</small>
                <small
                  v-if="!enabledPaymentStatuses.some((status) => status.enabled)"
                  class="service-error"
                  >ไม่มีสถานะการชำระเงินที่เปิดใช้งาน กรุณาติดต่อ Manager</small
                >
              </fieldset>
              <div class="payment-group">
                <span>ช่องทางชำระเงิน</span>
                <div class="payment-options">
                  <button
                    v-for="method in salePaymentMethods"
                    :key="method.code"
                    type="button"
                    :class="{
                      active: paymentCode === method.code,
                    }"
                    @click="paymentCode = method.code"
                  >
                    <img
                      v-if="paymentMethodIcon(method.code, method.name)"
                      :src="paymentMethodIcon(method.code, method.name)"
                      alt=""
                    />
                    <span>{{ paymentMethodLabel(method.code, method.name) }}</span>
                  </button>
                </div>
                <small v-if="!salePaymentMethods.length" class="service-error">
                  ไม่มีช่องทางชำระเงินที่เปิดใช้งาน กรุณาติดต่อ Manager
                </small>
              </div>
              <label class="sale-note"
                >หมายเหตุ (ไม่บังคับ)<textarea
                  v-model.trim="note"
                  placeholder="เพิ่มรายละเอียดเกี่ยวกับรายการขาย"
                />
              </label>
              <button v-if="reviewing" type="button" class="sale-edit" @click="reviewing = false">
                แก้ไข
              </button>
            </section>
            <aside class="sale-summary-card">
              <h2>สรุปรายการ</h2>
              <p>
                <span>ยอดก่อนส่วนลด</span><b>{{ money(rawPrice) }}</b>
              </p>
              <p>
                <span>ส่วนลดรายการรวม</span><b>− {{ money(itemDiscount) }}</b>
              </p>
              <p>
                <strong>ยอดหลังส่วนลดรายการ</strong><strong>{{ money(afterItemDiscount) }}</strong>
              </p>
              <p class="bill-discount">
                <span>ส่วนลดท้ายบิล</span><b>− {{ money(billDiscount) }}</b>
              </p>
              <p class="net-total">
                <strong>ยอดสุทธิที่ลูกค้าชำระ</strong><strong>{{ money(netAmount) }}</strong>
              </p>
              <div v-if="mode === 'course'" class="commission">
                <template v-if="commissionPreviewLoading">
                  <p><span>ค่าคอมมิชชันโดยประมาณ</span><b>กำลังคำนวณ…</b></p>
                </template>
                <template v-else-if="commissionPreviewError">
                  <p><span>ค่าคอมมิชชันโดยประมาณ</span><b>ยังคำนวณไม่ได้</b></p>
                  <small class="service-error">{{ commissionPreviewError }}</small>
                </template>
                <template v-else-if="courseCommissionPreview">
                  <p>
                    <span>ฐานค่าคอมมิชชัน</span><b>{{ money(courseCommissionPreview.netPrice) }}</b>
                  </p>
                  <p>
                    <span>{{ percentage(courseCommissionPreview.commissionRate) }}</span>
                    <b>{{ money(courseCommissionPreview.commissionPool) }}</b>
                  </p>
                  <p>
                    <span>ประมาณการต่อครั้ง</span>
                    <b>{{ money(courseCommissionPreview.commissionPerVisit) }}</b>
                  </p>
                  <small
                    >อัตราสุดท้ายจะล็อกตาม Tier
                    สูงสุดจากยอดคอร์สสะสมของนักกายภาพเมื่อปิดยอดเดือน</small
                  >
                </template>
              </div>
              <div v-else class="commission">
                <p><span>ฐานค่ามือบริการ</span><b>{{ money(netAmount) }}</b></p>
                <small>ค่ามือจริงคำนวณตามกฎที่มีผลในวันรักษา</small>
              </div>
              <button
                type="button"
                :disabled="loading"
                @click="reviewing ? (paymentDialogOpen = true) : (reviewing = true)"
              >
                {{ reviewing ? 'ยืนยันรับชำระเงิน' : 'ตรวจสอบและชำระเงิน' }}
              </button>
            </aside>
          </div>
        </template>

        <section v-else class="sale-success-card">
          <i><img :src="saleSuccessCheck" alt="" /><span>✓</span></i>
          <h1>บันทึกการขายสำเร็จ</h1>
          <p>เลขรายการ {{ receiptNo }}</p>
          <p v-if="appointmentStatusMessage" role="status">{{ appointmentStatusMessage }}</p>
          <div>
            <section>
              <b>{{ patient.fullName }}　HN {{ patient.hn }}</b
              ><span
                >{{ item }} {{ patientProfile?.branch?.name || patient.branchName || '—' }}</span
              ><small>{{ mode === 'course' ? 'ฐานค่าคอมมิชชัน' : 'ฐานค่ามือบริการ' }}　{{ money(netAmount) }}</small
              ><small
                >{{ mode === 'course' ? 'เจ้าของยอดคอร์ส' : 'ผู้ให้บริการ' }}　<b>{{ selectedStaffName }}</b></small
              >
            </section>
            <section>
              <span>รับชำระครั้งนี้</span><strong>{{ money(receivedAmount) }}</strong>
              <span>ยอดคงเหลือ</span><strong>{{ money(remainingAmount) }}</strong>
            </section>
          </div>
          <footer>
            <button type="button" @click="receiptTransactionId && router.push({ name: 'staff-receipt', params: { id: receiptTransactionId } })">ดูและพิมพ์ใบเสร็จ</button
            ><button type="button" @click="backToServiceMenu">กลับหน้าบริการ</button
            ><button v-if="route.query.appointmentId" type="button" @click="returnToAppointments">
              กลับหน้านัดหมาย
            </button>
          </footer>
        </section>

        <div
          v-if="paymentDialogOpen"
          class="payment-modal-backdrop"
          @click.self="paymentDialogOpen = false"
        >
          <section class="payment-modal">
            <h2>ยืนยันรับชำระเงิน</h2>
            <p>โปรดตรวจสอบอีกครั้งก่อนบันทึกการขาย</p>
            <div>
              <section>
                <span>ยอดที่รับชำระครั้งนี้</span><strong>{{ money(receivedAmount) }}</strong
                ><small v-if="remainingAmount > 0">ยอดคงเหลือ {{ money(remainingAmount) }}</small
                ><small
                  >ช่องทางการชำระเงิน　{{
                    selectedPaymentLabel
                  }}</small
                >
              </section>
              <section v-if="mode === 'course'">
                <template v-if="commissionPreviewLoading">
                  <span>ฐานค่าคอมมิชชัน</span><b>กำลังคำนวณ…</b>
                </template>
                <template v-else-if="commissionPreviewError">
                  <span>ค่าคอมมิชชันโดยประมาณ</span><b>ยังคำนวณไม่ได้</b>
                  <small class="service-error">{{ commissionPreviewError }}</small>
                </template>
                <template v-else-if="courseCommissionPreview">
                  <span>ฐานค่าคอมมิชชัน</span><b>{{ money(courseCommissionPreview.netPrice) }}</b>
                  <small>
                    ค่าคอมมิชชัน {{ percentage(courseCommissionPreview.commissionRate) }}
                    {{ money(courseCommissionPreview.commissionPool) }}
                  </small>
                  <small>
                    ประมาณการ {{ money(courseCommissionPreview.commissionPerVisit) }} ต่อครั้ง ({{
                      courseCommissionPreview.totalVisits
                    }}
                    ครั้ง)
                  </small>
                </template>
              </section>
              <section v-else>
                <span>ฐานค่ามือบริการ</span><b>{{ money(netAmount) }}</b
                ><small>ค่ามือจริงคำนวณตามกฎที่มีผลในวันรักษา</small>
              </section>
            </div>
            <footer>
              <button type="button" @click="paymentDialogOpen = false">ยกเลิก</button
              ><button type="button" :disabled="loading" @click="submit">
                {{ loading ? 'กำลังบันทึก...' : 'ยืนยันการชำระเงิน' }}
              </button>
            </footer>
          </section>
        </div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/manager-services.css"></style>
