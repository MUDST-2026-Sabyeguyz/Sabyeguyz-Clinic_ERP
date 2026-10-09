<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { commissionApi } from '@/api/client'

type TransactionType = 'Sales' | 'Treatment'
type TransactionRow = {
  id: string
  courseCode: string
  date: string
  branch: string
  patient: string
  staff: string
  rule: string
  amount: number
  commission: number
}

const route = useRoute()
const type = ref<TransactionType>(route.query.type === 'Treatment' ? 'Treatment' : 'Sales')
const router = useRouter()
const branch = ref('ทุกสาขา')
const today = new Date()
const defaultEndDate = today.toISOString().slice(0, 10)
const defaultStartDate = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 30)
  .toISOString()
  .slice(0, 10)
const startDate = ref(defaultStartDate)
const endDate = ref(defaultEndDate)
const search = ref('')
const page = ref(1)
const rows = ref<TransactionRow[]>([])
const errorMessage = ref('')
const pageSize = 8

const period = computed(() =>
  startDate.value && endDate.value ? { from: startDate.value, to: endDate.value } : null,
)

async function loadReport() {
  if (!period.value) return
  try {
    if (type.value === 'Treatment') {
      const data = await commissionApi.treatmentReport(period.value.from, period.value.to)
      rows.value = data.map((row) => ({
        id: `TXN-${String(row.id).padStart(6, '0')}`,
        courseCode: row.courseCode,
        date: row.visitDate,
        branch: row.branchName,
        patient: row.patientName,
        staff: row.treatingStaffName || '-',
        rule: `ค่ามือรักษา ${row.gross ? Math.round((row.treatmentFee / row.gross) * 100) : 0}%`,
        amount: Number(row.gross),
        commission: Number(row.treatmentFee),
      }))
    } else {
      const data = await commissionApi.report(period.value.from, period.value.to)
      rows.value = data.map((row, index) => ({
        id: `TXN-${String(index + 1).padStart(6, '0')}`,
        courseCode: '-',
        date: period.value?.from ?? '',
        branch: row.branchName,
        patient: '-',
        staff: row.staffName || '-',
        rule: `${row.rate * 100}%`,
        amount: Number(row.base),
        commission: Number(row.commission),
      }))
    }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลรายการไม่สำเร็จ'
  }
}

onMounted(loadReport)
watch([type, startDate, endDate], () => {
  page.value = 1
  loadReport()
})
const branches = computed(() => [
  'ทุกสาขา',
  ...Array.from(new Set(rows.value.map((row) => row.branch).filter(Boolean))).sort(),
])
const visible = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return rows.value.filter(
    (row) =>
      (branch.value === 'ทุกสาขา' || row.branch === branch.value) &&
      (!keyword ||
        [row.id, row.staff, row.branch].some((value) => value.toLowerCase().includes(keyword))),
  )
})
const pageCount = computed(() => Math.max(1, Math.ceil(visible.value.length / pageSize)))
const pagedRows = computed(() =>
  visible.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)
const totalCommission = computed(() => visible.value.reduce((sum, row) => sum + row.commission, 0))
const totalAmount = computed(() => visible.value.reduce((sum, row) => sum + row.amount, 0))
const staffCount = computed(
  () => new Set(visible.value.map((row) => row.staff).filter(Boolean)).size,
)
const currency = (value: number) => `฿${value.toLocaleString()}`

function selectType(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'Closing') {
    void router.push('/staff/commission/closing')
    return
  }
  type.value = value as TransactionType
}
</script>

