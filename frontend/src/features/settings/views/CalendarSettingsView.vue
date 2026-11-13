<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import SettingsNavIcon from '../components/SettingsNavIcon.vue'
import { ApiError, calendarApi, type CalendarStatus } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const menus = [
  { key: 'branches', label: 'สาขา', hint: 'ข้อมูลและเวลาทำการ', icon: 'branch' },
  { key: 'users', label: 'ผู้ใช้และสิทธิ์', hint: 'บัญชีและการเข้าถึง', icon: 'users' },
  { key: 'services', label: 'คอร์สและบริการ', hint: 'รายการและราคา', icon: 'services' },
  { key: 'payments', label: 'การชำระเงิน', hint: 'ช่องทางและสถานะ', icon: 'payments' },
  { key: 'rooms', label: 'Room / Resource', hint: 'ห้องและทรัพยากร', icon: 'rooms' },
  { key: 'commission', label: 'กฎค่ามือ', hint: 'ค่ามือการรักษา', icon: 'commission' },
  { key: 'tiers', label: 'กฎค่าคอมมิชชัน', hint: 'อัตราตามยอดขาย', icon: 'tiers' },
  { key: 'master', label: 'Master Data', hint: 'ข้อมูลพื้นฐานระบบ', icon: 'master' },
] as const
const authUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
      role?: string
      branchCode?: string
      branchName?: string
    } | null
  } catch {
    return null
  }
})

const isManager = computed(() => authUser.value?.role === 'MANAGER')
const selectedCode = computed(
  () => selectedManagerBranch()?.code ?? authUser.value?.branchCode ?? '',
)
const selectedBranchName = computed(
  () => selectedManagerBranch()?.name ?? authUser.value?.branchName ?? '',
)
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
    calendarIdTouched.value = true
    error.value = 'กรุณากรอก Calendar ID หากต้องการยกเลิกกรุณากดปุ่มยกเลิกการเชื่อมต่อ'
    return
  }
  if (!isCalendarIdValid.value) return
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
    if (result.failed > 0) message.value = `sync ไม่สำเร็จ ${result.failed} รายการ`
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

const keyText = ref('')
const keyFileName = ref('')
const keyTouched = ref(false)
const keyDialog = ref<HTMLDialogElement | null>(null)
const keyLooksValid = computed(() => {
  try {
    const key = JSON.parse(keyText.value) as { client_email?: string; private_key?: string }
    return !!key.client_email && !!key.private_key
  } catch {
    return false
  }
})
const keyInvalid = computed(
  () => keyTouched.value && keyText.value.trim().length > 0 && !keyLooksValid.value,
)
const canManageKey = computed(() => isManager.value && status.value?.keySource !== 'file')

async function onKeyFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 20_000) {
    error.value = 'ไฟล์ใหญ่เกินไป ไม่ใช่ไฟล์ key ของ Google'
    return
  }
  keyText.value = await file.text()
  keyFileName.value = file.name
  keyTouched.value = true
  error.value = ''
}

