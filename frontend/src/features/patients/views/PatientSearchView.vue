<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { branchApi, patientApi, type Branch, type Patient } from '@/api/client'

const query = ref('')
const branch = ref('ทั้งหมด')
const sortOrder = ref('asc')
const sortOptions = [
  { value: 'desc', label: 'ชื่อ ฮ-ก' },
  { value: 'newest', label: 'เพิ่มล่าสุด' },
]
const newPatientHn = ref('')
const showCreationNotice = ref(false)
const patients = ref<Patient[]>([])
const patientBirthDates = ref<Record<number, string>>({})
const branches = ref<Branch[]>([])
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const thaiDate = (value?: string) =>
  value ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'long' }).format(new Date(value)) : '-'
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
    const sort =
      sortOrder.value === 'asc' ? 'nameAsc' : sortOrder.value === 'desc' ? 'nameDesc' : 'newest'
    const result = await patientApi.search(
      query.value.trim(),
      page.value,
      20,
      branch.value === 'ทั้งหมด' ? '' : branch.value,
      sort,
    )
    patients.value = result.items
    const profiles = await Promise.allSettled(result.items.map((item) => patientApi.get(item.id)))
    patientBirthDates.value = Object.fromEntries(
      profiles.flatMap((profile, index) =>
        profile.status === 'fulfilled' ? [[result.items[index]!.id, profile.value.birthDate]] : [],
      ),
    )
    totalPages.value = result.totalPages
    totalItems.value = result.totalItems
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
async function changePage(nextPage: number) {
  if (nextPage >= 0 && nextPage < totalPages.value) {
    page.value = nextPage
    await loadPatients()
  }
}
let searchTimer: number | undefined
function refreshSearch() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 0
    void loadPatients()
  }, 250)
}
onMounted(() => {
  const createdHn = localStorage.getItem('patientCreationHn')
  if (!createdHn) return
  newPatientHn.value = createdHn
  localStorage.removeItem('patientCreationHn')
  showCreationNotice.value = true
  window.setTimeout(() => {
    showCreationNotice.value = false
  }, 1800)
})
onMounted(async () => {
  branches.value = (await branchApi.list()).filter((item) => item.active)
  await loadPatients()
})
watch(branch, () => {
  page.value = 0
  void loadPatients()
})
watch(sortOrder, () => {
  page.value = 0
  void loadPatients()
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
              @input="refreshSearch"
              aria-label="ค้นหาผู้ป่วย"
              placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
            />
          </div>
          <FilterDropdown
            v-model="branch"
            class="branch-filter"
            label="กรองตามสาขา"
            default-value="ทั้งหมด"
            default-label="สาขาทั้งหมด"
            :options="branches.map((item) => ({ value: item.branchCode, label: item.name }))"
          />
          <FilterDropdown
            v-model="sortOrder"
            class="sort-filter"
            label="เรียงลำดับผู้ป่วย"
            default-value="asc"
            default-label="ชื่อ ก-ฮ"
            :clear-on-reselect="false"
            :options="sortOptions"
          />
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
                <td>{{ thaiDate(patientBirthDates[patient.id]) }}</td>
                <td>{{ patient.branchName ?? patient.branchCode ?? '-' }}</td>
                <td>
                  <RouterLink class="row-action" :to="`/staff/patients/${patient.id}`"
                    >ดูข้อมูล</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="filteredPatients.length === 0">
                <td class="empty" colspan="6">ไม่พบผู้ป่วย</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          v-if="totalPages > 1"
          class="patient-pagination"
          aria-label="เปลี่ยนหน้ารายชื่อผู้ป่วย"
        >
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">
            ← ก่อนหน้า
          </button>
          <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
            ถัดไป →
          </button>
        </nav>
      </section>
    </main>
    <Teleport to="body">
      <Transition name="save-success">
        <div v-if="showCreationNotice" class="patient-save-success-backdrop" role="status">
          <section class="patient-save-success">ลงทะเบียนผู้ป่วยแล้ว</section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
