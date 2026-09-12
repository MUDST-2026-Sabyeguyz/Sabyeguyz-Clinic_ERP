<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { registerPatient } from '@/api/patients'
import { branchApi, masterDataApi } from '@/api/client'
import { type PatientRegistrationForm } from '../data/patientRegistration'

const submitting = ref(false)
const router = useRouter()
const submitted = ref(false)
const errorMessage = ref('')
const registeredHn = ref('')
const registeredPatientId = ref<number | null>(null)
const customerGroups = ref(['ลูกค้าทั่วไป', 'ชาวต่างชาติ'])
const referralChannels = ref(['ลูกค้าแนะนำ', 'Facebook', 'หน้าสาขา'])
const insuranceCompanies = ref(['AIA'])
const branches = ref<Array<{ code: string; name: string }>>([])
const form = reactive<PatientRegistrationForm>({
  branchCode: 'RP', email: '', title: 'นาย', customerType: customerGroups.value[0]!, firstName: '', lastName: '', nickname: '', fullNameEnglish: '', birthDate: '', gender: 'ชาย', phone: '', identityDocumentType: '', identityNumber: '', address: '', bloodGroup: 'ไม่ทราบ', nationality: 'ไทย', customerSource: referralChannels.value[0]!, insuranceCompany: '', salesChannel: 'หน้าสาขา', paymentType: 'ชำระด้วยตนเอง', drugAllergy: false, foodAllergy: false, chronicDisease: '', privacyConsent: false,
})
const identityIsValid = computed(() => {
  if (form.identityDocumentType === 'NATIONAL_ID') return /^\d{13}$/.test(form.identityNumber)
  if (form.identityDocumentType === 'PASSPORT') return /^\d{6,20}$/.test(form.identityNumber)
  return false
})
const emailIsValid = computed(() =>
  /^[a-z0-9](?:[a-z0-9._%+-]*[a-z0-9])?@gmail\.com$/i.test(form.email.trim()),
)
const thaiOnly = (value: string) => value.replace(/[^\u0E00-\u0E7F\s]/g, '')
const digitsOnly = (value: string) => value.replace(/\D/g, '')
const thaiNameIsValid = (value: string) => /^[\u0E00-\u0E7F\s]+$/.test(value.trim())
const phoneIsValid = computed(() => /^\d{10}$/.test(form.phone))
const canSubmit = computed(() => Boolean(form.branchCode && branches.value.length && thaiNameIsValid(form.firstName) && thaiNameIsValid(form.lastName) && thaiNameIsValid(form.nickname) && emailIsValid.value && form.birthDate && phoneIsValid.value && form.identityDocumentType && identityIsValid.value && form.address))
watch(() => form.title, (title) => { if (title === 'นาย') form.gender = 'ชาย'; if (title === 'นาง' || title === 'นางสาว') form.gender = 'หญิง' })
watch(() => form.identityDocumentType, () => { form.identityNumber = '' })
onMounted(async () => {
  try {
    const [items, branchList] = await Promise.all([masterDataApi.list(), branchApi.list()])
    branches.value = branchList.filter((item) => item.active).map((item) => ({ code: item.code, name: item.name }))
    if (branches.value.length && !branches.value.some((item) => item.code === form.branchCode)) form.branchCode = branches.value[0]!.code
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
  } catch { errorMessage.value = 'โหลดข้อมูลสาขาและข้อมูลพื้นฐานจาก backend ไม่สำเร็จ' }
})
async function submit() {
  submitted.value = true
  if (!canSubmit.value) { errorMessage.value = ''; return }
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
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาด' } finally { submitting.value = false }
}
</script>

