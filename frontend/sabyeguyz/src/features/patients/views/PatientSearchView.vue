<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patients } from '../data/mockPatients'

const query = ref('')
const branch = ref('ทั้งหมด')
const sortOrder = ref<'asc' | 'desc'>('asc')
const newPatientHn = ref('')
const showCreationNotice = ref(false)
const branches = [...new Set(patients.map((patient) => patient.branch))]
const filteredPatients = computed(() => {
  const value = query.value.trim().toLowerCase()
  const matchingPatients = patients.filter((patient) => {
    const matchesQuery =
      !value ||
      [patient.hn, patient.fullName, patient.phone].some((field) =>
        field.toLowerCase().includes(value),
      )
    const matchesBranch = branch.value === 'ทั้งหมด' || patient.branch === branch.value
    return matchesQuery && matchesBranch
  }).sort((left, right) => {
    const comparison = new Intl.Collator('th').compare(left.fullName, right.fullName)
    return sortOrder.value === 'asc' ? comparison : -comparison
  })
  if (!newPatientHn.value) return matchingPatients
  return matchingPatients.sort((left, right) => {
    if (left.hn === newPatientHn.value) return -1
    if (right.hn === newPatientHn.value) return 1
    return 0
  })
})
onMounted(() => {
  const createdHn = localStorage.getItem('patientCreationHn')
  if (!createdHn) return
  newPatientHn.value = createdHn
  localStorage.removeItem('patientCreationHn')
  showCreationNotice.value = true
  window.setTimeout(() => { showCreationNotice.value = false }, 3000)
})
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
          <select v-model="branch" class="branch-filter" aria-label="กรองตามสาขา">
            <option value="ทั้งหมด">สาขาทั้งหมด</option>
            <option v-for="item in branches" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="sortOrder" class="sort-filter" aria-label="เรียงลำดับผู้ป่วย">
            <option value="asc">ชื่อ ก-ฮ</option>
            <option value="desc">ชื่อ ฮ-ก</option>
          </select>
          <span class="result-count">พบผู้ป่วยทั้งหมด {{ filteredPatients.length }} ราย</span>
        </header>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>เลขประจำตัวผู้ป่วย</th>
                <th>ชื่อผู้ป่วย</th>
                <th>เบอร์โทรศัพท์</th>
                <th>วันเกิด</th>
                <th>สาขา</th>
                <th aria-label="การดำเนินการ"></th>
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
                <td>{{ patient.birthDate }}</td>
                <td>{{ patient.branch }}</td>
                <td>
                  <RouterLink class="row-action" :to="`/staff/patients/${patient.hn}`">ดูข้อมูล</RouterLink>
                </td>
              </tr>
              <tr v-if="filteredPatients.length === 0">
                <td class="empty" colspan="6">ไม่พบผู้ป่วย</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
    <Transition name="snackbar">
      <div v-if="showCreationNotice" class="creation-snackbar" role="status">
        เพิ่มผู้ป่วยใหม่แล้ว
      </div>
    </Transition>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
