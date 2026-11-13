<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  courseApi,
  type CourseExtensionPreview,
  type DirectCourseExtension,
  type CourseExtensionRequest,
  type CoursePurchase,
} from '@/api/client'
import { vModalFocus } from '@/features/appointments/modalFocus'
import { estimateCourseExpiry } from '@/features/courses/courseExtensionDate'
const props = defineProps<{ course: CoursePurchase; patientId: number; manager?: boolean }>()
const emit = defineEmits<{ close: []; requested: []; extended: [newExpiry: string] }>()
const direct = computed(() => props.manager)
const preview = ref<CourseExtensionPreview>()
const previewLoading = ref(false)
const previewError = ref('')
const previewRevision = ref(0)
const value = ref<number | ''>(1),
  unit = ref<CourseExtensionRequest['unit']>('Month'),
  reason = ref(''),
  busy = ref(false),
  error = ref('')
const requests = ref<CourseExtensionRequest[]>([]),
  historyLoading = ref(!direct.value),
  historyError = ref('')
const submitted = ref(false)
const canRequest = computed(() => props.course.status === 'ACTIVE')
const pending = computed(() => requests.value.some((request) => request.status === 'Pending'))
const durationLimits = { Day: 99, Month: 12, Year: 1 }
const maxDuration = computed(() => durationLimits[unit.value])
const validDuration = computed(() =>
  typeof value.value === 'number' && Number.isInteger(value.value) && value.value >= 1 && value.value <= maxDuration.value,
)
const estimatedExpiry = computed(() =>
  !direct.value && validDuration.value && typeof value.value === 'number'
    ? estimateCourseExpiry(props.course.expiresAt, value.value, unit.value)
    : undefined,
)
const displayedExpiry = computed(() => direct.value ? preview.value?.newExpiry : estimatedExpiry.value)
watch(
  () => [direct.value, canRequest.value, props.course.id, props.patientId, value.value, unit.value, previewRevision.value] as const,
  async ([manager, eligible, courseId, patientId, duration, durationUnit], _, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    preview.value = undefined
    previewError.value = ''
    previewLoading.value = false
    if (!manager || !eligible || typeof duration !== 'number' || !validDuration.value) return

    const body: DirectCourseExtension = {
      courseId, requestedByPatientId: patientId, value: duration, unit: durationUnit,
      reason: '', ownerRequestConfirmed: false,
    }
    previewLoading.value = true
    try {
      const dates = await courseApi.previewDirectExtension(body)
      if (current) preview.value = dates
    } catch (err) {
      if (current) previewError.value = err instanceof Error ? err.message : 'ตรวจวันหมดอายุใหม่ไม่สำเร็จ'
    } finally {
      if (current) previewLoading.value = false
    }
  },
  { immediate: true, flush: 'sync' },
)
const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeZone: 'Asia/Bangkok' }).format(
    new Date(value.length === 10 ? value + 'T12:00:00+07:00' : value),
  )
const unitLabel = (value: CourseExtensionRequest['unit']) =>
  ({ Day: 'วัน', Month: 'เดือน', Year: 'ปี' })[value]
