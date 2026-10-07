<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

type Tab = 'sales' | 'usage' | 'history'
type Step = 'list' | 'detail' | 'history-detail' | 'reason' | 'confirm' | 'success'
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
  voidNumber?: string
}

type VoidTransactionCommand = { key: string; reason: string; actor: string }
interface TransactionRepository {
  list(): Promise<Transaction[]>
  void(command: VoidTransactionCommand): Promise<Transaction>
}

const seedTransactions: Transaction[] = [
  {
    key: 'sale-84',
    kind: 'sale',
    id: 84,
    number: 'SL-2569-0084',
    date: '2026-09-10T10:15:00+07:00',
    patient: 'นายสมชาย ใจดี',
    hn: '2601080022',
    item: 'Manual Therapy',
    branchCode: 'BR01',
    quantity: '฿10,000',
    status: 'ปกติ',
    performedBy: 'กัญญา ใจดี',
    paymentMethod: 'โอนเงิน',
  },
  {
    key: 'sale-81',
    kind: 'sale',
    id: 81,
    number: 'SL-2569-0081',
    date: '2026-09-08T14:30:00+07:00',
    patient: 'นางวรรณา มั่นคง',
    hn: '2601080023',
    item: 'Exercise Therapy',
    branchCode: 'BR09',
    quantity: '฿1,500',
    status: 'ปกติ',
    performedBy: 'พิมพ์ชนก',
    paymentMethod: 'โอนเงิน',
  },
  {
    key: 'usage-31',
    kind: 'usage',
    id: 31,
    number: 'USE-2569-0031',
    date: '2026-09-17T09:00:00+07:00',
    patient: 'สมชาย ใจดี',
    hn: '2601080022',
    item: 'Manual Therapy',
    branchCode: 'BR01',
    quantity: '1 ครั้ง',
    status: 'ปกติ',
    performedBy: 'กภ. ศิริพร',
    paymentMethod: 'ใช้สิทธิ์คอร์ส',
  },
  {
    key: 'usage-42',
    kind: 'usage',
    id: 42,
    number: 'USE-2569-0042',
    date: '2026-09-10T09:00:00+07:00',
    patient: 'นายสมชาย ใจดี',
    hn: '2601080022',
    item: 'Manual Therapy',
    branchCode: 'BR01',
    quantity: '1 ครั้ง',
    status: 'ปกติ',
    performedBy: 'กภ. ศิริพร',
    paymentMethod: 'ใช้สิทธิ์คอร์ส',
  },
  {
    key: 'sale-76',
    kind: 'sale',
    id: 76,
    number: 'SL-2569-0076',
    date: '2026-09-05T11:20:00+07:00',
    patient: 'นายกิตติ วัฒนา',
    hn: '2601080018',
    item: 'Manual Therapy',
    branchCode: 'BR01',
    quantity: '฿2,000',
    status: 'ยกเลิก (Void)',
    performedBy: 'พิมพ์ชนก',
    paymentMethod: 'QR PromptPay',
    voidedAt: '2026-09-12T11:42:00+07:00',
    voidReason: 'ลงรายการผิด',
    voidNumber: 'VOID-2569-0014',
  },
  {
    key: 'usage-32',
    kind: 'usage',
    id: 32,
    number: 'USE-2569-0032',
    date: '2026-09-05T11:20:00+07:00',
    patient: 'นายกิตติ วัฒนา',
    hn: '2601080018',
    item: 'Manual Therapy',
    branchCode: 'BR01',
    quantity: '1 ครั้ง',
    status: 'ยกเลิก (Void)',
    performedBy: 'กภ. ศิริพร',
    paymentMethod: 'ใช้สิทธิ์คอร์ส',
    voidedAt: '2026-09-05T11:40:00+07:00',
    voidReason: 'ผู้ป่วยไม่ได้รับบริการ',
    voidNumber: 'VOID-2569-0012',
  },
]

