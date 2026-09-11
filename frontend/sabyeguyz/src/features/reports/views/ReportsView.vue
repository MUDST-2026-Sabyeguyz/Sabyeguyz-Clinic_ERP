<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { branchApi, commissionApi, courseApi, reportApi, type Branch, type CourseBalanceReport, type RevenueReport, type StaffSalesReport, type TransferRecord } from '@/api/client'
const report = ref('revenue')
const filters = reactive({ patient: '', branch: 'ทุกสาขา', treatment: 'ทั้งหมด', commissionRule: 'ทั้งหมด', saleType: 'ทั้งหมด', from: `${new Date().getFullYear()}-01-01`, to: new Date().toISOString().slice(0, 10) })
const branches = ref<Branch[]>([])
type CommissionReportRow = { staffId: number | null; staffName: string; branchCode: string; branchName: string; saleCount: number; base: number; rate: number; commission: number }
type ReportData = RevenueReport | CourseBalanceReport | StaffSalesReport | CommissionReportRow[] | TransferRecord[] | null
const reportData = ref<ReportData>(null)
const loading = ref(false)
const errorMessage = ref('')
const reports: Array<[string, string]> = [
  ['revenue', 'รายงานรายได้'],
  ['balance', 'ยอดคงเหลือคอร์ส'],
  ['transfer', 'ประวัติการโอนคอร์ส'],
  ['sales', 'ยอดขายบุคลากร'],
  ['commission', 'รายงานค่าคอมมิชชัน'],
]
const reportDescriptions: Record<string, string> = {
  revenue: 'ตรวจสอบรายได้ตามบริการและช่วงเวลา',
  balance: 'ตรวจสอบยอดคงเหลือและการใช้คอร์ส',
  transfer: 'ตรวจสอบประวัติการโอนคอร์ส',
  sales: 'เปรียบเทียบยอดขายของบุคลากร',
  commission: 'ตรวจสอบค่าคอมมิชชันและเกณฑ์ที่ใช้',
}
const title = computed(() => reports.find(([key]) => key === report.value)?.[1])
const reportDescription = computed(() => {
  return reportDescriptions[report.value]
})
const formatReportDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(value),
  )
