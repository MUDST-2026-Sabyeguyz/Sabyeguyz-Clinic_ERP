import { onBeforeUnmount, ref, watch } from 'vue'
import { commissionApi, type CommissionPreview, type CommissionPreviewParams } from '@/api/client'

export function useCourseCommissionPreview(paramsSource: () => CommissionPreviewParams | null) {
  const preview = ref<CommissionPreview | null>(null)
  const loading = ref(false)
  const error = ref('')
  let requestSequence = 0

  async function refresh() {
    const params = paramsSource()
    const requestId = ++requestSequence

    preview.value = null
    error.value = ''

    if (!params) {
      loading.value = false
      return
    }

    loading.value = true
    try {
      const result = await commissionApi.preview(params)
      if (requestId === requestSequence) preview.value = result
    } catch (reason) {
      if (requestId !== requestSequence) return
      error.value = reason instanceof Error ? reason.message : 'ยังคำนวณคอมมิชชันไม่ได้'
    } finally {
      if (requestId === requestSequence) loading.value = false
    }
  }

  watch(paramsSource, () => void refresh(), { immediate: true })
  onBeforeUnmount(() => {
    requestSequence += 1
  })

  return { preview, loading, error }
}
