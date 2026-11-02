<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi, type StaffPayment } from '@/api/client'
import { canClosePaymentBatch, isPaymentDateAllowed } from '../lib/paymentBatch'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'

type ClosingRow = {
  id: number
  staff: string
  sales: number
  rate: number
  status: 'Open' | 'Closed'
}

const selectedMonth = ref(`${clinicToday().slice(0, 7)}-01`)
const month = computed(() => selectedMonth.value.slice(0, 7))
const selectedBranchCode = computed(() => selectedManagerBranch()?.code ?? '')
const router = useRouter()
const branch = ref('ทุกสาขา')
const search = ref('')
const rows = ref<ClosingRow[]>([])
const errorMessage = ref('')
const isConfirming = ref(false)
const isSaving = ref(false)
const closedSuccessfully = ref(false)
const branchClosing = ref<{ status: 'OPEN' | 'CLOSED' } | null>(null)
const reopenReason = ref('')
const paymentRows = ref<StaffPayment[]>([])
const paymentError = ref('')
const paymentBusy = ref(false)
const paymentForms = ref<Record<number, { paidDate: string; reference: string }>>({})
const validationLoading = ref(false)
const closingsLoading = ref(false)
const validationError = ref('')
const tierIssue = ref('')
const dialogRef = ref<HTMLElement | null>(null)
const cancelRef = ref<HTMLButtonElement | null>(null)
let triggerElement: HTMLElement | null = null
let requestId = 0
const reportRows = ref<Awaited<ReturnType<typeof commissionApi.report>>>([])
const schemeLabel = ref('ยังไม่มีข้อมูลกฎค่าคอมมิชชัน')
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
  if (validationLoading.value || hasBlockingIssues.value) return 'รอตรวจสอบข้อมูล'
  const rates = [...new Set(reportRows.value.map((r) => Number(r.rate) * 100))]
  return rates.length === 1 ? `${rates[0]}%` : rates.length ? 'ตามช่วงยอดขายรายบุคคล' : '—'
})
const validationResults = ref({
  tiers: null as boolean | null,
  sales: null as boolean | null,
  courses: null as boolean | null,
  treatment: null as boolean | null,
})

