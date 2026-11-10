<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ApiError, type Appointment, type AppointmentProgress } from '@/api/client'
import { progressStateLabel, readAppointmentProgress } from '../appointmentRequests'
const props = defineProps<{ appointment: Appointment; canManage: boolean }>()
const progress = ref<AppointmentProgress | null>(null)
const loading = ref(false)
const error = ref('')
const receiptDocuments = computed(() => {
  const byReceiptId = new Map<number, AppointmentProgress['sales']>()
  for (const sale of progress.value?.sales ?? []) {
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
let request = 0
async function refresh() {
  // ใช้เลขคำขอทิ้งผลเก่าที่กลับมาหลังเปลี่ยนนัดหรือเริ่มโหลดรอบใหม่
  const current = ++request
  progress.value = null
  loading.value = true
  error.value = ''
  try {
    const result = await readAppointmentProgress(props.appointment.id, props.appointment)
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
      <span>{{ progressStateLabel(progress.state) }}</span>
      <span v-if="progress.reason" class="process-row-error">{{ progress.reason }}</span>
      <RouterLink
        v-for="receipt in receiptDocuments"
        :key="receipt.receiptId"
        :to="{ name: 'staff-receipt', params: { id: receipt.saleId } }"
      >
        {{ receipt.receiptNo || `เปิดรายการขาย #${receipt.saleId}` }}
      </RouterLink>
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
        ใช้คอร์ส #{{ usage.coursePurchaseId }} · {{ usage.status }}
      </RouterLink>
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
