<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { calendarApi, type CalendarStatus } from '@/api/client'

const props = defineProps<{ branchCode: string }>()
const status = ref<CalendarStatus | null>(null)
const loading = ref(true)
const failed = ref(false)

async function refresh() {
  loading.value = true
  failed.value = false
  try {
    status.value = await calendarApi.status()
  } catch {
    status.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

const indicator = computed(() => {
  if (loading.value) return { state: 'neutral', text: 'กำลังตรวจสอบสถานะ Google Calendar' }
  if (failed.value || !status.value)
    return { state: 'warning', text: 'ตรวจสอบสถานะ Google Calendar ไม่สำเร็จ' }
  if (!status.value.enabled)
    return { state: 'warning', text: 'ยังไม่เปิดใช้งานการซิงก์ Google Calendar' }
  const branches =
    props.branchCode === 'ทั้งหมด'
      ? status.value.branches
      : status.value.branches.filter((branch) => branch.branchCode === props.branchCode)
  if (!branches.length || branches.some((branch) => !branch.googleCalendarId)) {
    return { state: 'warning', text: 'ยังไม่ได้ตั้งค่า Google Calendar สำหรับสาขาที่เลือก' }
  }
  const waiting = branches.reduce((total, branch) => total + branch.waiting, 0)
  const errors = branches.reduce((total, branch) => total + branch.failed, 0)
  if (errors)
    return {
      state: 'warning',
      text: `Google Calendar: ซิงก์ไม่สำเร็จ ${errors} รายการ${waiting ? ` · รอซิงก์ ${waiting} รายการ` : ''}`,
    }
  if (waiting) return { state: 'pending', text: `Google Calendar: รอซิงก์ ${waiting} รายการ` }
  if (!status.value.lastRunAt)
    return {
      state: 'pending',
      text: 'Google Calendar: ตั้งค่าการซิงก์แล้ว · ยังไม่มีข้อมูลรอบซิงก์ล่าสุด',
    }
  if (status.value.lastResult?.failed)
    return { state: 'warning', text: 'Google Calendar: การซิงก์รอบล่าสุดของระบบมีรายการล้มเหลว' }
  return { state: 'success', text: 'Google Calendar: ไม่มีรายการรอซิงก์หรือผิดพลาดในสาขาที่แสดง' }
})

onMounted(refresh)
</script>

<template>
  <div class="sync-indicator" :data-state="indicator.state" role="status">
    <span>{{ indicator.text }}</span>
    <button type="button" :disabled="loading" @click="refresh">ตรวจสอบอีกครั้ง</button>
  </div>
</template>

<style scoped>
.sync-indicator {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  width: fit-content;
  margin: 12px 0 16px;
  padding: 9px 14px;
  border-radius: 8px;
  background: #edf3f0;
  color: #35564b;
  font-size: 14px;
}
.sync-indicator[data-state='success'] {
  background: #e5f5e9;
  color: #267449;
}
.sync-indicator[data-state='warning'] {
  background: #fff2dc;
  color: #805718;
}
.sync-indicator[data-state='pending'] {
  background: #eaf2fa;
  color: #355d7d;
}
.sync-indicator button {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  font: inherit;
}
.sync-indicator button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
