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
    for (const branch of status.value.branches)
      calendarIds[branch.branchCode] = branch.googleCalendarId ?? ''
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
    message.value = calendarIds[branchCode]?.trim()
      ? 'บันทึกปฏิทินของสาขาแล้ว'
      : 'เลิกซิงก์ปฏิทินของสาขาแล้ว'
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
  return value
    ? new Date(value).toLocaleString('th-TH', { dateStyle: 'medium', timeStyle: 'short' })
    : '-'
}

onMounted(load)
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <div class="settings-title">
        <h1>Google Calendar</h1>
      </div>

      <section class="settings-panel calendar-panel">
        <p v-if="loading" data-cy="calendar-loading">กำลังโหลด...</p>
        <template v-else-if="status">
          <div v-if="status.enabled" class="calendar-state is-enabled" data-cy="calendar-state">
            <span class="state-icon" aria-hidden="true">✓</span>
            <div>
              <strong>เปิดใช้งานการซิงก์</strong>
              <p>
                รอบล่าสุด: {{ formatTime(status.lastRunAt) }} · ระยะเวลานัดเริ่มต้น
                {{ status.defaultDurationMinutes }} นาที
              </p>
            </div>
          </div>

          <div class="calendar-section-heading">
            <h2>ปฏิทินของแต่ละสาขา</h2>
            <p>กำหนด Calendar ID เพื่อเชื่อมต่อปฏิทินของแต่ละสาขา</p>
          </div>
          <div class="calendar-table-scroll">
            <table class="calendar-table">
              <thead>
                <tr>
                  <th>สาขา</th>
                  <th>Calendar ID</th>
                  <th>รอซิงก์</th>
                  <th>ล้มเหลว</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="branch in status.branches"
                  :key="branch.branchCode"
                  :data-cy="`calendar-row-${branch.branchCode}`"
                >
                  <td>
                    {{ branch.branchName }} <small>({{ branch.branchCode }})</small>
                  </td>
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
                      class="calendar-save"
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
          </div>
          <p class="calendar-hint">เว้นช่อง Calendar ID ว่างแล้วกดบันทึกเพื่อเลิกซิงก์สาขานั้น</p>

          <button
            class="calendar-sync"
            data-cy="calendar-sync"
            :disabled="busy !== '' || !status.enabled"
            @click="syncNow"
          >
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
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.calendar-state {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  margin-bottom: 28px;
  border: 1px solid #f1dfb3;
  border-radius: 10px;
  background: #fff8e8;
  color: #71571e;
}
.calendar-state.is-enabled {
  border-color: #cfe9dd;
  background: #edf8f2;
  color: #27634b;
}
.state-icon {
  display: grid;
  flex: 0 0 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  background: #f5e5b9;
  font-weight: 700;
}
.is-enabled .state-icon {
  background: #d6eee0;
}
.calendar-state strong {
  font-size: 15px;
}
.calendar-state p {
  margin: 5px 0 0;
  color: inherit;
  font-size: 13px;
  line-height: 1.5;
}
.calendar-section-heading {
  margin-bottom: 16px;
}
.calendar-section-heading h2 {
  margin: 0 0 5px;
  color: #23423c;
  font-size: 18px;
}
.calendar-section-heading p {
  margin: 0;
  color: #71837d;
  font-size: 13px;
}
.calendar-table-scroll {
  overflow-x: auto;
  border: 1px solid #dce8e4;
  border-radius: 10px;
}
.calendar-table {
  width: 100%;
  min-width: 700px;
  border-collapse: collapse;
  margin: 0;
}
.calendar-table th,
.calendar-table td {
  padding: 19px 20px;
  text-align: left;
  border-bottom: 1px solid #e8eeeb;
}
.calendar-table th {
  background: #208780;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}
.calendar-table td {
  color: #29423b;
  font-size: 15px;
}
.calendar-table tbody tr:nth-child(odd) {
  background: #fff;
}
.calendar-table tr:last-child td {
  border-bottom: 0;
}
.calendar-table small {
  color: #7d9088;
}
.calendar-table input {
  box-sizing: border-box;
  width: 100%;
  min-width: 260px;
  min-height: 42px;
  padding: 9px 12px;
  border: 1px solid #cbdcd7;
  border-radius: 8px;
  font: inherit;
}
.calendar-table input:focus {
  outline: 2px solid #9ed2c8;
  border-color: #208880;
}
.calendar-save,
.calendar-sync {
  min-height: 40px;
  padding: 9px 22px;
  border: 1px solid #208780;
  border-radius: 999px;
  background: #208780;
  color: white;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.calendar-save:hover:not(:disabled),
.calendar-sync:hover:not(:disabled) {
  background: #176f6a;
  border-color: #176f6a;
}
.calendar-save:disabled,
.calendar-sync:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.calendar-hint {
  color: #68776f;
  font-size: 0.9rem;
  margin: 12px 0 22px;
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
    padding: 0;
  }
}
</style>
