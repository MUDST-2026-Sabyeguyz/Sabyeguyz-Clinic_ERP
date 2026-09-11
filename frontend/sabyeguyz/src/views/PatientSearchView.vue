<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { searchPatients, type PatientSearchItem } from '@/api/patients'

const query = ref('')
const patients = ref<PatientSearchItem[]>([])
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const hasSearched = ref(false)
const pageSize = 20

async function loadPatients(nextPage = 0) {
  loading.value = true
  errorMessage.value = ''
  hasSearched.value = true
  try {
    const result = await searchPatients(query.value.trim(), nextPage, pageSize)
    patients.value = result.items
    page.value = result.page
    totalPages.value = result.totalPages
    totalItems.value = result.totalItems
  } catch (error) {
    patients.value = []
    errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาดในการค้นหา'
  } finally {
    loading.value = false
  }
}

function submit() {
  void loadPatients()
}

onMounted(() => void loadPatients())
</script>

<template>
  <section class="patient-page">
    <div class="patient-page-header">
      <div>
        <p class="eyebrow">PATIENTS</p>
        <h1>ค้นหาผู้ป่วย</h1>
        <p class="page-subtitle">
          ค้นหาด้วย HN ชื่อ-นามสกุล เบอร์โทรศัพท์ หรือเลขเอกสารยืนยันตัวตน
        </p>
      </div>
      <RouterLink class="primary patient-header-action" to="/patients/register"
        >ลงทะเบียนผู้ป่วยใหม่</RouterLink
      >
    </div>

    <form class="patient-search-form" role="search" @submit.prevent="submit">
      <label for="patient-query">ค้นหาข้อมูลผู้ป่วย</label>
      <div class="patient-search-controls">
        <input
          id="patient-query"
          v-model="query"
          type="search"
          placeholder="เช่น HN, ชื่อ, เบอร์โทรศัพท์ หรือเลขบัตรประชาชน"
        />
        <button class="primary" type="submit" :disabled="loading">
          {{ loading ? 'กำลังค้นหา...' : 'ค้นหา' }}
        </button>
      </div>
    </form>

    <p v-if="errorMessage" class="state-card error" role="alert">{{ errorMessage }}</p>
    <div v-else-if="loading" class="state-card" aria-live="polite">กำลังโหลดข้อมูลผู้ป่วย...</div>
    <div v-else-if="hasSearched && patients.length === 0" class="state-card empty-state">
      <span class="state-icon">⌕</span>
      <h2>ไม่พบข้อมูลผู้ป่วย</h2>
      <p>ลองตรวจสอบคำค้นหาอีกครั้ง หรือเลือกลงทะเบียนผู้ป่วยใหม่</p>
    </div>
    <template v-else>
      <div class="result-summary" aria-live="polite">พบผู้ป่วย {{ totalItems }} ราย</div>
      <div class="patient-results">
        <RouterLink
          v-for="patient in patients"
          :key="patient.id"
          class="patient-result-card"
          :to="`/patients/${patient.id}`"
        >
          <div>
            <span class="patient-hn">{{ patient.hn }}</span>
            <h2>{{ patient.fullName }}</h2>
            <p>{{ patient.phone || 'ไม่ระบุเบอร์โทรศัพท์' }}</p>
          </div>
          <span class="result-arrow" aria-hidden="true">→</span>
        </RouterLink>
      </div>
      <nav v-if="totalPages > 1" class="pagination" aria-label="หน้าผลการค้นหา">
        <button class="secondary" :disabled="page === 0 || loading" @click="loadPatients(page - 1)">
          ก่อนหน้า
        </button>
        <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
        <button
          class="secondary"
          :disabled="page + 1 >= totalPages || loading"
          @click="loadPatients(page + 1)"
        >
          ถัดไป
        </button>
      </nav>
    </template>
  </section>
</template>
