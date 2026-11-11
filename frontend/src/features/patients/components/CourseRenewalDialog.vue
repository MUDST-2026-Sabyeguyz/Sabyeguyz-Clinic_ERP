<script setup lang="ts">
// ส่งคำขอต่ออายุและแสดงประวัติ โดยให้ Manager อนุมัติก่อนเปลี่ยนวันหมดอายุ
import { computed, onMounted, ref } from 'vue'
import {
  courseApi,
  type CourseExtensionRequest,
  type CoursePurchase,
} from '@/api/client'
import { vModalFocus } from '@/features/appointments/modalFocus'
const props = defineProps<{ course: CoursePurchase; patientId: number }>()
const emit = defineEmits<{ close: []; requested: [] }>()
const value = ref(1),
  unit = ref<CourseExtensionRequest['unit']>('Month'),
  reason = ref(''),
  busy = ref(false),
  error = ref('')
const requests = ref<CourseExtensionRequest[]>([]),
  historyLoading = ref(true),
  historyError = ref('')
const submitted = ref(false)
const canRequest = computed(() => props.course.status === 'ACTIVE')
const pending = computed(() => requests.value.some((request) => request.status === 'Pending'))
const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeZone: 'Asia/Bangkok' }).format(
    new Date(value.length === 10 ? value + 'T12:00:00+07:00' : value),
  )
const unitLabel = (value: CourseExtensionRequest['unit']) =>
  ({ Day: 'วัน', Month: 'เดือน', Year: 'ปี' })[value]
const statusLabel = (status: CourseExtensionRequest['status']) =>
  ({ Pending: 'รอ Manager พิจารณา', Approved: 'อนุมัติแล้ว', Rejected: 'ไม่อนุมัติ' })[status]
async function loadRequests() {
  historyLoading.value = true
  historyError.value = ''
  try {
    // โหลดคำขอและผลพิจารณาของคอร์สนี้
    requests.value = await courseApi.extensions({ courseId: props.course.id })
  } catch (err) {
    historyError.value = err instanceof Error ? err.message : 'โหลดคำขอต่ออายุไม่สำเร็จ'
  } finally {
    historyLoading.value = false
  }
}
onMounted(loadRequests)
async function submit() {
  if (busy.value || submitted.value || pending.value || !canRequest.value) return
  if (!Number.isInteger(value.value) || value.value < 1 || !reason.value.trim() || reason.value.trim().length > 255) {
    error.value = 'ระบุระยะเวลาต่ออายุอย่างน้อย 1 หน่วย และเหตุผลไม่เกิน 255 ตัวอักษร'
    return
  }
  busy.value = true
  error.value = ''
  try {
    // ส่งคำขอเท่านั้น ไม่แก้วันหมดอายุจากหน้าเว็บ
    await courseApi.requestExtension({
      courseId: props.course.id,
      value: value.value,
      unit: unit.value,
      reason: reason.value.trim(),
      requestedByPatientId: props.patientId,
    })
    submitted.value = true
    emit('requested')
    await loadRequests()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'ส่งคำขอต่ออายุไม่สำเร็จ กรุณาลองใหม่'
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
        role="dialog"
        aria-modal="true"
        aria-labelledby="course-renewal-title"
      >
        <header>
          <h2 id="course-renewal-title">ขอต่ออายุคอร์ส</h2>
          <button
            type="button"
            :disabled="busy"
            aria-label="ปิดหน้าต่ออายุคอร์ส"
            @click="emit('close')"
          >
            ✕
          </button>
        </header>
        <p>
          <strong>{{ course.course.name }}</strong>
        </p>
        <p>วันหมดอายุปัจจุบัน {{ dateLabel(course.expiresAt) }}</p>
        <p class="course-renewal-note">
          คำขอนี้ยังไม่เปลี่ยนวันหมดอายุ Manager ต้องอนุมัติก่อน ระบบจึงบันทึกวันใหม่และประวัติ
        </p>
        <form v-if="canRequest && !pending && !submitted" @submit.prevent="submit">
          <label
            >ระยะเวลาที่ขอเพิ่ม <span aria-hidden="true">*</span>
            <input v-model.number="value" type="number" min="1" step="1" required :disabled="busy" />
          </label>
          <label
            >หน่วยเวลา <span aria-hidden="true">*</span>
            <select v-model="unit" required :disabled="busy">
              <option value="Day">วัน</option>
              <option value="Month">เดือน</option>
              <option value="Year">ปี</option>
            </select>
          </label>
          <label
            >เหตุผล <span aria-hidden="true">*</span
            ><textarea v-model="reason" rows="3" maxlength="255" required :disabled="busy" />
          </label>
          <p v-if="error" class="course-renewal-error" role="alert">{{ error }}</p>
          <footer>
            <button type="button" :disabled="busy" @click="emit('close')">ยกเลิก</button
            ><button type="submit" class="course-renewal-primary" :disabled="busy">
              {{ busy ? 'กำลังส่งคำขอ…' : 'ส่งคำขอให้ Manager' }}
            </button>
          </footer>
        </form>
        <p v-else-if="submitted" class="course-renewal-success" role="status">
          ส่งคำขอแล้ว รอ Manager อนุมัติก่อนวันหมดอายุจะเปลี่ยน
        </p>
        <p v-else-if="pending" class="course-renewal-success" role="status">
          คอร์สนี้มีคำขอต่ออายุที่รอ Manager พิจารณาอยู่แล้ว
        </p>
        <p v-else>คอร์สนี้ไม่อยู่ในสถานะที่ขอต่ออายุได้</p>
        <section class="course-renewal-history">
          <h3>คำขอและประวัติการต่ออายุ</h3>
          <p v-if="historyLoading" role="status">กำลังโหลดประวัติ…</p>
          <div v-else-if="historyError" role="alert">
            {{ historyError }} <button type="button" @click="loadRequests">ลองใหม่</button>
          </div>
          <p v-else-if="!requests.length">ยังไม่มีคำขอต่ออายุ</p>
          <ol v-else>
            <li v-for="item in requests" :key="item.id">
              <strong>{{ statusLabel(item.status) }} · เพิ่ม {{ item.value }} {{ unitLabel(item.unit) }}</strong>
              <p v-if="item.reason">เหตุผล: {{ item.reason }}</p>
              <p v-if="item.oldExpiry && item.newExpiry">
                วันหมดอายุ {{ dateLabel(item.oldExpiry) }} → {{ dateLabel(item.newExpiry) }}
              </p>
              <small v-if="item.decisionNote">หมายเหตุ Manager: {{ item.decisionNote }}</small>
            </li>
          </ol>
        </section>
      </section>
    </div>
  </Teleport>
</template>
<style scoped>
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
  width: min(600px, 100%);
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
  padding: 28px;
  background: white;
  border-radius: 12px;
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
h3 {
  font-size: 18px;
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
.course-renewal-note {
  padding: 12px;
  background: #edf5f1;
  color: #52665e;
  font-size: 14px;
}
form {
  display: grid;
  gap: 16px;
}
label {
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
input,
select {
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  font: inherit;
}
footer {
  justify-content: flex-end;
  margin-top: 8px;
  padding-bottom: 16px;
}
.course-renewal-history {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #dbe3df;
}
ol {
  padding-left: 24px;
}
li {
  margin-bottom: 16px;
  overflow-wrap: anywhere;
}
li p {
  margin: 4px 0;
}
small {
  color: var(--color-muted);
}
.course-renewal-error {
  color: #b42318;
}
.course-renewal-success {
  color: var(--color-primary-strong);
  font-weight: 600;
}
:is(button, textarea):focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
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
