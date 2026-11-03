<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TransactionReportView from './TransactionReportView.vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import {
  branchApi,
  commissionApi,
  courseApi,
  reportApi,
  type Branch,
  type CourseBalanceReport,
  type RevenueReport,
  type StaffSalesReport,
  type TransferRecord,
} from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { filterPaymentReportRows } from '../lib/paymentReport'

type ReportKey =
  | 'revenue'
  | 'balance'
  | 'transfer'
  | 'commission'
  | 'salesTransaction'
  | 'treatmentTransaction'
  | 'payment'

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

type TreatmentTransactionRow = Awaited<ReturnType<typeof commissionApi.treatmentReport>>[number]
type ReportData =
  | RevenueReport
  | CourseBalanceReport
  | CommissionReportRow[]
  | TransferRecord[]
  | StaffSalesReport
  | TreatmentTransactionRow[]
  | null

const REPORT_KEYS: readonly ReportKey[] = [
  'revenue',
  'balance',
  'transfer',
  'commission',
  'salesTransaction',
  'treatmentTransaction',
  'payment',
]

const REPORTS: Array<[ReportKey, string]> = [
  ['revenue', 'รายรับตามวันที่รับชำระ'],
  ['balance', 'ยอดคงเหลือคอร์ส'],
  ['transfer', 'ประวัติการโอนคอร์ส'],
  ['commission', 'ค่าคอมมิชชั่น'],
  ['salesTransaction', 'Transaction'],
  ['payment', 'รายงานยอดรับชำระ'],
]

const TABLE_HEADERS: Record<ReportKey, string[]> = {
  revenue: ['ประเภทการขาย', 'การรักษา', 'สาขา', 'วันที่รับเงิน', 'ยอดรับชำระสุทธิ', 'สัดส่วน'],
  balance: ['ผู้ป่วย', 'คอร์ส', 'สาขา', 'วันที่ซื้อ', 'ซื้อ', 'ใช้', 'โอน', 'คงเหลือ', 'หมดอายุ'],
  transfer: ['เลขที่รายการ', 'ผู้โอน', 'ผู้รับ', 'สาขา', 'คอร์ส', 'ครั้ง', 'วันที่โอน'],
  commission: ['พนักงาน', 'สาขา', 'ประเภทงาน', 'ช่วงงวดขาย', 'จำนวนรายการ', 'ค่าคอมมิชชั่น'],
  salesTransaction: ['ผู้ขาย', 'จำนวนใบเสร็จ', 'ยอดขาย', 'ช่วงวันที่ขาย'],
  treatmentTransaction: [
    'วันที่ให้บริการ',
    'สาขา',
    'ผู้ป่วย',
    'นักกายภาพบำบัด',
    'คอร์ส',
    'ค่ามือ',
    'มูลค่าบริการ',
  ],
  payment: ['ประเภทการขาย', 'การรักษา', 'สาขา', 'วันที่รับเงิน', 'ยอดรับชำระ', 'สัดส่วน'],
}

