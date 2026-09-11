<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const report = ref('revenue')
const filters = reactive({
  patient: '',
  branch: 'ทุกสาขา',
  treatment: 'ทั้งหมด',
  commissionRule: 'ทั้งหมด',
  saleType: 'ทั้งหมด',
  from: '2026-08-01',
  to: '2026-08-31',
})
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
const reportRows: Record<string, string[][]> = {
  revenue: [
    ['Course Sales', 'Manual Therapy', 'ราชพฤกษ์', '฿185,000', '62%'],
    ['Single Visit Sales', 'Exercise Therapy', 'สุขุมวิท', '฿82,500', '28%'],
    ['Assessment', 'Initial Assessment', 'ราชพฤกษ์', '฿29,400', '10%'],
  ],
  balance: [
    ['สมชาย ใจดี', 'Manual Therapy 10 ครั้ง', 'ราชพฤกษ์', '10', '4', '2', '4', '0'],
    ['วรรณา มั่นคง', 'Exercise Therapy 6 ครั้ง', 'สุขุมวิท', '6', '2', '0', '4', '0'],
  ],
  transfer: [
    [
      'TRF-260821-0004',
      'สมชาย ใจดี',
      'วรรณา มั่นคง',
      'ราชพฤกษ์',
      'Manual Therapy',
      '2 ครั้ง',
      '21 ส.ค. 2026',
      '2026-08-21',
    ],
  ],
  sales: [
    ['พิมพ์นารา ว.', 'ราชพฤกษ์', '12 รายการ', '฿96,500'],
    ['อรทัย ก.', 'สุขุมวิท', '8 รายการ', '฿72,000'],
  ],
  commission: [
    ['กฤษดา น.', 'การให้บริการ', 'ราชพฤกษ์', '18 รายการ', '฿5,400'],
    ['พิมพ์นารา ว.', 'การขายคอร์ส', 'สุขุมวิท', '12 รายการ', '฿4,825'],
  ],
}
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
const activeRows = computed(() => {
  const rows = reportRows[report.value] ?? []
  const patient = filters.patient.trim().toLowerCase()
  const filteredRows = rows.filter((row) => {
    if (patient && !row.join(' ').toLowerCase().includes(patient)) return false
    if (filters.branch !== 'ทุกสาขา' && !row.includes(filters.branch)) return false
    if (report.value === 'transfer') {
      const transactionDate = row.at(-1) ?? ''
      if (transactionDate < filters.from || transactionDate > filters.to) return false
    }
    if (report.value === 'revenue' && filters.saleType !== 'ทั้งหมด') {
      if (!row[0]?.includes(filters.saleType)) return false
    }
    if (report.value === 'revenue' && filters.treatment !== 'ทั้งหมด') {
      if (!row[1]?.includes(filters.treatment)) return false
    }
    if (report.value === 'commission' && filters.commissionRule !== 'ทั้งหมด') {
      if (!row.join(' ').includes(filters.commissionRule)) return false
    }
    return true
  })
  if (report.value === 'revenue') {
    const period = `${formatReportDate(filters.from)} – ${formatReportDate(filters.to)}`
    return filteredRows.map((row) => [row[0]!, row[1]!, row[2]!, period, row[3]!, row[4]!])
  }
  if (report.value === 'balance') {
    const asOf = formatReportDate(filters.to)
    return filteredRows.map((row) => [row[0]!, row[1]!, row[2]!, asOf, ...row.slice(3)])
  }
  if (report.value === 'sales') {
    const period = `${formatReportDate(filters.from)} – ${formatReportDate(filters.to)}`
    return filteredRows.map((row) => [row[0]!, row[1]!, period, ...row.slice(2)])
  }
  if (report.value === 'commission') {
    const period = `${formatReportDate(filters.from)} – ${formatReportDate(filters.to)}`
    return filteredRows.map((row) => [row[0]!, row[1]!, row[2]!, period, ...row.slice(3)])
  }
  if (report.value === 'transfer') return filteredRows.map((row) => row.slice(0, -1))
  return filteredRows
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
                <option>ราชพฤกษ์</option>
                <option>สุขุมวิท</option>
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
        <header>
          <div>
            <small>REPORT</small>
            <h2>{{ title }}</h2>
            <p class="report-period">{{ reportPeriodLabel }} {{ filters.branch }}</p>
          </div>
          <button class="secondary">ส่งออก CSV</button>
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