const reportPeriodLabel = computed(() =>
  report.value === 'balance'
    ? `ข้อมูล ณ ${formatReportDate(filters.to)}`
    : `ช่วงวันที่ ${formatReportDate(filters.from)} – ${formatReportDate(filters.to)}`,
)
const headers: Record<string, string[]> = {
  revenue: ['ประเภทรายได้', 'การรักษา', 'สาขา', 'ช่วงวันที่', 'รายได้รวม', 'สัดส่วน'],
  balance: [
    'ผู้ป่วย',
    'คอร์ส',
    'สาขา',
    'ข้อมูล ณ วันที่',
    'ซื้อ',
    'ใช้',
    'โอน',
    'คงเหลือ',
    'หมดอายุ',
  ],
  transfer: ['เลขที่รายการ', 'ผู้โอน', 'ผู้รับ', 'สาขา', 'คอร์ส', 'จำนวน', 'วันที่'],
  sales: ['พนักงาน', 'สาขา', 'ช่วงวันที่', 'จำนวนรายการ', 'ยอดขาย'],
  commission: ['พนักงาน', 'ประเภท', 'สาขา', 'ช่วงวันที่', 'จำนวนรายการ', 'ค่าคอมมิชชัน'],
}
async function loadReport() {
  loading.value = true; errorMessage.value = ''; reportData.value = null
  try {
    const branchCode = filters.branch === 'ทุกสาขา' ? undefined : filters.branch
    if (report.value === 'revenue') reportData.value = await reportApi.revenue(filters.from, filters.to, branchCode)
    else if (report.value === 'balance') reportData.value = await reportApi.balances(filters.from, filters.to, branchCode)
    else if (report.value === 'sales') reportData.value = await reportApi.staffSales(filters.from, filters.to, branchCode)
    else if (report.value === 'transfer') reportData.value = await courseApi.transferHistory()
    else reportData.value = await commissionApi.report(filters.from, filters.to)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดรายงานไม่สำเร็จ' }
  finally { loading.value = false }
}
onMounted(async () => { try { branches.value = await branchApi.list() } catch { /* report request shows the API error */ } await loadReport() })
watch([report, () => filters.branch, () => filters.from, () => filters.to], loadReport)
const activeRows = computed(() => {
  const period = `${formatReportDate(filters.from)} – ${formatReportDate(filters.to)}`
  if (report.value === 'revenue') return ((reportData.value as RevenueReport | null)?.byCategory ?? []).map((row) => [row.type, row.type, filters.branch, period, `฿${Number(row.amount).toLocaleString()}`, `${Number(row.share) * 100}%`])
  if (report.value === 'balance') return ((reportData.value as CourseBalanceReport | null)?.rows ?? []).filter((row) => !filters.patient || `${row.hn} ${row.patientFullName}`.toLowerCase().includes(filters.patient.toLowerCase())).map((row) => [row.patientFullName, row.courseName, row.branchCode, formatReportDate(filters.to), String(row.purchased), String(row.used), '—', String(row.remaining), row.expired ? 'ใช่' : 'ไม่'])
  if (report.value === 'sales') return [...((reportData.value as StaffSalesReport | null)?.rows ?? []), (reportData.value as StaffSalesReport | null)?.unassigned].filter((row): row is NonNullable<typeof row> => Boolean(row)).map((row) => [row.fullName || 'ไม่ระบุ', filters.branch, period, `${row.receipts} รายการ`, `฿${Number(row.amount).toLocaleString()}`])
  if (report.value === 'transfer') return ((reportData.value as TransferRecord[] | null) ?? []).filter((row) => {
    const matchesPatient = !filters.patient || `${row.sourcePatientId} ${row.sourcePatientHn} ${row.sourcePatientName} ${row.destinationPatientId} ${row.destinationPatientHn} ${row.destinationPatientName}`.toLowerCase().includes(filters.patient.trim().toLowerCase())
    const matchesBranch = filters.branch === 'ทุกสาขา' || row.branchCode === filters.branch
    const date = row.createdAt.slice(0, 10)
    return matchesPatient && matchesBranch && date >= filters.from && date <= filters.to
  }).map((row) => [String(row.id), `${row.sourcePatientName} (${row.sourcePatientHn})`, `${row.destinationPatientName} (${row.destinationPatientHn})`, row.branchCode, String(row.courseId), `${row.quantity} ครั้ง`, formatReportDate(row.createdAt)])
  if (report.value === 'commission') return ((reportData.value as CommissionReportRow[] | null) ?? []).map((row) => [row.staffName || 'ไม่ระบุ', 'ค่าคอมมิชชัน', row.branchName, period, `${row.saleCount} รายการ`, `฿${Number(row.commission).toLocaleString()}`])
  return []
})
function resetFilters() {
  Object.assign(filters, {
    patient: '',
    branch: 'ทุกสาขา',
    treatment: 'ทั้งหมด',
    commissionRule: 'ทั้งหมด',
    saleType: 'ทั้งหมด',
    from: '2026-08-01',
    to: '2026-08-31',
  })
}
function exportCsv() {
  const rows = [headers[report.value] ?? [], ...activeRows.value]
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `report-${report.value}-${filters.from}-${filters.to}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
</script>
<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>MANAGER REPORTS</small>
        <h1>รายงาน</h1>
        <p>ตรวจสอบรายได้ คอร์ส ยอดขาย และค่าคอมมิชชันแบบลงรายละเอียด</p>
      </section>
      <section class="report-toolbar">
        <header class="report-picker">
          <div>
            <small>ประเภทรายงาน</small>
            <h2>{{ title }}</h2>
            <p>{{ reportDescription }}</p>
          </div>
          <label
            >เลือกรายงาน<select v-model="report">
              <option v-for="item in reports" :key="item[0]" :value="item[0]">{{ item[1] }}</option>
            </select></label
          >
        </header>
        <div class="filter-panel">
          <div class="filter-title">
            <div><b>ตัวกรองข้อมูล</b><small>แสดงเฉพาะตัวกรองที่ใช้กับรายงานนี้</small></div>
            <button @click="resetFilters">ล้างตัวกรอง</button>
          </div>
          <div class="report-filters">
            <label v-if="['revenue', 'balance', 'transfer'].includes(report)"
              >ผู้ป่วย<input v-model="filters.patient" placeholder="ชื่อหรือ HN"
            /></label>
            <label
              >สาขา<select v-model="filters.branch">
                <option>ทุกสาขา</option>
                <option v-for="item in branches" :key="item.code" :value="item.code">{{ item.name }} ({{ item.code }})</option>
              </select></label
            >
            <label v-if="report === 'revenue'"
              >การรักษา<select v-model="filters.treatment">
                <option>ทั้งหมด</option>
                <option>Assessment</option>
                <option>Manual Therapy</option>
                <option>Exercise Therapy</option>
              </select></label
            >
            <label v-if="report === 'revenue'"
              >ประเภทการขาย<select v-model="filters.saleType">
                <option>ทั้งหมด</option>
                <option>Course Sales</option>
                <option>Single Visit Sales</option>
              </select></label
            >
            <label v-if="report === 'commission'"
              >เกณฑ์คอมมิชชัน<select v-model="filters.commissionRule">
                <option>ทั้งหมด</option>
                <option>การให้บริการ</option>
                <option>การขายคอร์ส</option>
              </select></label
            >
            <label>ตั้งแต่<input v-model="filters.from" type="date" /></label>
            <label>ถึง<input v-model="filters.to" type="date" /></label>
          </div>
        </div>
      </section>
      <section class="staff-panel report-table">
        <p v-if="loading" class="status-feedback">กำลังโหลดรายงาน...</p>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <header>
          <div>
            <small>REPORT</small>
            <h2>{{ title }}</h2>
            <p class="report-period">{{ reportPeriodLabel }} {{ filters.branch }}</p>
          </div>
          <button class="secondary" :disabled="loading || !activeRows.length" @click="exportCsv">ส่งออก CSV</button>
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th v-for="head in headers[report]" :key="head">{{ head }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in activeRows" :key="index">
              <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                <b v-if="cellIndex === 0">{{ cell }}</b
                ><template v-else>{{ cell }}</template>
              </td>
            </tr>
            <tr v-if="!activeRows.length">
              <td :colspan="headers[report]?.length">ไม่พบข้อมูลตามตัวกรอง</td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/reports.css"></style>
