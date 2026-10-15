<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { branchApi, reportApi } from '@/api/client'

const from = ref(`${clinicToday().slice(0, 8)}01`)
const to = ref(clinicToday())
const branchCode = ref('')
const branches = ref<Array<{ branchCode: string; name: string }>>([])
const branchOptions = computed(() =>
  branches.value.map((branch) => ({ value: branch.branchCode, label: branch.name })),
)
const loading = ref(true)
const errorMessage = ref('')
const patients = ref(0)
const visits = ref(0)
const revenue = ref(0)
const courses = ref(0)
const newCustomers = ref(0)
const returningCustomers = ref(0)
const courseRenewals = ref(0)
const packageUpdates = ref(0)
const salesTotal = ref(0)
const dailySales = ref<Array<{ date: string; amount: number }>>([])
const maxDailySales = computed(() => Math.max(...dailySales.value.map(row => row.amount), 1))
const genderBreakdown = ref<Array<{ gender: string; patients: number }>>([])
const appointmentStatuses = ref<Array<{ status: string; appointments: number }>>([])
const monthlyRevenue = ref<Array<{ label: string; course: number; singleVisit: number }>>([])
const statusOrder = [
  'UNCONFIRMED',
  'CONFIRMED',
  'ARRIVED',
  'IN_SERVICE',
  'COMPLETED',
  'CANCELLED',
  'RESCHEDULED',
  'NO_SHOW',
]

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
  const counts = new Map(
    appointmentStatuses.value.map((item) => [
      item.status.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase(),
      item.appointments,
    ]),
  )
  return statusOrder.map((status) => ({ status, appointments: counts.get(status) ?? 0 }))
})
const maxAppointments = computed(() =>
  Math.max(...dashboardStatuses.value.map((item) => item.appointments), 1),
)
const branchName = computed(
  () => branches.value.find((item) => item.branchCode === branchCode.value)?.name ?? 'ทุกสาขา',
)
const genderLabel = (gender: string) =>
  ({ MALE: 'ชาย', FEMALE: 'หญิง', OTHER: 'อื่น ๆ', UNKNOWN: 'ไม่ระบุ' })[gender.toUpperCase()] ??
  gender
const money = (value: number) =>
  `${value.toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} บาท`
const dayLabel = (date: string) => new Intl.DateTimeFormat('th-TH', {
  day: 'numeric', month: 'short', timeZone: 'Asia/Bangkok',
}).format(new Date(`${date}T00:00:00+07:00`))
const statusLabel = (status: string) =>
  ({
    UNCONFIRMED: 'Unconfirmed',
    CONFIRMED: 'Confirmed',
    ARRIVED: 'Arrived',
    IN_SERVICE: 'In service',
    COMPLETED: 'Complete',
    CANCELLED: 'Canceled',
    RESCHEDULED: 'Rescheduled',
    NO_SHOW: 'No Show',
  })[status.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()] ?? status
const statusClass = (status: string) => `status-${status.toLowerCase().replaceAll('_', '-')}`

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [summary, appointments, balances, revenueReport] = await Promise.all([
      reportApi.dashboard(from.value, to.value, branchCode.value || undefined),
      reportApi.appointments(from.value, to.value, branchCode.value || undefined),
      reportApi.balances(from.value, to.value, branchCode.value || undefined),
      reportApi.revenue(from.value, to.value, branchCode.value || undefined),
    ])
    patients.value = summary.uniquePatients
    visits.value = summary.visits
    revenue.value = Number(summary.cashReceived)
    newCustomers.value = summary.newCustomers
    returningCustomers.value = summary.returningCustomers
    courseRenewals.value = summary.courseRenewals
    packageUpdates.value = summary.packageUpdates
    salesTotal.value = Number(summary.salesTotal)
    dailySales.value = summary.dailySales.map(row => ({ date: row.date, amount: Number(row.amount) }))
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
  try {
    branches.value = await branchApi.list()
  } catch {}
  await loadDashboard()
})
watch([from, to, branchCode], loadDashboard)
</script>

