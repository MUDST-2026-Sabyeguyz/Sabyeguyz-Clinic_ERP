<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getPatientProfile, type PatientProfile } from '@/api/patients'

const route = useRoute()
const patient = ref<PatientProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const notFound = ref(false)

function display(value: string | null | undefined) {
  return value || 'ไม่ระบุ'
}

function formatDate(value: string | null) {
  if (!value) return 'ไม่ระบุ'
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString('th-TH', { year: 'numeric', month: 'long', day: 'numeric' })
}

async function loadProfile() {
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id < 1) {
    notFound.value = true
    loading.value = false
    return
  }
  try {
    patient.value = await getPatientProfile(id)
  } catch (error) {
    if (error instanceof Error && error.message.includes(`ไม่พบผู้ป่วย id ${id}`))
      notFound.value = true
    else
      errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาดในการโหลดข้อมูล'
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadProfile())
</script>

<template>
  <section class="patient-page profile-page">
    <RouterLink class="back-link" to="/patients">← กลับไปหน้าค้นหาผู้ป่วย</RouterLink>
    <div v-if="loading" class="state-card" aria-live="polite">กำลังโหลดข้อมูลผู้ป่วย...</div>
    <div v-else-if="notFound" class="state-card empty-state" role="alert">
      <span class="state-icon">?</span>
      <h1>ไม่พบผู้ป่วย</h1>
      <p>ไม่พบข้อมูลผู้ป่วยที่ต้องการ</p>
    </div>
    <div v-else-if="errorMessage" class="state-card error" role="alert">{{ errorMessage }}</div>
    <template v-else-if="patient">
      <header class="profile-header">
        <div>
          <p class="eyebrow">PATIENT PROFILE</p>
          <h1>{{ patient.title ? `${patient.title} ` : '' }}{{ patient.fullName }}</h1>
          <p class="profile-hn">HN {{ patient.hn }}</p>
        </div>
        <span class="status-badge" :class="{ inactive: !patient.active }">{{
          patient.active ? 'กำลังใช้งาน' : 'ไม่ใช้งาน'
        }}</span>
      </header>

      <div class="profile-grid">
        <article class="profile-card">
          <h2>ข้อมูลส่วนตัว</h2>
          <dl>
            <div>
              <dt>ชื่อ-นามสกุล</dt>
              <dd>{{ display(patient.fullName) }}</dd>
            </div>
            <div>
              <dt>วันเกิด</dt>
              <dd>{{ formatDate(patient.birthDate) }}</dd>
            </div>
            <div>
              <dt>เพศ</dt>
              <dd>{{ display(patient.gender) }}</dd>
            </div>
            <div>
              <dt>เบอร์โทรศัพท์</dt>
              <dd>{{ display(patient.phone) }}</dd>
            </div>
            <div>
              <dt>{{ display(patient.identityDocumentType) }}</dt>
              <dd>{{ display(patient.identityNumber) }}</dd>
            </div>
            <div class="full-width">
              <dt>ที่อยู่</dt>
              <dd>{{ display(patient.address) }}</dd>
            </div>
          </dl>
        </article>
        <article class="profile-card">
          <h2>สาขา</h2>
          <dl>
            <div>
              <dt>ชื่อสาขา</dt>
              <dd>{{ display(patient.branch.name) }}</dd>
            </div>
            <div>
              <dt>รหัสสาขา</dt>
              <dd>{{ display(patient.branch.code) }}</dd>
            </div>
            <div>
              <dt>สถานะสาขา</dt>
              <dd>{{ patient.branch.active ? 'กำลังใช้งาน' : 'ไม่ใช้งาน' }}</dd>
            </div>
          </dl>
        </article>
        <article class="profile-card">
          <h2>ประวัติสุขภาพ</h2>
          <dl>
            <div>
              <dt>แพ้ยา</dt>
              <dd>{{ patient.drugAllergy ? 'มี' : 'ไม่มี' }}</dd>
            </div>
            <div>
              <dt>แพ้อาหาร</dt>
              <dd>{{ patient.foodAllergy ? 'มี' : 'ไม่มี' }}</dd>
            </div>
            <div class="full-width">
              <dt>โรคประจำตัว</dt>
              <dd>{{ display(patient.chronicDisease) }}</dd>
            </div>
          </dl>
        </article>
        <article class="profile-card">
          <h2>ผู้ติดต่อฉุกเฉิน</h2>
          <dl>
            <div>
              <dt>ชื่อ</dt>
              <dd>{{ display(patient.emergencyContactName) }}</dd>
            </div>
            <div>
              <dt>ความสัมพันธ์</dt>
              <dd>{{ display(patient.emergencyContactRelationship) }}</dd>
            </div>
            <div>
              <dt>เบอร์โทรศัพท์</dt>
              <dd>{{ display(patient.emergencyContactPhone) }}</dd>
            </div>
          </dl>
        </article>
      </div>
    </template>
  </section>
</template>