async function saveKey() {
  if (busy.value) return
  keyTouched.value = true
  if (!keyLooksValid.value) {
    error.value = 'ต้องเป็นเนื้อหาไฟล์ JSON ของ service account (มี client_email และ private_key)'
    return
  }
  busy.value = 'key'
  error.value = ''
  message.value = ''
  try {
    status.value = await calendarApi.saveCredentials(keyText.value.trim())
    keyText.value = ''
    keyFileName.value = ''
    keyTouched.value = false
    message.value = 'เชื่อมต่อ Google แล้ว ขั้นต่อไป: แชร์ปฏิทินให้อีเมลด้านล่าง แล้วกรอก Calendar ID ของสาขา'
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

async function removeKey() {
  if (busy.value) return
  busy.value = 'key'
  error.value = ''
  message.value = ''
  try {
    status.value = await calendarApi.removeCredentials()
    keyDialog.value?.close()
    message.value = 'ตัดการเชื่อมต่อ Google แล้ว'
  } catch (caught) {
    error.value = messageOf(caught)
  } finally {
    busy.value = ''
  }
}

async function copyEmail() {
  try {
    await navigator.clipboard.writeText(status.value?.serviceAccountEmail ?? '')
    message.value = 'คัดลอกอีเมลแล้ว'
  } catch {
    message.value = 'คัดลอกไม่ได้ กรุณาเลือกข้อความแล้วคัดลอกเอง'
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
          <h1>การตั้งค่า</h1>
          <p>
            {{
              isManager
                ? 'จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ'
                : 'ดูสถานะปฏิทินของสาขาที่คุณสังกัด'
            }}
          </p>
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
            <RouterLink class="settings-nav-item" to="/staff/settings/card-readers">
              <span class="settings-nav-icon" aria-hidden="true"><SettingsNavIcon name="payments" /></span>
              <span><b>เครื่องอ่านบัตรประชาชน</b><small>อุปกรณ์และการจับคู่</small></span>
              <span class="settings-nav-arrow" aria-hidden="true">›</span>
            </RouterLink>
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
              <p>
                {{
                  isManager
                    ? 'ตั้งค่าปฏิทินและตรวจสถานะ sync ของแต่ละสาขา'
                    : 'สถานะปฏิทินของสาขาที่คุณสังกัด'
                }}
              </p>
            </div>
            <span class="branch-tag">{{
              selectedBranchName || branch?.branchName || 'ยังไม่ได้เลือกสาขา'
            }}</span>
          </header>
          <div class="calendar-body">
            <p v-if="loading" data-cy="calendar-loading" role="status">กำลังโหลดการตั้งค่า…</p>
            <template v-else-if="status">
              <div v-if="!status.enabled" class="calendar-state" data-cy="calendar-state">
                <strong>ระบบ sync ยังไม่พร้อมใช้งาน</strong>
                <p v-if="isManager">
                  ยังไม่ได้เชื่อมต่อ Google วาง key ของ Google ในกล่องด้านล่างเพื่อเริ่มใช้งาน
                </p>
                <p v-else>ยังไม่ได้เชื่อมต่อ Google กรุณาแจ้งผู้จัดการให้ตั้งค่าการเชื่อมต่อ</p>
              </div>
              <section v-if="canManageKey" class="google-key" data-cy="google-key">
                <template v-if="status.keySource === 'ui'">
                  <h3>เชื่อมต่อ Google แล้ว</h3>
                  <p>
                    แชร์ปฏิทินของสาขาให้อีเมลนี้ โดยให้สิทธิ์ “Make changes to events”:
                  </p>
                  <div class="google-key-email">
                    <code data-cy="service-account-email">{{ status.serviceAccountEmail }}</code>
                    <button type="button" class="calendar-secondary" @click="copyEmail">
                      คัดลอก
                    </button>
                  </div>
                  <button
                    type="button"
                    class="calendar-secondary"
                    :disabled="!!busy"
                    @click="keyDialog?.showModal()"
                  >
                    ตัดการเชื่อมต่อ Google
                  </button>
                </template>
                <template v-else>
                  <h3>เชื่อมต่อ Google</h3>
                  <ol>
                    <li>
                      ใน Google Cloud Console สร้าง Service Account และดาวน์โหลด key แบบ JSON
                      (เปิดใช้ Google Calendar API ด้วย)
                    </li>
                    <li>เลือกไฟล์ หรือวางเนื้อหาไฟล์ JSON ในช่องนี้ แล้วกดเชื่อมต่อ</li>
                  </ol>
                  <div class="google-key-inputs">
                  <div class="google-key-upload-group">
                  <label for="google-key-file">อัปโหลดไฟล์</label>
                  <div class="google-key-upload">
                    <label class="google-key-file">
                      <span class="google-key-file-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V3m-4 4 4-4 4 4M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5" /></svg></span>
                      <small>เลือกไฟล์ Service Account นามสกุล .json</small>
                      <input id="google-key-file" type="file" accept=".json,application/json" @change="onKeyFile" />
                      <span class="google-key-choose" aria-hidden="true">เลือกไฟล์</span>
                      <span class="google-key-selected">{{ keyFileName || 'ยังไม่ได้เลือกไฟล์' }}</span>
                    </label>
                  </div>
                  </div>
                  <div class="google-key-paste">
                  <label for="google-key-json">หรือวางเนื้อหา JSON</label>
                  <textarea
                    id="google-key-json"
                    v-model="keyText"
                    rows="5"
                    spellcheck="false"
                    autocomplete="off"
                    :class="{ invalid: keyInvalid }"
                    placeholder='หรือวางเนื้อหาไฟล์ที่ขึ้นต้นด้วย { "type": "service_account", ...'
                    @blur="keyTouched = true"
                  ></textarea>
                  <small v-if="keyInvalid" class="field-hint" role="alert">
                    ต้องเป็นเนื้อหาไฟล์ JSON ของ service account (มี client_email และ private_key)
                  </small>
                  </div>
                  </div>
                  <div class="google-key-footer">
                  <small class="google-key-note"
                    >key ถูกเก็บแบบเข้ารหัสบนเซิร์ฟเวอร์ และจะไม่แสดงกลับมาอีก</small
                  >
                  <button
                    type="button"
                    class="calendar-save"
                    :disabled="!!busy || !keyText.trim()"
                    data-cy="save-google-key"
                    @click="saveKey"
                  >
                    {{ busy === 'key' ? 'กำลังตรวจสอบ…' : 'เชื่อมต่อ' }}
                  </button>
                  </div>
                </template>
              </section>
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
                    <label :for="`calendar-id-${branch.branchCode}`"
                      >Calendar ID<span class="calendar-required" aria-hidden="true">*</span></label
                    >
                    <input
                      :id="`calendar-id-${branch.branchCode}`"
                      v-model="calendarIds[branch.branchCode]"
                      :data-cy="`calendar-id-${branch.branchCode}`"
                      :disabled="!!busy"
                      :class="{ 'calendar-id-invalid': calendarIdFormatInvalid }"
                      placeholder="เช่น xxxx@group.calendar.google.com"
                      :aria-describedby="
                        calendarIdFormatInvalid
                          ? 'calendar-id-help calendar-id-error'
                          : 'calendar-id-help'
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
                    <p>
                      {{
                        branch.googleCalendarId
                          ? 'เชื่อม Calendar ID แล้ว'
                          : 'ยังไม่ได้ตั้งค่าปฏิทิน'
                      }}
                    </p>
                    <a
                      v-if="openUrl"
                      :href="openUrl"
                      class="calendar-secondary"
                      data-cy="calendar-open"
                      target="_blank"
                      rel="noopener noreferrer"
                      >เปิด Google Calendar ↗</a
                    >
                    <p v-else class="calendar-hint">ยังไม่มี Calendar ID สำหรับสาขานี้</p>
                  </section>
                  <p v-else-if="isManager" class="calendar-hint">
                    เลือกสาขาจาก Header เพื่อเริ่มตั้งค่า
                  </p>
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
                        หากต้องการเชิญนักกายภาพรายนัด ให้เปิดกิจกรรมนั้นใน Google Calendar เพิ่ม
                        Gmail ในผู้เข้าร่วม และตรวจสิทธิ์ก่อนส่งคำเชิญ
                      </li>
                    </ol>
                    <p>
                      การเชิญเข้ากิจกรรมต่างจากการแชร์ปฏิทินทั้งสาขา หน้านี้ไม่ได้จัดการสิทธิ์
                      Google รายบุคคล
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
            <dialog ref="keyDialog" class="disconnect-dialog">
              <p>
                ต้องการตัดการเชื่อมต่อ Google หรือไม่ การ sync นัดหมายกับ Google Calendar
                จะหยุดจนกว่าจะเชื่อมต่อใหม่
              </p>
              <div class="connection-actions">
                <button type="button" class="calendar-secondary" @click="keyDialog?.close()">
                  ยกเลิก
                </button>
                <button type="button" class="calendar-danger" :disabled="!!busy" @click="removeKey">
                  ตัดการเชื่อมต่อ
                </button>
              </div>
            </dialog>
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
.calendar-required {
  margin-left: 2px;
  color: #c0392b;
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
  color: var(--color-ink);
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
.google-key {
  display: grid;
  gap: 12px;
  margin-bottom: 28px;
  padding: 20px 22px;
  border: 1px solid #d5e5df;
  border-radius: 12px;
  background: #f7fbf9;
}
.google-key h3 {
  margin: 0;
  font-size: 1.05rem;
}
.google-key p,
.google-key ol {
  margin: 0;
  color: #4d5f57;
  font-size: 0.9rem;
  line-height: 1.6;
}
.google-key ol {
  padding-left: 20px;
}
.google-key textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border: 1px solid #b5ccc4;
  border-radius: 10px;
  background: #fff;
  font: 0.82rem/1.5 ui-monospace, Consolas, monospace;
  resize: vertical;
}
.google-key textarea.invalid {
  border-color: #d94b45;
  background: #fff7f6;
}
.google-key .calendar-save {
  justify-self: start;
}
.google-key .calendar-secondary {
  justify-self: start;
}
.google-key-email {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.google-key-email code {
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #d5e5df;
  font-size: 0.85rem;
  word-break: break-all;
}
.google-key-file {
  display: grid;
  gap: 6px;
  font-size: 0.9rem;
  font-weight: 600;
}
.google-key-name,
.google-key-note {
  color: #68776f;
  font-size: 0.82rem;
}
.google-key .field-hint {
  color: #b42318;
  font-size: 0.82rem;
}
.google-key-inputs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 8px;
}
.google-key-upload {
  display: grid;
  align-content: center;
  box-sizing: border-box;
  height: 220px;
  min-width: 0;
  padding: 24px 18px;
  border: 1px dashed #a8cac3;
  border-radius: 12px;
  background: #fff;
}
.google-key-upload .google-key-file {
  justify-items: center;
  gap: 12px;
  color: #28564e;
  text-align: center;
}
.google-key-file small { font-weight: 400; color: #68776f; line-height: 1.5; }
.google-key-file-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  background: #eaf5f1;
  border-radius: 12px;
  color: #187d78;
}
.google-key-file-icon svg { width: 24px; height: 24px; }
.google-key-file input[type='file'] {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  border: 0;
}
.google-key-file { cursor: pointer; }
.google-key-choose {
  padding: 9px 18px;
  border: 1px solid #96bfb8;
  border-radius: 8px;
  background: #fff;
  color: #187d78;
  font-weight: 600;
}
.google-key-file:focus-within .google-key-choose { outline: 2px solid #187d78; outline-offset: 3px; }
.google-key-selected { max-width: 100%; overflow-wrap: anywhere; color: #68776f; font-size: 13px; font-weight: 400; }
.google-key-file input::file-selector-button {
  padding: 9px 12px;
  margin-right: 10px;
  border: 1px solid #96bfb8;
  border-radius: 8px;
  color: #187d78;
  background: #fff;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.google-key-paste, .google-key-upload-group { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.google-key-paste > label, .google-key-upload-group > label { color: #415b52; font-size: 14px; font-weight: 600; line-height: 1.5; }
.google-key-paste textarea { flex: none; height: 220px; min-height: 220px; margin: 0; resize: none; }
.google-key-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #dfeae5;
}
.google-key-footer .calendar-save {
  min-width: 140px;
  min-height: 46px;
  margin: 0;
  padding: 10px 22px;
  border-radius: 9px;
  color: #fff;
  background: var(--ui-primary-gradient, #187d78);
  border: 0;
  font: inherit;
  font-weight: 600;
}
.google-key-footer .calendar-save:disabled { opacity: 0.5; cursor: not-allowed; }
.google-key-upload-group .google-key-name { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 1050px) {
  .google-key-inputs { grid-template-columns: minmax(0, 1fr); }
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
  background: var(--ui-primary);
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
  color: var(--color-primary);
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
