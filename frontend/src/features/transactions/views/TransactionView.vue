<script setup lang="ts">
import { salesApi, courseApi } from '@/api/client'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { vModalFocus } from '@/features/appointments/modalFocus'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import AppointmentDateFilter from '@/features/appointments/components/AppointmentDateFilter.vue'

type Tab = 'sales' | 'usage' | 'history'
type Step = 'list' | 'detail' | 'history-detail' | 'success'
type Transaction = {
  key: string
  kind: 'sale' | 'usage'
  id: number
  movementId?: number | null
  courseId?: number
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
}

type VoidTransactionCommand = { key: string; reason: string }
interface TransactionRepository {
  list(): Promise<Transaction[]>
  void(command: VoidTransactionCommand): Promise<Transaction>
}

const transactionRepository: TransactionRepository = {
  async list() {
    const first = await salesApi.list({ page: 0, size: 100, receiptsOnly: true })
    const sales = [...first.items]
    for (let page = 1; page < first.totalPages; page++)
      sales.push(...(await salesApi.list({ page, size: 100, receiptsOnly: true })).items)
    const usages = await courseApi.usageTransactions()
    return [
      ...sales.map((row): Transaction => ({
        key: `sale-${row.id}`,
        kind: 'sale',
        id: row.id,
        number: row.receiptNo,
        date: row.soldAt,
        patient: row.patient.fullName,
        hn: row.patient.hn,
        item: row.itemDescription || '—',
        branchCode: row.branchCode,
        quantity: `฿${Number(row.total).toLocaleString('th-TH')}`,
        status: row.status === 'VOIDED' ? 'ยกเลิก (Void)' : 'ปกติ',
        performedBy: row.salesperson?.fullName || '—',
        paymentMethod: '—',
        voidedAt: row.voidedAt,
        voidReason: row.voidReason,
      })),
      ...usages.map((row): Transaction => ({
        key: `usage-${row.id}`,
        movementId: row.id,
        courseId: row.coursePurchaseId,
        kind: 'usage',
        id: row.id ?? 0,
        number: String(row.id),
        date: row.usedAt,
        patient: row.patientName,
        hn: row.patientHn,
        item: row.courseName,
        branchCode: row.branchCode,
        quantity: '1 ครั้ง',
        status: row.voidedAt ? 'ยกเลิก (Void)' : 'ปกติ',
        performedBy: row.therapistName || '—',
        paymentMethod: '—',
        voidedAt: row.voidedAt || undefined,
        voidReason: row.voidReason || undefined,
      })),
    ]
  },
  async void(command) {
    const row = rows.value.find((item) => item.key === command.key)
    if (!row) throw new Error('ไม่พบรายการที่ต้องการยกเลิก')
    if (row.status !== 'ปกติ') throw new Error('รายการนี้ถูกยกเลิกแล้ว')
    if (row.kind === 'usage') {
      if (row.movementId == null)
        throw new Error('รายการนี้ไม่มี ledger อ้างอิง กรุณาให้ผู้ดูแลตรวจสอบ')
      await courseApi.reverse(row.movementId, command.reason)
      const saved = (await courseApi.usageTransactions()).find((item) => item.id === row.movementId)
      if (!saved?.voidedAt)
        throw new Error('ส่งคำขอยกเลิกแล้ว แต่ยังยืนยันผลไม่ได้ กรุณาโหลดรายการใหม่ก่อนทำซ้ำ')
      return {
        ...row,
        status: 'ยกเลิก (Void)',
        voidedAt: saved.voidedAt,
        voidReason: saved.voidReason || undefined,
      }
    }
    const saved = await salesApi.reverse(row.id, command.reason)
    return {
      ...row,
      status: 'ยกเลิก (Void)',
      voidedAt: saved.voidedAt,
      voidReason: saved.voidReason,
    } as Transaction
  },
}
const tab = ref<Tab>('sales')
const route = useRoute()
const router = useRouter()
const step = ref<Step>('list')
const rows = ref<Transaction[]>([])
const selected = ref<Transaction | null>(null)
const search = ref('')
const statusFilter = ref<'all' | 'normal' | 'voided'>('all')
const startDate = ref('')
const endDate = ref('')
const page = ref(1)
const sortOrder = ref<'oldest' | 'newest'>('newest')
function toggleSortOrder() {
  sortOrder.value = sortOrder.value === 'oldest' ? 'newest' : 'oldest'
  page.value = 1
}
const loading = ref(false)
const submitting = ref(false)
const showCancelWarning = ref(false)
const showCancellationInfo = ref(false)
const cancelWarningBackButton = ref<HTMLButtonElement | null>(null)
const errorMessage = ref('')
const loadError = ref('')
const reasonType = ref('')
const otherReason = ref('')
const lastCancelledKey = ref('')
const showPostCancellationNotice = ref(false)
let postCancellationNoticeTimer: ReturnType<typeof setTimeout> | undefined
const cancellationActor = ref('')
const pageSize = computed(() => (tab.value === 'history' ? 5 : 10))

