<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import {
  branchApi,
  commissionApi,
  courseApi,
  reportApi,
  type Branch,
  type CourseBalanceReport,
  type RevenueReport,
  type TransferRecord,
} from '@/api/client'

type ReportKey = 'revenue' | 'balance' | 'transfer' | 'commission'

type CommissionReportRow = {
  staffId: number | null
  staffName: string
  branchCode: string
  branchName: string
  saleCount: number
  base: number
  rate: number
  commission: number
}

type ReportData =
  | RevenueReport
  | CourseBalanceReport
  | CommissionReportRow[]
  | TransferRecord[]
  | null

const report = ref<ReportKey>('revenue')
const filters = reactive({
  patient: '',
  staff: '',
  course: 'ทั้งหมด',
  branch: 'ทุกสาขา',
  treatment: 'ทั้งหมด',
  saleType: 'ทั้งหมด',
  commissionType: 'ทั้งหมด',
  from: `${new Date().getFullYear()}-08-01`,
  to: new Date().toISOString().slice(0, 10),
})

// ── Pagination State ──────────────────────────────────────────────────────────
const page = ref(1)
const pageSize = 10

const branches = ref<Branch[]>([])
const courseOptions = ref<Array<{ id: number; name: string }>>([])
const reportData = ref<ReportData>(null)
const loading = ref(false)
const errorMessage = ref('')
const lastUpdatedText = ref('')

const REPORTS: Array<[ReportKey, string]> = [
  ['revenue', 'รายงานยอดขาย'],
  ['balance', 'ยอดคงเหลือคอร์ส'],
  ['transfer', 'ประวัติการโอนคอร์ส'],
  ['commission', 'ค่าคอมมิชชั่น'],
]

const TABLE_HEADERS: Record<ReportKey, string[]> = {
  revenue: ['ประเภทการขาย', 'การรักษา', 'สาขา', 'วันที่', 'รายได้รวม', 'สัดส่วน'],
  balance: ['ผู้ป่วย', 'คอร์ส', 'สาขา', 'วันที่', 'ซื้อ', 'ใช้', 'โอน', 'คงเหลือ', 'หมดอายุ'],
  transfer: ['เลขที่รายการ', 'ผู้โอน', 'ผู้รับ', 'สาขา', 'คอร์ส', 'ครั้ง', 'วันที่'],
  commission: ['พนักงาน', 'สาขา', 'ประเภทงาน', 'วันที่', 'จำนวนรายการ', 'ค่าคอมมิชชั่น'],
}

const RIGHT_ALIGNED: Partial<Record<ReportKey, number[]>> = {
  revenue: [4, 5],
  balance: [4, 5, 6, 7, 8],
  transfer: [],
  commission: [4, 5],
}

const SALES_COMMISSION_TYPE = 'ค่าคอมมิชชันการขาย'

const CATEGORY_NAMES: Record<string, string> = {
  COURSE: 'Course Sales',
  SINGLE_VISIT: 'Single Visit',
  ASSESSMENT: 'Assessment',
  'Course Sales': 'Course Sales',
  'Single Visit Sales': 'Single Visit',
}

const TREATMENT_NAMES: Record<string, string> = {
  COURSE: 'Manual Therapy',
  SINGLE_VISIT: 'Exercise Therapy',
  ASSESSMENT: 'Assessment',
  'Course Sales': 'Manual Therapy',
  'Single Visit Sales': 'Exercise Therapy',
}

const currentReportTitle = computed(() => {
  if (report.value === 'commission') return 'รายงานค่าคอมมิชชั่น'
  return REPORTS.find(([k]) => k === report.value)?.[1] ?? ''
})

function getTreatmentName(type: string): string {
  if (filters.treatment !== 'ทั้งหมด') return filters.treatment
  return TREATMENT_NAMES[type] ?? '—'
}

function getBranchName(branchCode?: string): string {
  if (branchCode) {
    const found = branches.value.find((b) => b.branchCode === branchCode || b.name === branchCode)
    if (found) return found.name
    return branchCode
  }
  if (filters.branch !== 'ทุกสาขา') {
    const found = branches.value.find((b) => b.branchCode === filters.branch)
    return found ? found.name : filters.branch
  }
  return 'ทุกสาขา'
}

function getCourseName(courseId?: number): string {
  if (courseId) {
    const found = courseOptions.value.find((c) => c.id === courseId)
    if (found) return found.name
  }
  if (filters.course !== 'ทั้งหมด') return filters.course
  return courseId ? `คอร์ส #${courseId}` : '—'
}

