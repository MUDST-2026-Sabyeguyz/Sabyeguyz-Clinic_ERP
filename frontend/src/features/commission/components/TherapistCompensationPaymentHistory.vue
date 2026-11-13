<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import type { StaffPayment, TherapistAdjustment } from '@/api/client'
import { baht, currentMonth, longDate, monthLabel, signedBaht } from '../lib/therapistPay'

const props = defineProps<{
  payments: StaffPayment[]
  adjustments: TherapistAdjustment[]
  month: string
}>()

const allPaid = computed(() => props.payments.filter((payment) => payment.status === 'PAID'))

const monthNames = Array.from({ length: 12 }, (_, index) =>
  new Intl.DateTimeFormat('th-TH', { month: 'long' }).format(new Date(2026, index, 1)),
)
const chosen = ref('')
watch(
  allPaid,
  (rows) => {
    if (chosen.value) return
    chosen.value =
      rows
        .map((row) => row.month)
        .sort()
        .pop() ?? currentMonth()
  },
  { immediate: true },
)
const chosenYear = computed(() => Number(chosen.value.slice(0, 4)))
const chosenMonth = computed(() => chosen.value.slice(5, 7))
const years = computed(() => {
  const thisYear = Number(currentMonth().slice(0, 4))
  const seen = allPaid.value.map((row) => Number(row.month.slice(0, 4)))
  const first = Math.min(thisYear - 2, ...seen)
  const last = Math.max(thisYear, ...seen)
  return Array.from({ length: last - first + 1 }, (_, index) => last - index)
})
const chooseYear = (value: string) => {
  chosen.value = `${value}-${chosenMonth.value}`
}
const moveMonth = (offset: number) => {
  const date = new Date(chosenYear.value, Number(chosenMonth.value) - 1 + offset, 1)
  chosen.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}
const paid = computed(() => allPaid.value.filter((row) => row.month === chosen.value))
const adjustmentOfMonth = computed(
  () =>
    props.adjustments.find(
      (row) =>
        row.appliedMonth === props.month ||
        row.applications.some((application) => application.month === props.month),
    ) ??
    props.adjustments[0] ??
    null,
)
const paidOn = (payment: StaffPayment) =>
  payment.paidDate
    ? longDate(payment.paidDate)
    : payment.paidAt
      ? longDate(payment.paidAt.slice(0, 10))
      : '—'
</script>

<template>
  <section class="therapist-payment-filter">
    <label>รอบเดือน</label>
    <div class="therapist-payment-range">
      <div class="therapist-month-stepper" aria-label="เลือกเดือน">
        <button type="button" aria-label="เดือนก่อน" @click="moveMonth(-1)">‹</button>
        <strong>{{ monthNames[Number(chosenMonth) - 1] }}</strong>
        <button type="button" aria-label="เดือนถัดไป" @click="moveMonth(1)">›</button>
      </div>
      <FilterDropdown
        :model-value="String(chosenYear)"
        :options="years.map((year) => ({ value: String(year), label: String(year + 543) }))"
        label="ปี"
        default-label="เลือกปี"
        :clear-on-reselect="false"
        hide-default-option
        @update:model-value="chooseYear"
      />
    </div>
  </section>
  <section class="therapist-payment-card">
    <div class="therapist-payment-table" role="table">
      <div class="therapist-payment-head" role="row">
        <span>รอบเดือน</span><span>Commission</span><span>ค่ามือ</span><span>Adjustment</span
        ><span>ยอดสุทธิ</span><span>วันที่จ่าย</span><span>สถานะ</span>
      </div>
      <div
        v-for="item in paid"
        :key="item.id"
        class="therapist-payment-head therapist-payment-row"
        role="row"
      >
        <span>{{ monthLabel(item.month) }}</span>
        <span>{{ baht(item.courseCommission) }}</span>
        <span>{{ baht((item.singleVisitFee ?? 0) + (item.substituteFee ?? 0)) }}</span>
        <span :class="{ negative: (item.adjustment ?? 0) < 0 }">{{
          signedBaht(item.adjustment)
        }}</span>
        <span>{{ baht(item.net) }}</span>
        <span>
          {{ paidOn(item) }}
          <small v-if="item.paymentReference" class="therapist-payment-ref"
            >อ้างอิง {{ item.paymentReference }}</small
          >
        </span>
        <span class="therapist-paid-status">จ่ายแล้ว · Paid</span>
      </div>
      <p v-if="paid.length === 0" class="therapist-line-empty" role="status">
        {{
          allPaid.length === 0
            ? 'ยังไม่มีงวดที่จ่ายแล้ว ยอดจะแสดงที่นี่เมื่อ Manager ยืนยันการจ่าย'
            : 'ไม่มีงวดที่จ่ายแล้วในเดือนที่เลือก'
        }}
      </p>
    </div>
    <p class="therapist-payment-formula">
      ยอดสุทธิ = Commission + ค่ามือ + Adjustment ที่ผ่านการปิดงวดแล้ว
    </p>
    <RouterLink
      v-if="adjustmentOfMonth"
      class="therapist-primary-action"
      :to="{
        name: 'therapist-adjustment-detail',
        params: { id: String(adjustmentOfMonth.id) },
      }"
      >ดู Adjustment เดือนนี้</RouterLink
    >
    <p v-else class="therapist-payment-formula">ยังไม่มี Adjustment</p>
  </section>
</template>