const RIGHT_ALIGNED: Partial<Record<ReportKey, number[]>> = {
  revenue: [4, 5],
  balance: [4, 5, 6, 7, 8],
  transfer: [],
  commission: [4, 5],
  salesTransaction: [1, 2],
  treatmentTransaction: [5, 6],
  payment: [4, 5],
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

function isReportKey(value: string): value is ReportKey {
  return REPORT_KEYS.includes(value as ReportKey)
}

function reportFromQuery(value: unknown): ReportKey {
  const reportKey = String(value ?? 'revenue')
  return isReportKey(reportKey) ? reportKey : 'revenue'
}

const route = useRoute()
const report = ref<ReportKey>(reportFromQuery(route.query.report))
watch(
  () => route.query.report,
  (value) => {
    report.value = reportFromQuery(value)
  },
)

const filters = reactive({
  patient: '',
  staff: '',
  course: 'ทั้งหมด',
  treatment: 'ทั้งหมด',
  saleType: 'ทั้งหมด',
  commissionType: 'ทั้งหมด',
  from: `${clinicToday().slice(0, 7)}-01`,
  to: clinicToday(),
})

const page = ref(1)
const pageSize = 10

const branches = ref<Branch[]>([])
const courseOptions = ref<Array<{ id: number; name: string }>>([])
const reportData = ref<ReportData>(null)
const loading = ref(false)
const errorMessage = ref('')
const lastUpdatedText = ref('')

const reportSelection = computed({
  get: () => (report.value === 'treatmentTransaction' ? 'salesTransaction' : report.value),
  set: (value: string) => {
    report.value = value as ReportKey
  },
})
const reportOptions = computed(() => REPORTS.map(([value, label]) => ({ value, label })))
const courseFilterOptions = computed(() =>
  courseOptions.value.map((course) => ({ value: course.name, label: course.name })),
)
const selectedHeaderBranchCode = computed(
  () =>
    selectedManagerBranch()?.code ??
    JSON.parse(localStorage.getItem('authUser') ?? 'null')?.branchCode ??
    '',
)

const currentReportTitle = computed(() => {
  if (['salesTransaction', 'treatmentTransaction'].includes(report.value)) return 'Transaction'
  if (report.value === 'commission') return 'รายงานค่าคอมมิชชั่น'
  return REPORTS.find(([k]) => k === report.value)?.[1] ?? ''
})

const isTransactionReport = computed(() =>
  ['salesTransaction', 'treatmentTransaction'].includes(report.value),
)

function getTreatmentName(type: string): string {
  if (filters.treatment !== 'ทั้งหมด') return filters.treatment
  return TREATMENT_NAMES[type] ?? '—'
}

function getBranchName(branchCode?: string): string {
  if (branchCode) {
    const found = branches.value.find((b) => b.code === branchCode || b.name === branchCode)
    if (found) return found.name
    return branchCode
  }
  if (selectedHeaderBranchCode.value) {
    const found = branches.value.find((b) => b.code === selectedHeaderBranchCode.value)
    return found ? found.name : selectedHeaderBranchCode.value
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
  return new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'Asia/Bangkok',
  }).format(d)
}

const formatMoney = (val: number) => `฿${Number(val).toLocaleString()}`

const formatPeriod = (from: string, to: string) =>
  from === to ? formatDate(from) : `${formatDate(from)} – ${formatDate(to)}`

function updateTimestamp() {
  const now = new Date()
  const dateStr = new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(now)
  const timeStr = new Intl.DateTimeFormat('th-TH', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' }).format(
    now,
  )
  lastUpdatedText.value = `${dateStr}, ${timeStr}`
}

const revenueData = computed(() => reportData.value as RevenueReport | null)

const filteredRevenueCategories = computed(() => {
  if (!revenueData.value?.byCategory) return []
  let list = revenueData.value.byCategory

  if (filters.saleType === 'Course Sales') {
    list = list.filter((cat) => cat.type === 'COURSE' || cat.type === 'Course Sales')
  } else if (filters.saleType === 'Single Visit Sales') {
    list = list.filter((cat) => cat.type === 'SINGLE_VISIT' || cat.type === 'Single Visit Sales')
  } else if (filters.saleType === 'Assessment') {
    list = list.filter((cat) => cat.type === 'ASSESSMENT' || cat.type === 'Assessment')
  }

  if (filters.treatment !== 'ทั้งหมด') {
    list = list.filter((cat) => {
      if (filters.treatment === 'Manual Therapy')
        return cat.type === 'COURSE' || cat.type === 'Course Sales'
      if (filters.treatment === 'Exercise Therapy')
        return cat.type === 'SINGLE_VISIT' || cat.type === 'Single Visit'
      return true
    })
  }

  return list
})

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

  return [{ label: 'ยอดรับชำระ', amount: formatMoney(total), share: '100%' }, ...items]
})

