<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    disabled?: boolean
    invalid?: boolean
    minDate?: string
    inclusiveMin?: boolean
    disableSunday?: boolean
    showCalendarIcon?: boolean
    numericMonth?: boolean
    hideCaret?: boolean
    placeholder?: string
  }>(),
  {
    disabled: false,
    invalid: false,
    minDate: '',
    inclusiveMin: false,
    disableSunday: false,
    showCalendarIcon: false,
    numericMonth: false,
    hideCaret: false,
    placeholder: 'เลือกวันที่',
  },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const containerRef = ref<HTMLElement | null>(null)
const instanceId = ref(`tdp-${Math.random().toString(36).substring(2, 9)}`)
const open = ref(false)
const viewDate = ref(props.modelValue ? new Date(`${props.modelValue}T00:00:00`) : new Date())

const months = [
  'มกราคม',
  'กุมภาพันธ์',
  'มีนาคม',
  'เมษายน',
  'พฤษภาคม',
  'มิถุนายน',
  'กรกฎาคม',
  'สิงหาคม',
  'กันยายน',
  'ตุลาคม',
  'พฤศจิกายน',
  'ธันวาคม',
]
const weekdays = ['อา', 'จ', 'อ', 'พ', 'พฤ', 'ศ', 'ส']
const years = Array.from(
  { length: new Date().getFullYear() - 1900 + 2 },
  (_, index) => 1900 + index,
)

const displayValue = computed(() => {
  if (!props.modelValue) return props.placeholder
  const date = new Date(`${props.modelValue}T00:00:00`)
  return props.numericMonth
    ? `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear() + 543}`
    : `${date.getDate()} ${months[date.getMonth()]} ${date.getFullYear() + 543}`
})

const days = computed(() => {
  const year = viewDate.value.getFullYear()
  const month = viewDate.value.getMonth()
  const start = new Date(year, month, 1).getDay()
  const count = new Date(year, month + 1, 0).getDate()
  return Array.from({ length: start + count }, (_, index) =>
    index < start ? null : index - start + 1,
  )
})

function changeMonth(amount: number) {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() + amount, 1)
}

function setMonth(event: Event) {
  viewDate.value = new Date(
    viewDate.value.getFullYear(),
    Number((event.target as HTMLSelectElement).value),
    1,
  )
}

function setYear(event: Event) {
  viewDate.value = new Date(
    Number((event.target as HTMLSelectElement).value),
    viewDate.value.getMonth(),
    1,
  )
}

function toggleOpen() {
  if (props.disabled) return
  if (!open.value) {
    window.dispatchEvent(new CustomEvent('thai-date-picker-open', { detail: instanceId.value }))
    open.value = true
  } else {
    open.value = false
  }
}

function handleOtherOpen(e: Event) {
  const customEv = e as CustomEvent
  if (customEv.detail !== instanceId.value) {
    open.value = false
  }
}

