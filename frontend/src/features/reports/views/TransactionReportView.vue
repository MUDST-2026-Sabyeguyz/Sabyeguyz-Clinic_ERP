<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { branchApi, commissionApi, salesApi, type SaleDetail } from '@/api/client'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
const type = ref<'single' | 'course'>('single')
const router = useRouter()
const reportSelection = ref('salesTransaction')
const reportOptions = [
  { value: 'revenue', label: 'รายงานยอดขาย' },
  { value: 'balance', label: 'ยอดคงเหลือคอร์ส' },
  { value: 'transfer', label: 'ประวัติการโอนคอร์ส' },
  { value: 'commission', label: 'ค่าคอมมิชชั่น' },
  { value: 'salesTransaction', label: 'Transaction' },
  { value: 'payment', label: 'รายงานยอดรับชำระ' },
]
const search = ref('')
const from = ref('')
const to = ref('')
const page = ref(1)
const loading = ref(false)
const error = ref('')
const updated = ref('')
const sourceTierOpen = ref(false)
const sales = ref<SaleDetail[]>([])
const treatments = ref<Awaited<ReturnType<typeof commissionApi.treatmentReport>>>([])
const branchNames = ref<Record<string, string>>({})
let requestId = 0
const money = (n: number | null) => n === null ? 'รอคำนวณ' : `฿${Number(n).toLocaleString('th-TH', { maximumFractionDigits: 2 })}`
const date = (v: string) =>
  new Date(v).toLocaleDateString('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
const title = computed(() =>
  type.value === 'single' ? 'Single Visit Transaction' : 'Course Transaction',
)
const keyword = computed(() => search.value.trim().toLowerCase())
const salesRows = computed(() =>
  sales.value.filter((row) => {
    const matchesType = row.items.some((item) => item.type === 'SINGLE_VISIT')
    return (
      matchesType &&
      (!from.value || row.soldAt.slice(0, 10) >= from.value) &&
      (!to.value || row.soldAt.slice(0, 10) <= to.value) &&
      `${row.patient.fullName} ${row.patient.hn}`.toLowerCase().includes(keyword.value)
    )
  }),
)
const courseRows = computed(() =>
  treatments.value.filter((row) =>
    `${row.patientName} ${row.patientHn}`.toLowerCase().includes(keyword.value),
  ),
)
const count = computed(() =>
  type.value === 'single' ? salesRows.value.length : courseRows.value.length,
)
const pageCount = computed(() => Math.max(1, Math.ceil(count.value / 10)))
const start = computed(() => (page.value - 1) * 10)
const metrics = computed(() =>
  type.value === 'single'
    ? [
        ['รายการที่แสดง', `${count.value} รายการ`],
        ['ค่ามือคอมมิชชันรวม', '—'],
        ['ค่าคอมมิชชันรวม', '—'],
        [
          'พนักงานขายที่ขายได้',
          `${new Set(salesRows.value.map((r) => r.salesperson?.id).filter(Boolean)).size} คน`,
        ],
      ]
    : [
        ['รายการที่แสดง', `${count.value} รายการ`],
        ['มูลค่าการรักษารวม', money(courseRows.value.reduce((n, r) => n + Number(r.gross), 0))],
        ['ค่ามือรวม', money(courseRows.value.reduce((n, r) => n + Number(r.treatmentFee), 0))],
      ],
)
const tierBase = computed(() =>
  type.value === 'single'
    ? salesRows.value.reduce((total, row) => total + Number(row.total), 0)
    : courseRows.value.reduce((total, row) => total + Number(row.gross), 0),
)
const sourceDetail = computed(() => {
  if (type.value === 'single') {
    const row = salesRows.value[0]
    if (!row) return null
    const course = row.items.find((item) => item.type === 'COURSE')
    return {
      receipt: row.receiptNo,
      course: course?.description || row.items.map((item) => item.description).join(', ') || '—',
      seller: row.salesperson?.fullName || '—',
      branch: branchNames.value[row.branchCode] || row.branchCode,
      soldAt: date(row.soldAt),
      gross: row.items.reduce((total, item) => total + Number(item.lineTotal), 0),
      net: Number(row.total),
    }
  }

  const row = courseRows.value[0]
  if (!row) return null
  return {
    receipt: row.courseCode,
    course: row.courseCode || '—',
    seller: row.treatingStaffName || '—',
    branch: row.branchName || row.branchCode,
    soldAt: date(row.visitDate),
    gross: Number(row.gross),
    net: Number(row.gross),
  }
})
function openTierRoundDetails() {
  sourceTierOpen.value = false
  void router.push({
    name: 'transaction-tier-round',
    query: {
      type: type.value,
      base: String(tierBase.value),
      from: from.value,
      to: to.value,
    },
  })
}
async function load() {
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    if (type.value === 'single') {
      const result = await salesApi.list({ page: 0, size: 100 })
      const all = [...result.items]
      for (let index = 1; index < result.totalPages; index++)
        all.push(...(await salesApi.list({ page: index, size: 100 })).items)
      const details = await Promise.all(
        all.filter((row) => row.status !== 'VOIDED').map((row) => salesApi.get(row.id)),
      )
      if (id === requestId) sales.value = details
    } else {
      const rows = await commissionApi.treatmentReport(
        from.value || '2000-01-01',
        to.value || new Date().toLocaleDateString('en-CA'),
      )
      if (id === requestId) treatments.value = rows
    }
    if (id === requestId) updated.value = new Date().toLocaleString('th-TH')
  } catch (caught) {
    if (id === requestId)
      error.value = caught instanceof Error ? caught.message : 'โหลดรายการไม่สำเร็จ'
  } finally {
    if (id === requestId) loading.value = false
  }
}
void branchApi
  .list()
  .then((rows) => {
    branchNames.value = Object.fromEntries(rows.map((r) => [r.code, r.name]))
  })
  .catch(() => {})
