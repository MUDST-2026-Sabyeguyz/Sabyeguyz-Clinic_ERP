<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { registerPatient, type PatientRegistrationPayload } from '@/api/patients'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { savePatientProfile } from '../data/mockPatients'

const submitting = ref(false)
const errorMessage = ref('')
const registeredHn = ref('')
const configuredMasterData = JSON.parse(localStorage.getItem('clinicMasterData') || '[]') as Array<{
  category: string
  name: string
  status: string
}>
const activeMaster = (category: string, fallback: string[]) => {
  const values = configuredMasterData
    .filter((item) => item.category === category && item.status === 'Active')
    .map((item) => item.name)
  return values.length ? values : fallback
}
const customerGroups = activeMaster('Customer Group', ['ลูกค้าทั่วไป', 'ชาวต่างชาติ'])
const referralChannels = activeMaster('Referral Channel', ['ลูกค้าแนะนำ', 'Facebook', 'หน้าสาขา'])
const insuranceCompanies = activeMaster('Insurance Company', ['AIA'])
const form = reactive<PatientRegistrationPayload>({
  branchCode: 'RP',
  email: '',
  title: 'นาย',
  customerType: customerGroups[0]!,
  fullName: '',
  nickname: '',
  fullNameEnglish: '',
  birthDate: '',
  gender: 'ชาย',
  phone: '',
  identityDocumentType: 'NATIONAL_ID',
  identityNumber: '',
  address: '',
  bloodGroup: 'ไม่ทราบ',
  nationality: 'ไทย',
  customerSource: referralChannels[0]!,
  insuranceCompany: '',
  salesChannel: 'หน้าสาขา',
  paymentType: 'ชำระด้วยตนเอง',
  drugAllergy: false,
  foodAllergy: false,
  chronicDisease: '',
  emergencyContactName: '',
  emergencyContactRelationship: '',
  emergencyContactPhone: '',
  privacyConsent: false,
})
const canSubmit = computed(() =>
  Boolean(form.fullName && form.birthDate && form.phone && form.identityNumber && form.address),
)

async function submit() {
  if (!canSubmit.value) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบถ้วน'
    return
  }
  form.privacyConsent = true
  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await registerPatient(form)
    registeredHn.value = result.hn
    savePatientProfile({
      hn: result.hn,
      title: form.title,
      nickname: form.nickname,
      fullNameEnglish: form.fullNameEnglish,
      fullName: form.fullName,
      phone: form.phone,
      email: form.email,
      nationality: form.nationality,
      bloodGroup: form.bloodGroup,
      customerType: form.customerType,
      customerSource: form.customerSource,
      insuranceCompany: form.insuranceCompany || 'ไม่มีประกัน',
      birthDate: form.birthDate,
      gender: form.gender,
      identityDocumentType:
        form.identityDocumentType === 'PASSPORT' ? 'หนังสือเดินทาง' : 'บัตรประจำตัวประชาชน',
      identityNumber: form.identityNumber,
      address: form.address,
      branch: form.branchCode === 'RP' ? 'สาขาราชพฤกษ์' : form.branchCode,
      status: result.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน',
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาด'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="registration-page">
    <StaffHeader />
    <div class="back-row">
      <RouterLink to="/staff/patients" class="back-link">← กลับไปค้นหาผู้ป่วย</RouterLink>
    </div>
    <section v-if="!registeredHn" class="registration-shell">
      <div class="intro">
        <h1>ลงทะเบียนผู้ป่วยใหม่</h1>
        <p>เจ้าหน้าที่กรอกข้อมูลเพื่อสร้างประวัติและเลขประจำตัวผู้ป่วย (HN)</p>
      </div>
      <form class="form-card" @submit.prevent="submit">
        <div class="form-grid">
          <label
            >คำนำหน้า <em>*</em>
            <select v-model="form.title">
              <option>นาย</option>
              <option>นาง</option>
              <option>นางสาว</option>
              <option>ไม่ระบุ</option>
            </select>
          </label>
          <label
            >ชื่อ-นามสกุล ภาษาไทย <em>*</em
            ><input v-model.trim="form.fullName" required placeholder="ชื่อ-นามสกุล"
          /></label>
          <label
            >เพศ <em>*</em>
            <select v-model="form.gender">
              <option>ชาย</option>
              <option>หญิง</option>
              <option>ไม่ระบุ</option>
            </select>
          </label>
          <label
            >เบอร์โทร <em>*</em
            ><input v-model.trim="form.phone" type="tel" required placeholder="xxx-xxx-xxxx"
          /></label>
          <label>ชื่อเล่น<input v-model.trim="form.nickname" placeholder="ชื่อเล่น" /></label>
          <label
            >เลขบัตรประชาชน/หนังสือเดินทาง <em>*</em
            ><input v-model.trim="form.identityNumber" required placeholder="x-xxxx-xxxxx-xx-x"
          /></label>
          <label>วันเกิด<input v-model="form.birthDate" type="date" required /></label>
          <label
            >กรุ๊ปเลือด
            <select v-model="form.bloodGroup">
              <option>ไม่ทราบ</option>
              <option>A</option>
              <option>B</option>
              <option>AB</option>
              <option>O</option>
            </select>
          </label>
          <label
            >สัญชาติ
            <select v-model="form.nationality">
              <option>ไทย</option>
              <option>ต่างชาติ</option>
            </select>
          </label>
          <label
            >กลุ่มลูกค้า
            <select v-model="form.customerType">
              <option v-for="item in customerGroups" :key="item">{{ item }}</option>
            </select>
          </label>
          <label
            >ช่องทางแนะนำ
            <select v-model="form.customerSource">
              <option v-for="item in referralChannels" :key="item">{{ item }}</option>
            </select>
          </label>
          <label
            >บริษัทประกัน
            <select v-model="form.insuranceCompany">
              <option value="">ไม่มีประกัน</option>
              <option v-for="item in insuranceCompanies" :key="item">{{ item }}</option>
            </select>
          </label>
          <label class="wide"
            >ที่อยู่ <em>*</em><textarea v-model.trim="form.address" rows="4" required />
          </label>
        </div>
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
        ><RouterLink class="primary" :to="`/staff/patients/${registeredHn}`"
          >เปิดข้อมูลผู้ป่วย</RouterLink
        >
      </div>
    </section>
  </main>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/therapist.css"></style>
