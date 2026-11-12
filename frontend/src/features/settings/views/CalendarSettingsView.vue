<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { ApiError, calendarApi, type CalendarStatus } from '@/api/client'

const status = ref<CalendarStatus | null>(null)
const calendarIds = reactive<Record<string, string>>({})
const loading = ref(true)
const busy = ref('')
const error = ref('')
const message = ref('')

function messageOf(caught: unknown) {
  return caught instanceof ApiError ? caught.message : 'เชื่อมต่อเซิร์ฟเวอร์ไม่ได้'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    status.value = await calendarApi.status()
    for (const branch of status.value.branches) calendarIds[branch.branchCode] = branch.googleCalendarId ?? ''
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    loading.value = false
  }
}

async function save(branchCode: string) {
  busy.value = branchCode
  error.value = ''
  message.value = ''
  try {
    await calendarApi.setBranchCalendar(branchCode, calendarIds[branchCode] ?? '')
    message.value = calendarIds[branchCode]?.trim() ? 'บันทึกปฏิทินของสาขาแล้ว' : 'เลิกซิงก์ปฏิทินของสาขาแล้ว'
    await load()
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

async function syncNow() {
  busy.value = 'sync'
  error.value = ''
  message.value = ''
  try {
    const result = await calendarApi.syncNow()
    message.value =
      `ซิงก์แล้ว: ส่งไป Google ${result.pushed}, ลบ ${result.removed}, ` +
      `เลื่อนจาก Google ${result.movedFromGoogle}, ยกเลิกจาก Google ${result.cancelledFromGoogle}, ล้มเหลว ${result.failed}`
    await load()
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('th-TH', { dateStyle: 'medium', timeStyle: 'short' }) : '-'
}

onMounted(load)
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <div class="settings-title">
        <h1>Google Calendar</h1>
        <p>ซิงก์นัดหมายกับปฏิทิน Google 2 ทาง แยก 1 ปฏิทินต่อสาขา</p>
      </div>

      <section class="settings-panel calendar-panel">
        <p v-if="loading" data-cy="calendar-loading">กำลังโหลด...</p>
        <template v-else-if="status">
          <div class="calendar-state" data-cy="calendar-state">
            <strong>{{ status.enabled ? 'เปิดใช้งานการซิงก์' : 'ยังไม่ได้เปิดใช้งาน' }}</strong>
            <span v-if="!status.enabled">
              ให้ผู้ดูแลเซิร์ฟเวอร์ตั้งค่า APP_CALENDAR_ENABLED และไฟล์คีย์ service account ก่อน (ดู docs/google-calendar-sync.md)
            </span>
            <span v-else>
              รอบล่าสุด: {{ formatTime(status.lastRunAt) }} · ระยะเวลานัดในปฏิทิน {{ status.defaultDurationMinutes }} นาที
            </span>
          </div>

          <table class="calendar-table">
            <thead>
              <tr><th>สาขา</th><th>Calendar ID</th><th>รอซิงก์</th><th>ล้มเหลว</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-for="branch in status.branches" :key="branch.branchCode" :data-cy="`calendar-row-${branch.branchCode}`">
                <td>{{ branch.branchName }} <small>({{ branch.branchCode }})</small></td>
                <td>
                  <input
                    v-model="calendarIds[branch.branchCode]"
                    :data-cy="`calendar-id-${branch.branchCode}`"
                    placeholder="xxxx@group.calendar.google.com"
                    aria-label="Calendar ID"
                  />
                </td>
                <td>{{ branch.waiting }}</td>
                <td :class="{ 'calendar-failed': branch.failed > 0 }">{{ branch.failed }}</td>
                <td>
                  <button
                    class="primary"
                    :data-cy="`calendar-save-${branch.branchCode}`"
                    :disabled="busy !== ''"
                    @click="save(branch.branchCode)"
                  >
                    บันทึก
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <p class="calendar-hint">เว้นช่อง Calendar ID ว่างแล้วกดบันทึกเพื่อเลิกซิงก์สาขานั้น</p>

          <button class="primary" data-cy="calendar-sync" :disabled="busy !== '' || !status.enabled" @click="syncNow">
            {{ busy === 'sync' ? 'กำลังซิงก์...' : 'ซิงก์ทันที' }}
          </button>
        </template>
        <p v-if="message" class="calendar-message" data-cy="calendar-message">{{ message }}</p>
        <p v-if="error" class="calendar-error" role="alert" data-cy="calendar-error">{{ error }}</p>
      </section>
    </main>
  </div>
</template>

<style scoped src="../styles/back-office.css"></style>
<style scoped>
.calendar-panel {
  margin-top: 32px;
  padding: 28px 32px;
}
.calendar-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 20px;
}
.calendar-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
}
.calendar-table th,
.calendar-table td {
  padding: 10px 8px;
  text-align: left;
  border-bottom: 1px solid #e6e0d4;
}
.calendar-table input {
  width: 100%;
  min-width: 260px;
  padding: 8px 10px;
  border: 1px solid #cfc8b8;
  border-radius: 8px;
}
.calendar-hint {
  color: #68776f;
  font-size: 0.9rem;
  margin: 0 0 20px;
}
.calendar-failed,
.calendar-error {
  color: #b3261e;
}
.calendar-message {
  color: #1e807c;
}
@media (max-width: 720px) {
  .calendar-panel {
    padding: 20px 16px;
    overflow-x: auto;
  }
}
</style>
