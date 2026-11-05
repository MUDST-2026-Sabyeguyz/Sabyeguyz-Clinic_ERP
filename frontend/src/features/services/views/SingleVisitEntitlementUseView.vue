<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { useAppointmentProcess } from '@/features/appointments/useAppointmentProcess'
import {
  patientApi,
  singleVisitEntitlementApi,
  staffApi,
  type PatientProfile,
  type SingleVisitEntitlement,
  type Staff,
} from '@/api/client'

const route = useRoute()
const router = useRouter()
const patient = ref<PatientProfile | null>(null)
const entitlements = ref<SingleVisitEntitlement[]>([])
const therapists = ref<Staff[]>([])
const selectedEntitlementId = ref<number | null>(null)
const treatingStaffId = ref<number | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const completed = ref(false)
const {
  appointmentId,
  sourceAppointment,
  processLoading,
  processError,
  refreshAppointmentProcess,
  canStartAppointment,
  processBlockReason,
  beginAppointmentWrite,
  finishAppointmentWrite,
  recoverAppointmentWrite,
} = useAppointmentProcess()
const branchCode = computed(() => sourceAppointment.value?.branchCode ?? '')
const available = computed(() => entitlements.value.filter(
  (item) => item.remaining > 0 && item.branchCode === branchCode.value,
))
const backToAppointmentService = () => router.push({
  name: 'staff-services',
  query: { patientId: route.query.patientId, appointmentId: route.query.appointmentId },
})

onMounted(async () => {
  const patientId = Number(route.query.patientId)
  if (!Number.isInteger(patientId) || patientId < 1 || !appointmentId.value) {
    error.value = 'ต้องเปิดสิทธิ์บริการจากนัดหมายของผู้ป่วย'
    loading.value = false
    return
  }
  try {
    await refreshAppointmentProcess()
    const [profile, rights, staffList] = await Promise.all([
      patientApi.get(patientId),
      singleVisitEntitlementApi.list(patientId),
      staffApi.list(),
    ])
    patient.value = profile
    entitlements.value = rights
    therapists.value = staffList.filter((item) =>
      item.active && (item.position ?? item.role ?? '').toLowerCase() === 'therapist'
      && item.branchCode === branchCode.value,
    )
    selectedEntitlementId.value = available.value[0]?.id ?? null
    treatingStaffId.value = therapists.value[0]?.id ?? null
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'โหลดสิทธิ์บริการรายครั้งไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})

async function useEntitlement() {
  if (saving.value || !appointmentId.value || !selectedEntitlementId.value || !treatingStaffId.value) return
  saving.value = true
  error.value = ''
  try {
    await refreshAppointmentProcess()
    if (!canStartAppointment.value) throw new Error(processBlockReason.value)
    beginAppointmentWrite()
    await singleVisitEntitlementApi.use(selectedEntitlementId.value, {
      appointmentId: appointmentId.value,
      treatingStaffId: treatingStaffId.value,
    })
    finishAppointmentWrite(true)
    await refreshAppointmentProcess()
    completed.value = true
    entitlements.value = await singleVisitEntitlementApi.list(Number(route.query.patientId))
  } catch (cause) {
    await recoverAppointmentWrite(cause)
    error.value = cause instanceof Error ? cause.message : 'ใช้สิทธิ์บริการไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="single-visit-use-page">
    <StaffHeader />
    <main>
      <button type="button" class="back" @click="backToAppointmentService">← กลับหน้าบริการ</button>
      <h1>ใช้สิทธิ์บริการรายครั้ง</h1>
      <p v-if="patient">{{ patient.fullName }} · HN {{ patient.hn }} · นัดหมาย #{{ appointmentId }}</p>
      <p v-if="processLoading" role="status">กำลังตรวจสอบสถานะนัดหมาย…</p>
      <p v-else-if="processError" role="alert">{{ processError }} <button type="button" @click="refreshAppointmentProcess">ลองใหม่</button></p>
      <p v-else-if="!canStartAppointment && !completed" role="alert">{{ processBlockReason }}</p>
      <section v-if="loading" role="status">กำลังโหลดสิทธิ์บริการ…</section>
      <section v-else-if="completed" class="success-panel" role="status">
        <h2>บันทึกการรับบริการแล้ว</h2>
        <p>ตัดสิทธิ์บริการรายครั้งเรียบร้อยแล้ว</p>
        <button type="button" @click="backToAppointmentService">กลับหน้าบริการ</button>
      </section>
      <section v-else class="use-card">
        <label for="prepaid-service">บริการที่ซื้อไว้</label>
        <select id="prepaid-service" v-model.number="selectedEntitlementId" :disabled="!available.length">
          <option v-for="item in available" :key="item.id" :value="item.id">
            {{ item.serviceName }} · เหลือ {{ item.remaining }} ครั้ง · ใบเสร็จ {{ item.receiptNo }}
          </option>
        </select>
        <label for="prepaid-therapist">นักกายภาพผู้รักษา</label>
        <select id="prepaid-therapist" v-model.number="treatingStaffId">
          <option v-for="therapist in therapists" :key="therapist.id" :value="therapist.id">
            {{ therapist.fullName }}
          </option>
        </select>
        <p v-if="!available.length">ไม่มีสิทธิ์บริการรายครั้งคงเหลือ</p>
        <p v-if="!therapists.length">ไม่พบนักกายภาพในสาขานี้</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <button type="button" :disabled="saving || !available.length || !therapists.length || !canStartAppointment" @click="useEntitlement">
          {{ saving ? 'กำลังบันทึก…' : 'ยืนยันใช้สิทธิ์บริการ' }}
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.single-visit-use-page main { width: min(760px, calc(100% - 32px)); margin: 32px auto; }
.back { margin-bottom: 18px; }
.use-card, .success-panel { display: grid; gap: 14px; margin-top: 24px; padding: 24px; border: 1px solid #d7e4df; border-radius: 14px; background: #fff; }
.use-card select { min-height: 46px; padding: 8px 12px; border: 1px solid #a8c5bf; border-radius: 8px; background: #fff; }
.use-card button, .success-panel button { min-height: 46px; border: 0; border-radius: 8px; background: #18847f; color: #fff; font-weight: 700; }
.use-card button:disabled { opacity: .5; }
</style>
