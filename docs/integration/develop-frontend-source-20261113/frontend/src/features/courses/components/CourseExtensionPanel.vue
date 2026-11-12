<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { courseExtensionApi, type CourseExtension } from '@/api/client'

// Extending a course: the original owner asks, a Manager approves, and the new expiry date is shared by everyone
// who holds the course. Someone the course was shared with cannot ask for an extension themselves.
const props = defineProps<{ courseId: number; canRequest: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const isManager = (() => {
  try {
    return (JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null)?.role === 'MANAGER'
  } catch {
    return false
  }
})()
const items = ref<CourseExtension[]>([])
const loading = ref(false)
const busy = ref(false)
const message = ref('')
const errorMessage = ref('')
const formOpen = ref(false)
const form = ref({ value: 1, unit: 'Month' as CourseExtension['unit'], reason: '' })
const decisionNote = ref('')

const pending = computed(() => items.value.find((item) => item.status === 'Pending') ?? null)
const unitLabel: Record<CourseExtension['unit'], string> = { Day: 'วัน', Month: 'เดือน', Year: 'ปี' }
const statusLabel: Record<CourseExtension['status'], string> = {
  Pending: 'รออนุมัติ',
  Approved: 'อนุมัติแล้ว',
  Rejected: 'ไม่อนุมัติ',
}
const date = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value))
    : '—'

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    items.value = await courseExtensionApi.list(props.courseId)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดประวัติการต่ออายุไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
async function run(action: () => Promise<unknown>, done: string) {
  busy.value = true
  errorMessage.value = ''
  message.value = ''
  try {
    await action()
    message.value = done
    formOpen.value = false
    decisionNote.value = ''
    await load()
    emit('changed')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ดำเนินการไม่สำเร็จ'
  } finally {
    busy.value = false
  }
}
const submit = () => {
  if (!Number.isInteger(Number(form.value.value)) || Number(form.value.value) < 1) {
    errorMessage.value = 'ระบุระยะเวลาที่ต่ออายุเป็นจำนวนเต็มตั้งแต่ 1'
    return
  }
  void run(
    () =>
      courseExtensionApi.create({
        courseId: props.courseId,
        value: Number(form.value.value),
        unit: form.value.unit,
        reason: form.value.reason.trim() || undefined,
      }),
    'ส่งคำขอต่ออายุแล้ว รอ Manager อนุมัติ',
  )
}
onMounted(load)
watch(() => props.courseId, load)
</script>

<template>
  <section class="course-extension-panel" aria-label="การต่ออายุคอร์ส">
    <h3>การต่ออายุคอร์ส</h3>
    <p class="course-extension-rule">
      เจ้าของคอร์สเดิมเป็นผู้ขอ และ Manager เป็นผู้อนุมัติ วันหมดอายุใหม่ใช้ร่วมกันกับทุกคนที่ถือสิทธิ์คอร์สนี้
    </p>
    <p v-if="!canRequest" class="course-extension-rule">
      ผู้ที่ได้รับสิทธิ์แชร์ขอต่ออายุเองไม่ได้ ให้เจ้าของคอร์สเดิมเป็นผู้ขอ
    </p>
    <p v-if="message" role="status" class="course-extension-ok">{{ message }}</p>
    <p v-if="errorMessage" role="alert" class="course-extension-error">{{ errorMessage }}</p>

    <div v-if="pending" class="course-extension-pending">
      <strong>
        รออนุมัติ: ต่ออายุ {{ pending.value }} {{ unitLabel[pending.unit] }}
      </strong>
      <span v-if="pending.reason">เหตุผล: {{ pending.reason }}</span>
      <template v-if="isManager">
        <label>
          หมายเหตุการพิจารณา
          <input v-model.trim="decisionNote" maxlength="255" placeholder="ไม่บังคับ" />
        </label>
        <div class="course-extension-actions">
          <button
            type="button"
            :disabled="busy"
            @click="run(() => courseExtensionApi.approve(pending!.id, decisionNote), 'อนุมัติการต่ออายุแล้ว')"
          >
            อนุมัติ
          </button>
          <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="run(() => courseExtensionApi.reject(pending!.id, decisionNote), 'ไม่อนุมัติคำขอแล้ว')"
          >
            ไม่อนุมัติ
          </button>
        </div>
      </template>
      <span v-else>รอ Manager อนุมัติ</span>
    </div>
    <template v-else-if="canRequest">
      <button v-if="!formOpen" type="button" class="course-extension-open" @click="formOpen = true">
        ขอต่ออายุคอร์ส
      </button>
      <form v-else class="course-extension-form" @submit.prevent="submit">
        <label>
          ต่ออายุเพิ่ม
          <input v-model.number="form.value" type="number" min="1" inputmode="numeric" />
        </label>
        <label>
          หน่วย
          <select v-model="form.unit" aria-label="หน่วยการต่ออายุ">
            <option value="Day">วัน</option>
            <option value="Month">เดือน</option>
            <option value="Year">ปี</option>
          </select>
        </label>
        <label>
          เหตุผล
          <input v-model.trim="form.reason" maxlength="255" placeholder="ไม่บังคับ" />
        </label>
        <div class="course-extension-actions">
          <button type="submit" :disabled="busy">ส่งคำขอ</button>
          <button type="button" class="secondary" @click="formOpen = false">ยกเลิก</button>
        </div>
      </form>
    </template>

    <h4>ประวัติการต่ออายุ</h4>
    <p v-if="loading">กำลังโหลด...</p>
    <p v-else-if="items.length === 0" class="course-extension-empty">ยังไม่มีประวัติการต่ออายุ</p>
    <ul v-else class="course-extension-history">
      <li v-for="item in items" :key="item.id">
        <strong>+{{ item.value }} {{ unitLabel[item.unit] }}</strong>
        <em :class="item.status.toLowerCase()">{{ statusLabel[item.status] }}</em>
        <span v-if="item.status === 'Approved'">
          {{ date(item.oldExpiry) }} → {{ date(item.newExpiry) }}
        </span>
        <span v-if="item.reason">เหตุผล: {{ item.reason }}</span>
        <span v-if="item.decisionNote">หมายเหตุ: {{ item.decisionNote }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.course-extension-panel {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid #d7e5e3;
  border-radius: 10px;
  background: #fff;
}
.course-extension-panel h3,
.course-extension-panel h4 {
  margin: 0;
}
.course-extension-rule {
  margin: 0;
  color: #52706d;
  font-size: 0.9rem;
}
.course-extension-ok {
  margin: 0;
  color: #176b45;
}
.course-extension-error {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.course-extension-pending,
.course-extension-form {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f3f9f8;
}
.course-extension-form label,
.course-extension-pending label {
  display: grid;
  gap: 4px;
}
.course-extension-form input,
.course-extension-form select,
.course-extension-pending input {
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  font: inherit;
}
.course-extension-actions {
  display: flex;
  gap: 8px;
}
.course-extension-history {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.course-extension-history li {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
}
.course-extension-history em {
  font-style: normal;
  font-size: 0.85rem;
}
.course-extension-history em.approved {
  color: #176b45;
}
.course-extension-history em.rejected {
  color: #b42318;
}
</style>
