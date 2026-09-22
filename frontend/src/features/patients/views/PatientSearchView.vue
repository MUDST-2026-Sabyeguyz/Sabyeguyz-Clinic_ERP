<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { branchApi, patientApi, type Branch, type Patient } from '@/api/client'

const query = ref('')
const branch = ref('ทั้งหมด')
const sortOrder = ref<'asc' | 'desc' | 'newest'>('asc')
const newPatientHn = ref('')
const showCreationNotice = ref(false)
const patients = ref<Patient[]>([])
const branches = ref<Branch[]>([])
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const filteredPatients = computed(() => {
  return [...patients.value].sort((left, right) => {
    if (sortOrder.value === 'newest') return right.id - left.id
    const comparison = new Intl.Collator('th').compare(left.fullName, right.fullName)
    return sortOrder.value === 'asc' ? comparison : -comparison
  })
})
async function loadPatients() {
  loading.value = true
  errorMessage.value = ''
  try {
    const sort = sortOrder.value === 'asc' ? 'nameAsc' : sortOrder.value === 'desc' ? 'nameDesc' : 'newest'
    const result = await patientApi.search(query.value.trim(), page.value, 20, branch.value === 'ทั้งหมด' ? '' : branch.value, sort)
    patients.value = result.items
    totalPages.value = result.totalPages
    totalItems.value = result.totalItems
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
async function changePage(nextPage: number) { if (nextPage >= 0 && nextPage < totalPages.value) { page.value = nextPage; await loadPatients() } }
let searchTimer: number | undefined
function refreshSearch() { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(() => { page.value = 0; void loadPatients() }, 250) }
onMounted(() => {
  const createdHn = localStorage.getItem('patientCreationHn')
  if (!createdHn) return
  newPatientHn.value = createdHn
  localStorage.removeItem('patientCreationHn')
  showCreationNotice.value = true
  window.setTimeout(() => { showCreationNotice.value = false }, 3000)
})
onMounted(async () => {
  branches.value = (await branchApi.list()).filter((item) => item.active)
  await loadPatients()
})
watch(branch, () => { page.value = 0; void loadPatients() })
watch(sortOrder, () => { page.value = 0; void loadPatients() })
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
              @input="refreshSearch"
              aria-label="ค้นหาผู้ป่วย"
              placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
            />
          </div>
          <select v-model="branch" class="branch-filter" aria-label="กรองตามสาขา">
            <option value="ทั้งหมด">สาขาทั้งหมด</option>
            <option v-for="item in branches" :key="item.code" :value="item.code">{{ item.name }}</option>
          </select>
          <select v-model="sortOrder" class="sort-filter" aria-label="เรียงลำดับผู้ป่วย">
            <option value="asc">ชื่อ ก-ฮ</option>
            <option value="desc">ชื่อ ฮ-ก</option>
            <option value="newest">เพิ่มล่าสุด</option>
          </select>
          <span class="result-count">พบผู้ป่วยทั้งหมด {{ totalItems }} ราย</span>
        </header>
        <div class="table-scroll">
          <div v-if="loading" class="empty">กำลังโหลดข้อมูลผู้ป่วย...</div>
          <div v-else-if="errorMessage" class="empty">{{ errorMessage }}</div>
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
              <tr v-for="patient in filteredPatients" :key="patient.id">
                <td class="patient-id">
                  <span class="patient-avatar">{{ patient.fullName.slice(0, 1) }}</span>
                  <b>{{ patient.hn }}</b>
                </td>
                <td>{{ patient.fullName }}</td>
                <td>{{ patient.phone }}</td>
                <td>-</td>
                <td>{{ patient.branchName ?? patient.branchCode ?? '-' }}</td>
                <td>
                  <RouterLink class="row-action" :to="`/staff/patients/${patient.id}`">ดูข้อมูล</RouterLink>
                </td>
              </tr>
              <tr v-if="filteredPatients.length === 0">
                <td class="empty" colspan="6">ไม่พบผู้ป่วย</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="totalPages > 1" class="patient-pagination" aria-label="เปลี่ยนหน้ารายชื่อผู้ป่วย">
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">← ก่อนหน้า</button>
          <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">ถัดไป →</button>
        </nav>
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
