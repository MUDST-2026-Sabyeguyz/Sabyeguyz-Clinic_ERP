<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { appointmentApi, type Appointment } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { statusText } from '@/features/appointments/calendarLayout'

const props = defineProps<{ branchCode: string; keyword?: string }>()
defineEmits<{ select: [appointment: Appointment] }>()
const appointments = ref<Appointment[]>([])
const loading = ref(false)
const error = ref('')
let request = 0
const today = clinicToday()
const dateLabel = new Intl.DateTimeFormat('th-TH', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
}).format(new Date(`${today}T12:00:00`))
const time = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    timeZone: 'Asia/Bangkok',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
const visible = computed(() => {
  const keyword = props.keyword?.trim().toLocaleLowerCase() ?? ''
  return appointments.value.filter(
    (item) =>
      !keyword ||
      [item.patient?.fullName, item.patient?.hn, item.contactName, item.contactPhone].some(
        (value) => value?.toLocaleLowerCase().includes(keyword),
      ),
  )
})
async function load() {
  const current = ++request
  appointments.value = []
  error.value = ''
  loading.value = false
  const branch = props.branchCode
  if (!branch) return
  loading.value = true
  try {
    // อ่านทุกหน้าของนัดวันนี้ แล้วแสดงเฉพาะผู้ป่วย CURRENT ที่รอชำระเงิน
    const items: Appointment[] = []
    let page = 0
    let pages = 1
    while (page < pages) {
      const result = await appointmentApi.search({
        branchCode: branch,
        date: today,
        page,
        size: 100,
      })
      if (current !== request) return
      items.push(...result.items)
      pages = result.totalPages
      page++
    }
    appointments.value = items
      .filter(
        (item) =>
          item.branchCode === branch &&
          item.status === 'UNCONFIRMED' &&
          item.patientType === 'CURRENT' &&
          Boolean(item.patient?.id),
      )
      .sort((a, b) => a.startAt.localeCompare(b.startAt))
  } catch {
    if (current === request) error.value = 'โหลดรายการนัดวันนี้ไม่สำเร็จ'
  } finally {
    if (current === request) loading.value = false
  }
}
watch(() => props.branchCode, load, { immediate: true })
onBeforeUnmount(() => {
  request++
})
</script>

<template>
  <section class="today-appointments" aria-label="ผู้ป่วยที่รอชำระเงิน" :aria-busy="loading">
    <header>
      <h2>ผู้ป่วยที่รอชำระเงิน</h2>
      <span>{{ dateLabel }}</span>
    </header>
    <p v-if="!branchCode" role="status">กรุณาเลือกสาขาเพื่อดูรายการนัดหมาย</p>
    <p v-else-if="loading" role="status">กำลังโหลดรายการนัดหมาย…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <button type="button" @click="load">ลองใหม่</button>
    </div>
    <p v-else-if="!visible.length" role="status">
      {{ keyword?.trim() ? 'ไม่พบผู้ป่วยที่รอชำระเงินตามคำค้น' : 'ไม่มีผู้ป่วยที่รอชำระเงินในวันนี้' }}
    </p>
    <div v-else class="today-table-scroll">
      <table>
        <thead>
          <tr>
            <th>เวลา</th>
            <th>ผู้ป่วย / HN</th>
            <th>นักกายภาพ</th>
            <th>บริการ</th>
            <th>สถานะ</th>
            <th>ทำรายการ</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in visible" :key="item.id">
            <td>{{ time(item.startAt) }}</td>
            <td>
              <b>{{ item.patient?.fullName || item.contactName || 'ไม่ระบุชื่อ' }}</b
              ><small>{{ item.patient?.hn ? `HN ${item.patient.hn}` : 'ยังไม่ลงทะเบียน' }}</small>
            </td>
            <td>{{ item.therapist?.fullName || '—' }}</td>
            <td>{{ item.service?.name || '—' }}</td>
            <td>{{ statusText[item.status] }}</td>
            <td>
              <button
                type="button"
                :disabled="item.status === 'UNCONFIRMED' && item.patientType !== 'NEW' && !item.patient?.id"
                @click="$emit('select', item)"
              >
                {{
                  item.status === 'CONFIRMED' ? 'ดูรายการเดิม' : item.patientType === 'NEW' ? 'ลงทะเบียนผู้ป่วย' : 'ชำระเงิน'
                }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.today-appointments {
  margin-top: 28px;
  padding: 20px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: #fff;
  color: var(--color-ink);
}
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
h2 {
  margin: 0;
  font-size: 1.2rem;
}
header span,
small {
  color: #52645b;
}
.today-table-scroll {
  overflow-x: auto;
  width: 100%;
  max-width: 100%;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: white;
}
table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}
th:nth-child(1),
td:nth-child(1) {
  width: 72px;
}
th:nth-child(2),
td:nth-child(2) {
  min-width: 220px;
}
th:nth-child(3),
td:nth-child(3),
th:nth-child(4),
td:nth-child(4) {
  min-width: 170px;
}
th:nth-child(5),
td:nth-child(5) {
  width: 130px;
}
th:nth-child(6),
td:nth-child(6) {
  width: 140px;
}
th {
  background: #1b7d78;
  color: white;
  text-align: left;
  padding: 14px;
  white-space: nowrap;
}
td {
  padding: 16px 14px;
  border-bottom: 1px solid #e4ebe7;
  vertical-align: middle;
}
tr:last-child td {
  border-bottom: 0;
}
small {
  display: block;
  margin-top: 4px;
}
button {
  border: 1px solid #167d78;
  border-radius: 8px;
  background: #fff;
  color: #146c67;
  padding: 9px 12px;
  font: inherit;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}
button:hover:not(:disabled) {
  background: #eaf5f2;
}
button:focus-visible {
  outline: 3px solid #167d78;
  outline-offset: 3px;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
