<script setup lang="ts">
import { hiddenFromGoogle } from '../googleCancel'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import GoogleCalendarPanel from './GoogleCalendarPanel.vue'
import {
  ApiError,
  appointmentApi,
  branchApi,
  calendarApi,
  type Appointment,
  type AppointmentAvailability,
  type Branch,
  type Therapist,
} from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'
import { loadAppointmentTherapists } from '../therapists'
import {
  activeAppointments,
  addDays,
  bangkokDate,
  clock,
  layoutEvents,
  minuteOf,
  statusText,
  therapistColor,
  weekDates,
} from '../calendarLayout'

const props = defineProps<{ manager?: boolean }>()
const router = useRouter(),
  route = useRoute()
const auth = (() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
      role?: string
      branchCode?: string
    } | null
  } catch {
    return null
  }
})()
const isManager = computed(() => !!props.manager && auth?.role === 'MANAGER')
const canCreate = computed(() => isManager.value || auth?.role === 'THERAPIST')
const branchCode = computed(() =>
  isManager.value ? (selectedManagerBranch()?.code ?? '') : (auth?.branchCode ?? ''),
)
const stateKey = `appointment-calendar:${auth?.role ?? 'unknown'}:${auth?.branchCode ?? 'manager'}`
const saved = (() => {
  try {
    return JSON.parse(sessionStorage.getItem(stateKey) ?? '{}') as {
      date?: string
      mode?: string
      therapists?: string[]
    }
  } catch {
    return {}
  }
})()
const validDate = (value: unknown) =>
  typeof value === 'string' &&
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(new Date(`${value}T12:00:00`).getTime())
const requestedDate = route.query.calendarDate ?? saved.date
const date = ref(validDate(requestedDate) ? String(requestedDate) : clinicToday())
const mode = ref<'day' | 'week'>(
  route.query.calendarMode === 'day' ||
    saved.mode === 'day' ||
    (!route.query.calendarMode && !saved.mode && window.matchMedia?.('(max-width: 720px)').matches)
    ? 'day'
    : 'week',
)
const selectedIds = ref<string[]>(saved.therapists ?? [])
const initialized = ref(false)
const appointments = ref<Appointment[]>([]),
  therapists = ref<Therapist[]>([]),
  branches = ref<Branch[]>([])
const availability = ref<Record<string, AppointmentAvailability>>({})
const loading = ref(true),
  refreshing = ref(false),
  syncingCalendar = ref(false),
  error = ref(''),
  availabilityError = ref(''),
  syncMessage = ref('')
const detail = ref<Appointment | null>(null),
  dialog = ref<HTMLDialogElement | null>(null)
