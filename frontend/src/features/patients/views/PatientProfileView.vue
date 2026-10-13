<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { patientApi, type PatientProfile, type PatientUpdatePayload } from '@/api/client'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

const route = useRoute()
type UiPatientProfile = PatientProfile & {
  nickname: string
  email: string
  bloodGroup: string
  nationality: string
  customerType: string
  customerSource: string
  insuranceCompany: string
}
const patient = ref<UiPatientProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const editing = ref(false)
const saved = ref(false)
const confirmationOpen = ref(false)
const titleOptions = ['นาย', 'นาง', 'นางสาว'].map((value) => ({ value, label: value }))
const genderOptions = ['ชาย', 'หญิง'].map((value) => ({ value, label: value }))
const bloodGroupOptions = ['ไม่ทราบ', 'A', 'B', 'AB', 'O'].map((value) => ({ value, label: value }))
const nationalityOptions = ['ไทย', 'ต่างชาติ'].map((value) => ({ value, label: value }))
const customerSourceOptions = ['ลูกค้าแนะนำ', 'Facebook', 'หน้าสาขา'].map((value) => ({
  value,
  label: value,
}))
const insuranceOptions = [{ value: 'AIA', label: 'AIA' }]
function splitFullName(fullName: string) {
  const [firstName = '', ...lastName] = fullName.trim().split(/\s+/)
  return { firstName, lastName: lastName.join(' ') }
}
function toDateInput(value: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const thaiMonths = [
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
  const match = value.match(/^(\d{1,2})\s+(.+)\s+(\d{4})$/)
  const month = match ? thaiMonths.indexOf(match[2]!) : -1
  if (!match || month < 0) return ''
  return `${Number(match[3]) - 543}-${String(month + 1).padStart(2, '0')}-${String(match[1]).padStart(2, '0')}`
}
const draft = reactive({
  title: '',
  fullName: '',
  birthDate: '',
  gender: '',
  phone: '',
  identityDocumentType: '',
  identityNumber: '',
  address: '',
  drugAllergy: false,
  foodAllergy: false,
  chronicDisease: '',
  emergencyContactName: '',
  emergencyContactRelationship: '',
  emergencyContactPhone: '',
  privacyConsent: true,
  active: true,
  nickname: '',
  email: '',
  bloodGroup: 'ไม่ทราบ',
  nationality: 'ไทย',
  customerType: 'ลูกค้าทั่วไป',
  customerSource: 'หน้าสาขา',
  insuranceCompany: '',
  firstName: '',
  lastName: '',
  birthDateInput: '',
})
const isForeignNationalityValue = (value: string) =>
  ['ต่างชาติ', 'FOREIGN', 'FOREIGNER', 'NON_THAI'].includes(value.trim().toUpperCase())
const isForeignNationality = computed(() => isForeignNationalityValue(draft.nationality))
const isPassportDocument = computed(() => draft.identityDocumentType === 'หนังสือเดินทาง')
const identityDocumentOptions = computed(() =>
  (isForeignNationality.value ? ['หนังสือเดินทาง'] : ['บัตรประจำตัวประชาชน', 'หนังสือเดินทาง']).map(
    (value) => ({ value, label: value }),
  ),
)
const digitsOnly = (value: string) => value.replace(/\D/g, '')
watch(
  () => draft.nationality,
  (nationality) => {
    if (isForeignNationalityValue(nationality) && draft.identityDocumentType !== 'หนังสือเดินทาง') {
      draft.identityDocumentType = 'หนังสือเดินทาง'
      draft.identityNumber = ''
    }
  },
)
watch(
  () => draft.title,
  (title) => {
    if (title === 'นาย') draft.gender = 'ชาย'
    else if (title === 'นาง' || title === 'นางสาว') draft.gender = 'หญิง'
  },
)
watch(
  () => draft.gender,
  (gender) => {
    if (gender === 'ชาย') draft.title = 'นาย'
    else if (gender === 'หญิง') draft.title = 'นางสาว'
  },
)
function normalizeProfile(profile: PatientProfile): UiPatientProfile {
  return {
    ...profile,
    nickname: profile.nickname ?? '',
    email: profile.email ?? '',
    bloodGroup: profile.bloodGroup ?? 'ไม่ทราบ',
    nationality: profile.nationality ?? 'ไทย',
    customerType: profile.customerType ?? 'ลูกค้าทั่วไป',
    customerSource: profile.customerSource ?? 'หน้าสาขา',
    insuranceCompany: profile.insuranceCompany ?? '',
  }
}
function assignDraft(profile: UiPatientProfile) {
  const foreignPatient = isForeignNationalityValue(profile.nationality)
  Object.assign(draft, profile, splitFullName(profile.fullName), {
    nationality: foreignPatient ? 'ต่างชาติ' : profile.nationality,
    identityDocumentType:
      foreignPatient || profile.identityDocumentType === 'PASSPORT'
        ? 'หนังสือเดินทาง'
        : 'บัตรประจำตัวประชาชน',
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
  errorMessage.value = ''
  try {
    const payload: PatientUpdatePayload = {
      title: draft.title,
      fullName: `${draft.firstName} ${draft.lastName}`.trim(),
      birthDate: draft.birthDateInput,
      gender: draft.gender,
      phone: draft.phone,
      identityDocumentType: isPassportDocument.value ? 'PASSPORT' : 'NATIONAL_ID',
      identityNumber: draft.identityNumber,
      address: draft.address,
      drugAllergy: draft.drugAllergy,
      foodAllergy: draft.foodAllergy,
      chronicDisease: draft.chronicDisease,
      emergencyContactName: draft.emergencyContactName,
      emergencyContactRelationship: draft.emergencyContactRelationship,
      emergencyContactPhone: draft.emergencyContactPhone,
      privacyConsent: draft.privacyConsent,
      active: draft.active,
      nickname: draft.nickname,
      email: draft.email,
      bloodGroup: draft.bloodGroup,
      nationality: draft.nationality,
      customerType: draft.customerType,
      customerSource: draft.customerSource,
      insuranceCompany: draft.insuranceCompany,
    }
    const result = await patientApi.update(patient.value.id, payload)
    patient.value = normalizeProfile(result)
    confirmationOpen.value = false
    editing.value = false
    saved.value = true
    window.setTimeout(() => {
      saved.value = false
    }, 1800)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Request failed'
  }
}
onMounted(async () => {
  const id = Number(route.params.id)
  if (!Number.isInteger(id)) {
    errorMessage.value = 'รหัสผู้ป่วยไม่ถูกต้อง'
    loading.value = false
    return
  }
  try {
    patient.value = normalizeProfile(await patientApi.get(id))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="patient-workspace patient-profile-page">
    <StaffHeader />
    <main>
      <button v-if="editing" type="button" class="back-link back-link-button" @click="cancelEdit">
        ← กลับข้อมูลผู้ป่วย
      </button>
      <RouterLink v-else class="back-link" to="/staff/patients">← กลับไปค้นหาผู้ป่วย</RouterLink>
      <p v-if="loading" class="status-feedback">กำลังโหลดข้อมูลผู้ป่วย...</p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <section v-if="patient && !editing" class="profile-heading">
        <div class="avatar">{{ patient.fullName.slice(0, 1) }}</div>
        <div>
          <small>PATIENT PROFILE</small>
          <h1>{{ patient.title }}{{ patient.fullName }}</h1>
          <p>HN {{ patient.hn }}</p>
        </div>
        <div class="profile-actions">
          <RouterLink
            class="profile-action-button"
            :to="{ path: `/staff/patients/${patient.id}/history`, query: { tab: 'courses' } }"
            >คอร์ส</RouterLink
          >
          <RouterLink class="profile-action-button" :to="`/staff/patients/${patient.id}/history`"
            >ประวัติการรักษา</RouterLink
          >
          <button class="profile-action-button" type="button" @click="openEdit">แก้ไขข้อมูล</button>
        </div>
      </section>

      <h1 v-if="patient && editing" class="patient-edit-title">แก้ไขข้อมูลผู้ป่วย</h1>
      <form
        v-if="patient && editing"
        class="patient-edit"
        @submit.prevent="confirmationOpen = true"
      >
        <div class="edit-fields">
          <h3>ข้อมูลส่วนตัว</h3>
          <label
            >คำนำหน้า
            <FilterDropdown
              v-model="draft.title"
              :options="titleOptions"
              default-label="เลือกคำนำหน้า"
              label="คำนำหน้า"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label>อีเมล <input v-model="draft.email" type="text" inputmode="email" /></label>
          <label>ชื่อ <em>*</em><input v-model="draft.firstName" required /></label>
          <label>นามสกุล <em>*</em><input v-model="draft.lastName" required /></label>
          <label>ชื่อเล่น <input v-model="draft.nickname" /></label>
          <label
            >เพศ
            <FilterDropdown
              v-model="draft.gender"
              :options="genderOptions"
              default-label="เลือกเพศ"
              label="เพศ"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >วันเกิด <em>*</em
            ><ThaiDatePicker v-model="draft.birthDateInput" show-calendar-icon hide-caret
          /></label>
          <label>เบอร์โทรศัพท์ <em>*</em><input v-model="draft.phone" type="tel" required /></label>
          <label
            >กรุ๊ปเลือด
            <FilterDropdown
              v-model="draft.bloodGroup"
              :options="bloodGroupOptions"
              default-label="เลือกกรุ๊ปเลือด"
              label="กรุ๊ปเลือด"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >สัญชาติ
            <FilterDropdown
              v-model="draft.nationality"
              :options="nationalityOptions"
              default-label="เลือกสัญชาติ"
              label="สัญชาติ"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >ประเภทเอกสารยืนยันตัวตน <em>*</em>
            <FilterDropdown
              v-model="draft.identityDocumentType"
              :options="identityDocumentOptions"
              default-label="เลือกประเภทเอกสาร"
              label="ประเภทเอกสารยืนยันตัวตน"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >{{ isPassportDocument ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }} <em>*</em
            ><input
              v-model.trim="draft.identityNumber"
              inputmode="numeric"
              :maxlength="isPassportDocument ? 20 : 13"
              :placeholder="
                isPassportDocument
                  ? 'กรอกเลขหนังสือเดินทางเป็นตัวเลข'
                  : 'กรอกเลขบัตรประชาชน 13 หลัก'
              "
              required
              @input="draft.identityNumber = digitsOnly(draft.identityNumber)"
          /></label>
          <label
            >กลุ่มลูกค้า<input :value="draft.customerType" readonly aria-readonly="true"
          /></label>
          <label
            >ช่องทางแนะนำ
            <FilterDropdown
              v-model="draft.customerSource"
              :options="customerSourceOptions"
              default-label="เลือกช่องทางแนะนำ"
              label="ช่องทางแนะนำ"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >บริษัทประกัน
            <FilterDropdown
              v-model="draft.insuranceCompany"
              :options="insuranceOptions"
              default-label="ไม่มีประกัน"
              label="บริษัทประกัน"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label class="wide"
            >ที่อยู่ <em>*</em><textarea v-model="draft.address" rows="3" required></textarea>
          </label>
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
            <dt>
              {{
                patient.identityDocumentType === 'หนังสือเดินทาง'
                  ? 'เลขหนังสือเดินทาง'
                  : 'เลขบัตรประชาชน'
              }}
            </dt>
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
      <Teleport to="body">
        <Transition name="save-success">
          <div v-if="saved && !editing" class="patient-save-success-backdrop" role="status">
            <section class="patient-save-success">แก้ไขข้อมูลสำเร็จ</section>
          </div>
        </Transition>
        <div
          v-if="confirmationOpen"
          class="patient-confirmation-backdrop"
          @click.self="confirmationOpen = false"
        >
          <section class="patient-confirmation" role="alertdialog" aria-modal="true">
            <h2>ต้องการบันทึกการแก้ไขข้อมูลหรือไม่?</h2>
            <div>
              <button type="button" class="outline" @click="confirmationOpen = false">ไม่ใช่</button
              ><button type="button" class="primary" @click="saveEdit">ใช่</button>
            </div>
          </section>
        </div>
      </Teleport>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
