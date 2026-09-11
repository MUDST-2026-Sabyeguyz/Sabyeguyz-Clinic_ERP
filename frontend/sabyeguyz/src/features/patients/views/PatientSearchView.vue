<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patientApi, type Patient } from '@/api/client'

const query = ref('')
const patients = ref<Patient[]>([])
const loading = ref(false)
const errorMessage = ref('')
const filteredPatients = computed(() => patients.value)
async function loadPatients() {
  loading.value = true
  errorMessage.value = ''
  try { patients.value = (await patientApi.search(query.value)).items }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ' }
  finally { loading.value = false }
}
onMounted(loadPatients)
let searchTimer: number | undefined
watch(query, () => { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(loadPatients, 250) })
</script>

<template>
  <div class="patient-workspace">
    <StaffHeader />
    <main>
      <section class="page-heading">
        <div>
          <h1>ค้นหาผู้ป่วย</h1>
          <p>ค้นหาด้วยชื่อหรือเลขประจำตัวผู้ป่วย</p>
          <RouterLink class="primary" to="/staff/patients/new">+ ลงทะเบียนผู้ป่วยใหม่</RouterLink>
        </div>
      </section>

      <section class="directory">
        <header>
          <div class="search">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input
              v-model="query"
              aria-label="ค้นหาผู้ป่วย"
              placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
            />
          </div>
          <span class="result-count">พบผู้ป่วยทั้งหมด {{ filteredPatients.length }} ราย</span>
        </header>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>เลขประจำตัวผู้ป่วย</th>
                <th>ชื่อผู้ป่วย</th>
                <th>เบอร์โทรศัพท์</th>
                <th>สถานะ</th>
                <th>การดำเนินการ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="patient in filteredPatients" :key="patient.hn">
                <td class="patient-id">
                  <span class="patient-avatar">{{ patient.fullName.slice(0, 1) }}</span>
                  <b>{{ patient.hn }}</b>
                </td>
                <td>{{ patient.fullName }}</td>
                <td>{{ patient.phone }}</td>
                <td>{{ patient.active ? 'ใช้งาน' : 'ปิดใช้งาน' }}</td>
                <td>
                  <RouterLink class="row-action" :to="`/staff/patients/${patient.id}`"
                    >ดูข้อมูล</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="loading"><td class="empty" colspan="6">กำลังโหลดข้อมูล...</td></tr>
              <tr v-if="!loading && filteredPatients.length === 0">
                <td class="empty" colspan="6">{{ errorMessage || 'ไม่พบผู้ป่วย' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
