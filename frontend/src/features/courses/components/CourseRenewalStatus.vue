<script setup lang="ts">
import { ref, watch } from 'vue'
import { courseApi, type CourseExtensionRequest } from '@/api/client'

const props = defineProps<{ courseId: number; revision: number }>()
const latest = ref<CourseExtensionRequest>()
const failed = ref(false)
const retry = ref(0)
const labels = { Pending: 'รอพิจารณา', Approved: 'อนุมัติแล้ว', Rejected: 'ไม่อนุมัติ' }

watch(
  () => [props.courseId, props.revision, retry.value],
  async (_, __, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    failed.value = false
    try {
      const requests = await courseApi.extensions({ courseId: props.courseId })
      if (current) latest.value = requests.reduce<CourseExtensionRequest | undefined>(
        (newest, request) => !newest || request.id > newest.id ? request : newest,
        undefined,
      )
    } catch {
      if (current) failed.value = true
    }
  },
  { immediate: true },
)
</script>

<template>
  <button v-if="failed" type="button" class="renewal-retry" @click="retry++">
    โหลดสถานะต่ออายุอีกครั้ง
  </button>
  <p v-else-if="latest" class="renewal-status" role="status">
    คำขอต่ออายุ
    <span :class="latest.status.toLowerCase()">{{ labels[latest.status] }}</span>
  </p>
</template>

<style scoped>
.renewal-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 8px 0 0;
  font-size: 13px;
}
.renewal-status span {
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 700;
}
.pending { background: #fff; color: #76500a; }
.approved { background: #def4e9; color: #205e46; }
.rejected { background: #fce7e5; color: #952e29; }
.renewal-retry {
  margin-top: 8px;
  border: 0;
  padding: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
