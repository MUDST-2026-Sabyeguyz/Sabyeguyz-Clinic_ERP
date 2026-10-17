<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  appointmentApi,
  branchApi,
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
const availability = ref<Record<string, string[]>>({})
const availabilityLoading = ref(false)
const therapists = ref<Therapist[]>([])
const branches = ref<Branch[]>([])
const loading = ref(true)
const errorMessage = ref('')
function openGoogleCalendarSettings() {
  void router.push({ name: 'staff-calendar' })
}

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
function therapistsForSlot(columnTherapist?: Therapist) {
  return mode.value === 'week' ? visibleTherapists.value : columnTherapist ? [columnTherapist] : []
}
function therapistForSlot(date: string, time: string, columnTherapist?: Therapist) {
  return therapistsForSlot(columnTherapist).find(
    (therapist) => availability.value[`${date}:${therapist.id}`]?.includes(time),
  ) ?? columnTherapist
}
function slotInBranchWindow(date: string, time: string, columnTherapist?: Therapist) {
  return therapistsForSlot(columnTherapist).some(
    (therapist) => availability.value[`${date}:${therapist.id}`]?.includes(time),
  )
}
const timeOptions = computed(() => {
  const times = new Set<string>()
  for (const column of columns.value) {
    const dayTherapists = mode.value === 'week' ? visibleTherapists.value : [column.therapist]
    for (const therapist of dayTherapists) {
      if (!therapist) continue
      for (const time of availability.value[`${column.date}:${therapist.id}`] ?? []) times.add(time)
    }
  }
  const visibleDates = new Set(columns.value.map((column) => column.date))
  for (const item of appointments.value) {
    if (visibleDates.has(dateInBangkok(item.startAt))) times.add(formatTime(item.startAt))
  }
  return [...times].sort()
})

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

function dateInBangkok(value: string) {
  return new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Bangkok',
  }).format(new Date(value))
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
      dateInBangkok(item.startAt) === date &&
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
    await loadAvailability()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดปฏิทินนัดหมายไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

async function loadAvailability() {
  availabilityLoading.value = true
  const next: Record<string, string[]> = {}
  try {
    const requests = columns.value.flatMap((column) => {
      const dayTherapists = mode.value === 'week' ? visibleTherapists.value : [column.therapist]
      return dayTherapists.flatMap((therapist) => {
        const branchCode = therapist?.branchCode ?? (selectedBranch.value !== 'ทั้งหมด' ? selectedBranch.value : '')
        if (!therapist || !branchCode) return []
        const key = `${column.date}:${therapist.id}`
        return [appointmentApi.availability({ branchCode, therapistId: therapist.id, date: column.date })
          .then((result) => { next[key] = result.slots.map((slot) => slot.time) })]
      })
    })
    await Promise.all(requests)
    availability.value = next
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดช่วงเวลาไม่สำเร็จ'
  } finally {
    availabilityLoading.value = false
  }
}

watch([anchorDate, mode, selectedTherapistId, selectedBranch], () => {
  if (branches.value.length) void loadAvailability()
})

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
        <button class="calendar-google" type="button" @click="openGoogleCalendarSettings">
          ตั้งค่า Google Calendar
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
        <div class="schedule-grid" :style="{ '--column-count': Math.max(columns.length, 1), '--time-count': Math.max(timeOptions.length, 1) }">
          <div class="time-column">
            <b>เวลา</b>
          <span v-for="time in timeOptions" :key="time">{{ time }}</span>
          <span v-if="availabilityLoading" class="calendar-time-loading">…</span>
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
              :disabled="!appointmentFor(column.date, time, column.therapist) && !slotInBranchWindow(column.date, time, column.therapist)"
              @click="openSlot(column.date, time, therapistForSlot(column.date, time, column.therapist))"
            >
              <template v-if="appointmentFor(column.date, time, column.therapist)">
                <strong>
                  {{ time }}
                  {{ appointmentFor(column.date, time, column.therapist)?.patient?.fullName || appointmentFor(column.date, time, column.therapist)?.contactName || 'ผู้ป่วยไม่ประจำ' }}
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
          <div v-else-if="!availabilityLoading && timeOptions.length === 0" class="calendar-empty">
            ไม่พบช่วงเวลารับนัดของสาขานี้
          </div>
        </div>
      </div>
    </main>

  </div>
</template>

<style scoped src="../styles/manager-appointment-calendar.css"></style>
<style src="../styles/therapist.css"></style>