const activeKpis = computed(() => {
  if (report.value === 'revenue') return kpiItems.value
  if (report.value === 'payment') return paymentKpis.value
  return []
})

const paymentRows = computed(() => {
  const data = reportData.value as RevenueReport | null
  return filterPaymentReportRows(data?.dailyByCategory ?? [], {
    from: filters.from,
    to: filters.to,
    saleType: filters.saleType,
    treatment: filters.treatment,
  }).map((row) => ({ ...row, branch: getBranchName() }))
})

const paymentKpis = computed(() => {
  const rows = paymentRows.value
  const total = rows.reduce((sum, row) => sum + row.paid, 0)
  const course = rows
    .filter((row) => row.category === 'Course Sales')
    .reduce((sum, row) => sum + row.paid, 0)
  const visit = total - course
  return [
    { label: 'ยอดรับชำระ', amount: formatMoney(total), share: '' },
    { label: 'รับชำระจากคอร์ส', amount: formatMoney(course), share: '' },
    { label: 'รับชำระจากรายครั้ง', amount: formatMoney(visit), share: '' },
  ]
})

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

const filteredCommissionRows = computed<CommissionReportRow[]>(() => {
  const data = (reportData.value as CommissionReportRow[] | null) ?? []

  return data.filter((row) => {
    const qStaff = filters.staff.trim().toLowerCase()
    const matchStaff = !qStaff || (row.staffName && row.staffName.toLowerCase().includes(qStaff))
    const matchBranch =
      !selectedHeaderBranchCode.value ||
      row.branchCode === selectedHeaderBranchCode.value ||
      row.branchName === selectedHeaderBranchCode.value
    const matchType =
      filters.commissionType === 'ทั้งหมด' || filters.commissionType === SALES_COMMISSION_TYPE
    return matchStaff && matchBranch && matchType
  })
})

