<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi, patientApi, salesApi, treatmentApi, masterDataApi, type CoursePurchase, type Page, type PatientProfile, type SaleSummary, type TreatmentHistoryItem } from '@/api/client'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

const route = useRoute()
type UiPatientProfile = PatientProfile & { nickname: string; bloodGroup: string; nationality: string; patientType: string; customerGroup: string; customerSource: string; insuranceCompany: string }
const patient = ref<UiPatientProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const editing = ref(false)
const masterLoading = ref(false)
const masterOptions = ref<Array<{ category: string; name: string; active: boolean }>>([])
const masterValues = (category: string) => masterOptions.value.filter(item => item.active && item.category.trim().toLowerCase() === category.toLowerCase()).map(item => item.name)
const saved = ref(false)
const historyPageSize = 10
const receipts = ref<Page<SaleSummary>>({ items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 })
const courses = ref<Page<CoursePurchase>>({ items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 })
const treatments = ref<Page<TreatmentHistoryItem>>({ items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 })
const historyLoading = reactive({ receipts: false, courses: false, treatments: false })
const historyErrors = reactive({ receipts: '', courses: '', treatments: '' })
const historyRequestIds = reactive({ receipts: 0, courses: 0, treatments: 0 })
let profileRequestId = 0
function toDateInput(value: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const thaiMonths = ['มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน', 'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม']
  const match = value.match(/^(\d{1,2})\s+(.+)\s+(\d{4})$/)
  const month = match ? thaiMonths.indexOf(match[2]!) : -1
  if (!match || month < 0) return ''
  return `${Number(match[3]) - 543}-${String(month + 1).padStart(2, '0')}-${String(match[1]).padStart(2, '0')}`
}
const draft = reactive({
  title: '', birthDate: '', gender: '', phone: '', identityDocumentType: '', identityNumber: '', address: '',
  nickname: '', bloodGroup: 'ไม่ทราบ', nationality: 'ไทย', patientType: 'Thai', customerGroup: '', customerSource: '', insuranceCompany: '', firstName: '', lastName: '', birthDateInput: '',
})
const isPassportDocument = computed(() => draft.identityDocumentType === 'PASSPORT' || draft.identityDocumentType === 'หนังสือเดินทาง')
function normalizeProfile(profile: PatientProfile): UiPatientProfile {
  return { ...profile, nickname: profile.nickname ?? '', bloodGroup: profile.bloodGroup ?? 'ไม่ทราบ', nationality: profile.nationality ?? 'ไทย', patientType: profile.patientType ?? 'Thai', customerGroup: profile.customerGroup ?? '', customerSource: profile.customerSource ?? '', insuranceCompany: profile.insuranceCompany ?? '' }
}
function assignDraft(profile: UiPatientProfile) {
  Object.assign(draft, profile, {
    firstName: profile.firstName,
    lastName: profile.lastName,
    identityDocumentType: profile.identityDocumentType,
    birthDateInput: toDateInput(profile.birthDate ?? ''),
  })
}
async function openEdit() {
  if (patient.value) assignDraft(patient.value)
  editing.value = true
  masterLoading.value = true
  try { masterOptions.value = await masterDataApi.list() }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลพื้นฐานไม่สำเร็จ' }
  finally { masterLoading.value = false }
}
async function saveEdit() {
  if (!patient.value) return

  try {
    const result = await patientApi.update(patient.value.id, {
      branchCode: patient.value.branch.code,
      title: draft.title, firstName: draft.firstName, lastName: draft.lastName, birthDate: draft.birthDateInput || null,
      gender: draft.gender, phone: draft.phone, identityDocumentType: isPassportDocument.value ? 'PASSPORT' : 'NATIONAL_ID',
      identityNumber: draft.identityNumber, address: draft.address, nickname: draft.nickname, bloodGroup: draft.bloodGroup,
      nationality: draft.nationality, customerType: draft.customerGroup, customerSource: draft.customerSource, insuranceCompany: draft.insuranceCompany,
    })
    patient.value = normalizeProfile(result)
    editing.value = false
    saved.value = true
    window.setTimeout(() => { saved.value = false }, 3000)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'บันทึกข้อมูลผู้ป่วยไม่สำเร็จ' }
}
async function loadHistoryPage(kind: 'receipts' | 'courses' | 'treatments', page = 0) {
  const patientId = patient.value?.id
  if (!patientId) return
  const requestId = ++historyRequestIds[kind]
  historyLoading[kind] = true
  historyErrors[kind] = ''
  try {
    if (kind === 'receipts') {
      const result = await salesApi.list({ patientId, page, size: historyPageSize })
      if (requestId === historyRequestIds[kind] && patient.value?.id === patientId) receipts.value = result
    }
    if (kind === 'courses') {
      const result = await courseApi.patientHistory(patientId, page, historyPageSize)
      if (requestId === historyRequestIds[kind] && patient.value?.id === patientId) courses.value = result
    }
    if (kind === 'treatments') {
      const result = await treatmentApi.historyByPatient(patientId, page, historyPageSize)
      if (requestId === historyRequestIds[kind] && patient.value?.id === patientId) treatments.value = result
    }
  } catch (error) {
    if (requestId === historyRequestIds[kind] && patient.value?.id === patientId) {
      historyErrors[kind] = error instanceof Error ? error.message : 'โหลดประวัติไม่สำเร็จ'
    }
  } finally {
    if (requestId === historyRequestIds[kind]) historyLoading[kind] = false
  }
}
async function loadPatientProfile(routeId: string | string[] | undefined) {
  const requestId = ++profileRequestId
  const id = Number(routeId)
  patient.value = null
  errorMessage.value = ''
  saved.value = false
  editing.value = false
  Object.assign(historyLoading, { receipts: false, courses: false, treatments: false })
  Object.assign(historyErrors, { receipts: '', courses: '', treatments: '' })
  receipts.value = { items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 }
  courses.value = { items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 }
  treatments.value = { items: [], page: 0, size: historyPageSize, totalItems: 0, totalPages: 0 }
  loading.value = true
  if (!Number.isInteger(id)) { errorMessage.value = 'รหัสผู้ป่วยไม่ถูกต้อง'; loading.value = false; return }
  try {
    const loadedPatient = normalizeProfile(await patientApi.get(id))
    if (requestId !== profileRequestId) return
    patient.value = loadedPatient
    await Promise.all([loadHistoryPage('receipts'), loadHistoryPage('courses'), loadHistoryPage('treatments')])
  } catch (error) {
    if (requestId === profileRequestId) errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ'
  } finally {
    if (requestId === profileRequestId) loading.value = false
  }
}
watch(() => route.params.id, (id) => { void loadPatientProfile(id) }, { immediate: true })
</script>