const formatDate = (value: string) => {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(d)
}

const formatMoney = (val: number) => `฿${Number(val).toLocaleString()}`

const formatPeriod = (from: string, to: string) =>
  from === to ? formatDate(from) : `${formatDate(from)} – ${formatDate(to)}`

function updateTimestamp() {
  const now = new Date()
  const dateStr = new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(now)
  const timeStr = new Intl.DateTimeFormat('th-TH', { hour: '2-digit', minute: '2-digit' }).format(now)
  lastUpdatedText.value = `${dateStr}, ${timeStr}`
}

// ── Dynamic Filtering ────────────────────────────────────────────────────────

const revenueData = computed(() => reportData.value as RevenueReport | null)

const filteredRevenueCategories = computed(() => {
  if (!revenueData.value?.byCategory) return []
  let list = revenueData.value.byCategory

  if (filters.saleType === 'Course Sales') {
    list = list.filter((cat) => cat.type === 'COURSE' || cat.type === 'Course Sales')
  } else if (filters.saleType === 'Single Visit Sales') {
    list = list.filter((cat) => cat.type === 'SINGLE_VISIT' || cat.type === 'Single Visit Sales')
  }

  if (filters.treatment !== 'ทั้งหมด') {
    list = list.filter((cat) => {
      const typeName = CATEGORY_NAMES[cat.type] ?? cat.type
      if (filters.treatment === 'Assessment') return cat.type === 'ASSESSMENT' || typeName === 'Assessment'
      if (filters.treatment === 'Manual Therapy') return cat.type === 'COURSE' || cat.type === 'Course Sales'
      if (filters.treatment === 'Exercise Therapy') return cat.type === 'SINGLE_VISIT' || cat.type === 'Single Visit'
      return true
    })
  }

  return list
})

// KPI Items (Only active for Revenue report per Figma)
const kpiItems = computed(() => {
  if (report.value !== 'revenue' || !revenueData.value) return []
  const list = filteredRevenueCategories.value
  const total = list.reduce((sum, cat) => sum + Number(cat.amount), 0)

  const items = list
    .filter((cat) => Number(cat.amount) > 0)
    .map((cat) => {
      const typeName = CATEGORY_NAMES[cat.type] ?? cat.type
      const share = total > 0 ? Math.round((Number(cat.amount) / total) * 100) : 0
      return {
        label: `รายได้จาก ${typeName}`,
        amount: formatMoney(cat.amount),
        share: `${share}%`,
      }
    })

  return [
    { label: 'รายได้รวม', amount: formatMoney(total), share: '100%' },
    ...items,
  ]
})

const activeKpis = computed(() => (report.value === 'revenue' ? kpiItems.value : []))

// Filtered Course Balance Rows
const balanceData = computed(() => reportData.value as CourseBalanceReport | null)

type ExtendedBalanceRow = {
  hn: string
  patientFullName: string
  branchCode: string
  courseName: string
  purchased: number
  used: number
  remaining: number
  purchasedAt: string
  expiresAt: string
  expired: boolean
}

const filteredBalanceRows = computed<ExtendedBalanceRow[]>(() => {
  if (!balanceData.value?.rows) return []
  let list = balanceData.value.rows as ExtendedBalanceRow[]

  if (filters.patient) {
    const q = filters.patient.trim().toLowerCase()
    list = list.filter((row) => `${row.hn} ${row.patientFullName}`.toLowerCase().includes(q))
  }

  if (filters.course !== 'ทั้งหมด') {
    list = list.filter((row) => row.courseName.toLowerCase().includes(filters.course.toLowerCase()))
  }

  return list
})

// Filtered Commission Rows
const filteredCommissionRows = computed<CommissionReportRow[]>(() => {
  const data = (reportData.value as CommissionReportRow[] | null) ?? []

  return data.filter((row) => {
    const qStaff = filters.staff.trim().toLowerCase()
    const matchStaff = !qStaff || (row.staffName && row.staffName.toLowerCase().includes(qStaff))
    const matchBranch =
      filters.branch === 'ทุกสาขา' ||
      row.branchCode === filters.branch ||
      row.branchName === filters.branch
    const matchType =
      filters.commissionType === 'ทั้งหมด' || filters.commissionType === SALES_COMMISSION_TYPE
    return matchStaff && matchBranch && matchType
  })
})

