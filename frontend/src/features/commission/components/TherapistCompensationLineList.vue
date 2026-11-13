<script setup lang="ts">
import type { TherapistLine } from '@/api/client'
import { baht, calcBase, isCommission, lineKey, shortDate, signedBaht } from '../lib/therapistPay'

defineProps<{ lines: TherapistLine[]; month: string; emptyText: string }>()

const detailRoute = (line: TherapistLine, month: string) => ({
  name: isCommission(line) ? 'therapist-commission-detail' : 'therapist-treatment-fee-detail',
  params: { id: lineKey(line) },
  query: { month },
})
const amountText = (line: TherapistLine) =>
  line.amount === null ? 'รอคำนวณ' : line.amount < 0 ? signedBaht(line.amount) : baht(line.amount)
</script>

<template>
  <div class="therapist-line-table" role="table" aria-label="รายละเอียดค่าตอบแทน">
    <div class="therapist-line-head" role="row">
      <span>วันที่</span><span>ผู้ป่วย / HN</span><span>รายการ</span><span>บทบาท</span
      ><span>ฐานคำนวณ</span><span>จำนวน</span>
    </div>
    <RouterLink
      v-for="line in lines"
      :key="lineKey(line)"
      class="therapist-line-row"
      role="row"
      :to="detailRoute(line, month)"
    >
      <span>{{ shortDate(line.date) }}</span>
      <span
        ><b>{{ line.patientName }} HN{{ line.patientHn ?? '—' }}</b></span
      >
      <span>{{ line.item }}</span>
      <span>{{ line.role }}</span>
      <span>{{ calcBase(line) }}</span>
      <span :class="['amount', { negative: (line.amount ?? 0) < 0 }]">{{ amountText(line) }}</span>
    </RouterLink>
    <p v-if="lines.length === 0" class="therapist-line-empty" role="status">
      {{ emptyText }}
    </p>
  </div>
</template>
