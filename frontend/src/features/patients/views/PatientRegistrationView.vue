<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { registerPatient } from '@/api/patients'
import { branchApi, masterDataApi, patientApi, type Patient } from '@/api/client'
import { type PatientRegistrationForm } from '../data/patientRegistration'

const submitting = ref(false)
const route = useRoute()
const router = useRouter()
const submitted = ref(false)
const emailFocused = ref(false)
const identityFocused = ref(false)
const errorMessage = ref('')
const confirmationOpen = ref(false)
const duplicateOpen = ref(false)
const duplicatePatient = ref<Patient | null>(null)
const registeredHn = ref('')
const registeredPatientId = ref<number | null>(null)
const customerGroups = ref(['ลูกค้าทั่วไป', 'ชาวต่างชาติ'])
const referralChannels = ref(['ลูกค้าแนะนำ', 'Facebook', 'หน้าสาขา'])
const insuranceCompanies = ref(['AIA'])
const branches = ref<Array<{ code: string; name: string }>>([])
const titleOptions = ['นาย', 'นาง', 'นางสาว'].map((value) => ({ value, label: value }))
const genderOptions = ['ชาย', 'หญิง'].map((value) => ({ value, label: value }))
const bloodGroupOptions = ['ไม่ทราบ', 'A', 'B', 'AB', 'O'].map((value) => ({ value, label: value }))
const nationalityOptions = [
  { value: 'ไทย', label: 'ไทย' },
  { value: 'ต่างชาติ', label: 'ชาวต่างชาติ' },
]
const identityDocumentOptions = [
  { value: 'NATIONAL_ID', label: 'บัตรประจำตัวประชาชน' },
  { value: 'PASSPORT', label: 'หนังสือเดินทาง (Passport)' },
]
const branchOptions = computed(() =>
  branches.value.map((branch) => ({ value: branch.code, label: branch.name })),
)
const customerGroupOptions = computed(() =>
  customerGroups.value.map((value) => ({ value, label: value })),
)
const referralChannelOptions = computed(() =>
  referralChannels.value.map((value) => ({ value, label: value })),
)
const insuranceCompanyOptions = computed(() =>
  insuranceCompanies.value.map((value) => ({ value, label: value })),
)
const form = reactive<PatientRegistrationForm>({
  branchCode: 'RP',
  email: '',
  title: 'นาย',
  customerType: customerGroups.value[0]!,
  firstName: '',
  lastName: '',
  nickname: '',
  fullNameEnglish: '',
  birthDate: '',
  gender: 'ชาย',
  phone: '',
  identityDocumentType: '',
  identityNumber: '',
  address: '',
  bloodGroup: 'ไม่ทราบ',
  nationality: 'ไทย',
  customerSource: referralChannels.value[0]!,
  insuranceCompany: '',
  salesChannel: 'หน้าสาขา',
  paymentType: 'ชำระด้วยตนเอง',
  drugAllergy: false,
  foodAllergy: false,
  chronicDisease: '',
  privacyConsent: false,
})
const identityIsValid = computed(() => {
  if (form.nationality === 'ต่างชาติ') return true
  if (form.identityDocumentType === 'NATIONAL_ID') return /^\d{13}$/.test(form.identityNumber)
  if (form.identityDocumentType === 'PASSPORT') return /^\d{10}$/.test(form.identityNumber)
  return false
})
const isForeign = computed(() => form.nationality === 'ต่างชาติ')
const emailIsValid = computed(() =>
  isForeign.value && !form.email.trim()
    ? true
    : /^[a-z0-9](?:[a-z0-9._%+-]*[a-z0-9])?@gmail\.com$/i.test(form.email.trim()),
)
const emailErrorVisible = computed(
  () => submitted.value && !emailFocused.value && !emailIsValid.value,
)
const identityErrorVisible = computed(
  () => submitted.value && !identityFocused.value && !identityIsValid.value,
)
const thaiOnly = (value: string) => value.replace(/[^\u0E00-\u0E7F\s]/g, '')
const allowedName = (value: string) =>
  isForeign.value ? value.replace(/[^\p{L}\s.'-]/gu, '') : thaiOnly(value)
const digitsOnly = (value: string) => value.replace(/\D/g, '')
const thaiNameIsValid = (value: string) => /^[\u0E00-\u0E7F\s]+$/.test(value.trim())
const personNameIsValid = (value: string) =>
  isForeign.value ? /^[\p{L}\s.'-]+$/u.test(value.trim()) : thaiNameIsValid(value)
const phoneIsValid = computed(() => /^\d{10}$/.test(form.phone))
const birthDateIsValid = computed(() =>
  Boolean(form.birthDate && form.birthDate < new Date().toISOString().slice(0, 10)),
)
const canSubmit = computed(() =>
  Boolean(
    form.branchCode &&
    branches.value.length &&
    personNameIsValid(form.firstName) &&
    personNameIsValid(form.lastName) &&
    (isForeign.value || thaiNameIsValid(form.nickname)) &&
    emailIsValid.value &&
    (isForeign.value || birthDateIsValid.value) &&
    phoneIsValid.value &&
    (isForeign.value || form.identityDocumentType) &&
    identityIsValid.value &&
    (isForeign.value || form.address),
  ),
)
watch(
  () => form.title,
  (title) => {
    if (title === 'นาย') form.gender = 'ชาย'
    else if (title === 'นาง' || title === 'นางสาว') form.gender = 'หญิง'
  },
)
watch(
  () => form.gender,
  (gender) => {
    if (gender === 'ชาย') form.title = 'นาย'
    else if (gender === 'หญิง') form.title = 'นางสาว'
  },
)
watch(
  () => form.identityDocumentType,
  () => {
    form.identityNumber = ''
  },
)
watch(
  () => form.nationality,
  (nationality) => {
    form.identityDocumentType = nationality === 'ต่างชาติ' ? 'PASSPORT' : 'NATIONAL_ID'
  },
)
onMounted(async () => {
  try {
    const [items, branchList] = await Promise.all([masterDataApi.list(), branchApi.list()])
    branches.value = branchList
      .filter((item) => item.active)
      .map((item) => ({ code: item.branchCode, name: item.name }))
    if (branches.value.length && !branches.value.some((item) => item.code === form.branchCode))
      form.branchCode = branches.value[0]!.code
    const active = items.filter((item) => item.active)
    const valuesFor = (category: string, fallback: string[]) => {
      const values = active.filter((item) => item.category === category).map((item) => item.name)
      return values.length ? values : fallback
    }
    customerGroups.value = valuesFor('Customer Group', customerGroups.value)
    referralChannels.value = valuesFor('Referral Channel', referralChannels.value)
    insuranceCompanies.value = valuesFor('Insurance Company', insuranceCompanies.value)
    if (!form.customerType) form.customerType = customerGroups.value[0]!
    if (!form.customerSource) form.customerSource = referralChannels.value[0]!
  } catch {
    errorMessage.value = 'โหลดข้อมูลสาขาและข้อมูลพื้นฐานจาก backend ไม่สำเร็จ'
  }
  if (import.meta.env.DEV && route.query.previewDuplicate === '1') {
    duplicatePatient.value = {
      id: 10,
      hn: '070700000010',
      fullName: 'ผู้ป่วยสาธิต ชาย 10',
      phone: '0890000010',
      active: true,
    }
    duplicateOpen.value = true
  }
})
async function requestSubmit() {
  submitted.value = true
  if (!canSubmit.value) {
    errorMessage.value = ''
    return
  }
  try {
    const fullName = `${form.firstName} ${form.lastName}`.trim()
    const [nameCandidates, identityCandidates] = await Promise.all([
      patientApi.searchAll(fullName),
      patientApi.searchAll(form.identityNumber),
    ])
    duplicatePatient.value =
      nameCandidates.find(
        (item) =>
          item.fullName.trim().toLocaleLowerCase('th-TH') === fullName.toLocaleLowerCase('th-TH'),
      ) ??
      identityCandidates[0] ??
      null
    if (duplicatePatient.value) {
      duplicateOpen.value = true
      return
    }
  } catch {
    // The backend still performs the authoritative duplicate validation on submit.
  }
  confirmationOpen.value = true
}
function openExistingPatient() {
  if (!duplicatePatient.value) return
  duplicateOpen.value = false
  void router.push(`/staff/patients/${duplicatePatient.value.id}`)
}
async function submit() {
  confirmationOpen.value = false
  form.privacyConsent = true
  submitting.value = true
  errorMessage.value = ''
  try {
    const fullName = `${form.firstName} ${form.lastName}`.trim()
    const result = await registerPatient({
      branchCode: form.branchCode,
      username: `patient-${Date.now()}`,
      password: `Temp${form.identityNumber.slice(-6)}!`,
      email: form.email,
      title: form.title,
      fullName,
      birthDate: form.birthDate,
      gender: form.gender,
      phone: form.phone,
      identityDocumentType: form.identityDocumentType,
      identityNumber: form.identityNumber,
      address: form.address,
      drugAllergy: form.drugAllergy,
      foodAllergy: form.foodAllergy,
      chronicDisease: form.chronicDisease,
      emergencyContactName: fullName,
      emergencyContactRelationship: 'ญาติ',
      emergencyContactPhone: form.phone,
      privacyConsent: true,
      nickname: form.nickname,
      bloodGroup: form.bloodGroup,
      nationality: form.nationality,
      customerType: form.customerType,
      customerSource: form.customerSource,
      insuranceCompany: form.insuranceCompany,
    })
    registeredHn.value = result.hn
    registeredPatientId.value = result.id
    localStorage.setItem('patientCreationHn', result.hn)
    await router.push('/staff/patients')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาด'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="registration-page">
    <StaffHeader />
    <main class="registration-content">
      <div class="back-row">
        <RouterLink to="/staff/patients" class="back-link">← กลับไปค้นหาผู้ป่วย</RouterLink>
      </div>
      <section v-if="!registeredHn" class="registration-shell">
        <div class="intro">
          <h1>ลงทะเบียนผู้ป่วยใหม่</h1>
          <p>กรอกข้อมูลเพื่อสร้างประวัติและเลขประจำตัวผู้ป่วย (HN)</p>
        </div>
        <form class="form-card" novalidate @submit.prevent="requestSubmit">
          <section class="registration-section">
            <h2>ข้อมูลส่วนตัว</h2>
            <fieldset class="patient-type">
              <legend>ประเภทผู้ป่วย <em>*</em></legend>
              <label :class="{ selected: form.nationality === 'ไทย' }">
                <input v-model="form.nationality" type="radio" value="ไทย" />ไทย
              </label>
              <label :class="{ selected: form.nationality === 'ต่างชาติ' }">
                <input v-model="form.nationality" type="radio" value="ต่างชาติ" />ชาวต่างชาติ
              </label>
            </fieldset>
            <div class="form-grid">
              <label
                >คำนำหน้า<FilterDropdown
                  v-model="form.title"
                  :options="titleOptions"
                  default-label="เลือกคำนำหน้า"
                  label="คำนำหน้า"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >เพศ<FilterDropdown
                  v-model="form.gender"
                  :options="genderOptions"
                  default-label="เลือกเพศ"
                  label="เพศ"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label :class="{ 'invalid-field': submitted && !personNameIsValid(form.firstName) }"
                >ชื่อ <em>*</em
                ><input
                  v-model.trim="form.firstName"
                  placeholder="ชื่อ"
                  @input="form.firstName = allowedName(form.firstName)"
                /><small v-if="submitted && !form.firstName" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small
                  v-else-if="submitted && !personNameIsValid(form.firstName)"
                  class="required-error"
                  >รูปแบบชื่อไม่ถูกต้อง</small
                ></label
              >
              <label :class="{ 'invalid-field': submitted && !personNameIsValid(form.lastName) }"
                >นามสกุล <em>*</em
                ><input
                  v-model.trim="form.lastName"
                  placeholder="นามสกุล"
                  @input="form.lastName = allowedName(form.lastName)"
                /><small v-if="submitted && !form.lastName" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small
                  v-else-if="submitted && !personNameIsValid(form.lastName)"
                  class="required-error"
                  >รูปแบบนามสกุลไม่ถูกต้อง</small
                ></label
              >
              <label
                :class="{
                  'invalid-field': submitted && !isForeign && !thaiNameIsValid(form.nickname),
                }"
                >ชื่อเล่น <em v-if="!isForeign">*</em
                ><input
                  v-model.trim="form.nickname"
                  placeholder="ชื่อเล่น"
                  @input="form.nickname = allowedName(form.nickname)"
                /><small v-if="submitted && !isForeign && !form.nickname" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small
                  v-else-if="submitted && !isForeign && !thaiNameIsValid(form.nickname)"
                  class="required-error"
                  >รูปแบบชื่อเล่นไม่ถูกต้อง</small
                ></label
              >
              <label :class="{ 'invalid-field': submitted && !isForeign && !birthDateIsValid }"
                >วันเกิด <em v-if="!isForeign">*</em
                ><ThaiDatePicker
                  v-model="form.birthDate"
                  :invalid="submitted && !isForeign && !birthDateIsValid"
                  show-calendar-icon
                  numeric-month
                  hide-caret
                /><small v-if="submitted && !isForeign && !form.birthDate" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small
                  v-else-if="submitted && !isForeign && !birthDateIsValid"
                  class="required-error"
                  >วันเกิดต้องเป็นวันในอดีต</small
                ></label
              >
              <label
                >กรุ๊ปเลือด<FilterDropdown
                  v-model="form.bloodGroup"
                  :options="bloodGroupOptions"
                  default-label="เลือกกรุ๊ปเลือด"
                  label="กรุ๊ปเลือด"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label v-if="isForeign"
                >เลขพาสปอร์ต<input
                  v-model.trim="form.identityNumber"
                  maxlength="20"
                  placeholder="กรอกเลขพาสปอร์ต (ไม่บังคับ)"
              /></label>
              <label v-else
                >สัญชาติ<FilterDropdown
                  v-model="form.nationality"
                  :options="nationalityOptions"
                  default-label="เลือกสัญชาติ"
                  label="สัญชาติ"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
            </div>
          </section>
          <section class="registration-section">
            <h2>ข้อมูลติดต่อ</h2>
            <div class="form-grid">
              <label :class="{ 'invalid-field': emailErrorVisible }"
                >อีเมล <em v-if="!isForeign">*</em
                ><input
                  v-model.trim="form.email"
                  type="text"
                  inputmode="email"
                  placeholder="example@gmail.com"
                  @focus="emailFocused = true"
                  @blur="emailFocused = false"
                /><small
                  v-if="emailErrorVisible && !isForeign && !form.email"
                  class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small v-else-if="emailErrorVisible" class="required-error"
                  >รูปแบบอีเมลไม่ถูกต้อง</small
                ></label
              >
              <label :class="{ 'invalid-field': submitted && !phoneIsValid }"
                >เบอร์โทร <em>*</em
                ><input
                  v-model.trim="form.phone"
                  inputmode="numeric"
                  maxlength="10"
                  placeholder="xxx-xxx-xxxx"
                  @input="form.phone = digitsOnly(form.phone)"
                /><small v-if="submitted && !form.phone" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small v-else-if="submitted && !phoneIsValid" class="required-error"
                  >กรุณากรอกตัวเลข 10 หลัก</small
                ></label
              >
              <label
                class="wide"
                :class="{ 'invalid-field': submitted && !isForeign && !form.address }"
                >ที่อยู่ <em v-if="!isForeign">*</em
                ><textarea v-model.trim="form.address" rows="4" /><small
                  v-if="submitted && !isForeign && !form.address"
                  class="required-error"
                  >กรุณากรอกข้อมูล</small
                ></label
              >
            </div>
          </section>
          <section v-if="!isForeign" class="registration-section">
            <h2>ข้อมูลยืนยันตัวตน</h2>
            <div class="form-grid">
              <label :class="{ 'invalid-field': submitted && !form.identityDocumentType }"
                >ประเภทเอกสารยืนยันตัวตน <em>*</em
                ><FilterDropdown
                  v-model="form.identityDocumentType"
                  :options="identityDocumentOptions"
                  default-label="เลือกประเภทเอกสาร"
                  label="ประเภทเอกสารยืนยันตัวตน"
                  :disabled="form.nationality === 'ต่างชาติ'"
                  :clear-on-reselect="false"
                  hide-default-option
                /><small v-if="submitted && !form.identityDocumentType" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ></label
              >
              <label :class="{ 'invalid-field': identityErrorVisible }"
                >{{
                  form.identityDocumentType === 'PASSPORT' ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน'
                }}
                <em>*</em
                ><input
                  v-model.trim="form.identityNumber"
                  :disabled="!form.identityDocumentType"
                  inputmode="numeric"
                  :maxlength="form.identityDocumentType === 'NATIONAL_ID' ? 13 : 10"
                  :placeholder="
                    !form.identityDocumentType
                      ? 'กรุณาเลือกประเภทเอกสารก่อน'
                      : form.identityDocumentType === 'NATIONAL_ID'
                        ? 'กรอกเลขบัตรประชาชน 13 หลัก'
                        : 'กรอกเลขหนังสือเดินทาง 10 หลัก'
                  "
                  @focus="identityFocused = true"
                  @blur="identityFocused = false"
                  @input="form.identityNumber = digitsOnly(form.identityNumber)"
                /><small v-if="identityErrorVisible && !form.identityNumber" class="required-error"
                  >กรุณากรอกข้อมูล</small
                ><small v-else-if="identityErrorVisible" class="required-error"
                  >กรุณากรอกตัวเลข
                  {{ form.identityDocumentType === 'NATIONAL_ID' ? '13' : '10' }} หลัก</small
                ></label
              >
            </div>
          </section>
          <section class="registration-section">
            <h2>ข้อมูลเพิ่มเติม (ไม่บังคับ)</h2>
            <div class="form-grid">
              <label
                >สาขาที่ลงทะเบียน
                <FilterDropdown
                  v-model="form.branchCode"
                  :options="branchOptions"
                  default-label="เลือกสาขา"
                  label="สาขาที่ลงทะเบียน"
                  :disabled="!branches.length"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >กลุ่มลูกค้า<FilterDropdown
                  v-model="form.customerType"
                  :options="customerGroupOptions"
                  default-label="เลือกกลุ่มลูกค้า"
                  label="กลุ่มลูกค้า"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >ช่องทางแนะนำ<FilterDropdown
                  v-model="form.customerSource"
                  :options="referralChannelOptions"
                  default-label="เลือกช่องทางแนะนำ"
                  label="ช่องทางแนะนำ"
                  :clear-on-reselect="false"
                  hide-default-option
              /></label>
              <label
                >บริษัทประกัน<FilterDropdown
                  v-model="form.insuranceCompany"
                  :options="insuranceCompanyOptions"
                  default-label="ไม่มีประกัน"
                  label="บริษัทประกัน"
                  :clear-on-reselect="false"
              /></label>
            </div>
          </section>
          <div class="actions end">
            <button type="submit" class="primary" :disabled="submitting">
              {{ submitting ? 'กำลังบันทึก...' : 'บันทึก' }}
            </button>
          </div>
          <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        </form>
      </section>
      <section v-else class="success-card">
        <span class="success-icon">✓</span>
        <h1>ลงทะเบียนสำเร็จ</h1>
        <p>เลขประจำตัวผู้ป่วย (HN)</p>
        <strong>{{ registeredHn }}</strong>
        <div class="success-actions">
          <RouterLink class="secondary" to="/staff/patients">กลับไปรายชื่อผู้ป่วย</RouterLink
          ><RouterLink
            v-if="registeredPatientId"
            class="primary"
            :to="`/staff/patients/${registeredPatientId}`"
            >เปิดข้อมูลผู้ป่วย</RouterLink
          >
        </div>
      </section>
    </main>
    <Teleport to="body"
      ><div
        v-if="confirmationOpen"
        class="registration-modal-backdrop"
        @click.self="confirmationOpen = false"
      >
        <section
          class="registration-modal confirmation-modal"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="registration-confirmation-title"
        >
          <h2 id="registration-confirmation-title">ต้องการลงทะเบียนหรือไม่?</h2>
          <div class="confirmation-modal-spacer" aria-hidden="true"></div>
          <div class="registration-modal-actions">
            <button type="button" class="secondary" @click="confirmationOpen = false">ไม่ใช่</button
            ><button type="button" class="primary" :disabled="submitting" @click="submit">
              {{ submitting ? 'กำลังบันทึก...' : 'ใช่' }}
            </button>
          </div>
        </section>
      </div></Teleport
    >
    <Teleport to="body">
      <div v-if="duplicateOpen" class="registration-modal-backdrop">
        <section class="registration-modal duplicate-modal" role="alertdialog" aria-modal="true">
          <h2>พบข้อมูลผู้ป่วยซ้ำ</h2>
          <strong>ไม่สามารถบันทึกข้อมูลได้</strong>
          <p>มีชื่อนี้อยู่ในระบบแล้ว โปรดตรวจสอบข้อมูลอีกครั้ง</p>
          <div v-if="duplicatePatient" class="duplicate-patient">
            <b>{{ duplicatePatient.fullName }}</b>
            <span>HN {{ duplicatePatient.hn }}</span>
          </div>
          <div class="registration-modal-actions">
            <button type="button" class="secondary" @click="duplicateOpen = false">
              กลับไปแก้ไข
            </button>
            <button type="button" class="primary" @click="openExistingPatient">
              เปิดข้อมูลผู้ป่วยเดิม
            </button>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/therapist.css"></style>
