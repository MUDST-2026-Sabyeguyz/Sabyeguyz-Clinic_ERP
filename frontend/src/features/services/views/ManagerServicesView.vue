<script setup lang="ts">
import { useAppointmentProcess } from '@/features/appointments/useAppointmentProcess'
import TodayAppointments from '../components/TodayAppointments.vue'
import AppointmentProcessPanel from '@/features/appointments/components/AppointmentProcessPanel.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import PhoneIcon from '@/components/PhoneIcon.vue'
import { branchApi, courseApi, singleVisitEntitlementApi, patientApi, staffApi, type CoursePurchase, type SingleVisitEntitlement, type Appointment, type Branch, type Patient, type PatientProfile } from '@/api/client'
import { selectedManagerBranch, setManagerBranches } from '@/features/staff/data/managerBranch'
const isManager = JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
const route = useRoute()
const {
  appointmentId, sourceAppointment,
  appointmentProgress,
  processLoading,
  processError,
  refreshAppointmentProcess,
  canStartAppointment, processBlockReason, recoveryRequired,
} = useAppointmentProcess()
const router = useRouter()
const query = ref('')
const patient = ref<Patient | null>(null)
const errorMessage = ref('')
const results = ref<Patient[]>([])
const allPatients = ref<Patient[]>([])
const patientProfile = ref<PatientProfile | null>(null)
const patientCoursePurchases = ref<CoursePurchase[]>([])
const patientSingleVisitEntitlements = ref<SingleVisitEntitlement[]>([])
const patientCourseLoading = ref(false)
const patientCourseError = ref(false)
const branches = ref<Branch[]>([])
const searched = ref(false)
const patientLoading = ref(Boolean(route.query.patientId))
const patientLoadError = ref('')
const masterLoadError = ref('')
const screen = ref<'search' | 'menu'>(
  route.query.patientId ? 'menu' : 'search',
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
async function loadMaster() {
  const patients = await patientApi.searchAll('', managerBranchCode.value)
  allPatients.value = patients
  results.value = patients
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
function startSale() {
  if (!patient.value) return
  void router.push({ name: 'staff-sale-checkout', query: {
    patientId: String(patient.value.id),
    appointmentId: appointmentId.value ? String(appointmentId.value) : undefined,
  } })
}
function returnToAppointments() {
  void router.push({ name: 'staff-appointments' })
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
      <p v-if="errorMessage && recoveryRequired && !loading" class="error" role="alert">{{ errorMessage }}</p>
      <p v-if="loading" role="status" aria-live="polite">กำลังบันทึก…</p>
      <p v-else-if="processLoading" role="status">กำลังตรวจสอบรายการของนัดหมาย…</p>
      <div v-else-if="processError" role="alert">
        <p>{{ processError }}</p>
        <button type="button" @click="refreshAppointmentProcess">ลองใหม่</button
        ><button type="button" @click="returnToAppointments">กลับหน้านัดหมาย</button>
      </div>
      <AppointmentProcessPanel
        v-else-if="
          appointmentProgress && !canStartAppointment && !appointmentAlreadyProcessed && !confirmed
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
            type="button"
            class="service-menu-action service-menu-action--entitlement"
            @click="openPatientCourses"
          >
            <span class="service-menu-action-title">
              <span class="service-menu-action-heading">{{ appointmentId ? 'ใช้สิทธิ์คอร์สที่มีอยู่' : 'ดูคอร์สของผู้ป่วย' }}</span>
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
    </main>
  </div>
</template>
<style scoped src="../styles/manager-services.css"></style>
