<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'

type ClosingRow = {
  id: number
  staff: string
  sales: number
  rate: number
  status: 'Open' | 'Closed' | 'Finalized'
  reopenReason: string | null
}

const selectedMonth = ref(`${clinicToday().slice(0, 8)}01`)
const month = computed(() => selectedMonth.value.slice(0, 7))
const router = useRouter()
const search = ref('')
const rows = ref<ClosingRow[]>([])
const errorMessage = ref('')
const isConfirming = ref(false)
const isSaving = ref(false)
const closedSuccessfully = ref(false)
const validationLoading = ref(false)
const validationResults = ref({
  tiers: null as boolean | null,
  sales: null as boolean | null,
  courses: null as boolean | null,
  treatment: null as boolean | null,
})

async function validatePeriod() {
  validationLoading.value = true
  const [year, number] = month.value.split('-').map(Number)
  const from = `${month.value}-01`
  const to = `${month.value}-${String(new Date(year!, number!, 0).getDate()).padStart(2, '0')}`

  const [schemesResult, salesResult, treatmentResult] = await Promise.allSettled([
    commissionApi.schemes(),
    commissionApi.report(from, to),
    commissionApi.treatmentReport(from, to),
  ])

  validationResults.value.sales =
    salesResult.status === 'fulfilled'
      ? salesResult.value.length > 0 &&
        salesResult.value.every((item) => item.staffId !== null && Number(item.base) >= 0)
      : null

  if (treatmentResult.status === 'fulfilled') {
    validationResults.value.courses = treatmentResult.value.every(
      (item) => Boolean(item.courseCode) && item.ownerStaffId !== null,
    )
    validationResults.value.treatment = treatmentResult.value.every(
      (item) => Number(item.treatmentFee) <= Number(item.gross),
    )
  } else {
    validationResults.value.courses = null
    validationResults.value.treatment = null
  }

  if (schemesResult.status === 'fulfilled' && Array.isArray(schemesResult.value)) {
    const scheme = (schemesResult.value as Array<{ id: number }>)[0]
    if (scheme) {
      try {
        const tierData = (await commissionApi.tiers(scheme.id)) as Array<{
          minimum: number
          maximum: number | null
        }>
        const tiers = [...tierData].sort((a, b) => Number(a.minimum) - Number(b.minimum))
        validationResults.value.tiers =
          tiers.length > 0 &&
          tiers.every((tier, index) => {
            if (index === 0) return Number(tier.minimum) === 0
            const previous = tiers[index - 1]!
            // tier ranges are in baht with two decimals: the next tier starts exactly one satang above the previous maximum
            return (
              previous.maximum !== null &&
              Math.round((Number(tier.minimum) - Number(previous.maximum)) * 100) === 1
            )
          })
      } catch {
        validationResults.value.tiers = null
      }
    } else validationResults.value.tiers = false
  } else validationResults.value.tiers = null
  validationLoading.value = false
}