<template>
  <main class="registration-page">
    <StaffHeader />
    <div class="back-row"><RouterLink to="/staff/patients" class="back-link">← กลับไปค้นหาผู้ป่วย</RouterLink></div>
    <section v-if="!registeredHn" class="registration-shell">
      <div class="intro"><h1>ลงทะเบียนผู้ป่วยใหม่</h1><p>เจ้าหน้าที่กรอกข้อมูลเพื่อสร้างประวัติและเลขประจำตัวผู้ป่วย (HN)</p></div>
      <form class="form-card" novalidate @submit.prevent="submit">
        <div class="form-grid">
          <label>สาขาที่ลงทะเบียน <select v-model="form.branchCode" :disabled="!branches.length"><option v-for="branch in branches" :key="branch.code" :value="branch.code">{{ branch.name }}</option></select></label>
          <label :class="{ 'invalid-field': submitted && !emailIsValid }">อีเมล <em>*</em><input v-model.trim="form.email" type="text" inputmode="email" placeholder="example@gmail.com" /><small v-if="submitted && !form.email" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !emailIsValid" class="required-error">รูปแบบอีเมลไม่ถูกต้อง ต้องใช้อีเมลที่ลงท้ายด้วย @gmail.com</small></label>
          <label>คำนำหน้า <em>*</em><select v-model="form.title"><option>นาย</option><option>นาง</option><option>นางสาว</option><option>ไม่ระบุ</option></select></label>
          <label :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.firstName) }">ชื่อ <em>*</em><input v-model.trim="form.firstName" placeholder="ชื่อ" @input="form.firstName = thaiOnly(form.firstName)" /><small v-if="submitted && !form.firstName" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !thaiNameIsValid(form.firstName)" class="required-error">กรุณากรอกเป็นภาษาไทย</small></label>
          <label :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.lastName) }">นามสกุล <em>*</em><input v-model.trim="form.lastName" placeholder="นามสกุล" @input="form.lastName = thaiOnly(form.lastName)" /><small v-if="submitted && !form.lastName" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !thaiNameIsValid(form.lastName)" class="required-error">กรุณากรอกเป็นภาษาไทย</small></label>
          <label>เพศ <em>*</em><select v-model="form.gender"><option>ชาย</option><option>หญิง</option><option>ไม่ระบุ</option></select></label>
          <label :class="{ 'invalid-field': submitted && !phoneIsValid }">เบอร์โทร <em>*</em><input v-model.trim="form.phone" inputmode="numeric" maxlength="10" placeholder="xxx-xxx-xxxx" @input="form.phone = digitsOnly(form.phone)" /><small v-if="submitted && !form.phone" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !phoneIsValid" class="required-error">กรุณากรอกตัวเลข 10 หลัก</small></label>
          <label :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.nickname) }">ชื่อเล่น <em>*</em><input v-model.trim="form.nickname" placeholder="ชื่อเล่น" @input="form.nickname = thaiOnly(form.nickname)" /><small v-if="submitted && !form.nickname" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !thaiNameIsValid(form.nickname)" class="required-error">กรุณากรอกเป็นภาษาไทย</small></label>
          <label :class="{ 'invalid-field': submitted && !form.identityDocumentType }">ประเภทเอกสารยืนยันตัวตน <em>*</em><select v-model="form.identityDocumentType"><option value="" disabled>เลือกประเภทเอกสาร</option><option value="NATIONAL_ID">บัตรประจำตัวประชาชน</option><option value="PASSPORT">หนังสือเดินทาง (Passport)</option></select><small v-if="submitted && !form.identityDocumentType" class="required-error">กรุณากรอกข้อมูล</small></label>
          <label :class="{ 'invalid-field': submitted && !identityIsValid }">{{ form.identityDocumentType === 'PASSPORT' ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }} <em>*</em><input v-model.trim="form.identityNumber" :disabled="!form.identityDocumentType" inputmode="numeric" :maxlength="form.identityDocumentType === 'NATIONAL_ID' ? 13 : 20" :placeholder="!form.identityDocumentType ? 'กรุณาเลือกประเภทเอกสารก่อน' : form.identityDocumentType === 'NATIONAL_ID' ? 'กรอกเลขบัตรประชาชน 13 หลัก' : 'กรอกเลขหนังสือเดินทางเป็นตัวเลข'" @input="form.identityNumber = digitsOnly(form.identityNumber)" /><small v-if="submitted && !form.identityNumber" class="required-error">กรุณากรอกข้อมูล</small><small v-else-if="submitted && !identityIsValid" class="required-error">{{ form.identityDocumentType === 'NATIONAL_ID' ? 'กรุณากรอกตัวเลข 13 หลัก' : 'กรุณากรอกตัวเลข 6–20 หลัก' }}</small></label>
          <label :class="{ 'invalid-field': submitted && !form.birthDate }">วันเกิด <em>*</em><ThaiDatePicker v-model="form.birthDate" /><small v-if="submitted && !form.birthDate" class="required-error">กรุณากรอกข้อมูล</small></label>
          <label>กรุ๊ปเลือด<select v-model="form.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
          <label>สัญชาติ<select v-model="form.nationality"><option>ไทย</option><option>ต่างชาติ</option></select></label>
          <label>กลุ่มลูกค้า<select v-model="form.customerType"><option v-for="item in customerGroups" :key="item">{{ item }}</option></select></label>
          <label>ช่องทางแนะนำ<select v-model="form.customerSource"><option v-for="item in referralChannels" :key="item">{{ item }}</option></select></label>
          <label>บริษัทประกัน<select v-model="form.insuranceCompany"><option value="">ไม่มีประกัน</option><option v-for="item in insuranceCompanies" :key="item">{{ item }}</option></select></label>
          <label class="wide" :class="{ 'invalid-field': submitted && !form.address }">ที่อยู่ <em>*</em><textarea v-model.trim="form.address" rows="4" /><small v-if="submitted && !form.address" class="required-error">กรุณากรอกข้อมูล</small></label>
        </div>
        <div class="actions end"><button type="submit" class="primary" :disabled="submitting">{{ submitting ? 'กำลังบันทึก...' : 'บันทึก' }}</button></div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </form>
    </section>
    <section v-else class="success-card"><span class="success-icon">✓</span><h1>ลงทะเบียนสำเร็จ</h1><p>เลขประจำตัวผู้ป่วย (HN)</p><strong>{{ registeredHn }}</strong><div class="success-actions"><RouterLink class="secondary" to="/staff/patients">กลับไปรายชื่อผู้ป่วย</RouterLink><RouterLink v-if="registeredPatientId" class="primary" :to="`/staff/patients/${registeredPatientId}`">เปิดข้อมูลผู้ป่วย</RouterLink></div></section>
  </main>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/therapist.css"></style>
