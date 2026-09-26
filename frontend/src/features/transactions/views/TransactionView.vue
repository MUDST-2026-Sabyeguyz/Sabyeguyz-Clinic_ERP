<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import searchIcon from '@/assets/transaction-search.svg'
import {
  branchApi,
  courseApi,
  salesApi,
  type CourseUsageTransaction,
  type SaleDetail,
  type SaleSummary,
} from '@/api/client'

type Tab = 'sales' | 'usage' | 'history'
type Step = 'list' | 'detail' | 'reason' | 'confirm' | 'success'
type Transaction = {
  key: string
  kind: 'sale' | 'usage'
  id: number
  number: string
  date: string
  patient: string
  hn: string
  item: string
  branchCode: string
  quantity: string
  status: 'ปกติ' | 'ยกเลิก (Void)'
  performedBy: string
  paymentMethod: string
  voidedAt?: string
  voidReason?: string
  detail?: SaleDetail
}

const tab = ref<Tab>('sales')
const step = ref<Step>('list')
const rows = ref<Transaction[]>([])
const selected = ref<Transaction | null>(null)
const search = ref('')
const branchFilter = ref('')
const statusFilter = ref('')
const page = ref(1)
const loading = ref(false)
const submitting = ref(false)
const showCancelWarning = ref(false)
const cancelWarningBackButton = ref<HTMLButtonElement | null>(null)
const errorMessage = ref('')
const reasonType = ref('')
const otherReason = ref('')
const expandedOriginal = ref<string[]>([])
const lastCancelledKey = ref('')
const branchNames = ref<Record<string, string>>({})
const branchOptions = ref<Array<{ code: string; name: string }>>([])
const cancellationActor = ref('')
const pageSize = computed(() => (tab.value === 'history' ? 5 : 10))

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'long', year: 'numeric' }).format(
        date,
      )
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : new Intl.DateTimeFormat('th-TH', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date)
}

function branchName(code: string) {
  return branchNames.value[code] ?? code
}

function saleRow(sale: SaleSummary): Transaction {
  return {
    key: `sale-${sale.id}`,
    kind: 'sale',
    id: sale.id,
    number: sale.receiptNo,
    date: sale.soldAt,
    patient: sale.patient.fullName,
    hn: sale.patient.hn,
    item: sale.itemDescription || 'รายการขายบริการ / คอร์ส',
    branchCode: sale.branchCode,
    quantity: `฿${Number(sale.total).toLocaleString('th-TH')}`,
    status: sale.status === 'VOIDED' ? 'ยกเลิก (Void)' : 'ปกติ',
    performedBy: sale.salesperson?.fullName ?? 'ไม่ระบุ',
    paymentMethod: '—',
    voidedAt: sale.voidedAt,
    voidReason: sale.voidReason,
  }
}

function usageRow(usage: CourseUsageTransaction): Transaction {
  return {
    key: `usage-${usage.id}`,
    kind: 'usage',
    id: usage.id,
    number: `USE-${usage.id}`,
    date: usage.usedAt,
    patient: usage.patientName,
    hn: usage.patientHn,
    item: usage.courseName,
    branchCode: usage.branchCode,
    quantity: '1 ครั้ง',
    status: usage.voidedAt ? 'ยกเลิก (Void)' : 'ปกติ',
    performedBy: 'ไม่ระบุ',
    paymentMethod: 'ใช้สิทธิ์คอร์ส',
    voidedAt: usage.voidedAt ?? undefined,
    voidReason: usage.voidReason ?? undefined,
  }
}

