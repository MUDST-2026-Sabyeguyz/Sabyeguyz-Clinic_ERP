<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  appointmentApi,
  branchApi,
  calendarIntegrationApi,
  catalogApi,
  type Appointment,
  type Branch,
  type Therapist,
} from '@/api/client'

type CalendarMode = 'day' | 'week'

const router = useRouter()
const mode = ref<CalendarMode>('week')
const toDateValue = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const anchorDate = ref(toDateValue(new Date()))
const selectedTherapistId = ref('ทั้งหมด')
const selectedBranch = ref('ทั้งหมด')
const appointments = ref<Appointment[]>([])
const therapists = ref<Therapist[]>([])
const branches = ref<Branch[]>([])
const loading = ref(true)
const errorMessage = ref('')
const calendarSyncOpen = ref(false)
const calendarConnecting = ref(false)
const calendarSyncMessage = ref('')

async function connectGoogleCalendar() {
  calendarSyncOpen.value = true
  calendarConnecting.value = true
  calendarSyncMessage.value = 'กำลังเริ่มการเชื่อมต่อ…'
  try {
    const { authorizationUrl } = await calendarIntegrationApi.startGoogleConnect()
    window.location.assign(authorizationUrl)
  } catch {
    calendarSyncMessage.value = 'ระบบเชื่อมต่อยังรอการเปิดใช้งานจาก backend'
  } finally {
    calendarConnecting.value = false
  }
}

const timeOptions = ['09:00', '11:00', '13:00', '15:00', '17:00']
const branchOptions = computed(() => branches.value.filter((branch) => branch.active))
const visibleTherapists = computed(() =>
  therapists.value
    .filter(
      (therapist) =>
        selectedTherapistId.value === 'ทั้งหมด' ||
        String(therapist.id) === selectedTherapistId.value,
    )
    .filter(
      (therapist) =>
        selectedBranch.value === 'ทั้งหมด' || therapist.branchCode === selectedBranch.value,
    )
    .slice(0, 5),
)

const weekDays = computed(() => {
  const selected = new Date(`${anchorDate.value}T00:00:00`)
  const offset = selected.getDay() === 0 ? -6 : 1 - selected.getDay()
  const monday = new Date(selected)
  monday.setDate(selected.getDate() + offset)
  return Array.from({ length: 5 }, (_, index) => {
    const date = new Date(monday)
    date.setDate(monday.getDate() + index)
    return toDateValue(date)
  })
})

const dayColumns = computed(() =>
  visibleTherapists.value.map((therapist) => ({
    key: String(therapist.id),
    label: therapist.fullName,
    sublabel: branchName(therapist.branchCode),
    date: anchorDate.value,
    therapist,
  })),
)

const weekColumns = computed(() =>
  weekDays.value.map((date) => ({
    key: date,
    label: formatWeekday(date),
    sublabel: '',
    date,
    therapist: visibleTherapists.value[0],
  })),
)

const columns = computed(() => (mode.value === 'week' ? weekColumns.value : dayColumns.value))

function branchName(code: string) {
  return branches.value.find((branch) => branch.code === code)?.name || code
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Bangkok',
  })
}

