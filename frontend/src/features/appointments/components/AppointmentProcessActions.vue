<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ApiError, type Appointment, type AppointmentProgress } from '@/api/client'
import { readAppointmentProgress } from '../appointmentRequests'
const props = defineProps<{ appointment: Appointment; canManage: boolean }>()
const progress = ref<AppointmentProgress | null>(null)
const loading = ref(false)
const error = ref('')
let request = 0
async function refresh() {
  const current = ++request
  loading.value = true
  error.value = ''
  try {
    const result = await readAppointmentProgress(props.appointment.id)
    if (request === current) progress.value = result
  } catch (cause) {
    if (request === current)
      error.value =
        cause instanceof ApiError && cause.status === 403
          ? 'ไม่มีสิทธิ์ดูข้อมูลชำระเงินของนัดนี้'
          : cause instanceof ApiError && cause.status === 401
            ? 'เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่'
            : 'โหลดข้อมูลชำระเงินไม่สำเร็จ'
  } finally {
    if (request === current) loading.value = false
  }
}
onBeforeUnmount(() => {
  ++request
})
watch(
  () => [props.appointment.id, props.appointment.status, props.appointment.patient?.id],
  refresh,
  { immediate: true },
)
</script>
<template>
  <div class="process-row-actions">
    <span v-if="loading" role="status">กำลังตรวจสอบ…</span>
    <div v-else-if="error" class="process-row-error" role="alert">
      <span>{{ error }}</span
      ><button type="button" @click="refresh">ลองใหม่</button>
    </div>
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
  font-size: 14px;
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
.process-row-error {
  display: grid;
  gap: 4px;
  justify-items: end;
}
.process-row-error span {
  color: #b42318;
}
</style>
