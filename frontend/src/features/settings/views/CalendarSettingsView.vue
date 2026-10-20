<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { ApiError, calendarApi, type CalendarStatus } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const menus = [
  ['branches', 'สาขา'],
  ['users', 'ผู้ใช้และสิทธิ์'],
  ['services', 'บริการและคอร์ส'],
  ['payments', 'การชำระเงิน'],
  ['rooms', 'Room / Resource'],
  ['commission', 'กฎค่ามือ'],
  ['tiers', 'กฎค่าคอมมิชชัน'],
  ['master', 'Master Data'],
]
const selectedCode = computed(() => selectedManagerBranch()?.code ?? '')
const visibleBranches = computed(
  () => status.value?.branches.filter((branch) => branch.branchCode === selectedCode.value) ?? [],
)
const branch = computed(() => visibleBranches.value[0])
const changed = computed(
  () =>
    !!branch.value &&
    (calendarIds[selectedCode.value] ?? '').trim() !== (branch.value.googleCalendarId ?? ''),
)
const openUrl = computed(() =>
  branch.value?.googleCalendarId
    ? `https://calendar.google.com/calendar/embed?src=${encodeURIComponent(branch.value.googleCalendarId)}&ctz=Asia%2FBangkok`
    : '',
)
const disconnectDialog = ref<HTMLDialogElement | null>(null)
const disconnectCode = ref('')

const status = ref<CalendarStatus | null>(null)
const calendarIds = reactive<Record<string, string>>({})
const loading = ref(true)
const busy = ref('')
const error = ref('')
const message = ref('')
watch(selectedCode, () => {
  error.value = ''
  message.value = ''
  disconnectDialog.value?.close()
  disconnectCode.value = ''
})

function messageOf(caught: unknown) {
  return caught instanceof ApiError ? caught.message : 'เชื่อมต่อเซิร์ฟเวอร์ไม่ได้'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    status.value = await calendarApi.status()
    for (const branch of status.value.branches)
      if (!(branch.branchCode in calendarIds))
        calendarIds[branch.branchCode] = branch.googleCalendarId ?? ''
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    loading.value = false
  }
}

