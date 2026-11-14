<script setup lang="ts">
import { computed } from 'vue'
import type { AppointmentProgress } from '@/api/client'
const props = defineProps<{ progress: AppointmentProgress; fromServices?: boolean; blockReason?: string; recoveryRequired?: boolean }>()
const emit = defineEmits<{ refresh: []; backToPatients: [] }>()
const receiptDocuments = computed(() => {
  const byReceiptId = new Map<number, AppointmentProgress['sales']>()
  for (const sale of props.progress.sales) {
    const group = byReceiptId.get(sale.receiptId) ?? []
    group.push(sale)
    byReceiptId.set(sale.receiptId, group)
  }
  return [...byReceiptId.values()].map((sales) => ({
    ...sales[0]!,
    active: sales.every((sale) => sale.active),
    saleCount: sales.length,
  }))
})
const hasRecord = computed(() => props.progress.sales.length > 0 || props.progress.usages.length > 0)
const headline = computed(() =>
  props.progress.state === 'REVIEW_REQUIRED' || !hasRecord.value
    ? 'นัดหมายนี้ต้องตรวจสอบรายการ'
    : 'นัดหมายนี้ทำรายการแล้ว',
)
const summary = computed(() => {
  if (props.progress.state === 'REVIEW_REQUIRED') return 'พบรายการที่ต้องตรวจสอบ กรุณาเปิดเอกสารเดิมก่อนทำรายการต่อ'
  if (props.progress.usages.length && props.progress.sales.length) return 'นัดหมายนี้มีทั้งการชำระเงินและการใช้สิทธิ์คอร์สแล้ว'
  if (props.progress.usages.length) return 'นัดหมายนี้ใช้สิทธิ์คอร์สไปแล้ว ไม่สามารถทำรายการซ้ำได้'
  if (props.progress.sales.length) return 'นัดหมายนี้ชำระเงินไปแล้ว ไม่สามารถทำรายการซ้ำได้'
  return ''
})
const usageStatusLabel = (status: string) =>
  ({ Completed: 'สำเร็จ', Reversed: 'ย้อนรายการแล้ว' } as Record<string, string>)[status] ?? status
const appointmentStatusLabel = (status: AppointmentProgress['appointmentStatus']) => ({
  UNCONFIRMED: 'ยังไม่ยืนยัน', CONFIRMED: 'ยืนยัน', CANCELLED: 'ยกเลิก',
})[status]
</script>
<template>
  <div class="appointment-process-wrapper">
    <button v-if="fromServices" type="button" class="back-to-patients app-back-link" @click="emit('backToPatients')">← กลับไปเลือกผู้ป่วย</button>
    <section class="appointment-process-panel" aria-label="ผลบริการของนัดหมาย">
      <h1>{{ headline }}</h1>
      <p v-if="summary">{{ summary }}</p>
      <p>สถานะนัดหมาย: {{ appointmentStatusLabel(progress.appointmentStatus) }}</p>
      <p v-if="progress.completedAt">รับบริการเมื่อ {{ new Date(progress.completedAt).toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' }) }}</p>
      <p v-if="progress.reason" role="status">{{ progress.reason }}</p>
      <p v-if="blockReason && !hasRecord">{{ blockReason }}</p>
      <p v-if="recoveryRequired" role="alert">คำขอก่อนหน้าอาจบันทึกสำเร็จแล้ว กรุณาตรวจสอบเอกสารก่อนทำรายการใหม่</p>
      <div v-for="receipt in receiptDocuments" :key="receipt.receiptId" class="process-document">
        <RouterLink :to="{ name: 'staff-receipt', params: { id: receipt.saleId } }">
          ดูใบเสร็จ {{ receipt.receiptNo || `#${receipt.saleId}` }}
        </RouterLink>
        <span>
          {{ receipt.receiptStatus }}
          <template v-if="receipt.saleCount > 1"> · {{ receipt.saleCount }} รายการขาย</template>
          <template v-if="!receipt.active"> · รายการไม่ทำงาน</template>
        </span>
      </div>
      <RouterLink
        v-for="usage in progress.usages"
        :key="usage.usageId"
        :to="{
          name: 'staff-courses',
          query: {
            patientId: progress.servedPatientId ? String(progress.servedPatientId) : undefined,
            courseId: String(usage.coursePurchaseId),
            action: 'history',
            appointmentId: String(progress.appointmentId),
          },
        }"
      >
        ดูประวัติการใช้คอร์ส ({{ usageStatusLabel(usage.status) }})
      </RouterLink>
      <div class="process-actions">
        <RouterLink v-if="progress.servedPatientId" :to="{ path: '/staff/patients/' + progress.servedPatientId + '/history' }">ดูประวัติผู้ป่วย</RouterLink>
        <RouterLink :to="{ name: 'staff-appointments' }">กลับไปหน้านัดหมาย</RouterLink>
        <button v-if="progress.state === 'REVIEW_REQUIRED' || recoveryRequired" type="button" @click="emit('refresh')">ตรวจสอบสถานะอีกครั้ง</button>
      </div>
    </section>
  </div>
</template>
<style scoped>
.appointment-process-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
  max-width: 900px;
  margin: 32px auto;
  padding: 32px;
  background: #fff;
  color: var(--color-ink);
  border: 1px solid #e6e1dc;
  border-radius: 12px;
}
.back-to-patients {
  margin: 0;
}
h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
p {
  margin: 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.appointment-process-panel > a,
.process-document > a {
  justify-self: start;
  max-width: 100%;
}
.process-document {
  display: grid;
  justify-items: start;
  gap: 2px;
}
.appointment-process-panel a,
.appointment-process-panel button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1.5;
  white-space: normal;
  overflow-wrap: anywhere;
}
.process-actions,
.process-payment-review {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
@media (max-width: 600px) {
  .appointment-process-panel {
    margin: 20px 12px;
    padding: 20px;
  }
  .process-actions > * {
    max-width: 100%;
  }
}
form,
form label {
  display: grid;
  gap: 12px;
  margin: 16px 0;
}
textarea,
select {
  max-width: 100%;
  font: inherit;
}
button,
a,
select {
  min-height: 44px;
  padding: 10px 14px;
  box-sizing: border-box;
  border: 1px solid #8fb5ae;
  border-radius: 8px;
  background: #fff;
  color: var(--color-primary-strong);
  font: inherit;
  text-decoration: none;
}
button {
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
:is(button, a, select):focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
}
</style>
