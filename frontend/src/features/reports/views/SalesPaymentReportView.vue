<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { branchApi, reportTransactionsApi, type Branch, type ReportTransactions } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'
import { buildReportCsv } from '../lib/reportCsv'

const props = defineProps<{ mode: 'sales' | 'payment' }>()
const router = useRouter()
const filters = reactive({ from: `${clinicToday().slice(0, 7)}-01`, to: clinicToday(), patient: '',
  branchCode: selectedManagerBranch()?.code ?? '', saleType: '', treatment: '' })
const applied = ref({ ...filters })
const branches = ref<Branch[]>([])
const data = ref<ReportTransactions | null>(null)
const loading = ref(false)
const error = ref('')
const page = ref(1)
let requestId = 0
const isManager = JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
const title = computed(() => props.mode === 'sales' ? 'รายงานยอดขาย' : 'รายงานยอดรับชำระ')
const dateLabel = computed(() => props.mode === 'sales' ? 'วันที่ขาย' : 'วันที่รับเงินจริง')
const pageCount = computed(() => Math.max(1, Math.ceil((data.value?.rows.length ?? 0) / 10)))
const shown = computed(() => data.value?.rows.slice((page.value - 1) * 10, page.value * 10) ?? [])
const money = (amount: number) => Number(amount).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const typeName = (type: string) => ({ CoursePurchase: 'คอร์ส', SingleVisit: 'บริการรายครั้ง', Assessment: 'ประเมินอาการ' })[type] ?? type
const headers = computed(() => ['ใบเสร็จ', 'ผู้ป่วย', 'HN', 'สาขา', dateLabel.value, 'ประเภท', 'การรักษา', 'ช่องทาง', 'ยอดเงิน'])
const csvRows = computed(() => (data.value?.rows ?? []).map(row => [row.receiptNo, row.patientName,
  row.patientHn, row.branchName, row.date, typeName(row.type), row.treatment, row.paymentMethod || '—', money(row.amount)]))
