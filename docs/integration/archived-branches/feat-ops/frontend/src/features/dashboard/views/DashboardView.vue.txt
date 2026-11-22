<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { patientApi, reportApi } from '@/api/client'
import {
  clearSelectedManagerBranch,
  selectedManagerBranch,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'

const now = new Date()
const from = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`)
const to = ref(now.toISOString().slice(0, 10))
const managerBranch = computed(() => selectedManagerBranch())
const branchCode = computed(() => managerBranch.value?.code ?? '')
const lastManagerBranch = ref(selectedManagerBranch())
const selectedPeriod = ref('june-2569')
const customRangeOpen = ref(false)
const customFrom = ref(from.value)
const customTo = ref(to.value)
const hasCustomRange = ref(false)
const periodOptions = [
  { value: 'june-2569', label: 'มิถุนายน 2569' },
  { value: 'may-2569', label: 'พฤษภาคม 2569' },
  { value: 'november-2569', label: 'พฤศจิกายน 2569' },
]
const loading = ref(true)
const errorMessage = ref('')
const patients = ref(0)
const visits = ref(0)
const revenue = ref(0)
const courses = ref(0)
const genderBreakdown = ref<Array<{ gender: string; patients: number }>>([])
const appointmentStatuses = ref<Array<{ status: string; appointments: number }>>([])
const monthlyRevenue = ref<Array<{ label: string; course: number; singleVisit: number }>>([])
const statusOrder = ['COMPLETED', 'IN_SERVICE', 'NO_SHOW', 'CANCELLED']

const maxMonthlyRevenue = computed(() =>
  Math.max(...monthlyRevenue.value.flatMap((item) => [item.course, item.singleVisit]), 1),
)
const chartMaximum = computed(() => {
  const rawMaximum = maxMonthlyRevenue.value
  const magnitude = 10 ** Math.max(String(Math.floor(rawMaximum)).length - 1, 0)
  return Math.ceil(rawMaximum / magnitude) * magnitude
})
const chartTicks = computed(() =>
  Array.from({ length: 6 }, (_, index) => Math.round(chartMaximum.value * (1 - index / 5))),
)
const dashboardStatuses = computed(() => {
  const counts = new Map(appointmentStatuses.value.map((item) => [item.status, item.appointments]))
  return statusOrder.map((status) => ({ status, appointments: counts.get(status) ?? 0 }))
})
const maxAppointments = computed(() =>
  Math.max(...dashboardStatuses.value.map((item) => item.appointments), 1),
)
const showNoResults = computed(
  () =>
    !loading.value &&
    hasCustomRange.value &&
    !errorMessage.value &&
    patients.value === 0 &&
    visits.value === 0 &&
    revenue.value === 0 &&
    courses.value === 0 &&
    !monthlyRevenue.value.some((item) => item.course > 0 || item.singleVisit > 0) &&
    !appointmentStatuses.value.some((item) => item.appointments > 0),
)
const branchName = computed(() => managerBranch.value?.name ?? 'ทุกสาขา')
const dashboardSubtitle = computed(() =>
  managerBranch.value
    ? `สรุปการดำเนินงาน · สาขา${managerBranch.value.name} · ข้อมูลตัวอย่าง`
    : 'สรุปการดำเนินงาน · ทุกสาขา · ข้อมูลตัวอย่าง',
)
const money = (value: number) =>
  `${value.toLocaleString('th-TH', { maximumFractionDigits: 0 })} บาท`
const statusLabel = (status: string) =>
  ({
    CONFIRMED: 'ยืนยันแล้ว',
    ARRIVED: 'มาถึงแล้ว',
    IN_SERVICE: 'กำลังให้บริการ',
    COMPLETED: 'เสร็จสิ้น',
    CANCELLED: 'ยกเลิก',
    RESCHEDULED: 'เลื่อนนัด',
    NO_SHOW: 'ไม่มาตามนัด',
  })[status] ?? status
const statusClass = (status: string) => `status-${status.toLowerCase().replaceAll('_', '-')}`

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [summary, appointments, balances, patientList, revenueReport] = await Promise.all([
      reportApi.dashboard(from.value, to.value, branchCode.value || undefined),
      reportApi.appointments(from.value, to.value, branchCode.value || undefined),
      reportApi.balances(from.value, to.value, branchCode.value || undefined),
      patientApi.searchAll('', branchCode.value),
      reportApi.revenue(from.value, to.value, branchCode.value || undefined),
    ])
    patients.value = summary.uniquePatients || patientList.length
    visits.value = summary.visits
    revenue.value = Number(summary.totalRevenue)
    courses.value = balances.purchases
    genderBreakdown.value = summary.patientsByGender ?? []
    appointmentStatuses.value = appointments.byStatus.map((item) => ({
      status: item.status,
      appointments: item.appointments,
    }))
    const byMonth = new Map<string, { course: number; singleVisit: number }>()
    const dailyByCategory = revenueReport.dailyByCategory ?? []
    dailyByCategory.forEach((item) => {
      const month = item.date.slice(0, 7)
      const row = byMonth.get(month) ?? { course: 0, singleVisit: 0 }
      if (item.type === 'COURSE') row.course += Number(item.amount)
      if (item.type === 'SINGLE_VISIT') row.singleVisit += Number(item.amount)
      byMonth.set(month, row)
    })
    monthlyRevenue.value = [...byMonth.entries()].map(([month, amounts]) => ({
      label: new Intl.DateTimeFormat('th-TH', { month: 'short' })
        .format(new Date(`${month}-01T00:00`))
        .replace('.', ''),
      ...amounts,
    }))
    if (!monthlyRevenue.value.length) {
      monthlyRevenue.value = [
        {
          label: new Intl.DateTimeFormat('th-TH', { month: 'short' })
            .format(new Date(`${from.value}T00:00:00`))
            .replace('.', ''),
          course: Number(summary.courseRevenue),
          singleVisit: Number(summary.singleVisitRevenue),
        },
      ]
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลแดชบอร์ดไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadDashboard()
})
function showAllBranches() {
  if (managerBranch.value) lastManagerBranch.value = managerBranch.value
  clearSelectedManagerBranch()
  window.dispatchEvent(new CustomEvent('manager-branch-changed'))
}
function restoreCurrentBranchView() {
  if (!lastManagerBranch.value) return
  setSelectedManagerBranch(lastManagerBranch.value)
  window.dispatchEvent(new CustomEvent('manager-branch-changed'))
}
watch([from, to, branchCode], loadDashboard)
watch(managerBranch, (branch) => {
  if (branch) lastManagerBranch.value = branch
})
watch(selectedPeriod, () => {
  hasCustomRange.value = false
})
function openCustomRange() {
  customFrom.value = from.value
  customTo.value = to.value
  customRangeOpen.value = true
}

function closeCustomRange() {
  customRangeOpen.value = false
}

function applyCustomRange() {
  if (!customFrom.value || !customTo.value || customFrom.value >= customTo.value) return
  const rangeUnchanged = from.value === customFrom.value && to.value === customTo.value
  loading.value = true
  errorMessage.value = ''
  hasCustomRange.value = true
  from.value = customFrom.value
  to.value = customTo.value
  customRangeOpen.value = false
  if (rangeUnchanged) void loadDashboard()
}
</script>

<template>
  <div class="manager-dashboard">
    <StaffHeader />
    <main
      v-if="loading"
      class="dashboard-main dashboard-loading"
      aria-busy="true"
      aria-label="กำลังโหลดแดชบอร์ด"
    >
      <div class="loading-heading">
        <span class="skeleton skeleton-title"></span>
        <span class="skeleton skeleton-subtitle"></span>
      </div>
      <section class="loading-section">
        <span class="skeleton skeleton-section-title"></span>
        <span class="skeleton skeleton-date"></span>
        <div class="loading-card-grid">
          <div v-for="card in 4" :key="card" class="loading-card">
            <span class="skeleton skeleton-accent"></span>
            <span class="skeleton skeleton-label"></span>
            <span class="skeleton skeleton-number"></span>
            <span class="skeleton skeleton-caption"></span>
          </div>
        </div>
      </section>
      <section class="loading-section">
        <div class="loading-section-row">
          <div>
            <span class="skeleton skeleton-section-title"></span
            ><span class="skeleton skeleton-date"></span>
          </div>
          <span class="skeleton skeleton-control"></span>
        </div>
        <div class="loading-card-grid">
          <div v-for="card in 4" :key="card" class="loading-card compact">
            <span class="skeleton skeleton-label"></span
            ><span class="skeleton skeleton-number"></span>
          </div>
        </div>
      </section>
      <section class="loading-chart">
        <span class="skeleton skeleton-section-title"></span
        ><span class="skeleton skeleton-subtitle"></span
        ><span class="skeleton skeleton-graph"></span>
      </section>
    </main>
    <main v-else class="dashboard-main">
      <header class="dashboard-heading">
        <div>
          <h1>ภาพรวมแดชบอร์ด</h1>
          <p>{{ dashboardSubtitle }}</p>
        </div>
        <button
          v-if="managerBranch"
          class="all-branches-button"
          type="button"
          @click="showAllBranches"
        >
          ดูภาพรวมทุกสาขา
        </button>
        <button
          v-else-if="lastManagerBranch"
          class="all-branches-button"
          type="button"
          @click="restoreCurrentBranchView"
        >
          กลับมุมมองสาขาปัจจุบัน
        </button>
      </header>

      <p v-if="errorMessage && !showNoResults" class="dashboard-feedback error" role="alert">
        {{ errorMessage }}
      </p>

      <section v-if="showNoResults" class="no-results" aria-label="ไม่พบข้อมูล">
        <h2>กำลังโหลดข้อมูล</h2>
        <p>กำลังดึงข้อมูลของสาขาและช่วงเวลาที่เลือก</p>
      </section>

      <template v-else>
        <section class="dashboard-section today-section" aria-label="ภาพรวมวันนี้">
          <h2>ภาพรวมวันนี้</h2>
          <p class="section-caption">
            {{
              new Date().toLocaleDateString('th-TH', {
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })
            }}
          </p>
          <div class="today-grid">
            <article class="today-card">
              <i></i>
              <p>ลูกค้าวันนี้</p>
              <b>{{ patients.toLocaleString() }} คน</b><small>ผู้ป่วยในระบบ</small>
            </article>
            <article class="today-card">
              <i></i>
              <p>ยอดรับชำระวันนี้</p>
              <b>{{ revenue ? money(revenue) : 'ยังไม่มีข้อมูล' }}</b
              ><small>ยอดรับเงินจริงในวันนี้</small>
            </article>
            <article class="today-card">
              <i></i>
              <p>ยอดขายวันนี้</p>
              <b>{{ money(revenue) }}</b
              ><small>ยอดขายหลังส่วนลด ไม่ใช่ยอดรับเงิน</small>
            </article>
            <article class="today-card">
              <i></i>
              <p>ต่ออายุวันนี้</p>
              <b>{{ courses.toLocaleString() }} คอร์ส</b><small>กำลังใช้งานอยู่</small>
            </article>
          </div>
        </section>

        <section class="dashboard-section period-section" aria-label="สรุปตามช่วงเวลา">
          <div class="section-row">
            <div>
              <h2>สรุปตามช่วงเวลา</h2>
              <p class="section-caption">{{ branchName }}</p>
            </div>
            <div class="period-actions">
              <div class="period-picker">
                <FilterDropdown
                  v-model="selectedPeriod"
                  :options="periodOptions"
                  default-label="เลือกช่วงเวลา"
                  label="เลือกช่วงเวลา"
                  :clear-on-reselect="false"
                  hide-default-option
                />
              </div>
              <button class="custom-range-button" type="button" @click="openCustomRange">
                ระบุช่วงวันที่เอง
              </button>
            </div>
          </div>
          <div class="summary-layout" aria-label="ข้อมูลสรุป">
            <div class="summary-column">
              <article class="summary-card">
                <p>ผู้ป่วยทั้งหมด</p>
                <b>{{ patients.toLocaleString() }} คน</b>
              </article>
              <article class="summary-card">
                <p>ยอดขายสุทธิ</p>
                <b>{{ money(revenue) }}</b>
              </article>
            </div>
            <div class="summary-column">
              <article class="summary-card">
                <p>เข้ารับบริการ</p>
                <b>{{ visits.toLocaleString() }} ครั้ง</b>
              </article>
              <article class="summary-card">
                <p>คอร์สที่กำลังใช้งาน</p>
                <b>{{ courses.toLocaleString() }} คอร์ส</b>
              </article>
            </div>
          </div>
        </section>

        <section class="revenue-card">
          <header>
            <div>
              <h2>แนวโน้มยอดขายแยกประเภท</h2>
              <p>แยกยอดขายคอร์สและรายครั้ง (ไม่ใช่ยอดรับชำระ)</p>
            </div>
            <div class="chart-legend">
              <span class="course">รายได้คอร์ส (Course)</span
              ><span class="visit">รายได้รายครั้ง (Single Visit)</span>
            </div>
          </header>
          <div class="chart-shell" role="img" aria-label="กราฟรายได้แยกตามประเภทในแต่ละเดือน">
            <div class="chart-axis" aria-hidden="true">
              <span v-for="tick in chartTicks" :key="tick">{{ tick.toLocaleString() }}</span>
            </div>
            <div class="monthly-chart">
              <div v-for="month in monthlyRevenue" :key="month.label" class="month-group">
                <div class="bar-area">
                  <i
                    class="course"
                    :style="{ height: `${(month.course / chartMaximum) * 100}%` }"
                    :title="`คอร์ส ${money(month.course)}`"
                  ></i>
                  <i
                    class="visit"
                    :style="{ height: `${(month.singleVisit / chartMaximum) * 100}%` }"
                    :title="`รายครั้ง ${money(month.singleVisit)}`"
                  ></i>
                </div>
                <span>{{ month.label }}</span>
              </div>
              <p v-if="!monthlyRevenue.length" class="empty-state">ยังไม่มีรายได้ในช่วงเวลานี้</p>
            </div>
          </div>
        </section>

        <section class="lower-grid">
          <article class="status-card">
            <header>
              <div>
                <h2>สถานะการเข้ารับบริการ</h2>
                <p>จากจำนวน {{ visits.toLocaleString() }} ครั้ง</p>
              </div>
            </header>
            <div v-for="item in dashboardStatuses" :key="item.status" class="status-row">
              <span>{{ statusLabel(item.status) }}</span>
              <div class="status-track">
                <i
                  :class="statusClass(item.status)"
                  :style="{ width: `${(item.appointments / maxAppointments) * 100}%` }"
                ></i>
              </div>
              <b>{{ item.appointments.toLocaleString() }}</b>
            </div>
          </article>
        </section>
      </template>
    </main>

    <div
      v-if="customRangeOpen"
      class="custom-range-backdrop"
      role="presentation"
      @click.self="closeCustomRange"
    >
      <section
        class="custom-range-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="custom-range-title"
      >
        <header>
          <div>
            <p class="dialog-eyebrow">สรุปตามช่วงเวลา</p>
            <h2 id="custom-range-title">ระบุช่วงวันที่เอง</h2>
            <p>เลือกวันเริ่มต้นและวันสิ้นสุดสำหรับดูข้อมูลสรุป</p>
          </div>
          <button class="dialog-close" type="button" aria-label="ปิด" @click="closeCustomRange">
            ×
          </button>
        </header>
        <div class="custom-range-fields">
          <label
            >วันที่เริ่มต้น
            <ThaiDatePicker v-model="customFrom" show-calendar-icon numeric-month hide-caret />
          </label>
          <span class="range-divider" aria-hidden="true">ถึง</span>
          <label
            >วันที่สิ้นสุด
            <ThaiDatePicker
              v-model="customTo"
              :min-date="customFrom"
              show-calendar-icon
              numeric-month
              hide-caret
            />
          </label>
        </div>
        <p v-if="customFrom && customTo && customFrom >= customTo" class="range-error">
          วันสิ้นสุดต้องหลังวันเริ่มต้นอย่างน้อย 1 วัน
        </p>
        <footer>
          <button class="dialog-cancel" type="button" @click="closeCustomRange">ยกเลิก</button>
          <button
            class="dialog-submit"
            type="button"
            :disabled="!customFrom || !customTo || customFrom >= customTo"
            @click="applyCustomRange"
          >
            แสดงผล
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.manager-dashboard {
  min-height: 100vh;
  background: #f9f4ee;
  color: #25372e;
}
.dashboard-main {
  width: min(1420px, calc(100% - 88px));
  margin: auto;
  padding: 43px 0 72px;
}
.dashboard-loading {
  animation: loading-enter 0.2s ease-out;
}
.skeleton {
  display: block;
  border-radius: 7px;
  background: linear-gradient(90deg, #ece8e2 20%, #f8f6f2 42%, #ece8e2 64%);
  background-size: 220% 100%;
  animation: skeleton-shimmer 1.25s ease-in-out infinite;
}
.loading-heading {
  display: grid;
  gap: 12px;
}
.skeleton-title {
  width: min(340px, 65%);
  height: 47px;
}
.skeleton-subtitle {
  width: min(440px, 78%);
  height: 17px;
}
.loading-section {
  margin-top: 47px;
}
.loading-section > .skeleton-section-title {
  width: 150px;
  height: 27px;
}
.loading-section > .skeleton-date {
  width: 118px;
  height: 16px;
  margin-top: 7px;
}
.loading-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin-top: 18px;
}
.loading-card {
  min-height: 140px;
  box-sizing: border-box;
  padding: 22px 24px;
  border: 1px solid #e6e1dc;
  border-radius: 12px;
  background: #fff;
}
.loading-card.compact {
  min-height: 140px;
  display: grid;
  align-content: center;
  gap: 12px;
}
.skeleton-accent {
  width: 32px;
  height: 3px;
  margin-bottom: 14px;
}
.skeleton-label {
  width: 42%;
  height: 16px;
}
.skeleton-number {
  width: 58%;
  height: 28px;
  margin-top: 11px;
}
.skeleton-caption {
  width: 72%;
  height: 13px;
  margin-top: 9px;
}
.loading-section-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
}
.loading-section-row .skeleton-section-title {
  width: 155px;
  height: 27px;
}
.loading-section-row .skeleton-date {
  width: 105px;
  height: 16px;
  margin-top: 7px;
}
.skeleton-control {
  width: 280px;
  height: 40px;
}
.loading-chart {
  min-height: 315px;
  display: grid;
  align-content: start;
  gap: 13px;
  margin-top: 26px;
  padding: 32px;
  border: 1px solid #e6e1dc;
  border-radius: 12px;
  background: #fff;
  box-sizing: border-box;
}
.loading-chart .skeleton-section-title {
  width: 250px;
  height: 27px;
}
.loading-chart .skeleton-subtitle {
  width: 355px;
  max-width: 70%;
  height: 16px;
}
.skeleton-graph {
  width: 100%;
  height: 200px;
  margin-top: 15px;
}
@keyframes skeleton-shimmer {
  to {
    background-position: -220% 0;
  }
}
@keyframes loading-enter {
  from {
    opacity: 0.5;
  }
  to {
    opacity: 1;
  }
}
.dashboard-heading {
  display: flex;
  justify-content: space-between;
  gap: 28px;
  align-items: start;
  flex-wrap: wrap;
}
.dashboard-heading h1 {
  margin: 0;
  font-size: clamp(2.15rem, 3.2vw, 3.25rem);
  line-height: 1.1;
  letter-spacing: -0.05em;
}
.dashboard-heading p,
.revenue-card header p {
  margin: 10px 0 0;
  color: #56665d;
}
.all-branches-button {
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid #1e918c;
  border-radius: 10px;
  color: #167d78;
  background: #fffdfb;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.all-branches-button:hover {
  background: #e8f4f0;
}
.dashboard-section {
  margin-top: 42px;
}
.dashboard-section h2 {
  margin: 0;
  font-size: 1.45rem;
  line-height: 1.35;
}
.section-caption {
  margin: 3px 0 0;
  color: #56665d;
  font-size: 0.92rem;
}
.today-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin-top: 18px;
}
.today-card {
  min-height: 140px;
  padding: 22px 24px;
  box-sizing: border-box;
  border: 1px solid #e6e1dc;
  border-radius: 12px;
  background: #fff;
}
.today-card > i {
  display: block;
  width: 32px;
  height: 3px;
  margin-bottom: 12px;
  border-radius: 3px;
  background: #1e918c;
}
.today-card p {
  margin: 0;
  color: #56665d;
  font-size: 0.9rem;
}
.today-card b {
  display: block;
  margin: 7px 0 4px;
  font-size: 1.5rem;
  line-height: 1.15;
}
.today-card small {
  color: #56665d;
  font-size: 0.76rem;
}
.period-section .summary-card {
  min-height: 140px;
  padding: 22px 24px;
  border-color: #e6e1dc;
}
.period-section .summary-card p {
  color: #56665d;
  font-size: 0.9rem;
}
.period-section .summary-card b {
  margin: 7px 0 4px;
  font-size: 1.5rem;
  line-height: 1.15;
}
.section-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
}
.period-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.period-picker {
  width: 178px;
}
.period-picker :deep(.filter-dropdown__button) {
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  color: #167d78;
  font-size: 0.9rem;
  font-weight: 700;
}
.period-picker :deep(.filter-dropdown__menu) {
  right: 0;
  left: auto;
  width: 100%;
}
.custom-range-button {
  min-height: 40px;
  padding: 0 15px;
  border: 1px solid #1e918c;
  border-radius: 8px;
  color: #167d78;
  background: #fff;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.custom-range-button:hover {
  background: #e8f4f0;
}
.custom-range-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #173f4452;
}
.custom-range-dialog {
  width: min(100%, 540px);
  box-sizing: border-box;
  padding: 28px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 60px #173f4433;
}
.custom-range-dialog header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}
.custom-range-dialog h2 {
  margin: 2px 0 5px;
  font-size: 1.5rem;
}
.custom-range-dialog header p {
  margin: 0;
  color: #66756e;
  font-size: 0.9rem;
}
.custom-range-dialog .dialog-eyebrow {
  color: #1e918c;
  font-weight: 700;
}
.dialog-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  color: #47615b;
  background: #edf5f1;
  font-size: 1.55rem;
  line-height: 1;
  cursor: pointer;
}
.custom-range-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 13px;
  align-items: end;
  margin-top: 25px;
}
.custom-range-fields label {
  display: grid;
  gap: 7px;
  color: #294943;
  font-size: 0.88rem;
  font-weight: 700;
}
.custom-range-fields :deep(.thai-date-picker) {
  margin: 0;
}
.custom-range-fields :deep(.date-trigger) {
  min-height: 45px;
  border-color: #cbd8d3;
  border-radius: 9px;
}
.custom-range-fields :deep(.calendar) {
  z-index: 110;
}
.range-divider {
  padding-bottom: 12px;
  color: #6e7f78;
  font-size: 0.88rem;
}
.range-error {
  margin: 10px 0 0;
  color: #b34940;
  font-size: 0.82rem;
}
.custom-range-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
}
.custom-range-dialog footer button {
  min-width: 110px;
  min-height: 42px;
  border-radius: 9px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.dialog-cancel {
  border: 1px solid #cbd8d3;
  color: #176f6b;
  background: #fff;
}
.dialog-submit {
  border: 1px solid #1e918c;
  color: #fff;
  background: #1e918c;
}
.dialog-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dashboard-filters {
  display: flex;
  align-items: stretch;
  min-width: 570px;
  padding: 14px 18px;
  border-radius: 22px;
  background: #fff9f9;
  box-shadow: 0 8px 30px #604f4515;
  margin-left: auto;
}
.dashboard-filters label,
.filter-period {
  display: grid;
  gap: 4px;
  color: #56665d;
  font-size: 0.78rem;
  font-weight: 700;
}
.filter-period {
  flex: 1;
  padding-right: 18px;
}
.filter-label {
  color: #56665d;
  font-size: 0.78rem;
  font-weight: 700;
}
.branch-filter {
  width: 180px;
  padding-left: 18px;
  border-left: 1px solid #e3d9d4;
}
.date-range {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto minmax(120px, 1fr);
  gap: 8px;
  align-items: center;
}
.date-range > span {
  color: #7b847e;
  font-size: 0.8rem;
}
.dashboard-filters :deep(.thai-date-picker) {
  margin-top: 0;
}
.dashboard-filters :deep(.date-trigger) {
  min-height: 38px;
  padding: 7px 9px;
  border-color: #d7dcd6;
  border-radius: 10px;
}
.dashboard-filters :deep(.calendar) {
  z-index: 60;
}
.dashboard-filters .date-end :deep(.calendar) {
  right: 0;
  left: auto;
}
.dashboard-feedback {
  margin: 26px 0 0;
  padding: 11px 14px;
  border-radius: 9px;
  background: #e7f2ed;
  color: #1e807c;
}
.dashboard-feedback.error {
  background: #ffebe7;
  color: #a1433b;
}
.no-results {
  display: grid;
  justify-items: center;
  min-height: 220px;
  align-content: center;
  margin-top: 35px;
  padding: 28px 24px;
  background: #fff;
  text-align: center;
}
.no-results h2 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
}
.no-results p {
  margin: 7px 0 0;
  color: #64736d;
  font-size: 0.78rem;
}
.summary-layout {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
  margin-top: 18px;
}
.summary-column {
  display: contents;
}
.summary-card,
.revenue-card,
.status-card {
  border: 1px solid #fff8f8;
  border-radius: 12px;
  background: #fff;
  box-shadow: none;
}
.summary-card {
  min-height: 116px;
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}
.summary-card p {
  margin: 0;
  color: #56665d;
  font-size: 1rem;
}
.summary-card b {
  margin: 10px 0 5px;
  font-size: clamp(1.55rem, 2.35vw, 2.35rem);
  line-height: 1.1;
}
.summary-card small {
  color: #56665d;
}
.gender-bar {
  display: flex;
  height: 10px;
  margin: 15px 0 9px;
  overflow: hidden;
  border-radius: 20px;
  background: #1e807c;
}
.gender-bar span {
  background: #e6d479;
}
.gender-bar .gender-secondary {
  background: #1e807c;
}
.gender-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
}
.gender-labels small {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.gender-labels i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #e6d479;
}
.gender-labels i.secondary {
  background: #1e807c;
}
.revenue-card {
  margin-top: 26px;
  padding: 32px;
}
.revenue-card header,
.status-card header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: start;
}
.revenue-card h2,
.status-card h2 {
  margin: 0;
  font-size: 1.55rem;
}
.chart-legend {
  display: flex;
  gap: 18px;
  color: #1f2937;
  font-size: 0.88rem;
  font-weight: 600;
}
.chart-legend span::before {
  content: '';
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 7px;
  border-radius: 3px;
  vertical-align: -1px;
}
.chart-legend .course::before,
.bar-area .course {
  background: #1e807c;
}
.chart-legend .visit::before,
.bar-area .visit {
  background: #e6d479;
}
.chart-shell {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr);
  margin-top: 34px;
}
.chart-axis {
  height: 286px;
  padding: 0 10px 27px 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-sizing: border-box;
  color: #7a867e;
  font-size: 0.75rem;
  text-align: right;
}
.monthly-chart {
  display: flex;
  min-height: 286px;
  align-items: end;
  justify-content: space-around;
  gap: 18px;
  padding: 0 34px 8px;
  border-bottom: 1px solid #dfe4de;
  background: repeating-linear-gradient(to bottom, transparent 0 49px, #e8ebe6 50px 51px);
}
.month-group {
  display: grid;
  justify-items: center;
  gap: 10px;
  min-width: 48px;
  color: #56665d;
  font-size: 0.86rem;
}
.bar-area {
  display: flex;
  align-items: end;
  gap: 7px;
  height: 250px;
}
.bar-area i {
  display: block;
  width: 22px;
  min-height: 3px;
  border-radius: 5px 5px 0 0;
  transition: height 0.2s ease;
}
.monthly-chart .empty-state {
  align-self: center;
}
.lower-grid {
  display: grid;
  margin-top: 32px;
}
.status-card {
  padding: 32px;
}
.status-card header p {
  margin: 5px 0 0;
  color: #7a867e;
}
.status-row {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr) 44px;
  gap: 16px;
  align-items: center;
  padding: 12px 0;
  color: #56665d;
}
.status-row b {
  color: #25372e;
  font-size: 0.82rem;
  text-align: right;
}
.status-track {
  height: 13px;
  overflow: hidden;
  border-radius: 999px;
  background: #c8e6c0;
}
.status-track i {
  display: block;
  height: 100%;
  min-width: 4px;
  border-radius: inherit;
}
.status-track .status-completed {
  background: #1e807c;
}
.status-track .status-in-service {
  background: #8faeae;
}
.status-track .status-no-show {
  background: #e6d479;
}
.status-track .status-cancelled {
  background: #e07d96;
}
.empty-state {
  color: #56665d;
}
@media (max-width: 1050px) {
  .dashboard-heading {
    flex-direction: column;
  }
  .dashboard-filters {
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
  }
  .summary-layout {
    grid-template-columns: 1fr 1fr;
  }
  .today-grid {
    grid-template-columns: 1fr 1fr;
  }
  .loading-card-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 700px) {
  .dashboard-main {
    width: min(100% - 32px, 620px);
    padding-top: 26px;
  }
  .dashboard-filters {
    display: grid;
  }
  .filter-period {
    padding: 0 0 14px;
  }
  .branch-filter {
    width: auto;
    padding: 14px 0 0;
    border-top: 1px solid #e3d9d4;
    border-left: 0;
  }
  .dashboard-filters :deep(.calendar) {
    position: absolute;
    top: calc(100% + 7px);
    right: auto;
    bottom: auto;
    left: 0;
    width: min(300px, calc(100vw - 64px));
    max-width: none;
    padding: 14px;
  }
  .dashboard-filters .date-end :deep(.calendar) {
    right: 0;
    left: auto;
  }
  .summary-layout {
    grid-template-columns: 1fr;
    gap: 15px;
    margin-top: 28px;
  }
  .today-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .loading-card-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .loading-section-row {
    align-items: stretch;
    flex-direction: column;
  }
  .skeleton-control {
    width: 100%;
  }
  .summary-column {
    gap: 15px;
  }
  .summary-card {
    min-height: 0;
  }
  .revenue-card {
    padding: 25px 19px;
  }
  .revenue-card header {
    flex-direction: column;
  }
  .chart-legend {
    flex-wrap: wrap;
  }
  .chart-shell {
    grid-template-columns: 48px minmax(0, 1fr);
  }
  .chart-axis {
    height: 228px;
  }
  .monthly-chart {
    min-height: 220px;
    padding: 0 8px 8px;
    overflow-x: auto;
    justify-content: flex-start;
  }
  .bar-area {
    height: 178px;
  }
  .status-card {
    padding: 23px 20px;
  }
  .status-row {
    grid-template-columns: 92px minmax(0, 1fr) 28px;
    gap: 7px;
    font-size: 0.88rem;
  }
  .status-row b {
    font-size: 0.78rem;
  }
  .custom-range-backdrop {
    align-items: end;
    padding: 0;
  }
  .custom-range-dialog {
    border-radius: 18px 18px 0 0;
    padding: 24px 20px;
  }
  .custom-range-fields {
    grid-template-columns: 1fr;
    gap: 13px;
  }
  .range-divider {
    display: none;
  }
  .custom-range-dialog footer button {
    flex: 1;
  }
  .section-row {
    align-items: stretch;
  }
  .period-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }
  .period-picker,
  .custom-range-button {
    width: 100%;
  }
}
@media (max-width: 420px) {
  .date-range {
    grid-template-columns: 1fr;
  }
  .date-range > span {
    display: none;
  }
  .dashboard-heading h1 {
    font-size: 2.25rem;
  }
}
</style>
