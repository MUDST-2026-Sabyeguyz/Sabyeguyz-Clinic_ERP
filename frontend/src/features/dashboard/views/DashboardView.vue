<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { reportApi } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const today = ref(clinicToday())
const now = new Date(`${today.value}T12:00:00`)
const from = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`)
const to = ref(
  `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`,
)
const managerBranch = computed(() => selectedManagerBranch())
const branchCode = computed(() => managerBranch.value?.code ?? '')
const selectedDateRange = computed(() => {
  const format = (date: string) =>
    new Intl.DateTimeFormat('th-TH', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(new Date(`${date}T00:00:00`))
  return `${format(from.value)} – ${format(to.value)}`
})
const customRangeOpen = ref(false)
const customFrom = ref(from.value)
const customTo = ref(to.value)
const shortcutSelectionStarted = ref(false)
const selectedDraftMonths = computed(() => {
  const endPeriod = customTo.value.slice(0, 7)
  const [year, month] = endPeriod.split('-').map(Number)
  if (!year || !month || customFrom.value > customTo.value) return null
  const lastDay = new Date(year, month, 0).getDate()
  return customFrom.value.endsWith('-01') &&
    customTo.value === `${endPeriod}-${String(lastDay).padStart(2, '0')}`
    ? { start: customFrom.value.slice(0, 7), end: endPeriod }
    : null
})
const lastEditedRangeField = ref<'from' | 'to'>('from')
const shortcutYear = ref(now.getFullYear())
const shortcutYearLabel = computed(() => String(shortcutYear.value + 543))
const shortcutYears = computed(() => {
  const latestYear = Math.max(now.getFullYear() + 1, shortcutYear.value)
  return Array.from({ length: latestYear - 1900 + 1 }, (_, index) => latestYear - index)
})
const periodOptions = computed(() =>
  Array.from({ length: 12 }, (_, index) => {
    const date = new Date(shortcutYear.value, index, 1)
    return {
      value: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`,
      label: new Intl.DateTimeFormat('th-TH', { month: 'long' }).format(date),
    }
  }),
)
const loading = ref(true)
const hasLoaded = ref(false)
const todaySummary = ref({ patients: 0, receipts: 0, sales: 0 })
const rangeDialog = ref<HTMLElement | null>(null)
let rangeTrigger: HTMLElement | null = null
let dashboardRequest = 0
let todayRequest = 0
let refreshTimer: ReturnType<typeof setInterval> | undefined
const errorMessage = ref('')
const patients = ref(0)
const visits = ref(0)
const revenue = ref(0)
const courses = ref(0)
const sales = ref(0)
const newCustomers = ref(0)
const packageUpdates = ref(0)
const packageUpdatesScope = ref('CLINIC')
const appointmentStatuses = ref<Array<{ status: string; appointments: number }>>([])
const appointmentTotal = ref(0)
const monthlyRevenue = ref<
  Array<{ key: string; label: string; course: number; singleVisit: number }>
>([])
const normalizeStatus = (status: string) => {
  const normalized = status.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()
  return normalized === 'UN_CONFIRMED' ? 'UNCONFIRMED' : normalized
}

const maxMonthlyRevenue = computed(() =>
  Math.max(...monthlyRevenue.value.flatMap((item) => [item.course, item.singleVisit]), 1),
)
const chartMaximum = computed(() => {
  const rawMaximum = maxMonthlyRevenue.value
  const magnitude = 10 ** Math.max(String(Math.floor(rawMaximum)).length - 1, 0)
  return Math.max(5, Math.ceil(rawMaximum / magnitude) * magnitude)
})
const chartTicks = computed(() =>
  Array.from({ length: 6 }, (_, index) => Math.round(chartMaximum.value * (1 - index / 5))),
)
// the dashboard always shows the three appointment statuses (a missing one counts as 0); any other status is listed after them
const shownStatuses = ['UNCONFIRMED', 'CONFIRMED', 'CANCELLED']
const dashboardStatuses = computed(() => [
  ...shownStatuses.map(
    (status) =>
      appointmentStatuses.value.find((item) => item.status === status) ?? { status, appointments: 0 },
  ),
  ...appointmentStatuses.value.filter((item) => !shownStatuses.includes(item.status)),
])
const maxAppointments = computed(() =>
  Math.max(...dashboardStatuses.value.map((item) => item.appointments), 1),
)
const branchName = computed(() => {
  const name = managerBranch.value?.name
  if (!name) return 'ยังไม่ได้เลือกสาขา'
  return `สาขา${name
    .replace(/^(?:สาขา\s*)+/, '')
    .trim()
    .replace(/พระราม\s+9/g, 'พระราม9')}`
})
const hasRevenue = computed(() =>
  monthlyRevenue.value.some((item) => item.course !== 0 || item.singleVisit !== 0),
)
const money = (value: number) =>
  `${value.toLocaleString('th-TH', { maximumFractionDigits: 2 })} บาท`