<template>
  <div class="patient-workspace patient-profile-page">
    <StaffHeader />
    <main>
      <RouterLink class="back-link" to="/staff/patients">← กลับไปค้นหาผู้ป่วย</RouterLink>
      <p v-if="loading" class="status-feedback">กำลังโหลดข้อมูลผู้ป่วย...</p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <section v-if="patient" class="profile-heading">
        <div class="avatar">{{ patient.fullName.slice(0, 1) }}</div>
        <div>
          <small>PATIENT PROFILE</small>
          <h1>{{ patient.title }}{{ patient.fullName }}</h1>
          <p>HN {{ patient.hn }}</p>
        </div>
        <button class="primary" @click="openEdit">แก้ไขข้อมูล</button>
      </section>

      <form v-if="patient && editing" class="patient-edit" @submit.prevent="saveEdit">
        <header>
          <h2>แก้ไขข้อมูลผู้ป่วย</h2>
        </header>
        <div class="edit-fields">
          <h3>ข้อมูลส่วนตัว</h3>
          <label>คำนำหน้า <em>*</em><select v-model="draft.title" required><option>นาย</option><option>นาง</option><option>นางสาว</option><option>ด.ช.</option><option>ด.ญ.</option></select></label>
          <label>ชื่อ <em>*</em><input v-model="draft.firstName" required /></label>
          <label>นามสกุล <em>*</em><input v-model="draft.lastName" required /></label>
          <label>ชื่อเล่น <input v-model="draft.nickname" /></label>
          <label>เพศ <em>*</em><select v-model="draft.gender" required><option value="Male">ชาย</option><option value="Female">หญิง</option><option value="Other">อื่น ๆ</option></select></label>
          <label>วันเกิด<ThaiDatePicker v-model="draft.birthDateInput" /></label>
          <label>เบอร์โทรศัพท์ <em>*</em><input v-model="draft.phone" type="tel" required /></label>
          <label>กรุ๊ปเลือด<select v-model="draft.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
          <label>สัญชาติ<input v-model.trim="draft.nationality" /></label>
          <label>ประเภทเอกสารยืนยันตัวตน<select v-model="draft.identityDocumentType" @change="draft.identityNumber = ''"><option value="NATIONAL_ID">บัตรประจำตัวประชาชน</option><option value="PASSPORT">หนังสือเดินทาง</option><option value="">ไม่มีเอกสาร</option></select></label>
          <label>{{ isPassportDocument ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }} <input v-model.trim="draft.identityNumber" :maxlength="isPassportDocument ? 20 : 13" :placeholder="isPassportDocument ? 'หนังสือเดินทาง (ถ้ามี)' : 'เลขบัตรประชาชน (ถ้ามี)'" :inputmode="isPassportDocument ? 'text' : 'numeric'" /></label>
          <label>กลุ่มลูกค้า<select v-model="draft.customerGroup" :disabled="masterLoading"><option value="">ไม่ระบุ</option><option v-if="draft.customerGroup && !masterValues('Customer Group').includes(draft.customerGroup)" :value="draft.customerGroup">{{ draft.customerGroup }}</option><option v-for="value in masterValues('Customer Group')" :key="value" :value="value">{{ value }}</option></select></label>
          <label>ช่องทางแนะนำ<select v-model="draft.customerSource" :disabled="masterLoading"><option value="">ไม่ระบุ</option><option v-if="draft.customerSource && !masterValues('Referral Channel').includes(draft.customerSource)" :value="draft.customerSource">{{ draft.customerSource }}</option><option v-for="value in masterValues('Referral Channel')" :key="value" :value="value">{{ value }}</option></select></label>
          <label>บริษัทประกัน<select v-model="draft.insuranceCompany" :disabled="masterLoading"><option value="">ไม่ระบุ</option><option v-if="draft.insuranceCompany && !masterValues('Insurance Company').includes(draft.insuranceCompany)" :value="draft.insuranceCompany">{{ draft.insuranceCompany }}</option><option v-for="value in masterValues('Insurance Company')" :key="value" :value="value">{{ value }}</option></select></label>
          <label class="wide">ที่อยู่<textarea v-model="draft.address" rows="3"></textarea></label>

        </div>
        <footer>
          <button type="button" class="outline" @click="editing = false">ยกเลิก</button
          ><button class="primary">บันทึกข้อมูล</button>
        </footer>
      </form>
      <section v-else-if="patient" class="profile-sheet">
        <div class="section-title">
          <div>
            <h2>ข้อมูลส่วนตัว</h2>
          </div>
        </div>
        <dl class="details four-columns">
          <div>
            <dt>ชื่อ–นามสกุล</dt>
            <dd>{{ patient.title }}{{ patient.fullName }}</dd>
          </div>
          <div>
            <dt>ชื่อเล่น</dt>
            <dd>{{ patient.nickname || '-' }}</dd>
          </div>
          <div>
            <dt>เพศ</dt>
            <dd>{{ patient.gender }}</dd>
          </div>
          <div>
            <dt>เบอร์โทรศัพท์</dt>
            <dd>{{ patient.phone }}</dd>
          </div>
          <div>
            <dt>วันเกิด</dt>
            <dd>{{ patient.birthDate || '-' }}</dd>
          </div>
          <div>
            <dt>กรุ๊ปเลือด</dt>
            <dd>{{ patient.bloodGroup }}</dd>
          </div>
          <div>
            <dt>ประเภทเอกสารยืนยันตัวตน</dt>
            <dd>{{ patient.identityDocumentType }}</dd>
          </div>
          <div>
            <dt>{{ patient.identityDocumentType === 'PASSPORT' ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }}</dt>
            <dd>{{ patient.identityNumber }}</dd>
          </div>
          <div>
            <dt>สัญชาติ</dt>
            <dd>{{ patient.nationality }}</dd>
          </div>
          <div>
            <dt>กลุ่มลูกค้า</dt>
            <dd>{{ patient.customerGroup || '-' }}</dd>
          </div>
          <div>
            <dt>ช่องทางแนะนำ</dt>
            <dd>{{ patient.customerSource }}</dd>
          </div>
          <div>
            <dt>บริษัทประกัน</dt>
            <dd>{{ patient.insuranceCompany || 'ไม่มีประกัน' }}</dd>
          </div>
          <div class="span-all">
            <dt>ที่อยู่</dt>
            <dd>{{ patient.address || '-' }}</dd>
          </div>
        </dl>
      </section>
      <section v-if="patient" class="profile-sheet patient-history" aria-labelledby="history-heading">
        <h2 id="history-heading">ประวัติการรักษาและการซื้อ</h2>
        <section aria-labelledby="receipt-history-heading">
          <h3 id="receipt-history-heading">ใบเสร็จ</h3>
          <p v-if="historyLoading.receipts" role="status">กำลังโหลดใบเสร็จ...</p>
          <p v-else-if="historyErrors.receipts" role="alert">{{ historyErrors.receipts }}</p>
          <p v-else-if="receipts.items.length === 0">ยังไม่มีประวัติใบเสร็จ</p>
          <ul v-else>
            <li v-for="receipt in receipts.items" :key="receipt.id">
              {{ receipt.receiptNo }} · {{ receipt.soldAt }} · {{ receipt.total }} บาท · {{ receipt.status }}
            </li>
          </ul>
          <div v-if="receipts.totalPages > 1" class="history-pagination">
            <button type="button" :disabled="historyLoading.receipts || receipts.page === 0" @click="loadHistoryPage('receipts', receipts.page - 1)">ก่อนหน้า</button>
            <span>หน้า {{ receipts.page + 1 }} / {{ receipts.totalPages }}</span>
            <button type="button" :disabled="historyLoading.receipts || receipts.page + 1 >= receipts.totalPages" @click="loadHistoryPage('receipts', receipts.page + 1)">ถัดไป</button>
          </div>
        </section>
        <section aria-labelledby="course-history-heading">
          <h3 id="course-history-heading">คอร์ส</h3>
          <p v-if="historyLoading.courses" role="status">กำลังโหลดประวัติคอร์ส...</p>
          <p v-else-if="historyErrors.courses" role="alert">{{ historyErrors.courses }}</p>
          <p v-else-if="courses.items.length === 0">ยังไม่มีประวัติคอร์ส</p>
          <ul v-else>
            <li v-for="purchase in courses.items" :key="purchase.id">
              {{ purchase.course.name }} · {{ purchase.purchasedAt }} · {{ purchase.price }} บาท · คงเหลือ {{ purchase.remaining }} ครั้ง · {{ purchase.status }}
            </li>
          </ul>
          <div v-if="courses.totalPages > 1" class="history-pagination">
            <button type="button" :disabled="historyLoading.courses || courses.page === 0" @click="loadHistoryPage('courses', courses.page - 1)">ก่อนหน้า</button>
            <span>หน้า {{ courses.page + 1 }} / {{ courses.totalPages }}</span>
            <button type="button" :disabled="historyLoading.courses || courses.page + 1 >= courses.totalPages" @click="loadHistoryPage('courses', courses.page + 1)">ถัดไป</button>
          </div>
        </section>
        <section aria-labelledby="care-history-heading">
          <h3 id="care-history-heading">การรักษา</h3>
          <p v-if="historyLoading.treatments" role="status">กำลังโหลดประวัติการรักษา...</p>
          <p v-else-if="historyErrors.treatments" role="alert">{{ historyErrors.treatments }}</p>
          <p v-else-if="treatments.items.length === 0">ยังไม่มีประวัติการรักษา</p>
          <ul v-else>
            <li v-for="treatment in treatments.items" :key="treatment.id">
              {{ treatment.createdAt }} · {{ treatment.service }} · {{ treatment.note }}
            </li>
          </ul>
          <div v-if="treatments.totalPages > 1" class="history-pagination">
            <button type="button" :disabled="historyLoading.treatments || treatments.page === 0" @click="loadHistoryPage('treatments', treatments.page - 1)">ก่อนหน้า</button>
            <span>หน้า {{ treatments.page + 1 }} / {{ treatments.totalPages }}</span>
            <button type="button" :disabled="historyLoading.treatments || treatments.page + 1 >= treatments.totalPages" @click="loadHistoryPage('treatments', treatments.page + 1)">ถัดไป</button>
          </div>
        </section>
      </section>
      <Transition name="snackbar">
        <div v-if="saved && !editing" class="profile-snackbar" role="status">บันทึกข้อมูลผู้ป่วยแล้ว</div>
      </Transition>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
