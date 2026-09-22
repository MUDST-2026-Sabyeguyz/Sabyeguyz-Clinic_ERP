<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi, salesApi } from '@/api/client'

type Transaction = {
  id: string
  date: string
  sortAt: number
  patient: string
  hn: string
  branch: string
  performedBy: string
  type: 'Receipt' | 'Course Transfer'
  amount: string
  status: 'สำเร็จ' | 'Void'
  detail?: string
  transferFrom?: string
  transferTo?: string
  voidReason?: string
  voidedAt?: string
  voidedBy?: string
  paymentId?: number
}

const search = ref('')
const sortOrder = ref<'date-desc' | 'date-asc' | 'id-desc' | 'id-asc'>('date-desc')
const typeFilter = ref('ทั้งหมด')
const statusFilter = ref('ทั้งหมด')
const selected = ref<Transaction | null>(null)
const voiding = ref(false)
const voidConfirmationOpen = ref(false)
const reason = ref('')
const snackbarMessage = ref('')
const errorMessage = ref('')
const loading = ref(false)
const rows = ref<Transaction[]>([])
const page = ref(0)
const pageSize = 20

const formatDate = (value: string) => new Date(value).toLocaleString('th-TH')

async function loadTransactions() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [payments, transfers] = await Promise.all([
      salesApi.list({ page: 0, size: 100 }),
      courseApi.transferHistory(),
    ])
    rows.value = [
      ...payments.items.map((payment): Transaction => ({
        id: payment.receiptNo,
        date: formatDate(payment.soldAt),
        sortAt: new Date(payment.soldAt).getTime(),
        patient: payment.patient.fullName,
        hn: payment.patient.hn,
        branch: payment.branchCode,
        performedBy: payment.salesperson?.fullName ?? 'ไม่ระบุ',
        type: 'Receipt',
        detail: 'รายการชำระเงิน',
        amount: `฿${Number(payment.paid).toLocaleString()}`,
        status: payment.status === 'VOIDED' ? 'Void' : 'สำเร็จ',
        voidedAt: payment.voidedAt ? formatDate(payment.voidedAt) : undefined,
        paymentId: payment.id,
      })),
      ...transfers.map((transfer): Transaction => ({
        id: `TRF-${transfer.id}`,
        date: formatDate(transfer.createdAt),
        sortAt: new Date(transfer.createdAt).getTime(),
        patient: transfer.destinationPatientName,
        hn: transfer.destinationPatientHn,
        branch: transfer.branchCode,
        performedBy: 'ไม่ระบุ',
        type: 'Course Transfer',
        detail: `คอร์ส #${transfer.courseId}`,
        transferFrom: transfer.sourcePatientName,
        transferTo: transfer.destinationPatientName,
        amount: `${transfer.quantity} ครั้ง`,
        status: 'สำเร็จ',
      })),
    ]
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการธุรกรรมไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

onMounted(loadTransactions)

function splitDateTime(value: string) {
  const separator = value.lastIndexOf(' ')
  return separator < 0
    ? { date: value, time: '-' }
    : { date: value.slice(0, separator), time: value.slice(separator + 1) }
}

const filteredRows = computed(() => {
  const term = search.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesSearch = !term || `${row.id} ${row.hn} ${row.patient} ${row.type}`.toLowerCase().includes(term)
    const matchesType = typeFilter.value === 'ทั้งหมด' || row.type === typeFilter.value
    const matchesStatus = statusFilter.value === 'ทั้งหมด' || row.status === statusFilter.value
    return matchesSearch && matchesType && matchesStatus
  }).sort((left, right) => {
    if (sortOrder.value.startsWith('id')) {
      return sortOrder.value === 'id-asc' ? left.id.localeCompare(right.id) : right.id.localeCompare(left.id)
    }
    return sortOrder.value === 'date-asc' ? left.sortAt - right.sortAt : right.sortAt - left.sortAt
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pageSize)))
const visibleRows = computed(() => {
  const start = page.value * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})

watch([search, typeFilter, statusFilter, sortOrder], () => {
  page.value = 0
  selected.value = null
})
watch(totalPages, (nextTotal) => {
  if (page.value >= nextTotal) page.value = nextTotal - 1
})

function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  selected.value = null
}

