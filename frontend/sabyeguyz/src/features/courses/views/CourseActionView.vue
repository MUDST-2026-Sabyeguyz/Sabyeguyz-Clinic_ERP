<script setup lang="ts">
import { computed, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patients, type PatientProfile } from '@/features/patients/data/mockPatients'
import { patientCourses, type PatientCourse } from '../data/mockCourses'

type CourseAction = 'transfer' | 'history'
const action = ref<CourseAction | null>(null)
const done = ref(false)
const recipientSearch = ref('')
const selectedRecipient = ref<PatientProfile | null>(null)
const transferAmount = ref(1)
const selectedCourse = ref<PatientCourse>(patientCourses[0]!)
const recipientResults = computed(() => {
  const term = recipientSearch.value.trim().toLowerCase()
  if (!term) return []
  return patients.filter(
    (patient) =>
      patient.fullName !== 'สมชาย ใจดี' &&
      `${patient.hn} ${patient.fullName} ${patient.phone}`.toLowerCase().includes(term),
  )
})
const title = computed(() => {
  if (action.value === 'transfer') return 'โอนสิทธิ์คอร์ส'
  if (action.value === 'history') return 'ประวัติการโอนคอร์ส'
  return 'จัดการคอร์ส'
})
function start(nextAction: CourseAction, course: PatientCourse) {
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
function confirm() {
  done.value = true
}
function selectRecipient(patient: PatientProfile) {
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
            <span class="avatar">ส</span>
            <div>
              <small>เจ้าของคอร์สที่เลือก</small><b>สมชาย ใจดี</b>
              <p>2601080001 สาขาราชพฤกษ์</p>
            </div>
          </div>
          <div class="course-list">
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
            <label>เจ้าของคอร์ส<input value="สมชาย ใจดี HN 2601080001" readonly /></label
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
                <tr>
                  <td>21 ส.ค. 2026 14:32</td>
                  <td>สมชาย ใจดี</td>
                  <td>วรรณา มั่นคง</td>
                  <td>Manual Therapy 10 ครั้ง</td>
                  <td><b>2 ครั้ง</b></td>
                  <td>เจ้าหน้าที่คลินิก</td>
                </tr>
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
