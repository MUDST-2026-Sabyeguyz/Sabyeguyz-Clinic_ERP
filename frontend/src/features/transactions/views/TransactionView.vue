<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { salesApi } from '@/api/client'
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
  reason = ref(''),
  loading = ref(false),
  errorMessage = ref('')
const rows = ref<Transaction[]>([])
const page = ref(0)
const totalPages = ref(0)
const typeFilter = ref('ทั้งหมด')
const statusFilter = ref('ทั้งหมด')
const sortOrder = ref<'date-desc' | 'date-asc' | 'id-desc' | 'id-asc'>('date-desc')
async function load() {
  try {
    const result = await salesApi.list({ page: page.value, size: 20 })
    totalPages.value = result.totalPages
    rows.value = result.items.map((sale) => ({
      id: sale.id.toString(),
      date: new Date(sale.soldAt).toLocaleString('th-TH'),
      patient: sale.patient.fullName,
      hn: sale.patient.hn,
      branch: sale.branchCode,
      performedBy: sale.salesperson?.fullName ?? 'ไม่ระบุ',
      type: 'Receipt',
      amount: `฿${Number(sale.paid).toLocaleString()}`,
      status: sale.status === 'VOIDED' ? 'Void' : 'สำเร็จ',
    }))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการชำระเงินไม่สำเร็จ'
  }
}
void load()
const visibleRows = computed(() => {
  const term = search.value.trim().toLowerCase()
  return rows.value
    .filter((row) => {
      const matchesSearch =
        !term || `${row.id} ${row.hn} ${row.patient} ${row.type}`.toLowerCase().includes(term)
      return (
        matchesSearch &&
        (typeFilter.value === 'ทั้งหมด' || row.type === typeFilter.value) &&
        (statusFilter.value === 'ทั้งหมด' || row.status === statusFilter.value)
      )
    })
    .sort((left, right) => {
      if (sortOrder.value.startsWith('id'))
        return sortOrder.value === 'id-asc'
          ? Number(left.id) - Number(right.id)
          : Number(right.id) - Number(left.id)
      const comparison = left.date.localeCompare(right.date)
      return sortOrder.value === 'date-asc' ? comparison : -comparison
    })
})
async function changePage(nextPage: number) {
  if (nextPage >= 0 && nextPage < totalPages.value) {
    page.value = nextPage
    selected.value = null
    await load()
  }
}
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
async function confirmVoid() {
  if (!selected.value || !reason.value.trim()) return
  loading.value = true
  try {
    await salesApi.reverse(Number(selected.value.id), reason.value.trim())
    await load()
    voiding.value = false
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
          <select v-model="typeFilter" aria-label="กรองประเภทธุรกรรม">
            <option>ทั้งหมด</option>
            <option>Receipt</option>
          </select>
          <select v-model="statusFilter" aria-label="กรองสถานะธุรกรรม">
            <option>ทั้งหมด</option>
            <option>สำเร็จ</option>
            <option>Void</option>
          </select>
          <select v-model="sortOrder" aria-label="เรียงรายการธุรกรรม">
            <option value="date-desc">วันที่ล่าสุด</option>
            <option value="date-asc">วันที่เก่าสุด</option>
            <option value="id-desc">เลขรายการมากไปน้อย</option>
            <option value="id-asc">เลขรายการน้อยไปมาก</option>
          </select>
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
                      <button v-if="row.status !== 'Void'" class="danger" @click="voiding = true">
                        ยกเลิกรายการ
                      </button>
                    </footer>
                  </section>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <footer v-if="totalPages > 1" class="pagination">
          <button class="secondary" :disabled="page === 0" @click="changePage(page - 1)">
            ก่อนหน้า
          </button>
          <span>หน้า {{ page + 1 }} / {{ totalPages }}</span>
          <button
            class="secondary"
            :disabled="page + 1 >= totalPages"
            @click="changePage(page + 1)"
          >
            ถัดไป
          </button>
        </footer>
        <p class="audit-note">
          รายการจะถูกเก็บเป็นประวัติตรวจสอบย้อนหลังและไม่สามารถลบออกจากระบบได้
        </p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/transactions.css"></style>
<style src="../styles/therapist.css"></style>