// สร้างแถวตามชนิดรายงาน เพื่อใช้วันที่และยอดจาก contract ของรายงานนั้น
const tableRows = computed<string[][]>(() => {
  if (report.value === 'revenue') {
    const list = filteredRevenueCategories.value
    const total = list.reduce((sum, cat) => sum + Number(cat.amount), 0)
    return list.map((row) => {
      const share = total > 0 ? Math.round((Number(row.amount) / total) * 100) : 0
      return [
        row.type === 'ASSESSMENT' ? '—' : (CATEGORY_NAMES[row.type] ?? row.type),
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
          !selectedHeaderBranchCode.value || row.branchCode === selectedHeaderBranchCode.value
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

  if (report.value === 'salesTransaction') {
    const data = reportData.value as StaffSalesReport | null
    const keyword = filters.staff.trim().toLowerCase()
    return (data?.rows ?? [])
      .filter((row) => !keyword || row.fullName.toLowerCase().includes(keyword))
      .map((row) => [
        row.fullName,
        `${row.receipts} ใบเสร็จ`,
        formatMoney(row.amount),
        formatPeriod(filters.from, filters.to),
      ])
  }

  if (report.value === 'treatmentTransaction') {
    const data = reportData.value as TreatmentTransactionRow[] | null
    const keyword = filters.staff.trim().toLowerCase()
    return (data ?? [])
      .filter((row) => {
        const matchBranch =
          !selectedHeaderBranchCode.value || row.branchCode === selectedHeaderBranchCode.value
        const searchable =
          `${row.patientName} ${row.treatingStaffName} ${row.courseCode}`.toLowerCase()
        return matchBranch && (!keyword || searchable.includes(keyword))
      })
      .map((row) => [
        formatDate(row.visitDate),
        row.branchName || getBranchName(row.branchCode),
        row.patientName || row.patientHn,
        row.treatingStaffName || 'ไม่ระบุ',
        row.courseCode,
        formatMoney(Number(row.treatmentFee)),
        formatMoney(Number(row.gross)),
      ])
  }

  if (report.value === 'payment') {
    const total = paymentRows.value.reduce((sum, row) => sum + row.paid, 0)
    return paymentRows.value.map((row) => [
      row.category,
      row.treatment,
      row.branch,
      formatDate(row.date),
      formatMoney(row.paid),
      `${total ? Math.round((row.paid / total) * 100) : 0}%`,
    ])
  }

  return []
})

const totalPages = computed(() => Math.max(1, Math.ceil(tableRows.value.length / pageSize)))

const paginatedRows = computed(() => {
  const start = (page.value - 1) * pageSize
  return tableRows.value.slice(start, start + pageSize)
})

watch(
  [report, filters],
  () => {
    page.value = 1
  },
  { deep: true },
)

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

  if (report.value === 'salesTransaction') {
    const rows = reportData.value as StaffSalesReport | null
    const totalReceipts = (rows?.rows ?? []).reduce((sum, row) => sum + row.receipts, 0)
    const totalSales = (rows?.rows ?? []).reduce((sum, row) => sum + row.amount, 0)
    return ['รวมทั้งหมด', `${totalReceipts} ใบเสร็จ`, formatMoney(totalSales), '']
  }

  if (report.value === 'treatmentTransaction') {
    const rows = reportData.value as TreatmentTransactionRow[] | null
    const totalFee = (rows ?? []).reduce((sum, row) => sum + Number(row.treatmentFee), 0)
    const totalGross = (rows ?? []).reduce((sum, row) => sum + Number(row.gross), 0)
    return ['รวมทั้งหมด', '', '', '', '', formatMoney(totalFee), formatMoney(totalGross)]
  }

  if (report.value === 'payment') {
    const total = paymentRows.value.reduce((sum, row) => sum + row.paid, 0)
    return ['รวมทั้งหมด', '', '', '', formatMoney(total), '100%']
  }

  return null
})

async function fetchReport() {
  if (isTransactionReport.value) return
  loading.value = true
  errorMessage.value = ''
  reportData.value = null
  try {
    const branchCode = selectedHeaderBranchCode.value || undefined
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
      case 'salesTransaction':
        reportData.value = await reportApi.staffSales(filters.from, filters.to, branchCode)
        break
      case 'treatmentTransaction':
        reportData.value = await commissionApi.treatmentReport(filters.from, filters.to)
        break
      case 'payment': {
        reportData.value = await reportApi.revenue(filters.from, filters.to, branchCode)
        break
      }
    }
    updateTimestamp()
  } catch (error) {
    reportData.value = null
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายงานไม่สำเร็จ'
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
    branches.value = [
      { code: 'BR', name: 'แบริ่ง' } as Branch,
      { code: 'R9', name: 'พระราม 9' } as Branch,
    ]
    courseOptions.value = [
      { id: 1, name: 'Manual Therapy' },
      { id: 2, name: 'Exercise Therapy' },
    ]
  }
  await fetchReport()
})

watch([report, selectedHeaderBranchCode, () => filters.from, () => filters.to], fetchReport)
</script>