async function loadTransactions() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [firstPage, usages, branches] = await Promise.all([
      salesApi.list({ page: 0, size: 100 }),
      courseApi.usageTransactions(),
      branchApi.list(),
    ])
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) =>
        salesApi.list({ page: index + 1, size: 100 }),
      ),
    )
    branchNames.value = Object.fromEntries(branches.map((branch) => [branch.branchCode, branch.name]))
    branchOptions.value = branches
      .filter((branch) => branch.active)
      .map(({ branchCode, name }) => ({ code: branchCode, name }))
    const sales = [firstPage, ...remainingPages].flatMap((result) => result.items)
    rows.value = [...sales.map(saleRow), ...usages.map(usageRow)]
    const voidedSales = rows.value.filter((row) => row.kind === 'sale' && row.status !== 'ปกติ')
    await Promise.all(
      voidedSales.map(async (row) => {
        try {
          const detail = await salesApi.get(row.id)
          updateFromDetail(row, detail)
        } catch {
          // A failed detail request must not hide the transaction from the list.
        }
      }),
    )
    if (selected.value) {
      selected.value = rows.value.find((row) => row.key === selected.value?.key) ?? selected.value
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการธุรกรรมไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

function updateFromDetail(row: Transaction, detail: SaleDetail) {
  row.detail = detail
  row.item = detail.items.map((item) => item.description).join(', ') || row.item
  row.paymentMethod =
    detail.payments.find((payment) => payment.type !== 'REFUND')?.paymentMethodCode ?? '—'
  row.voidReason = detail.voidReason
  row.voidedAt = detail.voidedAt
}

onMounted(loadTransactions)

const visibleRows = computed(() => {
  const term = search.value.trim().toLocaleLowerCase()
  return rows.value
    .filter((row) =>
      tab.value === 'history'
        ? row.status === 'ยกเลิก (Void)'
        : row.kind === (tab.value === 'sales' ? 'sale' : 'usage'),
    )
    .filter(
      (row) =>
        !term ||
        `${row.number} ${row.patient} ${row.hn} ${row.item}`.toLocaleLowerCase().includes(term),
    )
    .filter((row) => !branchFilter.value || row.branchCode === branchFilter.value)
    .filter(
      (row) =>
        !statusFilter.value ||
        (statusFilter.value === 'void' ? row.status !== 'ปกติ' : row.status === 'ปกติ'),
    )
    .sort((left, right) => Date.parse(right.date) - Date.parse(left.date))
})
const totalPages = computed(() => Math.max(1, Math.ceil(visibleRows.value.length / pageSize.value)))
const pagedRows = computed(() =>
  visibleRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const reason = computed(() =>
  reasonType.value === 'อื่น ๆ' ? otherReason.value.trim() : reasonType.value,
)
const canReview = computed(() => Boolean(reason.value))
const warning = computed(() =>
  selected.value?.kind === 'sale'
    ? 'ระบบจะบันทึกสถานะ Void และกลับรายการรับเงินของใบเสร็จนี้ คอร์สที่ซื้อในรายการจะถูกยกเลิก แต่สิทธิ์ที่ใช้ไปแล้วจะยังอยู่ในประวัติ'
    : 'ระบบจะบันทึกสถานะ Void และคืนสิทธิ์คอร์ส 1 ครั้ง โดยเก็บรายการเดิมไว้ตรวจสอบย้อนหลัง',
)

watch([tab, search, branchFilter, statusFilter], () => {
  page.value = 1
})
watch(totalPages, (count) => {
  if (page.value > count) page.value = count
})

function changeTab(next: Tab) {
  tab.value = next
  step.value = 'list'
  selected.value = null
  statusFilter.value = ''
  search.value = ''
  branchFilter.value = ''
  expandedOriginal.value = []
}

async function openDetail(row: Transaction) {
  selected.value = row
  errorMessage.value = ''
  expandedOriginal.value = []
  if (row.kind === 'sale' && !row.detail) {
    try {
      updateFromDetail(row, await salesApi.get(row.id))
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'โหลดรายละเอียดไม่สำเร็จ'
      return
    }
  }
  step.value = 'detail'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function startCancellation() {
  reasonType.value = ''
  otherReason.value = ''
  errorMessage.value = ''
  step.value = 'reason'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function openCancelWarning() {
  if (!selected.value || !canReview.value || submitting.value) return
  showCancelWarning.value = true
  await nextTick()
  cancelWarningBackButton.value?.focus()
}

function closeCancelWarning() {
  if (!submitting.value) showCancelWarning.value = false
}

async function confirmCancellation() {
  const row = selected.value
  if (!showCancelWarning.value || !row || !canReview.value || submitting.value) return
  submitting.value = true
  showCancelWarning.value = false
  errorMessage.value = ''
  try {
    if (row.kind === 'sale') {
      updateFromDetail(row, await salesApi.reverse(row.id, reason.value))
    } else {
      await courseApi.reverse(row.id, reason.value)
    }
    row.status = 'ยกเลิก (Void)'
    row.voidReason = reason.value
    row.voidedAt = new Date().toISOString()
    lastCancelledKey.value = row.key
    try {
      const user = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
        username?: string
      } | null
      cancellationActor.value = user?.username ?? 'ไม่ระบุ'
    } catch {
      cancellationActor.value = 'ไม่ระบุ'
    }
    step.value = 'success'
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ยกเลิกรายการไม่สำเร็จ'
  } finally {
    submitting.value = false
  }
}

function backToList(next: Tab = tab.value) {
  showCancelWarning.value = false
  step.value = 'list'
  tab.value = next
  selected.value = null
  expandedOriginal.value = []
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function toggleOriginal(key: string) {
  expandedOriginal.value = expandedOriginal.value.includes(key)
    ? expandedOriginal.value.filter((item) => item !== key)
    : [...expandedOriginal.value, key]
}
</script>

<template>
  <div class="staff-page transaction-page">
    <StaffHeader />
    <main class="transaction-main">
      <template v-if="step === 'list'">
        <header class="transaction-heading">
          <h1>ธุรกรรม</h1>
          <p>ตรวจสอบรายการขายและการใช้บริการ พร้อมหลักฐานการยกเลิกย้อนหลัง</p>
        </header>
        <nav class="transaction-tabs" aria-label="ประเภทของธุรกรรม">
          <button type="button" :class="{ active: tab === 'sales' }" @click="changeTab('sales')">
            รายการขาย
          </button>
          <button type="button" :class="{ active: tab === 'usage' }" @click="changeTab('usage')">
            รายการใช้บริการ
          </button>
          <button
            type="button"
            :class="{ active: tab === 'history' }"
            @click="changeTab('history')"
          >
            ประวัติการยกเลิก
          </button>
        </nav>
        <section class="transaction-card transaction-list">
          <h2>
            {{
              tab === 'sales'
                ? 'รายการขาย'
                : tab === 'usage'
                  ? 'รายการใช้บริการ'
                  : 'ประวัติการยกเลิก'
            }}
          </h2>
          <div v-if="tab !== 'history'" class="transaction-filters">
            <label class="transaction-search">
              <img :src="searchIcon" alt="" width="24" height="24" />
              <input
                v-model="search"
                type="search"
                aria-label="ค้นหารายการ"
                placeholder="ค้นหาเลขที่ ชื่อผู้ป่วย หรือรายการ"
              />
            </label>
            <select v-model="branchFilter" aria-label="กรองสาขา">
              <option value="">สาขาทั้งหมด</option>
              <option v-for="branch in branchOptions" :key="branch.code" :value="branch.code">
                {{ branch.name }}
              </option>
            </select>
            <select v-model="statusFilter" aria-label="กรองสถานะ">
              <option value="">สถานะทั้งหมด</option>
              <option value="normal">ปกติ</option>
              <option value="void">ยกเลิก (Void)</option>
            </select>
          </div>
          <div v-if="loading" class="transaction-empty" role="status">
            กำลังโหลดรายการธุรกรรม...
          </div>
          <div v-else-if="!visibleRows.length" class="transaction-empty">
            {{ tab === 'history' ? 'ยังไม่มีประวัติการยกเลิก' : 'ไม่พบรายการที่ตรงกับเงื่อนไข' }}
          </div>
          <div v-else-if="tab === 'history'" class="transaction-history-list">
            <article v-for="row in pagedRows" :key="row.key" class="transaction-history-card">
              <div class="transaction-record-head">
                <div>
                  <small>รายการที่ยกเลิก</small>
                  <h3>{{ row.number }}</h3>
                  <p>{{ row.kind === 'sale' ? 'ขายบริการ / คอร์ส' : 'ใช้สิทธิ์คอร์ส' }}</p>
                </div>
                <span class="transaction-status void">ยกเลิก (Void)</span>
              </div>
              <div class="transaction-patient">
                <strong>{{ row.patient }}</strong
                ><span>HN {{ row.hn }}</span>
              </div>
              <section class="transaction-evidence">
                <h3>หลักฐานการยกเลิก</h3>
                <dl class="transaction-detail-grid">
                  <div>
                    <dt>ผู้ดำเนินการยกเลิก</dt>
                    <dd>{{ row.key === lastCancelledKey ? cancellationActor : 'ไม่ระบุ' }}</dd>
                  </div>
                  <div>
                    <dt>วันที่และเวลาที่ยกเลิก</dt>
                    <dd>{{ formatDateTime(row.voidedAt) }}</dd>
                  </div>
                  <div>
                    <dt>เหตุผลในการยกเลิก</dt>
                    <dd>{{ row.voidReason || 'ไม่ระบุ' }}</dd>
                  </div>
                </dl>
              </section>
              <button
                class="transaction-secondary"
                type="button"
                :aria-expanded="expandedOriginal.includes(row.key)"
                @click="toggleOriginal(row.key)"
              >
                ดู / ซ่อนข้อมูลรายการเดิม
              </button>
              <dl
                v-if="expandedOriginal.includes(row.key)"
                class="transaction-detail-grid transaction-original"
              >
                <div>
                  <dt>บริการ / คอร์ส</dt>
                  <dd>{{ row.item }}</dd>
                </div>
                <div>
                  <dt>วันที่และเวลาของรายการเดิม</dt>
                  <dd>{{ formatDateTime(row.date) }}</dd>
                </div>
                <div>
                  <dt>สาขา</dt>
                  <dd>{{ branchName(row.branchCode) }}</dd>
                </div>
                <div>
                  <dt>ยอดชำระ / สิทธิ์ที่ใช้</dt>
                  <dd>{{ row.quantity }}</dd>
                </div>
              </dl>
            </article>
          </div>
          <div v-else class="transaction-table-wrap">
            <table class="transaction-table">
              <thead>
                <tr>
                  <th>เลขที่</th>
                  <th>วันที่</th>
                  <th>ผู้ป่วย</th>
                  <th>รายการ</th>
                  <th>สาขา</th>
                  <th>จำนวน</th>
                  <th>สถานะ</th>
                  <th>รายละเอียด</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="row.key">
                  <td>{{ row.number }}</td>
                  <td>{{ formatDate(row.date) }}</td>
                  <td>{{ row.patient }}</td>
                  <td>{{ row.item }}</td>
                  <td>{{ branchName(row.branchCode) }}</td>
                  <td>{{ row.quantity }}</td>
                  <td>
                    <span class="transaction-status" :class="{ void: row.status !== 'ปกติ' }">{{
                      row.status
                    }}</span>
                  </td>
                  <td>
                    <button
                      class="transaction-primary transaction-row-button"
                      type="button"
                      @click="openDetail(row)"
                    >
                      ดูรายละเอียด
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <nav
            v-if="tab === 'history' || totalPages > 1"
            class="transaction-pagination"
            :class="{ 'transaction-history-pagination': tab === 'history' }"
            aria-label="เปลี่ยนหน้ารายการ"
          >
            <button type="button" :disabled="page === 1" @click="page -= 1">← ก่อนหน้า</button>
            <span>หน้า {{ page }} จาก {{ totalPages }}</span>
            <button type="button" :disabled="page === totalPages" @click="page += 1">
              ถัดไป →
            </button>
          </nav>
        </section>
      </template>

      <template v-else-if="selected">
        <button
          class="transaction-back"
          type="button"
          @click="step === 'confirm' ? (step = 'detail') : backToList()"
        >
          ← {{ step === 'confirm' ? 'กลับรายละเอียด' : 'กลับไปรายการ' }}
        </button>

        <template v-if="step === 'detail'">
          <header class="transaction-heading">
            <h1>รายละเอียดธุรกรรม</h1>
            <p>ตรวจสอบรายการขายและการใช้บริการ พร้อมหลักฐานการยกเลิกย้อนหลัง</p>
          </header>
          <section class="transaction-card transaction-detail-card">
            <div class="transaction-record-head">
              <div>
                <small>เลขที่รายการ</small>
                <h2>{{ selected.number }}</h2>
                <p>{{ selected.kind === 'sale' ? 'ขายบริการ / คอร์ส' : 'ใช้สิทธิ์คอร์ส' }}</p>
              </div>
              <span class="transaction-status" :class="{ void: selected.status !== 'ปกติ' }">{{
                selected.status
              }}</span>
            </div>
            <div class="transaction-patient">
              <strong>{{ selected.patient }}</strong
              ><span>HN {{ selected.hn }}</span>
            </div>
            <h3>ข้อมูลรายการ</h3>
            <dl class="transaction-detail-grid">
              <div>
                <dt>บริการ / คอร์ส</dt>
                <dd>{{ selected.item }}</dd>
              </div>
              <div>
                <dt>วันที่และเวลา</dt>
                <dd>{{ formatDateTime(selected.date) }}</dd>
              </div>
              <div>
                <dt>สาขา</dt>
                <dd>{{ branchName(selected.branchCode) }}</dd>
              </div>
              <div>
                <dt>ผู้บันทึกรายการ</dt>
                <dd>{{ selected.performedBy }}</dd>
              </div>
              <div>
                <dt>{{ selected.kind === 'sale' ? 'ยอดชำระ' : 'สิทธิ์ที่ใช้' }}</dt>
                <dd>{{ selected.quantity }}</dd>
              </div>
              <div>
                <dt>ช่องทาง / ประเภท</dt>
                <dd>{{ selected.paymentMethod }}</dd>
              </div>
            </dl>
            <section v-if="selected.status !== 'ปกติ'" class="transaction-evidence">
              <h3>หลักฐานการยกเลิก</h3>
              <p>วันที่และเวลา: {{ formatDateTime(selected.voidedAt) }}</p>
              <p>เหตุผล: {{ selected.voidReason || 'ไม่ระบุ' }}</p>
            </section>
            <p v-else class="transaction-warning">
              <strong>โปรดตรวจสอบก่อนยกเลิก</strong><br />{{ warning }}
            </p>
            <div class="transaction-actions">
              <button class="transaction-secondary" type="button" @click="backToList()">
                ← กลับไปรายการ</button
              ><button
                v-if="selected.status === 'ปกติ'"
                class="transaction-danger"
                type="button"
                @click="startCancellation"
              >
                ยกเลิกรายการ
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'reason'">
          <header class="transaction-heading">
            <h1>ระบุเหตุผลในการยกเลิก</h1>
            <p>ตรวจสอบรายการและระบุเหตุผล ก่อนดำเนินการต่อ</p>
          </header>
          <section class="transaction-card transaction-detail-card">
            <h2>รายการที่จะยกเลิก</h2>
            <div class="transaction-record-head">
              <div>
                <strong>{{ selected.number }}</strong>
                <p>{{ selected.kind === 'sale' ? 'ขายบริการ / คอร์ส' : 'ใช้สิทธิ์คอร์ส' }}</p>
              </div>
            </div>
            <div class="transaction-patient">
              <strong>{{ selected.patient }}</strong
              ><span>HN {{ selected.hn }}</span>
            </div>
            <dl class="transaction-detail-grid">
              <div>
                <dt>บริการ / คอร์ส</dt>
                <dd>{{ selected.item }}</dd>
              </div>
              <div>
                <dt>วันที่และเวลา</dt>
                <dd>{{ formatDateTime(selected.date) }}</dd>
              </div>
              <div>
                <dt>สาขา</dt>
                <dd>{{ branchName(selected.branchCode) }}</dd>
              </div>
              <div>
                <dt>ผู้บันทึกรายการ</dt>
                <dd>{{ selected.performedBy }}</dd>
              </div>
              <div>
                <dt>{{ selected.kind === 'sale' ? 'ยอดชำระ' : 'สิทธิ์ที่ใช้' }}</dt>
                <dd>{{ selected.quantity }}</dd>
              </div>
              <div>
                <dt>ช่องทาง / ประเภท</dt>
                <dd>{{ selected.paymentMethod }}</dd>
              </div>
            </dl>
          </section>
          <section class="transaction-card transaction-reason-card">
            <h2>เหตุผลในการยกเลิก</h2>
            <label for="transaction-reason">เลือกเหตุผล <span aria-hidden="true">*</span></label
            ><select id="transaction-reason" v-model="reasonType">
              <option value="">เลือกเหตุผล</option>
              <option>บันทึกรายการซ้ำ</option>
              <option>บันทึกข้อมูลไม่ถูกต้อง</option>
              <option>อื่น ๆ</option></select
            ><label v-if="reasonType === 'อื่น ๆ'" for="transaction-other-reason"
              >รายละเอียดเพิ่มเติม <span aria-hidden="true">*</span></label
            ><textarea
              v-if="reasonType === 'อื่น ๆ'"
              id="transaction-other-reason"
              v-model="otherReason"
              rows="3"
              placeholder="ระบุเหตุผลเพิ่มเติม"
            />
            <p v-if="reasonType === 'อื่น ๆ' && !otherReason.trim()" class="transaction-validation">
              กรุณาระบุรายละเอียดเมื่อเลือก “อื่น ๆ”
            </p>
            <p class="transaction-warning">
              <strong>โปรดตรวจสอบก่อนยกเลิก</strong><br />{{ warning }}
            </p>
            <div class="transaction-actions">
              <button class="transaction-secondary" type="button" @click="step = 'detail'">
                ← กลับ</button
              ><button
                class="transaction-primary"
                type="button"
                :disabled="!canReview"
                @click="step = 'confirm'"
              >
                ตรวจสอบการยกเลิก →
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'confirm'">
          <header class="transaction-heading">
            <h1>ยืนยันการยกเลิกรายการ</h1>
            <p>ตรวจสอบข้อมูลและเหตุผลเป็นครั้งสุดท้าย ก่อนยืนยัน</p>
          </header>
          <section class="transaction-card transaction-detail-card">
            <div class="transaction-record-head">
              <div>
                <small>รายการที่จะยกเลิก</small>
                <h2>{{ selected.number }}</h2>
                <p>{{ selected.kind === 'sale' ? 'ขายบริการ / คอร์ส' : 'ใช้สิทธิ์คอร์ส' }}</p>
              </div>
              <span class="transaction-pending">รอยืนยันการยกเลิก</span>
            </div>
            <div class="transaction-patient">
              <strong>{{ selected.patient }}</strong
              ><span>HN {{ selected.hn }}</span>
            </div>
            <dl class="transaction-detail-grid">
              <div>
                <dt>บริการ / คอร์ส</dt>
                <dd>{{ selected.item }}</dd>
              </div>
              <div>
                <dt>วันที่และเวลา</dt>
                <dd>{{ formatDateTime(selected.date) }}</dd>
              </div>
              <div>
                <dt>สาขา</dt>
                <dd>{{ branchName(selected.branchCode) }}</dd>
              </div>
              <div>
                <dt>ผู้บันทึกรายการ</dt>
                <dd>{{ selected.performedBy }}</dd>
              </div>
              <div>
                <dt>ยอดชำระ / สิทธิ์ที่ใช้ในรายการเดิม</dt>
                <dd>{{ selected.quantity }}</dd>
              </div>
              <div>
                <dt>ช่องทาง / ประเภท</dt>
                <dd>{{ selected.paymentMethod }}</dd>
              </div>
            </dl>
            <div class="transaction-review-reason">
              <div>
                <strong>เหตุผลในการยกเลิก</strong>
                <p>{{ reason }}</p>
              </div>
              <button type="button" @click="step = 'reason'">แก้ไขเหตุผล</button>
            </div>
            <p class="transaction-warning">
              <strong>โปรดตรวจสอบผลของการยกเลิก</strong><br />{{ warning }}
            </p>
            <div class="transaction-actions">
              <button class="transaction-secondary" type="button" @click="step = 'detail'">
                ← ไม่ยกเลิกรายการ</button
              ><button
                class="transaction-danger"
                type="button"
                :disabled="submitting"
                @click="openCancelWarning"
              >
                {{ submitting ? 'กำลังยกเลิก...' : 'ยืนยันยกเลิกรายการ' }}
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'success'">
          <header class="transaction-success-heading">
            <span aria-hidden="true">✓</span>
            <div>
              <h1>ยกเลิกรายการสำเร็จ</h1>
              <p>บันทึกสถานะและเหตุผลการยกเลิกเรียบร้อยแล้ว</p>
            </div>
          </header>
          <section class="transaction-card transaction-detail-card">
            <div class="transaction-record-head">
              <div>
                <small>รายการที่ยกเลิก</small>
                <h2>{{ selected.number }}</h2>
                <p>{{ selected.kind === 'sale' ? 'ขายบริการ / คอร์ส' : 'ใช้สิทธิ์คอร์ส' }}</p>
              </div>
              <span class="transaction-status void">ยกเลิก (Void)</span>
            </div>
            <div class="transaction-patient">
              <strong>{{ selected.patient }}</strong
              ><span>HN {{ selected.hn }}</span>
            </div>
            <dl class="transaction-detail-grid">
              <div>
                <dt>บริการ / คอร์ส</dt>
                <dd>{{ selected.item }}</dd>
              </div>
              <div>
                <dt>วันที่และเวลาของรายการเดิม</dt>
                <dd>{{ formatDateTime(selected.date) }}</dd>
              </div>
              <div>
                <dt>สาขา</dt>
                <dd>{{ branchName(selected.branchCode) }}</dd>
              </div>
              <div>
                <dt>ผู้บันทึกรายการ</dt>
                <dd>{{ selected.performedBy }}</dd>
              </div>
              <div>
                <dt>ยอดชำระ / สิทธิ์ที่ใช้ในรายการเดิม</dt>
                <dd>{{ selected.quantity }}</dd>
              </div>
              <div>
                <dt>ช่องทาง / ประเภท</dt>
                <dd>{{ selected.paymentMethod }}</dd>
              </div>
            </dl>
            <section class="transaction-evidence">
              <h3>หลักฐานการยกเลิก</h3>
              <dl class="transaction-detail-grid">
                <div>
                  <dt>ผู้ดำเนินการยกเลิก</dt>
                  <dd>{{ cancellationActor }}</dd>
                </div>
                <div>
                  <dt>วันที่และเวลาที่ยกเลิก</dt>
                  <dd>{{ formatDateTime(selected.voidedAt) }}</dd>
                </div>
                <div>
                  <dt>เหตุผลในการยกเลิก</dt>
                  <dd>{{ selected.voidReason }}</dd>
                </div>
              </dl>
            </section>
            <div class="transaction-actions">
              <button class="transaction-secondary" type="button" @click="backToList()">
                กลับไปรายการ</button
              ><button class="transaction-primary" type="button" @click="backToList('history')">
                ดูประวัติการยกเลิก
              </button>
            </div>
          </section>
        </template>
      </template>

      <p v-if="errorMessage" class="transaction-error" role="alert">{{ errorMessage }}</p>
    </main>
    <div
      v-if="showCancelWarning && selected"
      class="transaction-modal-backdrop"
      @click.self="closeCancelWarning"
      @keydown.esc="closeCancelWarning"
    >
      <section
        class="transaction-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="transaction-modal-title"
        aria-describedby="transaction-modal-description"
      >
        <h2 id="transaction-modal-title">ยืนยันการยกเลิกรายการ?</h2>
        <p id="transaction-modal-description">
          การยกเลิกจะบันทึกประวัติไว้ และอาจมีผลต่อยอดชำระหรือสิทธิ์คอร์ส
          โปรดตรวจสอบอีกครั้งก่อนดำเนินการ
        </p>
        <div class="transaction-modal-summary">
          <strong>{{ selected.number }} · {{ selected.patient }}</strong>
          <span>{{ selected.item }} · {{ selected.quantity }}</span>
          <span>เหตุผล: {{ reason }}</span>
        </div>
        <div class="transaction-actions">
          <button
            ref="cancelWarningBackButton"
            class="transaction-secondary"
            type="button"
            :disabled="submitting"
            @click="closeCancelWarning"
          >
            กลับไปตรวจสอบ
          </button>
          <button
            class="transaction-danger"
            type="button"
            :disabled="submitting"
            @click="confirmCancellation"
          >
            {{ submitting ? 'กำลังยกเลิก...' : 'ยืนยันการยกเลิก' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="../styles/transactions.css"></style>