watch([type, from, to], load, { immediate: true })
watch([type, search, from, to], () => {
  page.value = 1
})
watch(reportSelection, (report) => {
  if (report !== 'salesTransaction') {
    void router.push({ path: '/staff/reports', query: { report } })
  }
})
</script>
<template>
  <main class="transaction-workspace">
    <header>
      <div>
        <h1>Transaction</h1>
        <p>ดูรายการขายและรายการใช้สิทธิ์คอร์ส โดยเลือกประเภทธุรกรรมที่ต้องการ</p>
      </div>
      <label class="transaction-report-select">
        <span>เลือกรายงาน</span>
        <FilterDropdown
          v-model="reportSelection"
          :options="reportOptions"
          default-value="salesTransaction"
          default-label="Transaction"
          label="เลือกรายงาน"
          hide-default-option
          :clear-on-reselect="false"
        />
      </label>
    </header>
    <section class="transaction-filters">
      <div>
        <b>เลือกประเภท</b>
        <div class="type-choices">
          <button
            v-for="option in ['single', 'course'] as const"
            :key="option"
            :class="{ active: type === option }"
            :aria-pressed="type === option"
            @click="type = option"
          >
            {{ type === option ? '✓ ' : ''
            }}{{ option === 'single' ? 'Single Visit Transaction' : 'Course Transaction' }}
          </button>
        </div>
      </div>
      <label
        >ค้นหาผู้ป่วย<input v-model="search" placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
      /></label>
      <div>
        <label>ช่วงวันที่</label>
        <div class="transaction-dates">
          <ThaiDatePicker v-model="from" placeholder="ตั้งแต่วันที่" numeric-month /><ThaiDatePicker
            v-model="to"
            placeholder="ถึงวันที่"
            numeric-month
          />
        </div>
      </div>
    </section>
    <section class="transaction-metrics">
      <article v-for="metric in metrics" :key="metric[0]">
        <p>{{ metric[0] }}</p>
        <strong>{{ loading ? '…' : metric[1] }}</strong>
      </article>
    </section>
    <section>
      <header class="results-heading">
        <div>
          <h2>{{ title }}</h2>
          <p>
            {{
              type === 'single'
                ? 'รายการขาย · Single Visit'
                : 'รายการใช้สิทธิ์คอร์ส · ไม่ใช่ยอดขายใหม่'
            }}
          </p>
        </div>
        <div class="results-actions">
          <button v-if="type === 'single'" class="action" type="button" @click="sourceTierOpen = true">
            ดูต้นทางยอดขายและฐาน Tier
          </button>
          <RouterLink v-else class="action" to="/staff/transactions">ดูรายการซื้อคอร์ส</RouterLink>
        </div>
      </header>
      <p v-if="error" role="alert">{{ error }}</p>
      <div class="transaction-table">
        <table>
          <thead>
            <tr>
              <th
                v-for="heading in type === 'single'
                  ? [
                      'เลขที่รายการ',
                      'วันที่ทำรายการ',
                      'สาขา',
                      'ผู้รักษา / ผู้ขาย',
                      'ประเภท / กฎ',
                      'จำนวนเงินที่ขาย',
                      'ค่ามือ',
                    ]
                  : [
                      'รหัสคอร์ส',
                      'วันที่ทำรายการ',
                      'สาขา',
                      'ผู้ป่วย',
                      'นักกายภาพบำบัด',
                      'ค่ามือ',
                      'จำนวนครั้ง',
                      'มูลค่าบริการ',
                    ]"
                :key="heading"
              >
                {{ heading }}
              </th>
            </tr>
          </thead>
          <tbody v-if="!loading && !error && type === 'single'">
            <tr v-for="row in salesRows.slice(start, start + 10)" :key="row.id">
              <td>{{ row.receiptNo }}</td>
              <td>{{ date(row.soldAt) }}</td>
              <td>{{ branchNames[row.branchCode] || row.branchCode }}</td>
              <td>{{ row.salesperson?.fullName || '—' }}</td>
              <td>Single Visit / —</td>
              <td>{{ money(row.total) }}</td>
              <td>—</td>
            </tr>
          </tbody>
          <tbody v-else-if="!loading && !error">
            <tr v-for="row in courseRows.slice(start, start + 10)" :key="row.id">
              <td>{{ row.courseCode }}</td>
              <td>{{ date(row.visitDate) }}</td>
              <td>{{ row.branchName || row.branchCode }}</td>
              <td>{{ row.patientName || row.patientHn }}</td>
              <td>{{ row.treatingStaffName || '—' }}</td>
              <td>{{ money(row.treatmentFee) }}</td>
              <td>1 ครั้ง</td>
              <td>{{ money(row.gross) }}</td>
            </tr>
          </tbody>
          <tbody v-if="loading || error || !count">
            <tr>
              <td :colspan="type === 'single' ? 7 : 8" class="empty">
                {{
                  loading
                    ? 'กำลังโหลดข้อมูล…'
                    : error
                      ? 'ไม่สามารถโหลดรายการได้'
                      : 'ไม่พบรายการธุรกรรม'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav class="transaction-pagination" aria-label="เปลี่ยนหน้าธุรกรรม">
        <button :disabled="page === 1" @click="page--">← ก่อนหน้า</button
        ><span>หน้า {{ page }} จาก {{ pageCount }}</span
        ><button :disabled="page >= pageCount" @click="page++">ถัดไป →</button>
      </nav>
      <footer>
        <span>แสดง {{ Math.min(10, Math.max(0, count - start)) }} จาก {{ count }} รายการ</span
        ><span v-if="updated">อัปเดตล่าสุด {{ updated }}</span>
      </footer>
    </section>

    <div
      v-if="sourceTierOpen"
      class="transaction-modal-backdrop"
      @click.self="sourceTierOpen = false"
    >
      <section
        class="transaction-source-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="transaction-source-title"
      >
        <header>
          <div>
            <h2 id="transaction-source-title">รายละเอียดต้นทางยอดขายและฐาน Tier</h2>
          </div>
          <button
            type="button"
            class="modal-close"
            aria-label="ปิด"
            @click="sourceTierOpen = false"
          >
            ×
          </button>
        </header>

        <dl v-if="sourceDetail" class="source-detail-list">
          <div>
            <dt>เลขที่ / ใบเสร็จ</dt>
            <dd>{{ sourceDetail.receipt }}</dd>
          </div>
          <div>
            <dt>คอร์ส</dt>
            <dd>{{ sourceDetail.course }}</dd>
          </div>
          <div>
            <dt>ผู้ขาย / สาขา / วันขาย</dt>
            <dd>
              {{ sourceDetail.seller }} · {{ sourceDetail.branch }} ·
              {{ sourceDetail.soldAt }}
            </dd>
          </div>
          <div>
            <dt>ราคาก่อนลด / ส่วนลดรายการ</dt>
            <dd>
              {{ money(sourceDetail.gross) }} /
              {{ money(Math.max(0, sourceDetail.gross - sourceDetail.net)) }}
            </dd>
            <small>ราคาหลังหักส่วนลด: {{ money(sourceDetail.net) }}</small>
          </div>
          <div>
            <dt>มูลค่าสุทธิสำหรับ Tier</dt>
            <dd>{{ money(sourceDetail.net) }} / {{ money(sourceDetail.net) }}</dd>
            <small>มูลค่าหลังหักส่วนลดที่นำไปคิดฐาน Tier</small>
          </div>
          <div>
            <dt>ยอดขายคอร์สรอบเดือน / Tier</dt>
            <dd>{{ money(tierBase) }} / —</dd>
            <small>Tier จะแสดงเมื่อมีข้อมูล Scheme ของรอบนั้น</small>
          </div>
          <p class="source-detail-note">
            หมายเหตุ:
            {{ type === 'single' ? 'CoursePurchase' : 'Course Transaction' }} ·
            ยอดและค่าคอมมิชชั่นจะอิงจากรายการที่บันทึกในระบบ
          </p>
        </dl>
        <p v-else class="source-detail-empty">ไม่พบรายการที่ใช้คำนวณฐาน Tier</p>

        <footer>
          <button type="button" class="modal-back" @click="sourceTierOpen = false">กลับ</button>
          <button type="button" class="modal-done" @click="openTierRoundDetails">
            ดูรายละเอียดรอบและฐาน Tier
          </button>
        </footer>
      </section>
    </div>
  </main>
</template>
<style scoped>
.transaction-workspace > * {
  min-width: 0;
}
.transaction-workspace {
  box-sizing: border-box;
  max-width: 100%;
}
.transaction-workspace {
  padding: 56px 40px;
  display: grid;
  gap: 32px;
  color: #25372e;
  background: #f8f4ee;
}
.transaction-workspace h1 {
  margin: 0;
  font-size: clamp(32px, 3.2vw, 52px);
  line-height: 1.3;
}
.transaction-workspace p {
  color: #52645b;
  margin: 8px 0 0;
}
.transaction-workspace > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}
.transaction-report-select {
  display: grid;
  width: min(280px, 100%);
  gap: 7px;
  color: #52645b;
  font-size: 14px;
  font-weight: 700;
}
.results-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}
.transaction-filters {
  display: grid;
  grid-template-columns: 1.4fr 1.1fr 1fr;
  gap: 16px;
  padding: 24px;
  background: white;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
  align-items: end;
}
.transaction-filters label {
  display: grid;
  gap: 10px;
  color: #52645b;
}
.type-choices {
  display: flex;
  gap: 4px;
  margin-top: 10px;
}
.type-choices button {
  min-height: 52px;
  padding: 12px 14px;
  white-space: nowrap;
  border: 0;
  border-radius: 8px;
  background: white;
  color: #52645b;
  font: inherit;
  font-size: 15px;
}
.type-choices button.active {
  background: #1e807c;
  color: white;
  font-weight: 600;
}
.transaction-filters input {
  height: 52px;
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d9e1dc;
  border-radius: 8px;
  padding: 14px;
  font: inherit;
  min-width: 0;
}
.transaction-filters > div,
.transaction-filters > label {
  min-width: 0;
}
.transaction-dates {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 10px;
}
.transaction-dates :deep(.date-trigger) {
  min-height: 52px;
}
@media (min-width: 601px) {
  .transaction-dates :deep(.thai-date-picker:last-child .calendar) {
    left: auto;
    right: 0;
  }
}
.transaction-metrics {
  display: flex;
  gap: 16px;
}
.transaction-metrics article {
  flex: 1;
  min-width: 0;
  padding: 24px;
  background: white;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
}
.transaction-metrics strong {
  display: block;
  font-size: 30px;
  margin-top: 6px;
}
.results-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}
.results-heading h2 {
  margin: 0;
  font-size: 26px;
}
.action {
  background: #1e8883;
  color: white;
  padding: 12px 20px;
  border: 0;
  border-radius: 10px;
  text-decoration: none;
  font: inherit;
  cursor: pointer;
}
.transaction-table {
  overflow: auto;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
  background: white;
}
.transaction-table table {
  border-collapse: collapse;
  width: 100%;
  min-width: 1000px;
}
.transaction-table th {
  background: #1e807c;
  color: white;
  text-align: left;
  font-weight: 500;
  padding: 24px;
  white-space: nowrap;
}
.transaction-table td {
  padding: 28px 24px;
  border-bottom: 1px solid #d9e1dc;
  white-space: nowrap;
}
.transaction-table tr:last-child td {
  border: 0;
}
.transaction-table .empty {
  text-align: center;
}
.transaction-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0;
}
.transaction-pagination button {
  border: 0;
  border-radius: 14px;
  padding: 10px 20px;
  background: #1e807c;
  color: white;
  font: inherit;
  cursor: pointer;
}
.transaction-pagination button:disabled {
  opacity: 0.45;
  background: white;
  color: #52645b;
  cursor: default;
}
.transaction-workspace footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: #52645b;
  font-size: 14px;
}
.transaction-modal-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(21 42 40 / 58%);
}
.transaction-source-modal {
  width: min(560px, 100%);
  max-height: min(740px, calc(100vh - 48px));
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 70px rgb(15 43 40 / 28%);
}
.transaction-source-modal > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px 14px;
  border-bottom: 1px solid #e0e9e6;
}
.transaction-source-modal h2 {
  margin: 0;
  font-size: 19px;
}
.modal-close {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #eef6f3;
  color: #23615d;
  font-size: 25px;
  line-height: 1;
  cursor: pointer;
}
.source-detail-list {
  min-height: 0;
  overflow: auto;
  margin: 0;
  padding: 0 26px 18px;
}
.source-detail-list > div {
  padding: 12px 0;
  border-bottom: 1px solid #dce8e5;
}
.source-detail-list dt {
  margin-bottom: 8px;
  color: #28514d;
  font-size: 13px;
  font-weight: 700;
}
.source-detail-list dd {
  margin: 0;
  color: #263e3b;
  font-size: 14px;
}
.source-detail-list small,
.source-detail-note {
  display: block;
  margin-top: 7px;
  color: #718984;
  font-size: 12px;
}
.source-detail-note {
  margin-bottom: 0;
  padding-top: 14px;
}
.source-detail-empty {
  padding: 26px;
  color: #718984;
}
.transaction-source-modal > footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 26px 20px;
  border-top: 1px solid #e0e9e6;
}
.modal-done {
  min-width: 118px;
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: #1d8b85;
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.modal-back {
  min-width: 118px;
  min-height: 42px;
  border: 1px solid #b8cbc6;
  border-radius: 8px;
  background: #fff;
  color: #267e78;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
@media (max-width: 1200px) {
  .transaction-filters {
    grid-template-columns: 1fr 1fr;
  }
  .transaction-filters > div:first-child {
    grid-column: 1/-1;
  }
}
@media (max-width: 650px) {
  .transaction-workspace {
    padding: 28px 16px;
    gap: 20px;
  }
  .transaction-workspace > header {
    align-items: stretch;
    flex-direction: column;
  }
  .transaction-report-select {
    width: 100%;
  }
  .transaction-filters {
    grid-template-columns: 1fr;
    padding: 16px;
  }
  .type-choices {
    flex-direction: column;
  }
  .transaction-metrics {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .transaction-metrics article {
    padding: 16px;
  }
  .transaction-metrics strong {
    font-size: 24px;
  }
  .results-heading {
    align-items: start;
    flex-direction: column;
  }
  .results-actions {
    width: 100%;
    justify-content: stretch;
  }
  .results-actions .action {
    flex: 1 1 100%;
    text-align: center;
  }
  .transaction-modal-backdrop {
    padding: 12px;
  }
  .transaction-source-modal > header,
  .transaction-source-modal > footer {
    padding-inline: 18px;
  }
  .source-detail-list {
    padding-inline: 18px;
  }
  .transaction-workspace footer {
    flex-direction: column;
  }
  .transaction-pagination button {
    padding: 10px;
  }
  .transaction-dates {
    grid-template-columns: 1fr;
  }
}
</style>
