<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import courseUsageSuccessIcon from '@/assets/course-usage-success.svg'
import {
  branchApi,
  courseApi,
  patientApi,
  staffApi,
  type CoursePurchase,
  type Branch,
  type Patient,
  type Staff,
} from '@/api/client'

type CourseAction = 'manage' | 'transfer' | 'history' | 'use'
const route = useRoute()
const router = useRouter()
const action = ref<CourseAction>('manage')
const recipientSearch = ref('')
const recipientSearchOpen = ref(false)
const selectedRecipient = ref<Patient | null>(null)
const recipientConfirmed = ref(false)
const transferAmount = ref(2)
const transferReason = ref('')
const showReasonError = ref(false)
const coTherapistId = ref<number | ''>('')
const primaryShare = ref(50)
const secondaryShare = ref(50)
const substituteNote = ref('')
const transferReceiptId = ref<number | null>(null)
type UiCourse = CoursePurchase & { name: string; expiry: string; balance: number; total: number }
type CourseMovement = {
  id: number
  type: string
  quantity: number
  balanceAfter: number
  reason: string
  appointmentId?: number | null
  createdAt: string
}
const owner = ref<Patient | null>(null)
const owners = ref<Patient[]>([])
const patientCourses = ref<UiCourse[]>([])
const recipientResults = ref<Patient[]>([])
const therapists = ref<Staff[]>([])
const branches = ref<Branch[]>([])
const courseUsageHistory = ref<CourseMovement[]>([])
const errorMessage = ref('')
const selectedCourse = ref<UiCourse>({} as UiCourse)
const selectedCourseId = ref(0)
const usageNote = ref('')
const usageDate = ref(new Date().toISOString().slice(0, 10))
const usageReview = ref(false)
const usageSuccess = ref(false)
const usageConfirmDialog = ref(false)
const usageSuccessBalance = computed(() =>
  usageSuccess.value ? selectedCourse.value.balance : Math.max(0, selectedCourse.value.balance - 1),
)
const printDialog = ref<'confirm' | 'success' | 'error' | null>(null)
const coursePrintPreview = ref(false)
const transferStep = ref<'form' | 'review' | 'success'>('form')
const shareIsValid = computed(
  () =>
    primaryShare.value >= 0 &&
    secondaryShare.value >= 0 &&
    primaryShare.value + secondaryShare.value === 100,
)
const formattedUsageDate = computed(() =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'long', year: 'numeric' }).format(
    new Date(`${usageDate.value}T00:00:00`),
  ),
)
const usedSessions = computed(() =>
  courseUsageHistory.value
    .filter((movement) => movement.type === 'USAGE')
    .reduce((total, movement) => total + Math.abs(movement.quantity), 0),
)
const transferredOut = computed(() =>
  courseUsageHistory.value
    .filter((movement) => movement.type === 'TRANSFER_OUT')
    .reduce((total, movement) => total + Math.abs(movement.quantity), 0),
)
const historyRemaining = computed(() => selectedCourse.value.balance)
const documentBranch = computed(() => {
  const code = owner.value?.branchCode?.toLocaleLowerCase('th')
  const name = owner.value?.branchName?.replace(/^สาขา/, '').toLocaleLowerCase('th')
  return branches.value.find(
    (branch) =>
      (code && branch.code.toLocaleLowerCase('th') === code) ||
      (name && branch.name.replace(/^สาขา/, '').toLocaleLowerCase('th') === name),
  )
})
const documentClinicAddress = computed(() => documentBranch.value?.address?.trim() || '—')
const documentClinicPhone = computed(() => documentBranch.value?.phone?.trim() || '—')
const usageEntries = computed(() =>
  courseUsageHistory.value
    .filter((movement) => movement.type === 'USAGE')
    .slice()
    .reverse(),
)
const activeCourses = computed(() => patientCourses.value.filter((course) => course.usable))
const inactiveCourses = computed(() =>
  patientCourses.value.filter(
    (course) => course.status === 'ACTIVE' && (course.expired || course.used >= course.purchased),
  ),
)
const historyFromInactive = ref(false)
function branchLabel(patient: Patient | null) {
  const name = patient?.branchName || patient?.branchCode
  return name ? (name.startsWith('สาขา') ? name : `สาขา${name}`) : 'ไม่ระบุสาขา'
}
function courseUsagePercent(course: UiCourse) {
  if (!course.total) return 0
  return Math.min(
    100,
    Math.max(0, Math.round(((course.total - course.balance) / course.total) * 100)),
  )
}
async function loadOwner() {
  const [patientList, staffList] = await Promise.all([patientApi.searchAll(), staffApi.list()])
  owners.value = patientList
  therapists.value = staffList.filter((person) => person.role === 'THERAPIST' && person.active)
  try {
    branches.value = await branchApi.list()
  } catch {
    branches.value = []
  }
  const patientId = Number(route.query.patientId)
  owner.value = owners.value.find((patient) => patient.id === patientId) ?? owners.value[0] ?? null
  if (owner.value) await loadCourses(owner.value.id)
}
async function loadCourses(patientId: number) {
  const purchases: CoursePurchase[] = await courseApi.patientCourses(patientId)
  patientCourses.value = purchases.map((course) => ({
    ...course,
    name: course.course.name,
    expiry: new Date(course.expiresAt).toLocaleDateString('th-TH'),
    balance: course.remaining,
    total: course.purchased,
  }))
  const requestedCourseId = Number(route.query.courseId)
  const requestedCourse = patientCourses.value.find((course) => course.id === requestedCourseId)
  selectedCourseId.value = requestedCourse?.id ?? patientCourses.value[0]?.id ?? 0
  if (route.query.view === 'after-transfer') {
    transferReceiptId.value = 1
    const persistedCourseId = Number(route.query.courseId)
    selectedCourse.value =
      patientCourses.value.find((course) => course.id === persistedCourseId) ??
      patientCourses.value[0] ??
      ({} as UiCourse)
    const persistedBalance = Number(route.query.balance)
    const afterTransferBalance =
      Number.isFinite(persistedBalance) && route.query.balance !== undefined
        ? persistedBalance
        : Math.max(0, selectedCourse.value.balance - transferAmount.value)
    selectedCourse.value = {
      ...selectedCourse.value,
      balance: afterTransferBalance,
      remaining: afterTransferBalance,
    }
    patientCourses.value = patientCourses.value.map((course) =>
      course.id === selectedCourse.value.id ? selectedCourse.value : course,
    )
    selectedCourseId.value = selectedCourse.value.id ?? 0
    if (route.query.action === 'inactive-history') {
      historyFromInactive.value = true
      action.value = 'history'
      try {
        const ledger = await courseApi.ledger(selectedCourse.value.id)
        courseUsageHistory.value = ledger.movements as CourseMovement[]
      } catch {
        courseUsageHistory.value = []
      }
      return
    }
    if (route.query.action === 'use-success') {
      action.value = 'use'
      usageReview.value = false
      usageSuccess.value = true
      return
    }
    if (route.query.action === 'use') {
      action.value = 'use'
      usageReview.value = true
      usageSuccess.value = false
      return
    }
    if (route.query.action === 'history') {
      action.value = 'history'
      try {
        const ledger = await courseApi.ledger(selectedCourse.value.id)
        courseUsageHistory.value = ledger.movements as CourseMovement[]
      } catch {
        courseUsageHistory.value = []
      }
      return
    }
    if (route.query.action === 'transfer-success') {
      action.value = 'transfer'
      transferStep.value = 'success'
      selectedRecipient.value =
        owners.value.find((patient) => patient.id !== owner.value?.id) ?? null
      return
    }
    action.value = 'manage'
    return
  }
  selectedCourse.value = requestedCourse ?? patientCourses.value[0] ?? ({} as UiCourse)
  if (route.query.action === 'history') {
    action.value = 'history'
    coursePrintPreview.value = route.query.print === '1'
    try {
      const ledger = await courseApi.ledger(selectedCourse.value.id)
      courseUsageHistory.value = ledger.movements as CourseMovement[]
    } catch {
      courseUsageHistory.value = []
    }
  } else if (route.query.action === 'use') {
    action.value = 'use'
    usageReview.value = false
    usageSuccess.value = false
  } else action.value = 'manage'
}
onMounted(async () => {
  try {
    await loadOwner()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดคอร์สไม่สำเร็จ'
  }
})
async function searchRecipients() {
  const keyword = recipientSearch.value.trim()
  recipientResults.value = []
  recipientSearchOpen.value = true
  if (!keyword) return
  try {
    const results = await patientApi.search(keyword, 0, 20)
    recipientResults.value = results.items
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
  }
}
async function start(nextAction: CourseAction, course: UiCourse) {
  const openedFromTransferredCourse = Boolean(transferReceiptId.value)
  action.value = nextAction
  selectedCourse.value = course
  selectedCourseId.value = course.id
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  recipientConfirmed.value = false
  transferAmount.value = 2
  transferReason.value = ''
  showReasonError.value = false
  coTherapistId.value = ''
  primaryShare.value = 50
  secondaryShare.value = 50
  substituteNote.value = ''
  if (!(openedFromTransferredCourse && (nextAction === 'history' || nextAction === 'use'))) {
    transferReceiptId.value = null
  }
  if (openedFromTransferredCourse && (nextAction === 'history' || nextAction === 'use')) {
    await router.replace({
      path: '/staff/courses',
      query: {
        patientId: owner.value?.id,
        view: 'after-transfer',
        action: nextAction,
        courseId: course.id,
        balance: course.balance,
      },
    })
  }
  usageReview.value = false
  usageSuccess.value = false
  transferStep.value = 'form'
  if (nextAction === 'use') usageReview.value = true
  if (nextAction === 'history') {
    try {
      const ledger = await courseApi.ledger(course.id)
      courseUsageHistory.value = ledger.movements as CourseMovement[]
    } catch (error) {
      courseUsageHistory.value = []
      errorMessage.value =
        error instanceof Error ? error.message : 'โหลดประวัติการใช้คอร์สไม่สำเร็จ'
    }
  }
}
async function openInactiveHistory() {
  historyFromInactive.value = true
  const course = inactiveCourses.value[0]
  if (course) {
    await start('history', course)
    historyFromInactive.value = true
    await router.replace({
      path: '/staff/courses',
      query: {
        patientId: owner.value?.id,
        view: 'after-transfer',
        action: 'inactive-history',
        courseId: course.id,
        balance: course.balance,
      },
    })
  } else {
    action.value = 'history'
    selectedCourse.value = {} as UiCourse
    courseUsageHistory.value = []
  }
}
async function changeInactiveHistoryCourse() {
  const course = inactiveCourses.value.find((item) => item.id === selectedCourseId.value)
  if (course) await start('history', course)
}
function selectCourse() {
  const course = patientCourses.value.find((item) => item.id === selectedCourseId.value)
  if (course) selectedCourse.value = course
}
function goBack() {
  if (route.path.startsWith('/staff/patients/') && owner.value) {
    void router.push({
      path: `/staff/patients/${owner.value.id}/history`,
      query: { tab: 'courses' },
    })
    return
  }
  action.value = 'manage'
  historyFromInactive.value = false
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  recipientConfirmed.value = false
  usageReview.value = false
  usageSuccess.value = false
  transferStep.value = 'form'
  if (transferReceiptId.value && owner.value) {
    router.replace({
      path: '/staff/courses',
      query: {
        patientId: owner.value.id,
        view: 'after-transfer',
        courseId: selectedCourse.value.id,
        balance: selectedCourse.value.balance,
      },
    })
  }
}
function cancelPatientCourseDetail() {
  if (route.path.startsWith('/staff/patients/') && owner.value) {
    void router.push({
      path: `/staff/patients/${owner.value.id}/history`,
      query: { tab: 'courses' },
    })
    return
  }
  coursePrintPreview.value = false
}
function returnToCourseManager() {
  action.value = 'manage'
  usageReview.value = false
  usageSuccess.value = false
  if (owner.value) {
    router.replace({
      path: '/staff/courses',
      query: transferReceiptId.value
        ? {
            patientId: owner.value.id,
            view: 'after-transfer',
            courseId: selectedCourse.value.id,
            balance: selectedCourse.value.balance,
          }
        : { patientId: owner.value.id },
    })
  }
}
async function confirm() {
  if (!owner.value || !selectedCourse.value.id) return
  try {
    const isCourseUsage = action.value === 'use'
    const usedCourseId = selectedCourse.value.id
    const balanceBeforeUse = selectedCourse.value.balance
    if (isCourseUsage) {
      usageConfirmDialog.value = false
      await courseApi.use({
        coursePurchaseId: selectedCourse.value.id,
        note: usageNote.value || 'ใช้สิทธิ์ผ่านหน้าจัดการคอร์ส',
      })
    } else {
      if (!selectedRecipient.value) return
      const transfer = await courseApi.transfer({
        sourcePurchaseId: selectedCourse.value.id,
        destinationPatientId: selectedRecipient.value.id,
        quantity: transferAmount.value,
        reason: transferReason.value || 'แชร์คอร์สตามคำขอผู้ป่วย',
      })
      transferReceiptId.value = transfer.id
    }
    await loadCourses(owner.value.id)
    if (isCourseUsage) {
      selectedCourse.value =
        patientCourses.value.find((course) => course.id === usedCourseId) ?? selectedCourse.value
      const newBalance = Math.max(0, balanceBeforeUse - 1)
      selectedCourse.value = { ...selectedCourse.value, balance: newBalance, remaining: newBalance }
      patientCourses.value = patientCourses.value.map((course) =>
        course.id === usedCourseId ? selectedCourse.value : course,
      )
      action.value = 'use'
      usageReview.value = false
      usageSuccess.value = true
      if (transferReceiptId.value) {
        await router.replace({
          path: '/staff/courses',
          query: {
            patientId: owner.value.id,
            view: 'after-transfer',
            action: 'use-success',
            courseId: usedCourseId,
            balance: newBalance,
          },
        })
      }
    } else {
      selectedCourse.value =
        patientCourses.value.find((course) => course.id === usedCourseId) ?? selectedCourse.value
      action.value = 'transfer'
      transferStep.value = 'success'
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'แชร์คอร์สไม่สำเร็จ'
  }
}
function selectRecipient(patient: Patient) {
  selectedRecipient.value = patient
  recipientConfirmed.value = false
  recipientSearchOpen.value = false
  recipientSearch.value = `${patient.fullName} HN ${patient.hn}`
}
function handleRecipientInput() {
  selectedRecipient.value = null
  recipientConfirmed.value = false
  recipientSearchOpen.value = false
  recipientResults.value = []
}
async function handleTransferPrimaryAction() {
  if (!selectedRecipient.value) {
    await searchRecipients()
    return
  }
  if (!recipientConfirmed.value) {
    recipientConfirmed.value = true
    const suggestedTherapist = therapists.value[0]
    coTherapistId.value = suggestedTherapist?.id ?? ''
    primaryShare.value = 70
    secondaryShare.value = 30
    return
  }
  if (!transferReason.value.trim()) {
    showReasonError.value = true
    return
  }
  transferStep.value = 'review'
}
function clearRecipient() {
  recipientSearch.value = ''
  selectedRecipient.value = null
  recipientConfirmed.value = false
  recipientSearchOpen.value = false
}
function startAnotherTransfer() {
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  recipientConfirmed.value = false
  transferAmount.value = 2
  transferReason.value = ''
  showReasonError.value = false
  coTherapistId.value = ''
  primaryShare.value = 50
  secondaryShare.value = 50
  substituteNote.value = ''
  transferReceiptId.value = null
  transferStep.value = 'form'
}
async function openLatestTransfer(course: UiCourse) {
  selectedCourse.value = course
  selectedRecipient.value = owners.value.find((patient) => patient.id !== owner.value?.id) ?? null
  transferReceiptId.value ||= 1
  transferStep.value = 'success'
  action.value = 'transfer'
  await router.replace({
    path: '/staff/courses',
    query: {
      patientId: owner.value?.id,
      view: 'after-transfer',
      action: 'transfer-success',
      courseId: course.id,
      balance: course.balance,
    },
  })
}
function confirmPrintHistory() {
  printDialog.value = null
  try {
    window.print()
  } catch {
    printDialog.value = 'error'
  }
}
function closePrintSuccess() {
  printDialog.value = null
  coursePrintPreview.value = false
}
</script>

<template>
  <div class="staff-page course-page">
    <StaffHeader />
    <main
      v-if="action === 'use'"
      class="course-use-main"
      :class="{ 'after-transfer': transferReceiptId }"
    >
      <section v-if="usageSuccess && transferReceiptId" class="course-after-use-success-backdrop">
        <article class="course-after-use-success-modal">
          <h1>ใช้สิทธิ์สำเร็จ</h1>
          <p>บันทึกการใช้สิทธิ์ {{ selectedCourse.name }} 1 ครั้งแล้ว</p>
          <div>
            <h2>{{ owner?.fullName }}　HN {{ owner?.hn }}</h2>
            <h3>{{ selectedCourse.name }}</h3>
            <strong>สิทธิ์คงเหลือหลังใช้ครั้งนี้</strong>
            <b
              >{{ usageSuccessBalance }} <small>/ {{ selectedCourse.total }} ครั้ง</small></b
            >
          </div>
          <footer>
            <button type="button" @click="returnToCourseManager">กลับไปจัดการคอร์ส</button>
            <button type="button" @click="start('history', selectedCourse)">
              ดูประวัติการใช้คอร์ส
            </button>
          </footer>
        </article>
      </section>
      <section v-else-if="usageSuccess" class="course-use-success">
        <div class="course-use-success-icon" aria-hidden="true">
          <img :src="courseUsageSuccessIcon" alt="" />
          <span>✓</span>
        </div>
        <h1>ใช้สิทธิ์สำเร็จ</h1>
        <p>บันทึก {{ selectedCourse.name }} จำนวน 1 ครั้งเรียบร้อยแล้ว</p>
        <div class="course-use-success-details">
          <p>
            <span>รายการคอร์ส</span><strong>#{{ selectedCourse.id }}</strong>
          </p>
          <p>
            <span>สิทธิ์คงเหลือ</span><strong>{{ usageSuccessBalance }} ครั้ง</strong>
          </p>
        </div>
        <footer>
          <button type="button" @click="start('history', selectedCourse)">ดูประวัติการใช้</button>
          <button type="button" @click="returnToCourseManager">กลับหน้าคอร์ส</button>
        </footer>
      </section>
      <section v-else-if="usageReview && transferReceiptId" class="course-after-use-confirm">
        <h1>ตรวจสอบการใช้สิทธิ์คอร์ส</h1>
        <div class="course-after-use-details">
          <h2>{{ owner?.fullName }}　HN {{ owner?.hn }}</h2>
          <h3>{{ selectedCourse.name }}</h3>
          <p>วันที่รับบริการ　{{ formattedUsageDate }}</p>
          <p>นักกายภาพ　ผู้ให้บริการตามตาราง</p>
          <p>สาขา　{{ branchLabel(owner) }}</p>
          <strong>สิทธิ์คงเหลือ</strong>
          <b
            >{{ selectedCourse.balance }} <small>/ {{ selectedCourse.total }} ครั้ง</small></b
          >
          <span>หมดอายุ {{ selectedCourse.expiry }}</span>
        </div>
        <footer>
          <button type="button" @click="returnToCourseManager">ยกเลิกการใช้สิทธิ์</button>
          <button
            type="button"
            :disabled="!selectedCourse.usable"
            @click="usageConfirmDialog = true"
          >
            ใช้สิทธิ์คอร์ส
          </button>
        </footer>
      </section>
      <section v-else-if="usageReview" class="course-use-review">
        <button class="course-review-back" type="button" @click="returnToCourseManager">
          ← กลับไปหน้าบริการ
        </button>
        <h1>ตรวจสอบการใช้สิทธิ์</h1>
        <p class="course-use-review-intro">ตรวจข้อมูลผู้ป่วยและรายละเอียดบริการก่อนยืนยัน</p>
        <section class="course-review-patient">
          <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) }}</i>
          <div>
            <strong>{{ owner?.fullName }}</strong
            ><span>HN {{ owner?.hn }}　☎ {{ owner?.phone }}</span>
          </div>
          <b>{{ branchLabel(owner) }}</b>
        </section>
        <div class="course-review-columns">
          <section class="course-review-details">
            <h2>ข้อมูลการรับบริการ</h2>
            <dl>
              <div>
                <dt>ผู้ป่วย</dt>
                <dd>{{ owner?.fullName }} · HN {{ owner?.hn }}</dd>
              </div>
              <div>
                <dt>คอร์ส</dt>
                <dd>{{ selectedCourse.name }}</dd>
              </div>
              <div>
                <dt>Course ID</dt>
                <dd>{{ selectedCourse.course?.code }}</dd>
              </div>
              <div>
                <dt>วันที่รับบริการ</dt>
                <dd>{{ formattedUsageDate }}</dd>
              </div>
              <div>
                <dt>นักกายภาพ</dt>
                <dd>ผู้ให้บริการตามตาราง</dd>
              </div>
              <div>
                <dt>สาขา</dt>
                <dd>{{ branchLabel(owner) }}</dd>
              </div>
              <div>
                <dt>จำนวนที่ใช้ครั้งนี้</dt>
                <dd>1 ครั้ง</dd>
              </div>
            </dl>
          </section>
          <aside class="course-review-summary">
            <h2>สรุปสิทธิ์</h2>
            <div>
              <span>สิทธิ์คงเหลือหลังทำรายการ</span>
              <strong>{{ Math.max(0, selectedCourse.balance - 1) }} ครั้ง</strong>
              <small>ใช้สิทธิ์ครั้งนี้ 1 ครั้ง</small>
            </div>
            <footer>
              <button type="button" @click="returnToCourseManager">กลับไปแก้ไข</button>
              <button
                type="button"
                :disabled="!selectedCourse.usable"
                @click="usageConfirmDialog = true"
              >
                ยืนยันใช้สิทธิ์
              </button>
            </footer>
          </aside>
        </div>
      </section>
      <template v-else>
        <RouterLink
          class="course-use-back"
          :to="{ path: '/staff/services', query: { patientId: owner?.id } }"
          >← กลับไปหน้าบริการ</RouterLink
        >
        <h1>ใช้สิทธิ์คอร์ส</h1>
        <section class="course-use-patient">
          <strong>{{ owner?.fullName }}</strong
          ><span>HN {{ owner?.hn }} · {{ branchLabel(owner) }}</span>
        </section>
        <section v-if="!patientCourses.length" class="course-use-empty">
          <h2>ยังไม่มีคอร์สที่ใช้สิทธิ์ได้</h2>
          <p>ผู้ป่วยรายนี้ยังไม่มีคอร์สคงเหลือสำหรับใช้บริการ</p>
          <button type="button" @click="returnToCourseManager">กลับหน้าจัดการคอร์ส</button>
        </section>
        <div v-else class="course-use-columns">
          <section class="course-use-form">
            <h2>รายละเอียดการใช้สิทธิ์</h2>
            <label
              >คอร์สที่เลือก<select v-model="selectedCourseId" @change="selectCourse">
                <option v-for="course in patientCourses" :key="course.id" :value="course.id">
                  {{ course.name }} จำนวนสิทธิ์ {{ course.balance }} / {{ course.total }} ครั้ง
                </option>
              </select></label
            >
            <label>บริการที่ใช้สิทธิ์ได้<input :value="selectedCourse.name" readonly /></label>
            <label>นักกายภาพ<input value="ผู้ให้บริการตามตาราง" readonly /></label>
            <label
              >วันที่ใช้บริการ<ThaiDatePicker
                v-model="usageDate"
                show-calendar-icon
                numeric-month
                hide-caret
            /></label>
            <label>จำนวนสิทธิ์ที่ใช้ครั้งนี้<input value="1 ครั้ง" readonly /></label>
            <label
              >หมายเหตุ (ไม่บังคับ)<textarea
                v-model.trim="usageNote"
                placeholder="ระบุรายละเอียดการใช้บริการ"
              />
            </label>
          </section>
          <aside class="course-use-summary">
            <h2>สรุปการใช้สิทธิ์</h2>
            <button type="button" :disabled="!selectedCourse.usable" @click="usageReview = true">
              ตรวจสอบและใช้สิทธิ์
            </button>
            <b>{{ selectedCourse.name }}</b>
            <p>ก่อนใช้ {{ selectedCourse.balance }} ครั้ง</p>
            <p>ใช้ครั้งนี้ 1 ครั้ง</p>
            <strong>จำนวนคงเหลือ {{ Math.max(0, selectedCourse.balance - 1) }} ครั้ง</strong>
            <div class="course-progress">
              <i
                :style="{
                  width: `${(Math.max(0, selectedCourse.balance - 1) / selectedCourse.total) * 100}%`,
                }"
              />
            </div>
            <small>หมดอายุ {{ selectedCourse.expiry }}</small>
          </aside>
        </div>
      </template>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <div
        v-if="usageConfirmDialog"
        class="course-confirm-backdrop"
        @click.self="usageConfirmDialog = false"
      >
        <section
          class="course-confirm-dialog course-usage-confirm"
          :class="{ 'after-transfer': transferReceiptId }"
          role="dialog"
          aria-modal="true"
          aria-labelledby="course-confirm-title"
        >
          <h2 id="course-confirm-title">
            {{ transferReceiptId ? 'ยืนยันการใช้สิทธิ์ 1 ครั้ง ?' : 'ยืนยันการใช้สิทธิ์?' }}
          </h2>
          <p>
            {{
              transferReceiptId
                ? 'ตรวจสอบยอดคงเหลือหลังใช้สิทธิ์ก่อนยืนยัน'
                : 'โปรดตรวจสอบจำนวนสิทธิ์ก่อนทำรายการ'
            }}
          </p>
          <div v-if="transferReceiptId" class="course-confirm-details course-after-confirm-details">
            <strong>{{ owner?.fullName }}　HN {{ owner?.hn }}</strong>
            <b>{{ selectedCourse.name }}</b>
            <p>จำนวนคงเหลือ {{ selectedCourse.balance }}/{{ selectedCourse.total }} ครั้ง</p>
          </div>
          <div v-else class="course-confirm-details">
            <strong>{{ selectedCourse.name }} · ใช้ 1 ครั้ง</strong>
            <p>
              <span>ผู้ป่วย</span><b>{{ owner?.fullName }}</b>
            </p>
            <p>
              <span>คงเหลือก่อนใช้</span><b>{{ selectedCourse.balance }} ครั้ง</b>
            </p>
            <p>
              <span>คงเหลือหลังใช้</span><b>{{ Math.max(0, selectedCourse.balance - 1) }} ครั้ง</b>
            </p>
          </div>
          <div class="course-confirm-actions">
            <button type="button" @click="usageConfirmDialog = false">
              {{ transferReceiptId ? 'กลับไปตรวจสอบ' : 'ยังไม่ยืนยัน' }}</button
            ><button type="button" @click="confirm">
              {{ transferReceiptId ? 'ยืนยันใช้ 1 ครั้ง' : 'ยืนยันใช้สิทธิ์' }}
            </button>
          </div>
        </section>
      </div>
    </main>
    <main
      v-else-if="action === 'transfer'"
      class="course-transfer-main"
      :class="{ 'is-review': transferStep === 'review', 'is-success': transferStep === 'success' }"
    >
      <button class="course-transfer-back" type="button" @click="goBack">
        ← กลับไปหน้าจัดการคอร์ส
      </button>
      <h1>{{ transferStep === 'review' ? 'ตรวจสอบการแชร์คอร์ส' : 'แชร์คอร์ส' }}</h1>
      <p class="course-transfer-intro">
        {{
          transferStep === 'review'
            ? 'ตรวจสอบผู้ให้ ผู้รับ และจำนวนสิทธิ์ก่อนยืนยัน'
            : 'ระบุผู้รับ จำนวนสิทธิ์ เหตุผล และสัดส่วนยอดขาย'
        }}
      </p>
      <ol class="course-transfer-steps" aria-label="ขั้นตอนการแชร์คอร์ส">
        <li :class="{ active: transferStep === 'form', completed: transferStep !== 'form' }">
          <span>1</span><b>ระบุข้อมูล</b>
        </li>
        <li :class="{ active: transferStep === 'review', completed: transferStep === 'success' }">
          <span>2</span><b>ตรวจสอบและยืนยัน</b>
        </li>
        <li :class="{ active: transferStep === 'success' }"><span>3</span><b>สำเร็จ</b></li>
      </ol>
      <template v-if="transferStep === 'form'">
        <section class="course-transfer-owner-summary">
          <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) }}</i>
          <div>
            <strong>{{ owner?.fullName }}</strong
            ><span>HN {{ owner?.hn }}　☎ {{ owner?.phone }}</span>
          </div>
          <b>{{ branchLabel(owner) }}</b>
        </section>
        <div class="course-transfer-columns">
          <section class="course-transfer-source">
            <h2>คอร์สที่ต้องการแชร์</h2>
            <div class="course-transfer-course-card">
              <h3>{{ selectedCourse.name }}</h3>
              <span>{{ selectedCourse.course?.code }} · ใช้งานได้</span>
              <p>
                ทั้งหมด {{ selectedCourse.total }}　 ใช้แล้ว
                {{ Math.max(0, selectedCourse.total - selectedCourse.balance) }}　 คงเหลือ
                {{ selectedCourse.balance }} ครั้ง
              </p>
            </div>
            <label
              >จำนวนสิทธิ์ที่ต้องการแชร์<span class="course-transfer-stepper"
                ><button type="button" :disabled="transferAmount <= 1" @click="transferAmount--">
                  −</button
                ><input
                  v-model.number="transferAmount"
                  type="number"
                  min="1"
                  :max="selectedCourse.balance"
                /><button
                  type="button"
                  :disabled="transferAmount >= selectedCourse.balance"
                  @click="transferAmount++"
                >
                  +</button
                ><em
                  >ผู้ให้จะเหลือ
                  {{ Math.max(0, selectedCourse.balance - (Number(transferAmount) || 0)) }}
                  ครั้ง</em
                ></span
              ></label
            >
            <label class="course-transfer-reason"
              >เหตุผลการแชร์ *<input
                v-model.trim="transferReason"
                :class="{ invalid: showReasonError && !transferReason.trim() }"
                :aria-invalid="showReasonError && !transferReason.trim()"
                @input="showReasonError = false"
                placeholder="ระบุเหตุผลเพื่อบันทึกในประวัติการแชร์"
              /><small v-if="showReasonError && !transferReason.trim()"
                >กรุณาระบุเหตุผลการแชร์</small
              ></label
            >
          </section>
          <section class="course-transfer-recipient">
            <h2>ผู้รับและผู้ดูแลร่วม</h2>
            <label
              >ค้นหาผู้รับสิทธิ์ *<input
                v-model.trim="recipientSearch"
                placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"
                @input="handleRecipientInput"
              /><small>แสดงเฉพาะผู้ป่วยที่ลงทะเบียนแล้ว</small></label
            >
            <div
              v-if="recipientSearchOpen && recipientSearch.trim() && !selectedRecipient"
              class="course-transfer-results"
            >
              <button
                v-for="patientResult in recipientResults.filter((item) => item.id !== owner?.id)"
                :key="patientResult.id"
                type="button"
                @click="selectRecipient(patientResult)"
              >
                <i>{{ patientResult.fullName.slice(0, 1) }}</i
                ><span
                  ><strong>{{ patientResult.fullName }}</strong
                  ><small>HN {{ patientResult.hn }} · {{ patientResult.phone || '-' }}</small></span
                >
              </button>
              <p v-if="!recipientResults.filter((item) => item.id !== owner?.id).length">
                ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา
              </p>
            </div>
            <div v-if="selectedRecipient" class="course-transfer-selected">
              <i>{{ selectedRecipient.fullName.slice(0, 1) }}</i>
              <div>
                <strong>{{ selectedRecipient.fullName }}</strong
                ><span>HN {{ selectedRecipient.hn }} · {{ selectedRecipient.phone || '-' }}</span>
              </div>
              <button type="button" aria-label="เปลี่ยนผู้รับ" @click="clearRecipient">×</button>
            </div>
            <label
              >นักกายภาพคนที่ 2 *
              <select v-model="coTherapistId">
                <option value="">เลือกนักกายภาพคนที่ 2</option>
                <option v-for="person in therapists" :key="person.id" :value="person.id">
                  {{ person.fullName }}
                </option></select
              ><small>พิมพ์ชื่อหรือเลือกจากรายชื่อในระบบ</small>
            </label>
            <div class="course-share-allocation">
              <strong>สัดส่วนยอดขาย</strong>
              <label
                >นักกายภาพหลัก<input
                  v-if="coTherapistId"
                  v-model.number="primaryShare"
                  type="number"
                  min="0"
                  max="100"
                /><em v-else>—</em></label
              >
              <label
                >{{
                  therapists.find((person) => person.id === coTherapistId)?.fullName ||
                  'นักกายภาพคนที่ 2'
                }}<input
                  v-if="coTherapistId"
                  v-model.number="secondaryShare"
                  type="number"
                  min="0"
                  max="100"
                /><em v-else>—</em></label
              >
              <small v-if="coTherapistId" :class="{ invalid: !shareIsValid }"
                >รวม {{ primaryShare + secondaryShare }}% — ต้องรวมเท่ากับ 100%</small
              >
            </div>
            <p class="course-transfer-allocation-hint">กำหนดสัดส่วนหลังเลือกนักกายภาพคนที่ 2</p>
            <p class="course-transfer-help">
              ค่ารักษาแทนคำนวณจากนักกายภาพผู้ให้บริการจริง ไม่ได้แบ่งตามสัดส่วนนี้
            </p>
          </section>
        </div>
        <footer class="course-transfer-footer">
          <button type="button" @click="goBack">ยกเลิก</button
          ><button
            type="button"
            :disabled="
              selectedRecipient && recipientConfirmed
                ? !coTherapistId ||
                  !shareIsValid ||
                  transferAmount < 1 ||
                  transferAmount > selectedCourse.balance
                : selectedRecipient
                  ? false
                  : !recipientSearch.trim()
            "
            @click="handleTransferPrimaryAction"
          >
            {{
              selectedRecipient
                ? recipientConfirmed
                  ? 'ตรวจสอบข้อมูล'
                  : 'เลือกผู้รับนี้'
                : 'ค้นหาผู้รับ'
            }}
          </button>
        </footer>
      </template>
      <section v-else-if="transferStep === 'review'" class="course-transfer-review">
        <div class="course-transfer-review-people">
          <article>
            <span>ผู้ให้สิทธิ์</span><strong>{{ owner?.fullName }}</strong
            ><small>HN {{ owner?.hn }} · {{ branchLabel(owner) }}</small>
          </article>
          <b class="course-transfer-direction" aria-hidden="true">→</b>
          <article>
            <span>ผู้รับสิทธิ์</span><strong>{{ selectedRecipient?.fullName }}</strong
            ><small>HN {{ selectedRecipient?.hn }} · {{ branchLabel(selectedRecipient) }}</small>
          </article>
        </div>
        <div class="course-transfer-review-summary">
          <p>
            <span>คอร์ส</span><b>{{ selectedCourse.name }} · {{ selectedCourse.course?.code }}</b>
          </p>
          <p>
            <span>จำนวนที่แชร์</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>ผู้ให้คงเหลือหลังแชร์</span
            ><b>{{ selectedCourse.balance - transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>นักกายภาพผู้ดูแลร่วม</span
            ><b>{{ therapists.find((person) => person.id === coTherapistId)?.fullName }}</b>
          </p>
          <p>
            <span>สัดส่วนยอดขาย</span
            ><b
              >นักกายภาพหลัก {{ primaryShare }}% ·
              {{
                therapists.find((person) => person.id === coTherapistId)?.fullName ||
                'นักกายภาพคนที่ 2'
              }}
              {{ secondaryShare }}%</b
            >
          </p>
          <p>
            <span>ผู้รับได้รับสิทธิ์</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>สิทธิ์ที่ผู้รับใช้ได้หลังแชร์</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>เหตุผลการแชร์</span><b>{{ transferReason }}</b>
          </p>
        </div>
        <p class="course-transfer-review-note">
          ยืนยันว่าข้อมูลตรงตามความประสงค์ของเจ้าของคอร์สที่แจ้งกับคลินิก
        </p>
        <footer>
          <button type="button" @click="transferStep = 'form'">กลับไปแก้ไข</button
          ><button type="button" @click="confirm">ยืนยันแชร์คอร์ส</button>
        </footer>
      </section>
      <section v-else class="course-transfer-success">
        <div class="course-transfer-success-icon">✓</div>
        <h2>แชร์คอร์สสำเร็จ</h2>
        <p>
          เพิ่ม {{ selectedRecipient?.fullName }} เป็นผู้ใช้สิทธิ์ในคอร์ส
          {{ selectedCourse.name }} แล้ว
        </p>
        <div class="course-transfer-success-details">
          <p><span>เลขอ้างอิง</span><b>SHR-2569-0001</b></p>
          <p>
            <span>จำนวนที่แชร์</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>ผู้ให้คงเหลือ</span><b>{{ selectedCourse.balance }} ครั้ง</b>
          </p>
          <p>
            <span>ผู้รับได้รับสิทธิ์</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
          <p>
            <span>สิทธิ์ที่ผู้รับใช้ได้</span><b>{{ transferAmount }} ครั้ง</b>
          </p>
        </div>
        <div class="course-transfer-success-actions">
          <button type="button" @click="startAnotherTransfer">แชร์คอร์สอีกครั้ง</button
          ><button type="button" @click="returnToCourseManager">ดูคอร์สหลังแชร์</button>
        </div>
      </section>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </main>
    <main
      v-else-if="action === 'history'"
      class="course-history-main"
      :class="{
        'after-transfer': transferReceiptId && !historyFromInactive,
        'inactive-history': historyFromInactive,
      }"
    >
      <section v-if="coursePrintPreview" class="course-cut-preview">
        <button class="course-cut-back-link" type="button" @click="cancelPatientCourseDetail">
          ← กลับไปประวัติการรักษา
        </button>
        <article class="course-cut-document">
          <header>
            <h1>ใบตัดคอร์สการรักษา</h1>
            <p>ที่อยู่: {{ documentClinicAddress }}</p>
            <p>โทรศัพท์: {{ documentClinicPhone }}</p>
          </header>
          <p class="course-cut-issued">ออก ณ วันที่ : 29/06/2569</p>
          <section class="course-cut-summary">
            <p>
              วันที่ซื้อคอร์ส {{ new Date(selectedCourse.purchasedAt).toLocaleDateString('th-TH') }}
            </p>
            <p>จำนวน: {{ selectedCourse.total }} ครั้ง</p>
            <p>ใช้ไปแล้ว: {{ usedSessions }} ครั้ง</p>
            <p>คงเหลือ: {{ selectedCourse.total - usedSessions }} ครั้ง</p>
            <strong>ชื่อคอร์ส : {{ selectedCourse.name }}</strong>
            <strong
              >วันหมดอายุ:
              {{ new Date(selectedCourse.expiresAt).toLocaleDateString('th-TH') }}</strong
            >
          </section>
          <div class="course-cut-table" role="table" aria-label="รายการใช้สิทธิ์คอร์ส">
            <div class="course-cut-table-head" role="row">
              <b>วันที่รับบริการ</b><b>นักกายภาพผู้ให้บริการ</b><b>สาขา</b><b>จำนวน</b>
            </div>
            <div v-for="entry in [...usageEntries].reverse()" :key="entry.id" role="row">
              <span>{{
                new Date(entry.createdAt).toLocaleDateString('th-TH', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                })
              }}</span>
              <span>{{ entry.reason || '—' }}</span>
              <span>{{ branchLabel(owner) }}</span>
              <span>{{ Math.abs(entry.quantity) }}</span>
            </div>
            <div class="course-cut-total" role="row">
              <b>รวมจำนวนที่ใช้</b><b>{{ usedSessions }}</b>
            </div>
          </div>
        </article>
        <footer class="course-cut-actions">
          <button type="button" @click="cancelPatientCourseDetail">ยกเลิก</button>
          <button type="button" @click="printDialog = 'confirm'">พิมพ์เอกสาร</button>
        </footer>
      </section>
      <template v-else>
        <button class="course-history-back" type="button" @click="goBack">
          ← กลับไปหน้าจัดการคอร์ส
        </button>
        <h1>
          {{ transferReceiptId && !historyFromInactive ? 'ประวัติคอร์ส' : 'ประวัติการใช้คอร์ส' }}
        </h1>
        <p class="course-history-intro">
          {{
            transferReceiptId || historyFromInactive
              ? ''
              : 'ตรวจสอบรายการใช้สิทธิ์และพิมพ์ใบตัดคอร์ส'
          }}
        </p>
        <section class="course-history-patient">
          <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) || '--' }}</i>
          <div>
            <strong>{{ owner?.fullName }}</strong>
            <span
              >HN {{ owner?.hn
              }}<template v-if="!transferReceiptId || historyFromInactive"
                >　☎ {{ owner?.phone }}</template
              ></span
            >
          </div>
          <em>{{ branchLabel(owner) }}</em>
        </section>
        <label
          v-if="historyFromInactive && inactiveCourses.length > 1"
          class="course-history-selector"
          >คอร์สที่หมดอายุ / ใช้สิทธิ์ครบ<select
            v-model="selectedCourseId"
            @change="changeInactiveHistoryCourse"
          >
            <option v-for="course in inactiveCourses" :key="course.id" :value="course.id">
              {{ course.name }} ({{ course.course.code }})
            </option>
          </select></label
        >
        <template v-if="selectedCourse.id">
          <section
            v-if="historyFromInactive"
            class="course-after-history-totals course-inactive-history-totals"
            aria-label="สรุปคอร์สหมดอายุหรือใช้สิทธิ์ครบ"
          >
            <h2>{{ selectedCourse.name }} ({{ selectedCourse.course?.code }})</h2>
            <div>
              <article>
                <span>ทั้งหมด</span><strong>{{ selectedCourse.total }} ครั้ง</strong>
              </article>
              <article>
                <span>ใช้แล้ว</span
                ><strong>{{ selectedCourse.total - selectedCourse.balance }} ครั้ง</strong>
              </article>
              <article><span>โอนออก</span><strong>0 ครั้ง</strong></article>
              <article>
                <span>คงเหลือ</span><strong>{{ historyRemaining }} ครั้ง</strong>
              </article>
            </div>
          </section>
          <section
            v-if="transferReceiptId && !historyFromInactive"
            class="course-after-history-totals"
            aria-label="สรุปประวัติคอร์ส"
          >
            <h2>{{ selectedCourse.name }} · {{ selectedCourse.course?.code }}</h2>
            <div>
              <article>
                <span>ทั้งหมด</span><strong>{{ selectedCourse.total }} ครั้ง</strong>
              </article>
              <article>
                <span>ใช้แล้ว</span><strong>{{ usedSessions }} ครั้ง</strong>
              </article>
              <article>
                <span>โอนออก</span><strong>{{ transferAmount }}</strong>
              </article>
              <article>
                <span>คงเหลือ</span><strong>{{ selectedCourse.balance }}</strong>
              </article>
            </div>
          </section>
          <section
            v-if="!historyFromInactive"
            class="course-history-summary"
            aria-label="สรุปสิทธิ์คอร์ส"
          >
            <div>
              <h2>{{ selectedCourse.name }}</h2>
              <p>
                {{ selectedCourse.course?.code }} · ซื้อเมื่อ
                {{
                  new Date(selectedCourse.purchasedAt).toLocaleDateString('th-TH', {
                    day: 'numeric',
                    month: 'long',
                    year: 'numeric',
                  })
                }}
              </p>
            </div>
            <div>
              <strong>คงเหลือ {{ historyRemaining }} ครั้ง</strong
              ><span
                >ทั้งหมด {{ selectedCourse.total }} · ใช้แล้ว {{ usedSessions }} · แชร์
                {{ transferredOut }}</span
              >
            </div>
          </section>
          <section class="course-history-entries">
            <div class="course-history-entries-header">
              <h2>
                {{
                  historyFromInactive
                    ? 'รายการใช้บริการ'
                    : transferReceiptId
                      ? 'รายการใช้บริการและการโอน'
                      : `รายการใช้สิทธิ์ ${usageEntries.length} รายการ`
                }}
              </h2>
              <button
                v-if="!transferReceiptId && !historyFromInactive"
                class="course-history-print"
                type="button"
                @click="coursePrintPreview = true"
              >
                พิมพ์ใบตัดคอร์ส
              </button>
            </div>
            <article
              v-if="transferReceiptId && !historyFromInactive"
              class="course-after-transfer-entry"
            >
              <strong>โอนสิทธิ์ · #{{ transferReceiptId }}</strong>
              <span
                >{{ owner?.fullName }} → {{ selectedRecipient?.fullName || 'ไม่ระบุผู้รับ' }} ·
                {{ branchLabel(owner) }}</span
              >
              <span
                >โอน {{ transferAmount }} ครั้ง · ผู้โอนคงเหลือ {{ selectedCourse.balance }} ครั้ง ·
                ผู้รับได้รับ {{ transferAmount }} ครั้ง</span
              >
            </article>
            <article v-for="entry in usageEntries" :key="entry.id" class="course-history-entry">
              <strong>{{
                new Date(entry.createdAt).toLocaleDateString('th-TH', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                })
              }}</strong>
              <div>
                <b>{{ selectedCourse.name }}</b>
                <span>{{ entry.reason || 'ไม่มีหมายเหตุ' }} · #{{ entry.id }}</span>
              </div>
              <aside>
                <b>ใช้ {{ Math.abs(entry.quantity) }} ครั้ง</b
                ><span>คงเหลือหลังใช้ {{ entry.balanceAfter }} ครั้ง</span>
              </aside>
            </article>
            <p v-if="!usageEntries.length" class="course-history-empty">
              ยังไม่มีประวัติการใช้บริการในคอร์สนี้
            </p>
          </section>
        </template>
        <p v-else class="course-history-empty">ยังไม่มีคอร์สที่หมดอายุหรือใช้สิทธิ์ครบ</p>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </template>
      <div v-if="printDialog" class="course-print-modal-backdrop" @click.self="printDialog = null">
        <section
          class="course-print-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="course-print-title"
        >
          <template v-if="printDialog === 'confirm'">
            <h2 id="course-print-title">ยืนยันการพิมพ์เอกสาร</h2>
            <p>ตรวจสอบชื่อผู้รับบริการ ข้อมูลคอร์ส และรายการใช้สิทธิ์ให้ครบถ้วนก่อนส่งพิมพ์</p>
            <footer>
              <button type="button" @click="printDialog = null">ยกเลิก</button>
              <button type="button" @click="confirmPrintHistory">ยืนยันและพิมพ์</button>
            </footer>
          </template>
          <template v-else-if="printDialog === 'success'">
            <h2 id="course-print-title">ส่งเอกสารไปยังเครื่องพิมพ์แล้ว</h2>
            <p>สถานะใบตัดคอร์สถูกบันทึกเป็น “พิมพ์แล้ว” สามารถกลับไปดูประวัติการใช้คอร์สได้</p>
            <footer>
              <button type="button" @click="printDialog = null">ยกเลิก</button>
              <button type="button" @click="closePrintSuccess">กลับหน้าประวัติ</button>
            </footer>
          </template>
          <template v-else>
            <h2 id="course-print-title">ไม่สามารถพิมพ์เอกสารได้</h2>
            <p>กรุณาตรวจสอบเครื่องพิมพ์แล้วลองใหม่อีกครั้ง</p>
            <footer>
              <button type="button" @click="printDialog = null">ยกเลิก</button>
              <button type="button" @click="printDialog = 'confirm'">ลองอีกครั้ง</button>
            </footer>
          </template>
        </section>
      </div>
    </main>
    <main
      v-else-if="action === 'manage'"
      class="course-manager-main"
      :class="{ 'after-transfer': transferReceiptId }"
    >
      <RouterLink
        class="course-use-back"
        :to="{ path: '/staff/services', query: { patientId: owner?.id } }"
        >← กลับไปหน้าบริการ</RouterLink
      >
      <h1>จัดการคอร์สผู้ป่วย</h1>
      <p class="course-manager-intro">ตรวจสอบสิทธิ์คงเหลือ ใช้สิทธิ์ ดูประวัติ และแชร์คอร์ส</p>
      <section v-if="transferReceiptId" class="course-after-patient">
        <strong>{{ owner?.fullName || 'กำลังโหลด...' }}</strong>
        <span>HN {{ owner?.hn || '-' }}</span>
      </section>
      <section v-else class="course-use-patient">
        <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) || '--' }}</i>
        <div>
          <strong>{{ owner?.fullName || 'กำลังโหลด...' }}</strong
          ><span>HN {{ owner?.hn || '-' }} <b>⌕</b> {{ owner?.phone || '-' }}</span>
        </div>
        <em>{{ branchLabel(owner) }}</em>
      </section>
      <div class="course-status-tabs">
        <button class="active" type="button">ใช้งานได้</button
        ><button type="button" @click="openInactiveHistory">หมดอายุ / ใช้สิทธิ์ครบ</button>
      </div>
      <section v-if="!activeCourses.length" class="course-use-empty">
        <h2>ยังไม่มีคอร์สที่ใช้งานได้</h2>
        <p>ผู้ป่วยรายนี้ยังไม่มีคอร์สคงเหลือสำหรับใช้บริการ</p>
      </section>
      <template v-for="course in activeCourses" :key="course.id">
        <section v-if="transferReceiptId" class="course-after-share-card">
          <header>
            <div>
              <h2>{{ course.name }}</h2>
              <p>{{ course.course.code }}</p>
            </div>
            <span>✓　ใช้งานได้</span>
          </header>
          <div class="course-after-remaining">
            <span>สิทธิ์คงเหลือ</span>
            <p>
              <strong>{{ course.balance }}</strong> ครั้ง
            </p>
            <small>จากทั้งหมด {{ course.total }} ครั้ง</small>
          </div>
          <div class="course-after-counts">
            <article>
              <span>ใช้แล้ว</span
              ><strong>{{ course.total - course.balance - transferAmount }} ครั้ง</strong>
            </article>
            <article>
              <span>แชร์ออก</span><strong>{{ transferAmount }} ครั้ง</strong>
            </article>
            <article>
              <span>สิทธิ์เพิ่มเติม</span><strong>{{ course.bonusSessions }} ครั้ง</strong>
            </article>
          </div>
          <div class="course-after-dates">
            <p>
              <span
                >วันที่ซื้อ<br /><b>{{
                  new Date(course.purchasedAt).toLocaleDateString('th-TH', {
                    day: 'numeric',
                    month: 'long',
                    year: 'numeric',
                  })
                }}</b></span
              >
            </p>
            <p>
              <span
                >วันหมดอายุ<br /><b>{{ course.expiry }}</b></span
              >
            </p>
          </div>
          <div class="course-after-actions">
            <button type="button" @click="start('history', course)">ดูประวัติคอร์ส</button>
            <button type="button" :disabled="!course.usable" @click="start('use', course)">
              ใช้สิทธิ์คอร์ส
            </button>
          </div>
          <button
            class="course-manager-latest-transfer"
            type="button"
            @click="openLatestTransfer(course)"
          >
            ← ดูรายการแชร์ล่าสุด
          </button>
        </section>
        <section v-else class="course-manager-card">
          <span>ใช้งานได้ · {{ course.course.code }}</span>
          <h2>{{ course.name }}</h2>
          <p>
            ซื้อเมื่อ
            {{
              new Date(course.purchasedAt).toLocaleDateString('th-TH', {
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })
            }}
            · Bonus {{ course.bonusSessions }} ครั้ง
          </p>
          <div class="course-manager-metrics" :class="{ transferred: transferReceiptId }">
            <article>
              <small>ทั้งหมด</small><strong>{{ course.total }} ครั้ง</strong>
            </article>
            <article>
              <small>ใช้แล้ว</small><strong>{{ course.total - course.balance }} ครั้ง</strong>
            </article>
            <article v-if="transferReceiptId">
              <small>แชร์ออก</small><strong>{{ transferAmount }} ครั้ง</strong>
            </article>
            <article>
              <small>คงเหลือ</small><strong>{{ course.balance }} ครั้ง</strong>
            </article>
          </div>
          <div
            class="course-progress"
            role="progressbar"
            aria-label="เปอร์เซ็นต์การใช้คอร์ส"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="courseUsagePercent(course)"
          >
            <i :style="{ width: `${courseUsagePercent(course)}%` }" />
          </div>
          <p class="course-manager-used">
            ใช้ไปแล้ว <strong>{{ courseUsagePercent(course) }}%</strong> ของคอร์ส
          </p>
          <div class="course-manager-actions">
            <button type="button" :disabled="!course.usable" @click="start('use', course)">
              ใช้สิทธิ์</button
            ><button type="button" @click="start('history', course)">ดูประวัติการใช้</button
            ><button type="button" @click="start('transfer', course)">แชร์คอร์ส</button>
          </div>
          <small class="course-manager-expiry">หมดอายุ {{ course.expiry }}</small>
          <button
            v-if="transferReceiptId"
            class="course-manager-latest-transfer"
            type="button"
            @click="openLatestTransfer(course)"
          >
            ← ดูรายการแชร์ล่าสุด
          </button>
        </section></template
      >
    </main>
  </div>
