<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { courseApi, patientApi, type CoursePurchase, type Patient } from '@/api/client'

type CourseAction = 'manage' | 'transfer' | 'history' | 'use'
const route = useRoute()
const router = useRouter()
const action = ref<CourseAction>('manage')
const recipientSearch = ref('')
const recipientSearchOpen = ref(false)
const selectedRecipient = ref<Patient | null>(null)
const transferAmount = ref(1)
const transferReason = ref('')
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
const courseUsageHistory = ref<CourseMovement[]>([])
const errorMessage = ref('')
const selectedCourse = ref<UiCourse>({} as UiCourse)
const selectedCourseId = ref(0)
const usageNote = ref('')
const usageDate = ref(clinicToday())
const usageReview = ref(false)
const usageSuccess = ref(false)
const usageConfirmDialog = ref(false)
const transferStep = ref<'form' | 'review' | 'success'>('form')
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
async function loadOwner() {
  owners.value = await patientApi.searchAll()
  const patientId = Number(route.query.patientId)
  owner.value = owners.value.find((patient) => patient.id === patientId) ?? owners.value[0] ?? null
  if (owner.value) await loadCourses(owner.value.id)
}
async function loadCourses(patientId: number) {
  patientCourses.value = (await courseApi.patientCourses(patientId)).map((course) => ({
    ...course,
    name: course.course.name,
    expiry: new Date(course.expiresAt).toLocaleDateString('th-TH'),
    balance: course.remaining,
    total: course.purchased,
  }))
  selectedCourseId.value = patientCourses.value[0]?.id ?? 0
  if (route.query.action === 'use') {
    action.value = 'use'
    selectedCourse.value = patientCourses.value[0] ?? ({} as UiCourse)
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
let searchTimer: number | undefined
let recipientSearchVersion = 0
watch(recipientSearch, (value) => {
  window.clearTimeout(searchTimer)
  const version = ++recipientSearchVersion
  const keyword = value.trim()
  if (selectedRecipient.value || !recipientSearchOpen.value) return
  recipientResults.value = []
  if (!keyword) return
  searchTimer = window.setTimeout(async () => {
    try {
      const results = await patientApi.search(keyword, 0, 20)
      if (version === recipientSearchVersion) recipientResults.value = results.items
    } catch (error) {
      if (version === recipientSearchVersion)
        errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
    }
  }, 250)
})
async function start(nextAction: CourseAction, course: UiCourse) {
  action.value = nextAction
  selectedCourse.value = course
  selectedCourseId.value = course.id
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  transferAmount.value = 1
  transferReason.value = ''
  transferReceiptId.value = null
  usageReview.value = false
  usageSuccess.value = false
  transferStep.value = 'form'
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
  if (course) await start('history', course)
  else {
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
  action.value = 'manage'
  historyFromInactive.value = false
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  usageReview.value = false
  usageSuccess.value = false
  transferStep.value = 'form'
}
function returnToCourseManager() {
  action.value = 'manage'
  usageReview.value = false
  usageSuccess.value = false
  if (owner.value) router.replace({ path: '/staff/courses', query: { patientId: owner.value.id } })
}
async function confirm() {
  if (!owner.value || !selectedCourse.value.id) return
  try {
    const isCourseUsage = action.value === 'use'
    const usedCourseId = selectedCourse.value.id
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
        reason: transferReason.value || 'โอนสิทธิ์ตามคำขอผู้ป่วย',
      })
      transferReceiptId.value = transfer.id
    }
    await loadCourses(owner.value.id)
    if (isCourseUsage) {
      selectedCourse.value =
        patientCourses.value.find((course) => course.id === usedCourseId) ?? selectedCourse.value
      action.value = 'use'
      usageReview.value = false
      usageSuccess.value = true
    } else {
      selectedCourse.value =
        patientCourses.value.find((course) => course.id === usedCourseId) ?? selectedCourse.value
      action.value = 'transfer'
      transferStep.value = 'success'
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โอนสิทธิ์ไม่สำเร็จ'
  }
}
function selectRecipient(patient: Patient) {
  selectedRecipient.value = patient
  recipientSearchOpen.value = false
  recipientSearch.value = `${patient.fullName} HN ${patient.hn}`
}
function handleRecipientInput() {
  selectedRecipient.value = null
  recipientSearchOpen.value = true
}
function clearRecipient() {
  recipientSearch.value = ''
  selectedRecipient.value = null
  recipientSearchOpen.value = false
}
function startAnotherTransfer() {
  recipientSearch.value = ''
  recipientSearchOpen.value = false
  selectedRecipient.value = null
  transferAmount.value = 1
  transferReason.value = ''
  transferReceiptId.value = null
  transferStep.value = 'form'
}
</script>

<template>
  <div class="staff-page course-page">
    <StaffHeader />
    <main v-if="action === 'use'" class="course-use-main">
      <section v-if="usageSuccess" class="course-use-success">
        <h1>บันทึกการใช้สิทธิ์สำเร็จ</h1>
        <strong>{{ owner?.fullName }} · HN {{ owner?.hn }}</strong>
        <div class="course-use-success-details">
          <p>ใช้สิทธิ์ {{ selectedCourse.name }} 1 ครั้ง</p>
          <p>วันที่ {{ formattedUsageDate }}</p>
          <p>ผู้ให้บริการตามตาราง</p>
          <p>สาขาราชพฤกษ์</p>
        </div>
        <p class="remaining">
          เหลือ {{ selectedCourse.balance }} จาก {{ selectedCourse.total }} ครั้ง
        </p>
        <div class="course-progress">
          <i :style="{ width: `${(selectedCourse.balance / selectedCourse.total) * 100}%` }" />
        </div>
        <button type="button" @click="returnToCourseManager">กลับหน้าจัดการคอร์ส</button>
      </section>
      <section v-else-if="usageReview" class="course-use-review">
        <h1>ยืนยันการใช้สิทธิ์</h1>
        <p class="course-use-review-intro">ตรวจสอบข้อมูลก่อนตัดสิทธิ์ออกจากคอร์ส</p>
        <div class="course-use-review-details">
          <strong>{{ owner?.fullName }}&nbsp;&nbsp;HN {{ owner?.hn }}</strong>
          <b>{{ selectedCourse.name }}</b>
          <p>วันที่ {{ formattedUsageDate }}</p>
          <p>ผู้ให้บริการตามตาราง</p>
          <p>สาขาราชพฤกษ์</p>
          <em>เหลือ {{ Math.max(0, selectedCourse.balance - 1) }} ครั้ง</em>
          <div class="course-progress">
            <i
              :style="{
                width: `${(Math.max(0, selectedCourse.balance - 1) / selectedCourse.total) * 100}%`,
              }"
            />
          </div>
          <small>หมดอายุ {{ selectedCourse.expiry }}</small>
        </div>
        <footer>
          <button type="button" @click="usageReview = false">กลับไปแก้ไข</button
          ><button
            type="button"
            :disabled="!selectedCourse.usable"
            @click="usageConfirmDialog = true"
          >
            ยืนยันใช้สิทธิ์
          </button>
        </footer>
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
          ><span>HN {{ owner?.hn }} · สาขาราชพฤกษ์</span>
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
          class="course-confirm-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="course-confirm-title"
        >
          <h2 id="course-confirm-title">ยืนยันการใช้สิทธิ์ 1 ครั้ง ?</h2>
          <p>ตรวจสอบยอดคงเหลือหลังใช้สิทธิ์ก่อนยืนยัน</p>
          <div class="course-confirm-details">
            <strong>{{ owner?.fullName }}&nbsp; HN {{ owner?.hn }}</strong>
            <strong>{{ selectedCourse.name }}</strong>
            <b>เดิมคงเหลือ {{ selectedCourse.balance }}/{{ selectedCourse.total }} ครั้ง</b>
          </div>
          <div class="course-confirm-actions">
            <button type="button" @click="usageConfirmDialog = false">กลับไปตรวจสอบ</button
            ><button type="button" @click="confirm">ยืนยันใช้ 1 ครั้ง</button>
          </div>
        </section>
      </div>
    </main>
    <main v-else-if="action === 'transfer'" class="course-transfer-main">
      <button class="course-transfer-back" type="button" @click="goBack">
        ← กลับไปหน้าจัดการคอร์ส
      </button>
      <h1>โอนสิทธิ์คอร์ส</h1>
      <p class="course-transfer-intro">
        เลือกผู้รับและจำนวนสิทธิ์ แล้วตรวจสอบข้อมูลก่อนยืนยันการโอน
      </p>
      <ol class="course-transfer-steps" aria-label="ขั้นตอนการโอนสิทธิ์">
        <li :class="{ active: transferStep === 'form' }">1&nbsp; ระบุการโอน</li>
        <li :class="{ active: transferStep === 'review' }">2&nbsp; ตรวจสอบและยืนยัน</li>
        <li :class="{ active: transferStep === 'success' }">3&nbsp; สำเร็จ</li>
      </ol>
      <template v-if="transferStep === 'form'">
        <div class="course-transfer-columns">
          <section class="course-transfer-source">
            <h2>คอร์สที่ต้องการโอน</h2>
            <div class="course-transfer-person">
              <i>{{ owner?.fullName?.slice(0, 1) }}</i>
              <div>
                <strong>{{ owner?.fullName }}</strong
                ><span>HN {{ owner?.hn }} · สาขาราชพฤกษ์</span>
              </div>
            </div>
            <h3>{{ selectedCourse.name }}</h3>
            <div class="course-transfer-balance">
              <strong>คงเหลือ {{ selectedCourse.balance }} ครั้ง</strong
              ><span>จากทั้งหมด {{ selectedCourse.total }} ครั้ง</span>
            </div>
            <p class="course-transfer-dates">
              ซื้อเมื่อ {{ new Date(selectedCourse.purchasedAt).toLocaleDateString('th-TH')
              }}<span>หมดอายุ {{ selectedCourse.expiry }}</span>
            </p>
            <label
              >จำนวนสิทธิ์ที่ต้องการโอน *<span class="course-transfer-stepper"
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
                ><em>ครั้ง</em></span
              ></label
            >
            <div class="course-transfer-remaining-hint" aria-live="polite">
              <small>โอนได้ 1–{{ selectedCourse.balance }} ครั้ง</small
              ><strong
                >ผู้โอนจะเหลือ
                {{
                  Math.max(0, selectedCourse.balance - (Number(transferAmount) || 0))
                }}
                ครั้ง</strong
              >
            </div>
          </section>
          <section class="course-transfer-recipient">
            <h2>ผู้รับสิทธิ์</h2>
            <label
              >ค้นหาผู้ป่วย<input
                v-model.trim="recipientSearch"
                placeholder="ค้นหาด้วยชื่อ, HN หรือเบอร์โทรศัพท์"
                @input="handleRecipientInput"
            /></label>
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
              <button
                type="button"
                aria-label="เปลี่ยนผู้รับ"
                @click="clearRecipient"
              >
                ×
              </button>
            </div>
            <label
              >เหตุผลการโอน (ไม่บังคับ)<textarea
                v-model.trim="transferReason"
                placeholder="ระบุเหตุผลการโอนสิทธิ์"
              ></textarea>
            </label>
            <p class="course-transfer-help">สิทธิ์ที่โอนจะคงวันหมดอายุเดิมของคอร์สไว้</p>
          </section>
        </div>
        <footer class="course-transfer-footer">
          <button type="button" @click="goBack">ยกเลิก</button
          ><button
            type="button"
            :disabled="
              !selectedRecipient || transferAmount < 1 || transferAmount > selectedCourse.balance
            "
            @click="transferStep = 'review'"
          >
            ตรวจสอบการโอน
          </button>
        </footer>
      </template>
      <section v-else-if="transferStep === 'review'" class="course-transfer-review">
        <h2>ตรวจสอบรายการโอน</h2>
        <div class="course-transfer-review-people">
          <article>
            <span>ผู้โอน</span><strong>{{ owner?.fullName }}</strong
            ><small>HN {{ owner?.hn }}</small
            ><small>{{ branchLabel(owner) }}</small>
          </article>
          <b>→</b>
          <article>
            <span>ผู้รับ</span><strong>{{ selectedRecipient?.fullName }}</strong
            ><small>HN {{ selectedRecipient?.hn }}</small
            ><small>{{ branchLabel(selectedRecipient) }}</small>
          </article>
        </div>
        <h3 class="course-transfer-course-name">
          {{ selectedCourse.name }}<small>{{ selectedCourse.course?.code }}</small>
        </h3>
        <div class="course-transfer-metrics">
          <article>
            <span>จำนวนที่โอน</span><strong>{{ transferAmount }}</strong
            ><small>ครั้ง</small>
          </article>
          <article>
            <span>ผู้โอนคงเหลือ</span><strong>{{ selectedCourse.balance - transferAmount }}</strong
            ><small>ครั้ง</small>
          </article>
          <article>
            <span>ผู้รับได้รับ</span><strong>{{ transferAmount }}</strong
            ><small>ครั้ง</small>
          </article>
        </div>
        <div class="course-transfer-detail-list">
          <p>วันหมดอายุสิทธิ์ที่โอน: {{ selectedCourse.expiry }}</p>
          <p>เหตุผล: {{ transferReason || 'โอนสิทธิ์ตามคำขอผู้ป่วย' }}</p>
        </div>
        <p class="course-transfer-review-note">
          กรุณาตรวจสอบผู้รับและจำนวนสิทธิ์ให้ถูกต้องก่อนกดยืนยัน
        </p>
        <footer>
          <button type="button" @click="transferStep = 'form'">ยกเลิก</button
          ><button type="button" @click="confirm">ยืนยันโอนสิทธิ์</button>
        </footer>
      </section>
      <section v-else class="course-transfer-success">
        <div class="course-transfer-success-icon">✓</div>
        <h2>โอนสิทธิ์คอร์สสำเร็จ</h2>
        <p v-if="transferReceiptId">เลขที่รายการ TRF-{{ transferReceiptId }}</p>
        <div class="course-transfer-review-people">
          <article>
            <span>ผู้โอน</span><strong>{{ owner?.fullName }}</strong
            ><small>HN {{ owner?.hn }}</small
            ><small>{{ branchLabel(owner) }}</small>
          </article>
          <b>→</b>
          <article>
            <span>ผู้รับ</span><strong>{{ selectedRecipient?.fullName }}</strong
            ><small>HN {{ selectedRecipient?.hn }}</small
            ><small>{{ branchLabel(selectedRecipient) }}</small>
          </article>
        </div>
        <h3 class="course-transfer-course-name">
          {{ selectedCourse.name }}<small>{{ selectedCourse.course?.code }}</small>
        </h3>
        <div class="course-transfer-metrics">
          <article>
            <span>จำนวนที่โอน</span><strong>{{ transferAmount }}</strong
            ><small>ครั้ง</small>
          </article>
          <article>
            <span>ผู้โอนคงเหลือ</span><strong>{{ selectedCourse.balance }}</strong
            ><small>ครั้ง</small>
          </article>
          <article>
            <span>ผู้รับได้รับ</span><strong>{{ transferAmount }}</strong
            ><small>ครั้ง</small>
          </article>
        </div>
        <div class="course-transfer-detail-list">
          <p>วันหมดอายุสิทธิ์ที่โอน: {{ selectedCourse.expiry }}</p>
          <p>เหตุผล: {{ transferReason || 'โอนสิทธิ์ตามคำขอผู้ป่วย' }}</p>
        </div>
        <div class="course-transfer-success-actions">
          <button type="button" @click="startAnotherTransfer">เริ่มรายการใหม่</button
          ><button type="button" @click="returnToCourseManager">กลับไปจัดการคอร์ส</button>
        </div>
      </section>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </main>
    <main v-else-if="action === 'history'" class="course-history-main">
      <button class="course-history-back" type="button" @click="goBack">
        ← กลับไปหน้าจัดการคอร์ส
      </button>
      <h1>ประวัติการใช้คอร์ส</h1>
      <section class="course-history-patient">
        <strong>{{ owner?.fullName }}</strong>
        <span>HN {{ owner?.hn }}</span>
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
        <h2 class="course-history-course">
          {{ selectedCourse.name }} ({{ selectedCourse.course?.code }})
        </h2>
        <section class="course-history-stats" aria-label="สรุปสิทธิ์คอร์ส">
          <article>
            <span>ทั้งหมด</span><strong>{{ selectedCourse.total }} ครั้ง</strong>
          </article>
          <article>
            <span>ใช้แล้ว</span><strong>{{ usedSessions }} ครั้ง</strong>
          </article>
          <article>
            <span>โอนออก</span><strong>{{ transferredOut }} ครั้ง</strong>
          </article>
          <article>
            <span>คงเหลือ</span><strong>{{ selectedCourse.balance }} ครั้ง</strong>
          </article>
        </section>
        <section class="course-history-entries">
          <h2>รายการใช้บริการ</h2>
          <article v-for="entry in usageEntries" :key="entry.id" class="course-history-entry">
            <div>
              <strong>{{
                new Date(entry.createdAt).toLocaleDateString('th-TH', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                })
              }}</strong>
              <b>{{ selectedCourse.name }}</b>
              <span>{{ entry.reason || 'ใช้สิทธิ์คอร์ส' }}</span>
              <span>ผู้ให้บริการตามตาราง · สาขาราชพฤกษ์</span>
            </div>
            <aside>
              <b>ใช้ {{ Math.abs(entry.quantity) }} ครั้ง</b
              ><span>คงเหลือ {{ entry.balanceAfter }} ครั้ง</span>
            </aside>
          </article>
          <p v-if="!usageEntries.length" class="course-history-empty">
            ยังไม่มีประวัติการใช้บริการในคอร์สนี้
          </p>
        </section>
      </template>
      <p v-else class="course-history-empty">ยังไม่มีคอร์สที่หมดอายุหรือใช้สิทธิ์ครบ</p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </main>
    <main v-else-if="action === 'manage'" class="course-manager-main">
      <RouterLink
        class="course-use-back"
        :to="{ path: '/staff/services', query: { patientId: owner?.id } }"
        >← กลับไปหน้าบริการ</RouterLink
      >
      <h1>จัดการคอร์สผู้ป่วย</h1>
      <section class="course-use-patient">
        <strong>{{ owner?.fullName || 'กำลังโหลด...' }}</strong
        ><span>HN {{ owner?.hn || '-' }}</span>
      </section>
      <div class="course-status-tabs">
        <button class="active" type="button">ใช้งานได้</button
        ><button type="button" @click="openInactiveHistory">หมดอายุ / ใช้สิทธิ์ครบ</button>
      </div>
      <section v-if="!activeCourses.length" class="course-use-empty">
        <h2>ยังไม่มีคอร์สที่ใช้งานได้</h2>
        <p>ผู้ป่วยรายนี้ยังไม่มีคอร์สคงเหลือสำหรับใช้บริการ</p>
      </section>
      <section v-for="course in activeCourses" :key="course.id" class="course-manager-card">
        <span>ใช้งานได้ · {{ course.course.code }}</span>
        <h2>{{ course.name }}</h2>
        <strong>คงเหลือ {{ course.balance }} ครั้ง</strong>
        <p>
          จากทั้งหมด {{ course.total }} ครั้ง | ใช้แล้ว {{ course.total - course.balance }} ครั้ง |
          Bonus {{ course.bonusSessions }} ครั้ง
        </p>
        <div class="course-progress">
          <i :style="{ width: `${(course.balance / course.total) * 100}%` }" />
        </div>
        <footer>
          <small>ซื้อเมื่อ {{ new Date(course.purchasedAt).toLocaleDateString('th-TH') }}</small
          ><small>หมดอายุ {{ course.expiry }}</small>
        </footer>
        <div class="course-manager-actions">
          <button type="button" :disabled="!course.usable" @click="start('use', course)">
            ใช้สิทธิ์คอร์ส</button
          ><button type="button" @click="start('history', course)">ดูประวัติการใช้</button
          ><button type="button" @click="start('transfer', course)">โอนสิทธิ์คอร์ส</button>
        </div>
      </section>
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
.course-transfer-recipient input {
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
.course-transfer-recipient input {
  width: 100%;
  padding: 0 12px;
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