<template>
  <div class="reports-page-wrapper">
    <StaffHeader />

    <TransactionReportView v-if="isTransactionReport" />
    <main v-else class="reports-page-container">
      <!-- ส่วนหัวรายงาน -->
      <section class="page-title-section">
        <h1>{{ ['payment', 'revenue'].includes(report) ? 'รายงานยอดรับชำระ' : 'รายงาน' }}</h1>
        <p>
          {{
            report === 'payment'
              ? 'ตรวจสอบเงินที่รับจริงตามวันที่รับชำระและประเภทบริการ'
              : ['revenue', 'payment'].includes(report)
                ? 'ยอดรับชำระและประเภทบริการตามวันที่รับเงินจริง'
                : 'ตรวจสอบรายงานคอร์ส ยอดขาย และค่าคอมมิชชั่น'
          }}
        </p>
      </section>

      <!-- สรุป KPI ของรายงานรายรับ -->
      <section
        v-if="['revenue', 'payment'].includes(report) && activeKpis.length"
        class="kpi-summary-box"
        :class="{ 'kpi-summary-box--payment': report === 'payment' }"
      >
        <div v-for="(kpi, idx) in activeKpis" :key="idx" class="kpi-col">
          <span class="kpi-label">{{ kpi.label }}</span>
          <div class="kpi-value-row">
            <b class="kpi-amount">{{ kpi.amount }}</b>
            <span class="kpi-share">{{ kpi.share }}</span>
          </div>
        </div>
      </section>

      <!-- ส่วนตัวกรองรายงาน -->
      <section class="filter-card">
        <!-- ชื่อรายงานและตัวเลือกประเภท -->
        <div class="filter-card-header">
          <h2>{{ currentReportTitle }}</h2>
          <div class="report-select-group">
            <label for="report-select">เลือกรายงาน</label>
            <FilterDropdown
              id="report-select"
              v-model="reportSelection"
              :options="reportOptions"
              default-value="revenue"
              default-label="รายรับตามวันที่รับชำระ"
              label="เลือกรายงาน"
              hide-default-option
              :clear-on-reselect="false"
            />
          </div>
        </div>

        <!-- กลุ่มตัวกรอง -->
        <div class="filter-card-body">
          <div
            v-if="['salesTransaction', 'treatmentTransaction'].includes(report)"
            class="field-group"
          >
            <label>ประเภทธุรกรรม</label>
            <FilterDropdown
              v-model="report"
              :options="[
                { value: 'salesTransaction', label: 'ยอดขายร้าน' },
                { value: 'treatmentTransaction', label: 'ค่ามือ' },
              ]"
              default-value="salesTransaction"
              default-label="ยอดขายร้าน"
              label="ประเภทธุรกรรม"
              hide-default-option
              :clear-on-reselect="false"
            />
          </div>
          <!-- ตัวกรองหลัก -->
          <div class="filter-grid-row1">
            <!-- ตัวกรองยอดคงเหลือและการโอน -->
            <template v-if="['balance', 'transfer'].includes(report)">
              <div class="field-group">
                <label>ผู้ป่วย</label>
                <input v-model="filters.patient" type="text" placeholder="ค้นหาชื่อผู้ป่วย" />
              </div>

              <div class="field-group">
                <label>คอร์ส</label>
                <FilterDropdown
                  v-model="filters.course"
                  :options="courseFilterOptions"
                  default-value="ทั้งหมด"
                  default-label="ทั้งหมด"
                  label="กรองตามคอร์ส"
                  :clear-on-reselect="false"
                />
              </div>
            </template>

            <!-- ตัวกรองการรับชำระ -->
            <template v-else-if="report === 'payment'">
              <div class="field-group">
                <label>การรักษา</label>
                <FilterDropdown
                  v-model="filters.treatment"
                  :options="[
                    { value: 'Manual Therapy', label: 'Manual Therapy' },
                    { value: 'Exercise Therapy', label: 'Exercise Therapy' },
                  ]"
                  default-value="ทั้งหมด"
                  default-label="ทั้งหมด"
                  label="กรองตามการรักษา"
                  :clear-on-reselect="false"
                />
              </div>
              <div class="field-group">
                <label>ประเภทการขาย</label>
                <FilterDropdown
                  v-model="filters.saleType"
                  :options="[
                    { value: 'Course Sales', label: 'Course Sales' },
                    { value: 'Single Visit Sales', label: 'Single Visit Sales' },
                  ]"
                  default-value="ทั้งหมด"
                  default-label="ทั้งหมด"
                  label="กรองตามประเภทการขาย"
                  :clear-on-reselect="false"
                />
              </div>
            </template>

            <!-- ตัวกรองรายรับ -->
            <template v-else-if="report === 'revenue'">
              <div class="field-group">
                <label>การรักษา</label>
                <FilterDropdown
                  v-model="filters.treatment"
                  :options="[
                    { value: 'Manual Therapy', label: 'Manual Therapy' },
                    { value: 'Exercise Therapy', label: 'Exercise Therapy' },
                  ]"
                  default-value="ทั้งหมด"
                  default-label="ทั้งหมด"
                  label="กรองตามการรักษา"
                  :clear-on-reselect="false"
                />
              </div>

              <div class="field-group">
                <label>ประเภทการขาย</label>
                <FilterDropdown
                  v-model="filters.saleType"
                  :options="[
                    { value: 'Assessment', label: 'Assessment' },
                    { value: 'Course Sales', label: 'Course Sales' },
                    { value: 'Single Visit Sales', label: 'Single Visit Sales' },
                  ]"
                  default-value="ทั้งหมด"
                  default-label="ทั้งหมด"
                  label="กรองตามประเภทการขาย"
                  :clear-on-reselect="false"
                />
              </div>

              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker v-model="filters.from" numeric-month show-calendar-icon />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker v-model="filters.to" numeric-month show-calendar-icon />
              </div>
            </template>

            <!-- ตัวกรองคอมมิชชันและธุรกรรม -->
            <template
              v-else-if="
                ['commission', 'salesTransaction', 'treatmentTransaction'].includes(report)
              "
            >
              <div class="field-group">
                <label>{{ report === 'treatmentTransaction' ? 'ค้นหารายการ' : 'พนักงาน' }}</label>
                <input
                  v-model="filters.staff"
                  type="text"
                  :placeholder="
                    report === 'treatmentTransaction'
                      ? 'ค้นหาผู้ป่วย นักกายภาพ หรือคอร์ส'
                      : 'ค้นหาชื่อพนักงาน'
                  "
                />
              </div>

              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker v-model="filters.from" numeric-month show-calendar-icon />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker v-model="filters.to" numeric-month show-calendar-icon />
              </div>
            </template>
          </div>

          <!-- ตัวกรองเพิ่มเติม -->
          <div class="filter-grid-row2">
            <!-- ประเภทคอมมิชชัน -->
            <div v-if="report === 'commission'" class="field-group field-group--short">
              <label>ประเภทค่าคอมมิชชั่น</label>
              <FilterDropdown
                v-model="filters.commissionType"
                :options="[
                  { value: 'ค่ามือรักษา', label: 'ค่ามือรักษา' },
                  { value: 'ค่าคอมมิชชันการขาย', label: 'ค่าคอมมิชชันการขาย' },
                ]"
                default-value="ทั้งหมด"
                default-label="ทั้งหมด"
                label="กรองตามประเภทค่าคอมมิชชัน"
                :clear-on-reselect="false"
              />
            </div>

            <!-- ช่วงวันที่รับชำระ -->
            <template v-if="report === 'payment'">
              <div class="field-group">
                <label>ตั้งแต่วันที่</label>
                <ThaiDatePicker v-model="filters.from" numeric-month show-calendar-icon />
              </div>

              <div class="field-group">
                <label>ถึงวันที่</label>
                <ThaiDatePicker v-model="filters.to" numeric-month show-calendar-icon />
              </div>
            </template>
          </div>
        </div>
      </section>

      <!-- ตารางรายงาน -->
      <section class="table-card">
        <p v-if="errorMessage" class="report-error" role="alert">{{ errorMessage }}</p>
        <p v-else-if="loading" class="report-loading">กำลังโหลดรายงาน...</p>
        <div class="table-scroll-wrapper">
          <table :class="['report-figma-table', `report-figma-table--${report}`]">
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

              <!-- แถวสรุปยอดรวม -->
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

        <!-- แบ่งหน้าและเวลาอัปเดต -->
        <div class="table-card-footer">
          <nav v-if="tableRows.length > 0" class="report-pagination" aria-label="เปลี่ยนหน้ารายการ">
            <button type="button" class="btn-prev" :disabled="page === 1" @click="page -= 1">
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