/** Backend seam: replace only this adapter with an API implementation later. */
const transactionRepository: TransactionRepository = {
  async list() {
    return structuredClone(seedTransactions.filter((row) => row.status === 'ปกติ'))
  },
  async void(command) {
    const row = rows.value.find((item) => item.key === command.key)
    if (!row) throw new Error('ไม่พบรายการที่ต้องการยกเลิก')
    row.status = 'ยกเลิก (Void)'
    row.voidReason = command.reason
    row.voidedAt = new Date().toISOString()
    row.voidNumber = `VOID-${new Date().getFullYear() + 543}-${String(row.id).padStart(4, '0')}`
    return { ...row }
  },
}

const tab = ref<Tab>('sales')
const step = ref<Step>('list')
const rows = ref<Transaction[]>([])
const selected = ref<Transaction | null>(null)
const search = ref('')
const branchFilter = ref('')
const statusFilter = ref('')
const startDate = ref('')
const endDate = ref('')
const page = ref(1)
const loading = ref(false)
const submitting = ref(false)
const showCancelWarning = ref(false)
const cancelWarningBackButton = ref<HTMLButtonElement | null>(null)
const errorMessage = ref('')
const reasonType = ref('')
const otherReason = ref('')
const internalNote = ref('')
const expandedOriginal = ref<string[]>([])
const lastCancelledKey = ref('')
const showPostCancellationNotice = ref(false)
let postCancellationNoticeTimer: ReturnType<typeof setTimeout> | undefined
const branchNames = ref<Record<string, string>>({ BR01: 'แบริ่ง', BR09: 'พระราม 9' })
const branchOptions = ref<Array<{ code: string; name: string }>>([
  { code: 'BR01', name: 'แบริ่ง' },
  { code: 'BR09', name: 'พระราม 9' },
])
const cancellationActor = ref('')
const pageSize = computed(() => (tab.value === 'history' ? 5 : 10))

function formatShortDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)
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

function formatShortDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return `${new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)} ${new Intl.DateTimeFormat('th-TH', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)} น.`
}

function branchName(code: string) {
  return branchNames.value[code] ?? code
}

