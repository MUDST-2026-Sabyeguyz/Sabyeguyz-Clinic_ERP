<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi, patientApi, type CoursePurchase, type Patient } from '@/api/client'

type CourseAction = 'transfer' | 'history'
const action = ref<CourseAction | null>(null)
const done = ref(false)
const recipientSearch = ref('')
const selectedRecipient = ref<Patient | null>(null)
const transferAmount = ref(1)
type UiCourse = CoursePurchase & { name: string; expiry: string; balance: number; total: number }
const owner = ref<Patient | null>(null)
const owners = ref<Patient[]>([])
const patientCourses = ref<UiCourse[]>([])
const recipientResults = ref<Patient[]>([])
const transferHistory = ref<Array<{ createdAt: string; sourcePatientId: number; destinationPatientId: number; quantity: number; reason: string }>>([])
const errorMessage = ref('')
const selectedCourse = ref<UiCourse>({} as UiCourse)
const selectedCourseId = ref(0)
async function loadOwner() {
  owners.value = await patientApi.searchAll()
  owner.value = owners.value[0] ?? null
  if (owner.value) await loadCourses(owner.value.id)
}
async function changeOwner() { if (owner.value) await loadCourses(owner.value.id) }
async function loadCourses(patientId: number) {
  patientCourses.value = (await courseApi.patientCourses(patientId)).map((course) => ({ ...course, name: course.course.name, expiry: new Date(course.expiresAt).toLocaleDateString('th-TH'), balance: course.remaining, total: course.purchased }))
  selectedCourseId.value = patientCourses.value[0]?.id ?? 0
  transferHistory.value = await courseApi.transferHistory(patientId) as typeof transferHistory.value
}
onMounted(async () => { try { await loadOwner() } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดคอร์สไม่สำเร็จ' } })
let searchTimer: number | undefined
watch(recipientSearch, () => { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(async () => { if (recipientSearch.value.trim()) recipientResults.value = (await patientApi.search(recipientSearch.value, 0, 20)).items }, 250) })
const title = computed(() => {
  if (action.value === 'transfer') return 'โอนสิทธิ์คอร์ส'
  if (action.value === 'history') return 'ประวัติการโอนคอร์ส'
  return 'จัดการคอร์ส'
})
function start(nextAction: CourseAction, course: UiCourse) {
  action.value = nextAction
  selectedCourse.value = course
  done.value = false
  recipientSearch.value = ''
  selectedRecipient.value = null
  transferAmount.value = 1
}
function goBack() {
  action.value = null
  done.value = false
  recipientSearch.value = ''
  selectedRecipient.value = null
}
async function confirm() {
  if (!owner.value || !selectedRecipient.value || !selectedCourse.value.id) return
  try {
    await courseApi.transfer({ sourcePurchaseId: selectedCourse.value.id, destinationPatientId: selectedRecipient.value.id, quantity: transferAmount.value, reason: 'โอนสิทธิ์ตามคำขอผู้ป่วย' })
    await loadCourses(owner.value.id); done.value = true
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โอนสิทธิ์ไม่สำเร็จ' }
}
function selectRecipient(patient: Patient) {
  selectedRecipient.value = patient
  recipientSearch.value = `${patient.fullName} HN ${patient.hn}`
}
function handleRecipientInput() {
  selectedRecipient.value = null
}
</script>

<template>
  <div class="staff-page course-page">
    <StaffHeader />
    <main class="staff-main course-page-main">
      <RouterLink class="page-back" to="/staff/services">← กลับไปหน้าบริการ</RouterLink>
      <h1 class="course-page-title">เลือกคอร์สที่ต้องการจัดการ</h1>
      <section class="course-browser">
        <div class="course-summary">
          <div class="patient-context">
            <span class="avatar">{{ owner?.fullName?.slice(0, 1) }}</span>
            <div>
              <small>เจ้าของคอร์สที่เลือก</small><b>{{ owner?.fullName || 'กำลังโหลด...' }}</b>
              <p>{{ owner?.hn }} สถานะ {{ owner?.active ? 'ใช้งาน' : 'ปิดใช้งาน' }}</p>
            </div>
            <label class="course-selector-field">
              <span>เลือกคอร์ส</span>
              <select v-model="selectedCourseId" class="owner-selector" data-test="course-selector">
                <option v-if="!patientCourses.length" :value="0" disabled>ไม่มีคอร์ส</option>
                <option v-for="course in patientCourses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.course.code }}</option>
              </select>
            </label>
          </div>
          <div class="course-list">
            <p v-if="!patientCourses.length" class="course-empty">ไม่มีคอร์ส</p>
            <article v-for="course in patientCourses" :key="course.name">
              <div class="course-main">
                <span class="course-icon">▱</span>
                <div>
                  <h3>{{ course.name }}</h3>
                  <p>หมดอายุ {{ course.expiry }}</p>
                </div>
              </div>
              <div class="balance">
                <small>สิทธิ์คงเหลือ</small><b>{{ course.balance }} ครั้ง</b
                ><span><i :style="{ width: `${(course.balance / course.total) * 100}%` }" /></span>
              </div>
              <div class="course-actions">
                <button class="course-action-button transfer" @click="start('transfer', course)">
                  โอนสิทธิ์</button
                ><button class="course-action-button history" @click="start('history', course)">
                  ดูประวัติ
                </button>
              </div>
            </article>
          </div>
        </div>
        <div v-if="action" class="course-workspace inline-workspace">
          <header class="form-title">
            <h2>{{ title }}</h2>
            <button class="back" @click="goBack">× ปิดแบบฟอร์ม</button>
          </header>
          <div v-if="action === 'transfer'" class="course-form">
            <label>เจ้าของคอร์ส<input :value="`${owner?.fullName ?? ''} HN ${owner?.hn ?? ''}`" readonly /></label
            ><label>คอร์ส<input :value="selectedCourse.name" readonly /></label
            ><label
              >สิทธิ์คงเหลือ<input :value="`${selectedCourse.balance} ครั้ง`" readonly
            /></label>
            <div class="recipient-field">
              <label
                >ผู้รับสิทธิ์<input
                  v-model.trim="recipientSearch"
                  placeholder="ค้นหา HN ชื่อ หรือเบอร์โทรศัพท์"
                  @input="handleRecipientInput"
              /></label>
              <div v-if="recipientSearch && !selectedRecipient" class="recipient-results">
                <button
                  v-for="patient in recipientResults"
                  :key="patient.hn"
                  type="button"
                  @click="selectRecipient(patient)"
                >
                  <b>{{ patient.fullName }}</b
                  ><span>{{ patient.hn }} {{ patient.phone }}</span>
                </button>
                <p v-if="!recipientResults.length">ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา</p>
              </div>
            </div>
            <label
              >จำนวนที่ต้องการโอน<input
                v-model.number="transferAmount"
                type="number"
                min="1"
                :max="selectedCourse.balance"
            /></label>
            <label>วันเริ่มต้น<input value="22 สิงหาคม 2026" readonly /></label>
            <label>วันหมดอายุ<input :value="selectedCourse.expiry" readonly /></label>
          </div>
          <footer v-if="action === 'transfer'" class="course-footer">
            <div class="result">
              <b>ตรวจสอบผลหลังการโอน</b
              ><span
                >เจ้าของเดิม {{ selectedCourse.balance }} →
                {{ selectedCourse.balance - transferAmount }} ครั้ง ผู้รับ
                {{ selectedRecipient?.fullName || 'ยังไม่ได้เลือก' }} ได้รับ
                {{ transferAmount }} ครั้ง ใช้วันหมดอายุเดิม</span
              >
            </div>
            <button
              class="primary"
              :disabled="
                !selectedRecipient || transferAmount < 1 || transferAmount > selectedCourse.balance
              "
              @click="confirm"
            >
              ยืนยันการโอนสิทธิ์
            </button>
          </footer>
          <p v-if="action === 'transfer' && done" class="success">
            บันทึกรายการโอนและประวัติตรวจสอบเรียบร้อยแล้ว
          </p>
          <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
          <div v-if="action === 'history'" class="transfer-history">
            <table class="staff-table">
              <thead>
                <tr>
                  <th>วันที่โอน</th>
                  <th>ผู้โอน</th>
                  <th>ผู้รับ</th>
                  <th>คอร์ส</th>
                  <th>จำนวน</th>
                  <th>ผู้ทำรายการ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in transferHistory" :key="entry.createdAt + entry.quantity">
                  <td>{{ new Date(entry.createdAt).toLocaleString('th-TH') }}</td>
                  <td>{{ entry.sourcePatientId }}</td>
                  <td>{{ entry.destinationPatientId }}</td>
                  <td>{{ selectedCourse?.name }}</td>
                  <td><b>{{ entry.quantity }} ครั้ง</b></td>
                  <td>{{ entry.reason }}</td>
                </tr>
                <tr v-if="!transferHistory.length"><td colspan="6">ไม่พบประวัติการโอน</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style src="../../staff/styles/staff.css"></style>
<style scoped src="../styles/course-components.css"></style>
<style scoped src="../styles/course-page.css"></style>
