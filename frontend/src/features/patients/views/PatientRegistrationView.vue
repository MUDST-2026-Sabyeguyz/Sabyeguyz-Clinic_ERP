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
const identityFocused = ref(false)
const errorMessage = ref('')
const registeredHn = ref('')
const registeredPatientId = ref<number | null>(null)
const customerGroups = ref<string[]>([])
const referralChannels = ref<string[]>([])
const insuranceCompanies = ref<string[]>([])
const branches = ref<Array<{ code: string; name: string }>>([])
const patientType = ref<'Thai' | 'Foreigner'>('Thai')
const form = reactive<PatientRegistrationForm>({
  branchCode: '', email: '', title: 'นาย', customerType: '', firstName: '', lastName: '', nickname: '', fullNameEnglish: '', birthDate: '', gender: 'ชาย', phone: '', identityDocumentType: '', identityNumber: '', address: '', bloodGroup: 'ไม่ทราบ', nationality: 'ไทย', customerSource: '', insuranceCompany: '', salesChannel: 'หน้าสาขา', paymentType: 'ชำระด้วยตนเอง', drugAllergy: false, foodAllergy: false, chronicDisease: '', privacyConsent: false,
})
const identityIsValid = computed(() => {
  if (!form.identityNumber.trim()) return true
  if (form.identityDocumentType === 'NATIONAL_ID') return /^\d{13}$/.test(form.identityNumber)
  return form.identityDocumentType === 'PASSPORT'
})
const identityErrorVisible = computed(() => submitted.value && !identityFocused.value && !identityIsValid.value)
const hasName = (value: string) => Boolean(value.trim())
const phoneIsValid = computed(() => Boolean(form.phone.trim()))
const canSubmit = computed(() => Boolean(form.branchCode && branches.value.length && hasName(form.firstName) && hasName(form.lastName) && phoneIsValid.value && identityIsValid.value))
watch(() => form.title, (title) => { if (title === 'นาย') form.gender = 'ชาย'; else if (title === 'นาง' || title === 'นางสาว') form.gender = 'หญิง' })
watch(() => form.gender, (gender) => { if (gender === 'ชาย') form.title = 'นาย'; else if (gender === 'หญิง') form.title = 'นางสาว' })
watch(() => form.identityDocumentType, () => { form.identityNumber = '' })
watch(patientType, (type) => { form.identityDocumentType = type === 'Foreigner' ? 'PASSPORT' : 'NATIONAL_ID' })
onMounted(async () => {
  try {
    const [items, branchList] = await Promise.all([masterDataApi.list(), branchApi.list({ accessible: true })])
    branches.value = branchList.filter((item) => item.active).map((item) => ({ code: item.code, name: item.name }))
    if (branches.value.length && !branches.value.some((item) => item.code === form.branchCode)) form.branchCode = branches.value[0]!.code
    const active = items.filter((item) => item.active)
    const valuesFor = (category: string) => active.filter((item) => item.category.trim().toLowerCase() === category.toLowerCase()).map((item) => item.name)
    customerGroups.value = valuesFor('Customer Group')
    referralChannels.value = valuesFor('Referral Channel')
    insuranceCompanies.value = valuesFor('Insurance Company')
    if (!form.customerType) form.customerType = customerGroups.value[0]!
    if (!form.customerSource) form.customerSource = referralChannels.value[0]!
  } catch { errorMessage.value = 'โหลดข้อมูลสาขาและข้อมูลพื้นฐานจาก backend ไม่สำเร็จ' }
})
async function submit() {
  submitted.value = true
  if (!canSubmit.value) { errorMessage.value = ''; return }
  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await registerPatient({
      branchCode: form.branchCode,
      title: form.title,
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      birthDate: form.birthDate || null,
      gender: form.gender,
      phone: form.phone,
      patientType: patientType.value,
      identityDocumentType: form.identityDocumentType,
      identityNumber: form.identityNumber.trim(),
      address: form.address.trim() || null,
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
        <section class="registration-section">
          <h2>ข้อมูลส่วนตัว</h2>
          <div class="form-grid">
            <label>คำนำหน้า <em>*</em><select v-model="form.title"><option>นาย</option><option>นาง</option><option>นางสาว</option></select></label>
            <label>เพศ <em>*</em><select v-model="form.gender"><option>ชาย</option><option>หญิง</option></select></label>
            <label :class="{ 'invalid-field': submitted && !hasName(form.firstName) }">ชื่อ <em>*</em><input v-model.trim="form.firstName" placeholder="ชื่อ" /><small v-if="submitted && !form.firstName" class="required-error">กรุณากรอกข้อมูล</small></label>
            <label :class="{ 'invalid-field': submitted && !hasName(form.lastName) }">นามสกุล <em>*</em><input v-model.trim="form.lastName" placeholder="นามสกุล" /><small v-if="submitted && !form.lastName" class="required-error">กรุณากรอกข้อมูล</small></label>
            <label>ชื่อเล่น<input v-model.trim="form.nickname" placeholder="ชื่อเล่น (ไม่บังคับ)" /></label>
            <label>วันเกิด<ThaiDatePicker v-model="form.birthDate" show-calendar-icon numeric-month hide-caret /></label>
            <label>กรุ๊ปเลือด<select v-model="form.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
            <label>ประเภทผู้ป่วย <em>*</em><select v-model="patientType" aria-label="ประเภทผู้ป่วย"><option value="Thai">Thai</option><option value="Foreigner">Foreigner</option></select></label>
            <label>สัญชาติ<input v-model.trim="form.nationality" aria-label="สัญชาติ" /></label>
          </div>
        </section>
        <section class="registration-section">
          <h2>ข้อมูลติดต่อ</h2>
          <div class="form-grid">
            <label :class="{ 'invalid-field': submitted && !phoneIsValid }">เบอร์โทร <em>*</em><input v-model.trim="form.phone" type="tel" placeholder="เบอร์โทร" /><small v-if="submitted && !phoneIsValid" class="required-error">กรุณากรอกข้อมูล</small></label>
            <label class="wide">ที่อยู่<textarea v-model.trim="form.address" rows="4" /></label>
          </div>
        </section>
        <section class="registration-section">
          <h2>เอกสารยืนยันตัวตน</h2>
          <div class="form-grid">
            <label>ประเภทเอกสารยืนยันตัวตน<select v-model="form.identityDocumentType"><option value="">ไม่มีเอกสาร</option><option value="NATIONAL_ID">บัตรประจำตัวประชาชน</option><option value="PASSPORT">หนังสือเดินทาง (Passport)</option></select></label>
            <label :class="{ 'invalid-field': identityErrorVisible }">{{ form.identityDocumentType === 'PASSPORT' ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }}<input v-model.trim="form.identityNumber" :disabled="!form.identityDocumentType" :inputmode="form.identityDocumentType === 'PASSPORT' ? 'text' : 'numeric'" :maxlength="form.identityDocumentType === 'NATIONAL_ID' ? 13 : 20" :placeholder="form.identityDocumentType === 'NATIONAL_ID' ? 'เลข 13 หลัก (ถ้ามี)' : 'หนังสือเดินทาง (ถ้ามี)'" @focus="identityFocused = true" @blur="identityFocused = false" /><small v-if="identityErrorVisible" class="required-error">เลขบัตรประชาชนต้องมี 13 หลัก</small></label>
          </div>
        </section>
        <section class="registration-section">
          <h2>ข้อมูลการลงทะเบียนและสิทธิ์การรักษา</h2>
          <div class="form-grid">
            <label>สาขาที่ลงทะเบียน <select v-model="form.branchCode" :disabled="!branches.length"><option v-for="branch in branches" :key="branch.code" :value="branch.code">{{ branch.name }}</option></select></label>
            <label>กลุ่มลูกค้า<select v-model="form.customerType"><option v-for="item in customerGroups" :key="item">{{ item }}</option></select></label>
            <label>ช่องทางแนะนำ<select v-model="form.customerSource"><option v-for="item in referralChannels" :key="item">{{ item }}</option></select></label>
            <label>บริษัทประกัน<select v-model="form.insuranceCompany"><option value="">ไม่มีประกัน</option><option v-for="item in insuranceCompanies" :key="item">{{ item }}</option></select></label>
          </div>
        </section>
        <div class="actions end"><button type="submit" class="primary" :disabled="submitting">{{ submitting ? 'กำลังบันทึก...' : 'บันทึก' }}</button></div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </form>
    </section>
    <section v-else class="success-card"><span class="success-icon">✓</span><h1>ลงทะเบียนสำเร็จ</h1><p>เลขประจำตัวผู้ป่วย (HN)</p><strong>{{ registeredHn }}</strong><div class="success-actions"><RouterLink class="secondary" to="/staff/patients">กลับไปรายชื่อผู้ป่วย</RouterLink><RouterLink v-if="registeredPatientId" class="primary" :to="`/staff/patients/${registeredPatientId}`">เปิดข้อมูลผู้ป่วย</RouterLink></div></section>
  </main>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/therapist.css"></style>
