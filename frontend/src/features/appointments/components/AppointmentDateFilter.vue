<script setup lang="ts">
import { computed, ref } from 'vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { clinicToday } from '@/utils/clinicDate'
import { vModalFocus } from '../modalFocus'
const props = defineProps<{ from: string; to: string }>()
const emit = defineEmits<{ apply: [from: string, to: string] }>()
const open = ref(false),
  draftFrom = ref(''),
  draftTo = ref(''),
  error = ref('')
const year = ref(Number(clinicToday().slice(0, 4)))
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
const years = computed(() =>
  [
    ...new Set([
      Number(clinicToday().slice(0, 4)),
      year.value,
      ...Array.from({ length: 11 }, (_, i) => Number(clinicToday().slice(0, 4)) - 5 + i),
    ]),
  ].sort((a, b) => b - a),
)
const todaySelected = computed(() => props.from === clinicToday() && props.to === clinicToday())
const customSelected = computed(() => Boolean(props.from || props.to) && !todaySelected.value)
function show() {
  draftFrom.value = props.from
  draftTo.value = props.to
  year.value = Number((props.from || clinicToday()).slice(0, 4))
  error.value = ''
  open.value = true
}
function monthKey(index: number) {
  return year.value + '-' + String(index + 1).padStart(2, '0')
}
function selected(index: number) {
  const key = monthKey(index)
  return Boolean(
    draftFrom.value &&
    draftTo.value &&
    key >= draftFrom.value.slice(0, 7) &&
    key <= draftTo.value.slice(0, 7),
  )
}
function chooseMonth(index: number) {
  const key = monthKey(index),
    first = draftFrom.value.slice(0, 7),
    last = draftTo.value.slice(0, 7)
  let start = key,
    end = key
  if (first && last) {
    if (key < first) end = last
    else if (key > last) start = first
    else if (first === last) {
      draftFrom.value = ''
      draftTo.value = ''
      return
    } else if (key === first) {
      const [y, m] = key.split('-').map(Number)
      const next = new Date(y!, m!, 1)
      start = next.getFullYear() + '-' + String(next.getMonth() + 1).padStart(2, '0')
      end = last
    } else {
      start = first
      const [y, m] = key.split('-').map(Number)
      const prev = new Date(y!, m! - 2, 1)
      end = prev.getFullYear() + '-' + String(prev.getMonth() + 1).padStart(2, '0')
    }
  }
  const [y, m] = end.split('-').map(Number)
  draftFrom.value = start + '-01'
  draftTo.value = end + '-' + String(new Date(y!, m!, 0).getDate()).padStart(2, '0')
  error.value = ''
}
function apply() {
  if (!draftFrom.value || !draftTo.value || draftFrom.value > draftTo.value) {
    error.value = 'เลือกวันเริ่มต้นและสิ้นสุด โดยวันสิ้นสุดต้องไม่ใช่วันก่อนวันเริ่มต้น'
    return
  }
  emit('apply', draftFrom.value, draftTo.value)
  open.value = false
}
</script>
<template>
  <div class="appointment-date-toolbar" role="group" aria-label="เลือกช่วงวันที่นัดหมาย">
    <button
      type="button"
      :aria-pressed="todaySelected"
      @click="emit('apply', clinicToday(), clinicToday())"
    >
      วันนี้
    </button>
    <button
      type="button"
      :aria-pressed="customSelected"
      :aria-expanded="open"
      aria-haspopup="dialog"
      @click="show"
    >
      กำหนดเอง
    </button>
    <button
      type="button"
      class="appointment-date-clear"
      aria-label="ล้างช่วงวันที่เพื่อดูทุกนัดหมาย"
      title="ล้างช่วงวันที่"
      :disabled="!from && !to"
      @click="emit('apply', '', '')"
    >
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M3 10a9 9 0 1 1 9 11M3 10V4m0 6h6m3-3v5h4"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
  </div>
  <Teleport to="body">
    <div v-if="open" class="appointment-range-backdrop" @click.self="open = false">
      <section
        v-modal-focus="{ close: () => (open = false) }"
        class="appointment-range-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="appointment-range-title"
      >
        <header>
          <h2 id="appointment-range-title">เลือกช่วงเวลา</h2>
          <button type="button" class="range-close" aria-label="ปิด" @click="open = false">
            ×
          </button>
        </header>
        <div class="appointment-range-layout">
          <section class="appointment-range-months">
            <div class="range-month-heading">
              <h3>เลือกเดือน</h3>
              <label
                >ปี
                <select v-model.number="year">
                  <option v-for="value in years" :key="value" :value="value">
                    {{ value + 543 }}
                  </option>
                </select></label
              >
            </div>
            <div class="range-month-grid">
              <button
                v-for="(month, index) in months"
                :key="month"
                type="button"
                :aria-pressed="selected(index)"
                @click="chooseMonth(index)"
              >
                {{ month }}
              </button>
            </div>
          </section>
          <section class="appointment-range-dates">
            <h3>กำหนดช่วงวันที่</h3>
            <label
              >วันที่เริ่มต้น<ThaiDatePicker
                v-model="draftFrom"
                show-calendar-icon
                numeric-month
                hide-caret
                placeholder="เลือกวันที่" /></label
            ><label
              >วันที่สิ้นสุด<ThaiDatePicker
                v-model="draftTo"
                :min-date="draftFrom"
                inclusive-min
                show-calendar-icon
                numeric-month
                hide-caret
                placeholder="เลือกวันที่"
            /></label>
          </section>
        </div>
        <p v-if="error" role="alert" class="range-error">{{ error }}</p>
        <footer>
          <button type="button" @click="open = false">ยกเลิก</button
          ><button type="button" class="range-confirm" @click="apply">ยืนยัน</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