async function load() {
  void validatePeriod()
  try {
    const data = await commissionApi.closings(month.value)
    rows.value = data.map((row) => ({
      id: row.id,
      staff: row.employeeName,
      sales: Number(row.sales),
      rate: Math.round(Number(row.rate) * 10000) / 100,
      status:
        row.status === 'FINALIZED' ? 'Finalized' : row.status === 'CLOSED' ? 'Closed' : 'Open',
      reopenReason: row.reopenReason ?? null,
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
    await load()
    isConfirming.value = false
    closedSuccessfully.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ปิดรอบไม่สำเร็จ'
    isConfirming.value = false
    closedSuccessfully.value = false
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
const reopenTarget = ref<ClosingRow | null>(null)
const reopenReason = ref('')
async function reopen() {
  const row = reopenTarget.value
  if (!row) return
  if (!reopenReason.value.trim()) {
    errorMessage.value = 'กรุณาระบุเหตุผลที่ Reopen'
    return
  }
  isSaving.value = true
  try {
    await commissionApi.reopenClosing(row.id, reopenReason.value.trim())
    reopenTarget.value = null
    reopenReason.value = ''
    closedSuccessfully.value = false
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Reopen ไม่สำเร็จ'
    reopenTarget.value = null
  } finally {
    isSaving.value = false
  }
}
const openCount = computed(() => rows.value.filter((row) => row.status === 'Open').length)
const totalPool = computed(() =>
  rows.value.reduce((sum, row) => sum + (row.sales * row.rate) / 100, 0),
)
const totalSales = computed(() => rows.value.reduce((sum, row) => sum + row.sales, 0))
const monthLabel = computed(() => {
  const [year, number] = month.value.split('-').map(Number)
  if (!year || !number) return month.value
  return new Intl.DateTimeFormat('th-TH', { month: 'short', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
})
const fullMonthLabel = computed(() => {
  const [year, number] = month.value.split('-').map(Number)
  if (!year || !number) return month.value
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
})
const periodLabel = computed(() => {
  const [year, number] = month.value.split('-').map(Number)
  if (!year || !number) return month.value
  return `1–${new Date(year, number, 0).getDate()} ${fullMonthLabel.value}`
})
const monthOptions = Array.from({ length: 12 }, (_, index) => {
  const date = new Date(2026, 9 - index, 1)
  return {
    value: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`,
    label: new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(date),
  }
})
const currency = (value: number) => `฿${value.toLocaleString()}`
const closeChecks = computed(() =>
  [
    { key: 'tiers', label: 'ไม่มีช่วง Tier ซ้อนกันหรือช่วงว่าง' },
    { key: 'sales', label: 'ยอดขายและข้อมูลผู้ขายครบถ้วน' },
    { key: 'courses', label: 'Course ID และเจ้าของคอร์สครบทุกคอร์ส' },
    { key: 'treatment', label: 'ค่ารักษาแทนไม่เกินยอดบริการ' },
  ].map((item) => ({
    ...item,
    result: validationResults.value[item.key as keyof typeof validationResults.value],
  })),
)
const hasBlockingIssues = computed(() => closeChecks.value.some((item) => item.result === false))
</script>

<template>
  <div class="closing-page commission-manager-page">
    <StaffHeader />
    <main class="closing-main commission-manager-main">
      <header class="commission-title">
        <h1>{{ closedSuccessfully ? 'ปิดรอบสำเร็จ' : 'ปิดรอบค่าคอมมิชชัน' }}</h1>
        <p>
          {{
            closedSuccessfully
              ? 'อัตราค่าคอมมิชชันของคอร์สในรอบนี้ถูกล็อกและบันทึก Audit Log แล้ว'
              : 'ตรวจยอดขาย กำหนด Tier และล็อกอัตราของคอร์สในรอบเดือน'
          }}
        </p>
      </header>
      <CommissionTabs active="closing" />
      <p v-if="errorMessage && !reopenTarget" class="reopen-error" role="alert">
        {{ errorMessage }}
      </p>
      <section class="commission-card commission-table-wrap">
        <table class="commission-table">
          <thead>
            <tr>
              <th>ผู้ขาย</th>
              <th>ยอดขาย</th>
              <th>อัตรา</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>{{ row.staff }}</td>
              <td>{{ currency(row.sales) }}</td>
              <td>{{ row.rate }}%</td>
              <td>
                {{
                  row.status === 'Finalized'
                    ? 'ปิดรอบแล้ว (จ่ายแล้ว)'
                    : row.status === 'Closed'
                      ? 'ปิดรอบแล้ว'
                      : 'รอปิดรอบ'
                }}
                <small v-if="row.reopenReason" class="reopen-reason"
                  >Reopen ล่าสุด: {{ row.reopenReason }}</small
                >
              </td>
              <td>
                <button
                  v-if="row.status === 'Closed'"
                  class="reopen-button"
                  type="button"
                  @click="reopenTarget = row"
                >
                  Reopen
                </button>
              </td>
            </tr>
            <tr v-if="visible.length === 0">
              <td colspan="5">ไม่พบข้อมูลตามตัวกรอง</td>
            </tr>
          </tbody>
        </table>
      </section>
      <section v-if="closedSuccessfully" class="closed-success commission-card">
        <i>✓</i>
        <h2>ปิดรอบ{{ fullMonthLabel }} เรียบร้อย</h2>
        <p>อัตราตาม Tier ถูกล็อกกับคอร์สในรอบเดือนแล้ว</p>
        <dl>
          <div>
            <dt>ยอดฐานที่ล็อก</dt>
            <dd>{{ currency(totalSales) }}</dd>
          </div>
          <div>
            <dt>Commission Pool</dt>
            <dd>{{ currency(totalPool) }}</dd>
          </div>
          <div>
            <dt>ผู้ดำเนินการ</dt>
            <dd>Manager / Finance</dd>
          </div>
        </dl>
        <button class="commission-primary" @click="router.push('/staff/commission')">
          กลับหน้าภาพรวม
        </button>
      </section>
      <template v-else>
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
        <section class="closing-workspace">
          <article class="commission-card calculation">
            <h2>รายละเอียดรอบคำนวณ</h2>
            <div class="calculation-period">
              <span>รอบคำนวณ</span>
              <div class="calculation-period-control">
                <FilterDropdown
                  v-model="selectedMonth"
                  :options="monthOptions"
                  :default-value="selectedMonth"
                  :default-label="fullMonthLabel"
                  label="เลือกรอบคำนวณ"
                  hide-default-option
                  :clear-on-reselect="false"
                /><small>ช่วงวันที่ {{ periodLabel }}</small>
              </div>
            </div>
            <div>
              <span>ยอดขายคอร์สรวม</span><b>{{ currency(totalSales) }}</b>
            </div>
            <div><span>ส่วนลดรายการ</span><b>รวมในฐานคำนวณสุทธิ</b></div>
            <div>
              <span>ฐานคำนวณ Tier</span><b>{{ currency(totalSales) }}</b>
            </div>
            <div class="pool">
              <span>Commission Pool ที่จะสร้าง</span><b>{{ currency(totalPool) }}</b>
            </div>
            <aside>เมื่อปิดรอบ อัตราที่คำนวณแล้วจะถูกล็อกตาม Course ID และแก้ไขไม่ได้โดยตรง</aside>
          </article>
          <article class="commission-card checklist">
            <h2>ตรวจสอบก่อนปิดรอบ</h2>
            <p v-if="validationLoading" class="checking">… กำลังตรวจสอบข้อมูลรอบนี้</p>
            <template v-else>
              <p
                v-for="check in closeChecks"
                :key="check.key"
                :class="{ warn: check.result === false, unknown: check.result === null }"
              >
                {{ check.result === true ? '✓' : check.result === false ? '!' : '?' }}
                {{ check.label }}
                <small v-if="check.result === null">ยังตรวจสอบไม่ได้</small>
              </p>
            </template>
            <button
              class="commission-primary"
              :disabled="validationLoading || hasBlockingIssues || openCount === 0"
              @click="isConfirming = true"
            >
              ยืนยันปิดรอบ
            </button>
          </article>
        </section>
      </template>
    </main>
    <div
      v-if="reopenTarget"
      class="modal-backdrop"
      role="presentation"
      @click.self="reopenTarget = null"
    >
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="reopen-title">
        <h2 id="reopen-title">Reopen รอบของ {{ reopenTarget.staff }}?</h2>
        <p>
          อัตราที่ล็อกไว้จะถูกปลดและค่าคอมมิชชันที่จัดสรรไว้จะถูกกลับรายการ ปิดรอบใหม่เพื่อคำนวณ
          Tier ตามยอดขายล่าสุด ทำได้ก่อนจ่ายเท่านั้น
        </p>
        <input
          v-model="reopenReason"
          aria-label="เหตุผลที่ Reopen"
          placeholder="เหตุผลที่ Reopen"
        />
        <p v-if="errorMessage" class="reopen-error" role="alert">{{ errorMessage }}</p>
        <div>
          <button class="cancel" type="button" :disabled="isSaving" @click="reopenTarget = null">
            ยกเลิก</button
          ><button class="confirm" type="button" :disabled="isSaving" @click="reopen">
            ยืนยัน Reopen
          </button>
        </div>
      </section>
    </div>
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
            ยกเลิก</button
          ><button class="confirm" type="button" :disabled="isSaving" @click="confirmClosing">
            {{ isSaving ? 'กำลังบันทึก...' : 'ยืนยันปิดรอบ' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-manager.css';
.closing-page {
  min-height: 100vh;
  background: #f9f4ee;
}
.closing-main {
  width: min(1540px, calc(100% - 10vw));
  margin: 0 auto;
  padding: 54px 0 64px;
}
.closing-period-picker {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
  padding: 18px 22px;
}
.closing-period-picker label {
  display: grid;
  width: min(320px, 100%);
  gap: 7px;
  color: #56665d;
  font-size: 0.9rem;
}
.closing-period-picker :deep(.date-field) {
  width: 100%;
}
.calculation .calculation-period {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.calculation-period-control {
  display: grid;
  width: min(320px, 100%);
  gap: 7px;
}
.calculation-period-control small {
  color: #667a72;
  text-align: left;
}
.calculation-period-control :deep(.filter-dropdown__button) {
  min-height: 46px;
  background: #fff;
}
.calculation-period-control :deep(.filter-dropdown__menu) {
  max-height: 270px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #7db8b2 #edf4f2;
}
.calculation-period-control :deep(.filter-dropdown__menu::-webkit-scrollbar) {
  width: 7px;
}
.calculation-period-control :deep(.filter-dropdown__menu::-webkit-scrollbar-track) {
  background: #edf4f2;
  border-radius: 999px;
}
.calculation-period-control :deep(.filter-dropdown__menu::-webkit-scrollbar-thumb) {
  background: #7db8b2;
  border-radius: 999px;
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
.closing-workspace {
  display: grid;
  grid-template-columns: 1.6fr 0.8fr;
  gap: 24px;
  margin-top: 24px;
}
.closing-workspace article {
  padding: 24px;
}
.closing-workspace h2 {
  margin: 0 0 18px;
}
.calculation > div {
  display: flex;
  justify-content: space-between;
  padding: 14px 0;
}
.calculation .pool {
  color: #16877f;
  font-weight: 700;
}
.calculation aside {
  margin-top: 24px;
  padding: 18px;
  border-radius: 9px;
  background: #fff1c7;
}
.checklist {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.checklist p {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #def3e7;
}
.checklist p.warn {
  background: #fff1c7;
}
.checklist button {
  margin-top: 0;
}
.checklist p.warn + button {
  margin-top: 6px;
}
.closed-success {
  width: min(760px, 100%);
  margin: 70px auto;
  padding: 42px;
  box-sizing: border-box;
  text-align: center;
}
.closed-success i {
  display: grid;
  width: 64px;
  height: 64px;
  margin: auto;
  place-items: center;
  border-radius: 50%;
  background: #dff4e8;
  color: #16875b;
  font-size: 2rem;
  font-style: normal;
}
.closed-success h2 {
  font-size: 1.8rem;
}
.closed-success p {
  color: #667a72;
}
.closed-success dl {
  display: grid;
  gap: 8px;
  margin: 26px auto;
  max-width: 500px;
  text-align: left;
}
.closed-success dl div {
  display: flex;
  justify-content: space-between;
  padding: 10px;
}
.closed-success button {
  width: 280px;
  max-width: 100%;
}
.checklist p.unknown,
.checklist p.checking {
  background: #eef1ef;
  color: #667a72;
}
.checklist p small {
  display: block;
  margin-top: 4px;
  color: #7c8983;
}
.checklist .commission-primary:disabled {
  cursor: not-allowed;
  opacity: 0.45;
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
  z-index: 9998;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #232b2280;
  backdrop-filter: blur(1.5px);
}
.confirm-modal {
  width: min(100%, 400px);
  padding: 24px;
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
  font-size: 20px;
  text-align: center;
}
.confirm-modal div {
  display: flex;
  gap: 10px;
  margin-top: 18px;
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
  min-height: 42px;
  border: 2px solid transparent;
  border-radius: 12px;
  background: #1e8b86;
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
  .closing-workspace {
    grid-template-columns: 1fr;
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
@media (min-width: 1201px) {
  .closing-panel {
    display: grid;
    grid-template-columns: 310px minmax(0, 1fr);
    gap: 28px;
    align-items: end;
  }
  .type-picker {
    width: 100%;
    margin: 0;
  }
  .closing-filters {
    grid-template-columns: minmax(260px, 1.4fr) minmax(180px, 0.9fr) minmax(150px, 0.8fr) auto;
    gap: 16px;
    padding: 0;
  }
  .search-field {
    grid-column: auto;
  }
  .error-message,
  .table-scroll {
    grid-column: 1 / -1;
  }
}
.reopen-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.reopen-button {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid #d9e1dc;
  border-radius: 8px;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.confirm-modal input {
  width: 100%;
  min-height: 42px;
  margin-top: 8px;
  padding: 0 12px;
  box-sizing: border-box;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  font: inherit;
}
</style>
