<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import TherapistCompensationLineList from '../components/TherapistCompensationLineList.vue'
import TherapistCompensationOverview from '../components/TherapistCompensationOverview.vue'
import TherapistCompensationPaymentHistory from '../components/TherapistCompensationPaymentHistory.vue'
import TherapistCompensationShell from '../components/TherapistCompensationShell.vue'
import {
  currentMonth,
  isCommission,
  monthLabel,
  statusLabel,
  useTherapistPay,
} from '../lib/therapistPay'
import { resolveTherapistCompensationTab } from '../types/therapistCompensation'

const route = useRoute()
const router = useRouter()
const { payments, adjustments, data, lines, month, months, payment, loading, errorMessage } =
  useTherapistPay()
const activeTab = computed(() => resolveTherapistCompensationTab(route.query.tab))
const commissionLines = computed(() => lines.value.filter(isCommission))
const feeLines = computed(() => lines.value.filter((line) => !isCommission(line)))

const subtitle = computed(() => {
  if (activeTab.value === 'payments') {
    return 'ยอดที่จ่ายแล้วไม่สามารถแก้ไขย้อนหลังได้ การแก้ไขจะทำเป็นใบปรับยอดในงวดถัดไป'
  }
  return `${monthLabel(month.value)} | ${statusLabel(payment.value?.status)}`
})
const title = computed(() =>
  activeTab.value === 'payments' ? 'ประวัติการจ่าย' : 'ค่าตอบแทนของฉัน',
)

const monthNames = Array.from({ length: 12 }, (_, index) =>
  new Intl.DateTimeFormat('th-TH', { month: 'long' }).format(new Date(2026, index, 1)),
)
const chosenYear = computed(() => Number(month.value.slice(0, 4)))
const chosenMonth = computed(() => month.value.slice(5, 7))
const years = computed(() => {
  const thisYear = Number(currentMonth().slice(0, 4))
  const fromPayments = months.value.map((value) => Number(value.slice(0, 4)))
  const first = Math.min(thisYear - 2, ...fromPayments)
  const last = Math.max(thisYear, ...fromPayments)
  return Array.from({ length: last - first + 1 }, (_, index) => last - index)
})
const yearLabel = (year: number) => String(year + 543)

function choose(year: number, monthNumber: string) {
  void router.replace({
    query: { ...route.query, month: `${year}-${monthNumber}` },
  })
}
function chooseYear(value: string) {
  choose(Number(value), chosenMonth.value)
}
function moveMonth(offset: number) {
  const date = new Date(chosenYear.value, Number(chosenMonth.value) - 1 + offset, 1)
  choose(date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'))
}
</script>

<template>
  <TherapistCompensationShell
    :title="title"
    :subtitle="subtitle"
    :active-tab="activeTab"
    :error="errorMessage"
    :loading="loading"
  >
    <template #aside>
      <div v-if="activeTab !== 'payments'" class="therapist-month-select">
        <span>เดือนที่รักษา</span>
        <div class="therapist-month-pickers">
          <div class="therapist-month-stepper" aria-label="เลือกเดือน">
            <button type="button" aria-label="เดือนก่อน" @click="moveMonth(-1)">‹</button>
            <strong>{{ monthNames[Number(chosenMonth) - 1] }}</strong>
            <button type="button" aria-label="เดือนถัดไป" @click="moveMonth(1)">›</button>
          </div>
          <FilterDropdown
            :model-value="String(chosenYear)"
            :options="years.map((year) => ({ value: String(year), label: yearLabel(year) }))"
            label="ปี"
            default-label="เลือกปี"
            :clear-on-reselect="false"
            hide-default-option
            @update:model-value="chooseYear"
          />
        </div>
      </div>
    </template>

    <TherapistCompensationOverview
      v-if="activeTab === 'overview'"
      :payment="payment"
      :lines="lines"
      :month="month"
      :pending-sessions="data?.pendingSessions ?? 0"
    />
    <section v-else-if="activeTab === 'commission'" class="therapist-table-card">
      <h2>ค่าคอมมิชชันของ{{ monthLabel(month) }}</h2>
      <TherapistCompensationLineList
        :lines="commissionLines"
        :month="month"
        empty-text="ยังไม่มีค่าคอมมิชชันในเดือนนี้"
      />
    </section>
    <section v-else-if="activeTab === 'treatment'" class="therapist-table-card">
      <h2>ค่ามือของ{{ monthLabel(month) }}</h2>
      <TherapistCompensationLineList
        :lines="feeLines"
        :month="month"
        empty-text="ยังไม่มีค่ามือในเดือนนี้"
      />
    </section>
    <TherapistCompensationPaymentHistory
      v-else
      :payments="payments"
      :adjustments="adjustments"
      :month="month"
    />
  </TherapistCompensationShell>
</template>
