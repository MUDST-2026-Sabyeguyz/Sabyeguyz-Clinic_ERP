<script setup lang="ts">
import { computed } from 'vue'
import type { AppointmentProgress } from '@/api/client'
import { progressStateLabel } from '../appointmentRequests'
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
const appointmentStatusLabel = (status: AppointmentProgress['appointmentStatus']) => ({
  UNCONFIRMED: 'ยังไม่ยืนยัน', CONFIRMED: 'ยืนยัน', CANCELLED: 'ยกเลิก',
})[status]
</script>
<template>
  <div class="appointment-process-wrapper">
    <button v-if="fromServices" type="button" class="back-to-patients" @click="emit('backToPatients')">← กลับไปเลือกผู้ป่วย</button>
    <section class="appointment-process-panel" aria-label="ผลบริการของนัดหมาย">
      <h1>รายการของนัดหมายนี้</h1>
      <p>{{ progressStateLabel(progress.state) }}</p>
      <p>สถานะนัดหมาย: {{ appointmentStatusLabel(progress.appointmentStatus) }} · {{ progress.patientType }}</p>
      <p v-if="progress.reason" role="status">{{ progress.reason }}</p>
      <p v-if="blockReason">{{ blockReason }}</p>
      <p v-if="recoveryRequired" role="alert">คำขอก่อนหน้าอาจบันทึกสำเร็จแล้ว กรุณาตรวจสอบเอกสารก่อนทำรายการใหม่</p>
      <p v-if="progress.visitId">Visit #{{ progress.visitId }}</p>
      <p v-if="progress.servedPatientId">ผู้ป่วย #{{ progress.servedPatientId }}</p>
      <p v-if="progress.completedAt">รับบริการเมื่อ {{ new Date(progress.completedAt).toLocaleString('th-TH', { timeZone: 'Asia/Bangkok' }) }}</p>
      <div v-for="receipt in receiptDocuments" :key="receipt.receiptId" class="process-document">
        <RouterLink :to="{ name: 'staff-receipt', params: { id: receipt.saleId } }">
          {{ receipt.receiptNo || `รายการขาย #${receipt.saleId}` }}
        </RouterLink>
        <span>
          สถานะใบเสร็จ {{ receipt.receiptStatus }}
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
        การใช้คอร์ส #{{ usage.coursePurchaseId }} · {{ usage.status }}
      </RouterLink>
      <div class="process-actions">
        <RouterLink v-if="progress.servedPatientId" :to="{ path: '/staff/patients/' + progress.servedPatientId + '/history' }">ดูประวัติผู้ป่วย</RouterLink>
        <button type="button" @click="emit('refresh')">ตรวจสอบล่าสุด</button>
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
  color: #25372e;
  border: 1px solid #e6e1dc;
  border-radius: 12px;
}
.back-to-patients {
  display: inline-block;
  border: 0;
  padding: 0;
  margin: 0;
  background: transparent;
  color: #176c68;
  font: inherit;
  cursor: pointer;
  transition: transform 180ms ease;
}
.back-to-patients:hover {
  text-decoration: underline;
  transform: translateX(-4px);
}
.back-to-patients:focus-visible {
  outline: 2px solid #176c68;
  outline-offset: 3px;
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
  color: #145c59;
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
  outline: 3px solid #145c59;
  outline-offset: 3px;
}
</style>