const dates = computed(() => (mode.value === 'week' ? weekDates(date.value) : [date.value]))
const branchInfo = computed(() => branches.value.find((item) => item.code === branchCode.value))
const branchTherapists = computed(() => {
  const people = new Map(
    therapists.value
      .filter((item) => item.branchCode === branchCode.value)
      .map((item) => [item.id, item]),
  )
  // แสดงนัดเดิมแม้ผู้รักษาจะไม่อยู่ในรายการที่เปิดใช้งานแล้ว
  for (const item of activeAppointments(appointments.value, branchCode.value)) {
    if (!people.has(item.therapist.id))
      people.set(item.therapist.id, {
        ...item.therapist,
        code: String(item.therapist.id),
        branchCode: item.branchCode,
      })
  }
  return [...people.values()]
})
const filteredAppointments = computed(() =>
  activeAppointments(appointments.value, branchCode.value).filter((item) =>
    selectedIds.value.includes(String(item.therapist.id)),
  ),
)
const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${value}T12:00:00`))
const rangeLabel = computed(() =>
  mode.value === 'week'
    ? `${dateLabel(dates.value[0]!)} – ${dateLabel(dates.value[6]!)}`
    : dateLabel(date.value),
)
const minutes = (value: string) => {
  const [hour, minute] = value.split(':').map(Number)
  return hour! * 60 + (minute ?? 0)
}
const range = computed(() => {
  let start = minutes(branchInfo.value?.bookingOpenTime ?? '09:00'),
    end = minutes(branchInfo.value?.bookingCloseTime ?? '18:00')
  for (const item of filteredAppointments.value) {
    if (dates.value.includes(bangkokDate(item.startAt))) {
      start = Math.min(start, minuteOf(item.startAt))
      end = Math.max(
        end,
        bangkokDate(item.endAt) === bangkokDate(item.startAt) ? minuteOf(item.endAt) : 1440,
      )
    }
  }
  return {
    start: Math.max(0, Math.floor(start / 30) * 30),
    end: Math.min(1440, Math.ceil(end / 30) * 30),
  }
})
const times = computed(() =>
  Array.from(
    { length: Math.max(1, (range.value.end - range.value.start) / 30) },
    (_, index) => range.value.start + index * 30,
  ),
)
function events(day: string) {
  // ใช้ startAt/endAt ของนัดจริงเพื่อจัดตำแหน่งและความสูงในปฏิทิน
  return layoutEvents(
    filteredAppointments.value
      .filter((item) => bangkokDate(item.startAt) === day)
      .map((item) => ({
        id: item.id,
        item,
        start: minuteOf(item.startAt),
        end: Math.max(
          minuteOf(item.startAt) + 20,
          bangkokDate(item.endAt) === day ? minuteOf(item.endAt) : 1440,
        ),
      })),
  )
}
function freeTherapists(day: string, time: number) {
  return branchTherapists.value.filter(
    (person) =>
      selectedIds.value.includes(String(person.id)) &&
      availability.value[`${day}:${person.id}`]?.slots.some(
        (slot) => slot.time === clock(time) && slot.available,
      ),
  )
}
function toggle(id: number, checked: boolean) {
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, String(id)])]
    : selectedIds.value.filter((value) => value !== String(id))
}
function selectDay(day: string) {
  date.value = day
  mode.value = 'day'
}
async function showAppointment(item: Appointment) {
  detail.value = item
  await nextTick()
  dialog.value?.showModal()
}
function createAppointment() {
  if (!canCreate.value || !branchCode.value) return
  void router.push({
    name: 'staff-appointment-create',
    query: {
      source: 'calendar',
      date: date.value,
      branchCode: branchCode.value,
      calendarDate: date.value,
      calendarMode: mode.value,
    },
  })
}
function create(day: string, time: number) {
  // ส่งวัน เวลา และผู้รักษาที่ว่างไปตั้งต้นในฟอร์มสร้างนัด
  if (!canCreate.value || refreshing.value || !branchCode.value) return
  const free = freeTherapists(day, time)
  if (!free.length) return
  void router.push({
    name: 'staff-appointment-create',
    query: {
      source: 'calendar',
      date: day,
      time: clock(time),
      branchCode: branchCode.value,
      calendarDate: date.value,
      calendarMode: mode.value,
      ...(isManager.value && free.length === 1 ? { therapistId: String(free[0]!.id) } : {}),
    },
  })
}
function editAppointment() {
  if (!isManager.value || !detail.value) return
  dialog.value?.close()
  void router.push({
    name: 'staff-appointment-edit',
    params: { id: detail.value.id },
    query: { source: 'calendar', calendarDate: date.value, calendarMode: mode.value },
  })
}
let version = 0
async function loadPeriod() {
  // โหลดนัดทุกหน้าในช่วงปฏิทิน และยกเลิกผลจากคำขอรอบเก่า
  const request = ++version
  error.value = ''
  availabilityError.value = ''
  appointments.value = []
  availability.value = {}
  if (!branchCode.value) {
    refreshing.value = false
    return
  }
  const code = branchCode.value,
    days = [...dates.value]
  refreshing.value = true
  try {
    const all: Appointment[] = []
    let page = 0,
      total = 1
    while (page < total) {
      const result = await appointmentApi.search({
        branchCode: code,
        startDate: days[0],
        endDate: days[days.length - 1],
        page,
        size: 100,
      })
      if (request !== version) return
      all.push(...result.items)
      total = result.totalPages
      page++
    }
    const allSelected =
      selectedIds.value.length > 0 &&
      branchTherapists.value.every((person) => selectedIds.value.includes(String(person.id)))
    appointments.value = hiddenFromGoogle(all)
    if (allSelected) selectedIds.value = branchTherapists.value.map((person) => String(person.id))
    const next: Record<string, AppointmentAvailability> = {}
    const requests = days.flatMap((day) =>
      therapists.value
        .filter((person) => person.branchCode === code)
        .map((person) => ({ day, person })),
    )
    let failed = false
    // จำกัดจำนวนคำขอพร้อมกัน เพราะการตรวจเวลาว่างอาจเรียก Google Calendar
    for (let index = 0; index < requests.length; index += 5) {
      await Promise.all(
        requests.slice(index, index + 5).map(async ({ day, person }) => {
          try {
            next[`${day}:${person.id}`] = await appointmentApi.availability({
              branchCode: code,
              therapistId: person.id,
              date: day,
            })
          } catch {
            failed = true
          }
        }),
      )
      if (request !== version) return
    }
    availability.value = next
    if (failed)
      availabilityError.value =
        'กรุณากดรีเฟรช'
  } catch (caught) {
    if (request === version)
      error.value = caught instanceof Error ? caught.message : 'โหลดปฏิทินไม่สำเร็จ'
  } finally {
    if (request === version) refreshing.value = false
  }
}
async function initialize() {
  loading.value = true
  error.value = ''
  try {
    const [branchList, people] = await Promise.all([branchApi.list(), loadAppointmentTherapists()])
    branches.value = branchList
    therapists.value = people
    selectedIds.value = saved.therapists ?? branchTherapists.value.map((item) => String(item.id))
    initialized.value = true
    await loadPeriod()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดข้อมูลสาขาและนักกายภาพไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

async function syncCalendar() {
  if (syncingCalendar.value) return

  syncingCalendar.value = true
  syncMessage.value = ''
  try {
    const result = await calendarApi.syncNow()
    syncMessage.value = `ซิงก์ปฏิทินแล้ว: เพิ่ม/อัปเดต ${result.pushed} · ลบ ${result.removed} · ล้มเหลว ${result.failed}`
    await loadPeriod()
  } catch (caught) {
    syncMessage.value = caught instanceof ApiError ? caught.message : 'ซิงก์ Google Calendar ไม่สำเร็จ'
  } finally {
    syncingCalendar.value = false
  }
}
onMounted(initialize)
watch([date, mode, branchCode], ([, , code], [, , oldCode]) => {
  dialog.value?.close()
  detail.value = null
  if (initialized.value) {
    if (code !== oldCode) selectedIds.value = branchTherapists.value.map((item) => String(item.id))
    void loadPeriod()
  }
})
watch(
  [date, mode, selectedIds],
  () => {
    try {
      sessionStorage.setItem(
        stateKey,
        JSON.stringify({ date: date.value, mode: mode.value, therapists: selectedIds.value }),
      )
    } catch {
      /* ปฏิทินยังใช้งานได้แม้อ่านข้อมูลจาก browser storage ไม่สำเร็จ */
    }
  },
  { deep: true },
)
</script>

<template>
  <div class="appointment-calendar-workspace">
    <StaffHeader />
    <main class="workspace-main">
      <RouterLink class="back-link" :to="{ name: 'staff-appointments' }"
        >← กลับไปหน้ารายการนัดหมาย</RouterLink
      >
      <header class="workspace-heading">
        <div>
          <h1>ปฏิทินนัดหมาย</h1>
          <p>{{ branchInfo?.name || 'เลือกสาขาจาก Header' }}</p>
        </div>
        <div class="heading-actions">
          <button
            v-if="canCreate"
            type="button"
            class="control calendar-primary"
            :disabled="loading || !branchCode"
            @click="createAppointment"
          >
            + สร้างนัดหมาย</button
          ><button
            v-if="isManager"
            type="button"
            class="control calendar-sync"
            :disabled="loading || syncingCalendar || !branchCode"
            @click="syncCalendar"
          >
            {{ syncingCalendar ? 'กำลังซิงก์…' : 'ซิงก์ปฏิทิน ↻' }}
          </button
          ><button
            type="button"
            class="control"
            :disabled="loading || refreshing || !branchCode"
            @click="initialized ? loadPeriod() : initialize()"
          >
            {{ refreshing ? 'กำลังโหลด…' : 'รีเฟรช ↻' }}
          </button>
        </div>
      </header>
      <section class="workspace-toolbar" aria-label="ควบคุมปฏิทิน">
        <button type="button" class="control" @click="date = clinicToday()">วันนี้</button>
        <div class="date-navigation">
          <button
            type="button"
            class="control"
            aria-label="ช่วงก่อนหน้า"
            @click="date = addDays(date, mode === 'week' ? -7 : -1)"
          >
            ‹</button
          ><strong>{{ rangeLabel }}</strong
          ><button
            type="button"
            class="control"
            aria-label="ช่วงถัดไป"
            @click="date = addDays(date, mode === 'week' ? 7 : 1)"
          >
            ›
          </button>
        </div>
        <div class="view-options" aria-label="มุมมอง">
          <button
            type="button"
            class="control"
            :aria-pressed="mode === 'day'"
            @click="mode = 'day'"
          >
            รายวัน</button
          ><button
            type="button"
            class="control"
            :aria-pressed="mode === 'week'"
            @click="mode = 'week'"
          >
            รายสัปดาห์
          </button>
        </div>
      </section>
      <fieldset class="therapist-filter">
        <legend>นักกายภาพ</legend>
        <button
          type="button"
          class="filter-all"
          @click="selectedIds = branchTherapists.map((item) => String(item.id))"
        >
          เลือกทั้งหมด
        </button>
        <label
          v-for="person in branchTherapists"
          :key="person.id"
          :style="{ '--person-color': therapistColor(person.id).color }"
          ><input
            type="checkbox"
            :checked="selectedIds.includes(String(person.id))"
            @change="toggle(person.id, ($event.target as HTMLInputElement).checked)"
          /><span class="person-dot"></span>{{ person.fullName }}</label
        >
      </fieldset>
      <p v-if="syncMessage" class="calendar-note" role="status">{{ syncMessage }}</p>
      <section v-if="route.query.googleCalendar === 'status'" class="google-status">
        <h2>สถานะ Google Calendar</h2>
        <GoogleCalendarPanel /><button
          type="button"
          class="control"
          @click="router.replace({ query: { ...route.query, googleCalendar: undefined } })"
        >
          ปิดสถานะ
        </button>
      </section>
      <p v-if="!isManager" class="calendar-note">
      </p>
      <p v-if="error" class="workspace-error" role="alert">{{ error }}</p>
      <p v-if="availabilityError" class="calendar-note" role="status">{{ availabilityError }}</p>
      <p v-if="loading || refreshing" role="status">กำลังโหลดปฏิทินและตรวจสอบเวลาว่าง…</p>
      <p v-else-if="!branchCode" class="calendar-note">
        ไม่มีสิทธิ์เข้าถึง กรุณาติดต่อ Manager 
      </p>
      <p v-else-if="!branchTherapists.length" class="calendar-note">ยังไม่มีนักกายภาพในสาขานี้</p>
      <p v-else-if="!selectedIds.length" class="calendar-note">
        เลือกนักกายภาพอย่างน้อยหนึ่งคนเพื่อดูตาราง
      </p>
      <template v-else-if="!error">
        <p class="calendar-note">
          {{
            filteredAppointments.length
              ? `แสดง ${filteredAppointments.length} นัด`
              : 'ยังไม่มีนัดในช่วงนี้'
          }}
        </p>
        <div
          class="timeline-scroll"
          tabindex="0"
          role="region"
          aria-label="ตารางนัดหมาย เลื่อนแนวนอนเพื่อดูครบทุกวัน"
        >
          <div
            class="timeline"
            :style="{ '--days': dates.length, '--height': `${times.length * 60}px` }"
          >
            <div class="time-axis">
              <header>เวลา</header>
              <div class="axis-body">
                <span v-for="time in times" :key="time">{{ clock(time) }}</span>
              </div>
            </div>
            <section
              v-for="day in dates"
              :key="day"
              class="day-column"
              :aria-label="dateLabel(day)"
            >
              <header :class="{ 'is-today': day === clinicToday() }">
                <button type="button" @click="selectDay(day)">
                  {{ dateLabel(day) }}<small v-if="day === clinicToday()">วันนี้</small>
                </button>
              </header>
              <div class="day-body">
                <button
                  v-for="time in times"
                  :key="time"
                  class="empty-slot"
                  type="button"
                  :disabled="!canCreate || !freeTherapists(day, time).length"
                  :aria-label="`สร้างนัด ${dateLabel(day)} เวลา ${clock(time)}`"
                  @click="create(day, time)"
                ></button>
                <button
                  v-for="event in events(day)"
                  :key="event.id"
                  type="button"
                  class="appointment-block"
                  :style="{
                    ...therapistColor(event.item.therapist.id),
                    top: `${(event.start - range.start) * 2}px`,
                    height: `${(event.end - event.start) * 2 - 2}px`,
                    left: `calc(${(event.lane / event.lanes) * 100}% + 2px)`,
                    width: `calc(${100 / event.lanes}% - 4px)`,
                  }"
                  :aria-label="`${clock(event.start)} ${event.item.patient?.fullName || event.item.contactName || 'ผู้ป่วยใหม่'} ${event.item.therapist.fullName} ${statusText[event.item.status]}`"
                  @click="showAppointment(event.item)"
                >
                  <strong>{{ clock(event.start) }}–{{ clock(minuteOf(event.item.endAt)) }}</strong
                  ><span>{{
                    event.item.patient?.fullName || event.item.contactName || 'ผู้ป่วยใหม่'
                  }}</span
                  ><span>{{ event.item.therapist.fullName }}</span
                  ><small>{{ statusText[event.item.status] }}</small>
                </button>
              </div>
            </section>
          </div>
        </div>
      </template>
      <dialog ref="dialog" class="appointment-dialog" aria-labelledby="appointment-detail-title">
        <template v-if="detail">
          <h2 id="appointment-detail-title">รายละเอียดนัดหมาย</h2>
          <p>
            <strong>{{ detail.patient?.fullName || detail.contactName || 'ผู้ป่วยใหม่' }}</strong>
          </p>
          <dl>
            <dt>นักกายภาพ</dt>
            <dd>{{ detail.therapist.fullName }}</dd>
            <dt>วันและเวลา</dt>
            <dd>
              {{ dateLabel(bangkokDate(detail.startAt)) }} {{ clock(minuteOf(detail.startAt)) }}–{{
                clock(minuteOf(detail.endAt))
              }}
            </dd>
            <dt>บริการ</dt>
            <dd>{{ detail.service.name || 'ยังไม่ระบุ' }}</dd>
            <dt>สถานะ</dt>
            <dd>{{ statusText[detail.status] }}</dd>
          </dl>
          <div class="dialog-actions">
            <button type="button" class="control" autofocus @click="dialog?.close()">ปิด</button
            ><button
              v-if="isManager"
              type="button"
              class="control calendar-primary"
              @click="editAppointment"
            >
              จัดการนัดหมาย
            </button>
          </div>
        </template>
      </dialog>
    </main>
  </div>
</template>
<style scoped src="../styles/calendar-workspace.css"></style>