function hasRecordValue(value?: string) {
  return Boolean(value && value !== '—' && value !== '-')
}

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

async function loadTransactions() {
  loading.value = true
  loadError.value = ''
  errorMessage.value = ''
  try {
    rows.value = await transactionRepository.list()
    if (selected.value) {
      selected.value = rows.value.find((row) => row.key === selected.value?.key) ?? selected.value
    } else {
      const { usage, sale, ...query } = route.query
      const key = typeof usage === 'string' ? usage : sale
      if (typeof key === 'string') {
        const kind = typeof usage === 'string' ? 'usage' : 'sale'
        tab.value = kind === 'usage' ? 'usage' : 'sales'
        const row = rows.value.find(
          (item) =>
            item.kind === kind && item.key === key && item.branchCode === headerBranch.value,
        )
        if (row) await openDetail(row)
        await router.replace({ query })
      }
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : 'โหลดรายการธุรกรรมไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

onMounted(loadTransactions)
onBeforeUnmount(() => clearTimeout(postCancellationNoticeTimer))

const headerBranch = computed(() => {
  try {
    const user = JSON.parse(localStorage.getItem('authUser') || 'null') as {
      role?: string
      branchCode?: string
    } | null
    return user?.role?.toLowerCase() === 'manager'
      ? selectedManagerBranch()?.code
      : user?.branchCode
  } catch {
    return undefined
  }
})
watch(headerBranch, () => {
  backToList()
  page.value = 1
})
const visibleRows = computed(() => {
  const term = search.value.trim().toLocaleLowerCase()
  return rows.value
    .filter((row) => Boolean(headerBranch.value) && row.branchCode === headerBranch.value)
    .filter((row) =>
      tab.value === 'history'
        ? row.status === 'ยกเลิก (Void)'
        : row.kind === (tab.value === 'sales' ? 'sale' : 'usage'),
    )
    .filter(
      (row) =>
        statusFilter.value === 'all' ||
        (statusFilter.value === 'normal'
          ? row.status === 'ปกติ'
          : row.status === 'ยกเลิก (Void)'),
    )
    .filter(
      (row) =>
        !term ||
        `${row.number} ${row.patient} ${row.hn} ${row.item}`.toLocaleLowerCase().includes(term),
    )
    .filter((row) => {
      const value = tab.value === 'history' ? row.voidedAt : row.date
      if (!value) return !startDate.value && !endDate.value
      const day = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Bangkok',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(new Date(value))
      return (
        (!startDate.value || day >= startDate.value) && (!endDate.value || day <= endDate.value)
      )
    })
    .sort((left, right) => {
      const timestamp = (row: Transaction) => {
        const time = Date.parse((tab.value === 'history' ? row.voidedAt : row.date) || '')
        return Number.isFinite(time) ? time : 0
      }
      const direction = sortOrder.value === 'oldest' ? 1 : -1
      return direction * (timestamp(left) - timestamp(right)) || left.key.localeCompare(right.key)
    })
})
const hasCancellationHistory = computed(() => rows.value.some((row) => row.status !== 'ปกติ'))
const totalPages = computed(() => Math.max(1, Math.ceil(visibleRows.value.length / pageSize.value)))
const pagedRows = computed(() =>
  visibleRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const reason = computed(() =>
  reasonType.value === 'อื่น ๆ' ? otherReason.value.trim() : reasonType.value,
)
const canReview = computed(() =>
  reasonType.value === 'อื่น ๆ' ? Boolean(otherReason.value.trim()) : Boolean(reasonType.value),
)
watch([tab, search, statusFilter, startDate, endDate], () => {
  page.value = 1
})
watch(totalPages, (count) => {
  if (page.value > count) page.value = count
})

function applyDateRange(from: string, to: string) {
  startDate.value = from
  endDate.value = to
}

function changeTab(next: Tab) {
  showPostCancellationNotice.value = false
  clearTimeout(postCancellationNoticeTimer)
  tab.value = next
  step.value = 'list'
  selected.value = null
  search.value = ''
  statusFilter.value = 'all'
}

async function openDetail(row: Transaction) {
  selected.value = row
  errorMessage.value = ''
  reasonType.value = ''
  otherReason.value = ''
  step.value = tab.value === 'history' || row.status !== 'ปกติ' ? 'history-detail' : 'detail'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function openCancelWarning() {
  if (!selected.value || !canReview.value || submitting.value) return
  showCancellationInfo.value = false
  showCancelWarning.value = true
  await nextTick()
  cancelWarningBackButton.value?.focus()
}

function closeCancelWarning() {
  if (!submitting.value) {
    showCancelWarning.value = false
    showCancellationInfo.value = false
  }
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
    })
    Object.assign(row, updated)
    const index = rows.value.findIndex((item) => item.key === updated.key)
    if (index >= 0) rows.value[index] = updated
    lastCancelledKey.value = row.key
    cancellationActor.value = '-'
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
  showPostCancellationNotice.value = shouldShowNotice
  clearTimeout(postCancellationNoticeTimer)
  if (shouldShowNotice) {
    postCancellationNoticeTimer = setTimeout(() => {
      showPostCancellationNotice.value = false
    }, 5000)
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
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
                : 'รายการธุรกรรม'
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
        <div class="transaction-workspace">
          <nav class="transaction-tabs" aria-label="ประเภทของธุรกรรม">
            <button
              type="button"
              :class="{ active: tab === 'sales' }"
              :aria-pressed="tab === 'sales'"
              @click="changeTab('sales')"
            >
              รายการขาย
            </button>
            <button
              type="button"
              :class="{ active: tab === 'usage' }"
              :aria-pressed="tab === 'usage'"
              @click="changeTab('usage')"
            >
              รายการใช้บริการ
            </button>
            <button
              type="button"
              :class="{ active: tab === 'history' }"
              :aria-pressed="tab === 'history'"
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
          <section class="transaction-list">
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
              <div class="transaction-date-filter">
                <span>{{
                  tab === 'history'
                    ? 'วันที่ยกเลิก'
                    : tab === 'usage'
                      ? 'วันที่ใช้สิทธิ์'
                      : 'วันที่ขาย'
                }}</span>
                <AppointmentDateFilter :from="startDate" :to="endDate" @apply="applyDateRange" />
              </div>
            </div>
            <div class="transaction-results-heading">
              <h2>
                {{
                  tab === 'sales'
                    ? 'รายการขายทั้งหมด'
                    : tab === 'usage'
                      ? 'รายการใช้สิทธิ์คอร์ส'
                      : 'ประวัติการยกเลิก'
                }}
              </h2>
              <div class="transaction-summary-actions">
                <button
                  class="transaction-sort"
                  type="button"
                  @click="toggleSortOrder"
                  :title="
                    sortOrder === 'oldest'
                      ? 'เก่าไปใหม่ — กดเพื่อเรียงใหม่ไปเก่า'
                      : 'ใหม่ไปเก่า — กดเพื่อเรียงเก่าไปใหม่'
                  "
                  :aria-label="
                    sortOrder === 'oldest'
                      ? 'เรียงเก่าไปใหม่ กดเพื่อสลับเป็นใหม่ไปเก่า'
                      : 'เรียงใหม่ไปเก่า กดเพื่อสลับเป็นเก่าไปใหม่'
                  "
                >
                  {{ sortOrder === 'newest' ? 'ล่าสุด↓' : 'ล่าสุด↑' }}
                </button>
                <span role="status">{{
                  loading
                    ? 'กำลังโหลด…'
                    : loadError
                      ? 'โหลดไม่สำเร็จ'
                      : 'ทั้งหมด ' + visibleRows.length.toLocaleString('th-TH') + ' รายการ'
                }}</span>
              </div>
            </div>
            <div v-if="loading" class="transaction-empty" role="status">
              กำลังโหลดรายการธุรกรรม...
            </div>
            <div v-else-if="loadError" class="transaction-empty" role="alert">
              <p>โหลดรายการไม่สำเร็จ กรุณาลองใหม่</p>
              <button class="transaction-secondary" type="button" @click="loadTransactions">
                ลองใหม่
              </button>
            </div>
            <div v-else-if="!visibleRows.length" class="transaction-empty">
              {{ tab === 'history' ? 'ยังไม่มีประวัติการยกเลิก' : 'ไม่พบรายการ' }}
            </div>
            <div v-else class="transaction-table-wrap">
              <table class="transaction-table">
                <thead>
                  <tr>
                    <th
                      scope="col"
                      :aria-sort="sortOrder === 'oldest' ? 'ascending' : 'descending'"
                    >
                      {{ tab === 'usage' ? 'วันที่' : 'วันที่ / เลขรายการ' }}
                    </th>
                    <th>ผู้ป่วย / HN</th>
                    <th>รายละเอียด</th>

                    <th>
                      {{
                        tab === 'usage'
                          ? 'สิทธิ์ที่ใช้'
                          : tab === 'history'
                            ? 'ยอดเงิน / สิทธิ์'
                            : 'ยอดเงิน'
                      }}
                    </th>
                    <th>
                      <FilterDropdown
                        v-model="statusFilter"
                        class="transaction-status-filter"
                        :options="[
                          { value: 'normal', label: 'ปกติ' },
                          { value: 'voided', label: 'ยกเลิกแล้ว' },
                        ]"
                        default-value="all"
                        default-label="ทั้งหมด"
                        trigger-label="สถานะ"
                        label="กรองรายการตามสถานะ"
                        :clear-on-reselect="false"
                        :menu-min-width="140"
                      />
                    </th>
                    <th>จัดการ</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in pagedRows" :key="row.key">
                    <td>
                      <span>{{ formatShortDate(tab === 'history' ? row.voidedAt : row.date) }}</span
                      ><small v-if="row.kind === 'sale'">{{ row.number }}</small>
                    </td>
                    <td>
                      <strong>{{ row.patient }}</strong
                      ><small>HN {{ row.hn }}</small>
                    </td>
                    <td v-if="tab === 'history'">
                      <span v-if="row.kind === 'sale'">อ้างอิง {{ row.number }}</span>
                      <span v-else>{{ row.item }}</span
                      ><small>{{ row.voidReason || 'ไม่ระบุเหตุผล' }}</small>
                    </td>
                    <td v-else>
                      <span>{{ row.item }}</span
                      ><small>{{ row.kind === 'sale' ? 'รายการขาย' : 'ใช้สิทธิ์คอร์ส' }}</small>
                    </td>

                    <td>{{ row.quantity }}</td>
                    <td>
                      <span
                        class="transaction-status"
                        :class="{ void: row.status !== 'ปกติ', usage: row.status === 'ปกติ' }"
                      >{{
                        row.status === 'ปกติ'
                          ? tab === 'sales'
                            ? 'ปกติ'
                            : 'ใช้บริการแล้ว'
                          : 'ยกเลิกแล้ว'
                      }}</span>
                    </td>
                    <td>
                      <button
                        class="transaction-primary transaction-row-button"
                        type="button"
                        @click="openDetail(row)"
                      >
                        {{ tab !== 'history' && row.status === 'ปกติ' ? 'ยกเลิก' : 'รายละเอียด' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="tab === 'usage' && visibleRows.length" class="transaction-list-note"></p>
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
        </div>
      </template>

      <template v-else-if="selected">
        <button
          class="transaction-back"
          type="button"
          @click="backToList(step === 'history-detail' ? 'history' : tab)"
        >
          ←
          {{ step === 'history-detail' ? 'กลับ' : 'กลับหน้ารายการ' }}
        </button>

        <template v-if="step === 'history-detail'">
          <section class="transaction-history-detail">
            <header>
              <div>
                <h1>ประวัติการยกเลิก</h1>
              </div>
              <RouterLink
                v-if="selected.kind === 'sale'"
                class="transaction-receipt-link"
                :to="{ name: 'staff-receipt', params: { id: selected.id } }"
                >ดูใบเสร็จเดิม</RouterLink
              >
            </header>
            <div class="transaction-audit-card">
              <section>
                <h2>ข้อมูลรายการต้นทาง</h2>
                <dl class="transaction-audit-source">
                  <div v-if="selected.kind === 'sale'">
                    <dt>เลขที่รายการ</dt>
                    <dd>{{ selected.number }}</dd>
                  </div>
                  <div>
                    <dt>ประเภท</dt>
                    <dd>{{ selected.kind === 'sale' ? 'รายการขาย' : 'รายการใช้บริการ' }}</dd>
                  </div>
                  <div>
                    <dt>รายละเอียด</dt>
                    <dd>
                      {{ selected.item }} ·
                      {{ selected.kind === 'sale' ? 'รายการขาย' : 'ใช้สิทธิ์คอร์ส' }}
                    </dd>
                  </div>
                  <div>
                    <dt>ยอดเงิน</dt>
                    <dd>{{ selected.quantity }}</dd>
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
                    <dd>HN {{ selected.hn }}</dd>
                  </div>
                  <div>
                    <dt>เบอร์โทรศัพท์</dt>
                    <dd>-</dd>
                  </div>
                </dl>
              </section>
              <section>
                <h2>ข้อมูลการยกเลิก</h2>
                <dl class="transaction-audit-cancellation">
                  <div v-if="selected.kind === 'sale'">
                    <dt>เลขที่อ้างอิง</dt>
                    <dd>{{ selected.number }}</dd>
                  </div>
                  <div>
                    <dt>เหตุผล</dt>
                    <dd>{{ selected.voidReason }}</dd>
                  </div>
                  <div>
                    <dt>ผู้ดำเนินการ</dt>
                    <dd>{{ selected.key === lastCancelledKey ? cancellationActor : '-' }}</dd>
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
          <header class="transaction-detail-heading">
            <div>
              <h1>รายละเอียดรายการ</h1>
              <p>ตรวจสอบข้อมูลให้ถูกต้องก่อนระบุเหตุผล</p>
            </div>
            <div class="transaction-detail-actions">
              <RouterLink
                v-if="selected.kind === 'sale'"
                class="transaction-receipt-link"
                :to="{
                  name: 'staff-receipt',
                  params: { id: selected.id },
                  query: { from: 'transactions' },
                }"
              >
                พิมพ์ใบเสร็จ
              </RouterLink>
              <RouterLink
                v-if="selected.kind === 'usage' && selected.courseId"
                class="transaction-receipt-link"
                :to="{
                  name: 'staff-course-prints',
                  params: { courseId: selected.courseId },
                  query: { usage: selected.key },
                }"
              >
                ประวัติการพิมพ์
              </RouterLink>
            <span
              class="transaction-status"
              :class="{
                void: selected.status !== 'ปกติ',
                usage: selected.kind === 'usage' && selected.status === 'ปกติ',
              }"
              >{{
                selected.status === 'ปกติ'
                  ? selected.kind === 'sale'
                    ? 'ปกติ'
                    : 'ใช้บริการแล้ว'
                  : 'ยกเลิกแล้ว'
              }}</span
            >
            </div>
          </header>
          <div class="transaction-review-layout">
            <section class="transaction-review-card">
              <h2>ข้อมูลรายการ</h2>
              <dl class="transaction-review-grid">
                <div v-if="selected.kind === 'sale'">
                  <dt>เลขที่รายการ</dt>
                  <dd>{{ selected.number }}</dd>
                </div>
                <div>
                  <dt>วันที่ทำรายการ</dt>
                  <dd>{{ formatDateTime(selected.date) }}</dd>
                </div>
                <div>
                  <dt>ประเภท</dt>
                  <dd>{{ selected.kind === 'sale' ? 'รายการขาย' : 'รายการใช้บริการ' }}</dd>
                </div>
                <div v-if="hasRecordValue(selected.performedBy)">
                  <dt>ผู้บันทึก</dt>
                  <dd>{{ selected.performedBy }}</dd>
                </div>
                <div v-if="hasRecordValue(selected.paymentMethod)">
                  <dt>ช่องทางชำระ</dt>
                  <dd>{{ selected.paymentMethod }}</dd>
                </div>
              </dl>
            </section>
            <aside class="transaction-review-patient">
              <b>ผู้ป่วย</b>
              <strong>{{ selected.patient }}</strong>
              <span>HN {{ selected.hn }}</span>
            </aside>
          </div>
          <section class="transaction-service-detail">
            <h2>รายละเอียดบริการ</h2>
            <div>
              <strong>{{ selected.item }}</strong
              ><span>{{ selected.kind === 'sale' ? 'รายการขาย' : 'ใช้สิทธิ์ 1 ครั้ง' }}</span>
            </div>
            <dl>
              <div>
                <dt>จำนวน</dt>
                <dd>{{ selected.kind === 'usage' ? '1 ครั้ง' : '-' }}</dd>
              </div>
              <div>
                <dt>ยอดสุทธิ</dt>
                <dd>{{ selected.kind === 'sale' ? selected.quantity : '—' }}</dd>
              </div>
            </dl>
          </section>
          <section
            v-if="selected.status === 'ปกติ'"
            aria-labelledby="transaction-reason-title"
            class="transaction-reason-flow"
          >
            <p
              v-if="selected.kind === 'usage' && selected.movementId == null"
              class="transaction-error"
              role="status"
            >
              รายการนี้ไม่มีข้อมูลการตัดสิทธิ์อ้างอิง กรุณาให้ผู้ดูแลตรวจสอบก่อนยกเลิก
            </p>
            <header>
              <h2 id="transaction-reason-title">ยกเลิกรายการ</h2>
            </header>
            <div class="transaction-reason-panel">
              <div
                class="transaction-reason-options"
                role="radiogroup"
                aria-label="เลือกเหตุผลการยกเลิก"
              >
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
              </div>
              <div v-if="reasonType === 'อื่น ๆ'" class="transaction-other-reason">
                <label for="transaction-other-reason">รายละเอียดเหตุผล *</label>
                <textarea
                  id="transaction-other-reason"
                  v-model="otherReason"
                  rows="3"
                  placeholder="กรุณาระบุรายละเอียดเพื่อให้ตรวจสอบย้อนหลังได้"
                />
              </div>
            </div>
            <div class="transaction-reason-actions">
              <button class="transaction-secondary" type="button" @click="backToList()">
                ย้อนกลับ
              </button>
              <button
                class="transaction-primary"
                type="button"
                :disabled="
                  !canReview ||
                  submitting ||
                  (selected.kind === 'usage' && selected.movementId == null)
                "
                @click="openCancelWarning"
              >
                ยืนยันการยกเลิก
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="step === 'success'">
          <section
            class="transaction-success-result"
            aria-labelledby="transaction-success-title"
            role="status"
          >
            <span class="transaction-success-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24" fill="none">
                <path
                  d="m5 12 4 4L19 6"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                /></svg
            ></span>
            <h1 id="transaction-success-title">ยกเลิกรายการสำเร็จ</h1>
            <p>บันทึกผลการยกเลิกไว้ในประวัติแล้ว</p>
            <dl>
              <div v-if="selected.kind === 'sale'">
                <dt>เลขรายการอ้างอิง</dt>
                <dd>{{ selected.number || '-' }}</dd>
              </div>
              <div>
                <dt>วันเวลาที่ยกเลิก</dt>
                <dd>{{ formatShortDateTime(selected.voidedAt) }}</dd>
              </div>
              <div class="transaction-success-wide">
                <dt>ผู้ป่วย</dt>
                <dd>
                  {{ selected.patient }}<small>HN {{ selected.hn }}</small>
                </dd>
              </div>
              <div class="transaction-success-wide">
                <dt>บริการ / คอร์ส</dt>
                <dd>{{ selected.item }}</dd>
              </div>
              <div>
                <dt>{{ selected.kind === 'sale' ? 'ยอดเงินรายการเดิม' : 'สิทธิ์ที่ยกเลิก' }}</dt>
                <dd>{{ selected.quantity }}</dd>
              </div>
              <div>
                <dt>สถานะ</dt>
                <dd class="transaction-success-status">ยกเลิกแล้ว</dd>
              </div>
              <div class="transaction-success-wide">
                <dt>เหตุผล</dt>
                <dd>{{ selected.voidReason || '-' }}</dd>
              </div>
            </dl>
            <div class="transaction-success-actions">
              <button class="transaction-secondary" type="button" @click="backToList('history')">
                ดูประวัติการยกเลิก
              </button>
              <button class="transaction-primary" type="button" @click="backToList()">
                กลับหน้ารายการ
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
        v-modal-focus="{ close: closeCancelWarning, busy: submitting }"
        class="transaction-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="transaction-modal-title"
        aria-describedby="transaction-modal-description"
      >
        <div class="transaction-modal-heading">
          <h2 id="transaction-modal-title">ยืนยันการยกเลิกรายการ</h2>
          <button
            class="transaction-cancellation-info-toggle"
            type="button"
            title="ข้อมูลเพิ่มเติมเกี่ยวกับการยกเลิก"
            aria-label="ข้อมูลเพิ่มเติมเกี่ยวกับการยกเลิก"
            :aria-expanded="showCancellationInfo"
            aria-controls="transaction-cancellation-info"
            @click="showCancellationInfo = !showCancellationInfo"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="12" r="8.25" stroke="currentColor" stroke-width="1.7" />
              <path
                d="M12 10.5v5"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <path
                d="M12 7.25h.01"
                stroke="currentColor"
                stroke-width="2.3"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </div>
        <p id="transaction-modal-description">
          ต้องการยกเลิกรายการ
          {{ selected.kind === 'sale' ? selected.number : selected.item }} ใช่หรือไม่?
        </p>
        <p
          v-if="showCancellationInfo"
          id="transaction-cancellation-info"
          class="transaction-cancellation-info"
        >
          ระบบจะเก็บรายการเดิมและประวัติการยกเลิกไว้เพื่อตรวจสอบย้อนหลัง
        </p>
        <dl class="transaction-modal-summary">
          <div class="transaction-modal-patient">
            <dt>ผู้ป่วย</dt>
            <dd>
              {{ selected.patient }}<small>HN {{ selected.hn }}</small>
            </dd>
          </div>
          <div>
            <dt>บริการ / คอร์ส</dt>
            <dd>{{ selected.item }}</dd>
          </div>
          <div class="transaction-modal-amount">
            <dt>{{ selected.kind === 'sale' ? 'ยอดเงิน' : 'สิทธิ์ที่ยกเลิก' }}</dt>
            <dd>{{ selected.quantity }}</dd>
          </div>
          <div>
            <dt>เหตุผล</dt>
            <dd>{{ reason }}</dd>
          </div>
        </dl>
        <div class="transaction-actions">
          <button
            ref="cancelWarningBackButton"
            class="transaction-secondary"
            type="button"
            :disabled="submitting"
            @click="closeCancelWarning"
          >
            ยกเลิก
          </button>
          <button
            class="transaction-danger"
            type="button"
            :disabled="submitting"
            @click="confirmCancellation"
          >
            {{ submitting ? 'กำลังยกเลิก...' : 'ยืนยัน' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="../styles/transactions.css"></style>

<style scoped src="../styles/transaction-system.css"></style>