</template>

<style src="../../staff/styles/staff.css"></style>
<style scoped src="../styles/course-page.css"></style>
<style scoped>
.course-use-main {
  width: min(1080px, calc(100% - 48px));
  margin: auto;
  padding: 20px 0 40px;
  color: #25372e;
}
.course-manager-main {
  width: min(920px, calc(100% - 48px));
  margin: auto;
  padding: 16px 0 36px;
  color: #25372e;
}
.course-confirm-dialog.course-usage-confirm {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: min(560px, 100%);
  min-height: 560px;
  gap: 0;
  padding: 25px 27px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
  box-shadow: none;
}
.course-confirm-dialog.course-usage-confirm h2 {
  margin: 0;
  color: #25372f;
  font-size: 25px;
  line-height: 1.4;
}
.course-confirm-dialog.course-usage-confirm > p {
  margin: 4px 0 22px;
  color: #667a72;
  font-size: 14px;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-details {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 16px 18px 20px;
  border-radius: 10px;
  background: #eef6f0;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-details > strong {
  margin-bottom: 2px;
  padding: 0;
  background: transparent;
  font-size: 17px;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-details p {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  min-height: 52px;
  margin: 0;
  padding: 0 17px;
  border-radius: 12px;
  background: #fff;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-details p span {
  color: #667a72;
  font-size: 14px;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-details p b {
  color: #25372f;
  font-size: 15px;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-actions {
  display: grid;
  grid-template-columns: 230px 1fr;
  gap: 24px;
  margin-top: auto;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-actions button {
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  color: #25372f;
  background: #fff;
  font-size: 15px;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-actions button:first-child {
  border: 1px solid #dde6e1;
  background: #fff;
}
.course-confirm-dialog.course-usage-confirm .course-confirm-actions button:last-child {
  border-color: #5f9155;
  color: #fff;
  background: #5f9155;
}
.course-confirm-dialog.course-usage-confirm.after-transfer {
  width: min(640px, 100%);
  min-height: 0;
  gap: 0;
  padding: 28px;
  border: 0;
  border-radius: 20px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer h2 {
  font-size: 29px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer > p {
  margin: 7px 0 18px;
  font-size: 15px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-after-confirm-details {
  gap: 10px;
  padding: 18px;
  border-radius: 14px;
  color: #25372e;
  background: #eff4ed;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-after-confirm-details > strong {
  margin: 0;
  font-size: 20px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-after-confirm-details > b {
  font-size: 18px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-after-confirm-details > p {
  display: block;
  min-height: 0;
  padding: 0;
  background: transparent;
  font-size: 16px;
  font-weight: 700;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-confirm-actions {
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 18px;
}
.course-confirm-dialog.course-usage-confirm.after-transfer .course-confirm-actions button {
  min-height: 46px;
  border: 0;
  border-radius: 10px;
  background: #eff4ed;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.course-confirm-dialog.course-usage-confirm.after-transfer
  .course-confirm-actions
  button:last-child {
  color: #fff;
  background: #1e807c;
}
@media (max-width: 600px) {
  .course-confirm-dialog.course-usage-confirm {
    min-height: auto;
    padding: 22px 18px;
  }
  .course-confirm-dialog.course-usage-confirm .course-confirm-actions {
    grid-template-columns: 1fr;
    gap: 10px;
    margin-top: 24px;
  }
  .course-confirm-dialog.course-usage-confirm .course-confirm-details p {
    grid-template-columns: 1fr;
    gap: 4px;
    padding-block: 10px;
  }
  .course-confirm-dialog.course-usage-confirm.after-transfer .course-confirm-actions {
    grid-template-columns: 1fr;
  }
}
.course-manager-main > h1 {
  margin: 14px 0;
  font-size: clamp(27px, 2.8vw, 33px);
  line-height: 1.3;
}
.course-manager-main .course-use-patient {
  display: grid;
  gap: 3px;
  padding: 15px 18px;
  border-radius: 13px;
  color: #fff;
  background: #1e807c;
}
.course-manager-main .course-use-patient strong {
  font-size: 20px;
}
.course-manager-main .course-use-patient span {
  font-size: 14px;
}
.course-status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0;
}
.course-status-tabs button,
.course-manager-actions button {
  min-height: 42px;
  padding: 0 17px;
  border: 0;
  border-radius: 10px;
  color: #25372e;
  background: #eff4ed;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.course-status-tabs button.active,
.course-manager-actions button:first-child {
  color: #fff;
  background: #1e807c;
}
.course-manager-card {
  display: grid;
  gap: 9px;
  margin-top: 13px;
  padding: 18px 20px;
  border-radius: 16px;
  background: #fff;
}
.course-manager-card > span {
  justify-self: start;
  color: #1e807c;
  font-size: 13px;
  font-weight: 700;
}
.course-manager-card h2,
.course-manager-card p {
  margin: 0;
}
.course-manager-card h2 {
  font-size: 21px;
}
.course-manager-card > strong {
  color: #1e807c;
  font-size: 28px;
}
.course-manager-card p {
  font-size: 14px;
}
.course-manager-card footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: #56665d;
  font-size: 13px;
}
.course-manager-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.course-manager-actions button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.course-use-main > h1 {
  margin: 18px 0;
  font-size: clamp(30px, 3.2vw, 38px);
  line-height: 1.3;
}
.course-use-back {
  display: inline-block;
  color: #173d47;
  text-decoration: none;
  transition: transform 0.18s ease;
}
.course-use-back:hover {
  transform: translateX(-3px);
}
.course-use-main .course-use-patient {
  display: grid;
  gap: 4px;
  padding: 18px 22px;
  border-radius: 14px;
  color: #fff;
  background: #1e807c;
}
.course-use-main .course-use-patient strong {
  font-size: 24px;
}
.course-use-main .course-use-patient span {
  font-size: 16px;
}
.course-use-empty {
  display: grid;
  gap: 12px;
  margin-top: 24px;
  padding: 32px;
  border-radius: 20px;
  background: #fff;
  text-align: center;
}
.course-use-empty h2,
.course-use-empty p {
  margin: 0;
}
.course-use-empty p {
  color: #56665d;
}
.course-use-empty button {
  justify-self: center;
  min-width: 220px;
  min-height: 52px;
  margin-top: 8px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: #1e807c;
  font: inherit;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}
.course-use-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(290px, 1fr);
  gap: 20px;
  margin-top: 20px;
}
.course-use-form,
.course-use-summary {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 22px;
  border-radius: 18px;
  background: #fff;
}
.course-use-form h2,
.course-use-summary h2 {
  margin: 0;
  font-size: 24px;
}
.course-use-form label {
  display: grid;
  gap: 7px;
  font-size: 16px;
  font-weight: 700;
}
.course-use-form input,
.course-use-form select,
.course-use-form textarea {
  min-height: 46px;
  box-sizing: border-box;
  width: 100%;
  padding: 0 14px;
  border: 1px solid #8f8f8f;
  border-radius: 10px;
  color: #1f2937;
  background: #fff;
  font: inherit;
  font-size: 16px;
  font-weight: 400;
}
.course-use-form textarea {
  min-height: 72px;
  padding-block: 10px;
  resize: vertical;
}
.course-use-summary > b {
  font-size: 20px;
}
.course-use-summary p {
  margin: 0;
  font-size: 16px;
}
.course-use-summary > strong {
  font-size: 24px;
}
.course-progress {
  overflow: hidden;
  height: 12px;
  border-radius: 99px;
  background: #e3e9e5;
}
.course-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1e807c;
}
.course-use-summary small {
  color: #56665d;
}
.course-use-summary button,
.course-use-success button,
.course-use-review footer button {
  min-height: 52px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: #1e807c;
  font: inherit;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}
.course-use-summary button:disabled,
.course-use-review button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.course-use-review,
.course-use-success {
  display: grid;
  gap: 24px;
  width: min(800px, 100%);
  margin: 32px auto;
  padding: 40px;
  border-radius: 24px;
  background: #fff;
}
.course-use-review h1,
.course-use-success h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
}
.course-use-review > div {
  display: grid;
  gap: 14px;
  padding: 24px;
  border-radius: 16px;
  background: #eff4ed;
}
.course-use-review > div b {
  font-size: 22px;
}
.course-use-review > div p {
  margin: 0;
}
.course-use-review > div span {
  margin-top: 6px;
  font-weight: 700;
}
.course-use-review > div em {
  color: #1e807c;
  font-size: 36px;
  font-style: normal;
  font-weight: 700;
}
.course-use-review > div small {
  color: #56665d;
}
.course-use-review footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.course-use-review footer button:first-child {
  color: #25372e;
  background: #eff4ed;
}
.course-use-success h1,
.course-use-success .remaining {
  color: #1e807c;
}
.course-use-success strong {
  font-size: 22px;
}
.course-use-success p {
  margin: 0;
  font-size: 18px;
}
.course-use-success .remaining {
  font-size: 28px;
  font-weight: 700;
}
.course-use-review {
  gap: 14px;
  width: min(600px, calc(100% - 40px));
  margin: 22px auto 36px;
  padding: 24px;
  box-sizing: border-box;
}
.course-use-review h1 {
  font-size: clamp(26px, 2.6vw, 32px);
  line-height: 1.35;
}
.course-use-review-intro {
  margin: -2px 0 0;
  color: #56665d;
  font-size: 14px;
}
.course-use-review .course-use-review-details {
  gap: 8px;
  padding: 16px;
}
.course-use-review-details strong {
  font-size: 18px;
  line-height: 1.45;
}
.course-use-review-details b {
  font-size: 17px;
  line-height: 1.45;
}
.course-use-review-details p {
  color: #25372e;
  font-size: 14px;
  line-height: 1.45;
}
.course-use-review-details em {
  margin-top: 2px;
  color: #25372e;
  font-size: 22px;
  line-height: 1.4;
}
.course-use-review-details .course-progress {
  width: 100%;
  height: 14px;
}
.course-use-review-details small {
  margin-top: 2px;
  font-size: 13px;
}
.course-use-review footer {
  gap: 12px;
}
.course-use-review footer button {
  height: 44px;
  min-height: 44px;
  font-size: 15px;
}
.course-use-success {
  gap: 14px;
  width: min(600px, calc(100% - 40px));
  margin: 22px auto 36px;
  padding: 24px;
  box-sizing: border-box;
}
.course-use-success h1 {
  color: #1e807c;
  font-size: clamp(26px, 2.6vw, 32px);
  line-height: 1.35;
}
.course-use-success > strong {
  color: #25372e;
  font-size: 18px;
  line-height: 1.45;
}
.course-use-success-details {
  display: grid;
  gap: 0;
}
.course-use-success-details p {
  color: #25372e;
  font-size: 14px;
  line-height: 1.45;
}
.course-use-success .remaining {
  margin-top: 2px;
  font-size: 22px;
  line-height: 1.4;
}
.course-use-success .course-progress {
  height: 14px;
}
.course-use-success button {
  width: 100%;
  height: 44px;
  min-height: 44px;
  font-size: 15px;
}
.course-confirm-backdrop {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: #173f4459;
}
.course-confirm-dialog {
  display: grid;
  gap: 14px;
  width: min(420px, 100%);
  padding: 28px;
  border: 1px solid #dce8e4;
  border-radius: 18px;
  color: #25372e;
  background: #fff;
  box-shadow: 0 18px 45px #173f4438;
}
.course-confirm-dialog h2,
.course-confirm-dialog p {
  margin: 0;
}
.course-confirm-dialog h2 {
  font-size: 25px;
}
.course-confirm-dialog p {
  color: #56665d;
}
.course-confirm-dialog strong {
  padding: 12px 14px;
  border-radius: 10px;
  background: #eff4ed;
}
.course-confirm-dialog > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.course-confirm-dialog button {
  min-height: 46px;
  border: 1px solid #b8ccca;
  border-radius: 10px;
  color: #315f5b;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.course-confirm-dialog button:last-child {
  border-color: #1e807c;
  color: #fff;
  background: #1e807c;
}
.course-confirm-dialog {
  box-sizing: border-box;
  width: min(560px, 100%);
  gap: 16px;
  padding: 28px;
  border: 0;
  border-radius: 20px;
}
.course-confirm-dialog h2 {
  font-size: clamp(22px, 3vw, 28px);
  line-height: 1.4;
}
.course-confirm-dialog > p {
  font-size: 15px;
}
.course-confirm-dialog .course-confirm-details {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 18px;
  border-radius: 14px;
  background: #eff4ed;
}
.course-confirm-details strong {
  padding: 0;
  background: transparent;
  font-size: 17px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.course-confirm-details b {
  font-size: 15px;
  line-height: 1.5;
}
.course-confirm-dialog .course-confirm-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.course-confirm-actions button {
  min-height: 44px;
  border: 0;
  border-radius: 10px;
  font-size: 15px;
}
.course-confirm-actions button:first-child {
  border: 0;
  color: #25372e;
  background: #eff4ed;
}
.course-transfer-main {
  width: min(960px, calc(100% - 48px));
  margin: auto;
  padding: 18px 0 44px;
  color: #25372e;
}
.course-transfer-owner-summary {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 16px;
  min-height: 104px;
  box-sizing: border-box;
  margin-bottom: 18px;
  padding: 18px 20px 18px 15px;
  border-left: 5px solid #1f8883;
  border-radius: 12px;
  background: #fff;
}
.course-transfer-owner-summary > i {
  display: grid;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #176c68;
  background: #e7f4f1;
  place-items: center;
  font-style: normal;
  font-weight: 700;
}
.course-transfer-owner-summary > div {
  display: grid;
  gap: 5px;
}
.course-transfer-owner-summary strong {
  font-size: 19px;
}
.course-transfer-owner-summary span {
  color: #667a72;
  font-size: 14px;
}
.course-transfer-owner-summary > b {
  padding: 8px 20px;
  border-radius: 999px;
  color: #34724c;
  background: #eef6f0;
  font-size: 13px;
}
.course-transfer-success-details {
  display: grid;
  gap: 8px;
  width: 100%;
  box-sizing: border-box;
  padding: 14px;
  border-radius: 10px;
  background: #eef6f0;
}
.course-transfer-success-details p {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  min-height: 48px;
  margin: 0;
  padding: 0 16px;
  background: #fff;
}
.course-transfer-success-details span {
  color: #667a72;
  font-size: 14px;
}
.course-transfer-success-details b {
  color: #25372f;
  font-size: 15px;
}
.course-transfer-back {
  padding: 0;
  border: 0;
  color: #173d47;
  background: transparent;
  font: inherit;
  cursor: pointer;
  transition: transform 0.18s ease;
}
.course-transfer-back:hover {
  transform: translateX(-3px);
}
.course-transfer-main > h1 {
  margin: 14px 0 4px;
  font-size: clamp(28px, 3vw, 36px);
}
.course-transfer-intro {
  margin: 0;
  color: #56665d;
  font-size: 15px;
}
.course-transfer-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin: 22px 0;
  padding: 0;
  list-style: none;
}
.course-transfer-steps li {
  padding-bottom: 8px;
  border-bottom: 2px solid #dce3dc;
  color: #56665d;
  font-size: 15px;
}
.course-transfer-steps li.active {
  border-color: #1e807c;
  color: #25372e;
  font-weight: 700;
}
.course-transfer-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.course-transfer-source,
.course-transfer-recipient,
.course-transfer-review,
.course-transfer-success {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 22px;
  border-radius: 16px;
  background: #fff;
}
.course-transfer-source h2,
.course-transfer-recipient h2,
.course-transfer-review h2,
.course-transfer-success h2 {
  margin: 0;
  font-size: 22px;
}
.course-transfer-person,
.course-transfer-selected {
  display: flex;
  align-items: center;
  gap: 12px;
}
.course-transfer-person i,
.course-transfer-selected i,
.course-transfer-results i {
  display: grid;
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: #25372e;
  background: #eff4ed;
  place-items: center;
  font-style: normal;
  font-size: 18px;
  font-weight: 700;
}
.course-transfer-person div,
.course-transfer-selected div {
  display: grid;
  gap: 2px;
}
.course-transfer-person strong,
.course-transfer-selected strong {
  font-size: 16px;
}
.course-transfer-person span,
.course-transfer-selected span {
  color: #56665d;
  font-size: 13px;
}
.course-transfer-source h3 {
  margin: 0;
  font-size: 19px;
}
.course-transfer-balance {
  display: grid;
  gap: 3px;
  padding: 14px;
  border-radius: 12px;
  background: #eff4ed;
}
.course-transfer-balance strong {
  font-size: 22px;
}
.course-transfer-balance span,
.course-transfer-dates,
.course-transfer-help {
  color: #56665d;
  font-size: 13px;
}
.course-transfer-dates {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 0;
}
.course-transfer-source label,
.course-transfer-recipient label {
  display: grid;
  gap: 7px;
  font-size: 14px;
  font-weight: 700;
}
.course-transfer-stepper {
  display: flex;
  align-items: center;
  gap: 9px;
}
.course-transfer-stepper button {
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: 10px;
  background: #eff4ed;
  font: inherit;
  font-size: 20px;
  cursor: pointer;
}
.course-transfer-stepper input,
.course-transfer-recipient input,
.course-transfer-recipient select,
.course-transfer-recipient textarea {
  box-sizing: border-box;
  min-height: 42px;
  border: 1px solid #9eaaa4;
  border-radius: 10px;
  color: #25372e;
  background: #fff;
  font: inherit;
}
.course-transfer-stepper input {
  width: 70px;
  padding: 0 8px;
  text-align: center;
}
.course-transfer-stepper em {
  color: #56665d;
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
}
.course-transfer-recipient input,
.course-transfer-recipient select,
.course-transfer-recipient textarea {
  width: 100%;
  padding: 0 12px;
}
.course-transfer-recipient textarea {
  min-height: 76px;
  padding-block: 10px;
  resize: vertical;
}
.course-share-allocation {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  background: #f0f6f4;
}
.course-share-allocation > strong,
.course-share-allocation > small {
  grid-column: 1 / -1;
}
.course-share-allocation label {
  font-size: 13px;
}
.course-share-allocation input {
  width: 100%;
}
.course-share-allocation small {
  color: #397461;
}
.course-share-allocation small.invalid {
  color: #b42318;
}
.course-transfer-results {
  display: grid;
  overflow: hidden;
  border: 1px solid #dce3dc;
  border-radius: 12px;
}
.course-transfer-results button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 0;
  border-bottom: 1px solid #e7ede9;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.course-transfer-results span {
  display: grid;
  gap: 2px;
}
.course-transfer-results small {
  color: #56665d;
}
.course-transfer-results p {
  margin: 0;
  padding: 16px;
  color: #56665d;
  text-align: center;
}
.course-transfer-selected {
  padding: 12px;
  border-radius: 12px;
  background: #eff4ed;
}
.course-transfer-selected button {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  color: #56665d;
  background: #fff;
  font-size: 20px;
  cursor: pointer;
}
.course-transfer-help {
  margin: 0;
}
.course-transfer-footer,
.course-transfer-review footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
}
.course-transfer-footer button,
.course-transfer-review footer button,
.course-transfer-success > button {
  min-height: 44px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  color: #25372e;
  background: #eff4ed;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.course-transfer-footer button:last-child,
.course-transfer-review footer button:last-child,
.course-transfer-success > button {
  color: #fff;
  background: #1e807c;
}
.course-transfer-footer button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.course-transfer-review,
.course-transfer-success {
  width: min(620px, 100%);
  box-sizing: border-box;
  margin: 0 auto;
}
.course-transfer-review > p,
.course-transfer-success > p {
  margin: 0;
  color: #56665d;
  font-size: 14px;
}
.course-transfer-review-people {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
}
.course-transfer-review-people article {
  display: grid;
  gap: 3px;
  padding: 14px;
  border: 1px solid #dce3dc;
  border-radius: 12px;
}
.course-transfer-review-people article span,
.course-transfer-review-people article small {
  color: #56665d;
  font-size: 13px;
}
.course-transfer-review-people article strong {
  font-size: 16px;
}
.course-transfer-review-people > b {
  color: #1e807c;
  font-size: 22px;
}
.course-transfer-review-summary {
  display: grid;
  gap: 5px;
  padding: 16px;
  border-radius: 12px;
  background: #eff4ed;
}
.course-transfer-review-summary strong {
  font-size: 18px;
}
.course-transfer-review-summary span {
  color: #56665d;
  font-size: 14px;
}
.course-transfer-success {
  justify-items: center;
  text-align: center;
}
.course-transfer-success-icon {
  display: grid;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  color: #1e807c;
  background: #eff4ed;
  place-items: center;
  font-size: 30px;
  font-weight: 700;
}
.course-transfer-success .course-transfer-review-people {
  width: 100%;
  text-align: left;
}
.course-transfer-success .course-transfer-review-summary {
  width: 100%;
  box-sizing: border-box;
  text-align: left;
}
.course-transfer-success > button {
  width: 100%;
}
.course-cut-preview {
  padding-top: 0;
}
.course-cut-back-link {
  padding: 0;
  border: 0;
  color: #66766f;
  background: transparent;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.course-cut-document {
  box-sizing: border-box;
  width: min(1020px, 100%);
  min-height: 920px;
  margin: 76px auto 0;
  padding: 26px 33px;
  border: 1px solid #d8e1de;
  border-radius: 2px;
  color: #25372f;
  background: #fff;
}
.course-cut-document > header {
  padding-bottom: 10px;
  border-bottom: 2px solid #25372f;
  text-align: center;
}
.course-cut-document > header h1 {
  margin: 0 0 10px;
  font-size: 22px;
}
.course-cut-document > header p {
  margin: 0;
  color: #52635c;
  font-size: 14px;
}
.course-cut-issued {
  margin: 12px 0 8px;
  color: #66766f;
  font-size: 14px;
  text-align: right;
}
.course-cut-summary {
  display: grid;
  grid-template-columns: 1.3fr 0.7fr 0.8fr 0.8fr;
  gap: 14px 20px;
  margin-top: 8px;
  font-size: 14px;
}
.course-cut-summary p {
  margin: 0;
}
.course-cut-summary strong:first-of-type {
  grid-column: 1 / 4;
}
.course-cut-table {
  margin-top: 18px;
  font-size: 12px;
}
.course-cut-table > div {
  display: grid;
  grid-template-columns: 1.1fr 1.65fr 1fr 80px;
  align-items: center;
  min-height: 52px;
  border: 1px solid #25372f;
  border-bottom: 0;
}
.course-cut-table > div:last-child {
  border-bottom: 1px solid #25372f;
}
.course-cut-table > div > * {
  padding: 0 16px;
}
.course-cut-table-head {
  min-height: 48px !important;
  background: #e8f5f0;
}
.course-cut-total {
  grid-template-columns: 1fr 80px !important;
}
.course-cut-total b:first-child {
  text-align: right;
}
.course-cut-actions {
  display: flex;
  justify-content: flex-end;
  gap: 24px;
  width: min(1020px, 100%);
  margin: 34px auto 0;
}
.course-cut-actions button {
  min-width: 170px;
  min-height: 48px;
  border: 1px solid #cbd8d4;
  border-radius: 8px;
  color: #25372f;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.course-cut-actions button:last-child {
  min-width: 202px;
  border-color: #5f9855;
  color: #fff;
  background: #5f9855;
}
.course-print-modal-backdrop {
  position: fixed;
  z-index: 120;
  inset: 0;
  display: grid;
  padding: 20px;
  background: rgb(25 47 42 / 52%);
  place-items: center;
}
.course-print-modal {
  display: grid;
  align-content: start;
  box-sizing: border-box;
  width: min(620px, 100%);
  gap: 18px;
  padding: 32px 36px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 54px rgb(0 0 0 / 18%);
}
.course-print-modal h2 {
  margin: 0;
  color: #26352f;
  font-size: 28px;
}
.course-print-modal p {
  margin: 0;
  color: #51635c;
  font-size: 16px;
}
.course-print-modal footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.course-print-modal button {
  min-height: 48px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  color: #26352f;
  background: #eef2f0;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.course-print-modal button:last-child {
  padding-inline: 22px;
  color: #fff;
  background: #1e807c;
}
@media (max-width: 760px) {
  .course-cut-document {
    min-height: auto;
    margin-top: 28px;
    padding: 22px 16px;
    overflow-x: auto;
  }
  .course-cut-summary {
    grid-template-columns: 1fr;
  }
  .course-cut-summary strong:first-of-type {
    grid-column: auto;
  }
  .course-cut-table {
    min-width: 680px;
  }
  .course-cut-actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .course-cut-actions button {
    width: 100%;
  }
  .course-print-modal {
    padding: 26px 22px;
  }
  .course-print-modal footer {
    align-items: stretch;
    flex-direction: column;
  }
  .course-print-modal button {
    width: 100%;
  }
}
.course-history-main {
  width: min(960px, calc(100% - 48px));
  margin: auto;
  padding: 20px 0 48px;
  color: #25372e;
}
.course-history-back {
  padding: 0;
  border: 0;
  color: #173d47;
  background: transparent;
  font: inherit;
  cursor: pointer;
  transition: transform 0.18s ease;
}
.course-history-back:hover {
  transform: translateX(-3px);
}
.course-history-main > h1 {
  margin: 18px 0;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.3;
}
.course-history-patient {
  display: grid;
  gap: 4px;
  padding: 18px 22px;
  border-radius: 14px;
  color: #fff;
  background: #1e807c;
}
.course-history-patient strong {
  font-size: 22px;
}
.course-history-patient span {
  font-size: 15px;
}
.course-history-course {
  margin: 20px 0 14px;
  font-size: 21px;
}
.course-history-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.course-history-stats article {
  display: grid;
  gap: 6px;
  min-height: 84px;
  padding: 14px;
  border: 1px solid #e2eae5;
  border-radius: 12px;
  background: #fff;
  text-align: center;
}
.course-history-stats span {
  color: #56665d;
  font-size: 14px;
}
.course-history-stats strong {
  color: #25372e;
  font-size: 20px;
}
.course-history-stats article:last-child {
  border-color: #cce3df;
  background: #eaf5f3;
}
.course-history-stats article:last-child strong {
  color: #1e807c;
}
.course-history-entries {
  display: grid;
  gap: 12px;
  margin-top: 24px;
}
.course-history-entries h2 {
  margin: 0 0 2px;
  font-size: 23px;
}
.course-history-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border: 1px solid #e2eae5;
  border-radius: 14px;
  background: #fff;
}
.course-history-entry > div {
  display: grid;
  gap: 5px;
}
.course-history-entry strong {
  font-size: 16px;
}
.course-history-entry b {
  font-size: 18px;
}
.course-history-entry span {
  color: #56665d;
  font-size: 14px;
}
.course-history-entry aside {
  display: grid;
  gap: 6px;
  min-width: 136px;
  color: #1e807c;
  text-align: right;
}
.course-history-entry aside b {
  font-size: 16px;
}
.course-history-entry aside span {
  color: #1e807c;
}
.course-history-empty {
  margin: 0;
  padding: 28px;
  border-radius: 14px;
  color: #56665d;
  background: #fff;
  text-align: center;
}
.course-history-selector {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  font-size: 14px;
  font-weight: 700;
}
.course-history-selector select {
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #dce3dc;
  border-radius: 10px;
  color: #25372e;
  background: #fff;
  font: inherit;
}
.course-history-patient + .course-history-empty {
  margin-top: 20px;
}
.course-transfer-recipient textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 68px;
  padding: 10px 12px;
  border: 1px solid #9eaaa4;
  border-radius: 10px;
  color: #25372e;
  background: #fff;
  font: inherit;
  resize: vertical;
}
.course-transfer-review,
.course-transfer-success {
  width: min(960px, 100%);
}
.course-transfer-course-name {
  display: grid;
  gap: 2px;
  margin: 2px 0;
  font-size: 20px;
}
.course-transfer-course-name small {
  color: #56665d;
  font-size: 14px;
  font-weight: 500;
}
.course-transfer-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.course-transfer-metrics article {
  display: grid;
  gap: 3px;
  padding: 15px;
  border-radius: 12px;
  background: #eff4ed;
  text-align: center;
}
.course-transfer-metrics span,
.course-transfer-metrics small {
  color: #25372e;
  font-size: 13px;
}
.course-transfer-metrics strong {
  color: #25372e;
  font-size: 30px;
  line-height: 1.1;
}
.course-transfer-metrics article:nth-child(2) strong {
  color: #1e807c;
}
.course-transfer-detail-list {
  display: grid;
  gap: 7px;
}
.course-transfer-detail-list p,
.course-transfer-review-note {
  margin: 0;
  color: #56665d;
  font-size: 14px;
}
.course-transfer-success .course-transfer-metrics {
  width: 100%;
}
.course-transfer-results {
  max-height: 260px;
  overflow-y: auto;
  overflow-x: hidden;
  align-content: start;
  scrollbar-color: #1e807c #eff4ed;
  scrollbar-width: thin;
}
.course-transfer-results::-webkit-scrollbar {
  width: 8px;
}
.course-transfer-results::-webkit-scrollbar-track {
  background: #eff4ed;
}
.course-transfer-results::-webkit-scrollbar-thumb {
  border-radius: 8px;
  background: #1e807c;
}
.course-transfer-remaining-hint {
  display: grid;
  gap: 3px;
  margin-top: -7px;
}
.course-transfer-remaining-hint small {
  color: #56665d;
  font-size: 12px;
}
.course-transfer-remaining-hint strong {
  color: #25372e;
  font-size: 14px;
}
.course-transfer-success .course-transfer-detail-list {
  width: 100%;
  text-align: left;
}
.course-transfer-success-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}
.course-transfer-success-actions button {
  min-height: 44px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  color: #25372e;
  background: #eff4ed;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.course-transfer-success-actions button:last-child {
  color: #fff;
  background: #1e807c;
}
@media (max-width: 480px) {
  .course-confirm-dialog {
    padding: 22px;
  }
  .course-confirm-dialog .course-confirm-actions {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 760px) {
  .course-use-main,
  .course-manager-main,
  .course-history-main,
  .course-transfer-main {
    width: min(100% - 32px, 1200px);
  }
  .course-use-columns,
  .course-transfer-columns {
    grid-template-columns: 1fr;
  }
  .course-transfer-owner-summary {
    grid-template-columns: 44px 1fr;
    padding: 16px 14px;
  }
  .course-transfer-owner-summary > b {
    grid-column: 2;
    justify-self: start;
  }
  .course-transfer-success-details p {
    grid-template-columns: 1fr;
    gap: 4px;
    padding-block: 10px;
  }
  .course-use-form,
  .course-use-summary,
  .course-manager-card {
    padding: 20px;
  }
  .course-manager-card footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .course-use-review,
  .course-use-success {
    box-sizing: border-box;
    padding: 24px;
  }
  .course-use-review footer {
    grid-template-columns: 1fr;
  }
  .course-history-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .course-history-entry {
    align-items: flex-start;
    flex-direction: column;
  }
  .course-history-entry aside {
    text-align: left;
  }
  .course-transfer-steps {
    gap: 8px;
  }
  .course-transfer-steps li {
    font-size: 13px;
  }
  .course-transfer-dates {
    align-items: flex-start;
    flex-direction: column;
  }
  .course-transfer-review-people {
    grid-template-columns: 1fr;
  }
  .course-transfer-review-people > b {
    justify-self: center;
    transform: rotate(90deg);
  }
  .course-transfer-footer,
  .course-transfer-review footer {
    display: grid;
    grid-template-columns: 1fr;
  }
  .course-transfer-footer button,
  .course-transfer-review footer button {
    width: 100%;
  }
}
@media (max-width: 560px) {
  .course-transfer-metrics {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .course-transfer-success-actions {
    flex-direction: column;
  }
  .course-transfer-success-actions button {
    width: 100%;
  }
}
</style>
<style scoped>
.course-manager-main {
  width: min(1200px, calc(100% - 48px));
  padding: 15px 0 80px;
}
.course-manager-main > h1 {
  margin: 12px 0 2px;
  font-size: 30px;
}
.course-manager-intro {
  margin: 0 0 20px;
  color: #667a72;
  font-size: 15px;
}
.course-manager-main .course-use-patient {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 16px;
  min-height: 104px;
  box-sizing: border-box;
  padding: 20px 21px 20px 15px;
  border-left: 5px solid #1f8883;
  border-radius: 12px;
  color: #25372f;
  background: #fff;
}
.course-manager-main .course-use-patient > i {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #176c68;
  background: #e7f4f1;
  font-size: 14px;
  font-style: normal;
  font-weight: 700;
}
.course-manager-main .course-use-patient > div {
  display: grid;
  gap: 7px;
}
.course-manager-main .course-use-patient strong {
  font-size: 19px;
}
.course-manager-main .course-use-patient span {
  color: #667a72;
  font-size: 14px;
}
.course-manager-main .course-use-patient span b {
  margin-inline: 12px 4px;
  font-weight: 400;
}
.course-manager-main .course-use-patient > em {
  padding: 8px 20px;
  border-radius: 999px;
  color: #34724c;
  background: #eef6f0;
  font-size: 13px;
  font-style: normal;
  font-weight: 700;
}
.course-status-tabs {
  display: grid;
  grid-template-columns: 225px 225px;
  gap: 0;
  width: fit-content;
  margin: 14px 0;
  overflow: hidden;
  border-radius: 8px;
}
.course-status-tabs button {
  min-height: 44px;
  border-radius: 0;
  background: #fff;
  font-weight: 500;
}
.course-status-tabs button.active {
  color: #25372e;
  background: #eef6f0;
  font-weight: 700;
}
.course-manager-card {
  position: relative;
  gap: 5px;
  align-content: start;
  min-height: 420px;
  box-sizing: border-box;
  margin-top: 0;
  padding: 20px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
}
.course-manager-card > span {
  color: #267557;
}
.course-manager-card h2 {
  margin-top: 4px;
  font-size: 22px;
}
.course-manager-card > p {
  color: #667a72;
}
.course-manager-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 250px));
  gap: 18px;
  margin-top: 17px;
}
.course-manager-metrics.transferred {
  grid-template-columns: repeat(4, minmax(0, 190px));
}
.course-manager-latest-transfer {
  justify-self: start;
  margin-top: 10px;
  padding: 0;
  border: 0;
  color: #25372f;
  background: transparent;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.course-manager-metrics article {
  display: grid;
  gap: 7px;
  min-height: 76px;
  box-sizing: border-box;
  padding: 10px 14px;
  border-radius: 9px;
  background: #f2f6f3;
}
.course-manager-metrics small {
  color: #667a72;
  font-size: 13px;
}
.course-manager-metrics strong {
  font-size: 20px;
}
.course-manager-card > .course-progress {
  height: 8px;
  margin-top: 18px;
}
.course-manager-card > .course-progress i {
  background: #34724c;
}
.course-manager-used {
  margin-top: 5px !important;
  margin-bottom: 72px !important;
  color: #667a72 !important;
  font-size: 13px !important;
}
.course-manager-used strong {
  color: #34724c;
  font-size: 14px;
}
.course-manager-actions {
  position: absolute;
  left: 20px;
  bottom: 40px;
  gap: 12px;
}
.course-manager-actions button {
  min-width: 184px;
  min-height: 44px;
  border: 1px solid #d8e1de;
  background: #fff;
  font-weight: 600;
}
.course-manager-actions button:first-child {
  border-color: #76a36d;
  background: #76a36d;
}
.course-manager-expiry {
  position: absolute;
  right: 20px;
  bottom: 20px;
  color: #667a72;
}
.course-history-main {
  position: relative;
}
.course-history-print {
  position: absolute;
  top: 74px;
  right: 0;
  min-height: 44px;
  padding: 0 20px;
  border: 1px solid #1f8883;
  border-radius: 10px;
  color: #1f8883;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.course-print-dialog {
  text-align: center;
}
.course-print-dialog > button {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: #1f8883;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.course-print-result-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  margin: 0 auto 18px;
  border-radius: 50%;
  color: #fff;
  font-size: 34px;
  font-weight: 700;
}
.course-print-result-icon.success {
  background: #76a36d;
}
.course-print-result-icon.error {
  background: #c65d58;
}
.course-use-review {
  display: block;
  width: min(1200px, calc(100% - 48px));
  margin: 0 auto;
  padding: 16px 0 70px;
  border-radius: 0;
  background: transparent;
  color: #25372f;
}
.course-review-back {
  margin: 0 0 14px;
  padding: 8px 0;
  border: 0;
  color: #173d47;
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.course-use-review > h1 {
  margin: 0 0 3px;
  font-size: 30px;
}
.course-use-review-intro {
  margin: 0 0 20px;
  color: #667a72;
  font-size: 15px;
}
.course-review-patient {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 16px;
  min-height: 104px;
  box-sizing: border-box;
  padding: 18px 20px 18px 15px;
  border-left: 5px solid #1f8883;
  border-radius: 12px;
  background: #fff;
}
.course-review-patient > i {
  display: grid;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #176c68;
  background: #e7f4f1;
  place-items: center;
  font-style: normal;
  font-weight: 700;
}
.course-review-patient > div {
  display: grid;
  gap: 5px;
}
.course-review-patient strong {
  font-size: 19px;
}
.course-review-patient span {
  color: #667a72;
  font-size: 14px;
}
.course-review-patient > b {
  padding: 8px 20px;
  border-radius: 999px;
  color: #34724c;
  background: #eef6f0;
  font-size: 13px;
}
.course-review-columns {
  display: grid !important;
  grid-template-columns: minmax(0, 740px) minmax(320px, 430px);
  gap: 30px !important;
  margin-top: 18px;
  padding: 0 !important;
  background: transparent !important;
}
.course-review-details,
.course-review-summary {
  min-height: 440px;
  box-sizing: border-box;
  padding: 20px 22px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
}
.course-review-details h2,
.course-review-summary h2 {
  margin: 0 0 14px;
  font-size: 20px;
}
.course-review-details dl {
  display: grid;
  gap: 0;
  margin: 0;
}
.course-review-details dl > div {
  display: grid;
  grid-template-columns: 172px 1fr;
  min-height: 52px;
  align-items: center;
  padding: 0 16px;
}
.course-review-details dt {
  color: #667a72;
  font-size: 14px;
}
.course-review-details dd {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.course-review-summary {
  display: flex;
  flex-direction: column;
}
.course-review-summary > div {
  display: grid;
  gap: 9px;
  padding: 18px 20px;
  border-radius: 12px;
  background: #eef6f0;
}
.course-review-summary span,
.course-review-summary small {
  color: #667a72;
  font-size: 13px;
}
.course-review-summary strong {
  color: #34724c;
  font-size: 28px;
}
.course-review-summary footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: auto;
}
.course-review-summary footer button {
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  color: #25372f;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.course-review-summary footer button:last-child {
  border-color: #5f9155;
  color: #fff;
  background: #5f9155;
}
@media (max-width: 1000px) {
  .course-use-review {
    width: min(100% - 32px, 1200px);
  }
  .course-review-columns {
    grid-template-columns: 1fr;
    gap: 18px !important;
  }
  .course-review-details,
  .course-review-summary {
    min-height: auto;
  }
  .course-review-summary footer {
    margin-top: 28px;
  }
}
@media (max-width: 760px) {
  .course-manager-main {
    width: min(100% - 28px, 1200px);
  }
  .course-manager-main .course-use-patient {
    grid-template-columns: 44px 1fr;
    padding: 16px 14px;
  }
  .course-manager-main .course-use-patient > em {
    grid-column: 2;
    justify-self: start;
  }
  .course-status-tabs {
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }
  .course-manager-card {
    min-height: auto;
    padding-bottom: 20px;
  }
  .course-manager-metrics {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .course-manager-metrics.transferred {
    grid-template-columns: 1fr;
  }
  .course-manager-actions {
    position: static;
    display: grid;
    margin-top: 22px;
  }
  .course-manager-actions button {
    width: 100%;
    min-width: 0;
  }
  .course-manager-expiry {
    position: static;
    justify-self: end;
    margin-top: 8px;
  }
  .course-history-print {
    position: static;
    width: 100%;
    margin: 0 0 16px;
  }
  .course-use-review {
    width: min(100% - 28px, 1200px);
  }
  .course-review-patient {
    grid-template-columns: 44px 1fr;
  }
  .course-review-patient > b {
    grid-column: 2;
    justify-self: start;
  }
  .course-review-columns {
    grid-template-columns: 1fr;
  }
  .course-review-details,
  .course-review-summary {
    min-height: auto;
  }
  .course-review-details dl > div {
    grid-template-columns: 1fr;
    gap: 4px;
    padding-block: 10px;
  }
  .course-review-summary footer {
    margin-top: 24px;
  }
}
.course-history-main {
  position: relative;
  width: min(1200px, calc(100% - 48px));
  padding: 28px 0 80px;
}
.course-history-back {
  margin-top: 0;
  color: #66766f;
  font-size: 12px;
}
.course-history-main > h1 {
  margin: 14px 0 1px;
  font-size: 30px;
}
.course-history-intro {
  margin: 0 0 20px;
  color: #70817a;
  font-size: 14px;
}
.course-history-patient {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: 16px;
  min-height: 104px;
  box-sizing: border-box;
  padding: 18px 20px 18px 15px;
  border-left: 5px solid #1f8883;
  border-radius: 12px;
  color: #25372f;
  background: #fff;
}
.course-history-patient > i {
  display: grid;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #176c68;
  background: #e7f4f1;
  place-items: center;
  font-style: normal;
  font-weight: 700;
}
.course-history-patient > div {
  display: grid;
  gap: 5px;
}
.course-history-patient strong {
  font-size: 19px;
}
.course-history-patient span {
  color: #667a72;
  font-size: 14px;
}
.course-history-patient > em {
  padding: 8px 20px;
  border-radius: 999px;
  color: #34724c;
  background: #eef6f0;
  font-size: 13px;
  font-style: normal;
  font-weight: 700;
}
.course-history-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
  min-height: 126px;
  margin-top: 18px;
  padding: 16px 18px;
  border: 1px solid #d8e1de;
  border-radius: 10px;
  background: #fff;
}
.course-history-summary h2 {
  margin: 0 0 5px;
  font-size: 20px;
}
.course-history-summary p,
.course-history-summary span {
  margin: 0;
  color: #66766f;
  font-size: 13px;
}
.course-history-summary > div:last-child {
  display: grid;
  gap: 6px;
  text-align: right;
}
.course-history-summary strong {
  color: #2e7548;
  font-size: 22px;
}
.course-history-print {
  position: static;
  min-width: 212px;
  min-height: 46px;
  border-color: #5f9855;
  border-radius: 8px;
  color: #fff;
  background: #5f9855;
}
.course-history-entries {
  gap: 12px;
  margin-top: 22px;
}
.course-history-entries-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 4px;
}
.course-history-entries h2 {
  margin: 0;
  font-size: 16px;
}
.course-history-entry {
  display: grid;
  grid-template-columns: 140px 1fr 170px;
  align-items: start;
  min-height: 96px;
  box-sizing: border-box;
  padding: 16px 17px;
  border-color: #d8e1de;
  border-radius: 10px;
}
.course-history-entry > strong {
  font-size: 14px;
}
.course-history-entry > div {
  gap: 8px;
}
.course-history-entry b {
  font-size: 14px;
}
.course-history-entry span {
  color: #66766f;
  font-size: 13px;
}
.course-history-entry aside {
  gap: 8px;
  color: #2e7548;
  text-align: left;
}
.course-history-entry aside b {
  color: #2e7548;
  font-size: 14px;
}
.course-history-entry aside span {
  color: #66766f;
  font-size: 12px;
}
@media (max-width: 760px) {
  .course-history-main {
    width: min(100% - 28px, 1200px);
    padding-top: 22px;
  }
  .course-history-patient {
    grid-template-columns: 44px 1fr;
    padding: 16px 14px;
  }
  .course-history-patient > em {
    grid-column: 2;
    justify-self: start;
  }
  .course-history-summary {
    flex-direction: column;
    gap: 18px;
  }
  .course-history-summary > div:last-child {
    text-align: left;
  }
  .course-history-entries-header {
    align-items: stretch;
    flex-direction: column;
  }
  .course-history-print {
    width: 100%;
    margin: 0;
  }
  .course-history-entries {
    margin-top: 18px;
  }
  .course-history-entry {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .course-history-entry aside {
    text-align: left;
  }
}
.course-use-main .course-use-success {
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
  width: min(660px, 100%);
  min-height: 520px;
  gap: 0;
  margin: 26px auto 56px;
  padding: 35px 27px 43px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
}
.course-use-success-icon {
  position: relative;
  width: 68px;
  height: 68px;
}
.course-use-success-icon img {
  display: block;
  width: 68px;
  height: 68px;
}
.course-use-success-icon span {
  position: absolute;
  inset: 0;
  display: grid;
  color: #17663a;
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
  place-items: center;
}
.course-use-main .course-use-success > h1 {
  margin: 12px 0 4px;
  color: #25372f;
  font-size: 27px;
  line-height: 1.4;
}
.course-use-main .course-use-success > p {
  margin: 0;
  color: #667a72;
  font-size: 15px;
  line-height: 1.4;
  text-align: center;
}
.course-use-main .course-use-success-details {
  display: grid;
  width: 100%;
  gap: 14px;
  box-sizing: border-box;
  margin-top: 21px;
  padding: 18px;
  border-radius: 10px;
  background: #eef6f0;
}
.course-use-main .course-use-success-details p {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  box-sizing: border-box;
  min-height: 52px;
  margin: 0;
  padding: 0 17px;
  border-radius: 12px;
  color: #25372f;
  background: #fff;
  font-size: 15px;
}
.course-use-main .course-use-success-details span {
  color: #667a72;
  font-size: 14px;
}
.course-use-main .course-use-success-details strong {
  padding: 0;
  background: transparent;
  font-size: 15px;
}
.course-use-main .course-use-success footer {
  display: grid;
  grid-template-columns: 260px 300px;
  gap: 44px;
  width: 100%;
  margin-top: 40px;
}
.course-use-main .course-use-success footer button {
  width: 100%;
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  color: #25372f;
  background: #fff;
  font-size: 15px;
}
.course-use-main .course-use-success footer button:last-child {
  border-color: #5f9155;
  color: #fff;
  background: #5f9155;
}
@media (max-width: 700px) {
  .course-use-main .course-use-success {
    min-height: auto;
    margin-top: 18px;
    padding: 28px 18px;
  }
  .course-use-main .course-use-success-details p {
    grid-template-columns: 1fr;
    gap: 4px;
    padding-block: 10px;
  }
  .course-use-main .course-use-success footer {
    grid-template-columns: 1fr;
    gap: 10px;
    margin-top: 28px;
  }
}

/* Figma 140:3632 — share course form */
.course-transfer-main {
  width: min(1200px, calc(100% - 48px));
  padding: 15px 0 142px;
}
.course-transfer-back {
  color: #667a72;
  font-size: 13px;
}
.course-transfer-main > h1 {
  margin: 14px 0 2px;
  font-size: 29px;
  line-height: 1.4;
}
.course-transfer-intro {
  color: #667a72;
  font-size: 15px;
}
.course-transfer-steps {
  position: relative;
  grid-template-columns: repeat(3, 1fr);
  width: min(720px, 100%);
  margin: 25px auto 27px;
  gap: 0;
}
.course-transfer-steps li {
  position: relative;
  min-height: 56px;
  box-sizing: border-box;
  padding: 31px 10px 0;
  border: 0;
  color: #52645b;
  font-size: 16px;
  text-align: center;
}
.course-transfer-steps li > span {
  position: absolute;
  z-index: 2;
  top: 3px;
  left: 50%;
  display: grid;
  width: 22px;
  height: 22px;
  border: 1px solid #b8c9c4;
  border-radius: 50%;
  color: #52645b;
  background: #fff;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  transform: translateX(-50%);
}
.course-transfer-steps li::after {
  content: '';
  position: absolute;
  top: 14px;
  left: calc(50% + 33px);
  width: calc(100% - 66px);
  height: 2px;
  background: #cbd5cf;
}
.course-transfer-steps li:last-child::after {
  display: none;
}
.course-transfer-steps li.active {
  color: #25372e;
  font-weight: 700;
}
.course-transfer-steps li > b {
  font: inherit;
}
.course-transfer-steps li.active > span {
  border-color: #1f8883;
  box-shadow: 0 0 0 4px #e7f4f1;
}
.course-transfer-steps li.completed > span,
.course-transfer-steps li.active > span {
  border-color: #1f8883;
  color: #fff;
  background: #1f8883;
}
.course-transfer-steps li.completed::after {
  background: #1f8883;
}
.course-transfer-owner-summary {
  margin-bottom: 18px;
  padding: 18px 32px 18px 15px;
}
.course-transfer-columns {
  grid-template-columns: 575px minmax(0, 605px);
  gap: 20px;
}
.course-transfer-source,
.course-transfer-recipient {
  min-height: 590px;
  box-sizing: border-box;
  align-content: start;
  gap: 0;
  padding: 17px 19px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
}
.course-transfer-source h2,
.course-transfer-recipient h2 {
  margin: 0 0 13px;
  font-size: 20px;
}
.course-transfer-course-card {
  min-height: 120px;
  box-sizing: border-box;
  padding: 14px;
  border-radius: 10px;
  background: #eef6f0;
}
.course-transfer-course-card h3 {
  margin: 0 0 3px;
  font-size: 18px;
}
.course-transfer-course-card span {
  color: #667a72;
  font-size: 13px;
}
.course-transfer-course-card p {
  margin: 15px 0 0;
  font-size: 14px;
}
.course-transfer-source > label,
.course-transfer-recipient > label {
  gap: 7px;
  color: #667a72;
  font-size: 14px;
  font-weight: 400;
}
.course-transfer-source > label:first-of-type {
  margin-top: 50px;
}
.course-transfer-stepper {
  gap: 12px;
}
.course-transfer-stepper button {
  width: 48px;
  height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  background: #fff;
}
.course-transfer-stepper input {
  width: 90px;
  height: 48px;
  border: 0;
  border-radius: 9px;
  background: #eef6f0;
  font-weight: 700;
}
.course-transfer-stepper em {
  margin-left: 6px;
}
.course-transfer-reason {
  margin-top: 24px;
  color: #25372e !important;
  font-weight: 600 !important;
}
.course-transfer-reason input,
.course-transfer-recipient input,
.course-transfer-recipient select {
  box-sizing: border-box;
  width: 100%;
  min-height: 50px;
  padding: 0 13px;
  border: 1px solid #cbd8d4;
  border-radius: 8px;
  color: #25372f;
  background: #fff;
  font: inherit;
}
.course-transfer-reason input.invalid {
  border-color: #c43d3d;
  box-shadow: 0 0 0 1px #c43d3d;
}
.course-transfer-reason > small {
  color: #b42318;
  font-size: 12px;
  font-weight: 500;
}
.course-transfer-recipient > label + .course-transfer-results,
.course-transfer-recipient > label + .course-transfer-selected {
  margin-top: 8px;
}
.course-transfer-recipient > label:nth-of-type(2) {
  margin-top: 14px;
}
.course-transfer-recipient label small {
  color: #667a72;
  font-size: 12px;
  font-weight: 400;
}
.course-share-allocation {
  grid-template-columns: 1fr;
  gap: 8px;
  margin-top: 26px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f4f7f5;
}
.course-share-allocation > strong {
  margin-bottom: 4px;
  font-size: 15px;
}
.course-share-allocation label {
  display: grid;
  grid-template-columns: 1fr 58px;
  align-items: center;
  min-height: 42px;
  color: #25372f;
  font-size: 14px;
  font-weight: 400;
}
.course-share-allocation input,
.course-share-allocation em {
  display: grid;
  width: 58px;
  height: 34px;
  box-sizing: border-box;
  border: 1px solid #b8c9c4;
  border-radius: 7px;
  background: #fff;
  place-items: center;
  text-align: center;
  font-style: normal;
}
.course-share-allocation > small {
  margin-top: 6px;
}
.course-transfer-allocation-hint {
  margin: 10px 0 0;
  color: #1d736c;
  font-size: 13px;
  font-weight: 600;
}
.course-transfer-help {
  margin-top: 13px;
  line-height: 1.5;
}
.course-transfer-footer {
  gap: 12px;
  margin-top: 30px;
}
.course-transfer-footer button {
  width: 140px;
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  background: #fff;
}
.course-transfer-footer button:last-child {
  width: 175px;
  border-color: #5f9155;
  background: #5f9155;
}
.course-transfer-review {
  width: 100%;
  padding: 0;
  border-radius: 0;
  background: transparent;
}
.course-transfer-main.is-review {
  width: min(1000px, calc(100% - 48px));
  padding-bottom: 90px;
}
.course-transfer-main.is-review > h1 {
  font-size: 27px;
}
.course-transfer-main.is-review .course-transfer-steps {
  width: min(620px, 100%);
  margin-top: 20px;
  margin-bottom: 22px;
}
.course-transfer-review-people {
  display: grid;
  grid-template-columns: minmax(0, 440px) auto minmax(0, 440px);
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.course-transfer-direction {
  color: #1f8883;
  font-size: 26px;
  line-height: 1;
}
.course-transfer-review-people article {
  display: grid;
  gap: 4px;
  min-height: 94px;
  box-sizing: border-box;
  padding: 13px 15px;
  border: 1px solid #dde6e1;
  border-radius: 10px;
  background: #f4f7f5;
}
.course-transfer-review-people article span {
  color: #1e807c;
  font-size: 13px;
  font-weight: 700;
}
.course-transfer-review-people article strong {
  font-size: 19px;
}
.course-transfer-review-people article small {
  color: #667a72;
  font-size: 14px;
}
.course-transfer-review-summary {
  display: grid;
  gap: 6px;
  box-sizing: border-box;
  width: 100%;
  margin-top: 9px;
  padding: 12px 34px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
}
.course-transfer-review-summary p {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(300px, 1fr);
  align-items: center;
  min-height: 46px;
  margin: 0;
}
.course-transfer-review-summary span {
  color: #667a72;
  font-size: 14px;
}
.course-transfer-review-summary b {
  color: #25372f;
  font-size: 15px;
  font-weight: 600;
}
.course-transfer-review-note {
  box-sizing: border-box;
  min-height: 58px;
  margin-top: 28px !important;
  padding: 17px 16px;
  border-radius: 10px;
  color: #25372f !important;
  background: #fff4d6;
}
.course-transfer-review footer {
  gap: 12px;
  margin-top: 24px;
}
.course-transfer-review footer button {
  width: 170px;
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  background: #fff;
}
.course-transfer-review footer button:last-child {
  width: 180px;
  border-color: #5f9155;
  background: #5f9155;
}
@media (max-width: 1048px) {
  .course-transfer-main.is-review {
    width: min(100% - 40px, 1000px);
  }
  .course-transfer-review-people {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  }
}
@media (max-width: 900px) {
  .course-transfer-columns {
    grid-template-columns: 1fr;
  }
  .course-transfer-source,
  .course-transfer-recipient {
    min-height: auto;
  }
  .course-transfer-review-people {
    grid-template-columns: 1fr;
  }
  .course-transfer-direction {
    justify-self: center;
    transform: rotate(90deg);
  }
}
@media (max-width: 620px) {
  .course-transfer-main {
    width: min(100% - 28px, 1200px);
    padding-top: 18px;
  }
  .course-transfer-main > h1 {
    font-size: 27px;
  }
  .course-transfer-steps li {
    padding-inline: 2px;
    font-size: 12px;
  }
  .course-transfer-steps li::after {
    left: calc(50% + 20px);
    width: calc(100% - 40px);
  }
  .course-transfer-owner-summary {
    grid-template-columns: 48px 1fr;
    padding-right: 15px;
  }
  .course-transfer-owner-summary > b {
    grid-column: 2;
    justify-self: start;
  }
  .course-transfer-stepper {
    flex-wrap: wrap;
  }
  .course-transfer-stepper em {
    flex-basis: 100%;
    margin-left: 0;
  }
  .course-transfer-footer {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .course-transfer-footer button,
  .course-transfer-footer button:last-child {
    width: 100%;
  }
  .course-transfer-review-summary {
    padding: 12px 16px;
  }
  .course-transfer-review-summary p {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 8px 0;
  }
  .course-transfer-review-note {
    margin-top: 22px !important;
  }
  .course-transfer-review footer {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .course-transfer-review footer button,
  .course-transfer-review footer button:last-child {
    width: 100%;
  }
}

/* Figma 861:4967 — transfer success */
.course-transfer-main.is-success {
  width: min(620px, calc(100% - 32px));
  padding: 28px 0 70px;
}
.course-transfer-main.is-success > .course-transfer-back,
.course-transfer-main.is-success > h1,
.course-transfer-main.is-success > .course-transfer-intro,
.course-transfer-main.is-success > .course-transfer-steps {
  display: none;
}
.course-transfer-main.is-success .course-transfer-success {
  display: grid;
  justify-items: center;
  box-sizing: border-box;
  width: 100%;
  gap: 0;
  padding: 27px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
}
.course-transfer-main.is-success .course-transfer-success-icon {
  width: 68px;
  height: 68px;
  color: #17663a;
  background: #d9f2e4;
  font-size: 30px;
}
.course-transfer-main.is-success .course-transfer-success h2 {
  margin-top: 20px;
  font-size: 26px;
}
.course-transfer-main.is-success .course-transfer-success > p {
  margin-top: 8px;
  font-size: 14px;
}
.course-transfer-main.is-success .course-transfer-success-details {
  gap: 10px;
  margin-top: 22px;
  padding: 14px 18px;
}
.course-transfer-main.is-success .course-transfer-success-details p {
  min-height: 54px;
  border-radius: 0;
}
.course-transfer-main.is-success .course-transfer-success-actions {
  display: grid;
  grid-template-columns: 250px 1fr;
  gap: 28px;
  margin-top: 46px;
}
.course-transfer-main.is-success .course-transfer-success-actions button {
  min-height: 48px;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  background: #fff;
}
.course-transfer-main.is-success .course-transfer-success-actions button:last-child {
  border-color: #5f9155;
  color: #fff;
  background: #5f9155;
}

/* Figma 140:3876 — course after transfer */
.course-manager-main.after-transfer {
  width: min(1200px, calc(100% - 48px));
  padding-bottom: 60px;
}
.course-manager-main.after-transfer > h1 {
  margin-top: 35px;
  font-size: 42px;
}
.course-manager-main.after-transfer .course-manager-intro {
  display: none;
}
.course-after-patient {
  display: grid;
  gap: 4px;
  box-sizing: border-box;
  width: 100%;
  min-height: 104px;
  margin-top: 18px;
  padding: 24px;
  border-radius: 16px;
  color: #fff;
  background: #1e8883;
}
.course-after-patient strong {
  font-size: 22px;
}
.course-after-patient span {
  font-size: 15px;
}
.course-manager-main.after-transfer .course-status-tabs {
  grid-template-columns: 184px 200px;
  gap: 12px;
  overflow: visible;
}
.course-manager-main.after-transfer .course-status-tabs button {
  border-radius: 9px;
  background: #eff4ed;
}
.course-manager-main.after-transfer .course-status-tabs button.active {
  color: #fff;
  background: #1e8883;
}
.course-after-share-card {
  display: grid;
  gap: 18px;
  box-sizing: border-box;
  padding: 24px;
  border: 1px solid #dce3dc;
  border-radius: 18px;
  background: #fff;
}
.course-after-share-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.course-after-share-card header h2 {
  margin: 0;
  font-size: 28px;
}
.course-after-share-card header p {
  margin: 5px 0 0;
  color: #56665d;
}
.course-after-share-card header > span {
  min-width: 180px;
  padding: 10px 18px;
  border-radius: 10px;
  color: #1e807c;
  background: #eff4ed;
  text-align: center;
}
.course-after-remaining {
  padding: 20px;
  border-radius: 12px;
  background: #eff4ed;
}
.course-after-remaining > span {
  font-size: 16px;
}
.course-after-remaining p {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 4px 0;
}
.course-after-remaining strong {
  color: #1e807c;
  font-size: 52px;
  line-height: 1.15;
}
.course-after-remaining small {
  color: #56665d;
  font-size: 15px;
}
.course-after-counts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.course-after-counts article {
  display: grid;
  gap: 5px;
  padding: 16px;
  border: 1px solid #dce3dc;
  border-radius: 12px;
  text-align: center;
}
.course-after-counts strong {
  font-size: 26px;
}
.course-after-dates {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
.course-after-dates p {
  display: flex;
  gap: 12px;
  margin: 0;
}
.course-after-dates b {
  font-weight: 500;
}
.course-after-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.course-after-actions button {
  min-width: 270px;
  min-height: 56px;
  border: 1px solid #dce3dc;
  border-radius: 10px;
  background: #fff;
  font: inherit;
  font-weight: 700;
}
.course-after-actions button:not(:disabled),
.course-manager-latest-transfer {
  cursor: pointer;
}
.course-after-actions button:disabled {
  cursor: not-allowed;
}
.course-after-actions button:last-child {
  border-color: #1e8883;
  color: #fff;
  background: #1e8883;
}
.course-after-share-card .course-manager-latest-transfer {
  margin-top: 0;
}

/* Figma 177:1915 — use a transferred course */
.course-use-main.after-transfer {
  display: grid;
  place-items: center;
  width: min(640px, calc(100% - 32px));
  min-height: calc(100vh - 148px);
  padding: 28px 0 44px;
}
.course-after-use-confirm {
  box-sizing: border-box;
  width: 100%;
  padding: 28px;
  border: 1px solid #1e807c;
  border-radius: 24px;
  background: #fff;
}
.course-after-use-confirm > h1 {
  margin: 0 0 18px;
  font-size: 29px;
}
.course-after-use-details {
  display: grid;
  gap: 9px;
  padding: 18px;
  border-radius: 16px;
  background: #eff4ed;
}
.course-after-use-details h2,
.course-after-use-details h3,
.course-after-use-details p {
  margin: 0;
}
.course-after-use-details h2 {
  font-size: 20px;
}
.course-after-use-details h3 {
  font-size: 19px;
}
.course-after-use-details p,
.course-after-use-details > span {
  font-size: 15px;
}
.course-after-use-details strong {
  margin-top: 3px;
  font-size: 16px;
}
.course-after-use-details > b {
  color: #1e807c;
  font-size: 32px;
  line-height: 1.15;
}
.course-after-use-details > b small {
  font-size: 18px;
  font-weight: 400;
}
.course-after-use-details > span {
  color: #56665d;
}
.course-after-use-confirm footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 18px;
}
.course-after-use-confirm footer button {
  min-height: 46px;
  border: 0;
  border-radius: 12px;
  color: #1f2937;
  background: #eff4ed;
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.course-after-use-confirm footer button:disabled {
  cursor: not-allowed;
}
.course-after-use-confirm footer button:last-child {
  color: #fff;
  background: #1e807c;
}
.course-after-use-success-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  box-sizing: border-box;
  padding: 24px;
  background: rgb(0 0 0 / 45%);
}
.course-after-use-success-modal {
  display: grid;
  box-sizing: border-box;
  width: min(640px, 100%);
  gap: 0;
  padding: 28px;
  border-radius: 20px;
  background: #fff;
}
.course-after-use-success-modal > h1 {
  margin: 0;
  font-size: 29px;
}
.course-after-use-success-modal > p {
  margin: 7px 0 18px;
  color: #56665d;
  font-size: 15px;
}
.course-after-use-success-modal > div {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 14px;
  background: #eff4ed;
}
.course-after-use-success-modal h2,
.course-after-use-success-modal h3 {
  margin: 0;
}
.course-after-use-success-modal h2 {
  font-size: 20px;
}
.course-after-use-success-modal h3 {
  font-size: 18px;
}
.course-after-use-success-modal strong {
  margin-top: 2px;
  font-size: 16px;
}
.course-after-use-success-modal b {
  color: #1e807c;
  font-size: 28px;
}
.course-after-use-success-modal b small {
  font-size: 18px;
  font-weight: 400;
}
.course-after-use-success-modal footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 18px;
}
.course-after-use-success-modal button {
  min-height: 46px;
  border: 0;
  border-radius: 10px;
  color: #25372e;
  background: #eff4ed;
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.course-after-use-success-modal button:last-child {
  color: #fff;
  background: #1e807c;
}

/* Figma 177:1968 — history after transfer */
.course-history-main.inactive-history {
  width: min(1000px, calc(100% - 48px));
  padding-top: 22px;
}
.course-history-main.inactive-history > .course-history-intro {
  display: none;
}
.course-history-main.inactive-history > h1 {
  margin-bottom: 20px;
  font-size: 40px;
}
.course-inactive-history-totals {
  margin-top: 24px;
}
.course-history-main.after-transfer {
  width: min(1000px, calc(100% - 48px));
  padding-top: 18px;
}
.course-history-main.after-transfer > .course-history-back {
  margin-bottom: 22px;
}
.course-history-main.after-transfer > h1 {
  margin: 0 0 20px;
  font-size: 40px;
}
.course-history-main.after-transfer > .course-history-intro {
  display: none;
}
.course-history-main.after-transfer > .course-history-patient {
  display: grid;
  min-height: 84px;
  grid-template-columns: 1fr;
  gap: 4px;
  padding: 18px 20px;
  border: 0;
  border-radius: 16px;
  color: #fff;
  background: #1e807c;
  margin-bottom: 24px;
}
.course-history-main.after-transfer > .course-history-patient i,
.course-history-main.after-transfer > .course-history-patient em,
.course-history-main.after-transfer > .course-history-patient b {
  display: none;
}
.course-history-main.after-transfer > .course-history-patient div {
  display: grid;
  gap: 4px;
}
.course-history-main.after-transfer > .course-history-patient strong {
  font-size: 22px;
}
.course-history-main.after-transfer > .course-history-patient span {
  color: #fff;
  font-size: 16px;
}
.course-history-main.after-transfer .course-history-summary {
  display: none;
}
.course-after-history-totals {
  display: grid;
  gap: 18px;
}
.course-after-history-totals > h2 {
  margin: 0;
  font-size: 23px;
}
.course-after-history-totals > div {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.course-after-history-totals article {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 15px 12px;
  border: 1px solid #dce3dc;
  border-radius: 12px;
  background: #fff;
}
.course-after-history-totals article:last-child {
  background: #eff4ed;
}
.course-after-history-totals span {
  color: #56665d;
}
.course-after-history-totals strong {
  font-size: 19px;
}
.course-after-history-totals article:last-child strong {
  color: #1e807c;
}
.course-history-main.after-transfer .course-history-entries-header h2 {
  font-size: 21px;
}
.course-after-transfer-entry {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border: 1px solid #dce3dc;
  border-radius: 16px;
  background: #fff;
}
.course-after-transfer-entry strong {
  font-size: 20px;
}
.course-after-transfer-entry span {
  font-size: 16px;
}
@media (max-width: 760px) {
  .course-transfer-main.is-success .course-transfer-success {
    padding: 24px 18px;
  }
  .course-transfer-main.is-success .course-transfer-success-actions {
    grid-template-columns: 1fr;
    gap: 10px;
    margin-top: 28px;
  }
  .course-manager-main.after-transfer {
    width: min(100% - 28px, 1200px);
  }
  .course-manager-main.after-transfer > h1 {
    font-size: 30px;
  }
  .course-after-counts {
    grid-template-columns: 1fr;
  }
  .course-after-dates,
  .course-after-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .course-after-actions button {
    width: 100%;
    min-width: 0;
  }
  .course-after-use-success-modal {
    padding: 22px 18px;
  }
  .course-after-use-success-modal footer {
    grid-template-columns: 1fr;
  }
  .course-after-use-confirm {
    padding: 24px 18px;
  }
  .course-after-use-confirm > h1 {
    font-size: 28px;
  }
  .course-after-use-confirm footer {
    grid-template-columns: 1fr;
  }
  .course-history-main.after-transfer {
    width: min(100% - 28px, 1200px);
  }
  .course-history-main.inactive-history {
    width: min(100% - 28px, 1000px);
  }
  .course-history-main.after-transfer > h1 {
    font-size: 34px;
  }
  .course-after-history-totals > div {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 480px) {
  .course-after-share-card {
    padding: 18px;
  }
  .course-after-share-card header {
    align-items: flex-start;
    flex-direction: column;
  }
  .course-after-share-card header > span {
    min-width: 0;
  }
  .course-manager-main.after-transfer .course-status-tabs {
    grid-template-columns: 1fr;
  }
  .course-after-history-totals > div {
    grid-template-columns: 1fr;
  }
}
@media print {
  @page {
    size: A4 portrait;
    margin: 0;
  }
  :global(html),
  :global(body) {
    margin: 0 !important;
    background: #fff !important;
  }
  :deep(.staff-header),
  :deep(.menu-backdrop),
  .staff-page > header,
  .course-history-back,
  .course-history-print,
  .course-cut-back-link,
  .course-cut-actions,
  .course-print-modal-backdrop,
  .course-confirm-backdrop {
    display: none !important;
  }
  .staff-page,
  .course-page,
  .course-history-main,
  .course-cut-preview {
    width: 100% !important;
    min-height: 0 !important;
    margin: 0 !important;
    padding: 0 !important;
    background: #fff !important;
  }
  :deep(.staff-header) {
    box-shadow: none !important;
  }
  .course-cut-document {
    display: block;
    width: 100%;
    min-height: 0;
    height: 265mm;
    max-height: 265mm;
    box-sizing: border-box;
    margin: 0;
    padding: 8mm 12mm 7mm;
    border: 0;
    border-radius: 0;
    box-shadow: none;
    overflow: hidden;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
}
</style>
