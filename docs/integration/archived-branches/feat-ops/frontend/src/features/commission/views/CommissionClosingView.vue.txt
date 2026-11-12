<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'

type ClosingRow = {
  id: number
  staff: string
  sales: number
  rate: number
  status: 'Open' | 'Closed'
}

const selectedMonth = ref(`${new Date().toISOString().slice(0, 7)}-01`)
const month = computed(() => selectedMonth.value.slice(0, 7))
const router = useRouter()
const rows = ref<ClosingRow[]>([])
const errorMessage = ref('')
const isConfirming = ref(false)
const isSaving = ref(false)
const closedSuccessfully = ref(false)
const validationLoading = ref(false)
const draftMessage = ref('')
const reportRows = ref<Awaited<ReturnType<typeof commissionApi.report>>>([])
const schemeLabel = ref('ยังไม่มีข้อมูล Scheme')
const schemePeriodLabel = ref('ยังไม่มีข้อมูลช่วงวันที่')
const shortDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(`${value}T12:00:00`),
  )
const totalBase = computed(() => reportRows.value.reduce((sum, r) => sum + Number(r.base), 0))
const commissionPool = computed(() =>
  reportRows.value.reduce((sum, r) => sum + Number(r.commission), 0),
)
const reportCount = computed(() => reportRows.value.reduce((sum, r) => sum + r.saleCount, 0))
const proposedRate = computed(() => {
  const rates = [...new Set(reportRows.value.map((r) => Number(r.rate) * 100))]
  return rates.length === 1 ? `${rates[0]}%` : rates.length ? 'ตาม Tier รายบุคคล' : '—'
})
function saveDraft() {
  try {
    localStorage.setItem('commission-closing-draft-month', selectedMonth.value)
    draftMessage.value = 'บันทึกฉบับร่างแล้ว'
  } catch {
    draftMessage.value = 'บันทึกฉบับร่างในเครื่องไม่สำเร็จ'
  }
}
const validationResults = ref({
  tiers: null as boolean | null,
  sales: null as boolean | null,
  courses: null as boolean | null,
  treatment: null as boolean | null,
})

