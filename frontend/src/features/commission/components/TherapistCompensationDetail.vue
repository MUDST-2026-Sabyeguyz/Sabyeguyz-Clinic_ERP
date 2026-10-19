<script setup lang="ts">
import { computed } from 'vue'
import type { TherapistLine } from '@/api/client'
import {
  baht,
  feeRuleText,
  lineKey,
  lineStatusText,
  longDate,
  monthLabel,
  percent,
  signedBaht,
} from '../lib/therapistPay'
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'

const props = defineProps<{
  kind: 'commission' | 'treatment'
  line: TherapistLine | null
  month: string
}>()

type Row = readonly [label: string, value: string, hint: string]

const commissionRows = computed<Row[]>(() => {
  const line = props.line
  if (!line) return []
  return [
    ['บริการ', line.item, `วันที่รายการ ${longDate(line.date)}`],
    ['ยอดบริการก่อนส่วนลด', baht(line.grossAmount), `รายการต้นทาง ${line.saleNo ?? '—'}`],
    ['ส่วนลดระดับรายการ', signedBaht(-(line.lineDiscount ?? 0)), 'ลดฐานค่าคอมมิชชัน'],
    [
      'ฐานค่าคอมมิชชัน',
      baht(line.base),
      `${baht(line.grossAmount)} − ${baht(line.lineDiscount)} = ${baht(line.base)}`,
    ],
    [
      'Tier และอัตราที่ล็อก',
      line.rate === null ? 'รอปิดยอดเดือนที่ขาย' : `${line.tier ?? 'Tier'} (${percent(line.rate)})`,
      line.rate === null ? 'ยังไม่ล็อกอัตรา' : `อัตราที่ใช้สำหรับงวด${monthLabel(props.month)}`,
    ],
    [
      'ค่าคอมมิชชันสุทธิ',
      line.amount === null ? 'รอคำนวณ' : baht(line.amount),
      line.rate === null
        ? 'รอคำนวณ'
        : `${baht(line.base)} × ${percent(line.rate)} = ${baht(line.pool)} ÷ ${line.totalVisits ?? 1} ครั้ง · ${lineStatusText(line.paymentStatus)}`,
    ],
  ]
})

const treatmentRows = computed<Row[]>(() => {
  const line = props.line
  if (!line) return []
  return [
    ['บริการ', line.item, `วันที่รักษา ${longDate(line.date)}`],
    ['เจ้าของเคส', line.ownerName ?? '—', 'ผู้รับค่าคอมมิชชันหลัก'],
    ['ผู้ให้บริการจริง', line.treatingName ?? '—', 'ผู้ได้รับค่ามือ'],
    [
      'วิธีคำนวณ',
      line.feeType === 'Percentage' ? 'เปอร์เซ็นต์ของยอด' : 'จำนวนเงินคงที่',
      'ตามกฎนักกายภาพทั่วไป',
    ],
    [
      'อัตราค่ามือตามกฎ',
      line.feeType === 'Percentage' ? feeRuleText(line) : baht(line.feeValue),
      'อัตราตามกฎที่มีผล ณ วันรักษา',
    ],
    ['ค่ามือสุทธิที่ได้รับ', baht(line.amount), lineStatusText(line.paymentStatus)],
  ]
})

const rows = computed(() =>
  props.kind === 'commission' ? commissionRows.value : treatmentRows.value,
)
const source = computed(() => ({
  name: props.kind === 'commission' ? 'therapist-commission-source' : 'therapist-treatment-source',
  params: { id: props.line ? lineKey(props.line) : '' },
  query: { month: props.month },
}))
</script>

<template>
  <section class="therapist-detail-card therapist-figma-detail-card">
    <h2>
      {{ kind === 'commission' ? 'รายละเอียดค่าคอมมิชชัน' : 'รายละเอียดค่ามือ' }}
    </h2>
    <p class="therapist-detail-period">
      {{
        kind === 'commission'
          ? `งวด${monthLabel(month)} · ระบบคำนวณให้อัตโนมัติ`
          : `${line?.visitNo ?? 'Visit'} · งวด${monthLabel(month)} · อ่านอย่างเดียว`
      }}
    </p>
    <dl v-if="line" class="therapist-calculation-list">
      <div
        v-for="([label, value, hint], index) in rows"
        :key="label"
        :class="{ total: index === rows.length - 1 }"
      >
        <dt>{{ label }}</dt>
        <dd>{{ value }}</dd>
        <small>{{ hint }}</small>
      </div>
    </dl>
    <TherapistCompensationEmptyState v-else />
  </section>
  <div class="therapist-detail-actions">
    <RouterLink
      class="therapist-back-action"
      :to="{ name: 'therapist-compensation', query: { tab: kind, month } }"
      >←&nbsp;&nbsp; กลับภาพรวม</RouterLink
    >
    <RouterLink v-if="line" class="therapist-primary-action" :to="source">
      {{ kind === 'commission' ? 'ดูรายการขายต้นทาง' : 'ดูรายการรักษาต้นทาง' }}
    </RouterLink>
  </div>
</template>
