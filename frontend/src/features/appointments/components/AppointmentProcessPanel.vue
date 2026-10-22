<script setup lang="ts">
import { ref } from 'vue'
import {
  appointmentApi,
  paymentMethodApi,
  type AppointmentProgress,
  type PaymentMethod,
} from '@/api/client'
const props = defineProps<{ progress: AppointmentProgress }>()
const emit = defineEmits<{ refresh: [] }>()
const collecting = ref(false)
const confirming = ref(false)
const method = ref('')
const methods = ref<PaymentMethod[]>([])
const error = ref('')
const reviewReason = ref('')
const reviewing = ref(false)
const candidates = ref<{
  receipts: Array<{ id: number; label: string }>
  usages: Array<{ id: number; label: string }>
} | null>(null)
const previousReceipt = ref('')
const previousUsage = ref('')
async function openReview() {
  error.value = ''
  try {
    candidates.value = await appointmentApi.documentCandidates(props.progress.appointmentId)
    reviewing.value = true
  } catch {
    error.value = 'โหลดรายการเดิมไม่สำเร็จ กรุณาลองใหม่'
  }
}
async function finishReview() {
  if (collecting.value || !reviewReason.value.trim()) return
  collecting.value = true
  error.value = ''
  try {
    if (previousReceipt.value || previousUsage.value) {
      await appointmentApi.linkDocuments(
        props.progress.appointmentId,
        Number(previousReceipt.value) || null,
        Number(previousUsage.value) || null,
        reviewReason.value.trim(),
      )
    } else {
      await appointmentApi.verifyNoDocuments(
        props.progress.appointmentId,
        reviewReason.value.trim(),
      )
    }
    reviewing.value = false
    emit('refresh')
  } catch (problem) {
    error.value = problem instanceof Error ? problem.message : 'บันทึกผลการตรวจสอบไม่สำเร็จ'
  } finally {
    collecting.value = false
  }
}
async function reviewPayment() {
  error.value = ''
  try {
    methods.value = (await paymentMethodApi.list()).filter((item) => item.active !== false)
    method.value = methods.value[0]?.code ?? ''
    confirming.value = true
  } catch {
    error.value = 'โหลดช่องทางชำระเงินไม่สำเร็จ กรุณาลองใหม่'
  }
}
async function settle() {
  if (collecting.value || !method.value) return
  collecting.value = true
  error.value = ''
  try {
    // Full outstanding balance of the same bill; backend rechecks under a lock before collecting.
    await appointmentApi.settle(
      props.progress.appointmentId,
      props.progress.outstanding,
      method.value,
    )
    confirming.value = false
    emit('refresh')
  } catch (problem) {
    error.value =
      problem instanceof Error ? problem.message : 'รับชำระไม่สำเร็จ กรุณาตรวจสอบรายการก่อนลองใหม่'
  } finally {
    collecting.value = false
  }
}
</script>
<template>
  <section class="appointment-process-panel" aria-label="ความคืบหน้านัดหมาย">
    <h1>รายการของนัดหมายนี้</h1>
    <p>{{ progress.registered ? 'ลงทะเบียนแล้ว' : 'ยังไม่ได้ลงทะเบียนผู้ป่วย' }}</p>
    <template v-if="progress.paymentStatus === 'REVIEW_REQUIRED'">
      <p>นัดหมายเก่า: ให้ผู้จัดการตรวจสอบใบรายการและการใช้สิทธิ์เดิมก่อนทำรายการต่อ</p>
      <button v-if="progress.canReview && !reviewing" type="button" @click="openReview">
        ตรวจสอบเอกสารเดิม
      </button>
      <form v-if="reviewing && candidates" @submit.prevent="finishReview">
        <label
          >ใบรายการเดิม
          <select v-model="previousReceipt" :disabled="collecting">
            <option value="">ไม่พบใบรายการที่ตรงกับนัดนี้</option>
            <option v-for="item in candidates.receipts" :key="item.id" :value="String(item.id)">
              {{ item.label }}
            </option>
          </select></label
        >
        <label
          >รายการใช้สิทธิ์เดิม
          <select v-model="previousUsage" :disabled="collecting">
            <option value="">ไม่พบการใช้สิทธิ์ที่ตรงกับนัดนี้</option>
            <option v-for="item in candidates.usages" :key="item.id" :value="String(item.id)">
              {{ item.label }}
            </option>
          </select></label
        >
        <label
          >ผลการตรวจสอบ
          <textarea v-model="reviewReason" required :disabled="collecting" rows="3"></textarea>
        </label>
        <button type="submit" :disabled="collecting || !reviewReason.trim()">
          {{
            collecting
              ? 'กำลังบันทึก…'
              : previousReceipt || previousUsage
                ? 'ยืนยันเชื่อมเอกสารที่เลือก'
                : 'ยืนยันตรวจแล้วว่าไม่มีรายการเดิม'
          }}
        </button>
        <button type="button" :disabled="collecting" @click="reviewing = false">ยกเลิก</button>
      </form>
    </template>
    <p v-if="progress.paymentStatus === 'PAID'">ชำระครบแล้ว · {{ progress.receiptNo }}</p>
    <p v-else-if="progress.paymentStatus === 'PARTIAL' || progress.paymentStatus === 'UNPAID'">
      {{ progress.receiptNo }} · คงเหลือ
      {{ progress.outstanding.toLocaleString('th-TH', { minimumFractionDigits: 2 }) }} บาท
    </p>
    <p v-if="progress.courseStatus === 'USED'">ใช้สิทธิ์คอร์สแล้ว</p>
    <p v-if="progress.paymentStatus === 'REVERSED' || progress.courseStatus === 'REVERSED'">
      มีรายการย้อนคืน กรุณาตรวจสอบประวัติ
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="confirming" class="process-payment-review">
      <label
        >ช่องทางชำระเงิน
        <select v-model="method" :disabled="collecting">
          <option v-for="item in methods" :key="item.code" :value="item.code">
            {{ item.name }}
          </option>
        </select></label
      >
      <button type="button" :disabled="collecting || !method" @click="settle">
        {{ collecting ? 'กำลังบันทึก…' : 'ยืนยันรับชำระยอดคงเหลือ' }}
      </button>
      <button type="button" :disabled="collecting" @click="confirming = false">ยกเลิก</button>
    </div>
    <div class="process-actions">
      <button
        v-if="
          progress.canManage &&
          !confirming &&
          (progress.paymentStatus === 'PARTIAL' || progress.paymentStatus === 'UNPAID')
        "
        type="button"
        @click="reviewPayment"
      >
        ชำระยอดคงเหลือ
      </button>
      <RouterLink
        v-if="progress.patientId"
        :to="{ path: `/staff/patients/${progress.patientId}/history`, query: { tab: 'payments' } }"
        >{{ progress.saleId ? 'ดูใบรายการเดิม' : 'ดูประวัติผู้ป่วย' }}</RouterLink
      >
      <RouterLink
        v-if="progress.courseStatus !== 'NOT_STARTED'"
        :to="{
          name: 'staff-courses',
          query: {
            patientId: String(progress.patientId),
            courseId: String(progress.courseId),
            action: 'history',
            appointmentId: String(progress.appointmentId),
          },
        }"
        >ดูประวัติใช้สิทธิ์</RouterLink
      >
      <RouterLink
        v-if="
          progress.canManage &&
          progress.includesCourse &&
          progress.paymentStatus === 'PAID' &&
          progress.courseStatus === 'NOT_STARTED'
        "
        :to="{
          name: 'staff-courses',
          query: {
            patientId: String(progress.patientId),
            appointmentId: String(progress.appointmentId),
          },
        }"
        >ใช้สิทธิ์คอร์ส</RouterLink
      >
      <RouterLink :to="{ name: 'staff-appointments' }">กลับหน้านัดหมาย</RouterLink>
      <button type="button" @click="emit('refresh')">ตรวจสอบล่าสุด</button>
    </div>
  </section>
</template>
<style scoped>
.appointment-process-panel {
  max-width: 900px;
  margin: 32px auto;
  padding: 32px;
  background: #fff;
  color: #25372e;
  border: 1px solid #e6e1dc;
  border-radius: 12px;
}
h1 {
  margin: 0 0 20px;
  font-size: 28px;
}
p {
  line-height: 1.6;
}
.process-actions,
.process-payment-review {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
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