const statusLabel = (status: string) =>
  ({
    CONFIRMED: 'ยืนยันแล้ว',
    UNCONFIRMED: 'ยังไม่ยืนยัน',
    CANCELLED: 'ยกเลิก',
  })[status] ?? status
const statusClass = (status: string) => `status-${status.toLowerCase().replaceAll('_', '-')}`

async function loadDashboard() {
  const requestId = ++dashboardRequest
  ++todayRequest
  if (!branchCode.value) {
    loading.value = false
    errorMessage.value = 'กรุณาเลือกสาขาจาก Header'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    // ใช้ค่าจาก dashboard, appointments, balances และ revenue API ตามความหมายของแต่ละ metric
    const [summary, appointments, balances, revenueReport, dailySummary] = await Promise.all([
      reportApi.dashboard(from.value, to.value, branchCode.value || undefined),
      reportApi.appointments(from.value, to.value, branchCode.value || undefined),
      reportApi.balances(from.value, to.value, branchCode.value || undefined),
      reportApi.revenue(from.value, to.value, branchCode.value || undefined),
      reportApi.dashboard(today.value, today.value, branchCode.value),
    ])
    if (requestId !== dashboardRequest) return
    // แสดงยอดรายวันแยกจากช่วงวันที่ที่เลือกด้านบน
    patients.value = summary.uniquePatients
    visits.value = summary.visits
    appointmentTotal.value = appointments.appointments
    revenue.value = Number(summary.cashReceived)
    sales.value = Number(summary.salesTotal)
    newCustomers.value = summary.newCustomers
    packageUpdates.value = summary.packageUpdates
    packageUpdatesScope.value = summary.packageUpdatesScope
    courses.value = balances.purchases
    todaySummary.value = {
      patients: dailySummary.uniquePatients,
      receipts: Number(dailySummary.cashReceived),
      sales: Number(dailySummary.salesTotal),
    }
    appointmentStatuses.value = appointments.byStatus.map((item) => ({
      status: normalizeStatus(item.status),
      appointments: item.appointments,
    }))
    const byMonth = new Map<string, { course: number; singleVisit: number }>()
    const dailyByCategory = revenueReport.dailyByCategory ?? []
    dailyByCategory.forEach((item) => {
      const month = item.date.slice(0, 7)
      const row = byMonth.get(month) ?? { course: 0, singleVisit: 0 }
      const type = normalizeStatus(item.type)
      if (type === 'COURSE' || type === 'COURSE_PURCHASE') row.course += Number(item.amount)
      if (type === 'SINGLE_VISIT') row.singleVisit += Number(item.amount)
      byMonth.set(month, row)
    })
    const firstMonth = from.value.slice(0, 7)
    const lastMonth = to.value.slice(0, 7)
    const showYear = firstMonth.slice(0, 4) !== lastMonth.slice(0, 4)
    const currentMonth = new Date(`${firstMonth}-01T00:00:00`)
    const months: typeof monthlyRevenue.value = []
    while (true) {
      const key = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, '0')}`
      if (key > lastMonth) break
      const label = new Intl.DateTimeFormat('th-TH', {
        month: 'short',
        ...(showYear ? { year: 'numeric' as const } : {}),
      }).format(currentMonth)
      months.push({ key, label, ...(byMonth.get(key) ?? { course: 0, singleVisit: 0 }) })
      currentMonth.setMonth(currentMonth.getMonth() + 1)
    }
    monthlyRevenue.value = months
    hasLoaded.value = true
  } catch {
    if (requestId !== dashboardRequest) return
    hasLoaded.value = true
    errorMessage.value = 'โหลดข้อมูลไม่สำเร็จ กรุณาลองใหม่หรือเลือกช่วงเวลาอื่น'
  } finally {
    if (requestId === dashboardRequest) loading.value = false
  }
}

async function refreshToday() {
  if (!branchCode.value || document.hidden || loading.value) return
  const currentDay = clinicToday()
  if (currentDay !== today.value) {
    today.value = currentDay
    await loadDashboard()
    return
  }
  const requestId = ++todayRequest
  const branch = branchCode.value
  try {
    const summary = await reportApi.dashboard(currentDay, currentDay, branch)
    if (requestId !== todayRequest || branch !== branchCode.value) return
    todaySummary.value = {
      patients: summary.uniquePatients,
      receipts: Number(summary.cashReceived),
      sales: Number(summary.salesTotal),
    }
  } catch {
    // Keep the last successful figures; the regular dashboard retry handles errors.
  }
}

onMounted(async () => {
  await loadDashboard()
  refreshTimer = setInterval(() => void refreshToday(), 60_000)
  window.addEventListener('focus', refreshToday)
  document.addEventListener('visibilitychange', refreshToday)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('focus', refreshToday)
  document.removeEventListener('visibilitychange', refreshToday)
})
watch([from, to, branchCode], loadDashboard)
function chooseDraftMonth(period: string) {
  if (!shortcutSelectionStarted.value) {
    const [year, month] = period.split('-').map(Number)
    const lastDay = new Date(year!, month!, 0).getDate()
    customFrom.value = `${period}-01`
    customTo.value = `${period}-${String(lastDay).padStart(2, '0')}`
    shortcutSelectionStarted.value = true
    return
  }
  let start = period
  let end = period
  const range = selectedDraftMonths.value
  if (range) {
    if (period < range.start) {
      end = range.end
    } else if (period > range.end) {
      start = range.start
    } else if (range.start === range.end) {
      customFrom.value = ''
      customTo.value = ''
      return
    } else if (period === range.start) {
      const [year, month] = period.split('-').map(Number)
      const next = new Date(year!, month!, 1)
      start = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
      end = range.end
    } else {
      const [year, month] = period.split('-').map(Number)
      start = range.start
      const previous = new Date(year!, month! - 2, 1)
      end = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`
    }
  }
  const [year, month] = end.split('-').map(Number)
  const lastDay = new Date(year!, month!, 0).getDate()
  customFrom.value = `${start}-01`
  customTo.value = `${end}-${String(lastDay).padStart(2, '0')}`
}
function openCustomRange() {
  rangeTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  lastEditedRangeField.value = 'from'
  customFrom.value = from.value
  customTo.value = to.value
  shortcutSelectionStarted.value = false
  shortcutYear.value = Number(from.value.slice(0, 4)) || now.getFullYear()
  customRangeOpen.value = true
  void nextTick(() => rangeDialog.value?.focus())
}

