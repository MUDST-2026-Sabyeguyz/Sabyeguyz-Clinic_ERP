<script setup lang="ts">
import { ref, watch } from 'vue'
import { appointmentApi, type Appointment, type AppointmentProgress } from '@/api/client'
const props = defineProps<{ appointment: Appointment; canManage: boolean }>()
const progress = ref<AppointmentProgress | null>(null)
const loading = ref(false)
const error = ref(false)
let request = 0
async function refresh() {
  const current = ++request
  loading.value = true
  error.value = false
  try {
    const result = await appointmentApi.progress(props.appointment.id)
    if (request === current) progress.value = result
  } catch {
    if (request === current) error.value = true
  } finally {
    if (request === current) loading.value = false
  }
}
watch(
  () => [props.appointment.id, props.appointment.status, props.appointment.patient?.id],
  refresh,
  { immediate: true },
)
</script>
<template>
  <div class="process-row-actions">
    <span v-if="loading">กำลังตรวจสอบ…</span>
    <button v-else-if="error" type="button" @click="refresh">ตรวจสอบอีกครั้ง</button>
    <template v-else-if="progress">
      <span v-if="progress.paymentStatus === 'REVIEW_REQUIRED'">ต้องตรวจสอบประวัติเดิม</span>
      <span v-if="progress.paymentStatus === 'PAID'">ชำระครบแล้ว</span>
      <span v-if="progress.courseStatus === 'USED'">ใช้สิทธิ์แล้ว</span>
      <span v-if="progress.paymentStatus === 'REVERSED' || progress.courseStatus === 'REVERSED'"
        >มีรายการย้อนคืน</span
      >
      <RouterLink
        :to="{
          name: 'staff-services',
          query: {
            patientId: String(appointment.patient?.id),
            appointmentId: String(appointment.id),
          },
        }"
      >
        {{
          progress.paymentStatus === 'PARTIAL' || progress.paymentStatus === 'UNPAID'
            ? 'ชำระยอดคงเหลือ'
            : progress.paymentStatus !== 'NOT_STARTED' || progress.courseStatus !== 'NOT_STARTED'
              ? 'ดูรายการเดิม'
              : 'บริการและชำระเงิน'
        }}
      </RouterLink>
      <RouterLink
        v-if="
          canManage &&
          progress.canManage &&
          progress.paymentStatus === 'NOT_STARTED' &&
          progress.courseStatus === 'NOT_STARTED'
        "
        :to="{
          name: 'staff-courses',
          query: {
            patientId: String(appointment.patient?.id),
            appointmentId: String(appointment.id),
          },
        }"
        >ใช้สิทธิ์คอร์ส</RouterLink
      >
    </template>
  </div>
</template>
<style scoped>
.process-row-actions {
  display: grid;
  justify-items: end;
  gap: 4px;
  line-height: 1.5;
}
span {
  font-size: 13px;
  color: #56665d;
}
a,
button {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  color: #145c59;
  font: inherit;
  font-size: 14px;
  text-decoration: underline;
  text-underline-offset: 3px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: right;
}
</style>