// All Table Rows
const tableRows = computed<string[][]>(() => {
  if (report.value === 'revenue') {
    const list = filteredRevenueCategories.value
    const total = list.reduce((sum, cat) => sum + Number(cat.amount), 0)
    return list.map((row) => {
      const share = total > 0 ? Math.round((Number(row.amount) / total) * 100) : 0
      return [
        CATEGORY_NAMES[row.type] ?? row.type,
        getTreatmentName(row.type),
        getBranchName(),
        formatPeriod(filters.from, filters.to),
        formatMoney(row.amount),
        `${share}%`,
      ]
    })
  }

  if (report.value === 'balance') {
    return filteredBalanceRows.value.map((row) => [
      row.patientFullName,
      row.courseName,
      getBranchName(row.branchCode),
      formatDate(row.purchasedAt),
      String(row.purchased),
      String(row.used),
      '—',
      String(row.remaining),
      formatDate(row.expiresAt),
    ])
  }

  if (report.value === 'transfer') {
    const data = (reportData.value as TransferRecord[] | null) ?? []
    return data
      .filter((row) => {
        const q = filters.patient.trim().toLowerCase()
        const matchPatient =
          !q ||
          `${row.sourcePatientName} ${row.sourcePatientHn} ${row.destinationPatientName} ${row.destinationPatientHn}`
            .toLowerCase()
            .includes(q)
        const matchBranch =
          filters.branch === 'ทุกสาขา' ||
          row.branchCode === filters.branch
        const matchCourse =
          filters.course === 'ทั้งหมด' ||
          getCourseName(row.courseId).toLowerCase().includes(filters.course.toLowerCase())
        const transferDate = row.createdAt.slice(0, 10)
        const matchDate = transferDate >= filters.from && transferDate <= filters.to
        return matchPatient && matchBranch && matchCourse && matchDate
      })
      .map((row) => {
        return [
          `TRF-${row.id}`,
          row.sourcePatientName,
          row.destinationPatientName,
          getBranchName(row.branchCode),
          getCourseName(row.courseId),
          `${row.quantity} ครั้ง`,
          formatDate(row.createdAt),
        ]
      })
  }

  if (report.value === 'commission') {
    return filteredCommissionRows.value.map((row) => [
      row.staffName || 'ไม่ระบุ',
      row.branchName || getBranchName(row.branchCode),
      SALES_COMMISSION_TYPE,
      formatPeriod(filters.from, filters.to),
      `${row.saleCount} รายการ`,
      formatMoney(row.commission),
    ])
  }

  return []
})

// Pagination Calculations
const totalPages = computed(() => Math.max(1, Math.ceil(tableRows.value.length / pageSize)))

const paginatedRows = computed(() => {
  const start = (page.value - 1) * pageSize
  return tableRows.value.slice(start, start + pageSize)
})

// Reset page to 1 when filters or report change
watch([report, filters], () => {
  page.value = 1
}, { deep: true })

// Dynamic Total Row
const totalRow = computed<string[] | null>(() => {
  if (!tableRows.value.length) return null

  if (report.value === 'revenue') {
    const total = filteredRevenueCategories.value.reduce((sum, cat) => sum + Number(cat.amount), 0)
    return ['รวมทั้งหมด', '', '', '', formatMoney(total), '100%']
  }

  if (report.value === 'balance') {
    const rows = filteredBalanceRows.value
    const p = rows.reduce((s, r) => s + r.purchased, 0)
    const u = rows.reduce((s, r) => s + r.used, 0)
    const rem = rows.reduce((s, r) => s + r.remaining, 0)
    return ['รวมทั้งหมด', '', '', '', String(p), String(u), '—', String(rem), '—']
  }

  if (report.value === 'commission') {
    const rows = filteredCommissionRows.value
    const totalCount = rows.reduce((s, r) => s + r.saleCount, 0)
    const totalComm = rows.reduce((s, r) => s + r.commission, 0)
    return ['รวมทั้งหมด', '', '', '', `${totalCount} รายการ`, formatMoney(totalComm)]
  }

  return null
})