function closeCustomRange() {
  customRangeOpen.value = false
  void nextTick(() => rangeTrigger?.focus())
}

function handleRangeKeys(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeCustomRange()
  }
  if (event.key !== 'Tab') return
  const controls = Array.from(
    rangeDialog.value?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex="0"]',
    ) ?? [],
  )
  const first = controls[0]
  const last = controls.at(-1)
  if (!first || !last) return
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === rangeDialog.value)
  ) {
    event.preventDefault()
    last.focus()
  } else if (
    !event.shiftKey &&
    (document.activeElement === last || document.activeElement === rangeDialog.value)
  ) {
    event.preventDefault()
    first.focus()
  }
}

function resetDateRange() {
  const current = clinicToday()
  const monthStart = current.slice(0, 7) + '-01'
  const unchanged = from.value === monthStart && to.value === current
  from.value = monthStart
  to.value = current
  customFrom.value = monthStart
  customTo.value = current
  if (unchanged) void loadDashboard()
}

function applyCustomRange() {
  if (!customFrom.value || !customTo.value || customFrom.value > customTo.value) return
  const rangeUnchanged = from.value === customFrom.value && to.value === customTo.value
  loading.value = true
  errorMessage.value = ''
  from.value = customFrom.value
  to.value = customTo.value
  closeCustomRange()
  if (rangeUnchanged) void loadDashboard()
}
</script>