async function save(branchCode: string) {
  if (busy.value) return
  const calendarId = (calendarIds[branchCode] ?? '').trim()
  if (!calendarId) {
    error.value = 'กรุณากรอก Calendar ID หากต้องการยกเลิกกรุณากดปุ่มยกเลิกการเชื่อมต่อ'
    return
  }
  busy.value = branchCode
  error.value = ''
  message.value = ''
  try {
    const saved = await calendarApi.setBranchCalendar(branchCode, calendarId)
    const current = status.value?.branches.find((item) => item.branchCode === branchCode)
    if (current) current.googleCalendarId = saved.googleCalendarId
    calendarIds[branchCode] = saved.googleCalendarId ?? ''
    if (selectedCode.value === branchCode)
      message.value = 'บันทึก Calendar ID แล้ว ยังไม่ใช่การยืนยันสิทธิ์เข้าถึงปฏิทิน'
    await load()
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

async function syncNow() {
  if (busy.value || !status.value?.enabled) return
  busy.value = 'sync'
  error.value = ''
  message.value = ''
  try {
    const result = await calendarApi.syncNow()
    message.value =
      `sync แล้ว ส่งไปยัง Google ${result.pushed}, ลบ ${result.removed}, ` +
      `เลื่อนจาก Google ${result.movedFromGoogle}, ยกเลิกจาก Google ${result.cancelledFromGoogle}, ล้มเหลว ${result.failed}`
    await load()
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

function askDisconnect() {
  if (!branch.value?.googleCalendarId || busy.value) return
  disconnectCode.value = branch.value.branchCode
  disconnectDialog.value?.showModal()
}
async function disconnect() {
  const code = disconnectCode.value
  if (!code || code !== selectedCode.value || busy.value) return
  busy.value = 'disconnect'
  error.value = ''
  message.value = ''
  try {
    await calendarApi.setBranchCalendar(code, '')
    calendarIds[code] = ''
    const current = status.value?.branches.find((item) => item.branchCode === code)
    if (current) current.googleCalendarId = null
    disconnectDialog.value?.close()
    if (selectedCode.value === code) message.value = 'ยกเลิกการเชื่อมต่อแล้ว'
    await load()
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

function formatTime(value: string | null) {
  return value
    ? new Date(value).toLocaleString('th-TH', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Bangkok',
      })
    : 'ยังไม่มีประวัติการ sync'
}

onMounted(load)
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <div class="settings-title">
        <h1>การตั้งค่า</h1>
        <p>จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ</p>
      </div>

      <div class="settings-layout">
        <aside class="settings-nav">
          <h2>หมวดการตั้งค่า</h2>
          <RouterLink
            v-for="menu in menus"
            :key="menu[0]"
            class="settings-nav-item"
            :to="`/staff/settings?section=${menu[0]}`"
            >{{ menu[1] }}</RouterLink
          >
          <RouterLink
            class="settings-nav-item active"
            to="/staff/settings/calendar"
            aria-current="page"
            >Google Calendar</RouterLink
          >
          <RouterLink class="settings-nav-link" to="/staff/settings/audit-log"
            >ประวัติการแก้ไขระบบ</RouterLink
          >
        </aside>
        <section class="settings-panel calendar-panel" :aria-busy="loading || !!busy">
          <header class="calendar-heading">
            <div>
              <h2>Google Calendar</h2>
              <p>เชื่อมปฏิทินนัดหมายแต่ละสาขา</p>
            </div>
            <span class="branch-tag">{{
              selectedManagerBranch()?.name || 'ยังไม่ได้เลือกสาขา'
            }}</span>
          </header>
          <p v-if="loading" data-cy="calendar-loading" role="status">กำลังโหลดการตั้งค่า…</p>
          <template v-else-if="status">
            <div
              class="calendar-state"
              :class="{ 'is-enabled': status.enabled }"
              data-cy="calendar-state"
            >
              <strong>{{
                status.enabled ? 'ระบบ sync เปิดใช้งาน' : 'ระบบ sync ยังไม่พร้อมใช้งาน'
              }}</strong>
              <p v-if="!status.enabled">
                กรุณาลองใหม่
              </p>
              <p v-else>บันทึกปฏิทินของสาขา แล้วใช้ปุ่ม sync เพื่ออัปเดตนัดหมาย</p>
            </div>
            <form
              v-if="branch"
              class="calendar-form"
              :data-cy="`calendar-row-${branch.branchCode}`"
              novalidate
              @submit.prevent="save(branch.branchCode)"
            >
              <div class="form-title">
                <h3>ปฏิทินของ{{ branch.branchName }}</h3>
                <span class="connection-status">{{
                  branch.googleCalendarId
                    ? 'มี Calendar ID ที่บันทึกแล้ว'
                    : 'ยังไม่ได้ตั้งค่าปฏิทิน'
                }}</span>
              </div>
              <label :for="`calendar-id-${branch.branchCode}`">Calendar ID</label>
              <input
                :id="`calendar-id-${branch.branchCode}`"
                v-model="calendarIds[branch.branchCode]"
                :data-cy="`calendar-id-${branch.branchCode}`"
                :disabled="!!busy"
                placeholder="เช่น xxxx@group.calendar.google.com"
                aria-describedby="calendar-id-help"
                autocomplete="off"
                spellcheck="false"
              />
              <p id="calendar-id-help" class="calendar-hint">
                กรอก Calendar ID ที่ต้องการเชื่อมต่อ
              </p>
              <p v-if="changed" class="draft-note">
                มีการแก้ไขที่ยังไม่บันทึก กรุณาสลับสาขาแล้วข้อมูลที่กรอกยังอยู่จนกว่าจะออกจากหน้านี้
              </p>
              <div class="connection-actions">
                <button
                  type="submit"
                  class="calendar-save"
                  :data-cy="`calendar-save-${branch.branchCode}`"
                  :disabled="!!busy || !changed"
                >
                  {{ busy === branch.branchCode ? 'กำลังบันทึก…' : 'บันทึกการเชื่อมต่อ' }}
                </button>
                <a
                  v-if="openUrl"
                  :href="openUrl"
                  class="calendar-secondary"
                  data-cy="calendar-open"
                  target="_blank"
                  rel="noopener noreferrer"
                  >เปิด Google Calendar ↗</a
                >
              </div>
              <button
                v-if="branch.googleCalendarId"
                type="button"
                class="disconnect-link"
                data-cy="calendar-disconnect"
                :disabled="!!busy"
                @click="askDisconnect"
              >
                ยกเลิกการเชื่อมต่อ
              </button>
            </form>
            <p v-else class="calendar-hint">เลือกสาขาจาก Header เพื่อเริ่มตั้งค่า</p>
            <details class="calendar-help">
              <summary>วิธีตั้งค่าและเชิญนักกายภาพ</summary>
              <ol>
                <li>
                  เปิด Google Calendar สร้างหรือเลือกปฏิทินของสาขาที่ต้องการ
                </li>
                <li>
                  ในหน้าตั้งค่าปฏิทิน คัดลอก Calendar ID แล้ววางในช่องด้านบน
                </li>
                <li>
                  ให้สิทธิ์บัญชี service account ที่ทีม backend ตั้งไว้ในการจัดการกิจกรรม
                  ไม่ต้องนำไฟล์คีย์หรือรหัสลับมากรอก
                </li>
                <li>
                  หากต้องการเชิญนักกายภาพรายนัด ให้เปิดกิจกรรมนั้นใน Google Calendar เพิ่ม Gmail
                  ในผู้เข้าร่วม และตรวจสิทธิ์ก่อนส่งคำเชิญ
                </li>
              </ol>
              <p>
                การเชิญเข้ากิจกรรมต่างจากการแชร์ปฏิทินทั้งสาขา หน้านี้ไม่ได้จัดการสิทธิ์ Google
                รายบุคคล
              </p>
            </details>
            <section class="sync-section" aria-label="สถานะการ sync">
              <h3>สถานะการ sync</h3>
              <dl v-if="branch" class="sync-stats">
                <div>
                  <dt>รอ sync</dt>
                  <dd>{{ branch.waiting }}</dd>
                </div>
                <div>
                  <dt>ล้มเหลว</dt>
                  <dd :class="{ 'calendar-failed': branch.failed > 0 }">{{ branch.failed }}</dd>
                </div>
              </dl>
              <p class="calendar-hint">
                sync ล่าสุด : {{ formatTime(status.lastRunAt) }}
              </p>
              <p v-if="!status.enabled" class="calendar-hint">
              </p>
              <button
                type="button"
                class="calendar-sync"
                data-cy="calendar-sync"
                :disabled="!!busy || !status.enabled"
                @click="syncNow"
              >
                {{ busy === 'sync' ? 'กำลัง sync …' : 'sync ↻' }}
              </button>
            </section>
          </template>
          <p v-if="message" class="calendar-message" role="status" data-cy="calendar-message">
            {{ message }}
          </p>
          <p v-if="error" class="calendar-error" role="alert" data-cy="calendar-error">
            {{ error }}
          </p>
          <button v-if="!status && !loading" type="button" class="calendar-secondary" @click="load">
            ลองโหลดอีกครั้ง
          </button>
          <dialog
            ref="disconnectDialog"
            class="disconnect-dialog"
            aria-labelledby="disconnect-title"
          >
            <h3 id="disconnect-title">ยกเลิกการเชื่อมต่อ{{ branch?.branchName }}?</h3>
            <p>
              ระบบจะหยุด sync ปฏิทินของสาขานี้ โดยไม่ลบนัดหมายในคลินิกหรือกิจกรรมเดิมใน Google
              Calendar
            </p>
            <p v-if="error" class="calendar-error" role="alert">{{ error }}</p>
            <div class="connection-actions">
              <button
                type="button"
                class="calendar-secondary"
                :disabled="!!busy"
                autofocus
                @click="disconnectDialog?.close()"
              >
                กลับ
              </button>
              <button
                type="button"
                class="calendar-danger"
                :disabled="!!busy"
                data-cy="calendar-confirm-disconnect"
                @click="disconnect"
              >
                ยืนยันยกเลิกการเชื่อมต่อ
              </button>
            </div>
          </dialog>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped src="../styles/back-office.css"></style>
<style scoped>
.calendar-panel {
  margin-top: 0;
  padding: 28px 32px;
}
.calendar-heading,
.form-title,
.connection-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.calendar-heading {
  justify-content: space-between;
  margin-bottom: 24px;
}
.calendar-heading h2,
.calendar-form h3,
.sync-section h3 {
  margin: 0;
}
.calendar-heading p {
  margin: 6px 0 0;
  color: #68776f;
}
.branch-tag {
  padding: 6px 12px;
  border-radius: 8px;
  background: #edf8f2;
  color: #27634b;
}
.calendar-form label {
  display: block;
  font-weight: 600;
  margin: 20px 0 8px;
}
.calendar-form input {
  box-sizing: border-box;
  width: 100%;
  min-height: 46px;
  padding: 10px 14px;
  border: 1px solid #cbdcd7;
  border-radius: 8px;
  font: inherit;
}
.connection-status,
.calendar-help {
  color: #68776f;
  font-size: 14px;
}
.draft-note {
  color: #71571e;
  font-size: 14px;
}
.connection-actions .calendar-save {
  margin-left: 0;
}
.calendar-secondary,
.calendar-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  box-sizing: border-box;
  padding: 9px 18px;
  border: 1px solid #b5ccc4;
  border-radius: 999px;
  background: #fff;
  color: #176965;
  font: inherit;
  text-decoration: none;
  cursor: pointer;
}
.calendar-secondary:hover {
  background: #eef5f2;
}
.calendar-danger {
  color: #a32924;
  border-color: #a32924;
}
.calendar-danger:hover {
  background: #fce9e7;
}
.disconnect-link {
  margin-top: 18px;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: #a32924;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.calendar-help {
  margin-top: 28px;
  padding: 18px 0;
  border-top: 1px solid #e3ebe7;
  border-bottom: 1px solid #e3ebe7;
  line-height: 1.7;
}
.calendar-help summary {
  color: #176965;
  font-weight: 600;
  cursor: pointer;
}
.calendar-help li + li {
  margin-top: 8px;
}
.sync-section {
  margin-top: 24px;
}
.sync-stats {
  display: flex;
  gap: 40px;
  margin: 18px 0;
  flex-wrap: wrap;
}
.sync-stats dt {
  color: #68776f;
  font-size: 14px;
}
.sync-stats dd {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 600;
}
.disconnect-dialog {
  box-sizing: border-box;
  width: min(500px, calc(100% - 32px));
  border: 1px solid #cbdcd7;
  border-radius: 16px;
  padding: 24px;
  color: #25372e;
}
.disconnect-dialog::backdrop {
  background: rgb(0 0 0 / 40%);
}
.disconnect-dialog p {
  line-height: 1.7;
}
button:focus-visible,
a:focus-visible,
input:focus-visible,
summary:focus-visible {
  outline: 3px solid #208780;
  outline-offset: 3px;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
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
.settings-nav .settings-nav-link {
  display: block;
}
.settings-nav .active {
  background: #1e807c;
  color: white;
  border-radius: 10px;
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
  display: block;
  margin-left: auto;
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
    padding: 24px 18px;
  }
  .connection-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .connection-actions > * {
    width: 100%;
  }
}
</style>
