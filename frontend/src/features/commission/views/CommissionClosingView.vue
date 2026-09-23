<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { commissionApi } from '@/api/client'

type ClosingRow = {
  id: number
  staff: string
  sales: number
  rate: number
  status: 'Open' | 'Closed'
}

const selectedMonth = ref('2026-08-01')
const month = computed(() => selectedMonth.value.slice(0, 7))
const router = useRouter()
const branch = ref('ทุกสาขา')
const search = ref('')
const rows = ref<ClosingRow[]>([])
const errorMessage = ref('')
const isConfirming = ref(false)
const isSaving = ref(false)

async function load() {
  try {
    const data = await commissionApi.closings(month.value)
    rows.value = data.map((row) => ({
      id: row.id,
      staff: row.employeeName,
      sales: Number(row.sales),
      rate: Number(row.rate) * 100,
      status: row.status === 'CLOSED' ? 'Closed' : 'Open',
    }))
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรอบคอมมิชชันไม่สำเร็จ'
  }
}

async function confirmClosing() {
  isSaving.value = true
  try {
    await commissionApi.closeMonth(month.value)
    isConfirming.value = false
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ปิดรอบไม่สำเร็จ'
  } finally {
    isSaving.value = false
  }
}

onMounted(load)
watch(month, load)