<template>
  <div class="manager-dashboard">
    <StaffHeader />
    <main
      v-if="loading && !hasLoaded"
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
        <div class="loading-card-grid today-grid">
          <div v-for="card in 3" :key="card" class="loading-card">
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
    <main v-else class="dashboard-main" :aria-busy="loading">
      <header class="dashboard-heading">
        <div>
          <h1>ภาพรวมแดชบอร์ด</h1>
          <p class="dashboard-branch-label" aria-live="polite">{{ branchName }}</p>
        </div>
      </header>

      <p v-if="errorMessage" class="dashboard-feedback error" role="alert">
        {{ errorMessage }}
        <button class="dashboard-retry" type="button" @click="loadDashboard">ลองใหม่</button>
      </p>
      <p v-if="loading" class="dashboard-feedback" role="status">กำลังอัปเดตข้อมูล…</p>

      <div class="dashboard-content">
        <section
          v-if="!errorMessage && !loading"
          class="dashboard-section today-section"
          aria-label="ภาพรวมวันนี้"
        >
          <h2>ภาพรวมวันนี้</h2>
          <p class="section-caption">
            {{
              new Date(`${today}T12:00:00`).toLocaleDateString('th-TH', {
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })
            }}
          </p>
          <div class="today-grid">
            <article class="today-card">
              <i></i>
              <p>ผู้ป่วยวันนี้</p>
              <b>{{ todaySummary.patients.toLocaleString() }} คน</b>
            </article>
            <article class="today-card">
              <i></i>
              <p>ยอดรับชำระวันนี้</p>
              <b>{{ money(todaySummary.receipts) }}</b>
            </article>
            <article class="today-card">
              <i></i>
              <p>ยอดขายวันนี้</p>
              <b>{{ money(todaySummary.sales) }}</b>
            </article>
          </div>
        </section>

        <section class="dashboard-section period-section" aria-label="สรุปตามช่วงเวลา">
          <div class="section-row">
            <div>
              <h2>สรุปตามช่วงเวลา</h2>
              <p class="section-caption">{{ selectedDateRange }}</p>
            </div>
            <div class="period-actions">
              <button
                class="custom-range-button"
                type="button"
                aria-haspopup="dialog"
                :aria-expanded="customRangeOpen"
                @click="openCustomRange"
              >
                กำหนดช่วงเวลา
              </button>
              <button
                class="reset-range-button"
                type="button"
                aria-label="รีเซ็ตช่วงเวลาเป็นเดือนนี้ถึงวันนี้"
                title="รีเซ็ตเป็นเดือนนี้ถึงวันนี้"
                @click="resetDateRange"
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M3 10a9 9 0 1 1 9 11M3 10V4m0 6h6m3-3v5h4"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </div>
          <div v-if="!errorMessage && !loading" class="summary-layout" aria-label="ข้อมูลสรุป">
            <div class="summary-column">
              <article class="summary-card">
                <p>ผู้ป่วยในช่วงนี้</p>
                <b>{{ patients.toLocaleString() }} คน</b>
              </article>
              <article class="summary-card">
                <p>ยอดขายตามวันที่ขาย</p>
                <b>{{ money(sales) }}</b>
              </article>
              <article class="summary-card">
                <p>ยอดรับชำระตามวันที่รับเงิน</p>
                <b>{{ money(revenue) }}</b>
              </article>
            </div>
            <div class="summary-column">
              <article class="summary-card">
                <p>เข้ารับบริการ</p>
                <b>{{ visits.toLocaleString() }} ครั้ง</b>
              </article>
              <article class="summary-card">
                <p>คอร์สที่ซื้อ</p>
                <b>{{ courses.toLocaleString() }} คอร์ส</b>
              </article>
              <article class="summary-card">
                <p>ลูกค้าใหม่</p>
                <b>{{ Number(newCustomers || 0).toLocaleString() }} คน</b>
              </article>
              <article class="summary-card">
                <p>ปรับสิทธิ์คอร์ส ({{ packageUpdatesScope === 'CLINIC' ? 'ทั้งคลินิก' : packageUpdatesScope }})</p>
                <b>{{ Number(packageUpdates || 0).toLocaleString() }} รายการ</b>
              </article>
            </div>
          </div>
        </section>

        <section v-if="!errorMessage && !loading" class="revenue-card">
          <header>
            <div>
              <h2>ยอดรับชำระรายเดือน</h2>
              <p>{{ selectedDateRange }}</p>
            </div>
            <div class="chart-legend">
              <span class="course">คอร์ส</span><span class="visit">รายครั้ง</span>
            </div>
          </header>
          <p v-if="!hasRevenue" class="empty-state">ยังไม่มียอดรับชำระในช่วงนี้</p>
          <div
            v-else
            class="chart-shell"
            role="img"
            aria-label="กราฟยอดรับชำระรายเดือน หน่วยบาท ดูค่าที่แน่นอนในตารางด้านล่าง"
          >
            <div class="chart-axis" aria-hidden="true">
              <span v-for="tick in chartTicks" :key="tick">{{ tick.toLocaleString() }}</span>
            </div>
            <div class="monthly-chart">
              <div v-for="month in monthlyRevenue" :key="month.key" class="month-group">
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
          <div
            v-if="hasRevenue"
            class="revenue-table-wrap"
            tabindex="0"
            role="region"
            aria-label="ตารางยอดรับชำระรายเดือน"
          >
            <table class="revenue-table">
              <caption>
                ยอดรับชำระ (บาท)
              </caption>
              <thead>
                <tr>
                  <th scope="col">เดือน</th>
                  <th scope="col">คอร์ส</th>
                  <th scope="col">รายครั้ง</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="month in monthlyRevenue" :key="month.key">
                  <th scope="row">{{ month.label }}</th>
                  <td>{{ money(month.course) }}</td>
                  <td>{{ money(month.singleVisit) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="!errorMessage && !loading" class="lower-grid">
          <article class="status-card">
            <header>
              <div>
                <h2>สถานะนัดหมาย</h2>
                <p>จากนัดหมายทั้งหมด {{ appointmentTotal.toLocaleString() }} ครั้ง</p>
              </div>
            </header>
            <p v-if="!appointmentStatuses.length" class="empty-state" role="status">
              ยังไม่มีนัดหมายในช่วงนี้
            </p>
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
      </div>
    </main>

    <div
      v-if="customRangeOpen"
      class="custom-range-backdrop"
      role="presentation"
      @click.self="closeCustomRange"
    >
      <section
        ref="rangeDialog"
        class="custom-range-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="custom-range-title"
        tabindex="-1"
        @keydown="handleRangeKeys"
      >
        <header>
          <div>
            <h2 id="custom-range-title">เลือกช่วงเวลา</h2>
          </div>
          <button class="dialog-close" type="button" aria-label="ปิด" @click="closeCustomRange">
            ×
          </button>
        </header>
        <div class="range-picker-layout">
          <section class="range-month-panel" aria-labelledby="range-month-title">
            <div class="range-month-heading">
              <h3 id="range-month-title">เลือกเดือน</h3>
              <label class="range-year-picker">
                <span>ปี</span>
                <select v-model.number="shortcutYear" aria-label="เลือกปี">
                  <option v-for="year in shortcutYears" :key="year" :value="year">
                    {{ year + 543 }}
                  </option>
                </select>
              </label>
            </div>
            <div class="range-months" role="group" :aria-label="`เลือกเดือน${shortcutYearLabel}`">
              <button
                v-for="option in periodOptions"
                :key="option.value"
                class="range-month-button"
                type="button"
                :aria-pressed="
                  Boolean(
                    selectedDraftMonths &&
                    option.value >= selectedDraftMonths.start &&
                    option.value <= selectedDraftMonths.end,
                  )
                "
                @click="chooseDraftMonth(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </section>
          <section class="range-date-panel" aria-labelledby="range-date-title">
            <h3 id="range-date-title">กำหนดช่วงวันที่</h3>
            <div class="custom-range-fields">
              <label
                >วันที่เริ่มต้น
                <ThaiDatePicker
                  v-model="customFrom"
                  show-calendar-icon
                  numeric-month
                  hide-caret
                  @update:model-value="lastEditedRangeField = 'from'"
                />
              </label>
              <label
                >วันที่สิ้นสุด
                <ThaiDatePicker
                  v-model="customTo"
                  @update:model-value="lastEditedRangeField = 'to'"
                  :min-date="customFrom"
                  inclusive-min
                  show-calendar-icon
                  numeric-month
                  hide-caret
                />
              </label>
            </div>
          </section>
        </div>
        <p v-if="customFrom && customTo && customFrom > customTo" class="range-error" role="alert">
          {{
            lastEditedRangeField === 'from'
              ? 'วันเริ่มต้นต้องเป็นวันเดียวกันกับวันสิ้นสุดหรือก่อนวันสิ้นสุด'
              : 'วันสิ้นสุดต้องเป็นวันเดียวกันกับวันเริ่มต้นหรือหลังวันเริ่มต้น'
          }}
        </p>
        <footer>
          <button class="dialog-cancel" type="button" @click="closeCustomRange">ยกเลิก</button>
          <button
            class="dialog-submit"
            type="button"
            :disabled="!customFrom || !customTo || customFrom > customTo"
            @click="applyCustomRange"
          >
            ยืนยัน
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
  color: var(--color-ink);
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
  font-size: clamp(1.75rem, 2.4vw, 2.25rem);
  line-height: 1.35;
  color: var(--color-primary-strong);
  font-weight: 800;
}
.dashboard-heading .dashboard-branch-label {
  color: var(--color-ink);
  font-size: 1rem;
  font-weight: 500;
}
.dashboard-heading p,
.revenue-card header p {
  margin: 10px 0 0;
  color: var(--color-muted);
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
  color: var(--color-muted);
  font-size: 0.92rem;
}
.today-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  color: var(--color-muted);
  font-size: 0.9rem;
}
.today-card b {
  display: block;
  margin: 7px 0 4px;
  font-size: clamp(1.6rem, 2.1vw, 2rem);
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.today-card small {
  color: var(--color-muted);
  font-size: 0.76rem;
}
.period-section .summary-card {
  min-height: 140px;
  padding: 22px 24px;
  border-color: #e6e1dc;
}
.period-section .summary-card p {
  color: var(--color-muted);
  font-size: 0.9rem;
}
.period-section .summary-card b {
  margin: 7px 0 4px;
  font-size: clamp(1.6rem, 2.1vw, 2rem);
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
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
.custom-range-button {
  min-height: 44px;
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
  width: min(100%, 860px);
  box-sizing: border-box;
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
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
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border: 0;
  border-radius: 50%;
  color: #47615b;
  background: #edf5f1;
  font-size: 1.55rem;
  line-height: 1;
  cursor: pointer;
}
.dialog-close:hover {
  background: #dcece5;
}
.custom-range-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 13px;
  align-items: end;
  margin-top: 20px;
}
.range-picker-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px;
  margin-top: 24px;
}
.range-picker-layout h3 {
  margin: 0;
  color: #294943;
  font-size: 1rem;
}
.range-date-panel {
  padding-left: 28px;
  border-left: 1px solid #dce6e1;
}
.range-date-panel > h3 {
  display: flex;
  align-items: center;
  min-height: 44px;
}
.range-date-panel .custom-range-fields {
  margin-top: 14px;
}
.range-month-hint {
  margin: 6px 0 14px;
  color: var(--color-muted);
  font-size: 0.82rem;
  line-height: 1.5;
}
.range-month-heading,
.range-year-picker {
  display: flex;
  align-items: center;
  gap: 10px;
}
.range-month-heading {
  justify-content: space-between;
  min-height: 44px;
}
.range-year-picker {
  color: #294943;
  font-size: 0.9rem;
}
.range-year-picker select {
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #b8cec7;
  border-radius: 8px;
  background: #fff;
  color: #176f6b;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.range-year-picker select:focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
}
.range-months {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}
.range-month-button {
  min-height: 44px;
  padding: 8px;
  border: 1px solid #b8cec7;
  border-radius: 8px;
  background: #fff;
  color: #176f6b;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.range-month-button:hover {
  background: #e8f4f0;
}
.range-month-button[aria-pressed='true'] {
  border-color: #167d78;
  background: #167d78;
  color: #fff;
}
.range-month-button[aria-pressed='true']:hover {
  background: #126b67;
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
.custom-range-fields :deep(.date-trigger.placeholder) {
  color: #6b7280;
  font-weight: 400;
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
  border: 1px solid #167d78;
  color: #fff;
  background: #167d78;
}
.dialog-submit:hover:not(:disabled) {
  background: #126b67;
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
  color: var(--color-muted);
  font-size: 0.78rem;
  font-weight: 700;
}
.filter-period {
  flex: 1;
  padding-right: 18px;
}
.filter-label {
  color: var(--color-muted);
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
  color: var(--color-primary);
}
.dashboard-feedback.error {
  background: #ffebe7;
  color: #a1433b;
}
.dashboard-retry {
  min-height: 40px;
  margin-left: 12px;
  padding: 7px 14px;
  border: 1px solid currentColor;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.dashboard-retry:hover {
  background: #fff;
}
.custom-range-button:focus-visible,
.dashboard-retry:focus-visible,
.custom-range-dialog button:focus-visible,
.revenue-table-wrap:focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
}
.revenue-table-wrap {
  margin-top: 24px;
  overflow-x: auto;
}
.revenue-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}
.revenue-table caption {
  text-align: left;
  margin-bottom: 8px;
  color: var(--color-muted);
}
.revenue-table th,
.revenue-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #e3eae6;
  text-align: right;
  white-space: nowrap;
}
.revenue-table th:first-child {
  text-align: left;
}
.revenue-table thead {
  background: #f2f7f4;
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
  gap: 20px;
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
  color: var(--color-muted);
  font-size: 1rem;
}
.summary-card b {
  margin: 10px 0 5px;
  font-size: clamp(1.55rem, 2.35vw, 2.35rem);
  line-height: 1.1;
}
.summary-card small {
  color: var(--color-muted);
}
.gender-bar {
  display: flex;
  height: 10px;
  margin: 15px 0 9px;
  overflow: hidden;
  border-radius: 20px;
  background: var(--color-primary);
}
.gender-bar span {
  background: #e6d479;
}
.gender-bar .gender-secondary {
  background: var(--color-primary);
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
  background: var(--color-primary);
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
  background: var(--color-primary);
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
  color: var(--color-muted);
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
  overflow-x: auto;
  background: repeating-linear-gradient(to bottom, transparent 0 49px, #e8ebe6 50px 51px);
}
.month-group {
  display: grid;
  flex: 1 0 64px;
  justify-items: center;
  gap: 10px;
  min-width: 48px;
  color: var(--color-muted);
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
  color: var(--color-muted);
}
.status-row b {
  color: var(--color-ink);
  font-size: 0.82rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.status-track {
  height: 13px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2ef;
}
.status-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #8faeae;
}
.status-track .status-completed {
  background: #39a96b;
}
.status-track .status-unconfirmed {
  background: #e8b93f;
}
.status-track .status-confirmed {
  background: #5599d0;
}
.status-track .status-arrived {
  background: #66b59f;
}
.status-track .status-in-service {
  background: #8a9fcd;
}
.status-track .status-no-show {
  background: #d49c55;
}
.status-track .status-cancelled {
  background: #df6671;
}
.status-track .status-rescheduled {
  background: #a68abd;
}
.empty-state {
  color: var(--color-muted);
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
    max-height: calc(100dvh - 24px);
    overflow-y: auto;
  }
  .range-picker-layout {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .range-date-panel {
    padding: 20px 0 0;
    border-left: 0;
    border-top: 1px solid #dce6e1;
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
    flex-direction: column;
  }
  .period-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }
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
    font-size: 1.75rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .skeleton,
  .dashboard-loading {
    animation: none;
  }
  .bar-area i {
    transition: none;
  }
}
</style>

<style scoped>
.reset-range-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  min-height: 44px;
  padding: 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-primary-strong);
  cursor: pointer;
}
.reset-range-button svg {
  width: 20px;
  height: 20px;
}
.reset-range-button:hover {
  background: #edf5f1;
}
.reset-range-button:focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
}
</style>