<style scoped>
.appointment-date-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
button,
select {
  min-height: 44px;
  padding: 8px 14px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  background: white;
  color: #145c59;
  font: inherit;
  font-size: 16px;
  cursor: pointer;
  box-sizing: border-box;
}
button:hover:not(:disabled) {
  background: #edf5f1;
  border-color: #145c59;
}
button[aria-pressed='true'],
.range-confirm {
  background: #167f7a;
  color: white;
  border-color: #167f7a;
}
button[aria-pressed='true']:hover {
  background: #145c59;
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.appointment-date-clear {
  width: 44px;
  padding: 10px;
  justify-content: center;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-color: transparent;
  background: transparent;
}
.appointment-date-clear svg {
  width: 20px;
  height: 20px;
}
.appointment-range-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #173f4452;
}
.appointment-range-dialog {
  width: min(800px, 100%);
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
  box-sizing: border-box;
  padding: 24px;
  border-radius: 12px;
  background: white;
  color: #25372f;
  font-size: 16px;
  line-height: 1.5;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;
}
h2 {
  margin: 0;
  font-size: 24px;
}
h3 {
  margin: 0;
  font-size: 16px;
}
.range-close {
  border: 0;
  background: #edf5f1;
  border-radius: 50%;
  width: 44px;
  font-size: 26px;
  padding: 0;
}
.appointment-range-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px;
}
.appointment-range-months {
  border-right: 1px solid #dbe3df;
  padding-right: 28px;
}
.range-month-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.range-month-heading label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.range-month-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.range-month-grid button {
  padding: 8px 4px;
  font-size: 14px;
}
.appointment-range-dates {
  display: grid;
  align-content: start;
  gap: 16px;
}
.appointment-range-dates label {
  display: grid;
  gap: 8px;
  font-size: 14px;
}
footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
}
footer button {
  min-width: 110px;
}
.range-error {
  color: #b42318;
}
:is(button, select):focus-visible {
  outline: 3px solid #145c59;
  outline-offset: 3px;
}
@media (max-width: 650px) {
  .appointment-range-backdrop {
    padding: 16px;
  }
  .appointment-range-dialog {
    padding: 20px;
  }
  .appointment-range-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
  }
  .appointment-range-months {
    border-right: 0;
    border-bottom: 1px solid #dbe3df;
    padding-right: 0;
    padding-bottom: 24px;
  }
}
</style>
