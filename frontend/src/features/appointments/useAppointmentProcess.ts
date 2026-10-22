import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { appointmentApi, type AppointmentProgress } from '@/api/client'

// Read committed documents again on entry. Navigation/localStorage is not proof of payment or usage.
export function useAppointmentProcess() {
  const route = useRoute()
  const appointmentId = computed(() => {
    const value = Number(route.query.appointmentId)
    return Number.isSafeInteger(value) && value > 0 ? value : null
  })
  const appointmentProgress = ref<AppointmentProgress | null>(null)
  const processLoading = ref(Boolean(route.query.appointmentId))
  const processError = ref('')
  let request = 0
  async function refreshAppointmentProcess() {
    const current = ++request
    if (!route.query.appointmentId) {
      processLoading.value = false
      return
    }
    processLoading.value = true
    processError.value = ''
    try {
      if (!appointmentId.value) throw new Error('ไม่พบนัดหมายต้นทาง')
      const result = await appointmentApi.progress(appointmentId.value)
      if (current === request) appointmentProgress.value = result
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
      (appointmentProgress.value.paymentStatus !== 'NOT_STARTED' ||
        appointmentProgress.value.courseStatus !== 'NOT_STARTED'),
    ),
  )
  return {
    appointmentId,
    appointmentProgress,
    processLoading,
    processError,
    refreshAppointmentProcess,
    hasAppointmentDocument,
  }
}