async function loadTransactions() {
  loading.value = true
  errorMessage.value = ''
  try {
    rows.value = await transactionRepository.list()
    if (selected.value) {
      selected.value = rows.value.find((row) => row.key === selected.value?.key) ?? selected.value
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการธุรกรรมไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

onMounted(loadTransactions)
onBeforeUnmount(() => clearTimeout(postCancellationNoticeTimer))

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
    .filter((row) => !startDate.value || new Date(row.date) >= new Date(startDate.value))
    .filter((row) => !endDate.value || new Date(row.date) <= new Date(`${endDate.value}T23:59:59`))
    .filter(
      (row) =>
        !statusFilter.value ||
        (statusFilter.value === 'void' ? row.status !== 'ปกติ' : row.status === 'ปกติ'),
    )
    .sort((left, right) => Date.parse(right.date) - Date.parse(left.date))
})
const hasCancellationHistory = computed(() =>
  rows.value.some((row) => row.status === 'ยกเลิก (Void)'),
)
const totalPages = computed(() => Math.max(1, Math.ceil(visibleRows.value.length / pageSize.value)))
const pagedRows = computed(() =>
  visibleRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const reason = computed(() =>
  reasonType.value === 'อื่น ๆ' ? otherReason.value.trim() : reasonType.value,
)
const canReview = computed(() =>
  reasonType.value === 'อื่น ๆ' ? otherReason.value.trim().length >= 10 : Boolean(reasonType.value),
)
watch([tab, search, branchFilter, statusFilter, startDate, endDate], () => {
  page.value = 1
})
watch(totalPages, (count) => {
  if (page.value > count) page.value = count
})

function changeTab(next: Tab) {
  showPostCancellationNotice.value = false
  clearTimeout(postCancellationNoticeTimer)
  tab.value = next
  step.value = 'list'
  selected.value = null
  statusFilter.value = ''
  search.value = ''
  branchFilter.value = ''
  startDate.value = ''
  endDate.value = ''
  expandedOriginal.value = []
}

async function openDetail(row: Transaction) {
  selected.value = row
  errorMessage.value = ''
  expandedOriginal.value = []
  step.value = tab.value === 'history' ? 'history-detail' : 'detail'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function startCancellation() {
  reasonType.value = 'ลงรายการหรือเลือกบริการผิด'
  otherReason.value = ''
  internalNote.value = ''
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
    const updated = await transactionRepository.void({
      key: row.key,
      reason: reason.value,
      actor: cancellationActor.value || 'พนักงานหน้าร้าน',
    })
    Object.assign(row, updated)
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
  const shouldShowNotice = step.value === 'success' && next === 'sales'
  showCancelWarning.value = false
  step.value = 'list'
  tab.value = next
  selected.value = null
  expandedOriginal.value = []
  showPostCancellationNotice.value = shouldShowNotice
  clearTimeout(postCancellationNoticeTimer)
  if (shouldShowNotice) {
    postCancellationNoticeTimer = setTimeout(() => {
      showPostCancellationNotice.value = false
    }, 5000)
  }
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
          <h1>
            {{
              tab === 'history' && !loading && !hasCancellationHistory
                ? 'ประวัติการยกเลิก'
                : 'ยกเลิกและ Void รายการ'
            }}
          </h1>
          <p v-if="tab === 'history' && !loading && !hasCancellationHistory">
            ตรวจสอบรายการที่ถูกยกเลิกและข้อมูลย้อนหลัง
          </p>
          <p v-else-if="tab === 'sales' && showPostCancellationNotice">
            รายการขายหลังดำเนินการยกเลิก
          </p>
          <p v-else>ค้นหา ตรวจสอบ และยกเลิกรายการที่บันทึกผิด โดยยังคงประวัติการทำรายการไว้</p>
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
        <div
          v-if="tab === 'sales' && showPostCancellationNotice"
          class="transaction-cancelled-notice"
          role="status"
        >
          <span aria-hidden="true">✓</span>
          <div>
            <strong>รายการถูกยกเลิกเรียบร้อยแล้ว</strong>
            <p>ระบบคงรายการไว้เพื่อการตรวจสอบ และไม่สามารถดำเนินการยกเลิกซ้ำได้</p>
          </div>
        </div>
        <section
          v-if="tab === 'history' && !loading && !hasCancellationHistory"
          class="transaction-history-empty"
        >
          <span aria-hidden="true">⌕</span>
          <h2>ยังไม่มีประวัติการยกเลิก</h2>
          <p>เมื่อมีการยกเลิกรายการ ประวัติและข้อมูลตรวจสอบย้อนหลังจะแสดงที่นี่</p>
          <button class="transaction-primary" type="button" @click="changeTab('sales')">
            ไปหน้ารายการ
          </button>
        </section>
        <section v-else class="transaction-list">
          <div class="transaction-filters">
            <label class="transaction-control transaction-search">
              <span>ค้นหา</span>
              <input
                v-model="search"
                type="search"
                aria-label="ค้นหารายการ"
                placeholder="เลขรายการ / ชื่อผู้ป่วย / HN"
              />
            </label>
            <div class="transaction-control transaction-select">
              <span>สาขา</span>
              <FilterDropdown
                v-model="branchFilter"
                label="สาขา"
                default-label="ทุกสาขา"
                :options="
                  branchOptions.map((branch) => ({ value: branch.code, label: branch.name }))
                "
              />
            </div>
            <div class="transaction-control transaction-select">
              <span>สถานะ</span>
              <FilterDropdown
                v-model="statusFilter"
                label="สถานะ"
                default-label="ทุกสถานะ"
                :options="
                  tab === 'history'
                    ? [{ value: 'void', label: 'ยกเลิกแล้ว' }]
                    : [
                        {
                          value: 'normal',
                          label: tab === 'sales' ? 'ชำระแล้ว' : 'ใช้บริการแล้ว',
                        },
                        { value: 'void', label: 'ยกเลิกแล้ว' },
                      ]
                "
              />
            </div>
            <label class="transaction-control transaction-date">
              <span>วันที่เริ่มต้น</span>
              <ThaiDatePicker v-model="startDate" show-calendar-icon numeric-month hide-caret />
            </label>
            <label class="transaction-control transaction-date">
              <span>วันที่สิ้นสุด</span>
              <ThaiDatePicker v-model="endDate" show-calendar-icon numeric-month hide-caret />
            </label>
          </div>
          <p v-if="tab === 'usage'" class="transaction-filter-note">
            ค่าเริ่มต้นแสดงทั้งหมด • ยกเลิกการเลือกเพื่อกลับมาแสดงทั้งหมด
          </p>
          <div v-if="loading" class="transaction-empty" role="status">
            กำลังโหลดรายการธุรกรรม...
          </div>
          <div v-else-if="!visibleRows.length" class="transaction-empty">
            {{ tab === 'history' ? 'ยังไม่มีประวัติการยกเลิก' : 'ไม่พบรายการที่ตรงกับเงื่อนไข' }}
          </div>
          <div v-else-if="false" class="transaction-history-list">
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
                  <th>วันที่ / เลขรายการ</th>
                  <th>ผู้ป่วย / HN</th>
                  <th>รายละเอียด</th>
                  <th>สาขา</th>
                  <th>ยอดเงิน</th>
                  <th>สถานะ</th>
                  <th>จัดการ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedRows" :key="row.key">
                  <td>
                    <span>{{ formatShortDate(tab === 'history' ? row.voidedAt : row.date) }}</span
                    ><small>{{
                      tab === 'history' ? row.voidNumber || row.number : row.number
                    }}</small>
                  </td>
                  <td>
                    <strong>{{ row.patient }}</strong
                    ><small>HN{{ row.hn }}</small>
                  </td>
                  <td v-if="tab === 'history'">
                    <span>อ้างอิง {{ row.number }}</span
                    ><small>{{ row.voidReason || 'ไม่ระบุเหตุผล' }}</small>
                  </td>
                  <td v-else>
                    <span>{{ row.item }}</span
                    ><small>{{
                      row.kind === 'sale'
                        ? row.id === 84
                          ? 'คอร์ส 10 ครั้ง'
                          : 'รายครั้ง'
                        : row.item === 'Manual Therapy'
                          ? 'ใช้สิทธิ์ 1 ครั้ง'
                          : 'รายครั้ง'
                    }}</small>
                  </td>
                  <td>{{ branchName(row.branchCode) }}</td>
                  <td>{{ row.kind === 'sale' ? row.quantity : '—' }}</td>
                  <td>
                    <span
                      class="transaction-status"
                      :class="{
                        void: row.status !== 'ปกติ',
                        usage: tab === 'usage' && row.status === 'ปกติ',
                      }"
                      >{{
                        row.status === 'ปกติ'
                          ? tab === 'sales'
                            ? 'ชำระแล้ว'
                            : 'ใช้บริการแล้ว'
                          : 'ยกเลิกแล้ว'
                      }}</span
                    >
                  </td>
                  <td>
                    <button
                      class="transaction-primary transaction-row-button"
                      type="button"
                      :disabled="tab !== 'history' && row.status !== 'ปกติ'"
                      @click="openDetail(row)"
                    >
                      {{
                        tab === 'history'
                          ? 'ดูรายละเอียด'
                          : row.status === 'ปกติ'
                            ? 'ตรวจสอบ'
                            : 'ยกเลิกแล้ว'
                      }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="tab === 'usage' && visibleRows.length" class="transaction-list-note">
            แสดงรายการล่าสุดก่อน • รายการที่ยกเลิกแล้วไม่สามารถยกเลิกซ้ำได้
          </p>
          <nav
            v-if="totalPages > 1"
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
          @click="
            step === 'history-detail'
              ? backToList('history')
              : step === 'confirm'
                ? (step = 'reason')
                : step === 'reason'
                  ? (step = 'detail')
                  : backToList()
          "
        >
          ←
          {{
            step === 'history-detail'
              ? 'กลับประวัติการยกเลิก'
              : step === 'reason'
                ? 'กลับหน้าตรวจสอบ'
                : step === 'confirm'
                  ? 'กลับไปแก้ไข'
                  : 'กลับหน้ารายการ'
          }}
        </button>

        <template v-if="step === 'history-detail'">
          <section class="transaction-history-detail">
            <header>
              <div>
                <h1>รายละเอียดประวัติการยกเลิก</h1>
                <p>ข้อมูลแบบอ่านอย่างเดียวสำหรับตรวจสอบย้อนหลัง</p>
              </div>
              <span>ยกเลิกแล้ว</span>
            </header>
            <div class="transaction-audit-card">
              <section>
                <h2>ข้อมูลรายการต้นทาง</h2>
                <dl class="transaction-audit-source">
                  <div>
                    <dt>เลขที่รายการ</dt>
                    <dd>{{ selected.number }}</dd>
                  </div>
                  <div>
                    <dt>ประเภท</dt>
                    <dd>{{ selected.kind === 'sale' ? 'รายการขายคอร์ส' : 'รายการใช้บริการ' }}</dd>
                  </div>
                  <div>
                    <dt>รายละเอียด</dt>
                    <dd>
                      {{ selected.item }} ·
                      {{ selected.kind === 'sale' ? 'คอร์ส 10 ครั้ง' : 'ใช้สิทธิ์คอร์ส' }}
                    </dd>
                  </div>
                  <div>
                    <dt>ยอดเงิน</dt>
                    <dd>{{ selected.kind === 'sale' ? selected.quantity : '—' }}</dd>
                  </div>
                  <div>
                    <dt>สาขา</dt>
                    <dd>{{ branchName(selected.branchCode) }}</dd>
                  </div>
                </dl>
              </section>
              <section>
                <h2>ข้อมูลผู้ป่วย</h2>
                <dl class="transaction-audit-patient">
                  <div>
                    <dt>ชื่อผู้ป่วย</dt>
                    <dd>{{ selected.patient }}</dd>
                  </div>
                  <div>
                    <dt>HN</dt>
                    <dd>HN{{ selected.hn }}</dd>
                  </div>
                  <div>
                    <dt>เบอร์โทรศัพท์</dt>
                    <dd>089-123-4567</dd>
                  </div>
                </dl>
              </section>
              <section>
                <h2>ข้อมูลการยกเลิก</h2>
                <dl class="transaction-audit-cancellation">
                  <div>
                    <dt>เลขที่อ้างอิง</dt>
                    <dd>{{ selected.voidNumber }}</dd>
                  </div>
                  <div>
                    <dt>เหตุผล</dt>
                    <dd>{{ selected.voidReason }}</dd>
                  </div>
                  <div>
                    <dt>ผู้ดำเนินการ</dt>
                    <dd>{{ selected.key === lastCancelledKey ? cancellationActor : 'ไม่ระบุ' }}</dd>
                  </div>
                  <div>
                    <dt>วันเวลา</dt>
                    <dd>{{ formatShortDateTime(selected.voidedAt) }}</dd>
                  </div>
                </dl>
              </section>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'detail'">
          <div class="transaction-progress" aria-label="ขั้นตอนการยกเลิกรายการ">
            <div class="active"><b>1</b><span>ตรวจสอบรายการ</span></div>
            <i></i>
            <div><b>2</b><span>ระบุเหตุผล</span></div>
            <i></i>
            <div><b>3</b><span>ยืนยัน</span></div>
          </div>
          <header class="transaction-detail-heading">
            <div>
              <h1>ตรวจสอบรายการที่ต้องการยกเลิก</h1>
              <p>ตรวจสอบข้อมูลให้ถูกต้องก่อนระบุเหตุผล</p>
            </div>
            <span
              class="transaction-status"
              :class="{
                void: selected.status !== 'ปกติ',
                usage: selected.kind === 'usage' && selected.status === 'ปกติ',
              }"
              >{{
                selected.status === 'ปกติ'
                  ? selected.kind === 'sale'
                    ? 'ชำระแล้ว'
                    : 'ใช้บริการแล้ว'
                  : 'ยกเลิกแล้ว'
              }}</span
            >
          </header>
          <div class="transaction-review-layout">
            <section class="transaction-review-card">
              <h2>ข้อมูลรายการ</h2>
              <dl class="transaction-review-grid">
                <div>
                  <dt>เลขที่รายการ</dt>
                  <dd>{{ selected.number }}</dd>
                </div>
                <div>
                  <dt>วันที่ทำรายการ</dt>
                  <dd>{{ formatDateTime(selected.date) }}</dd>
                </div>
                <div>
                  <dt>ประเภท</dt>
                  <dd>{{ selected.kind === 'sale' ? 'รายการขายคอร์ส' : 'รายการใช้บริการ' }}</dd>
                </div>
                <div>
                  <dt>สาขา</dt>
                  <dd>{{ branchName(selected.branchCode) }}</dd>
                </div>
                <div>
                  <dt>ผู้บันทึก</dt>
                  <dd>{{ selected.performedBy }}</dd>
                </div>
                <div>
                  <dt>ช่องทางชำระ</dt>
                  <dd>{{ selected.paymentMethod }}</dd>
                </div>
              </dl>
            </section>
            <aside class="transaction-review-patient">
              <b>ผู้ป่วย</b><strong>{{ selected.patient }}</strong
              ><span>HN{{ selected.hn }}</span
              ><small>☎ 089-123-4567</small>
            </aside>
          </div>
          <section class="transaction-service-detail">
            <h2>รายละเอียดบริการ</h2>
            <div>
              <strong>{{ selected.item }}</strong
              ><span>{{
                selected.kind === 'sale' && selected.id === 84
                  ? 'คอร์ส 10 ครั้ง'
                  : 'ใช้สิทธิ์ 1 ครั้ง'
              }}</span>
            </div>
            <dl>
              <div>
                <dt>จำนวน</dt>
                <dd>1</dd>
              </div>
              <div>
                <dt>ยอดสุทธิ</dt>
                <dd>{{ selected.kind === 'sale' ? selected.quantity : '—' }}</dd>
              </div>
            </dl>
          </section>
          <div class="transaction-review-actions">
            <button class="transaction-secondary" type="button" @click="backToList()">
              ย้อนกลับ
            </button>
            <button
              v-if="selected.status === 'ปกติ'"
              class="transaction-primary"
              type="button"
              @click="startCancellation"
            >
              ดำเนินการยกเลิก
            </button>
          </div>
        </template>

        <template v-else-if="step === 'reason'">
          <div class="transaction-progress reason-step" aria-label="ขั้นตอนการยกเลิกรายการ">
            <div class="active"><b>1</b><span>ตรวจสอบรายการ</span></div>
            <i class="complete"></i>
            <div class="active"><b>2</b><span>ระบุเหตุผล</span></div>
            <i></i>
            <div><b>3</b><span>ยืนยัน</span></div>
          </div>
          <section class="transaction-reason-flow">
            <header>
              <h1>ระบุเหตุผลการยกเลิก</h1>
              <p>เหตุผลเป็นข้อมูลบังคับและจะแสดงในประวัติการยกเลิก</p>
            </header>
            <div class="transaction-reason-panel">
              <h2>เลือกเหตุผล *</h2>
              <label
                v-for="option in [
                  'ลงรายการหรือเลือกบริการผิด',
                  'บันทึกรายการซ้ำ',
                  'ผู้ป่วยขอยกเลิกรายการ',
                  'การชำระเงินไม่สำเร็จหรือยอดไม่ถูกต้อง',
                  'อื่น ๆ',
                ]"
                :key="option"
                class="transaction-reason-option"
                :class="{ selected: reasonType === option }"
              >
                <input v-model="reasonType" type="radio" name="void-reason" :value="option" />
                <span>{{ option }}</span>
              </label>
              <div v-if="reasonType === 'อื่น ๆ'" class="transaction-other-reason">
                <label for="transaction-other-reason">รายละเอียดเหตุผล *</label>
                <textarea
                  id="transaction-other-reason"
                  v-model="otherReason"
                  rows="3"
                  placeholder="กรุณาระบุรายละเอียดเพื่อให้ตรวจสอบย้อนหลังได้"
                />
                <p v-if="otherReason.trim().length < 10" class="transaction-validation">
                  กรุณาระบุรายละเอียดอย่างน้อย 10 ตัวอักษร
                </p>
              </div>
              <label class="transaction-note-field" for="transaction-note"
                >หมายเหตุภายใน (ไม่บังคับ)</label
              >
              <textarea
                id="transaction-note"
                v-model="internalNote"
                rows="3"
                placeholder="ข้อมูลเพิ่มเติมสำหรับเจ้าหน้าที่"
              />
            </div>
            <div class="transaction-reason-actions">
              <button class="transaction-secondary" type="button" @click="step = 'detail'">
                ย้อนกลับ
              </button>
              <button
                class="transaction-primary"
                type="button"
                :disabled="!canReview"
                @click="step = 'confirm'"
              >
                ตรวจสอบก่อนยืนยัน
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'confirm'">
          <div class="transaction-progress confirm-step" aria-label="ขั้นตอนการยกเลิกรายการ">
            <div class="active"><b>1</b><span>ตรวจสอบรายการ</span></div>
            <i class="complete"></i>
            <div class="active"><b>2</b><span>ระบุเหตุผล</span></div>
            <i class="complete"></i>
            <div class="active"><b>3</b><span>ยืนยัน</span></div>
          </div>
          <section class="transaction-confirm-flow">
            <header>
              <h1>ตรวจสอบก่อนยืนยัน</h1>
              <p>ตรวจสอบรายการและเหตุผลให้ถูกต้องก่อนเปิดหน้าต่างยืนยัน</p>
            </header>
            <div class="transaction-confirm-summary">
              <h2>สรุปรายการ</h2>
              <dl>
                <div>
                  <dt>เลขที่รายการ</dt>
                  <dd>{{ selected.number }}</dd>
                </div>
                <div>
                  <dt>ผู้ป่วย</dt>
                  <dd>{{ selected.patient }} · HN{{ selected.hn }}</dd>
                </div>
                <div>
                  <dt>รายละเอียด</dt>
                  <dd>
                    {{ selected.item }} ·
                    {{ selected.kind === 'sale' ? 'คอร์ส 10 ครั้ง' : 'ใช้สิทธิ์คอร์ส' }}
                  </dd>
                </div>
                <div>
                  <dt>ยอดเงิน</dt>
                  <dd>{{ selected.kind === 'sale' ? selected.quantity : '—' }}</dd>
                </div>
                <div>
                  <dt>เหตุผล</dt>
                  <dd>{{ reason }}</dd>
                </div>
                <div>
                  <dt>ผู้ดำเนินการ</dt>
                  <dd>{{ selected.performedBy }}</dd>
                </div>
              </dl>
            </div>
            <div class="transaction-confirm-actions">
              <button class="transaction-secondary" type="button" @click="step = 'reason'">
                ย้อนกลับ
              </button>
              <button
                class="transaction-danger"
                type="button"
                :disabled="submitting"
                @click="openCancelWarning"
              >
                {{ submitting ? 'กำลังยกเลิก...' : 'ยืนยันการยกเลิก' }}
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'success'">
          <section class="transaction-success-result">
            <span class="transaction-success-icon" aria-hidden="true">✓</span>
            <h1>ยกเลิกรายการสำเร็จ</h1>
            <p>รายการถูกบันทึกในประวัติเรียบร้อยแล้ว</p>
            <dl>
              <div>
                <dt>สถานะ</dt>
                <dd>ยกเลิกแล้ว</dd>
              </div>
              <div>
                <dt>เลขที่อ้างอิง</dt>
                <dd>{{ selected.voidNumber }}</dd>
              </div>
              <div>
                <dt>ผู้ดำเนินการ</dt>
                <dd>{{ cancellationActor }}</dd>
              </div>
              <div>
                <dt>วันเวลา</dt>
                <dd>{{ formatShortDateTime(selected.voidedAt) }}</dd>
              </div>
            </dl>
          </section>
          <div class="transaction-success-actions">
            <button class="transaction-secondary" type="button" @click="backToList('history')">
              ดูประวัติการยกเลิก
            </button>
            <button class="transaction-primary" type="button" @click="backToList()">
              กลับหน้ารายการ
            </button>
          </div>
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
        <h2 id="transaction-modal-title">ยืนยันการยกเลิกรายการ</h2>
        <p id="transaction-modal-description">
          ต้องการยกเลิกรายการ {{ selected.number }} ใช่หรือไม่?
        </p>
        <div class="transaction-modal-summary">
          <div>
            <span>ยอดเงิน</span>
            <strong>{{ selected.kind === 'sale' ? selected.quantity : '—' }}</strong>
          </div>
          <div>
            <span>เหตุผล</span>
            <strong>{{ reason }}</strong>
          </div>
        </div>
        <div class="transaction-actions">
          <button
            ref="cancelWarningBackButton"
            class="transaction-secondary"
            type="button"
            :disabled="submitting"
            @click="closeCancelWarning"
          >
            แก้ไข
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