const visible = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return rows.value.filter((row) => !keyword || row.staff.toLowerCase().includes(keyword))
})
const openCount = computed(() => rows.value.filter((row) => row.status === 'Open').length)
const totalSales = computed(() => rows.value.reduce((sum, row) => sum + row.sales, 0))
const monthLabel = computed(() => {
  const [year, number] = month.value.split('-').map(Number)
  if (!year || !number) return month.value
  return new Intl.DateTimeFormat('th-TH', { month: 'short', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
})
const currency = (value: number) => `฿${value.toLocaleString()}`

function selectView(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'Sales') void router.push('/staff/commission')
  if (value === 'Treatment') void router.push('/staff/commission?type=Treatment')
}
</script>

<template>
  <div class="closing-page">
    <StaffHeader />
    <main class="closing-main">
      <h1>ปิดรอบรายเดือน</h1>
      <section class="closing-summary" aria-label="สรุปรอบรายเดือน">
        <article>
          <span>รอบคำนวณ</span><b>{{ monthLabel }}</b>
        </article>
        <article>
          <span>ยอดขายรวม</span><b>{{ currency(totalSales) }}</b>
        </article>
        <article>
          <span>รอปิดรอบ</span><b>{{ openCount }}</b>
        </article>
        <article>
          <span>สถานะเดือน</span><b>{{ openCount ? 'รอปิดรอบ' : 'ปิดรอบแล้ว' }}</b>
        </article>
      </section>
      <section class="closing-panel">
        <label class="type-picker"
          ><span>เลือกประเภท</span
          ><select value="Closing" @change="selectView">
            <option value="Sales">Sales Transaction</option>
            <option value="Treatment">Treatment Transaction</option>
            <option value="Closing">ปิดรอบรายเดือน</option>
          </select></label
        >
        <div class="closing-filters">
          <label class="search-field"
            ><span class="sr-only">ค้นหา</span
            ><svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m21 21-4.35-4.35m2.35-5.15a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z" /></svg
            ><input v-model="search" type="search" placeholder="ค้นหาชื่อบุคลากร"
          /></label>
          <ThaiDatePicker
            v-model="selectedMonth"
            class="month-field"
            show-calendar-icon
            numeric-month
            hide-caret
          />
          <label class="branch-field"
            ><span class="sr-only">สาขา</span
            ><select v-model="branch">
              <option>ทุกสาขา</option>
            </select></label
          >
          <button
            class="close-all"
            type="button"
            :disabled="!openCount"
            @click="isConfirming = true"
          >
            ปิดรอบทั้งหมด
          </button>
        </div>
        <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
        <div class="table-scroll">
          <table>
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
              <tr v-for="row in visible" :key="row.id">
                <td>{{ row.staff }}</td>
                <td>{{ monthLabel }}</td>
                <td>{{ currency(row.sales) }}</td>
                <td>{{ row.rate }}%</td>
                <td>
                  <span class="status" :class="row.status === 'Open' ? 'open' : 'closed'">{{
                    row.status === 'Open' ? 'รอปิดรอบ' : 'ปิดรอบแล้ว'
                  }}</span>
                </td>
                <td>
                  <button
                    v-if="row.status === 'Open'"
                    class="close-one"
                    type="button"
                    @click="isConfirming = true"
                  >
                    ปิดรอบ</button
                  ><span v-else class="already-closed">ปิดรอบแล้ว</span>
                </td>
              </tr>
              <tr v-if="!visible.length">
                <td colspan="6" class="empty-row">ไม่พบข้อมูลตามตัวกรอง</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
    <div
      v-if="isConfirming"
      class="modal-backdrop"
      role="presentation"
      @click.self="isConfirming = false"
    >
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="close-title">
        <h2 id="close-title">ต้องการปิดรอบเดือนหรือไม่?</h2>
        <div>
          <button class="cancel" type="button" :disabled="isSaving" @click="isConfirming = false">
            ไม่ใช่</button
          ><button class="confirm" type="button" :disabled="isSaving" @click="confirmClosing">
            {{ isSaving ? 'กำลังบันทึก...' : 'ใช่' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.closing-page {
  min-height: 100vh;
  background: #f9f4ee;
}
.closing-main {
  width: min(1540px, calc(100% - 10vw));
  margin: 0 auto;
  padding: 105px 0 64px;
}
h1 {
  margin: 0 0 45px;
  color: #25372e;
  font-size: clamp(2.6rem, 4vw, 3.45rem);
  letter-spacing: 0.03em;
}
.closing-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: 42px;
  overflow: hidden;
  border-radius: 22px;
  background: #fffafa;
  box-shadow: 0 4px 18px #00000014;
}
.closing-summary article {
  display: grid;
  gap: 5px;
  padding: 25px 34px;
  border-right: 1px solid #dde6df;
}
.closing-summary article:last-child {
  border-right: 0;
}
.closing-summary span {
  color: #56665d;
}
.closing-summary b {
  color: #1f2937;
  font-size: 1.45rem;
}
.closing-panel {
  overflow: visible;
  display: block;
}
.type-picker {
  display: grid;
  gap: 10px;
  width: min(330px, 100%);
  margin: 28px 0 16px;
  color: #1f2937;
  font-size: 18px;
}
.type-picker select {
  height: 54px;
  padding: 0 16px;
  border: 1px solid #173d47;
  border-radius: 9px;
  color: #1f2937;
  background: #fff;
  font: inherit;
}
.closing-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 22px;
  align-items: center;
  padding: 0 0 28px;
}
.search-field,
.branch-field {
  display: flex;
  align-items: center;
  height: 54px;
  border: 1px solid #173d47;
  border-radius: 9px;
  background: #fff;
}
.search-field {
  grid-column: 1 / -1;
  gap: 10px;
  padding: 0 16px;
}
.search-field svg {
  width: 23px;
  height: 23px;
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
  color: #25372e;
  background: transparent;
  font: inherit;
}
.branch-field select {
  padding: 0 14px;
}
.month-field {
  margin: 0;
}
.month-field :deep(.date-trigger) {
  min-height: 54px;
  border: 1px solid #173d47;
  border-radius: 9px;
  color: #25372e;
}
.month-field :deep(.calendar) {
  z-index: 40;
  width: min(330px, calc(100vw - 40px));
}
.close-all,
.close-one,
.confirm {
  border: 0;
  color: #fff;
  background: #739266;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.close-all {
  height: 53px;
  padding: 0 22px;
  border-radius: 20px;
  box-shadow: 0 3px 2px #00000024;
  white-space: nowrap;
}
.close-one {
  padding: 10px 19px;
  border-radius: 20px;
  background: #1e807c;
}
.close-all:disabled,
.confirm:disabled {
  color: #fff;
  background: #dff4d7;
  box-shadow: none;
  opacity: 1;
  cursor: not-allowed !important;
}
.error-message {
  grid-column: 1 / -1;
  margin: 0 32px 22px;
  padding: 12px 15px;
  border-radius: 8px;
  color: #b44743;
  background: #fee8e4;
}
.table-scroll {
  grid-column: 1 / -1;
  overflow-x: auto;
}
table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
}
th {
  padding: 20px 28px;
  color: #fff;
  background: #1e807c;
  font-weight: 600;
  text-align: left;
}
td {
  padding: 20px 28px;
  border-bottom: 1px solid #dde6df;
  color: #1f2937;
}
.status {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 22px;
  font-size: 0.95rem;
}
.status.open {
  border: 1px solid #1e807c;
  color: #647a63;
  background: #b9ef9a;
}
.status.closed,
.already-closed {
  color: #647a63;
  background: #cbcbcb;
}
.status.closed {
  border: 1px solid #4d4d4d;
}
.already-closed {
  display: inline-block;
  padding: 10px 19px;
  border-radius: 20px;
}
.empty-row {
  padding: 44px;
  color: #56665d;
  text-align: center;
}
.modal-backdrop {
  position: fixed;
  z-index: 20;
  top: 148px;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #232b2280;
  backdrop-filter: blur(1.5px);
}
.confirm-modal {
  width: min(100%, 480px);
  padding: 33px;
  border: 1px solid #e9e4dc;
  border-radius: 16px;
  background: #fff;
  box-shadow:
    0 20px 40px -10px #2a362e2e,
    0 8px 16px -6px #2a362e1a;
}
.confirm-modal h2 {
  margin: 0;
  color: #25372e;
  font-size: 22px;
  text-align: center;
}
.confirm-modal div {
  display: flex;
  gap: 14px;
  margin-top: 12px;
}
.cancel {
  flex: 1;
  min-height: 46px;
  border: 2px solid #1e807c;
  border-radius: 12px;
  color: #1f2937;
  background: #faf6f0;
  font: inherit;
  cursor: pointer;
}
.confirm {
  flex: 1;
  min-height: 46px;
  border: 2px solid transparent;
  border-radius: 12px;
  background: #749b6c;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@media (max-width: 1050px) {
  .closing-summary {
    grid-template-columns: repeat(2, 1fr);
  }
  .closing-summary article:nth-child(2) {
    border-right: 0;
  }
  .closing-summary article:nth-child(-n + 2) {
    border-bottom: 1px solid #dde6df;
  }
  .closing-filters {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .close-all {
    justify-self: start;
  }
}
@media (max-width: 620px) {
  .closing-main {
    width: min(100% - 32px, 720px);
    padding-top: 48px;
  }
  .closing-summary {
    grid-template-columns: 1fr;
  }
  .closing-summary article,
  .closing-summary article:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid #dde6df;
  }
  .closing-summary article:last-child {
    border-bottom: 0;
  }
  .closing-filters {
    grid-template-columns: 1fr;
    padding: 20px;
  }
  .search-field {
    grid-column: auto;
  }
  .type-picker {
    width: 100%;
    margin: 20px 0 16px;
  }
  .confirm-modal {
    padding: 27px 23px;
  }
  .confirm-modal div {
    flex-direction: column-reverse;
  }
  .confirm-modal button {
    width: 100%;
  }
}
</style>
