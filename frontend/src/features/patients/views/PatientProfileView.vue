<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patientApi, type PatientProfile } from '@/api/client'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

const route = useRoute()
type UiPatientProfile = PatientProfile & { nickname: string; email: string; bloodGroup: string; nationality: string; customerType: string; customerSource: string; insuranceCompany: string }
const patient = ref<UiPatientProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const editing = ref(false)
const saved = ref(false)
function splitFullName(fullName: string) {
  const [firstName = '', ...lastName] = fullName.trim().split(/\s+/)
  return { firstName, lastName: lastName.join(' ') }
}
function toDateInput(value: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const thaiMonths = ['มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน', 'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม']
  const match = value.match(/^(\d{1,2})\s+(.+)\s+(\d{4})$/)
  const month = match ? thaiMonths.indexOf(match[2]!) : -1
  if (!match || month < 0) return ''
  return `${Number(match[3]) - 543}-${String(month + 1).padStart(2, '0')}-${String(match[1]).padStart(2, '0')}`
}
const draft = reactive({
  title: '', fullName: '', birthDate: '', gender: '', phone: '', identityDocumentType: '', identityNumber: '', address: '',
  drugAllergy: false, foodAllergy: false, chronicDisease: '', emergencyContactName: '', emergencyContactRelationship: '', emergencyContactPhone: '', privacyConsent: true, active: true,
  nickname: '', email: '', bloodGroup: 'ไม่ทราบ', nationality: 'ไทย', customerType: 'ลูกค้าทั่วไป', customerSource: 'หน้าสาขา', insuranceCompany: '', firstName: '', lastName: '', birthDateInput: '',
})
const isPassportDocument = computed(() => draft.identityDocumentType === 'หนังสือเดินทาง')
const digitsOnly = (value: string) => value.replace(/\D/g, '')
watch(() => draft.title, (title) => {
  if (title === 'นาย') draft.gender = 'ชาย'
  else if (title === 'นาง' || title === 'นางสาว') draft.gender = 'หญิง'
})
watch(() => draft.gender, (gender) => {
  if (gender === 'ชาย') draft.title = 'นาย'
  else if (gender === 'หญิง') draft.title = 'นางสาว'
})
function normalizeProfile(profile: PatientProfile): UiPatientProfile {
  return { ...profile, nickname: profile.nickname ?? '', email: profile.email ?? '', bloodGroup: profile.bloodGroup ?? 'ไม่ทราบ', nationality: profile.nationality ?? 'ไทย', customerType: profile.customerType ?? 'ลูกค้าทั่วไป', customerSource: profile.customerSource ?? 'หน้าสาขา', insuranceCompany: profile.insuranceCompany ?? '' }
}
function assignDraft(profile: UiPatientProfile) {
  Object.assign(draft, profile, splitFullName(profile.fullName), {
    identityDocumentType: profile.identityDocumentType === 'PASSPORT' ? 'หนังสือเดินทาง' : 'บัตรประจำตัวประชาชน',
    birthDateInput: toDateInput(String(profile.birthDate)),
  })
}
function openEdit() {
  if (patient.value) assignDraft(patient.value)
  saved.value = false
  editing.value = true
}
function cancelEdit() {
  saved.value = false
  editing.value = false
}
async function saveEdit() {
  if (!patient.value) return
  try {
    const result = await patientApi.update(patient.value.id, {
      title: draft.title, fullName: `${draft.firstName} ${draft.lastName}`.trim(), birthDate: draft.birthDateInput,
      gender: draft.gender, phone: draft.phone, identityDocumentType: isPassportDocument.value ? 'PASSPORT' : 'NATIONAL_ID',
      identityNumber: draft.identityNumber, address: draft.address, drugAllergy: draft.drugAllergy, foodAllergy: draft.foodAllergy,
      chronicDisease: draft.chronicDisease, emergencyContactName: draft.emergencyContactName, emergencyContactRelationship: draft.emergencyContactRelationship,
      emergencyContactPhone: draft.emergencyContactPhone, privacyConsent: draft.privacyConsent, active: draft.active,
      nickname: draft.nickname, email: draft.email, bloodGroup: draft.bloodGroup, nationality: draft.nationality,
      customerType: draft.customerType, customerSource: draft.customerSource, insuranceCompany: draft.insuranceCompany,
    })
    patient.value = normalizeProfile(result)
    editing.value = false
    saved.value = true
    window.setTimeout(() => { saved.value = false }, 3000)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'บันทึกข้อมูลผู้ป่วยไม่สำเร็จ' }
}
onMounted(async () => {
  const id = Number(route.params.id)
  if (!Number.isInteger(id)) { errorMessage.value = 'รหัสผู้ป่วยไม่ถูกต้อง'; loading.value = false; return }
  try { patient.value = normalizeProfile(await patientApi.get(id)) } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ' }
  finally { loading.value = false }
})
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
          <label>คำนำหน้า<select v-model="draft.title"><option>นาย</option><option>นาง</option><option>นางสาว</option></select></label>
          <label>เพศ<select v-model="draft.gender"><option>ชาย</option><option>หญิง</option></select></label>
          <label>อีเมล <input v-model="draft.email" type="text" inputmode="email" /></label>
          <label>ชื่อ <em>*</em><input v-model="draft.firstName" required /></label>
          <label>นามสกุล <em>*</em><input v-model="draft.lastName" required /></label>
          <label>ชื่อเล่น <input v-model="draft.nickname" /></label>
          <label>วันเกิด <em>*</em><ThaiDatePicker v-model="draft.birthDateInput" /></label>
          <label>เบอร์โทรศัพท์ <em>*</em><input v-model="draft.phone" type="tel" required /></label>
          <label>กรุ๊ปเลือด<select v-model="draft.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
          <label>สัญชาติ<select v-model="draft.nationality"><option>ไทย</option><option>ต่างชาติ</option></select></label>
          <label>ประเภทเอกสารยืนยันตัวตน <em>*</em><select v-model="draft.identityDocumentType" @change="draft.identityNumber = ''"><option>บัตรประจำตัวประชาชน</option><option>หนังสือเดินทาง</option></select></label>
          <label>{{ isPassportDocument ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }} <em>*</em><input v-model.trim="draft.identityNumber" inputmode="numeric" :maxlength="isPassportDocument ? 20 : 13" :placeholder="isPassportDocument ? 'กรอกเลขหนังสือเดินทางเป็นตัวเลข' : 'กรอกเลขบัตรประชาชน 13 หลัก'" required @input="draft.identityNumber = digitsOnly(draft.identityNumber)" /></label>
          <label>กลุ่มลูกค้า<input :value="draft.customerType" readonly aria-readonly="true" /></label>
          <label>ช่องทางแนะนำ<select v-model="draft.customerSource"><option>ลูกค้าแนะนำ</option><option>Facebook</option><option>หน้าสาขา</option></select></label>
          <label>บริษัทประกัน<select v-model="draft.insuranceCompany"><option>ไม่มีประกัน</option><option>AIA</option></select></label>
          <label class="wide">ที่อยู่ <em>*</em><textarea v-model="draft.address" rows="3" required></textarea></label>

        </div>
        <footer>
          <button type="button" class="outline" @click="cancelEdit">ยกเลิก</button
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
            <dt>อีเมล</dt>
            <dd>{{ patient.email || '-' }}</dd>
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
            <dd>{{ patient.birthDate }}</dd>
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
            <dt>{{ patient.identityDocumentType === 'หนังสือเดินทาง' ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }}</dt>
            <dd>{{ patient.identityNumber }}</dd>
          </div>
          <div>
            <dt>สัญชาติ</dt>
            <dd>{{ patient.nationality }}</dd>
          </div>
          <div>
            <dt>กลุ่มลูกค้า</dt>
            <dd>{{ patient.customerType }}</dd>
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
            <dd>{{ patient.address }}</dd>
          </div>
        </dl>
      </section>
      <Transition name="snackbar">
        <div v-if="saved && !editing" class="profile-snackbar" role="status">บันทึกข้อมูลผู้ป่วยแล้ว</div>
      </Transition>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
