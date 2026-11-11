<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
type ViewType = 'Sales Transaction' | 'Treatment Transaction' | 'ปิดรอบรายเดือน'
type ClosingRow = {
  id: number
  staff: string
  date: string
  sales: number
  rate: number
  closed: boolean
}
const type = ref<ViewType>('Sales Transaction'),
  search = ref(''),
  date = ref(''),
  branch = ref(''),
  confirmOpen = ref(false),
  closingTarget = ref<number | 'all' | null>(null)
const salesRows: Array<{
  id: string
  date: string
  branch: string
  name: string
  rate: string
  amount: number
  commission: number
}> = []
const treatmentRows: Array<{
  id: string
  date: string
  branch: string
  patient: string
  staff: string
  fee: number
  rule: string
  amount: number
}> = []
const closingRows = ref<ClosingRow[]>([])
const openCount = computed(() => closingRows.value.filter((r) => !r.closed).length),
  totalSales = computed(() => closingRows.value.reduce((s, r) => s + r.sales, 0))
const searchPlaceholder = computed(() => {
  if (type.value === 'Treatment Transaction') return 'ค้นหารหัสคอร์สหรือชื่อผู้ป่วย'
  if (type.value === 'ปิดรอบรายเดือน') return 'ค้นหาชื่อผู้ขาย'
  return 'ค้นหาเลขที่รายการหรือชื่อผู้ขาย'
})
const keyword = computed(() => search.value.trim().toLowerCase())
const filteredSalesRows = computed(() =>
  salesRows.filter(
    (row) =>
      !keyword.value ||
      row.id.toLowerCase().includes(keyword.value) ||
      row.name.toLowerCase().includes(keyword.value),
  ),
)
const filteredTreatmentRows = computed(() =>
  treatmentRows.filter(
    (row) =>
      !keyword.value ||
      row.id.toLowerCase().includes(keyword.value) ||
      row.patient.toLowerCase().includes(keyword.value),
  ),
)
const filteredClosingRows = computed(() =>
  closingRows.value.filter(
    (row) => !keyword.value || row.staff.toLowerCase().includes(keyword.value),
  ),
)
const pageSize = 10
const page = ref(1)
const activeRowCount = computed(() => {
  if (type.value === 'Treatment Transaction') return filteredTreatmentRows.value.length
  if (type.value === 'ปิดรอบรายเดือน') return filteredClosingRows.value.length
  return filteredSalesRows.value.length
})
const pageCount = computed(() => Math.max(1, Math.ceil(activeRowCount.value / pageSize)))
const pageStart = computed(() => (page.value - 1) * pageSize)
const pagedSalesRows = computed(() =>
  filteredSalesRows.value.slice(pageStart.value, pageStart.value + pageSize),
)
const pagedTreatmentRows = computed(() =>
  filteredTreatmentRows.value.slice(pageStart.value, pageStart.value + pageSize),
)
const pagedClosingRows = computed(() =>
  filteredClosingRows.value.slice(pageStart.value, pageStart.value + pageSize),
)
watch([type, search, branch, date], () => {
  page.value = 1
})
const money = (v: number) => `฿${v.toLocaleString('th-TH')}`
function requestClose(target: number | 'all') {
  closingTarget.value = target
  confirmOpen.value = true
}
function confirmClose() {
  if (closingTarget.value === 'all') closingRows.value.forEach((r) => (r.closed = true))
  else {
    const row = closingRows.value.find((r) => r.id === closingTarget.value)
    if (row) row.closed = true
  }
  confirmOpen.value = false
  closingTarget.value = null
}
</script>
<template>
  <div class="personnel-page">
    <StaffHeader />
    <main class="personnel-main">
      <header>
        <h1>{{ type }}</h1>
        <p v-if="type !== 'ปิดรอบรายเดือน'">ตรวจสอบข้อมูลจาก Treatment และ Sales Transaction</p>
      </header>
      <section v-if="type === 'Sales Transaction'" class="summary">
        <article><span>รายการที่แสดง</span><b>2 รายการ</b></article>
        <article><span>ค่ามือคอมมิชชันรวม</span><b>฿600</b></article>
        <article><span>ค่าคอมมิชชันรวม</span><b>84,375</b></article>
        <article><span>พนักงานขายที่ขายได้</span><b>2 คน</b></article>
      </section>
      <section v-else-if="type === 'Treatment Transaction'" class="summary">
        <article><span>รายการที่แสดง</span><b>2 รายการ</b></article>
        <article><span>มูลค่าการรักษารวม</span><b>700</b></article>
        <article><span>ค่ามือรวม</span><b>70</b></article>
        <article><span>สาขา</span><b>สาขาทั้งหมด</b></article>
      </section>
      <section v-else class="summary">
        <article><span>รอบคำนวณ</span><b>ส.ค. 2569</b></article>
        <article>
          <span>ยอดขายรวม</span><b>{{ money(totalSales) }}</b>
        </article>
        <article>
          <span>รอปิดรอบ</span><b>{{ openCount }}</b>
        </article>
        <article>
          <span>สถานะเดือน</span><b>{{ openCount ? 'รอปิดรอบ' : 'ปิดรอบแล้ว' }}</b>
        </article>
      </section>
      <section class="filters">
        <label class="type-field"
          >เลือกประเภท<FilterDropdown
            v-model="type"
            label="เลือกประเภท"
            default-value="Sales Transaction"
            default-label="Sales Transaction"
            :clear-on-reselect="false"
            :options="[
              { value: 'Treatment Transaction', label: 'Treatment Transaction' },
              { value: 'ปิดรอบรายเดือน', label: 'ปิดรอบรายเดือน' },
            ]" /></label
        ><label class="search"
          ><span>⌕</span><input v-model="search" :placeholder="searchPlaceholder" /></label
        ><ThaiDatePicker
          v-model="date"
          numeric-month
          show-calendar-icon
          hide-caret
        /><FilterDropdown
          v-model="branch"
          label="สาขา"
          default-value=""
          default-label="สาขาทั้งหมด"
          :options="[
            { value: 'BR', label: 'แบริ่ง' },
            { value: 'R9', label: 'พระราม 9' },
          ]"
        /><button
          v-if="type === 'ปิดรอบรายเดือน'"
          class="close-all"
          :disabled="!openCount"
          @click="requestClose('all')"
        >
          ปิดรอบทั้งหมด
        </button>
      </section>
      <div class="table-wrap">
        <table v-if="type === 'Sales Transaction'">
          <thead>
            <tr>
              <th>เลขที่รายการ</th>
              <th>วันที่ทำรายการ</th>
              <th>สาขา</th>
              <th>ผู้ขาย</th>
              <th>เกณฑ์คอมมิชชัน</th>
              <th>จำนวนเงินที่ขาย</th>
              <th>ค่าคอมมิชชัน</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in pagedSalesRows" :key="r.id">
              <td>{{ r.id }}</td>
              <td>{{ r.date }}</td>
              <td>{{ r.branch }}</td>
              <td>{{ r.name }}</td>
              <td>{{ r.rate }}</td>
              <td>{{ money(r.amount) }}</td>
              <td>{{ money(r.commission) }}</td>
            </tr>
            <tr v-if="!filteredSalesRows.length">
              <td colspan="7" class="empty-row">ไม่พบรายการที่ค้นหา</td>
            </tr>
          </tbody>
        </table>
        <table v-else-if="type === 'Treatment Transaction'">
          <thead>
            <tr>
              <th>รหัสคอร์ส</th>
              <th>วันที่ทำรายการ</th>
              <th>สาขา</th>
              <th>ผู้ป่วย</th>
              <th>นักกายภาพบำบัด</th>
              <th>ค่ามือ</th>
              <th>กติกาค่ามือ</th>
              <th>มูลค่าบริการ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in pagedTreatmentRows" :key="r.id">
              <td>{{ r.id }}</td>
              <td>{{ r.date }}</td>
              <td>{{ r.branch }}</td>
              <td>{{ r.patient }}</td>
              <td>{{ r.staff }}</td>
              <td>{{ money(r.fee) }}</td>
              <td>{{ r.rule }}</td>
              <td>{{ money(r.amount) }}</td>
            </tr>
            <tr v-if="!filteredTreatmentRows.length">
              <td colspan="8" class="empty-row">ไม่พบรายการที่ค้นหา</td>
            </tr>
          </tbody>
        </table>
        <table v-else>
          <thead>
            <tr>
              <th>ผู้ขาย</th>
              <th>วันที่ทำรายการ</th>
              <th>ยอดขายคอร์ส</th>
              <th>อัตราคอมมิชชัน</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in pagedClosingRows" :key="r.id">
              <td>{{ r.staff }}</td>
              <td>{{ r.date }}</td>
              <td>{{ money(r.sales) }}</td>
              <td>{{ r.rate }}%</td>
              <td>
                <span class="status" :class="{ closed: r.closed }">{{
                  r.closed ? 'ปิดรอบแล้ว' : 'รอปิดรอบ'
                }}</span>
              </td>
              <td>
                <button class="close-row" :disabled="r.closed" @click="requestClose(r.id)">
                  {{ r.closed ? 'ปิดรอบแล้ว' : 'ปิดรอบ' }}
                </button>
              </td>
            </tr>
            <tr v-if="!filteredClosingRows.length">
              <td colspan="6" class="empty-row">ไม่พบรายการที่ค้นหา</td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav class="pagination">
        <button :disabled="page === 1" @click="page--">← ก่อนหน้า</button>
        <span>หน้า {{ page }} จาก {{ pageCount }}</span>
        <button :disabled="page === pageCount" @click="page++">ถัดไป →</button>
      </nav>
    </main>
    <div v-if="confirmOpen" class="modal-backdrop" @click.self="confirmOpen = false">
      <section class="confirm-modal" role="dialog" aria-modal="true">
        <h2>ต้องการปิดรอบเดือนหรือไม่?</h2>
        <div>
          <button class="no" @click="confirmOpen = false">ไม่ใช่</button
          ><button class="yes" @click="confirmClose">ใช่</button>
        </div>
      </section>
    </div>
  </div>