async function validatePeriod(id: number, selectedPeriod: string) {
  validationLoading.value = true
  validationError.value = ''
  tierIssue.value = ''
  reportRows.value = []
  schemeLabel.value = 'ยังไม่มีข้อมูลกฎค่าคอมมิชชัน'
  schemePeriodLabel.value = 'ยังไม่มีข้อมูลช่วงวันที่'
  validationResults.value = { tiers: null, sales: null, courses: null, treatment: null }
  const [year, number] = selectedPeriod.split('-').map(Number)
  const from = `${selectedPeriod}-01`
  const to = `${selectedPeriod}-${String(new Date(year!, number!, 0).getDate()).padStart(2, '0')}`
  const branchCode = selectedBranchCode.value
  if (!branchCode) {
    validationError.value = 'กรุณาเลือกสาขาก่อนตรวจรอบ'
    validationResults.value = { tiers: null, sales: null, courses: null, treatment: null }
    validationLoading.value = false
    return
  }

  const [schemesResult, salesResult, treatmentResult] = await Promise.allSettled([
    commissionApi.schemes(),
    commissionApi.report(from, to),
    commissionApi.treatmentReport(from, to),
  ])
  if (id !== requestId) return

  if (
    schemesResult.status === 'rejected' ||
    salesResult.status === 'rejected' ||
    treatmentResult.status === 'rejected'
  ) {
    validationError.value = 'โหลดข้อมูลตรวจสอบไม่ครบ กรุณาตรวจอีกครั้งก่อนปิดรอบ'
  }

  const scopedSales =
    salesResult.status === 'fulfilled'
      ? salesResult.value.filter((item) => item.branchCode === branchCode)
      : []
  if (salesResult.status === 'fulfilled') reportRows.value = scopedSales
  validationResults.value.sales =
    salesResult.status === 'fulfilled'
      ? scopedSales.every((item) => item.staffId !== null && Number(item.base) >= 0)
      : null

  if (treatmentResult.status === 'fulfilled') {
    const scopedTreatments = treatmentResult.value.filter((item) => item.branchCode === branchCode)
    validationResults.value.courses = scopedTreatments.every(
      (item) => Boolean(item.courseCode) && item.ownerStaffId !== null,
    )
    validationResults.value.treatment = scopedTreatments.every(
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
        if (id !== requestId) return
        if (!tiers.length) tierIssue.value = 'ยังไม่มีช่วงยอดขายในกฎนี้'
        else if (Number(tiers[0]!.minimum) !== 0)
          tierIssue.value = `ช่วงแรกต้องเริ่มที่ ฿0 (ขณะนี้เริ่มที่ ${currency(Number(tiers[0]!.minimum))})`
        else {
          for (let index = 0; index < tiers.length; index++) {
            const tier = tiers[index]!
            const minimum = Number(tier.minimum)
            const maximum = tier.maximum === null ? null : Number(tier.maximum)
            if (maximum !== null && maximum < minimum) {
              tierIssue.value = `ช่วงที่ ${index + 1}: ยอดสูงสุดต่ำกว่ายอดขั้นต่ำ`
              break
            }
            if (index < tiers.length - 1) {
              const nextMinimum = Number(tiers[index + 1]!.minimum)
              if (maximum === null) {
                tierIssue.value = `ช่วงที่ ${index + 1} ไม่จำกัดยอดสูงสุด แต่ยังมีช่วงถัดไป`
                break
              }
              if (Math.abs(nextMinimum - maximum - 0.01) >= 0.001) {
                tierIssue.value =
                  nextMinimum > maximum
                    ? `มีช่วงว่างระหว่าง ${currency(maximum)} ถึง ${currency(nextMinimum)}`
                    : `มีช่วงซ้อนระหว่าง ${currency(nextMinimum)} ถึง ${currency(maximum)}`
                break
              }
            }
          }
        }
        validationResults.value.tiers = !tierIssue.value
      } catch {
        if (id !== requestId) return
        validationResults.value.tiers = null
        tierIssue.value = 'โหลดช่วงยอดขายไม่สำเร็จ'
      }
    } else {
      const noCourseSales = reportRows.value.length === 0
      validationResults.value.tiers = noCourseSales
      tierIssue.value = noCourseSales ? '' : 'ไม่พบกฎค่าคอมมิชชันที่ใช้งานในเดือนนี้'
    }
  } else validationResults.value.tiers = null
  validationLoading.value = false
}

async function load() {
  const id = ++requestId
  const selectedPeriod = month.value
  closedSuccessfully.value = false
  branchClosing.value = null
  rows.value = []
  paymentRows.value = []
  paymentForms.value = {}
  errorMessage.value = ''
  closingsLoading.value = true
  void validatePeriod(id, selectedPeriod)
  try {
    // โหลดสถานะปิดรอบระดับสาขาแยกจากรายการรายบุคคล
    const branchCode = selectedBranchCode.value
    if (!branchCode) throw new Error('กรุณาเลือกสาขาก่อนตรวจรอบ')
    const [data, state, payroll] = await Promise.all([
        commissionApi.closings(selectedPeriod, branchCode),
      commissionApi.branchClosing(selectedPeriod, branchCode),
      commissionApi.payments(selectedPeriod, branchCode),
    ])
    if (id !== requestId) return
    branchClosing.value = state
    paymentRows.value = payroll
    for (const row of payroll)
      paymentForms.value[row.id] ??= {
        paidDate: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date()),
        reference: '',
      }
    rows.value = data.map((row) => ({
      id: row.id,
      staff: row.employeeName,
      sales: Number(row.sales),
      rate: Number(row.rate) * 100,
      status: row.status === 'CLOSED' ? 'Closed' : 'Open',
    }))
    errorMessage.value = ''
  } catch (error) {
    if (id !== requestId) return
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรอบคอมมิชชันไม่สำเร็จ'
  } finally {
    if (id === requestId) closingsLoading.value = false
  }
}

