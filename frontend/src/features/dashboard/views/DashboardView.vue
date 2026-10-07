<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { branchApi, patientApi, reportApi } from '@/api/client'

const now = new Date()
const from = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`)
const to = ref(now.toISOString().slice(0, 10))
const branchCode = ref('')
const branches = ref<Array<{ code: string; name: string }>>([])
const loading = ref(true)
const errorMessage = ref('')
const patients = ref(0)
const visits = ref(0)
const revenue = ref(0)
const courses = ref(0)
const genderBreakdown = ref<Array<{ gender: string; patients: number }>>([])
const appointmentStatuses = ref<Array<{ status: string; appointments: number }>>([])
const monthlyRevenue = ref<Array<{ label: string; course: number; singleVisit: number }>>([])
const statusOrder = [
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
const dashboardStatuses = computed(() => {
  const counts = new Map(appointmentStatuses.value.map((item) => [item.status, item.appointments]))
  return statusOrder.map((status) => ({ status, appointments: counts.get(status) ?? 0 }))
})
const maxAppointments = computed(() =>
  Math.max(...dashboardStatuses.value.map((item) => item.appointments), 1),
)
const branchName = computed(
  () => branches.value.find((item) => item.code === branchCode.value)?.name ?? 'ทุกสาขา',
)
const patientGenderLabel = computed(
  () =>
    genderBreakdown.value
      .map((item) => `${item.gender} ${item.patients.toLocaleString()}`)
      .join('  ') || 'ยังไม่มีข้อมูลผู้ป่วยในช่วงนี้',
)
const money = (value: number) =>
  `${value.toLocaleString('th-TH', { maximumFractionDigits: 0 })} บาท`
const statusLabel = (status: string) =>
  ({
    CONFIRMED: 'Confirmed',
    ARRIVED: 'Arrived',
    IN_SERVICE: 'In service',
    COMPLETED: 'Complete',
    CANCELLED: 'Canceled',
    RESCHEDULED: 'Rescheduled',
    NO_SHOW: 'No Show',
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
      patientApi.searchAll(),
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
      <header class="dashboard-heading">
        <div>
          <h1>ภาพรวมแดชบอร์ด</h1>
          <p>ตรวจสอบข้อมูลจาก Treatment และ Sales Transaction</p>
        </div>
        <div class="dashboard-filters" aria-label="ตัวกรองแดชบอร์ด">
          <label
            >ช่วงเวลา<ThaiDatePicker v-model="from" numeric-month show-calendar-icon hide-caret
          /></label>
          <span>ถึง</span>
          <label class="date-end"
            ><ThaiDatePicker v-model="to" numeric-month show-calendar-icon hide-caret
          /></label>
          <label
            >สาขา<select v-model="branchCode">
              <option value="">ทุกสาขา</option>
              <option v-for="branch in branches" :key="branch.code" :value="branch.code">
                {{ branch.name }}
              </option>
            </select></label
          >
        </div>
      </header>

      <p v-if="loading" class="dashboard-feedback">กำลังโหลดข้อมูลจาก backend...</p>
      <p v-else-if="errorMessage" class="dashboard-feedback error" role="alert">
        {{ errorMessage }}
      </p>

      <section class="summary-grid" aria-label="ข้อมูลสรุป">
        <article class="patient-card">
          <p>ผู้ป่วยทั้งหมด</p>
          <b>{{ patients.toLocaleString() }} คน</b>
          <div class="gender-bar">
            <span
              v-for="(gender, index) in genderBreakdown"
              :key="gender.gender"
              :style="{ width: `${(gender.patients / Math.max(patients, 1)) * 100}%` }"
              :class="index ? 'gender-secondary' : ''"
            ></span>
          </div>
          <small>{{ patientGenderLabel }}</small>
        </article>
        <article>
          <p>จำนวนครั้งที่เข้ารับบริการ</p>
          <b>{{ visits.toLocaleString() }} ครั้ง</b><small>ในช่วงเวลาที่เลือก</small>
        </article>
        <article>
          <p>รายได้รวม</p>
          <b>{{ money(revenue) }}</b
          ><small>{{ branchName }}</small>
        </article>
        <article>
          <p>คอร์สที่กำลังใช้งาน</p>
          <b>{{ courses.toLocaleString() }} คอร์ส</b><small>ซื้อในช่วงเวลาที่เลือก</small>
        </article>
      </section>

      <section class="revenue-card">
        <header>
          <div>
            <h2>รายได้แยกตามประเภท</h2>
            <p>เปรียบเทียบรายได้คอร์สและรายครั้ง ตามช่วงเวลาที่เลือก</p>
          </div>
          <div class="chart-legend">
            <span class="course">รายได้คอร์ส (Course)</span
            ><span class="visit">รายได้รายครั้ง (Single Visit)</span>
          </div>
        </header>
        <div class="monthly-chart" role="img" aria-label="กราฟรายได้แยกตามประเภทในแต่ละเดือน">
          <div v-for="month in monthlyRevenue" :key="month.label" class="month-group">
            <div class="bar-area">
              <i
                class="course"
                :style="{ height: `${(month.course / maxMonthlyRevenue) * 100}%` }"
                :title="`คอร์ส ${money(month.course)}`"
              ></i>
              <i
                class="visit"
                :style="{ height: `${(month.singleVisit / maxMonthlyRevenue) * 100}%` }"
                :title="`รายครั้ง ${money(month.singleVisit)}`"
              ></i>
            </div>
            <span>{{ month.label }}</span>
          </div>
          <p v-if="!monthlyRevenue.length" class="empty-state">ยังไม่มีรายได้ในช่วงเวลานี้</p>
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
  width: min(1420px, calc(100% - 72px));
  margin: auto;
  padding: 43px 0 72px;
}
.dashboard-heading {
  display: flex;
  justify-content: space-between;
  gap: 28px;
  align-items: start;
}
.dashboard-heading h1 {
  margin: 0;
  font-size: clamp(2.3rem, 4vw, 3.7rem);
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
  align-items: end;
  gap: 9px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #fff9f9;
  box-shadow: 0 7px 18px #25372e14;
}
.dashboard-filters label {
  display: grid;
  gap: 4px;
  color: #56665d;
  font-size: 0.78rem;
  font-weight: 700;
}
.dashboard-filters select {
  min-height: 38px;
  border: 1px solid #d7dcd6;
  border-radius: 8px;
  padding: 0 9px;
  background: white;
  color: #25372e;
  font: inherit;
}
.dashboard-filters :deep(.thai-date-picker) {
  margin-top: 0;
}
.dashboard-filters :deep(.date-trigger) {
  min-height: 38px;
  padding: 7px 9px;
  border-color: #d7dcd6;
  border-radius: 8px;
}
.dashboard-filters :deep(.calendar) {
  z-index: 60;
}
.dashboard-filters .date-end :deep(.calendar) {
  right: 0;
  left: auto;
}
.dashboard-filters > span {
  padding-bottom: 9px;
  color: #56665d;
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
.summary-grid {
  display: grid;
  grid-template-columns: 1.42fr repeat(3, 1fr);
  gap: 24px;
  margin-top: 48px;
}
.summary-grid article,
.revenue-card,
.status-card,
.period-card {
  border: 1px solid #fff8f8;
  border-radius: 26px;
  background: #fff9f9;
  box-shadow: 0 4px 25px #00000016;
}
.summary-grid article {
  min-height: 142px;
  padding: 25px 28px;
  display: flex;
  flex-direction: column;
}
.summary-grid p,
.period-card p {
  margin: 0;
  color: #56665d;
  font-size: 1rem;
}
.summary-grid b {
  margin: 10px 0 5px;
  font-size: clamp(1.55rem, 2.35vw, 2.35rem);
  line-height: 1.1;
}
.summary-grid small,
.period-card span {
  color: #56665d;
}
.gender-bar {
  display: flex;
  height: 10px;
  margin: 11px 0 6px;
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
.revenue-card {
  margin-top: 26px;
  padding: 31px 38px;
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
.monthly-chart {
  display: flex;
  min-height: 280px;
  align-items: end;
  justify-content: space-around;
  gap: 18px;
  margin-top: 34px;
  padding: 0 30px 8px;
  border-bottom: 1px solid #dfe4de;
  background: repeating-linear-gradient(to bottom, transparent 0 46px, #e8ebe6 47px 48px);
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
  height: 236px;
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
  grid-template-columns: 1fr;
  gap: 24px;
  margin-top: 26px;
}
.status-card,
.period-card {
  padding: 27px 30px;
}
.status-card a,
.period-card a {
  color: #1e807c;
  font-weight: 700;
  text-decoration: none;
}
.status-card header p {
  margin: 5px 0 0;
  color: #7a867e;
}
.status-row {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr) 34px;
  gap: 10px;
  align-items: center;
  padding: 7px 0;
  color: #56665d;
}
.status-row b {
  color: #25372e;
  font-size: 0.82rem;
  text-align: right;
}
.status-track {
  height: 10px;
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
.status-track .status-complete,
.status-track .status-completed {
  background: #1e807c;
}
.status-track .status-confirmed,
.status-track .status-arrived {
  background: #8faeae;
}
.status-track .status-in-service,
.status-track .status-checked-in {
  background: #8faeae;
}
.status-track .status-no-show {
  background: #e6d479;
}
.status-track .status-cancelled,
.status-track .status-canceled {
  background: #e07d96;
}
.empty-state {
  color: #56665d;
}
.period-card {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 10px;
}
.period-card b {
  font-size: 1.3rem;
}
.period-card a {
  margin-top: auto;
}
@media (max-width: 1050px) {
  .dashboard-heading {
    flex-direction: column;
  }
  .dashboard-filters {
    width: 100%;
    box-sizing: border-box;
  }
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 700px) {
  .dashboard-main {
    width: min(100% - 32px, 620px);
    padding-top: 26px;
  }
  .dashboard-filters {
    display: grid;
    grid-template-columns: 1fr 22px 1fr;
  }
  .dashboard-filters label:last-child {
    grid-column: 1/-1;
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
  .dashboard-filters .date-end :deep(.calendar) { right: 0; left: auto; }
  .summary-grid,
  .lower-grid {
    grid-template-columns: 1fr;
    gap: 15px;
    margin-top: 28px;
  }
  .summary-grid article {
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
  .monthly-chart {
    min-height: 220px;
    padding: 0 8px 8px;
    overflow-x: auto;
    justify-content: flex-start;
  }
  .bar-area {
    height: 178px;
  }
  .status-card,
  .period-card {
    padding: 23px 20px;
  }
  .status-row {
    grid-template-columns: 104px minmax(0, 1fr) 24px;
    gap: 7px;
    font-size: 0.88rem;
  }
  .status-row b {
    font-size: 0.78rem;
  }
}
@media (max-width: 420px) {
  .dashboard-filters {
    grid-template-columns: 1fr;
  }
  .dashboard-filters > span {
    display: none;
  }
  .dashboard-filters label,
  .dashboard-filters label:last-child {
    grid-column: auto;
  }
  .dashboard-heading h1 {
    font-size: 2.25rem;
  }
}
</style>