<template>
  <div class="manager-dashboard">
    <StaffHeader />
    <main class="dashboard-main">
      <RouterLink class="dashboard-compensation-link" to="/staff/commission">
        ค่าคอมมิชชันและค่ารักษาแทน
      </RouterLink>
      <header class="dashboard-heading">
        <div>
          <h1>ภาพรวมแดชบอร์ด</h1>
          <p>ตรวจสอบข้อมูลจาก Treatment และ Sales Transaction</p>
        </div>
        <div class="dashboard-filters" aria-label="ตัวกรองแดชบอร์ด">
          <div class="filter-period">
            <span class="filter-label">ช่วงเวลา</span>
            <div class="date-range">
              <ThaiDatePicker v-model="from" numeric-month show-calendar-icon hide-caret />
              <span>ถึง</span>
              <div class="date-end">
                <ThaiDatePicker v-model="to" numeric-month show-calendar-icon hide-caret />
              </div>
            </div>
          </div>
          <label class="branch-filter"
            >สาขา<FilterDropdown
              v-model="branchCode"
              :options="branchOptions"
              default-value=""
              default-label="ทุกสาขา"
              label="กรองตามสาขา"
              :clear-on-reselect="false"
          /></label>
        </div>
      </header>

      <p v-if="loading" class="dashboard-feedback">กำลังโหลดข้อมูลจาก backend...</p>
      <p v-else-if="errorMessage" class="dashboard-feedback error" role="alert">
        {{ errorMessage }}
      </p>

      <section class="summary-layout" aria-label="ข้อมูลสรุป">
        <div class="summary-column">
          <article class="summary-card patient-card">
            <p>ผู้ป่วยที่มาใช้บริการ</p>
            <b>{{ patients.toLocaleString() }} คน</b>
            <div class="gender-bar">
              <span
                v-for="(gender, index) in genderBreakdown"
                :key="gender.gender"
                :style="{ width: `${(gender.patients / Math.max(patients, 1)) * 100}%` }"
                :class="index ? 'gender-secondary' : ''"
              ></span>
            </div>
            <div v-if="genderBreakdown.length" class="gender-labels">
              <small v-for="(gender, index) in genderBreakdown" :key="gender.gender">
                <i :class="index ? 'secondary' : ''"></i>{{ genderLabel(gender.gender) }}
                {{ gender.patients.toLocaleString() }} คน
              </small>
            </div>
            <small v-else>ยังไม่มีข้อมูลผู้ป่วยในช่วงนี้</small>
          </article>
          <article class="summary-card revenue-summary">
            <p>เงินรับรวม</p>
            <b>{{ money(revenue) }}</b
            ><small>{{ branchName }} · ก่อนหักคืนเงิน</small>
          </article>
        </div>
        <div class="summary-column">
          <article class="summary-card">
            <p>จำนวนครั้งที่เข้ารับบริการ</p>
            <b>{{ visits.toLocaleString() }} ครั้ง</b><small>ในช่วงเวลาที่เลือก</small>
          </article>
          <article class="summary-card">
            <p>คอร์สที่กำลังใช้งาน</p>
            <b>{{ courses.toLocaleString() }} คอร์ส</b><small>ซื้อในช่วงเวลาที่เลือก</small>
          </article>
        </div>
      </section>

      <section class="activity-summary" aria-label="ยอดขายและกิจกรรมลูกค้า">
        <article class="summary-card">
          <p>ยอดขายสุทธิ</p><b>{{ money(salesTotal) }}</b><small>ตามวันขาย · ไม่รวมใบยกเลิก</small>
        </article>
        <article class="summary-card">
          <p>ลูกค้าใหม่</p><b>{{ newCustomers.toLocaleString() }} คน</b><small>ลงทะเบียนในช่วงที่เลือก</small>
        </article>
        <article class="summary-card">
          <p>ลูกค้าเก่า</p><b>{{ returningCustomers.toLocaleString() }} คน</b><small>ลงทะเบียนก่อนช่วงและมาใช้บริการในช่วงนี้</small>
        </article>
        <article class="summary-card">
          <p>ต่ออายุคอร์ส</p><b>{{ courseRenewals.toLocaleString() }} รายการ</b><small>รายการที่บันทึกในช่วงที่เลือก</small>
        </article>
        <article class="summary-card">
          <p>อัปเดตแม่แบบคอร์ส</p><b>{{ packageUpdates.toLocaleString() }} รายการ</b><small>รายการที่บันทึก · ทั้งคลินิก</small>
        </article>
      </section>

      <section class="revenue-card">
        <header><div><h2>ยอดขายรายวัน</h2><p>ยอดสุทธิตามวันขาย รวมวันที่ไม่มียอดขาย</p></div></header>
        <ol class="daily-sales-chart" aria-label="ยอดขายสุทธิรายวัน">
          <li v-for="day in dailySales" :key="day.date">
            <span>{{ dayLabel(day.date) }}</span>
            <div class="daily-sales-track" aria-hidden="true"><i :style="{ width: `${(day.amount / maxDailySales) * 100}%` }"></i></div>
            <b>{{ money(day.amount) }}</b>
          </li>
        </ol>
      </section>

      <section class="revenue-card">
        <header>
          <div>
            <h2>เงินรับแยกตามประเภท</h2>
            <p>เงินรับคอร์สและรายครั้งตามวันรับเงิน ก่อนหักคืนเงิน</p>
          </div>
          <div class="chart-legend">
            <span class="course">รายได้คอร์ส (Course)</span
            ><span class="visit">รายได้รายครั้ง (Single Visit)</span>
          </div>
        </header>
        <div class="chart-shell" role="img" aria-label="กราฟรายได้แยกตามประเภทในแต่ละเดือน">
          <div class="chart-axis" aria-hidden="true">
            <span v-for="(tick, index) in chartTicks" :key="index">{{ tick.toLocaleString() }}</span>
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
    </main>
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
.dashboard-compensation-link {
  display: flex;
  width: max-content;
  min-width: 230px;
  min-height: 42px;
  margin: 0 0 24px auto;
  padding: 0 22px;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border-radius: 9px;
  background: #1e918c;
  color: #fff;
  font-size: 0.9rem;
  font-weight: 700;
  text-decoration: none;
  transition:
    background-color 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.dashboard-compensation-link:hover {
  background: #176f6c;
  box-shadow: 0 6px 16px #1e807c2e;
  transform: translateY(-1px);
}
.dashboard-compensation-link:focus-visible {
  outline: 3px solid #87c9e8;
  outline-offset: 3px;
}
.dashboard-heading {
  display: flex;
  justify-content: space-between;
  gap: 28px;
  align-items: start;
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
.dashboard-filters {
  display: flex;
  align-items: stretch;
  min-width: 570px;
  padding: 14px 18px;
  border-radius: 22px;
  background: #fff9f9;
  box-shadow: 0 8px 30px #604f4515;
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
.summary-layout {
  display: grid;
  grid-template-columns: 1.45fr 1fr;
  gap: 24px;
  margin-top: 48px;
}
.summary-column {
  display: grid;
  gap: 24px;
}
.activity-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 16px;
  margin-top: 24px;
}
.activity-summary .summary-card {
  padding: 24px;
}
.activity-summary .summary-card b {
  font-size: 1.65rem;
}
.daily-sales-chart {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 24px 0 0;
  list-style: none;
}
.daily-sales-chart li {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr) 115px;
  gap: 12px;
  align-items: center;
  font-size: 0.85rem;
}
.daily-sales-chart b {
  text-align: right;
}
.daily-sales-track {
  height: 12px;
  border-radius: 6px;
  background: #e7efeb;
  overflow: hidden;
}
.daily-sales-track i {
  display: block;
  height: 100%;
  background: #1e807c;
  border-radius: inherit;
}
.summary-card,
.revenue-card,
.status-card {
  border: 1px solid #fff8f8;
  border-radius: 32px;
  background: #fff9f9;
  box-shadow: 0 8px 30px #604f4515;
}
.summary-card {
  min-height: 158px;
  padding: 29px 34px;
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
  margin-top: 32px;
  padding: 34px 42px 30px;
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
  padding: 34px 42px 38px;
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
}
@media (max-width: 420px) {
  .dashboard-compensation-link {
    width: 100%;
    min-width: 0;
  }
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