async function validatePeriod() {
  validationLoading.value = true
  reportRows.value = []
  schemeLabel.value = 'ยังไม่มีข้อมูล Scheme'
  schemePeriodLabel.value = 'ยังไม่มีข้อมูลช่วงวันที่'
  validationResults.value = { tiers: null, sales: null, courses: null, treatment: null }
  const [year, number] = month.value.split('-').map(Number)
  const from = `${month.value}-01`
  const to = `${month.value}-${String(new Date(year!, number!, 0).getDate()).padStart(2, '0')}`

  const [schemesResult, salesResult, treatmentResult] = await Promise.allSettled([
    commissionApi.schemes(),
    commissionApi.report(from, to),
    commissionApi.treatmentReport(from, to),
  ])

  if (salesResult.status === 'fulfilled') reportRows.value = salesResult.value
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
    const scheme = (
      schemesResult.value as Array<{
        id: number
        name: string
        active: boolean
        effectiveFrom: string
        effectiveTo: string | null
      }>
    ).find((s) => s.active && s.effectiveFrom <= to && (!s.effectiveTo || s.effectiveTo > to))
    if (scheme) {
      schemeLabel.value = scheme.name
      if (scheme.effectiveTo) {
        const lastDay = new Date(`${scheme.effectiveTo}T12:00:00`)
        lastDay.setDate(lastDay.getDate() - 1)
        const inclusiveEnd = `${lastDay.getFullYear()}-${String(lastDay.getMonth() + 1).padStart(2, '0')}-${String(lastDay.getDate()).padStart(2, '0')}`
        schemePeriodLabel.value = `${shortDate(scheme.effectiveFrom)}–${shortDate(inclusiveEnd)}`
      } else schemePeriodLabel.value = `${shortDate(scheme.effectiveFrom)} เป็นต้นไป`

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
            return (
              previous.maximum !== null &&
              Math.abs(Number(tier.minimum) - Number(previous.maximum) - 0.01) < 0.001
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
  closedSuccessfully.value = false
  draftMessage.value = ''
  void validatePeriod()
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

const openCount = computed(() => rows.value.filter((row) => row.status === 'Open').length)
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
  const now = new Date()
  const date = new Date(now.getFullYear(), now.getMonth() - index, 1)
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
const hasBlockingIssues = computed(() => closeChecks.value.some((item) => item.result !== true))
const passedChecks = computed(() => closeChecks.value.filter((item) => item.result === true).length)

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
      <div v-if="!closedSuccessfully" class="closing-month-selector">
        <FilterDropdown
          v-model="selectedMonth"
          :options="monthOptions"
          :default-value="selectedMonth"
          :default-label="periodLabel"
          label="เลือกรอบคำนวณ"
          hide-default-option
          :clear-on-reselect="false"
        />
      </div>
      <CommissionTabs active="closing" />
      <p v-if="errorMessage" class="state-card error" role="alert">{{ errorMessage }}</p>
      <section v-if="closedSuccessfully" class="closed-success commission-card">
        <i>✓</i>
        <h2>ปิดรอบ{{ fullMonthLabel }} เรียบร้อย</h2>
        <p>{{ schemeLabel }} · บันทึกรอบและล็อกอัตราสำเร็จ</p>
        <dl>
          <div>
            <dt>ยอดฐานที่ล็อก</dt>
            <dd>{{ currency(totalBase) }}</dd>
          </div>
          <div>
            <dt>Commission Pool</dt>
            <dd>{{ currency(commissionPool) }}</dd>
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
            <span>ยอดขายคอร์ส</span><b>{{ currency(totalBase) }}</b
            ><small>ฐานสุทธิจากรายงาน · {{ reportCount }} รายการขาย</small>
          </article>
          <article>
            <span>อัตราที่เสนอ</span><b>{{ proposedRate }}</b>
          </article>
          <article>
            <span>คอร์สรอล็อก</span><b>—</b><small>ระบบยังไม่ส่งจำนวนคอร์สรอล็อก</small>
          </article>
          <article>
            <span>สถานะรอบ</span><b>{{ rows.length && !openCount ? 'ปิดรอบแล้ว' : 'เปิดอยู่' }}</b
            ><small>{{ fullMonthLabel }}</small>
          </article>
        </section>
        <section class="closing-workspace">
          <article class="commission-card calculation">
            <h2>รายละเอียดรอบคำนวณ</h2>
            <p class="calculation-caption">สรุปฐานที่ใช้คำนวณค่าคอมมิชชันก่อนปิดรอบ</p>
            <div class="period-cards">
              <div class="period-card">
                <span>รอบคำนวณ</span><b>{{ periodLabel }}</b>
              </div>
              <div class="period-card scheme-card">
                <span>Commission Scheme</span><b>{{ schemePeriodLabel }}</b>
              </div>
            </div>
            <div class="calculation-breakdown">
              <div><span>ยอดขายคอร์สรวมก่อนส่วนลด</span><b>—</b></div>
              <div><span>ส่วนลดรายรายการ</span><b>—</b></div>
              <div class="base-total">
                <span>ฐานคำนวณ Tier</span><b>{{ currency(totalBase) }}</b>
              </div>
            </div>
            <p class="calculation-caption">ระบบยังไม่ส่งยอดก่อนส่วนลดและส่วนลดแยกในรายงานรอบนี้</p>
            <div class="pool">
              <span>Commission Pool ที่จะสร้าง<small>คำนวณตาม Tier ของแต่ละบุคคล</small></span
              ><b>{{ currency(commissionPool) }}</b>
            </div>
            <aside>
              <strong>เมื่อปิดรอบ อัตราค่าคอมมิชชันจะถูกล็อกตาม Course ID</strong
              ><br />การแก้ย้อนหลังต้องใช้ Admin Override พร้อมระบุเหตุผลและเก็บ Audit Log
            </aside>
          </article>
          <article class="commission-card checklist">
            <h2>ตรวจสอบก่อนปิดรอบ</h2>
            <p :class="{ warn: hasBlockingIssues }">
              <strong>{{
                validationLoading
                  ? 'กำลังตรวจสอบ'
                  : hasBlockingIssues
                    ? 'ต้องตรวจสอบเพิ่มเติม'
                    : 'พร้อมปิดรอบ'
              }}</strong
              ><small
                >ผ่าน {{ passedChecks }} รายการ · ต้องตรวจ {{ 4 - passedChecks }} รายการ</small
              >
            </p>
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
              :disabled="validationLoading || hasBlockingIssues"
              @click="isConfirming = true"
            >
              ปิดรอบ
            </button>
            <button class="commission-secondary" @click="saveDraft">บันทึกฉบับร่าง</button>
            <small v-if="draftMessage" role="status">{{ draftMessage }}</small>
            <small>เมื่อยืนยันแล้ว การแก้ไขต้องผ่าน Admin Override</small>
          </article>
        </section>
      </template>
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
.closing-month-selector {
  display: flex;
  justify-content: flex-end;
  margin: -60px 0 24px;
}
.closing-month-selector :deep(.filter-dropdown) {
  width: 280px;
}
.calculation .period-cards {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
  padding: 16px 0;
}
.period-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #dce5e1;
  border-radius: 9px;
  min-width: 0;
}
.period-card span {
  color: #6d7d78;
  font-size: 13px;
}
.period-card b {
  font-size: 16px;
  font-weight: 400;
}
.scheme-card {
  background: #e3f4ed;
  border-color: #e3f4ed;
}
@media (max-width: 900px) {
  .closing-month-selector {
    margin: 16px 0 20px;
  }
}
@media (max-width: 620px) {
  .calculation .period-cards {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .closing-month-selector :deep(.filter-dropdown) {
    width: 100%;
  }
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
  gap: 20px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: 42px;
  overflow: hidden;
  background: transparent;
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
.calculation-caption {
  color: #6d7d78;
  font-size: 14px;
}
.calculation .calculation-breakdown {
  display: block;
  padding: 12px 18px;
  border: 1px solid #dce5e1;
  border-radius: 10px;
  background: #fbfcfb;
}
.calculation-breakdown > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
}
.calculation-breakdown .base-total {
  border-top: 1px solid #dce5e1;
}
.calculation .pool {
  padding: 18px;
  border-radius: 10px;
  background: #d5efe4;
}
.pool small {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  font-weight: 400;
}
.closing-summary small {
  color: #6d7d78;
  font-size: 13px;
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
  border: 1px solid #dce5e1;
  border-radius: 12px;
  background: #fff;
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
</style>