function open(row: Transaction) {
  if (selected.value?.id === row.id) {
    selected.value = null
    return
  }
  selected.value = row
  voiding.value = false
  voidConfirmationOpen.value = false
  reason.value = ''
  nextTick(() => document.querySelector('.detail-row')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
}

function confirmVoid() {
  if (!selected.value?.paymentId || !reason.value.trim()) return
  voidConfirmationOpen.value = true
}

async function applyVoid() {
  if (!selected.value?.paymentId || !reason.value.trim()) return
  loading.value = true
  try {
    await salesApi.reverse(selected.value.paymentId, reason.value.trim())
    snackbarMessage.value = 'ยกเลิกรายการเรียบร้อยแล้ว'
    window.setTimeout(() => { snackbarMessage.value = '' }, 3500)
    voiding.value = false
    voidConfirmationOpen.value = false
    reason.value = ''
    selected.value = null
    await loadTransactions()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ยกเลิกรายการไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="staff-page transaction-page">
    <StaffHeader />
    <main class="staff-main transaction-main">
      <section class="staff-hero">
        <h1>รายการธุรกรรม</h1>
        <p>รวมรายการขาย การใช้บริการ การโอนสิทธิ์</p>
      </section>
      <section class="staff-panel transaction-list">
        <header class="panel-head">
          <div class="search-box">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input
              v-model="search"
              aria-label="ค้นหารายการธุรกรรม"
              placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย"
            />
          </div>
          <select v-model="typeFilter" class="transaction-filter" aria-label="กรองประเภทธุรกรรม">
            <option>ทั้งหมด</option>
            <option>Receipt</option>
            <option>Course Transfer</option>
          </select>
          <select v-model="statusFilter" class="transaction-filter" aria-label="กรองสถานะธุรกรรม">
            <option>ทั้งหมด</option>
            <option>สำเร็จ</option>
            <option>Void</option>
          </select>
          <select v-model="sortOrder" class="sort-filter" aria-label="เรียงลำดับธุรกรรม">
            <option value="date-desc">วันที่ล่าสุด</option>
            <option value="date-asc">วันที่เก่าสุด</option>
            <option value="id-desc">เลขรายการมากไปน้อย</option>
            <option value="id-asc">เลขรายการน้อยไปมาก</option>
          </select>
        </header>
        <div class="transaction-table-scroll">
          <table class="staff-table">
          <thead>
            <tr>
              <th>เลขที่รายการ</th>
              <th>วันที่</th>
              <th>เวลา</th>
              <th>ผู้ป่วย</th>
              <th>ประเภทรายการ</th>
              <th>จำนวน</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!visibleRows.length">
              <td colspan="8" class="empty-result">ไม่พบรายการที่ตรงกับคำค้น</td>
            </tr>
            <template v-for="row in visibleRows" :key="row.id">
              <tr
                class="transaction-row"
                :class="{ 'is-expanded': selected?.id === row.id }"
                tabindex="0"
                @click="open(row)"
                @keydown.enter="open(row)"
                @keydown.space.prevent="open(row)"
              >
                <td>
                  <b>{{ row.id }}</b>
                </td>
                <td>{{ splitDateTime(row.date).date }}</td>
                <td>{{ splitDateTime(row.date).time }}</td>
                <td>
                  <b>{{ row.patient }}</b>
                </td>
                <td>{{ row.type }}</td>
                <td>{{ row.amount }}</td>
                <td>
                  <span class="status" :class="{ void: row.status === 'Void' }">{{
                    row.status === 'Void' ? 'ยกเลิกแล้ว' : row.status
                  }}</span>
                </td>
                <td>
                  <button
                    class="detail-toggle"
                    :class="{ open: selected?.id === row.id }"
                    :aria-expanded="selected?.id === row.id"
                    :aria-label="selected?.id === row.id ? 'ซ่อนรายละเอียด' : 'ดูรายละเอียด'"
                    @click.stop="open(row)"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="m7 10 5 5 5-5" />
                    </svg>
                  </button>
                </td>
              </tr>
              <tr v-if="selected?.id === row.id" class="detail-row">
                <td colspan="8">
                  <section class="inline-transaction-detail">
                    <header class="detail-header">
                      <div class="detail-title">
                        <span class="receipt-icon" aria-hidden="true">
                          <svg viewBox="0 0 24 24">
                            <path d="M7 3h10a2 2 0 0 1 2 2v16l-3-2-4 2-4-2-3 2V5a2 2 0 0 1 2-2Z" />
                            <path d="M8 8h8M8 12h6" />
                          </svg>
                        </span>
                        <div>
                          <small>รายละเอียดธุรกรรม</small>
                          <h2>{{ row.id }}</h2>
                        </div>
                      </div>
                      <div class="detail-amount">
                        <small>ยอดรายการ</small>
                        <strong>{{ row.amount }}</strong>
                      </div>
                    </header>

                    <dl class="inline-detail-grid">
                      <div>
                        <dt>ประเภทรายการ</dt>
                        <dd>{{ row.type }}</dd>
                      </div>
                      <div v-if="row.detail">
                        <dt>รายการที่ซื้อ</dt>
                        <dd>{{ row.detail }}</dd>
                      </div>
                      <div v-if="row.transferFrom">
                        <dt>เจ้าของคอร์ส</dt>
                        <dd>{{ row.transferFrom }}</dd>
                      </div>
                      <div v-if="row.transferTo">
                        <dt>ผู้รับสิทธิ์</dt>
                        <dd>{{ row.transferTo }}</dd>
                      </div>
                      <div>
                        <dt>วันที่</dt>
                        <dd>{{ splitDateTime(row.date).date }}</dd>
                      </div>
                      <div>
                        <dt>เวลา</dt>
                        <dd>{{ splitDateTime(row.date).time }}</dd>
                      </div>
                      <div v-if="!row.transferFrom">
                        <dt>ผู้ป่วย</dt>
                        <dd>{{ row.patient }}</dd>
                      </div>
                      <div v-if="!row.transferFrom">
                        <dt>รหัสผู้ป่วย</dt>
                        <dd>{{ row.hn }}</dd>
                      </div>
                      <div>
                        <dt>ผู้ทำรายการ</dt>
                        <dd>{{ row.performedBy }}</dd>
                      </div>
                      <div>
                        <dt>สาขา</dt>
                        <dd>{{ row.branch }}</dd>
                      </div>
                    </dl>

                    <section v-if="row.status === 'Void' && row.voidReason" class="void-history">
                      <h3>ข้อมูลการยกเลิกรายการ</h3>
                      <p><b>เหตุผล:</b> {{ row.voidReason }}</p>
                      <p>{{ row.voidedAt }} {{ row.voidedBy }}</p>
                    </section>

                    <section v-if="voiding" class="void-form">
                      <label>
                        เหตุผลการยกเลิกรายการ <span>*</span>
                        <div class="void-form-controls">
                          <textarea
                            v-model.trim="reason"
                            rows="1"
                            placeholder="ระบุเหตุผลการยกเลิกรายการ"
                          ></textarea>
                          <button
                            class="danger solid"
                            :disabled="!reason.trim()"
                            @click="confirmVoid"
                          >
                            ยืนยันยกเลิกรายการ
                          </button>
                        </div>
                      </label>
                    </section>

                    <footer v-if="!voiding">
                      <button
                        v-if="row.status !== 'Void' && row.paymentId"
                        class="danger"
                        @click="voiding = true"
                      >
                        ยกเลิกรายการ
                      </button>
                    </footer>
                  </section>
                </td>
              </tr>
            </template>
          </tbody>
          </table>
        </div>
        <nav v-if="totalPages > 1" class="transaction-pagination" aria-label="เปลี่ยนหน้ารายการธุรกรรม">
          <button type="button" :disabled="page === 0" @click="changePage(page - 1)">← ก่อนหน้า</button>
          <span>หน้า {{ page + 1 }} จาก {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">ถัดไป →</button>
        </nav>
        <p v-if="loading" class="empty-result">กำลังโหลดรายการธุรกรรม...</p>
        <p v-if="errorMessage" class="empty-result" role="alert">{{ errorMessage }}</p>
        <p class="audit-note">
          รายการจะถูกเก็บเป็นประวัติตรวจสอบย้อนหลังและไม่สามารถลบออกจากระบบได้
        </p>
      </section>
    </main>
    <Teleport to="body">
      <div
        v-if="voidConfirmationOpen"
        class="confirmation-backdrop"
        @click.self="voidConfirmationOpen = false"
      >
        <section
          class="confirmation-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-describedby="void-confirmation-description"
        >
          <p id="void-confirmation-description">
            ต้องการยกเลิกรายการ <strong>{{ selected?.id }}</strong> หรือไม่?
          </p>
          <div class="confirmation-actions">
            <button type="button" class="secondary" @click="voidConfirmationOpen = false">
              ยกเลิก
            </button>
            <button type="button" class="danger solid" :disabled="loading" @click="applyVoid">ยืนยัน</button>
          </div>
        </section>
      </div>
    </Teleport>
    <Transition name="snackbar">
      <p v-if="snackbarMessage" class="transaction-snackbar" role="status" aria-live="polite">
        {{ snackbarMessage }}
      </p>
    </Transition>
  </div>
</template>
<style scoped src="../styles/transactions.css"></style>
<style src="../styles/therapist.css"></style>
