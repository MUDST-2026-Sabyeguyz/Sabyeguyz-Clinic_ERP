<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { courseApi, type CoursePurchase, type CourseRenewalHistory } from '@/api/client'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { vModalFocus } from '@/features/appointments/modalFocus'
import { clinicToday } from '@/utils/clinicDate'
const props = defineProps<{ course: CoursePurchase; patientId: number }>()
const emit = defineEmits<{ close: []; renewed: [] }>()
const expiry = ref(''),
  reason = ref(''),
  busy = ref(false),
  error = ref('')
const history = ref<CourseRenewalHistory[]>([]),
  historyLoading = ref(true),
  historyError = ref('')
const completed = ref(false)
const canRenew = computed(
  () => props.course.status === 'ACTIVE' && props.course.purchased > props.course.used,
)
const oldExpiry = props.course.expiresAt.slice(0, 10)
const valid = computed(
  () =>
    expiry.value > oldExpiry &&
    expiry.value > clinicToday() &&
    Boolean(reason.value.trim()) &&
    reason.value.trim().length <= 1000,
)
const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeZone: 'Asia/Bangkok' }).format(
    new Date(value.length === 10 ? value + 'T12:00:00+07:00' : value),
  )
async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    history.value = await courseApi.renewals(props.course.id)
  } catch (err) {
    historyError.value = err instanceof Error ? err.message : 'โหลดประวัติไม่สำเร็จ'
  } finally {
    historyLoading.value = false
  }
}
onMounted(loadHistory)
async function submit() {
  if (busy.value || completed.value || !canRenew.value) return
  if (!valid.value) {
    error.value = 'เลือกวันใหม่ที่หลังวันเดิมและหลังวันนี้ พร้อมระบุเหตุผล'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await courseApi.renew(props.course.id, {
      patientId: props.patientId,
      expectedExpiryDate: oldExpiry,
      newExpiryDate: expiry.value,
      reason: reason.value.trim(),
    })
    completed.value = true
    emit('renewed')
    await loadHistory()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'ต่ออายุไม่สำเร็จ กรุณาลองใหม่'
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
          <h2 id="course-renewal-title">ต่ออายุคอร์ส</h2>
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
        <p>วันหมดอายุเดิม {{ dateLabel(oldExpiry) }}</p>
        <p class="course-renewal-note">
          เปลี่ยนเฉพาะวันหมดอายุของคอร์สและสิทธิ์ที่แชร์ ไม่เพิ่มจำนวนครั้งหรือสร้างยอดชำระใหม่
        </p>
        <form v-if="!completed && canRenew" @submit.prevent="submit">
          <label
            >วันหมดอายุใหม่ <span aria-hidden="true">*</span
            ><ThaiDatePicker
              v-model="expiry"
              :disabled="busy"
              :min-date="oldExpiry > clinicToday() ? oldExpiry : clinicToday()"
              show-calendar-icon
              numeric-month
              hide-caret
          /></label>
          <label
            >เหตุผล <span aria-hidden="true">*</span
            ><textarea v-model="reason" rows="3" maxlength="1000" required :disabled="busy" />
          </label>
          <p v-if="error" class="course-renewal-error" role="alert">{{ error }}</p>
          <footer>
            <button type="button" :disabled="busy" @click="emit('close')">ยกเลิก</button
            ><button type="submit" class="course-renewal-primary" :disabled="busy">
              {{ busy ? 'กำลังบันทึก…' : 'ยืนยันต่ออายุ' }}
            </button>
          </footer>
        </form>
        <p v-else-if="completed" class="course-renewal-success" role="status">
          ต่ออายุถึง {{ dateLabel(expiry) }} แล้ว
        </p>
        <p v-else>คอร์สนี้ต่ออายุไม่ได้ สามารถดูประวัติการต่ออายุด้านล่างได้</p>
        <section class="course-renewal-history">
          <h3>ประวัติการต่ออายุ</h3>
          <p v-if="historyLoading" role="status">กำลังโหลดประวัติ…</p>
          <div v-else-if="historyError" role="alert">
            {{ historyError }} <button type="button" @click="loadHistory">ลองใหม่</button>
          </div>
          <p v-else-if="!history.length">ยังไม่มีประวัติการต่ออายุ</p>
          <ol v-else>
            <li v-for="item in history" :key="item.id">
              <strong
                >{{ dateLabel(item.oldExpiryDate) }} → {{ dateLabel(item.newExpiryDate) }}</strong
              >
              <p>{{ item.reason }}</p>
              <small>{{ dateLabel(item.changedAt) }} · ผู้ดำเนินการ #{{ item.changedBy }}</small>
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
  color: #145c59;
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
  color: #145c59;
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.course-renewal-primary {
  background: #145c59;
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
footer {
  justify-content: flex-end;
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
  color: #56665d;
}
.course-renewal-error {
  color: #b42318;
}
.course-renewal-success {
  color: #145c59;
  font-weight: 600;
}
:is(button, textarea):focus-visible {
  outline: 3px solid #145c59;
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
