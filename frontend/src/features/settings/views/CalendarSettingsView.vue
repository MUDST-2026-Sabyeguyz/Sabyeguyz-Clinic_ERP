<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import SettingsNavIcon from '../components/SettingsNavIcon.vue'
import { ApiError, calendarApi, type CalendarStatus } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const menus = [
  { key: 'branches', label: 'สาขา', hint: 'ข้อมูลและเวลาทำการ', icon: 'branch' },
  { key: 'users', label: 'ผู้ใช้และสิทธิ์', hint: 'บัญชีและการเข้าถึง', icon: 'users' },
  { key: 'services', label: 'บริการและคอร์ส', hint: 'รายการและราคา', icon: 'services' },
  { key: 'payments', label: 'การชำระเงิน', hint: 'ช่องทางและสถานะ', icon: 'payments' },
  { key: 'rooms', label: 'Room / Resource', hint: 'ห้องและทรัพยากร', icon: 'rooms' },
  { key: 'commission', label: 'กฎค่ามือ', hint: 'ค่ามือการรักษา', icon: 'commission' },
  { key: 'tiers', label: 'กฎค่าคอมมิชชัน', hint: 'อัตราตามยอดขาย', icon: 'tiers' },
  { key: 'master', label: 'Master Data', hint: 'ข้อมูลพื้นฐานระบบ', icon: 'master' },
] as const
const authUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string; branchCode?: string; branchName?: string } | null
  } catch {
    return null
  }
})
// Manager จัดการได้เฉพาะสาขาที่เลือก ส่วน Therapist เห็นสาขาของบัญชีตน
const isManager = computed(() => authUser.value?.role === 'MANAGER')
const selectedCode = computed(() => selectedManagerBranch()?.code ?? authUser.value?.branchCode ?? '')
const selectedBranchName = computed(() => selectedManagerBranch()?.name ?? authUser.value?.branchName ?? '')
const visibleBranches = computed(() => {
  const branches = status.value?.branches ?? []
  return isManager.value
    ? branches.filter((branch) => branch.branchCode === selectedCode.value)
    : branches
})
const branch = computed(() => visibleBranches.value[0])
const changed = computed(
  () =>
    !!branch.value &&
    (calendarIds[selectedCode.value] ?? '').trim() !== (branch.value.googleCalendarId ?? ''),
)
const calendarIdValue = computed(() => (calendarIds[selectedCode.value] ?? '').trim())
const isCalendarIdValid = computed(() =>
  /^[^\s@]+@(gmail\.com|group\.calendar\.google\.com)$/i.test(calendarIdValue.value),
)
const calendarIdTouched = ref(false)
const calendarIdFormatInvalid = computed(
  () => calendarIdTouched.value && calendarIdValue.value.length > 0 && !isCalendarIdValid.value,
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
  calendarIdTouched.value = false
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
    // โหลดสถานะจาก backend แล้วเติม Calendar ID ที่ยังไม่มีในฟอร์ม
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
  if (!isCalendarIdValid.value) return
  busy.value = branchCode
  error.value = ''
  message.value = ''
  try {
    // บันทึก Calendar ID; การบันทึกไม่ได้ยืนยันสิทธิ์เชื่อมต่อ Google
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
    // สั่ง sync แล้วแสดงจำนวนรายการที่ส่ง เปลี่ยน และล้มเหลวจากผล API
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
      <header class="settings-title">
        <div>
          <span class="settings-eyebrow">ศูนย์ควบคุมระบบ</span>
          <h1>การตั้งค่า</h1>
              <p>{{ isManager ? 'จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ' : 'ดูสถานะปฏิทินของสาขาที่คุณสังกัด' }}</p>
        </div>
      </header>

      <div class="settings-layout">
        <aside v-if="isManager" class="settings-nav">
          <div class="settings-nav-head">
            <div>
              <h2>หมวดการตั้งค่า</h2>
              <p>เลือกหัวข้อเพื่อจัดการ</p>
            </div>
          </div>
          <nav class="settings-nav-list" aria-label="หมวดการตั้งค่า">
            <RouterLink
              v-for="menu in menus"
              :key="menu.key"
              class="settings-nav-item"
              :to="`/staff/settings?section=${menu.key}`"
              ><span class="settings-nav-icon" aria-hidden="true"
                ><SettingsNavIcon :name="menu.icon" /></span
              ><span
                ><b>{{ menu.label }}</b
                ><small>{{ menu.hint }}</small></span
              ><span class="settings-nav-arrow" aria-hidden="true">›</span></RouterLink
            >
          </nav>
          <div class="settings-nav-footer">
            <RouterLink
              class="settings-nav-item active"
              to="/staff/settings/calendar"
              aria-current="page"
              ><span class="settings-nav-icon" aria-hidden="true"
                ><SettingsNavIcon name="calendar" /></span
              ><span><b>Google Calendar</b><small>ปฏิทินและการซิงก์</small></span
              ><span class="settings-nav-arrow" aria-hidden="true">›</span></RouterLink
            >
            <RouterLink class="settings-nav-link" to="/staff/settings/audit-log"
              >ดูประวัติการแก้ไขระบบ <span>→</span></RouterLink
            >
          </div>
        </aside>
        <section class="settings-panel calendar-panel" :aria-busy="loading || !!busy">
          <header class="settings-panel-head calendar-heading">
            <div>
              <h2>Google Calendar</h2>
              <p>{{ isManager ? 'ตั้งค่าปฏิทินและตรวจสถานะ sync ของแต่ละสาขา' : 'สถานะปฏิทินของสาขาที่คุณสังกัด' }}</p>
            </div>
            <span class="branch-tag">{{
              selectedBranchName || branch?.branchName || 'ยังไม่ได้เลือกสาขา'
            }}</span>
          </header>
          <div class="calendar-body">
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
                <p v-if="!status.enabled">กรุณาลองใหม่</p>
                <p v-else-if="isManager">บันทึกปฏิทินของสาขา แล้วใช้ปุ่ม sync เพื่ออัปเดตนัดหมาย</p>
                <p v-else>ดูปฏิทินได้โดยไม่สามารถแก้ Calendar ID หรือสั่ง sync</p>
              </div>
              <div class="calendar-workspace">
                <div class="calendar-connection">
                  <form
                    v-if="branch && isManager"
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
                  <span v-if="changed" class="draft-status">ยังไม่บันทึก</span>
                </div>
                <label :for="`calendar-id-${branch.branchCode}`">Calendar ID</label>
                <input
                  :id="`calendar-id-${branch.branchCode}`"
                  v-model="calendarIds[branch.branchCode]"
                  :data-cy="`calendar-id-${branch.branchCode}`"
                  :disabled="!!busy"
                  :class="{ 'calendar-id-invalid': calendarIdFormatInvalid }"
                  placeholder="เช่น xxxx@group.calendar.google.com"
                  :aria-describedby="
                    calendarIdFormatInvalid ? 'calendar-id-help calendar-id-error' : 'calendar-id-help'
                  "
                  :aria-invalid="calendarIdFormatInvalid"
                  autocomplete="off"
                  spellcheck="false"
                  @blur="calendarIdTouched = true"
                />
                <p id="calendar-id-help" class="calendar-hint">
                  กรอก Calendar ID ที่ต้องการเชื่อมต่อ
                </p>
                <p
                  v-if="calendarIdFormatInvalid"
                  id="calendar-id-error"
                  class="calendar-field-error"
                  role="alert"
                >
                  รูปแบบ Calendar ID ไม่ถูกต้อง
                </p>
                <div class="connection-actions">
                  <button
                    type="submit"
                    class="calendar-save"
                    :data-cy="`calendar-save-${branch.branchCode}`"
                    :disabled="!!busy || !changed || !isCalendarIdValid"
                  >
                    {{ busy === branch.branchCode ? 'กำลังบันทึก…' : 'บันทึก' }}
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
                  <section v-else-if="branch" class="calendar-form calendar-readonly">
                    <h3>ปฏิทินของ{{ branch.branchName }}</h3>
                    <p>{{ branch.googleCalendarId ? 'เชื่อม Calendar ID แล้ว' : 'ยังไม่ได้ตั้งค่าปฏิทิน' }}</p>
                    <a v-if="openUrl" :href="openUrl" class="calendar-secondary" data-cy="calendar-open" target="_blank" rel="noopener noreferrer">เปิด Google Calendar ↗</a>
                    <p v-else class="calendar-hint">ยังไม่มี Calendar ID สำหรับสาขานี้</p>
                  </section>
                  <p v-else-if="isManager" class="calendar-hint">เลือกสาขาจาก Header เพื่อเริ่มตั้งค่า</p>
                  <details v-if="isManager" class="calendar-help">
                    <summary>วิธีตั้งค่าและเชิญนักกายภาพ</summary>
                    <ol>
                      <li>เปิด Google Calendar สร้างหรือเลือกปฏิทินของสาขาที่ต้องการ</li>
                      <li>ในหน้าตั้งค่าปฏิทิน คัดลอก Calendar ID แล้ววางในช่องด้านบน</li>
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
                </div>
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
                <p class="calendar-hint">sync ล่าสุด : {{ formatTime(status.lastRunAt) }}</p>
                <p v-if="!status.enabled" class="calendar-hint"></p>
                <button
                  v-if="isManager"
                  type="button"
                  class="calendar-sync"
                  data-cy="calendar-sync"
                  :disabled="!!busy || !status.enabled"
                  @click="syncNow"
                >
                  {{ busy === 'sync' ? 'กำลัง sync …' : 'sync ↻' }}
                </button>
                </section>
              </div>
            </template>
            <p v-if="message" class="calendar-message" role="status" data-cy="calendar-message">
              {{ message }}
            </p>
            <p v-if="error" class="calendar-error" role="alert" data-cy="calendar-error">
              {{ error }}
            </p>
            <button
              v-if="!status && !loading"
              type="button"
              class="calendar-secondary"
              @click="load"
            >
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
                  ยืนยัน
                </button>
              </div>
            </dialog>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped src="../styles/back-office.css"></style>
<style scoped>
.calendar-panel {
  margin-top: 0;
  padding: 0;
}
.calendar-body {
  padding: 26px 32px 32px;
}
.calendar-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(230px, 0.55fr);
  gap: 20px;
  align-items: stretch;
}
.calendar-connection {
  min-width: 0;
  padding: 20px;
  border: 1px solid #e1ebe7;
  border-radius: 14px;
  background: #fff;
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
  margin-bottom: 0;
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
  margin: 18px 0 8px;
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
.calendar-form input.calendar-id-invalid {
  border-color: #d34d47;
  box-shadow: 0 0 0 3px #d34d471c;
}
.calendar-field-error {
  margin: -10px 0 0;
  color: #bd3e39;
  font-size: 0.82rem;
  font-weight: 600;
}
.connection-status,
.calendar-help {
  color: #68776f;
  font-size: 14px;
}
.draft-status {
  display: inline-flex;
  align-items: center;
  min-height: 25px;
  padding: 2px 9px;
  border-radius: 999px;
  background: #fff4d8;
  color: #86611f;
  font-size: 0.75rem;
  font-weight: 700;
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
  margin-top: 22px;
  padding: 16px 0 0;
  border-top: 1px solid #e3ebe7;
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
  display: flex;
  flex-direction: column;
  margin-top: 0;
  padding: 20px;
  border: 1px solid #e1ebe7;
  border-radius: 14px;
  background: #f9fcfa;
}
.sync-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
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
.sync-section .calendar-hint {
  margin-bottom: 16px;
}
.sync-section .calendar-sync {
  margin-top: auto;
}
.disconnect-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  box-sizing: border-box;
  width: min(500px, calc(100% - 32px));
  margin: 0;
  border: 1px solid #cbdcd7;
  border-radius: 16px;
  padding: 30px 32px;
  color: #25372e;
  transform: translate(-50%, -50%);
}
.disconnect-dialog::backdrop {
  background: rgb(0 0 0 / 40%);
}
.disconnect-dialog p {
  margin: 10px 0 0;
  line-height: 1.7;
}
.disconnect-dialog .connection-actions {
  margin-top: 24px;
  gap: 12px;
}
.disconnect-dialog .calendar-danger {
  border-color: #c74742;
  background: #c74742;
  color: #fff;
}
.disconnect-dialog .calendar-danger:hover:not(:disabled) {
  border-color: #ab3632;
  background: #ab3632;
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
.settings-nav .settings-nav-item.active {
  background: linear-gradient(135deg, #1c8d82, #16756e);
  box-shadow: 0 7px 14px #16756e33;
}
.settings-nav .settings-nav-item.active b,
.settings-nav .settings-nav-item.active small,
.settings-nav .settings-nav-item.active .settings-nav-arrow {
  color: #fff;
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
  margin: 18px 0 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: #edf8f2;
  color: #1e807c;
}
.calendar-error {
  margin: 18px 0 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff1ef;
}
@media (max-width: 720px) {
  .calendar-panel {
    padding: 0;
  }
  .calendar-body {
    padding: 22px 18px 24px;
  }
  .calendar-workspace {
    grid-template-columns: 1fr;
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
