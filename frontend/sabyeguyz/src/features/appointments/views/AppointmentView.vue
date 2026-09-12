<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { appointmentApi, branchApi, catalogApi, patientApi, type Appointment, type AppointmentStatus, type Branch, type Patient, type ServiceOption, type Therapist } from '@/api/client'

const viewMode = ref<'list' | 'create' | 'edit'>('list')
const query = ref('')
const selectedBranch = ref('ทั้งหมด')
const appointments = ref<Appointment[]>([])
const page = ref(0)
const totalPages = ref(0)
const branches = ref<Branch[]>([])
const patients = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const therapists = ref<Therapist[]>([])
const patientQuery = ref('')
const editingId = ref<number | null>(null)
const savingStatusId = ref<number | null>(null)
const statusNotice = ref('')
const errorMessage = ref('')
const patientSearch = ref('')
const patientDropdownOpen = ref(false)
const pendingCancellation = ref<{ item: Appointment } | null>(null)
const cancellationReason = ref('')
const form = ref({ patientId: 0, therapistId: 0, serviceId: 0, branchCode: 'RP', date: new Date().toISOString().slice(0, 10), time: '09:00', note: '' })
const branchOptions = computed(() => branches.value.filter((item) => item.active))
const isEditing = computed(() => viewMode.value === 'edit')
const patientOptions = computed(() => {
  const keyword = patientSearch.value.trim().toLowerCase()
  return patients.value.filter((patient) => !keyword || `${patient.hn} ${patient.fullName}`.toLowerCase().includes(keyword))
})
const therapistOptions = computed(() => therapists.value.filter((therapist) => !form.value.branchCode || therapist.branchCode === form.value.branchCode))
const filteredAppointments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return appointments.value.filter((item) => (!keyword || `${item.patient.hn} ${item.patient.fullName} ${item.service.name} ${item.therapist.fullName}`.toLowerCase().includes(keyword)) && (selectedBranch.value === 'ทั้งหมด' || item.branchCode === selectedBranch.value))
})
const statusOptions: AppointmentStatus[] = ['CONFIRMED', 'ARRIVED', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'RESCHEDULED', 'NO_SHOW']
const timeOptions = ['09:00', '10:00', '11:00', '14:00']
const statusLabels: Record<AppointmentStatus, string> = { CONFIRMED: 'ยืนยันแล้ว (Confirmed)', ARRIVED: 'มาถึงคลินิก (Arrived)', IN_SERVICE: 'กำลังรับบริการ (In Service)', COMPLETED: 'เสร็จสิ้น (Completed)', CANCELLED: 'ยกเลิก (Cancelled)', RESCHEDULED: 'เลื่อนนัด (Rescheduled)', NO_SHOW: 'ไม่มาตามนัด (No Show)' }
const formatStatus = (status: AppointmentStatus) => statusLabels[status]
const formatThaiDate = (value: string) => new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value))
const formatTime = (value: string) => new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' })
const toStartAt = () => `${form.value.date}T${form.value.time}:00+07:00`
async function load() {
  try {
    const [a, b, p, s, t] = await Promise.all([appointmentApi.search({ page: page.value, size: 20 }), branchApi.list(), patientApi.searchAll(), catalogApi.services(), catalogApi.therapists()])
    appointments.value = a.items; totalPages.value = a.totalPages; branches.value = b; patients.value = p; services.value = s; therapists.value = t
    if (branches.value[0] && !form.value.branchCode) form.value.branchCode = branches.value[0].code
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลนัดหมายไม่สำเร็จ' }
}
onMounted(load)
async function changePage(nextPage: number) { if (nextPage >= 0 && nextPage < totalPages.value) { page.value = nextPage; await load() } }
function openCreate() { editingId.value = null; patientQuery.value = ''; patientSearch.value = ''; const branch = branchOptions.value[0]; form.value = { patientId: 0, therapistId: therapistOptions.value[0]?.id ?? 0, serviceId: services.value[0]?.id ?? 0, branchCode: branch?.code ?? 'RP', date: new Date().toISOString().slice(0, 10), time: '09:00', note: '' }; viewMode.value = 'create' }
function choosePatient(patient: Patient) { form.value.patientId = patient.id; patientQuery.value = `${patient.fullName} HN ${patient.hn}`; patientDropdownOpen.value = false }
function openEdit(item: Appointment) { editingId.value = item.id; patientQuery.value = `${item.patient.fullName} HN ${item.patient.hn}`; form.value = { patientId: item.patient.id, therapistId: item.therapist.id, serviceId: item.service.id, branchCode: item.branchCode, date: item.startAt.slice(0, 10), time: new Date(item.startAt).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' }), note: item.note ?? '' }; viewMode.value = 'edit' }
async function save() { errorMessage.value = ''; try { const patient = patients.value.find((item) => item.id === form.value.patientId); if (!editingId.value && !patient) throw new Error('กรุณาเลือกผู้ป่วยจากรายการ'); if (!form.value.patientId) throw new Error('ไม่พบผู้ป่วยที่ระบุ'); if (editingId.value) { const result = await appointmentApi.reschedule(editingId.value, { startAt: toStartAt(), therapistId: form.value.therapistId }); const index = appointments.value.findIndex((item) => item.id === result.id); if (index >= 0) appointments.value[index] = result } else { appointments.value.unshift(await appointmentApi.create({ branchCode: form.value.branchCode, patientId: form.value.patientId, therapistId: form.value.therapistId, serviceId: form.value.serviceId, startAt: toStartAt(), note: form.value.note })) }; viewMode.value = 'list' } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'บันทึกนัดหมายไม่สำเร็จ' } }
async function saveStatus(item: Appointment, status: AppointmentStatus, reason = '') { savingStatusId.value = item.id; statusNotice.value = 'กำลังบันทึก...'; try { const result = status === 'ARRIVED' ? await appointmentApi.checkIn(item.id) : status === 'IN_SERVICE' ? await appointmentApi.startService(item.id) : status === 'COMPLETED' ? await appointmentApi.complete(item.id) : status === 'NO_SHOW' ? await appointmentApi.noShow(item.id) : status === 'CANCELLED' ? await appointmentApi.cancel(item.id, reason) : null; if (!result) throw new Error('backend รองรับการเปลี่ยนสถานะผ่าน action ที่กำหนดเท่านั้น'); Object.assign(item, result); statusNotice.value = 'บันทึกแล้ว' } catch (error) { statusNotice.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ' } finally { savingStatusId.value = null; window.setTimeout(() => (statusNotice.value = ''), 2200) } }
function changeStatus(event: Event, item: Appointment) { const status = (event.target as HTMLSelectElement).value as AppointmentStatus; if (status === 'CANCELLED') { pendingCancellation.value = { item }; cancellationReason.value = ''; return } void saveStatus(item, status) }
function confirmCancellation() { if (!pendingCancellation.value || !cancellationReason.value.trim()) return; const { item } = pendingCancellation.value; pendingCancellation.value = null; void saveStatus(item, 'CANCELLED', cancellationReason.value.trim()) }
</script>
<template>
  <div
    class="staff-page appointment-page"
    :class="{ 'appointment-form-open': viewMode !== 'list' }"
  >
    <StaffHeader />
    <main class="staff-main">
      <section v-if="viewMode === 'list'" class="staff-hero">
        <small>Appointment & Patient Visit</small>
        <h1>นัดหมาย</h1>
        <p>ตรวจสอบตารางนัด สร้าง แก้ไข และอัปเดตสถานะการเข้ารับบริการ</p>
        <button class="primary hero-action" @click="openCreate">+ สร้างนัดหมาย</button>
      </section>
      <div v-if="viewMode !== 'list'" class="appointment-form-page">
        <button class="appointment-back" type="button" @click="viewMode = 'list'">
          ← กลับไปหน้านัดหมาย
        </button>
        <section class="staff-panel create-card">
          <h1>{{ isEditing ? 'แก้ไขนัดหมาย' : 'สร้างนัดหมายใหม่' }}</h1>
          <div class="form-grid">
            <label class="patient-picker">
              ผู้ป่วย
              <button type="button" class="patient-picker-trigger" :disabled="isEditing" @click="patientDropdownOpen = !patientDropdownOpen">
                {{ patients.find((patient) => patient.id === form.patientId)?.fullName ?? 'เลือกผู้ป่วย' }}
                <span>⌄</span>
              </button>
              <div v-if="patientDropdownOpen && !isEditing" class="patient-picker-menu">
                <input v-model.trim="patientSearch" placeholder="ค้นหาชื่อหรือ HN" aria-label="ค้นหาผู้ป่วย" />
                <button v-for="patient in patientOptions" :key="patient.id" type="button" class="patient-picker-option" @click="choosePatient(patient)">
                  {{ patient.fullName }} (HN {{ patient.hn }})
                </button>
                <small v-if="patientOptions.length === 0">ไม่พบผู้ป่วย</small>
              </div>
            </label>
            <label>
              ประเภทบริการ
              <select v-model="form.serviceId" :disabled="isEditing">
                <option v-for="service in services" :key="service.id" :value="service.id">{{ service.name }}</option>
              </select>
            </label>
            <label>
              นักกายภาพ
              <select v-model="form.therapistId">
                <option v-for="therapist in therapistOptions" :key="therapist.id" :value="therapist.id">
                  {{ therapist.fullName }}
                </option>
              </select>
            </label>
            <label>วันที่<input v-model="form.date" type="date" /></label>
            <label>
              เวลา
              <select v-model="form.time">
                <option v-for="time in timeOptions" :key="time" :value="time">
                  {{ time }}
                </option>
              </select>
            </label>
            <label>
              สาขา
              <select v-model="form.branchCode" :disabled="isEditing">
                <option v-for="item in branchOptions" :key="item.code" :value="item.code">{{ item.name }} ({{ item.code }})</option>
              </select>
            </label>
            <label class="wide">หมายเหตุ<textarea v-model.trim="form.note" maxlength="500" /></label>
          </div>
          <div class="appointment-save">
            <button class="primary" @click="save">บันทึก</button>
          </div>
        </section>
      </div>
      <section v-else class="staff-panel appointment-panel">
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <header class="panel-head">
          <div class="search-box">
            <span class="search-icon" aria-hidden="true"
              ><svg viewBox="0 0 24 24">
                <circle cx="11" cy="11" r="6" />
                <path d="m16 16 4 4" /></svg></span
            ><input v-model="query" placeholder="ค้นหาชื่อหรือเลขประจำตัวผู้ป่วย" />
          </div>
          <select v-model="selectedBranch" class="branch-filter" aria-label="กรองตามสาขา">
            <option value="ทั้งหมด">สาขาทั้งหมด</option>
            <option v-for="item in branchOptions" :key="item.code" :value="item.code">{{ item.name }}</option>
          </select>
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>วันที่</th>
              <th>เวลา</th>
              <th>ผู้ป่วย</th>
              <th>บริการ</th>
              <th>ผู้ให้บริการ</th>
              <th>สาขา</th>
              <th>ห้อง</th>
              <th>สถานะ</th>
              <th class="status-notice-cell">
                <span v-if="statusNotice" class="status-feedback" aria-live="polite">{{
                  statusNotice
                }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredAppointments" :key="item.id">
              <td>{{ formatThaiDate(item.startAt) }}</td>
              <td>
                <b>{{ formatTime(item.startAt) }}</b>
              </td>
              <td>
                <b>{{ item.patient.fullName }}</b>
              </td>
              <td>{{ item.service.name }}</td>
              <td>{{ item.therapist.fullName }}</td>
              <td>
                <span class="branch-label">{{ item.branchCode }}</span>
              </td>
              <td>—</td>
              <td>
                <select
                  :value="item.status"
                  class="status-select"
                  :disabled="savingStatusId !== null"
                  @change="changeStatus($event, item)"
                >
                  <option v-for="status in statusOptions" :key="status" :value="status">{{ formatStatus(status) }}</option>
                </select>
              </td>
              <td><button class="secondary" @click="openEdit(item)">แก้ไข</button></td>
            </tr>
            <tr v-if="filteredAppointments.length === 0">
              <td class="appointment-empty" colspan="9">ไม่พบข้อมูลนัดหมาย</td>
            </tr>
          </tbody>
        </table>
        <footer v-if="totalPages > 1" class="pagination">
          <button class="secondary" :disabled="page === 0" @click="changePage(page - 1)">ก่อนหน้า</button>
          <span>หน้า {{ page + 1 }} / {{ totalPages }}</span>
          <button class="secondary" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">ถัดไป</button>
        </footer>
      </section>
    </main>
    <div
      v-if="pendingCancellation"
      class="status-modal-backdrop"
      @click.self="pendingCancellation = null"
    >
      <form class="status-modal" @submit.prevent="confirmCancellation">
        <h2>ยืนยันการยกเลิกนัดหมาย</h2>
        <p>{{ pendingCancellation.item.patient.fullName }} เวลา {{ formatTime(pendingCancellation.item.startAt) }}</p>
        <label
          >เหตุผลที่ยกเลิก <em>*</em>
          <textarea v-model.trim="cancellationReason" rows="3" required></textarea>
        </label>
        <div>
          <button type="button" class="secondary" @click="pendingCancellation = null">
            กลับ
          </button>
          <button type="submit" class="danger">ยืนยันการยกเลิก</button>
        </div>
      </form>
    </div>
  </div>
</template>
<style scoped src="../styles/appointments.css"></style>
<style src="../styles/therapist.css"></style>