async function preparePayments() {
  const branchCode = selectedManagerBranch()?.code
  if (!branchCode || branchClosing.value?.status !== 'CLOSED' || paymentBusy.value) return
  paymentBusy.value = true
  paymentError.value = ''
  try {
    // เตรียมงวดจ่ายได้หลังปิดยอดคอมมิชชันของสาขาแล้ว
    paymentRows.value = await commissionApi.preparePayments(month.value, branchCode)
  } catch (error) {
    paymentError.value = error instanceof Error ? error.message : 'เตรียมงวดจ่ายไม่สำเร็จ'
  } finally {
    paymentBusy.value = false
  }
}

async function closePaymentMonth() {
  const branchCode = selectedManagerBranch()?.code
  if (!branchCode || branchClosing.value?.status !== 'CLOSED' || paymentBusy.value) return
  if (!canClosePaymentBatch(paymentRows.value)) {
    paymentError.value = 'ยอดหรือจำนวนครั้งบางรายการยังไม่ครบ จึงปิดงวดจ่ายไม่ได้'
    return
  }
  paymentBusy.value = true
  paymentError.value = ''
  try {
    // ปิดงวดได้เมื่อรายการ OPEN มีข้อมูลครบตาม contract
    paymentRows.value = await commissionApi.closePayments(month.value, branchCode)
  } catch (error) {
    paymentError.value = error instanceof Error ? error.message : 'ปิดงวดจ่ายไม่สำเร็จ'
  } finally {
    paymentBusy.value = false
  }
}

async function payStaff(row: StaffPayment) {
  const form = paymentForms.value[row.id]
  if (row.status !== 'CLOSED' || !form?.paidDate || !form.reference.trim() || paymentBusy.value) return
  if (!isPaymentDateAllowed(form.paidDate, clinicToday())) {
    paymentError.value = 'วันที่จ่ายต้องไม่เกินวันที่ปัจจุบันของคลินิก'
    return
  }
  paymentBusy.value = true
  paymentError.value = ''
  try {
    // บันทึกวันจ่ายและเลขอ้างอิงเพื่อเปลี่ยนงวดจาก CLOSED เป็น PAID
    await commissionApi.pay(row.id, form.paidDate, form.reference.trim())
    const branchCode = selectedManagerBranch()?.code
    if (branchCode) paymentRows.value = await commissionApi.payments(month.value, branchCode)
  } catch (error) {
    paymentError.value = error instanceof Error ? error.message : 'บันทึกการจ่ายไม่สำเร็จ'
  } finally {
    paymentBusy.value = false
  }
}

async function reopenPayment(row: StaffPayment) {
  if (row.status !== 'CLOSED' || paymentBusy.value) return
  paymentBusy.value = true
  paymentError.value = ''
  try {
    await commissionApi.reopenPayment(row.id)
    const branchCode = selectedManagerBranch()?.code
    if (branchCode) paymentRows.value = await commissionApi.payments(month.value, branchCode)
  } catch (error) {
    paymentError.value = error instanceof Error ? error.message : 'เปิดงวดรายบุคคลไม่สำเร็จ'
  } finally {
    paymentBusy.value = false
  }
}

function openConfirm(event: Event) {
  triggerElement = event.currentTarget as HTMLElement
  isConfirming.value = true
  void nextTick(() => cancelRef.value?.focus())
}

function closeConfirm() {
  if (isSaving.value) return
  isConfirming.value = false
  void nextTick(() => triggerElement?.focus())
}

function onDialogKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeConfirm()
    return
  }
  if (event.key !== 'Tab' || !dialogRef.value) return
  const controls = [...dialogRef.value.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')]
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

async function confirmClosing() {
  isSaving.value = true
  try {
    const branchCode = selectedManagerBranch()?.code
    if (!branchCode) throw new Error('กรุณาเลือกสาขาก่อนปิดรอบ')
    // ปิดยอดของเดือนและสาขาที่เลือก
    await commissionApi.closeMonth(month.value, branchCode)
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

async function reopenBranch() {
  const branchCode = selectedManagerBranch()?.code
  const reason = reopenReason.value.trim()
  if (!branchCode || !reason || reason.length > 255 || isSaving.value) return
  isSaving.value = true
  errorMessage.value = ''
  try {
    // เปิดรอบสาขาที่ไม่มีรายการรายบุคคลได้เมื่อระบุเหตุผล
    await commissionApi.reopenEmptyBranch(month.value, branchCode, reason)
    reopenReason.value = ''
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'เปิดรอบสาขาไม่สำเร็จ'
  } finally {
    isSaving.value = false
  }
}

onMounted(load)
watch([month, selectedBranchCode], load)
onUnmounted(() => {
  requestId++
})

const visible = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return rows.value.filter((row) => !keyword || row.staff.toLowerCase().includes(keyword))
})
const openCount = computed(() => rows.value.filter((row) => row.status === 'Open').length)
const monthLabel = computed(() => {
  const [year, number] = month.value.split('-').map(Number)
  if (!year || !number) return month.value
  return new Intl.DateTimeFormat('th-TH', { month: 'short', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
})
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
    { key: 'tiers', label: 'ช่วงยอดขายค่าคอมมิชชันไม่ซ้อนกันหรือมีช่วงว่าง' },
    { key: 'sales', label: 'ยอดขายและข้อมูลผู้ขายครบถ้วน' },
    { key: 'courses', label: 'รหัสคอร์สและเจ้าของคอร์สครบทุกคอร์ส' },
    { key: 'treatment', label: 'ค่ารักษาแทนไม่เกินยอดบริการ' },
  ].map((item) => ({
    ...item,
    result: validationResults.value[item.key as keyof typeof validationResults.value],
  })),
)
const hasBlockingIssues = computed(() => closeChecks.value.some((item) => item.result !== true))
const passedChecks = computed(() => closeChecks.value.filter((item) => item.result === true).length)
const periodStatus = computed(() =>
  closingsLoading.value
    ? 'กำลังโหลด'
    : errorMessage.value
      ? 'ตรวจสอบไม่ได้'
      : branchClosing.value?.status === 'CLOSED'
        ? 'ปิดรอบแล้ว'
        : 'ยังไม่ปิดรอบ',
)

function selectView(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'Sales') void router.push('/staff/commission')
  if (value === 'Treatment') void router.push('/staff/commission?type=Treatment')
}
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
              ? 'ระบบบันทึกรอบค่าคอมมิชชันเรียบร้อยแล้ว'
              : 'ตรวจยอดขาย กำหนดช่วงค่าคอมมิชชัน และล็อกอัตราของคอร์สในรอบเดือน'
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
      <p v-if="branchClosing" role="status">สถานะรอบสาขา: {{ branchClosing.status }}</p>
      <section v-if="branchClosing?.status === 'CLOSED' && rows.length === 0" class="commission-card" aria-label="เปิดรอบสาขาที่ไม่มีรายการรายบุคคล">
        <label>เหตุผลที่เปิดรอบอีกครั้ง<textarea v-model="reopenReason" maxlength="255" rows="2" /></label>
        <button type="button" :disabled="isSaving || !reopenReason.trim()" @click="reopenBranch">
          เปิดรอบสาขาอีกครั้ง
        </button>
      </section>
      <CommissionTabs active="closing" />
      <section class="commission-card" aria-label="งวดจ่ายค่าตอบแทน">
        <h2>งวดจ่ายค่าตอบแทน · {{ fullMonthLabel }}</h2>
        <p>สถานะรอบสาขา: {{ branchClosing?.status ?? 'กำลังโหลด' }}</p>
        <button type="button" :disabled="paymentBusy || branchClosing?.status !== 'CLOSED'" @click="preparePayments">เตรียม/คำนวณงวดจ่าย</button>
        <button type="button" :disabled="paymentBusy || branchClosing?.status !== 'CLOSED' || !canClosePaymentBatch(paymentRows)" @click="closePaymentMonth">ปิดงวดจ่ายทั้งสาขา</button>
        <p v-if="paymentError" role="alert">{{ paymentError }}</p>
        <p v-if="!paymentRows.length">ยังไม่มีรายการจ่ายในสาขา/เดือนนี้</p>
        <article v-for="row in paymentRows" :key="row.id" class="payment-row">
          <b>{{ row.staffName }}</b>
          <span>สถานะ {{ row.status }}</span>
          <span>ยอดสุทธิ {{ row.net == null ? '—' : currency(row.net) }}</span>
          <span>ครั้งรอปิด {{ row.pendingSessions == null ? '—' : row.pendingSessions }}</span>
          <template v-if="row.status === 'CLOSED'">
            <label>วันที่จ่าย<input v-model="paymentForms[row.id]!.paidDate" type="date" :max="clinicToday()" /></label>
            <label>เลขอ้างอิง<input v-model="paymentForms[row.id]!.reference" maxlength="120" /></label>
            <button type="button" :disabled="paymentBusy || !paymentForms[row.id]?.paidDate || !paymentForms[row.id]?.reference.trim()" @click="payStaff(row)">บันทึกว่าจ่ายแล้ว</button>
            <button type="button" :disabled="paymentBusy" @click="reopenPayment(row)">เปิดงวดรายบุคคล</button>
          </template>
        </article>
      </section>
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
            <dt>ยอดค่าคอมมิชชัน</dt>
            <dd>{{ currency(commissionPool) }}</dd>
          </div>
        </dl>
        <button class="commission-primary" @click="router.push('/staff/commission')">
          กลับหน้าภาพรวม
        </button>
      </section>
      <template v-else>
        <section class="closing-summary" aria-label="สรุปรอบรายเดือน">
          <article>
            <span>อัตราค่าคอมมิชชัน</span><b>{{ proposedRate }}</b>
            <small v-if="hasBlockingIssues">แสดงอัตราเมื่อข้อมูลผ่านการตรวจ</small>
          </article>
          <article>
            <span>สถานะการปิดรอบ</span><b>{{ periodStatus }}</b>
            <small v-if="!validationLoading && hasBlockingIssues && periodStatus === 'ยังไม่ปิดรอบ'"
              >ยังปิดรอบไม่ได้ · ดูผลตรวจด้านล่าง</small
            >
          </article>
        </section>
        <section class="closing-workspace">
          <article class="commission-card calculation">
            <div class="workspace-heading">
              <div>
                <span class="eyebrow">ขั้นตอนที่ 1</span>
                <h2>ตรวจสอบยอดสำหรับปิดรอบ</h2>
                <p class="calculation-caption">ข้อมูลสรุปที่จะใช้ล็อกอัตราค่าคอมมิชชันของรอบนี้</p>
              </div>
            </div>
            <div class="period-cards">
              <div class="period-card">
                <span>รอบคำนวณ</span><b>{{ periodLabel }}</b>
              </div>
              <div class="period-card scheme-card">
                <span>กฎค่าคอมมิชชัน</span><b>{{ schemePeriodLabel }}</b>
              </div>
            </div>
            <div class="calculation-breakdown">
              <div><span>ยอดขายคอร์สรวมก่อนส่วนลด</span><b>—</b></div>
              <div><span>ส่วนลดรายรายการ</span><b>—</b></div>
              <div class="base-total">
                <span>ฐานคำนวณค่าคอมมิชชัน</span>
                <b>{{ validationLoading || validationError ? '—' : currency(totalBase) }}</b>
              </div>
            </div>
            <p class="data-note">ยอดก่อนส่วนลดและส่วนลดรายรายการจะแสดงเมื่อรายงานส่งข้อมูลครบ</p>
            <div class="pool" aria-label="ยอดค่าคอมมิชชันที่จะสร้าง">
              <span>
                <small>ยอดค่าคอมมิชชันที่จะสร้าง</small>
                <b>{{
                  validationLoading || validationError || hasBlockingIssues
                    ? 'รอตรวจสอบข้อมูล'
                    : currency(commissionPool)
                }}</b>
              </span>
              <em>คำนวณตามช่วงยอดขายของแต่ละบุคคล</em>
            </div>
          </article>
          <article class="commission-card checklist">
            <div class="workspace-heading checklist-heading">
              <div>
                <span class="eyebrow">ขั้นตอนที่ 2</span>
                <h2>ตรวจสอบความพร้อม</h2>
              </div>
              <span
                class="readiness-badge"
                :class="{ ready: !validationLoading && !hasBlockingIssues }"
              >
                {{
                  validationLoading
                    ? 'กำลังตรวจ'
                    : hasBlockingIssues
                      ? 'ต้องตรวจสอบ'
                      : 'พร้อมปิดรอบ'
                }}
              </span>
            </div>
            <p class="checklist-summary">
              ผ่าน <b>{{ passedChecks }}</b> จาก 4 รายการ
              <span v-if="hasBlockingIssues"> · แก้รายการที่แจ้งก่อนดำเนินการ</span>
            </p>
            <p v-if="validationError" class="validation-error" role="alert">
              {{ validationError }}
            </p>
            <div v-if="validationLoading" class="check-row checking">กำลังตรวจสอบข้อมูลรอบนี้…</div>
            <ul v-else class="check-list">
              <li
                v-for="check in closeChecks"
                :key="check.key"
                :class="{ issue: check.result === false, unknown: check.result === null }"
              >
                <span class="check-icon">{{
                  check.result === true ? '✓' : check.result === false ? '!' : '?'
                }}</span>
                <span>{{ check.label }}</span>
                <small>{{
                  check.result === true
                    ? 'ผ่าน'
                    : check.result === false
                      ? 'ต้องแก้ไข'
                      : 'ตรวจไม่ได้'
                }}</small>
              </li>
            </ul>
            <div v-if="tierIssue" class="tier-recovery" role="status">
              <p>{{ tierIssue }}</p>
              <div>
                <RouterLink to="/staff/settings/commission-tiers">แก้กฎค่าคอมมิชชัน</RouterLink>
                <button
                  type="button"
                  :disabled="validationLoading"
                  @click="validatePeriod(requestId, month)"
                >
                  ตรวจอีกครั้ง
                </button>
              </div>
            </div>
            <button
              v-else-if="validationError"
              type="button"
              class="retry-button"
              :disabled="validationLoading"
              @click="load"
            >
              โหลดข้อมูลอีกครั้ง
            </button>
            <div class="closing-actions">
              <p
                v-if="!validationLoading && !closingsLoading && hasBlockingIssues"
                class="closing-blocker"
                role="status"
              >
                ยังปิดรอบไม่ได้ · แก้รายการที่ไม่ผ่านด้านบนแล้วตรวจอีกครั้ง
              </p>
              <button
                v-if="branchClosing?.status !== 'CLOSED'"
                class="commission-primary"
                :disabled="
                  validationLoading || closingsLoading || !!errorMessage || hasBlockingIssues
                "
                @click="openConfirm"
              >
                ปิดรอบ{{ fullMonthLabel }}
              </button>
            </div>
          </article>
        </section>
      </template>
    </main>
    <div v-if="isConfirming" class="modal-backdrop" role="presentation" @click.self="closeConfirm">
      <section
        ref="dialogRef"
        class="confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="close-title"
        aria-describedby="close-description"
        @keydown="onDialogKeydown"
      >
        <h2 id="close-title">ยืนยันปิดรอบ{{ fullMonthLabel }}</h2>
        <p id="close-description">
          ตรวจสอบยอดและรายการก่อนปิดรอบ เมื่อยืนยันแล้วระบบจะล็อกอัตราค่าคอมมิชชันของรอบนี้
        </p>
        <dl class="confirm-facts">
          <div>
            <dt>รายการขาย</dt>
            <dd>{{ reportCount }} รายการ</dd>
          </div>
          <div>
            <dt>ฐานคำนวณ</dt>
            <dd>{{ currency(totalBase) }}</dd>
          </div>
          <div>
            <dt>ค่าคอมมิชชันรวม</dt>
            <dd>{{ currency(commissionPool) }}</dd>
          </div>
        </dl>
        <div class="confirm-actions">
          <button
            ref="cancelRef"
            class="cancel"
            type="button"
            :disabled="isSaving"
            @click="closeConfirm"
          >
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
.closing-blocker {
  margin: 0 0 10px;
  color: #805318;
  font-size: 0.875rem;
  line-height: 1.5;
}
@media (max-width: 900px) {
  .closing-month-selector {
    margin: 16px 0 20px;
  }
}
@media (max-width: 620px) {
  .closing-summary {
    grid-template-columns: 1fr;
  }
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  padding: 28px;
}
.closing-workspace h2 {
  margin: 3px 0 0;
  font-size: 1.25rem;
}
.workspace-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.eyebrow {
  display: block;
  color: #16877f;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.period-badge,
.readiness-badge {
  flex: none;
  padding: 7px 10px;
  border-radius: 999px;
  color: #647a63;
  background: #f0f5f1;
  font-size: 0.8rem;
  font-weight: 700;
  white-space: nowrap;
}
.readiness-badge {
  color: #9a6517;
  background: #fff1c7;
}
.readiness-badge.ready {
  color: #16875b;
  background: #def3e7;
}
.calculation > div {
  display: flex;
  justify-content: space-between;
  padding: 14px 0;
}
.calculation .pool {
  align-items: center;
  color: #135f5a;
}
.calculation aside {
  margin-top: 18px;
}
.calculation-caption {
  margin: 6px 0 0;
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
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: 12px;
  background: #dff2eb;
}
.pool small {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
}
.pool b {
  font-size: 1.4rem;
}
.pool em {
  color: #54716b;
  font-size: 0.8rem;
  font-style: normal;
  text-align: right;
}
.data-note {
  margin: 10px 0 0;
  color: #7a8782;
  font-size: 0.78rem;
}
.closing-summary small {
  color: #6d7d78;
  font-size: 13px;
}
.checklist {
  display: block;
}
.checklist-summary {
  margin: 16px 0 12px;
  color: #687a74;
  font-size: 0.86rem;
}
.checklist-summary b {
  color: #25372e;
}
.check-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.check-list li,
.check-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #d7eadf;
  border-radius: 9px;
  color: #35584d;
  background: #f3faf6;
  font-size: 0.84rem;
}
.check-list li.issue {
  border-color: #f2dda4;
  color: #76551b;
  background: #fff8e6;
}
.check-list li.unknown,
.check-row.checking {
  border-color: #e0e6e2;
  color: #6b7d76;
  background: #f6f8f7;
}
.check-icon {
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border-radius: 50%;
  color: #16875b;
  background: #dff2e8;
  font-size: 0.75rem;
  font-weight: 800;
}
.issue .check-icon {
  color: #9a6517;
  background: #fff0c1;
}
.unknown .check-icon {
  color: #6b7d76;
  background: #e8ece9;
}
.check-list small {
  color: inherit;
  font-size: 0.72rem;
  font-weight: 700;
  white-space: nowrap;
}
.tier-recovery,
.validation-error {
  margin: 12px 0 0;
  padding: 12px 14px;
  border: 1px solid #e9d6a4;
  border-radius: 10px;
  color: #634b1d;
  background: #fff8e9;
  font-size: 0.85rem;
}
.tier-recovery p {
  margin: 0 0 10px;
}
.tier-recovery div {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.tier-recovery a,
.tier-recovery button,
.retry-button {
  padding: 8px 11px;
  border: 1px solid #9cbfbb;
  border-radius: 8px;
  color: #155f5c;
  background: #fff;
  font: inherit;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}
.retry-button {
  margin-top: 12px;
}
.tier-recovery a:hover,
.tier-recovery button:hover,
.retry-button:hover {
  background: #e7f4f1;
}
.tier-recovery :is(a, button):focus-visible,
.retry-button:focus-visible {
  outline: 3px solid #16877f;
  outline-offset: 2px;
}
.closing-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-top: 20px;
}
.closing-actions button {
  min-height: 46px;
  min-width: 210px;
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
.confirm-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.confirm-modal p {
  margin: 12px 0 0;
  color: #536a63;
  line-height: 1.5;
  text-align: center;
}
.confirm-facts {
  margin: 18px 0 0;
  padding: 8px 14px;
  border-radius: 10px;
  background: #eef6f3;
}
.confirm-facts div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
}
.confirm-facts div + div {
  border-top: 1px solid #d9e6e1;
}
.confirm-facts dt {
  color: #536a63;
}
.confirm-facts dd {
  margin: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.confirm-modal :is(.cancel, .confirm):focus-visible {
  outline: 3px solid #16877f;
  outline-offset: 2px;
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
  .confirm-actions {
    flex-direction: column-reverse;
  }
  .confirm-modal button {
    width: 100%;
  }
  .workspace-heading,
  .pool {
    flex-direction: column;
  }
  .pool em {
    text-align: left;
  }
  .closing-actions {
    display: block;
  }
  .closing-actions button {
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