async function load() {
  const current = ++requestId
  loading.value = true; error.value = ''; data.value = null; page.value = 1
  const selection = { ...filters }
  try {
    if (selection.to < selection.from) throw new Error('วันสิ้นสุดต้องไม่ก่อนวันเริ่มต้น')
    const result = await reportTransactionsApi.read(props.mode, selection)
    if (current === requestId) { data.value = result; applied.value = selection }
  } catch (cause) {
    if (current === requestId) error.value = cause instanceof Error ? cause.message : 'โหลดรายงานไม่สำเร็จ'
  } finally { if (current === requestId) loading.value = false }
}
function exportCsv() {
  if (!isManager || loading.value || !data.value?.rows.length) return
  try {
    const csv = buildReportCsv({ title: title.value, filters: {
      ผู้ป่วย: applied.value.patient, สาขา: applied.value.branchCode,
      ตั้งแต่วันที่: applied.value.from, ถึงวันที่: applied.value.to,
      ประเภทการขาย: applied.value.saleType, การรักษา: applied.value.treatment,
    }, exportedAt: new Date().toISOString(), headers: headers.value, rows: csvRows.value,
      total: ['รวมทั้งหมด', '', '', '', '', '', '', '', money(data.value.total)] })
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    try {
      const link = document.createElement('a')
      link.href = url; link.download = `sabyeguyz-${props.mode}-${clinicToday()}.csv`
      document.body.appendChild(link)
      try { link.click() } finally { link.remove() }
    } finally { URL.revokeObjectURL(url) }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'สร้างหรือดาวน์โหลดไฟล์ไม่สำเร็จ' }
}
watch(() => props.mode, load)
onMounted(async () => {
  try { branches.value = await branchApi.list() } catch { error.value = 'โหลดรายชื่อสาขาไม่สำเร็จ' }
  await load()
})
</script>
<template>
  <main class="detail-report">
    <h1>{{ title }}</h1>
    <p>{{ dateLabel }} · ยอดรับและยอดขายแยกจากกัน · ตรวจต้นทางได้จากเลขใบเสร็จ</p>
    <label>เลือกรายงาน
      <select :value="mode" @change="router.push({ path: '/staff/reports', query: { report: ($event.target as HTMLSelectElement).value } })">
        <option value="sales">รายงานยอดขาย</option><option value="payment">รายงานยอดรับชำระ</option>
        <option value="balance">ยอดคงเหลือคอร์ส</option><option value="transfer">ประวัติการโอนคอร์ส</option>
        <option value="commission">ค่าคอมมิชชัน</option><option value="salesTransaction">Transaction</option>
      </select>
    </label>
    <form aria-label="ตัวกรองรายงาน" @submit.prevent="load">
      <label>ผู้ป่วย<input v-model="filters.patient" placeholder="ค้นหาชื่อผู้ป่วยหรือ HN" /></label>
      <label>สาขา<select v-model="filters.branchCode"><option value="">ทุกสาขา</option><option v-for="branch in branches" :key="branch.code" :value="branch.code">{{ branch.name }}</option></select></label>
      <label>ตั้งแต่วันที่<input v-model="filters.from" type="date" required /></label>
      <label>ถึงวันที่<input v-model="filters.to" type="date" required /></label>
      <label>ประเภทการขาย<select v-model="filters.saleType"><option value="">ทั้งหมด</option><option value="CoursePurchase">คอร์ส</option><option value="SingleVisit">บริการรายครั้ง</option><option value="Assessment">ประเมินอาการ</option></select></label>
      <label>การรักษา<input v-model="filters.treatment" placeholder="ค้นหาบริการหรือคอร์ส" /></label>
      <button type="submit" :disabled="loading">แสดงรายงาน</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="loading" role="status">กำลังโหลดรายงาน…</p>
    <section v-if="data" aria-label="ผลรายงาน">
      <p>ทั้งหมด {{ data.rows.length }} รายการ · ยอดรวม <strong data-testid="report-total">{{ money(data.total) }}</strong> บาท</p>
      <button v-if="isManager" type="button" :disabled="loading || data.rows.length === 0" @click="exportCsv">ส่งออกรายงาน CSV</button>
      <p v-if="!data.rows.length">ไม่พบข้อมูลตามเงื่อนไขที่เลือก</p>
      <div class="report-scroll"><table>
        <thead><tr><th v-for="header in headers" :key="header">{{ header }}</th></tr></thead>
        <tbody><tr v-for="row in shown" :key="`${row.receiptId}-${row.source}-${row.sourceId}-${row.date}`">
          <td><RouterLink :to="{ name: 'staff-course-payment-receipt', params: { id: row.receiptId } }">{{ row.receiptNo }}</RouterLink></td>
          <td>{{ row.patientName }}</td><td>{{ row.patientHn }}</td><td>{{ row.branchName }}</td><td>{{ row.date }}</td>
          <td>{{ typeName(row.type) }}{{ row.source === 'COURSE_COLLECTION' ? ' (รับเงินคอร์สเดิม)' : '' }}</td>
          <td>{{ row.treatment }}</td><td>{{ row.paymentMethod || '—' }}</td><td>{{ money(row.amount) }}</td>
        </tr></tbody>
      </table></div>
      <nav v-if="data.rows.length" aria-label="เปลี่ยนหน้ารายงาน">
        <button :disabled="page === 1" @click="page--">ก่อนหน้า</button><span>หน้า {{ page }} จาก {{ pageCount }}</span><button :disabled="page === pageCount" @click="page++">ถัดไป</button>
      </nav>
      <section v-if="mode === 'payment'" aria-label="ช่องทางและเงินคืน">
        <h2>เงินรับแยกช่องทาง</h2><p v-for="(amount, method) in data.byMethod" :key="method">{{ method }}: {{ money(amount) }}</p>
        <h2>เงินคืนสำเร็จ (แยกจากเงินรับ)</h2><p>รวม {{ money(data.refundedTotal) }} บาท</p>
        <ul><li v-for="refund in data.refunds" :key="refund.refundId">{{ refund.date }} · {{ refund.receiptNo }} · {{ refund.paymentMethod }} · {{ money(refund.amount) }}</li></ul>
      </section>
    </section>
  </main>
</template>
<style scoped>
.detail-report { max-width: 1400px; margin: auto; padding: 24px; }
form { display: flex; flex-wrap: wrap; gap: 16px; margin: 20px 0; }
label { display: grid; gap: 6px; } input, select, button { min-height: 40px; padding: 8px 12px; }
.report-scroll { overflow-x: auto; margin-top: 16px; } table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; border-bottom: 1px solid #d7dce2; padding: 12px; } nav { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
[role='alert'] { color: #a21d28; }
</style>
