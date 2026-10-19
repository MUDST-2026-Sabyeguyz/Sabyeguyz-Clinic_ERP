<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, calendarApi, type CalendarStatus } from '@/api/client'

// The two-way Google Calendar sync as the clinic tested it: every branch has its own calendar, a booking goes to Google, and a
// move or delete in Google comes back. A therapist sees the status and opens the calendars; the Manager also syncs on demand.
const status = ref<CalendarStatus | null>(null)
const message = ref('')
const busy = ref(false)
const isManager = (() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
  } catch {
    return false
  }
})()
const calendars = computed(() => (status.value?.branches ?? []).filter((branch) => branch.googleCalendarId))
const openUrl = (calendarId: string) =>
  `https://calendar.google.com/calendar/embed?src=${encodeURIComponent(calendarId)}&ctz=Asia%2FBangkok`

async function load() {
  try {
    status.value = await calendarApi.status()
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'ตรวจสถานะ Google Calendar ไม่สำเร็จ'
  }
}
async function syncNow() {
  busy.value = true
  message.value = ''
  try {
    const result = await calendarApi.syncNow()
    message.value =
      `ซิงก์แล้ว: ส่งไป Google ${result.pushed}, ลบ ${result.removed}, เลื่อนจาก Google ${result.movedFromGoogle}, ` +
      `ยกเลิกจาก Google ${result.cancelledFromGoogle}, ล้มเหลว ${result.failed}`
    await load()
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'ซิงก์ไม่สำเร็จ'
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="google-calendar-panel">
    <p v-if="status && !status.enabled">ยังไม่เปิดการซิงก์กับ Google Calendar</p>
    <ul v-else-if="status" class="google-calendar-list">
      <li v-for="branch in status.branches" :key="branch.branchCode">
        <b>{{ branch.branchName }}</b>
        <template v-if="branch.googleCalendarId">
          · รอซิงก์ {{ branch.waiting }} · ล้มเหลว {{ branch.failed }} ·
          <a :href="openUrl(branch.googleCalendarId)" target="_blank" rel="noopener noreferrer">เปิด Google Calendar</a>
        </template>
        <template v-else> · ยังไม่ได้ผูกปฏิทิน</template>
      </li>
      <li v-if="calendars.length === 0">ยังไม่มีสาขาที่ผูกปฏิทิน</li>
    </ul>
    <p v-else>กำลังตรวจสถานะ…</p>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="!isManager" class="google-calendar-note">การตั้งค่าและการซิงก์ทันทีทำได้เฉพาะผู้จัดการ</p>
    <div v-else class="google-calendar-actions">
      <button type="button" :disabled="busy || !status?.enabled" @click="syncNow">ซิงก์ทันที</button>
      <RouterLink to="/staff/calendar">ตั้งค่าปฏิทิน Google</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.google-calendar-panel {
  display: grid;
  gap: 10px;
  text-align: left;
}
.google-calendar-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
}
.google-calendar-note {
  color: #6b7d76;
  font-size: 13px;
}
.google-calendar-actions {
  display: flex;
  gap: 14px;
  align-items: center;
}
.google-calendar-actions button {
  padding: 9px 16px;
  border: 0;
  border-radius: 8px;
  background: #1e807c;
  color: #fff;
  font: inherit;
  cursor: pointer;
}
.google-calendar-actions button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