const units = ['Day', 'Month', 'Year'] as const
const unitOptions = units.map((value) => ({ value, label: unitLabel(value) }))
function selectUnit(value: string) {
  const selected = units.find((option) => option === value)
  if (selected) unit.value = selected
}
function updateDuration(event: Event) {
  const input = event.target as HTMLInputElement
  const number = input.valueAsNumber
  if (unit.value === 'Day' && input.value !== '' && (!/^\d{1,2}$/.test(input.value) || number > maxDuration.value)) {
    input.value = String(value.value)
    return
  }
  const next = Number.isFinite(number) ? Math.min(maxDuration.value, Math.trunc(number)) : ''
  input.value = String(next)
  value.value = next
}
watch(maxDuration, (limit) => {
  if (typeof value.value === 'number' && value.value > limit) value.value = limit
}, { flush: 'sync' })
async function loadRequests() {
  historyLoading.value = true
  historyError.value = ''
  try {
    requests.value = await courseApi.extensions({ courseId: props.course.id })
  } catch (err) {
    historyError.value = err instanceof Error ? err.message : 'โหลดคำขอต่ออายุไม่สำเร็จ'
  } finally {
    historyLoading.value = false
  }
}
onMounted(() => {
  if (!direct.value) void loadRequests()
})
async function submit() {
  if (busy.value || historyLoading.value || historyError.value || submitted.value || pending.value || !canRequest.value) return
  const dates = preview.value
  if (direct.value && (!dates || previewLoading.value)) return
  if (typeof value.value !== 'number' || !validDuration.value) return
  if (reason.value.trim().length > 255) {
    error.value = 'เหตุผลต้องไม่เกิน 255 ตัวอักษร'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const body = {
      courseId: props.course.id,
      value: value.value,
      unit: unit.value,
      reason: reason.value.trim(),
      requestedByPatientId: props.patientId,
    }
    if (direct.value && dates) {
      const extension = { ...body, ...dates, ownerRequestConfirmed: true }
      const result = await courseApi.extendDirect(extension)
      submitted.value = true
      emit('extended', result.newExpiry)
      return
    }
    await courseApi.requestExtension(body)
    submitted.value = true
    emit('requested')
  } catch (err) {
    if (direct.value) previewRevision.value++
    error.value = err instanceof Error
      ? err.message
      : direct.value ? 'ต่ออายุคอร์สไม่สำเร็จ กรุณาลองใหม่' : 'ส่งคำขอต่ออายุไม่สำเร็จ กรุณาลองใหม่'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <Teleport to="body">
    <div class="course-renewal-backdrop" @click.self="!busy && emit('close')">
      <section
        v-modal-focus="{ close: () => emit('close'), busy }"
        class="course-renewal-dialog"
        :class="{ 'is-pending': pending }"
        role="dialog"
        aria-modal="true"
        aria-labelledby="course-renewal-title"
      >
        <header>
          <h2 id="course-renewal-title">{{ direct ? 'ต่ออายุคอร์ส' : 'ขอต่ออายุคอร์ส' }}</h2>
          <button
            type="button"
            :disabled="busy"
            aria-label="ปิดหน้าต่ออายุคอร์ส"
            @click="emit('close')"
          >
            ✕
          </button>
        </header>
        <div v-if="!pending && !historyLoading" class="course-renewal-overview">
          <strong>{{ course.course.name }}</strong>
          <p>วันหมดอายุปัจจุบัน <b>{{ dateLabel(preview?.oldExpiry ?? course.expiresAt) }}</b></p>
        </div>
        <p v-if="historyLoading" role="status">กำลังตรวจสอบคำขอเดิม…</p>
        <div v-else-if="historyError" role="alert">
          {{ historyError }} <button type="button" @click="loadRequests">ลองใหม่</button>
        </div>
        <form v-else-if="canRequest && !pending && !submitted" @submit.prevent="submit">
          <div class="course-renewal-duration">
            <label>
              <span>ระยะเวลาที่ขอเพิ่ม <b aria-hidden="true">*</b></span>
              <input
                :value="value"
                @input="updateDuration"
                type="number"
                min="1"
                :max="maxDuration"
                step="1"
                required
                :aria-invalid="!validDuration"
                :disabled="busy"
              />
            </label>
            <div class="course-renewal-field">
              <span>หน่วยเวลา</span>
              <FilterDropdown
                :model-value="unit"
                :options="unitOptions"
                default-label="เลือกหน่วยเวลา"
                label="หน่วยเวลา"
                hide-default-option
                :clear-on-reselect="false"
                :disabled="busy"
                @update:model-value="selectUnit"
              />
            </div>
          </div>
          <div v-if="direct || displayedExpiry" class="course-renewal-preview" aria-live="polite">
            <p v-if="previewLoading">กำลังคำนวณวันหมดอายุใหม่…</p>
            <div v-else-if="previewError">
              <p class="course-renewal-error">{{ previewError }}</p>
              <button type="button" @click="previewRevision++">ลองใหม่</button>
            </div>
            <template v-else-if="displayedExpiry">
              <p>คอร์สจะหมดอายุวันที่ <b>{{ dateLabel(displayedExpiry) }}</b></p>
              <p v-if="direct">กดยืนยันต่ออายุเมื่อเจ้าของคอร์สเดิมแจ้งขอแล้วเท่านั้น</p>
            </template>
          </div>
          <label>
            <span>เหตุผล <small>(ไม่บังคับ)</small></span>
            <textarea v-model="reason" rows="2" maxlength="255" placeholder="ระบุเหตุผลเพิ่มเติม" :disabled="busy" />
          </label>
          <p v-if="error" class="course-renewal-error" role="alert">{{ error }}</p>
          <footer>
            <button type="button" :disabled="busy" @click="emit('close')">ยกเลิก</button>
            <button type="submit" class="course-renewal-primary" :disabled="busy || !validDuration || (direct && !preview)">
              {{ busy ? 'กำลังดำเนินการ…' : direct ? 'ยืนยันต่ออายุ' : 'ส่งคำขอให้ Manager' }}
            </button>
          </footer>
        </form>
        <p v-else-if="!submitted && pending" class="course-renewal-success" role="status">
          รอการพิจารณา
        </p>
        <p v-else-if="!submitted">คอร์สนี้ไม่อยู่ในสถานะที่ขอต่ออายุได้</p>
      </section>
    </div>
  </Teleport>
</template>
<style scoped>
.course-renewal-preview:not(:empty) {
  padding: 12px 14px;
  border-radius: 10px;
  background: #edf5f1;
}
.course-renewal-preview p {
  margin: 0;
}
.course-renewal-preview p + p {
  margin-top: 4px;
  font-size: 14px;
}
.course-renewal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1100;
  background: #173f4452;
  display: grid;
  place-items: center;
  padding: 24px;
}
.course-renewal-dialog {
  box-sizing: border-box;
  width: min(520px, 100%);
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 28px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 18px 48px #173f4433;
  color: #25372f;
  font-size: 16px;
  line-height: 1.5;
}
header,
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h2 {
  margin: 0;
  font-size: 24px;
  color: var(--color-primary-strong);
}
button {
  min-height: 44px;
  padding: 8px 14px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  background: white;
  color: var(--color-primary-strong);
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.course-renewal-primary {
  background: var(--color-primary-strong);
  color: white;
}
.course-renewal-overview {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #edf5f1;
  overflow-wrap: anywhere;
}
.course-renewal-overview p {
  margin: 3px 0 0;
  color: #52665e;
  font-size: 14px;
}
form {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}
.course-renewal-duration {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}
label,
.course-renewal-field {
  display: grid;
  gap: 8px;
}
textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 12px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  resize: vertical;
  font: inherit;
}
input {
  box-sizing: border-box;
  width: 100%;
  height: 46px;
  padding: 10px 12px;
  border: 1px solid #b7cecc;
  border-radius: 10px;
  font: inherit;
}
footer {
  justify-content: flex-end;
  flex-wrap: wrap;
}
small {
  color: var(--color-muted);
}
.course-renewal-error {
  color: #b42318;
}
.course-renewal-success {
  margin: 16px 0 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: #fff5da;
  color: #76500a;
  font-weight: 600;
  text-align: center;
}
.course-renewal-dialog.is-pending {
  width: min(390px, 100%);
}
.is-pending h2 {
  font-size: 21px;
}
.is-pending header button {
  flex: 0 0 44px;
  padding: 0;
}
:is(button, input, textarea):focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
}
@media (max-width: 380px) {
  .course-renewal-duration {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 600px) {
  .course-renewal-backdrop {
    padding: 16px;
  }
  .course-renewal-dialog {
    padding: 20px;
  }
}
</style>
