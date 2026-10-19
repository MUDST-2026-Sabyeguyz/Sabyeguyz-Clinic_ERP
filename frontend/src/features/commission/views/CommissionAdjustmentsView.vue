<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const adjustments = ref<ManagerAdjustment[]>([])
const loading = ref(true)
const errorMessage = ref('')
const search = ref('')
const month = ref('')
const status = ref('')

const monthLabel = (value: string) => {
  const [year, number] = value.split('-').map(Number)
  if (!year || !number) return value
  return new Intl.DateTimeFormat('th-TH', { month: 'short', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
}
const amount = (value: number) =>
  `${value > 0 ? '+' : '-'}฿${Math.abs(value).toLocaleString('th-TH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
const reference = (row: ManagerAdjustment) => {
  if (row.saleTransactionId) return `SALE-${String(row.saleTransactionId).padStart(5, '0')}`
  if (row.courseId) return `COURSE-${String(row.courseId).padStart(5, '0')}`
  if (row.visitId) return `VISIT-${String(row.visitId).padStart(5, '0')}`
  if (row.singleVisitFeeId) return `FEE-${String(row.singleVisitFeeId).padStart(5, '0')}`
  return `ADJ-${String(row.id).padStart(5, '0')}`
}
const statusLabel = (value: string) => (value === 'Applied' ? 'ดำเนินการ' : 'รอดำเนินการ')
const visible = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return adjustments.value.filter(
    (row) =>
      (!keyword ||
        row.staffName.toLowerCase().includes(keyword) ||
        reference(row).toLowerCase().includes(keyword)) &&
      (!month.value || row.originalMonth === month.value) &&
      (!status.value || statusLabel(row.status) === status.value),
  )
})
const monthOptions = computed(() =>
  [...new Set(adjustments.value.map((row) => row.originalMonth))].sort().reverse(),
)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    adjustments.value = await commissionApi.adjustments()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการปรับปรุงไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page adjustment-page">
    <StaffHeader />
    <main class="commission-manager-main adjustment-main">
      <header class="adjustment-title">
        <div>
          <RouterLink class="back-to-overview" to="/staff/commission">← กลับหน้าแรก</RouterLink>
          <h1>รายการปรับปรุงค่าตอบแทน</h1>
          <p>จัดการผลต่างค่าตอบแทนหลัง Finalized · เลือกรายการเพื่อดูรายละเอียด</p>
        </div>
        <RouterLink class="commission-primary add-adjustment" to="/staff/commission/adjustments/new"
          >+ เพิ่มรายการปรับปรุง</RouterLink
        >
      </header>

      <section class="adjustment-filters" aria-label="ตัวกรองรายการปรับปรุง">
        <label>
          <span class="sr-only">ค้นหารายการ</span>
          <input v-model="search" placeholder="ค้นหาชื่อพนักงานหรือรหัสอ้างอิง" />
        </label>
        <FilterDropdown
          v-model="month"
          label="งวดต้นทาง"
          default-label="งวดต้นทาง: ทุกงวด"
          :options="monthOptions.map((item) => ({ value: item, label: `งวดต้นทาง: ${monthLabel(item)}` }))"
        />
        <FilterDropdown
          v-model="status"
          label="สถานะ Adjustment"
          default-label="สถานะ: Adjustment ทั้งหมด"
          :options="[
            { value: 'รอดำเนินการ', label: 'รอดำเนินการ' },
            { value: 'ดำเนินการ', label: 'ดำเนินการ' },
          ]"
        />
      </section>

      <p v-if="loading" class="state-card">กำลังโหลดรายการปรับปรุง...</p>
      <p v-else-if="errorMessage" class="state-card error" role="alert">{{ errorMessage }}</p>
      <section v-else class="adjustment-table-card">
        <h2>รายการ Adjustment ทั้งหมด</h2>
        <div class="adjustment-table-wrap">
          <table>
            <thead>
              <tr>
                <th>พนักงาน</th>
                <th>รายการอ้างอิง</th>
                <th>งวดต้นทาง</th>
                <th>สถานะรอบ</th>
                <th>ยอด Adjustment</th>
                <th>สถานะ Adjustment</th>
                <th><span class="sr-only">การทำงาน</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visible" :key="row.id">
                <td>{{ row.staffName }}</td>
                <td>{{ reference(row) }}</td>
                <td>{{ monthLabel(row.originalMonth) }}</td>
                <td>Paid / Finalized</td>
                <td :class="{ deduction: row.amount < 0 }">{{ amount(row.amount) }}</td>
                <td>{{ statusLabel(row.status) }}</td>
                <td><RouterLink class="detail-button" :to="{ name: 'commission-adjustment-detail', params: { id: row.id } }">ดูรายละเอียด</RouterLink></td>
              </tr>
              <tr v-if="visible.length === 0">
                <td colspan="7" class="empty-row">ยังไม่มีรายการ Adjustment ตามเงื่อนไขที่เลือก</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="adjustment-note">ยอดคงเหลือคือยอดที่ยังไม่ได้บันทึกดำเนินการ ไม่ใช่ยอดที่ระบบจะหักเงินจริงอัตโนมัติ</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.adjustment-main { padding-top: 34px; }
.adjustment-title { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.back-to-overview { display: inline-flex; margin-bottom: 8px; color: #397b74; font-size: .9rem; font-weight: 700; text-decoration: none; }
.back-to-overview:hover { color: #1e8b86; text-decoration: underline; }
.adjustment-title h1 { margin: 0; font-size: clamp(1.75rem, 2.7vw, 2.25rem); }
.adjustment-title p { margin: 7px 0 0; color: #667a72; }
.adjustment-title .commission-primary { min-height: 42px; font-size: .9rem; white-space: nowrap; }
.add-adjustment { display: inline-flex; align-items: center; text-decoration: none; }
.adjustment-filters { display: grid; grid-template-columns: 1.55fr 1fr 1.1fr; gap: 16px; margin: 20px 0 18px; }
.adjustment-filters input { width: 100%; min-height: 46px; box-sizing: border-box; padding: 0 14px; border: 1px solid #b7cecc; border-radius: 10px; background: #fff; color: #3f514a; font: inherit; font-size: .87rem; }
.adjustment-table-card { padding: 16px; border: 1px solid #e0e6e2; border-radius: 10px; background: #fff; }
.adjustment-table-card h2 { margin: 0 0 14px; font-size: 1.05rem; }
.adjustment-table-wrap { overflow-x: auto; }
table { width: 100%; min-width: 920px; border-collapse: collapse; font-size: .82rem; }
th { padding: 12px 14px; background: #eff6f2; color: #4e6159; text-align: left; font-weight: 700; }
td { padding: 15px 14px; border-bottom: 1px solid #edf0ee; color: #374840; }
tbody tr:last-child td { border-bottom: 0; }
.deduction { color: #bd514c; font-weight: 700; }
.detail-button { display: inline-flex; border: 0; border-radius: 5px; padding: 7px 13px; background: #e7f3ef; color: #31736b; font: inherit; font-size: .78rem; text-decoration: none; }
.empty-row { padding: 32px; color: #667a72; text-align: center; }
.adjustment-note { margin: 12px 0 0; color: #77867f; font-size: .78rem; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 900px) { .adjustment-filters { grid-template-columns: 1fr 1fr; } }
@media (max-width: 620px) { .adjustment-main { padding-top: 26px; } .adjustment-title { align-items: flex-start; flex-direction: column; } .adjustment-filters { grid-template-columns: 1fr; } .adjustment-title .commission-primary { width: 100%; } }
</style>
