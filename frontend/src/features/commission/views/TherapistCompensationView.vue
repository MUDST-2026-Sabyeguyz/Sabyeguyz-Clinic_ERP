<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type StaffPayment } from '@/api/client'

type Tab = 'ภาพรวม' | 'ประวัติการจ่าย'
const tabs: Tab[] = ['ภาพรวม', 'ประวัติการจ่าย']
const activeTab = ref<Tab>('ภาพรวม')
const payments = ref<StaffPayment[]>([])
const selectedMonth = ref('')
const loading = ref(false)
const errorMessage = ref('')

const statusLabel = (status: StaffPayment['status']) =>
  status === 'PAID' ? 'จ่ายแล้ว' : status === 'CLOSED' ? 'ปิดงวด รอจ่าย' : 'อยู่ระหว่างคำนวณ'
const money = (value: number | null) =>
  value === null ? null : `฿${Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2 })}`
const monthLabel = (month: string) => {
  const [year, number] = month.split('-').map(Number)
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(year!, number! - 1, 1),
  )
}

const months = computed(() => payments.value.map((payment) => payment.month))
const selected = computed(
  () => payments.value.find((payment) => payment.month === selectedMonth.value) ?? null,
)
const paidHistory = computed(() => payments.value.filter((payment) => payment.status === 'PAID'))

onMounted(async () => {
  loading.value = true
  try {
    payments.value = await commissionApi.myPayments()
    selectedMonth.value = payments.value[0]?.month ?? ''
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดค่าตอบแทนไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})

</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main">
      <header class="therapist-compensation-heading">
        <div>
          <h1>{{ activeTab === 'ภาพรวม' ? 'ค่าตอบแทนของฉัน' : 'ประวัติการจ่าย' }}</h1>
          <p>
            {{
              activeTab === 'ภาพรวม'
                ? 'ค่าคอมมิชชันและค่ามือของคุณในแต่ละเดือนที่รักษา'
                : 'ยอดที่จ่ายแล้วไม่สามารถแก้ไขย้อนหลังได้ การแก้ไขจะทำเป็นใบปรับยอดในงวดถัดไป'
            }}
          </p>
        </div>
        <div v-if="activeTab === 'ภาพรวม' && selected" class="latest-status">
          <span>สถานะงวด</span
          ><b :class="{ paid: selected.status === 'PAID' }">{{ statusLabel(selected.status) }}</b>

        </div>
        <span class="therapist-status-pill">รอตรวจสอบ</span>
      </header>

      <nav class="compensation-tabs" aria-label="เมนูค่าตอบแทน">
        <button
          v-for="tab in tabs"
          :key="tab"
          type="button"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </nav>

      <p v-if="errorMessage" role="alert" class="compensation-error">{{ errorMessage }}</p>
      <p v-else-if="loading">กำลังโหลดค่าตอบแทน...</p>
      <p v-else-if="payments.length === 0" class="empty-row">
        ยังไม่มีงวดค่าตอบแทน งวดจะปรากฏหลังผู้จัดการเตรียมงวดจ่ายของเดือนที่คุณรักษา
      </p>

      <template v-else-if="activeTab === 'ภาพรวม' && selected">
        <section class="compensation-filters">
          <label
            ><span>เดือนที่รักษา</span>
            <select v-model="selectedMonth" aria-label="เดือนที่รักษา">
              <option v-for="month in months" :key="month" :value="month">
                {{ monthLabel(month) }}
              </option>
            </select></label
          >
        </section>
        <section class="compensation-summary" aria-label="สรุปค่าตอบแทน">
          <article class="commission-total">
            <span>ค่าคอมมิชชันคอร์ส</span>
            <b v-if="selected.courseCommission !== null">{{ money(selected.courseCommission) }}</b>
            <b v-else>รอคำนวณ</b>
            <small v-if="selected.courseCommission === null"
              >{{ selected.pendingSessions }} ครั้งที่เดือนขายคอร์สยังไม่ปิดยอด</small
            >
          </article>
          <article>
            <span>ค่ามือรายครั้ง</span><b>{{ money(selected.singleVisitFee) ?? '—' }}</b>
          </article>
          <article>
            <span>ค่ารักษาแทน</span><b>{{ money(selected.substituteFee) ?? '—' }}</b>
          </article>
          <article>
            <span>ปรับยอด</span><b>{{ money(selected.adjustment) ?? '—' }}</b>
          </article>
          <article class="net-total">
            <span>ยอดสุทธิ</span>
            <b v-if="selected.net !== null">{{ money(selected.net) }}</b>
            <b v-else>รอคำนวณ</b>
            <small>{{ statusLabel(selected.status) }}</small>
          </article>
        </section>
      </template>

      <template v-else-if="activeTab === 'ประวัติการจ่าย'">
        <section class="payment-history-table">
          <header>
            <span>รอบเดือน</span><span>Commission</span><span>ค่ามือ</span><span>ปรับยอด</span
            ><span>รวมสุทธิ</span><span>วันที่จ่าย</span>
          </header>
          <article v-for="item in paidHistory" :key="item.id">
            <div>
              <small>รอบเดือน</small><b>{{ monthLabel(item.month) }}</b>
            </div>
            <div>
              <small>Commission</small><b>{{ money(item.courseCommission) }}</b>
            </div>
            <div>
              <small>ค่ามือ</small
              ><b>{{ money((item.singleVisitFee ?? 0) + (item.substituteFee ?? 0)) }}</b>
            </div>
            <div>
              <small>ปรับยอด</small><b>{{ money(item.adjustment) }}</b>
            </div>
            <div>
              <small>รวมสุทธิ</small><b>{{ money(item.net) }}</b>
            </div>
            <div>
              <small>วันที่จ่าย</small
              ><b>{{
                item.paidAt
                  ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium' }).format(
                      new Date(item.paidAt),
                    )
                  : '—'
              }}</b>
            </div>
          </article>
          <p v-if="paidHistory.length === 0" class="empty-row">ยังไม่มีงวดที่จ่ายแล้ว</p>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped src="../styles/therapist-compensation.css"></style>
<style scoped>
.compensation-error {
  padding: 11px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.compensation-filters select {
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  background: #fff;
  font: inherit;
}
.compensation-summary {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
</style>
