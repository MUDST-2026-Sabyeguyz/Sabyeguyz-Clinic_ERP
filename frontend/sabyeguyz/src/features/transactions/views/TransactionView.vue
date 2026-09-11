<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
type Transaction = {
  id: string
  date: string
  patient: string
  hn: string
  branch: string
  performedBy: string
  type: string
  amount: string
  status: string
  voidReason?: string
  voidedAt?: string
  voidedBy?: string
}
const search = ref(''),
  selected = ref<Transaction | null>(null),
  voiding = ref(false),
  reason = ref('')
const rows = ref<Transaction[]>([
  {
    id: 'PAY-260822-0021',
    date: '22 ส.ค. 2026 09:05',
    patient: 'สมชาย ใจดี',
    hn: 'HN2601080022',
    branch: 'สาขาราชพฤกษ์',
    performedBy: 'เจ้าหน้าที่คลินิก',
    type: 'Course Sale',
    amount: '฿8,500',
    status: 'สำเร็จ',
  },
  {
    id: 'TRT-260822-0018',
    date: '22 ส.ค. 2026 10:42',
    patient: 'วรรณา มั่นคง',
    hn: 'HN2601080012',
    branch: 'สาขาราชพฤกษ์',
    performedBy: 'กฤษดา น. นักกายภาพบำบัด',
    type: 'Course Usage',
    amount: '−1 ครั้ง',
    status: 'สำเร็จ',
  },
  {
    id: 'TRF-260821-0004',
    date: '21 ส.ค. 2026 14:32',
    patient: 'นรินทร์ สุขใจ',
    hn: 'HN2601080031',
    branch: 'สาขาราชพฤกษ์',
    performedBy: 'เจ้าหน้าที่คลินิก',
    type: 'Course Transfer',
    amount: '2 ครั้ง',
    status: 'สำเร็จ',
  },
  {
    id: 'VOID-260820-0002',
    date: '20 ส.ค. 2026 16:20',
    patient: 'กนกวรรณ พูนใจ',
    hn: 'HN2601080007',
    branch: 'สาขาราชพฤกษ์',
    performedBy: 'เจ้าหน้าที่คลินิก',
    type: 'Single Visit Sale',
    amount: '฿1,000',
    status: 'Void',
    voidReason: 'บันทึกรายการผิด ต้องยกเลิกเพื่อออก Transaction ใหม่ให้ถูกต้อง',
    voidedAt: '20 ส.ค. 2026 16:28',
    voidedBy: 'Manager สาขาราชพฤกษ์',
  },
])
const visibleRows = computed(() => {
  const term = search.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesSearch =
      !term || `${row.id} ${row.hn} ${row.patient} ${row.type}`.toLowerCase().includes(term)
    return matchesSearch
  })
})
function open(row: Transaction) {
  if (selected.value?.id === row.id) {
    selected.value = null
    return
  }
  selected.value = row
  voiding.value = false
  reason.value = ''
  nextTick(() =>
    document.querySelector('.detail-row')?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
  )
}
function confirmVoid() {
  if (!selected.value || !reason.value.trim()) return
  selected.value.status = 'Void'
  selected.value.voidReason = reason.value.trim()
  selected.value.voidedAt = new Intl.DateTimeFormat('th-TH', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date())
  selected.value.voidedBy = 'Manager สาขาราชพฤกษ์'
  voiding.value = false
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
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>เลขที่รายการ</th>
              <th>วันที่และเวลา</th>
              <th>ผู้ป่วย</th>
              <th>ประเภทรายการ</th>
              <th>จำนวน</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!visibleRows.length">
              <td colspan="7" class="empty-result">ไม่พบรายการที่ตรงกับคำค้น</td>
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
                <td>{{ row.date }}</td>
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
                <td colspan="7">
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
                      <div>
                        <dt>วันที่และเวลา</dt>
                        <dd>{{ row.date }}</dd>
                      </div>
                      <div>
                        <dt>ผู้ป่วย</dt>
                        <dd>{{ row.patient }} {{ row.hn }}</dd>
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
                      <p>
                        {{ row.voidedAt }} {{ row.voidedBy }}
                      </p>
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
                        v-if="row.status !== 'Void'"
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
        <p class="audit-note">
          รายการจะถูกเก็บเป็นประวัติตรวจสอบย้อนหลังและไม่สามารถลบออกจากระบบได้
        </p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/transactions.css"></style>
<style src="../styles/therapist.css"></style>