async function fetchReport() {
  loading.value = true
  errorMessage.value = ''
  reportData.value = null
  try {
    const branchCode = filters.branch === 'ทุกสาขา' ? undefined : filters.branch
    switch (report.value) {
      case 'revenue':
        reportData.value = await reportApi.revenue(filters.from, filters.to, branchCode)
        break
      case 'balance':
        reportData.value = await reportApi.balances(filters.from, filters.to, branchCode)
        break
      case 'transfer':
        reportData.value = await courseApi.transferHistory()
        break
      case 'commission':
        reportData.value = await commissionApi.report(filters.from, filters.to)
        break
    }
    updateTimestamp()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'โหลดรายงานไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

function isRightAligned(colIndex: number) {
  return RIGHT_ALIGNED[report.value]?.includes(colIndex) ?? false
}

onMounted(async () => {
  try {
    const [branchList, courseList] = await Promise.all([branchApi.list(), courseApi.list()])
    branches.value = branchList
    courseOptions.value = courseList.map((c) => ({ id: c.id, name: c.name }))
  } catch {
    // Non-critical
  }
  await fetchReport()
})

watch([report, () => filters.branch, () => filters.from, () => filters.to], fetchReport)
</script>

<template>
  <div class="reports-page-wrapper">
    <StaffHeader />

    <main class="reports-page-container">
      <!-- ── Header Section ──────────────────────────────────────────────── -->
      <section class="page-title-section">
        <h1>รายงาน</h1>
        <p>ตรวจสอบรายได้ คอร์ส ยอดขาย และค่าคอมมิชชั่น</p>
      </section>

      <!-- ── KPI Summary Box (Only for Revenue Report per Figma) ──────────── -->
      <section v-if="report === 'revenue' && activeKpis.length" class="kpi-summary-box">
        <div v-for="(kpi, idx) in activeKpis" :key="idx" class="kpi-col">
          <span class="kpi-label">{{ kpi.label }}</span>
          <div class="kpi-value-row">
            <b class="kpi-amount">{{ kpi.amount }}</b>
            <span class="kpi-share">{{ kpi.share }}</span>
          </div>
        </div>
      </section>

      <!-- ── Filter Card Section ─────────────────────────────────────────── -->
      <section class="filter-card">
        <!-- Top bar: Title left, Select dropdown right -->
        <div class="filter-card-header">
          <h2>{{ currentReportTitle }}</h2>
          <div class="report-select-group">
            <label for="report-select">เลือกรายงาน</label>
            <div class="select-wrapper">
              <select id="report-select" v-model="report">
                <option v-for="[key, label] in REPORTS" :key="key" :value="key">{{ label }}</option>
              </select>
              <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
          </div>
        </div>

        <!-- Filter Grid -->
        <div class="filter-card-body">
          <!-- ── Row 1 Filters ── -->
          <div class="filter-grid-row1">
            <!-- Balance / Transfer Report Filters -->
            <template v-if="['balance', 'transfer'].includes(report)">
              <div class="field-group">
                <label>ผู้ป่วย</label>
                <input v-model="filters.patient" type="text" placeholder="ค้นหาชื่อผู้ป่วย" />
              </div>

              <div class="field-group">
                <label>คอร์ส</label>
                <div class="select-wrapper">
                  <select v-model="filters.course">
                    <option>ทั้งหมด</option>
                    <option v-for="c in courseOptions" :key="c.id" :value="c.name">
                      {{ c.name }}
                    </option>
                  </select>
                  <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                    <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>

              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker
                  v-model="filters.from"
                  numeric-month
                  show-calendar-icon
                />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker
                  v-model="filters.to"
                  numeric-month
                  show-calendar-icon
                />
              </div>
            </template>

            <!-- Revenue Report Filters -->
            <template v-else-if="report === 'revenue'">
              <div class="field-group">
                <label>สาขา</label>
                <div class="select-wrapper">
                  <select v-model="filters.branch">
                    <option>ทุกสาขา</option>
                    <option v-for="b in branches" :key="b.branchCode" :value="b.branchCode">
                      {{ b.name }} ({{ b.branchCode }})
                    </option>
                  </select>
                  <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                    <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>

              <div class="field-group">
                <label>การรักษา</label>
                <div class="select-wrapper">
                  <select v-model="filters.treatment">
                    <option>ทั้งหมด</option>
                    <option>Assessment</option>
                    <option>Manual Therapy</option>
                    <option>Exercise Therapy</option>
                  </select>
                  <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                    <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>

              <div class="field-group">
                <label>ประเภทการขาย</label>
                <div class="select-wrapper">
                  <select v-model="filters.saleType">
                    <option>ทั้งหมด</option>
                    <option>Course Sales</option>
                    <option>Single Visit Sales</option>
                  </select>
                  <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                    <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>
            </template>

            <!-- Commission Report Filters -->
            <template v-else-if="report === 'commission'">
              <div class="field-group">
                <label>พนักงาน</label>
                <input v-model="filters.staff" type="text" placeholder="ค้นหาชื่อพนักงาน" />
              </div>

              <div class="field-group">
                <label>สาขา</label>
                <div class="select-wrapper">
                  <select v-model="filters.branch">
                    <option>ทุกสาขา</option>
                    <option v-for="b in branches" :key="b.branchCode" :value="b.branchCode">
                      {{ b.name }} ({{ b.branchCode }})
                    </option>
                  </select>
                  <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                    <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>

              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker
                  v-model="filters.from"
                  numeric-month
                  show-calendar-icon
                />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker
                  v-model="filters.to"
                  numeric-month
                  show-calendar-icon
                />
              </div>
            </template>
          </div>

          <!-- ── Row 2 Filters ── -->
          <div class="filter-grid-row2">
            <!-- Branch for Balance / Transfer -->
            <div v-if="['balance', 'transfer'].includes(report)" class="field-group field-group--short">
              <label>สาขา</label>
              <div class="select-wrapper">
                <select v-model="filters.branch">
                  <option>ทุกสาขา</option>
                  <option v-for="b in branches" :key="b.branchCode" :value="b.branchCode">
                    {{ b.name }} ({{ b.branchCode }})
                  </option>
                </select>
                <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                  <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
            </div>

            <!-- Commission Type Filter for Commission Report -->
            <div v-if="report === 'commission'" class="field-group field-group--short">
              <label>ประเภทค่าคอมมิชชั่น</label>
              <div class="select-wrapper">
                <select v-model="filters.commissionType">
                  <option>ทั้งหมด</option>
                  <option>ค่ามือรักษา</option>
                  <option>ค่าคอมมิชชันการขาย</option>
                </select>
                <svg class="select-arrow" viewBox="0 0 20 20" fill="none">
                  <path d="M5 7.5L10 12.5L15 7.5" stroke="#4B5563" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
            </div>

            <!-- Date Pickers for Revenue Report -->
            <template v-if="report === 'revenue'">
              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker
                  v-model="filters.from"
                  numeric-month
                  show-calendar-icon
                />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker
                  v-model="filters.to"
                  numeric-month
                  show-calendar-icon
                />
              </div>
            </template>
          </div>
        </div>
      </section>

      <!-- ── Data Table Section ─────────────────────────────────────────── -->
      <section class="table-card">
        <p v-if="errorMessage" class="report-error" role="alert">{{ errorMessage }}</p>
        <p v-else-if="loading" class="report-loading">กำลังโหลดรายงาน...</p>
        <div class="table-scroll-wrapper">
          <table class="report-figma-table">
            <thead>
              <tr>
                <th
                  v-for="(head, idx) in TABLE_HEADERS[report]"
                  :key="head"
                  :class="{ 'text-right': isRightAligned(idx) }"
                >
                  {{ head }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rIdx) in paginatedRows" :key="rIdx">
                <td
                  v-for="(cell, cIdx) in row"
                  :key="cIdx"
                  :class="{ 'text-right': isRightAligned(cIdx) }"
                >
                  <span>{{ cell }}</span>
                </td>
              </tr>

              <!-- Total Row -->
              <tr v-if="totalRow" class="total-row">
                <td
                  v-for="(cell, cIdx) in totalRow"
                  :key="cIdx"
                  :class="{ 'text-right': isRightAligned(cIdx) }"
                >
                  <b>{{ cell }}</b>
                </td>
              </tr>

              <tr v-if="!tableRows.length && !loading">
                <td :colspan="TABLE_HEADERS[report]?.length" class="empty-cell">
                  ไม่พบข้อมูลตามตัวกรองที่เลือก
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ── Pagination Bar & Last Updated ─────────────────────────────── -->
        <div class="table-card-footer">
          <nav
            v-if="tableRows.length > 0"
            class="report-pagination"
            aria-label="เปลี่ยนหน้ารายการ"
          >
            <button
              type="button"
              class="btn-prev"
              :disabled="page === 1"
              @click="page -= 1"
            >
              ← ก่อนหน้า
            </button>
            <span class="pagination-info">หน้า {{ page }} จาก {{ totalPages }}</span>
            <button
              type="button"
              class="btn-next"
              :disabled="page === totalPages"
              @click="page += 1"
            >
              ถัดไป →
            </button>
          </nav>
          <div v-if="lastUpdatedText" class="last-updated-tag">
            อัปเดตล่าสุด {{ lastUpdatedText }}
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped src="../styles/reports.css"></style>