</template>
<style scoped>
.personnel-page {
  min-height: 100vh;
  background: #f9f4ee;
  color: var(--color-ink);
}
.personnel-main {
  width: min(1500px, calc(100% - 72px));
  margin: auto;
  padding: 46px 0 58px;
}
.personnel-main header h1 {
  margin: 0;
  font-size: clamp(2rem, 3vw, 2.8rem);
  letter-spacing: 0.04em;
}
.personnel-main header p {
  margin: 5px 0 95px;
  color: var(--color-muted);
  font-size: 1.1rem;
}
.summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 38px 0 46px;
  border: 1px solid var(--color-border-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 3px 8px #0002;
}
.summary article {
  padding: 13px 24px;
  border-right: 1px solid var(--color-border-soft);
}
.summary article:last-child {
  border: 0;
}
.summary span,
.summary b {
  display: block;
}
.summary span {
  color: var(--color-muted);
}
.summary b {
  margin-top: 4px;
  font-size: 1.2rem;
  font-weight: 500;
}
.filters {
  display: grid;
  grid-template-columns: 1.2fr 1.2fr 1fr 0.9fr auto;
  gap: 14px;
  align-items: end;
  margin-bottom: 20px;
}
.filters label {
  display: grid;
  gap: 8px;
}
.search {
  order: -1;
  width: 100%;
  height: 46px;
  min-height: 46px;
  box-sizing: border-box;
  display: flex !important;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid #173d47;
  border-radius: 9px;
  background: #fff;
}
.search span {
  display: none;
}
.search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
}
.filters :deep(.filter-dropdown__button),
.filters :deep(.date-trigger) {
  min-height: 46px;
  border-color: #173d47;
}
.filters .type-field,
.filters .type-field :deep(.filter-dropdown),
.filters .type-field :deep(.filter-dropdown__button) {
  width: 100%;
}
.filters .type-field :deep(.filter-dropdown__button) {
  height: 46px;
  min-height: 46px;
  box-sizing: border-box;
}
.close-all,
.close-row {
  min-height: 44px;
  padding: 0 20px;
  border: 0;
  border-radius: 16px;
  background: var(--color-primary);
  color: #fff;
  font: inherit;
  cursor: pointer;
}
.close-all {
  background: #789d70;
}
.close-all:disabled,
.close-row:disabled {
  background: #c8cbc8;
  color: #666;
  cursor: not-allowed;
}
.table-wrap {
  overflow-x: auto;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 3px 8px #0002;
  scrollbar-color: var(--color-primary) #e7efeb;
  scrollbar-width: thin;
}
.table-wrap::-webkit-scrollbar {
  height: 10px;
}
.table-wrap::-webkit-scrollbar-track {
  border-radius: 999px;
  background: #e7efeb;
}
.table-wrap::-webkit-scrollbar-thumb {
  border: 2px solid #e7efeb;
  border-radius: 999px;
  background: var(--color-primary);
}
.table-wrap::-webkit-scrollbar-thumb:hover {
  background: #176b68;
}
.table-wrap table {
  width: 100%;
  min-width: 960px;
  border-collapse: collapse;
}
.table-wrap th {
  padding: 18px;
  background: #1e8882;
  color: #fff;
  text-align: center;
  white-space: nowrap;
}
.table-wrap td {
  padding: 18px;
  border-bottom: 1px solid #dde3df;
  text-align: center;
  white-space: nowrap;
}
.status {
  display: inline-flex;
  padding: 5px 14px;
  border: 1px solid #70bd54;
  border-radius: 999px;
  background: #c4f4a9;
  color: #558249;
}
.status.closed {
  border-color: #9aa09d;
  background: #d1d3d2;
  color: #626865;
}
.table-wrap .empty-row {
  height: 110px;
  color: #667a72;
  font-size: 1rem;
  text-align: center;
}
.pagination {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  margin-top: 28px;
}
.pagination button {
  width: max-content;
  padding: 9px 18px;
  border: 0;
  border-radius: 14px;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
}
.pagination button:disabled {
  background: #fff;
  color: #aaa;
  cursor: not-allowed;
}
.pagination button:last-child {
  justify-self: end;
}
.modal-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: #28332d88;
}
.confirm-modal {
  width: min(420px, 100%);
  padding: 34px;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 18px 50px #0004;
}
.confirm-modal h2 {
  margin: 0;
  text-align: center;
  font-size: 1.25rem;
}
.confirm-modal div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 30px;
}
.confirm-modal button {
  height: 46px;
  border-radius: 9px;
  font: inherit;
  cursor: pointer;
}
.confirm-modal .no {
  border: 2px solid var(--color-primary);
  background: #fff;
}
.confirm-modal .yes {
  border: 0;
  background: #79a46e;
  color: #fff;
}
@media (max-width: 1100px) {
  .summary {
    grid-template-columns: repeat(2, 1fr);
  }
  .summary article:nth-child(2) {
    border-right: 0;
  }
  .summary article:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-border-soft);
  }
}
@media (max-width: 1200px) {
  .personnel-main {
    width: calc(100% - 40px);
    padding-top: 36px;
  }
  .filters {
    grid-template-columns: 1fr 1fr;
  }
  .filters .search,
  .type-field {
    grid-column: 1 / -1;
  }
}
@media (max-width: 700px) {
  .personnel-main {
    width: calc(100% - 28px);
    padding-top: 28px;
  }
  .personnel-main header h1 {
    font-size: 1.8rem;
  }
  .personnel-main header p {
    margin-bottom: 48px;
  }
  .summary {
    grid-template-columns: 1fr;
    margin: 28px 0 32px;
  }
  .summary article,
  .summary article:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .summary article:last-child {
    border-bottom: 0;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .filters .search,
  .type-field {
    grid-column: auto;
  }
  .close-all {
    width: 100%;
  }
  .pagination {
    gap: 8px;
  }
  .pagination span {
    font-size: 0.85rem;
  }
  .confirm-modal div {
    grid-template-columns: 1fr;
  }
}
</style>
