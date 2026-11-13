<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { courseApi } from '@/api/client'

const props = defineProps<{ patientId?: number }>()
const count = ref<number | null>(null)
let timer: ReturnType<typeof setInterval> | undefined
let active = true
let loading = false
async function refresh() {
  if (loading || document.hidden) return
  loading = true
  try {
    const requests = await courseApi.extensions({ status: 'Pending' })
    if (active) count.value = requests.filter((request) =>
      request.status === 'Pending' &&
      (props.patientId == null || request.requestedPatientId === props.patientId),
    ).length
  } catch {
    if (active) count.value = null
  } finally {
    loading = false
  }
}
onMounted(() => {
  void refresh()
  timer = setInterval(refresh, 30000)
  window.addEventListener('focus', refresh)
})
onBeforeUnmount(() => {
  active = false
  clearInterval(timer)
  window.removeEventListener('focus', refresh)
})
</script>

<template>
  <RouterLink
    class="renewal-queue-link"
    :to="{ name: 'staff-course-extensions', query: patientId == null ? {} : { patientId } }"
  >
    <span>คำขอต่ออายุคอร์ส</span>
    <span v-if="count !== null" class="renewal-queue-count" aria-live="polite">
      <span class="queue-sr-only">รอพิจารณา </span>({{ count }})
    </span>
  </RouterLink>
</template>

<style scoped>
.queue-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
.renewal-queue-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 44px;
  box-sizing: border-box;
  padding: 10px 16px;
  border: 1px solid #187d78;
  border-radius: 8px;
  background: #fff;
  color: #187d78;
  text-decoration: none;
  font-weight: 600;
}
.renewal-queue-count {
  border-radius: 999px;
  padding: 3px 8px;
  background: #e6f4f1;
  color: #145c59;
  font-size: 13px;
}
.renewal-queue-link:focus-visible {
  outline: 2px solid #145c59;
  outline-offset: 3px;
}
</style>