function formatWeekday(value: string) {
  const date = new Date(`${value}T00:00:00`)
  return new Intl.DateTimeFormat('th-TH', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(date)
}

const dateLabel = computed(() => {
  if (mode.value === 'day') {
    return new Intl.DateTimeFormat('th-TH', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(`${anchorDate.value}T00:00:00`))
  }
  const start = new Date(`${weekDays.value[0]}T00:00:00`)
  const end = new Date(`${weekDays.value[4]}T00:00:00`)
  return `${start.getDate()}–${end.getDate()} ${new Intl.DateTimeFormat('th-TH', {
    month: 'long',
    year: 'numeric',
  }).format(end)}`
})

function appointmentFor(date: string, time: string, therapist?: Therapist) {
  return appointments.value.find(
    (item) =>
      item.startAt.slice(0, 10) === date &&
      formatTime(item.startAt) === time &&
      (mode.value === 'week'
        ? selectedTherapistId.value === 'ทั้งหมด' ||
          String(item.therapist.id) === selectedTherapistId.value
        : item.therapist.id === therapist?.id) &&
      (selectedBranch.value === 'ทั้งหมด' || item.branchCode === selectedBranch.value),
  )
}

function slotState(item?: Appointment) {
  if (!item) return 'empty'
  if (item.status === 'COMPLETED') return 'completed'
  if (item.status === 'CANCELLED' || item.status === 'NO_SHOW') return 'cancelled'
  if (item.status === 'CONFIRMED') return 'confirmed'
  return 'pending'
}

function moveDate(direction: number) {
  const date = new Date(`${anchorDate.value}T00:00:00`)
  date.setDate(date.getDate() + direction * (mode.value === 'week' ? 7 : 1))
  anchorDate.value = toDateValue(date)
}

function openSlot(date: string, time: string, therapist?: Therapist) {
  const item = appointmentFor(date, time, therapist)
  if (item) {
    void router.push({
      name: 'staff-appointment-edit',
      params: { id: item.id },
    })
    return
  }
  void router.push({
    name: 'staff-appointment-create',
    query: {
      source: 'calendar',
      date,
      time,
      ...(therapist ? { therapistId: String(therapist.id) } : {}),
    },
  })
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [appointmentPage, branchList, therapistList] = await Promise.all([
      appointmentApi.search({ page: 0, size: 100 }),
      branchApi.list(),
      catalogApi.therapists(),
    ])
    appointments.value = appointmentPage.items
    branches.value = branchList
    therapists.value = therapistList
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดปฏิทินนัดหมายไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="staff-page appointment-page appointment-calendar-page">
    <StaffHeader />
    <main class="calendar-main">
      <header class="calendar-title">
        <button type="button" @click="router.push({ name: 'staff-appointments' })">
          ← กลับไปหน้ารายการนัดหมาย
        </button>
        <h1>ปฏิทินนัดหมาย</h1>
        <p>ดูเวลาว่างและตารางนัดของนักกายภาพ</p>
      </header>

      <section class="calendar-toolbar" aria-label="ตัวกรองปฏิทิน">
        <div class="calendar-date-control">
          <button type="button" aria-label="ก่อนหน้า" @click="moveDate(-1)">‹</button>
          <strong>{{ dateLabel }}</strong>
          <button type="button" aria-label="ถัดไป" @click="moveDate(1)">›</button>
        </div>
        <FilterDropdown
          v-model="selectedTherapistId"
          label="กรองนักกายภาพ"
          default-value="ทั้งหมด"
          default-label="นักกายภาพ: ทั้งหมด"
          :options="
            therapists.map((item) => ({
              value: String(item.id),
              label: item.fullName,
            }))
          "
        />
        <FilterDropdown
          v-model="selectedBranch"
          label="กรองสาขา"
          default-value="ทั้งหมด"
          default-label="สาขา: ทั้งหมด"
          :options="
            branchOptions.map((item) => ({
              value: item.code,
              label: item.name,
            }))
          "
        />
        <div class="calendar-view-toggle">
          <button type="button" :class="{ active: mode === 'day' }" @click="mode = 'day'">
            รายวัน
          </button>
          <button type="button" :class="{ active: mode === 'week' }" @click="mode = 'week'">
            รายสัปดาห์
          </button>
        </div>
        <button class="calendar-google" type="button" @click="connectGoogleCalendar">
          เชื่อมต่อ Google Calendar
        </button>
      </section>

      <div class="calendar-legend">
        <span data-status="empty">ว่าง</span>
        <span data-status="pending">ยังไม่ยืนยันนัด</span>
        <span data-status="confirmed">ยืนยันนัดหมาย</span>
        <span data-status="completed">ใช้บริการแล้ว</span>
        <span data-status="cancelled">ยกเลิก</span>
      </div>

      <p v-if="errorMessage" class="calendar-error" role="alert">
        {{ errorMessage }}
      </p>
      <div v-else-if="loading" class="calendar-loading">กำลังโหลดปฏิทิน...</div>
      <div v-else class="calendar-grid-scroll">
        <div class="schedule-grid" :style="{ '--column-count': Math.max(columns.length, 1) }">
          <div class="time-column">
            <b>เวลา</b>
            <span v-for="time in timeOptions" :key="time">{{ time }}</span>
          </div>
          <div v-for="column in columns" :key="column.key" class="schedule-column">
            <header>
              <b>{{ column.label }}</b>
              <small v-if="column.sublabel">{{ column.sublabel }}</small>
            </header>
            <button
              v-for="time in timeOptions"
              :key="time"
              type="button"
              class="schedule-slot"
              :data-state="slotState(appointmentFor(column.date, time, column.therapist))"
              @click="openSlot(column.date, time, column.therapist)"
            >
              <template v-if="appointmentFor(column.date, time, column.therapist)">
                <strong>
                  {{ time }}
                  {{ appointmentFor(column.date, time, column.therapist)?.patient.fullName }}
                </strong>
                <small
                  >นก.
                  {{
                    appointmentFor(column.date, time, column.therapist)?.therapist.fullName
                  }}</small
                >
              </template>
              <template v-else>
                <strong>ว่าง</strong>
                <small>เลือกช่องนี้เพื่อสร้างนัดหมาย</small>
              </template>
            </button>
          </div>
          <div v-if="columns.length === 0" class="calendar-empty">ไม่พบนักกายภาพตามตัวกรอง</div>
        </div>
      </div>
    </main>

    <div
      v-if="calendarSyncOpen"
      class="calendar-modal-backdrop"
      @click.self="calendarSyncOpen = false"
    >
      <section
        class="calendar-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="calendar-sync-title"
      >
        <h2 id="calendar-sync-title">เชื่อมต่อ Google Calendar</h2>
        <p>{{ calendarSyncMessage }}</p>
        <button type="button" :disabled="calendarConnecting" @click="calendarSyncOpen = false">
          {{ calendarConnecting ? 'กำลังเชื่อมต่อ…' : 'รับทราบ' }}
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped src="../styles/appointment-calendar.css"></style>
<style src="../styles/therapist.css"></style>