<template>
  <div class="transaction-page">
    <StaffHeader />
    <main class="transaction-main">
      <header class="page-title">
        <h1>{{ type }} Transaction</h1>
        <p>ตรวจสอบข้อมูลจาก Treatment และ Sales Transaction</p>
      </header>
      <section class="summary-cards" aria-label="สรุปรายการ">
        <article>
          <span>รายการที่แสดง</span><b>{{ visible.length }} รายการ</b>
        </article>
        <article>
          <span>{{ type === 'Treatment' ? 'มูลค่าการรักษารวม' : 'ค่ามือคอมมิชชันรวม' }}</span
          ><b>{{ currency(type === 'Treatment' ? totalAmount : totalCommission) }}</b>
        </article>
        <article>
          <span>{{ type === 'Treatment' ? 'ค่ามือรวม' : 'ค่าคอมมิชชันรวม' }}</span
          ><b>{{ currency(type === 'Treatment' ? totalCommission : totalAmount) }}</b>
        </article>
        <article>
          <span>{{ type === 'Treatment' ? 'สาขา' : 'พนักงานขายที่ขายได้' }}</span
          ><b>{{ type === 'Treatment' ? branch : `${staffCount} คน` }}</b>
        </article>
      </section>
      <section class="transaction-content">
        <label class="type-picker"
          ><span>เลือกประเภท</span
          ><select :value="type" @change="selectType">
            <option value="Sales">Sales Transaction</option>
            <option value="Treatment">Treatment Transaction</option>
            <option value="Closing">ปิดรอบรายเดือน</option>
          </select></label
        >
        <div class="filters">
          <label class="search-field"
            ><span class="sr-only">ค้นหา</span
            ><svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m21 21-4.35-4.35m2.35-5.15a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z" /></svg
            ><input v-model="search" type="search" placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
          /></label>
          <div class="date-range" aria-label="เลือกช่วงวันที่">
            <ThaiDatePicker v-model="startDate" show-calendar-icon numeric-month hide-caret />
            <span>ถึง</span>
            <ThaiDatePicker v-model="endDate" show-calendar-icon numeric-month hide-caret />
          </div>
          <div class="branch-field">
            <FilterDropdown
              v-model="branch"
              label="สาขา"
              default-value="ทุกสาขา"
              default-label="ทุกสาขา"
              :options="
                branches
                  .filter((item) => item !== 'ทุกสาขา')
                  .map((item) => ({ value: item, label: item }))
              "
            />
          </div>
        </div>
      </section>
      <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>{{ type === 'Treatment' ? 'รหัสคอร์ส' : 'เลขที่รายการ' }}</th>
              <th>วันที่ทำรายการ</th>
              <th>สาขา</th>
              <th>{{ type === 'Sales' ? 'ผู้ขาย' : 'ผู้ป่วย' }}</th>
              <th>{{ type === 'Sales' ? 'เกณฑ์คอมมิชชัน' : 'นักกายภาพบำบัด' }}</th>
              <th>{{ type === 'Sales' ? 'จำนวนเงินที่ขาย' : 'ค่ามือ' }}</th>
              <th>{{ type === 'Sales' ? 'ค่าคอมมิชชัน' : 'กติกาค่ามือ' }}</th>
              <th v-if="type === 'Treatment'">มูลค่าบริการ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedRows" :key="row.id">
              <td>{{ type === 'Treatment' ? row.courseCode : row.id }}</td>
              <td>{{ row.date }}</td>
              <td>{{ row.branch }}</td>
              <td>{{ type === 'Treatment' ? row.patient : row.staff }}</td>
              <td>{{ type === 'Treatment' ? row.staff : row.rule }}</td>
              <td>{{ currency(type === 'Treatment' ? row.commission : row.amount) }}</td>
              <td>{{ type === 'Treatment' ? row.rule : currency(row.commission) }}</td>
              <td v-if="type === 'Treatment'">{{ currency(row.amount) }}</td>
            </tr>
            <tr v-if="!pagedRows.length">
              <td :colspan="type === 'Treatment' ? 8 : 7" class="empty-row">
                ไม่พบข้อมูลตามตัวกรอง
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav class="pagination" aria-label="เปลี่ยนหน้า">
        <button type="button" :disabled="page === 1" @click="page--">← ก่อนหน้า</button
        ><span>หน้า {{ page }} จาก {{ pageCount }}</span
        ><button type="button" :disabled="page === pageCount" @click="page++">ถัดไป →</button>
      </nav>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.transaction-page {
  min-height: 100vh;
  background: #f9f4ee;
}
.transaction-main {
  width: min(1420px, calc(100% - 78px));
  margin: 0 auto;
  padding: 52px 0 48px;
}
.page-title h1 {
  margin: 0;
  color: #25372e;
  font-size: 46px;
  font-weight: 600;
  letter-spacing: 4.4px;
}
.page-title p {
  margin: 5px 0 52px;
  color: #56665d;
  font-size: 19px;
}
.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  overflow: hidden;
  margin: 0 0 54px;
  border-radius: 21px;
  background: #fff;
  box-shadow: 0 4px 4px #00000017;
}
.summary-cards article {
  display: grid;
  gap: 11px;
  padding: 18px 29px;
  border-right: 1px solid #d5d8d5;
}
.summary-cards article:last-child {
  border-right: 0;
}
.summary-cards span {
  color: #56665d;
  font-size: 20px;
}
.summary-cards b {
  color: #1f2937;
  font-size: 21px;
  font-weight: 400;
}
.transaction-content {
  display: grid;
  grid-template-columns: 310px 1fr;
  gap: 28px;
  align-items: end;
  margin-bottom: 24px;
}
.type-picker {
  display: grid;
  gap: 10px;
  color: #1f2937;
  font-size: 17px;
}
.type-picker select {
  height: 54px;
  padding: 0 20px;
  border: 1px solid #173d47;
  border-radius: 11px;
  color: #1f2937;
  background: #fff;
  font: 600 16px inherit;
  letter-spacing: 1.44px;
}
.filters {
  display: grid;
  grid-template-columns: 330px 1fr 280px;
  gap: 16px;
}
.search-field,
.date-range,
.branch-field {
  display: flex;
  align-items: center;
  height: 54px;
  border: 1px solid #173d47;
  border-radius: 11px;
  background: #fff;
}
.search-field {
  gap: 12px;
  padding: 0 14px;
  box-shadow: 0 4px 4px #00000017;
}
.search-field svg {
  width: 22px;
  stroke: #56665d;
  stroke-width: 2;
  fill: none;
}
.search-field input,
.branch-field select {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #56665d;
  background: transparent;
  font: 16px inherit;
  letter-spacing: 1.44px;
}
.date-range {
  border: 0;
  border-radius: 0;
  background: transparent;
  gap: 8px;
  padding: 0;
  color: #56665d;
  font-size: 14px;
  white-space: nowrap;
}
.date-range :deep(.thai-date-picker) {
  flex: 1;
  min-width: 0;
  margin: 0;
}
.date-range :deep(.date-trigger) {
  min-height: 54px;
  padding: 7px 12px;
  border: 1px solid #173d47;
  border-radius: 11px;
  color: #56665d;
  font-size: 14px;
}
.date-range :deep(.calendar) {
  z-index: 40;
  width: min(330px, calc(100vw - 40px));
}
.date-range :deep(.thai-date-picker:last-of-type .calendar) {
  right: 0;
  left: auto;
}
.branch-field select {
  padding: 0 20px;
  color: #1f2937;
}
.error-message {
  margin: 0 0 22px;
  padding: 12px 15px;
  border-radius: 8px;
  color: #b44743;
  background: #fee8e4;
}
.table-scroll {
  overflow-x: auto;
  scrollbar-color: #1e807c #edf2ed;
  scrollbar-width: thin;
}
.table-scroll::-webkit-scrollbar {
  height: 10px;
}
.table-scroll::-webkit-scrollbar-track {
  border-radius: 999px;
  background: #edf2ed;
}
.table-scroll::-webkit-scrollbar-thumb {
  border: 2px solid #edf2ed;
  border-radius: 999px;
  background: #1e807c;
}
.table-scroll::-webkit-scrollbar-thumb:hover {
  background: #176a67;
}
@media (max-width: 760px) {
  .table-scroll::-webkit-scrollbar {
    height: 8px;
  }
}
table {
  width: 100%;
  min-width: 1000px;
  border-collapse: collapse;
}
th {
  height: 60px;
  padding: 0 28px;
  color: #fff;
  background: #1e807c;
  font-size: 17px;
  font-weight: 400;
  text-align: left;
  white-space: nowrap;
}
td {
  height: 64px;
  padding: 0 28px;
  border-bottom: 1px solid #d5d8d5;
  color: #1f2937;
  font-size: 16px;
  white-space: nowrap;
}
.empty-row {
  text-align: center;
  color: #56665d;
}
.pagination {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
  padding: 32px 0 0;
  border-top: 1px solid #d5d8d5;
}
.pagination button {
  min-width: 102px;
  height: 42px;
  padding: 0 18px;
  border: 0;
  border-radius: 16px;
  color: #fff;
  background: #1e807c;
  box-shadow: 0 2px 4px #0000002b;
  font: 14px inherit;
  cursor: pointer;
}
.pagination button:disabled {
  color: #a8b1ad;
  background: #fff;
  box-shadow: 0 2px 4px #00000018;
  cursor: default;
}
.pagination span {
  color: #25372e;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}
.pagination button:first-child {
  justify-self: start;
}
.pagination button:last-child {
  justify-self: end;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
@media (max-width: 1180px) {
  .transaction-content {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .type-picker {
    width: min(330px, 100%);
  }
  .filters {
    grid-template-columns: 1fr 1fr;
  }
  .search-field {
    grid-column: 1 / -1;
  }
  .branch-field {
    grid-column: auto;
  }
  .summary-cards span {
    font-size: 18px;
  }
}
.pagination {
  gap: 90px;
}
@media (max-width: 760px) {
  .transaction-main {
    width: min(100% - 32px, 680px);
    padding-top: 42px;
  }
  .page-title h1 {
    font-size: clamp(2.2rem, 11vw, 3rem);
    letter-spacing: 1px;
  }
  .page-title p {
    margin-bottom: 36px;
    font-size: 1rem;
  }
  .summary-cards {
    grid-template-columns: 1fr 1fr;
    margin-bottom: 38px;
  }
  .summary-cards article {
    padding: 18px;
  }
  .summary-cards article:nth-child(2) {
    border-right: 0;
  }
  .summary-cards article:nth-child(-n + 2) {
    border-bottom: 1px solid #d5d8d5;
  }
  .summary-cards span,
  .summary-cards b {
    font-size: 1rem;
  }
  .transaction-content {
    display: grid;
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .search-field {
    grid-column: auto;
  }
  .branch-field {
    grid-column: auto;
  }
  .pagination {
    gap: 10px;
  }
  .pagination span {
    font-size: 0.9rem;
  }
}
@media (max-width: 440px) {
  .transaction-main {
    width: min(100% - 24px, 680px);
  }
  .summary-cards {
    grid-template-columns: 1fr;
  }
  .summary-cards article,
  .summary-cards article:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid #d5d8d5;
  }
  .summary-cards article:last-child {
    border-bottom: 0;
  }
  .date-range :deep(.date-trigger) {
    padding: 7px 3px;
    font-size: 12px;
  }
  .pagination button {
    min-width: auto;
    padding: 0 10px;
  }
}
</style>
