import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ApiError,
  appointmentApi,
  staffApi,
  type Appointment,
  type AppointmentProgress,
} from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { hasProgressRecord, normalizeAppointmentProgress } from './appointmentRequests'

// รวมการตรวจผลบริการ สิทธิ์ และการกู้สถานะการเขียนธุรกรรมของนัดหมาย
export function useAppointmentProcess() {
  const route = useRoute()
  const appointmentId = computed(() => {
    const id = Number(route.query.appointmentId)
    return Number.isSafeInteger(id) && id > 0 ? id : null
  })
  const appointmentProgress = ref<AppointmentProgress | null>(null)
  const appointment = ref<Appointment | null>(null)
  const processLoading = ref(Boolean(route.query.appointmentId))
  const processError = ref('')
  const recoveryRequired = ref(false)
  const mayManage = ref(false)
  const missingStaffIdentity = ref(false)
  let request = 0
  let activeWrite: { key: string; id: number } | null = null
  const recoveryKey = () =>
    `appointment-write:${JSON.parse(localStorage.getItem('authUser') ?? 'null')?.id ?? 'unknown'}:${appointmentId.value}`
  async function refreshAppointmentProcess() {
    // ล้างผลเดิมก่อนอ่านใหม่ เพื่อไม่ให้สลับนัดแล้วเห็นข้อมูลค้าง
    const current = ++request
    const id = appointmentId.value
    appointmentProgress.value = null
    appointment.value = null
    mayManage.value = false
    missingStaffIdentity.value = false
    processError.value = ''
    recoveryRequired.value = Boolean(id && sessionStorage.getItem(recoveryKey()))
    if (!route.query.appointmentId) {
      processLoading.value = false
      return
    }
    processLoading.value = true
    try {
      if (!id) throw new Error('ไม่พบนัดหมายต้นทาง')
      const user = JSON.parse(localStorage.getItem('authUser') ?? 'null')
      missingStaffIdentity.value = user?.role === 'THERAPIST' && !Number.isSafeInteger(user.staffId)
      const [result, booking, staff] = await Promise.all([
        appointmentApi.progress(id),
        appointmentApi.get(id),
        user?.role === 'THERAPIST' ? staffApi.list() : Promise.resolve([]),
      ])
      if (current !== request || id !== appointmentId.value) return
      const normalizedProgress = normalizeAppointmentProgress(result, id, booking)
      appointment.value = booking
      appointmentProgress.value = normalizedProgress
      if (hasProgressRecord(normalizedProgress)) {
        sessionStorage.removeItem(recoveryKey())
        recoveryRequired.value = false
      }
      // canManage จาก progress ใช้กับนัดที่ยืนยันแล้ว; การเริ่มนัด UNCONFIRMED ตรวจเจ้าของและสาขาจากนัดจริง
      // backend ยังตรวจสิทธิ์ซ้ำทุกครั้งที่บันทึก
      mayManage.value =
        user?.role === 'MANAGER'
          ? !selectedManagerBranch()?.code || selectedManagerBranch()?.code === booking.branchCode
          : user?.role === 'THERAPIST' &&
            user.branchCode === booking.branchCode &&
            Number.isSafeInteger(user.staffId) &&
            user.staffId === booking.therapist.id &&
            staff.some((person) => person.id === user.staffId && person.active !== false)
    } catch (error) {
      if (current === request)
        processError.value = error instanceof Error ? error.message : 'ตรวจสอบนัดหมายไม่สำเร็จ'
    } finally {
      if (current === request) processLoading.value = false
    }
  }
  const hasAppointmentDocument = computed(() =>
    Boolean(
      appointmentProgress.value &&
      hasProgressRecord(appointmentProgress.value),
    ),
  )
  // อนุญาตเริ่มธุรกรรมเมื่อผลตรวจล่าสุดยืนยันว่าเป็นนัด UNCONFIRMED ที่ยังไม่มีรายการ
  const processBlockReason = computed(() => {
    if (!route.query.appointmentId) return ''
    if (processError.value) return processError.value
    if (processLoading.value || !appointmentProgress.value) return 'กำลังตรวจสอบรายการของนัดหมาย'
    if (recoveryRequired.value)
      return 'ยังยืนยันผลคำขอก่อนหน้าไม่ได้ กรุณาให้ Manager ตรวจสอบก่อนทำรายการต่อ'
    if (missingStaffIdentity.value) return 'ไม่พบข้อมูลพนักงานของบัญชี กรุณาเข้าสู่ระบบใหม่'
    if (!mayManage.value) return 'ไม่มีสิทธิ์จัดการนัดหมายนี้ หรือสาขาที่เลือกไม่ตรงกับนัด'
    const progress = appointmentProgress.value
    if (hasAppointmentDocument.value)
      return 'นัดหมายนี้มีรายการแล้วหรือต้องตรวจสอบ กรุณาเปิดเอกสารเดิม'
    if (appointment.value?.status !== 'UNCONFIRMED') return 'นัดหมายนี้ไม่อยู่ในสถานะรอรับบริการ'
    const patientId = Number(route.query.patientId)
    if (!Number.isSafeInteger(patientId) || patientId <= 0)
      return 'กรุณาระบุผู้ป่วยที่ลงทะเบียนแล้วจากขั้นตอนรับบริการ'
    if (appointment.value?.patientType === 'CURRENT' && appointment.value.patient?.id !== patientId)
      return 'ผู้ป่วยที่เลือกไม่ตรงกับนัดหมาย'
    return ''
  })
  const canStartAppointment = computed(() => !processBlockReason.value)
  function beginAppointmentWrite() {
    if (!appointmentId.value) return
    // บันทึก marker ก่อน POST เพื่อให้ตรวจรายการเดิมได้หลัง timeout หรือ refresh
    activeWrite = { key: recoveryKey(), id: appointmentId.value }
    sessionStorage.setItem(activeWrite.key, 'pending')
    recoveryRequired.value = true
  }
  function finishAppointmentWrite(expectVisit = true) {
    if (!activeWrite) return
    if (!expectVisit) {
      // ซื้อคอร์สอย่างเดียวไม่สร้าง Visit ของนัดหมาย
      sessionStorage.removeItem(activeWrite.key)
      if (activeWrite.id === appointmentId.value) recoveryRequired.value = false
      activeWrite = null
      return
    }
    // ถ้าอ่านผลหลัง POST ไม่ได้ ให้คง marker เพื่อกันการส่งซ้ำ
    sessionStorage.setItem(activeWrite.key, 'response-received')
    activeWrite = null
  }
  async function recoverAppointmentWrite(error?: unknown) {
    if (!activeWrite) return
    const id = activeWrite.id
    // เก็บ marker เมื่อผลลัพธ์ยังไม่ชัดเจน แต่ล้างเมื่อ backend ปฏิเสธข้อมูล/สิทธิ์แน่นอน
    if (error instanceof ApiError && [400, 401, 403, 422].includes(error.status)) {
      sessionStorage.removeItem(activeWrite.key)
      activeWrite = null
    }
    if (id === appointmentId.value) await refreshAppointmentProcess()
  }
  watch(
    () => [route.query.appointmentId, route.query.patientId, selectedManagerBranch()?.code],
    () => {
      void refreshAppointmentProcess()
    },
    { flush: 'sync' },
  )
  onBeforeUnmount(() => {
    ++request
  })
  return {
    appointmentId,
    sourceAppointment: appointment,
    appointmentProgress,
    processLoading,
    processError,
    refreshAppointmentProcess,
    hasAppointmentDocument,
    canStartAppointment,
    processBlockReason,
    recoveryRequired,
    beginAppointmentWrite,
    finishAppointmentWrite,
    recoverAppointmentWrite,
  }
}
