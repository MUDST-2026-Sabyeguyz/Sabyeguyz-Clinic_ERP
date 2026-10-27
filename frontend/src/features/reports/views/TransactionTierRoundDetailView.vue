<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { commissionApi, type CommissionFormula } from '@/api/client'

type CommissionRow = Awaited<ReturnType<typeof commissionApi.report>>[number]
type TreatmentRow = Awaited<ReturnType<typeof commissionApi.treatmentReport>>[number]
const route = useRoute()
const router = useRouter()
const formula = ref<CommissionFormula | null>(null)
const rows = ref<CommissionRow[]>([])
const treatments = ref<TreatmentRow[]>([])
const loading = ref(true)
const errorMessage = ref('')
const selectedStaffId = ref<number | null>(null)
const now = new Date()
const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const from = `${month}-01`
const to = `${month}-${String(new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()).padStart(2, '0')}`
const money = (v: number) => `฿${Number(v).toLocaleString('th-TH', { maximumFractionDigits: 2 })}`
const monthLabel = new Intl.DateTimeFormat('th-TH', {
  month: 'long',
  year: 'numeric',
}).format(new Date(`${month}-01T12:00:00`))
const sourceBase = computed(() => Number(route.query.base ?? 0))
const pageMode = computed(() => String(route.query.mode ?? 'overview'))
const paymentDate = ref(new Date().toISOString().slice(0, 10))
const paymentMethod = ref('โอนเงินผ่านธนาคาร')
const paymentReference = ref('')
const confirmed = ref(false)
const reopenReason = ref('')
function openMode(mode: 'finalize' | 'reopen') {
  void router.push({ query: { ...route.query, mode } })
}
function returnToOverview() {
  const query = { ...route.query }
  delete query.mode
  void router.push({ query })
}
const treatmentByStaff = computed(() => {
  const totals = new Map<number, number>()
  treatments.value.forEach((item) => {
    if (item.treatingStaffId)
      totals.set(
        item.treatingStaffId,
        (totals.get(item.treatingStaffId) ?? 0) + Number(item.treatmentFee),
      )
  })
  return totals
})
const totalCommission = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.commission), 0),
)
const totalTreatment = computed(() =>
  [...treatmentByStaff.value.values()].reduce((sum, value) => sum + value, 0),
)
const selectedRow = computed(
  () => rows.value.find((row) => row.staffId === selectedStaffId.value) ?? rows.value[0] ?? null,
)
const selectedTier = computed(
  () =>
    formula.value?.tiers.find(
      (tier) =>
        selectedRow.value &&
        Number(selectedRow.value.base) >= Number(tier.minimum) &&
        (tier.maximum === null || Number(selectedRow.value.base) <= Number(tier.maximum)),
    ) ?? null,
)
onMounted(async () => {
  try {
    const [commission, treatment, currentFormula] = await Promise.all([
      commissionApi.report(from, to),
      commissionApi.treatmentReport(from, to),
      commissionApi.formula(),
    ])
    rows.value = commission
    treatments.value = treatment
    formula.value = currentFormula
    selectedStaffId.value = commission[0]?.staffId ?? null
  } catch {
    errorMessage.value = 'ยังไม่มีข้อมูลรอบค่าตอบแทนที่ปิดแล้ว'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="round-page">
    <StaffHeader />
    <main v-if="pageMode === 'finalize'" class="action-main">
      <h1>Paid / Finalized รอบค่าตอบแทน</h1>
      <nav class="round-status">
        <span>OPEN</span><span class="active">CLOSED / PENDING PAYMENT</span
        ><span>PAID / FINALIZED</span>
      </nav>
      <section class="action-card">
        <h2>ยืนยันจ่ายและล็อกรอบถาวร</h2>
        <div class="payment-grid">
          <label
            >วันที่จ่าย *<ThaiDatePicker
              v-model="paymentDate"
              numeric-month
              show-calendar-icon /></label
          ><label
            >ช่องทางจ่าย *<FilterDropdown
              v-model="paymentMethod"
              :options="[
                { value: 'โอนเงินผ่านธนาคาร', label: 'โอนเงินผ่านธนาคาร' },
                { value: 'เงินสด', label: 'เงินสด' },
                { value: 'พร้อมเพย์', label: 'พร้อมเพย์' },
              ]"
              default-label="เลือกช่องทางจ่าย"
              label="ช่องทางจ่าย"
              hide-default-option
              :clear-on-reselect="false"
          /></label>
        </div>
        <label
          >เลขอ้างอิงการจ่าย<input
            v-model="paymentReference"
            placeholder="PAY-YYYYMMDD-001" /></label
        ><label class="check-line"
          ><input v-model="confirmed" type="checkbox" />
          ฉันตรวจสอบยอดสุทธิและยืนยันว่าชำระเงินแล้ว</label
        >
        <footer>
          <button class="secondary" type="button" @click="returnToOverview">ยกเลิก</button
          ><button class="primary" type="button" :disabled="!confirmed">Finalize รอบ</button>
        </footer>
      </section>
    </main>
    <main v-else-if="pageMode === 'reopen'" class="action-main">
      <h1>เปิดรอบค่าตอบแทนอีกครั้ง</h1>
      <p class="action-subtitle">สำหรับ Manager · งวดที่ปิดแล้วและยังไม่ Finalized</p>
      <section class="action-card reopen-card">
        <header>
          <div>
            <h2>ตรวจสอบก่อนเปิดรอบ</h2>
            <p>{{ monthLabel }} | Closed / Pending Payment</p>
          </div>
          <span class="closed-pill">รอบปิด</span>
        </header>
        <label
          >เหตุผลในการเปิดรอบอีกครั้ง *<textarea
            v-model="reopenReason"
            placeholder="ระบุเหตุผลในการเปิดรอบอีกครั้ง"
          ></textarea></label
        ><small>ระบบเก็บเหตุผล ผู้ดำเนินการ และเวลาไว้ในประวัติการเปิดรอบ</small>
        <footer>
          <button class="secondary" type="button" @click="returnToOverview">
            ดูประวัติการเปิดรอบ</button
          ><button class="primary" type="button" :disabled="!reopenReason.trim()">
            ยืนยันเปิดรอบอีกครั้ง
          </button>
        </footer>
      </section>
    </main>
    <main v-else class="round-main">
      <header class="round-heading">
        <span class="round-eyebrow">การจ่ายค่าตอบแทน</span>
        <h1>รอบค่าตอบแทน {{ monthLabel }}</h1>
        <p>ตรวจยอดรายบุคคลให้ครบก่อนบันทึกการจ่ายเงิน</p>
      </header>
      <section class="round-progress-note">
        <span class="pending-dot"></span>
        <div>
          <strong>สถานะ: ปิดรอบแล้ว · รอการยืนยันจ่ายเงิน</strong
          ><small>ตรวจสอบยอดด้านล่าง แล้วระบุวันที่และช่องทางจ่าย</small>
        </div>
      </section>
      <section class="round-kpis" aria-label="ยอดสรุปก่อนจ่าย">
        <article>
          <span>คอมมิชชันคอร์ส</span><strong>{{ money(totalCommission) }}</strong>
          <small>จากยอดขายคอร์สที่ปิดรอบแล้ว</small>
        </article>
        <article>
          <span>ค่ามือและค่ารักษา</span><strong>{{ money(totalTreatment) }}</strong>
          <small>จากรายการให้บริการในรอบนี้</small>
        </article>
        <article>
          <span>รายการปรับยอด</span><strong>—</strong><small>ยังไม่มีรายการปรับยอด</small>
        </article>
        <article class="net">
          <span>ยอดสุทธิรอจ่าย</span><strong>{{ money(totalCommission + totalTreatment) }}</strong>
          <small>ยอดรวมก่อนยืนยันจ่ายเงิน</small>
        </article>
      </section>
      <section class="round-table">
        <header class="section-heading">
          <div>
            <h2>ผู้รับเงินในรอบนี้</h2>
            <p>เลือกแถวเพื่อดูฐาน Tier และยอดคำนวณของบุคลากร</p>
          </div>
          <span v-if="!loading && !errorMessage" class="person-count">{{ rows.length }} คน</span>
        </header>
        <p v-if="loading" class="state">กำลังโหลดข้อมูล...</p>
        <p v-else-if="errorMessage" class="state error">{{ errorMessage }}</p>
        <table v-else>
          <thead>
            <tr>
              <th>ผู้รับเงิน</th>
              <th>คอมมิชชันคอร์ส</th>
              <th>ค่ามือ</th>
              <th>ปรับยอด</th>
              <th>ยอดสุทธิ</th>
              <th>สถานะ</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.staffId ?? row.staffName"
              :class="{ selected: selectedStaffId === row.staffId }"
              @click="selectedStaffId = row.staffId"
            >
              <td>{{ row.staffName }}</td>
              <td>{{ money(Number(row.commission)) }}</td>
              <td>
                {{ money(row.staffId ? (treatmentByStaff.get(row.staffId) ?? 0) : 0) }}
              </td>
              <td>—</td>
              <td>
                <b>{{
                  money(
                    Number(row.commission) +
                      (row.staffId ? (treatmentByStaff.get(row.staffId) ?? 0) : 0),
                  )
                }}</b>
              </td>
              <td><span class="pending">รอจ่าย</span></td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="6" class="empty">ไม่พบข้อมูลค่าตอบแทนในรอบนี้</td>
            </tr>
          </tbody>
        </table>
      </section>
      <section class="tier-detail">
        <header class="section-heading">
          <div>
            <h2>ฐาน Tier ของผู้ที่เลือก</h2>
            <p>ใช้ตรวจสอบที่มาของคอมมิชชันก่อนยืนยันจ่าย</p>
          </div>
        </header>
        <template v-if="selectedRow"
          ><label>พนักงาน / เดือนขาย</label>
          <div>{{ selectedRow.staffName }} · {{ monthLabel }}</div>
          <small>ตัวอย่างที่ใช้ดูรายละเอียดการขาย</small><label>รายการประกอบฐาน Tier</label>
          <div>
            {{
              sourceBase
                ? `Transaction ที่เลือก ${money(sourceBase)}`
                : `${selectedRow.saleCount} รายการขาย`
            }}
          </div>
          <small>ฐานหลังหักส่วนลดรายรายการ ก่อนส่วนลดท้ายบิล</small
          ><label>ยอดขายคอร์สรอบเดือน / อัตรา</label>
          <div>
            {{ money(Number(selectedRow.base)) }} /
            {{
              selectedTier ? `${(Number(selectedTier.rate) * 100).toLocaleString('th-TH')}%` : '—'
            }}
          </div>
          <small>เฉพาะ CoursePurchase · ไม่รวมการใช้สิทธิ์และ Adjustment เงิน</small></template
        >
        <p v-else class="state">เลือกพนักงานจากตารางเพื่อดูรายละเอียดฐาน Tier</p>
      </section>
      <footer class="round-actions">
        <button class="secondary" type="button" @click="openMode('reopen')">
          เปิดรอบเพื่อแก้ไข</button
        ><button class="primary" type="button" @click="openMode('finalize')">
          ดำเนินการยืนยันจ่าย
        </button>
      </footer>
    </main>
  </div>
</template>

<style>
.round-page {
  min-height: 100vh;
  background: #f8f4ee;
  color: #263e3a;
}
.round-main {
  width: min(1280px, calc(100% - 48px));
  margin: auto;
  padding: 72px 0;
}
.round-heading h1,
.round-table h2,
.tier-detail h2 {
  margin: 0;
}
.round-eyebrow {
  display: block;
  margin-bottom: 8px;
  color: #16837d;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.round-heading h1 {
  font-size: clamp(1.75rem, 3vw, 2.25rem);
}
.round-heading p {
  margin: 8px 0 0;
  color: #728580;
}
.primary {
  background: #1d8c86;
  color: #fff;
}
.round-progress-note {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 760px;
  margin-bottom: 28px;
  padding: 13px 16px;
  border-radius: 10px;
  background: #fff7dc;
  color: #6e5721;
}
.round-progress-note div {
  display: grid;
  gap: 2px;
}
.round-progress-note small {
  color: #81734f;
  font-size: 0.8rem;
}
.pending-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #d59b20;
  box-shadow: 0 0 0 5px #ffebad;
}
.round-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 30px;
}
.round-kpis article {
  display: grid;
  gap: 7px;
  padding: 20px;
  border: 1px solid #d9e4e0;
  border-radius: 12px;
  background: #fff;
}
.round-kpis span,
.tier-detail label {
  color: #728580;
  font-size: 14px;
}
.round-kpis strong {
  font-size: 1.5rem;
}
.round-kpis small {
  color: #788984;
  font-size: 0.75rem;
  line-height: 1.45;
}
.round-kpis .net strong {
  color: #16837d;
}
.round-table,
.tier-detail {
  padding: 24px;
  border: 1px solid #d9e4e0;
  border-radius: 12px;
  background: #fff;
}
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.section-heading p {
  margin: 6px 0 0;
  color: #728580;
  font-size: 0.84rem;
}
.person-count {
  flex: none;
  padding: 7px 10px;
  border-radius: 999px;
  color: #52726b;
  background: #eef5f2;
  font-size: 0.78rem;
  font-weight: 700;
}
.round-table table {
  width: 100%;
  margin-top: 18px;
  border-collapse: collapse;
}
.round-table th {
  padding: 12px 14px;
  border-bottom: 1px solid #d9e4e0;
  color: #728580;
  font-size: 13px;
  text-align: left;
}
.round-table td {
  padding: 15px 14px;
  border-bottom: 1px solid #d9e4e0;
  cursor: pointer;
}
.round-table tr.selected td {
  background: #eef8f5;
}
.round-table tbody tr:hover td {
  background: #f7fbf9;
}
.pending {
  padding: 7px 18px;
  border-radius: 999px;
  background: #fff0b7;
  font-size: 13px;
  font-weight: 700;
}
.empty,
.state {
  padding: 24px;
  color: #728580;
}
.state.error {
  color: #bf4242;
}
.tier-detail {
  margin-top: 22px;
  display: grid;
  gap: 10px;
}
.tier-detail label {
  margin-top: 10px;
  font-weight: 700;
}
.tier-detail div {
  padding: 15px;
  border: 1px solid #d9e4e0;
  border-radius: 8px;
  font-weight: 600;
}
.tier-detail small {
  color: #728580;
}
.round-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid #d9e4e0;
}
.round-actions button {
  min-width: 180px;
  padding: 13px 18px;
  border-radius: 8px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.secondary {
  border: 1px solid #d9e4e0;
  background: #fff;
  color: #2e4a46;
}
.primary {
  border: 0;
  opacity: 1;
}
.primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
@media (max-width: 760px) {
  .round-main {
    width: min(100% - 28px, 1280px);
    padding: 36px 0;
  }
  .round-kpis {
    grid-template-columns: 1fr 1fr;
  }
  .round-table {
    overflow: auto;
  }
  .round-table table {
    min-width: 760px;
  }
  .round-actions {
    flex-direction: column;
  }
  .round-actions button {
    width: 100%;
  }
}
.action-main {
  width: min(1120px, calc(100% - 48px));
  margin: auto;
  padding: 56px 0 80px;
}
.action-main > h1 {
  margin: 0;
  font-size: 34px;
}
.action-subtitle {
  margin: 10px 0 42px;
  color: #607671;
}
.action-card {
  width: min(860px, 100%);
  margin: 42px auto 0;
  padding: 38px 42px;
  border: 1px solid #d9e4e0;
  border-radius: 18px;
  background: #fff;
  display: grid;
  gap: 24px;
}
.action-card h2 {
  margin: 0;
  font-size: 27px;
}
.final-warning,
.reopen-note {
  display: grid;
  gap: 10px;
  padding: 20px 22px;
  border-radius: 12px;
  background: #fff0b7;
  color: #624d1d;
}
.final-warning b {
  color: #c7443d;
}
.payment-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
}
.action-card label {
  display: grid;
  gap: 9px;
  font-weight: 700;
}
.action-card input:not([type='checkbox']),
.action-card select,
.action-card textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 52px;
  padding: 12px 14px;
  border: 1px solid #d3e0dc;
  border-radius: 8px;
  background: #fff;
  color: #324b47;
  font: inherit;
}
.action-card .thai-date-picker,
.action-card .filter-dropdown {
  width: 100%;
}
.action-card .date-trigger,
.action-card .filter-dropdown__button {
  min-height: 52px;
  border-color: #d3e0dc;
  border-radius: 8px;
}
.action-card textarea {
  min-height: 148px;
  resize: vertical;
}
.check-line {
  display: flex !important;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.check-line input {
  width: 30px;
  height: 30px;
  accent-color: #1d8c86;
}
.action-card footer {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 42px;
}
.action-card > footer > button {
  min-width: 190px;
  padding: 14px 18px;
  border-radius: 8px;
  font: inherit;
  font-weight: 700;
}
.reopen-card > header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
.reopen-card header p {
  margin: 18px 0 0;
  color: #71857f;
}
.closed-pill {
  align-self: start;
  padding: 10px 14px;
  border-radius: 999px;
  background: #eef4f1;
  color: #53706a;
}
.reopen-note {
  background: #eef4f1;
  color: #3d5954;
}
.action-card small {
  color: #71857f;
}
@media (max-width: 760px) {
  .action-main {
    width: min(100% - 28px, 1120px);
    padding: 36px 0;
  }
  .payment-grid {
    grid-template-columns: 1fr;
  }
  .action-card {
    padding: 24px 20px;
  }
  .action-card footer {
    flex-direction: column;
  }
  .action-card > footer > button {
    width: 100%;
  }
}
</style>