function handleClickOutside(e: MouseEvent) {
  if (open.value && containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => {
  window.addEventListener('thai-date-picker-open', handleOtherOpen)
  document.addEventListener('pointerdown', handleClickOutside, true)
})

onUnmounted(() => {
  window.removeEventListener('thai-date-picker-open', handleOtherOpen)
  document.removeEventListener('pointerdown', handleClickOutside, true)
})

function choose(day: number) {
  if (isDisabled(day)) return
  const year = viewDate.value.getFullYear()
  const month = String(viewDate.value.getMonth() + 1).padStart(2, '0')
  emit('update:modelValue', `${year}-${month}-${String(day).padStart(2, '0')}`)
  open.value = false
}

function isDisabled(day: number) {
  if (
    props.disableSunday &&
    new Date(viewDate.value.getFullYear(), viewDate.value.getMonth(), day).getDay() === 0
  )
    return true
  if (!props.minDate) return false
  const candidate = `${viewDate.value.getFullYear()}-${String(viewDate.value.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  return props.inclusiveMin ? candidate < props.minDate : candidate <= props.minDate
}

function isSelected(day: number) {
  return (
    props.modelValue ===
    `${viewDate.value.getFullYear()}-${String(viewDate.value.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  )
}
</script>

<template>
  <div ref="containerRef" class="thai-date-picker">
    <button
      type="button"
      class="date-trigger"
      :class="{ invalid, placeholder: !modelValue }"
      :disabled="disabled"
      @click="toggleOpen"
    >
      <span>{{ displayValue }}</span>
      <span class="trigger-actions">
        <svg v-if="showCalendarIcon" viewBox="0 0 24 24" aria-hidden="true">
          <rect x="4" y="5" width="16" height="15" rx="2" />
          <path d="M8 3v4M16 3v4M4 10h16" />
        </svg>
        <span v-if="!hideCaret" aria-hidden="true">▾</span>
      </span>
    </button>
    <div v-if="open" class="calendar" role="dialog" aria-label="เลือกวันที่">
      <div class="calendar-head">
        <button type="button" aria-label="เดือนก่อน" @click="changeMonth(-1)">‹</button>
        <div class="period-select">
          <select :value="viewDate.getMonth()" aria-label="เลือกเดือน" @change="setMonth">
            <option v-for="(month, index) in months" :key="month" :value="index">
              {{ numericMonth ? String(index + 1).padStart(2, '0') : month }}
            </option>
          </select>
          <select :value="viewDate.getFullYear()" aria-label="เลือกปี" @change="setYear">
            <option v-for="year in years" :key="year" :value="year">{{ year + 543 }}</option>
          </select>
        </div>
        <button type="button" aria-label="เดือนถัดไป" @click="changeMonth(1)">›</button>
      </div>
      <div class="weekdays">
        <span v-for="day in weekdays" :key="day">{{ day }}</span>
      </div>
      <div class="days">
        <span v-for="(day, index) in days" :key="index" class="blank">
          <button
            v-if="day"
            type="button"
            :class="{ selected: isSelected(day), disabled: isDisabled(day) }"
            :disabled="isDisabled(day)"
            @click="choose(day)"
          >
            {{ day }}
          </button>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thai-date-picker {
  position: relative;
  margin-top: 5px;
}

.date-trigger {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #8fa19e;
  border-radius: 7px;
  color: #173f44;
  background: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.date-trigger.invalid {
  border-color: #d84b4b;
  box-shadow: 0 0 0 3px #d84b4b1f;
}

.trigger-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}

.trigger-actions svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  stroke-width: 2;
  fill: none;
}

.calendar {
  position: absolute;
  z-index: 1000;
  top: calc(100% + 7px);
  left: 0;
  box-sizing: border-box;
  width: min(330px, calc(100vw - 32px));
  max-height: calc(100dvh - 24px);
  overflow-y: auto;
  padding: 14px;
  border: 1px solid #c7d7d0;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 28px #173f4430;
}

.calendar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  color: #173f44;
}

.calendar-head button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  color: #315d59;
  background: #e9f4ef;
  font-size: 24px;
  cursor: pointer;
}

.period-select {
  display: flex;
  flex: 1;
  gap: 6px;
}

.period-select select {
  min-width: 0;
  flex: 1;
  padding: 5px;
  border: 1px solid #c7d7d0;
  border-radius: 6px;
  color: #173f44;
  background: #fff;
  font: inherit;
}

.weekdays,
.days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
}

.weekdays {
  margin-bottom: 5px;
  color: #718582;
  font-size: 12px;
}

.days {
  gap: 3px;
}

.blank {
  min-height: 34px;
}

.days button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  color: #173f44;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.days button:hover,
.days button.selected {
  color: #fff;
  background: #1e807c;
}

.days button.disabled,
.days button.disabled:hover {
  color: #b5bfba;
  background: transparent;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .calendar {
    position: fixed;
    top: auto;
    right: 16px;
    bottom: max(16px, env(safe-area-inset-bottom));
    left: 16px;
    width: auto;
    max-width: 420px;
    max-height: calc(100dvh - 32px);
    margin-inline: auto;
    padding: 14px;
    border-radius: 14px;
    overscroll-behavior: contain;
  }

  .days button,
  .blank {
    width: 100%;
    height: clamp(34px, 9vw, 42px);
    min-height: 34px;
  }

  .calendar-head {
    gap: 6px;
    margin-bottom: 8px;
  }

  .calendar-head button {
    flex: 0 0 30px;
  }

  .period-select select {
    height: 38px;
    min-height: 38px;
    padding: 4px 6px;
  }

  .weekdays {
    margin-bottom: 3px;
  }
}

@media (max-width: 380px), (max-height: 620px) {
  .calendar {
    right: 10px;
    bottom: max(10px, env(safe-area-inset-bottom));
    left: 10px;
    max-height: calc(100dvh - 20px);
    padding: 10px;
  }

  .calendar-head {
    margin-bottom: 5px;
  }

  .calendar-head button {
    width: 28px;
    height: 28px;
    flex-basis: 28px;
  }

  .period-select select {
    height: 34px;
    min-height: 34px;
    font-size: 13px;
  }

  .days {
    gap: 1px;
  }

  .days button,
  .blank {
    height: 32px;
    min-height: 32px;
  }
}
</style>
