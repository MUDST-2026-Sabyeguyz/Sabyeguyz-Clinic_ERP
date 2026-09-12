<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { registerPatient } from '@/api/patients'
import { branchApi, masterDataApi } from '@/api/client'
import { type PatientRegistrationForm } from '../data/patientRegistration'
const router = useRouter(); const submitting = ref(false); const submitted = ref(false); const emailTouched = ref(false); const errorMessage = ref(''); const registeredHn = ref(''); const registeredPatientId = ref<number | null>(null)
const customerGroups = ref(['ลูกค้าทั่วไป', 'ชาวต่างชาติ']); const referralChannels = ref(['ลูกค้าแนะนำ', 'Facebook', 'หน้าสาขา']); const insuranceCompanies = ref(['AIA']); const branches = ref<Array<{ code: string; name: string }>>([])
const form = reactive<PatientRegistrationForm>({ branchCode: 'RP', email: '', title: 'นาย', customerType: customerGroups.value[0]!, firstName: '', lastName: '', nickname: '', fullNameEnglish: '', birthDate: '', gender: 'ชาย', phone: '', identityDocumentType: '', identityNumber: '', address: '', bloodGroup: 'ไม่ทราบ', nationality: 'ไทย', customerSource: referralChannels.value[0]!, insuranceCompany: '', salesChannel: 'หน้าสาขา', paymentType: 'ชำระด้วยตนเอง', drugAllergy: false, foodAllergy: false, chronicDisease: '', privacyConsent: false })
const identityIsValid = computed(() => form.identityDocumentType === 'NATIONAL_ID' ? /^\d{13}$/.test(form.identityNumber) : form.identityDocumentType === 'PASSPORT' && /^\d{6,20}$/.test(form.identityNumber)); const emailIsValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())); const phoneIsValid = computed(() => /^\d{10}$/.test(form.phone)); const thaiOnly = (v: string) => v.replace(/[^\u0E00-\u0E7F\s]/g, ''); const digitsOnly = (v: string) => v.replace(/\D/g, ''); const thaiNameIsValid = (v: string) => /^[\u0E00-\u0E7F\s]+$/.test(v.trim())
const canSubmit = computed(() => Boolean(form.branchCode && branches.value.length && thaiNameIsValid(form.firstName) && thaiNameIsValid(form.lastName) && thaiNameIsValid(form.nickname) && emailIsValid.value && form.birthDate && phoneIsValid.value && form.identityDocumentType && identityIsValid.value && form.address)); const maleTitles = ['นาย', 'เด็กชาย']; const femaleTitles = ['นาง', 'นางสาว', 'เด็กหญิง']
const invalidFieldNames = computed(() => [
  !form.branchCode || !branches.value.length ? 'สาขาที่ลงทะเบียน' : '',
  !emailIsValid.value ? 'อีเมล' : '',
  !thaiNameIsValid(form.firstName) ? 'ชื่อ' : '',
  !thaiNameIsValid(form.lastName) ? 'นามสกุล' : '',
  !form.title ? 'คำนำหน้า' : '',
  !form.gender ? 'เพศ' : '',
  !phoneIsValid.value ? 'เบอร์โทร' : '',
  !thaiNameIsValid(form.nickname) ? 'ชื่อเล่น' : '',
  !form.identityDocumentType ? 'ประเภทเอกสารยืนยันตัวตน' : '',
  form.identityDocumentType && !identityIsValid.value ? 'เลขบัตร/Passport' : '',
  !form.birthDate ? 'วันเกิด' : '',
  !form.address ? 'ที่อยู่' : '',
].filter(Boolean))
watch(() => form.title, (v) => { if (maleTitles.includes(v)) form.gender = 'ชาย'; if (femaleTitles.includes(v)) form.gender = 'หญิง' }); watch(() => form.gender, (v) => { if (v === 'ชาย' && !maleTitles.includes(form.title)) form.title = 'นาย'; if (v === 'หญิง' && !femaleTitles.includes(form.title)) form.title = 'นางสาว' }); watch(() => form.identityDocumentType, () => { form.identityNumber = '' })
onMounted(async () => { try { const [items, branchList] = await Promise.all([masterDataApi.list(), branchApi.list()]); branches.value = branchList.filter((i) => i.active).map((i) => ({ code: i.code, name: i.name })); if (branches.value.length && !branches.value.some((i) => i.code === form.branchCode)) form.branchCode = branches.value[0]!.code; const active = items.filter((i) => i.active); const valuesFor = (c: string, f: string[]) => { const v = active.filter((i) => i.category === c).map((i) => i.name); return v.length ? v : f }; customerGroups.value = valuesFor('Customer Group', customerGroups.value); referralChannels.value = valuesFor('Referral Channel', referralChannels.value); insuranceCompanies.value = valuesFor('Insurance Company', insuranceCompanies.value); if (!form.customerType) form.customerType = customerGroups.value[0]!; if (!form.customerSource) form.customerSource = referralChannels.value[0]! } catch { errorMessage.value = 'โหลดข้อมูลสาขาและข้อมูลพื้นฐานจาก backend ไม่สำเร็จ' } })
async function submit() { submitted.value = true; if (!canSubmit.value) { errorMessage.value = `ข้อมูลไม่ถูกต้อง: ${invalidFieldNames.value.join(', ')}`; return }; form.privacyConsent = true; submitting.value = true; errorMessage.value = ''; try { const fullName = `${form.firstName} ${form.lastName}`.trim(); const result = await registerPatient({ branchCode: form.branchCode, username: `patient-${Date.now()}`, password: `Temp${form.identityNumber.slice(-6)}!`, email: form.email, title: form.title, fullName, birthDate: form.birthDate, gender: form.gender, phone: form.phone, identityDocumentType: form.identityDocumentType, identityNumber: form.identityNumber, address: form.address, drugAllergy: form.drugAllergy, foodAllergy: form.foodAllergy, chronicDisease: form.chronicDisease, emergencyContactName: fullName, emergencyContactRelationship: 'ญาติ', emergencyContactPhone: form.phone, privacyConsent: true, nickname: form.nickname, bloodGroup: form.bloodGroup, nationality: form.nationality, customerType: form.customerType, customerSource: form.customerSource, insuranceCompany: form.insuranceCompany }); registeredHn.value = result.hn; registeredPatientId.value = result.id; localStorage.setItem('patientCreationHn', result.hn); await router.push('/staff/patients') } catch (e) { errorMessage.value = e instanceof Error ? e.message : 'เกิดข้อผิดพลาด' } finally { submitting.value = false } }
</script>
<template>
     <main class="registration-page">
          <StaffHeader />
          
          <section v-if="!registeredHn" class="registration-shell form-card  !bg-white/85 p-4">
               <div class="back-row !m-0">
               <RouterLink to="/staff/patients" class="back-link ">← กลับไปค้นหาผู้ป่วย</RouterLink>
          </div>
               <div class="intro">
                    <h1>ลงทะเบียนผู้ป่วยใหม่</h1>
                    <p>เจ้าหน้าที่กรอกข้อมูลเพื่อสร้างประวัติและเลขประจำตัวผู้ป่วย (HN)</p>
               </div>
               <form class="" novalidate @submit.prevent="submit">
                    <div class="grid grid-cols-1 gap-4 md:grid-cols-6">
                         <label class="md:col-span-3">สาขาที่ลงทะเบียน<select v-model="form.branchCode"
                                   :disabled="!branches.length">
                                   <option v-for="b in branches" :key="b.code" :value="b.code">{{ b.name }}</option>
                              </select></label>
                         <label class="md:col-span-3">กลุ่มลูกค้า<select v-model="form.customerType">
                                   <option v-for="i in customerGroups" :key="i">{{ i }}</option>
                              </select>
                         </label>
                         <label class="md:col-span-1 md:col-start-1">คำนำหน้า <em>*</em><select v-model="form.title">
                                   <option>นาย</option>
                                   <option>นาง</option>
                                   <option>นางสาว</option>

                                   <option>เด็กชาย</option>
                                   <option>เด็กหญิง</option>
                                   <option>ไม่ระบุ</option>
                              </select></label>
                         <label class="md:col-span-2"
                              :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.firstName) }">ชื่อ
                              <em>*</em><input v-model.trim="form.firstName"
                                   @input="form.firstName = thaiOnly(form.firstName)" /><small
                                   v-if="submitted && !form.firstName"
                                   class="required-error">กรุณากรอกข้อมูล</small></label>
                         <label class="md:col-span-2"
                              :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.lastName) }">นามสกุล
                              <em>*</em><input v-model.trim="form.lastName"
                                   @input="form.lastName = thaiOnly(form.lastName)" /><small
                                   v-if="submitted && !form.lastName"
                                   class="required-error">กรุณากรอกข้อมูล</small></label>
                         <label class="md:col-span-1"
                              :class="{ 'invalid-field': submitted && !thaiNameIsValid(form.nickname) }">ชื่อเล่น
                              <em>*</em><input v-model.trim="form.nickname"
                                   @input="form.nickname = thaiOnly(form.nickname)" /></label>
                         <label class="md:col-span-2">วันเกิด <em>*</em>
                              <ThaiDatePicker v-model="form.birthDate" />
                         </label><label class="md:col-span-2">กรุ๊ปเลือด<select v-model="form.bloodGroup">
                                   <option>ไม่ทราบ</option>
                                   <option>A</option>
                                   <option>B</option>
                                   <option>AB</option>
                                   <option>O</option>
                              </select></label>
                         <label class="md:col-span-2">เพศ <em>*</em><select v-model="form.gender">
                                   <option>ชาย</option>
                                   <option>หญิง</option>
                                   <option>ไม่ระบุ</option>
                              </select></label>
                         <label class="md:col-span-2" :class="{ 'invalid-field': submitted && !phoneIsValid }">เบอร์โทร
                              <em>*</em><input v-model.trim="form.phone" maxlength="10"
                                   @input="form.phone = digitsOnly(form.phone)" /></label>

                         <label class="md:col-span-4"
                              :class="{ 'invalid-field': (submitted || emailTouched) && !emailIsValid }">อีเมล
                              <em>*</em>
                              <input v-model.trim="form.email" class="w-full" type="text" inputmode="email"
                                   placeholder="example@email.com" @blur="emailTouched = true" /><small
                                   v-if="(submitted || emailTouched) && !form.email"
                                   class="required-error">กรุณากรอกข้อมูล</small><small
                                   v-else-if="(submitted || emailTouched) && !emailIsValid"
                                   class="required-error">รูปแบบอีเมลไม่ถูกต้อง ต้องใช้อีเมลที่ลงท้ายด้วย
                                   กรุณากรอกอีเมลให้ถูกต้อง เช่น example@email.com</small></label>
                         <label class="md:col-span-3 md:col-start-1">ประเภทเอกสารยืนยันตัวตน <em>*</em><select
                                   v-model="form.identityDocumentType">
                                   <option value="" disabled>เลือกประเภทเอกสาร</option>
                                   <option value="NATIONAL_ID">บัตรประจำตัวประชาชน</option>
                                   <option value="PASSPORT">หนังสือเดินทาง (Passport)</option>
                              </select></label>
                         <label class="md:col-span-3">เลขบัตร/Passport <em>*</em><input
                                   v-model.trim="form.identityNumber" :disabled="!form.identityDocumentType"
                                   @input="form.identityNumber = digitsOnly(form.identityNumber)" /></label>
                         <label class="md:col-span-2">สัญชาติ<select v-model="form.nationality">
                                   <option>ไทย</option>
                                   <option>ต่างชาติ</option>
                              </select></label>
                         <label class="md:col-span-2">ช่องทางแนะนำ<select v-model="form.customerSource">
                                   <option v-for="i in referralChannels" :key="i">{{ i }}</option>
                              </select></label><label class="md:col-span-2">บริษัทประกัน<select
                                   v-model="form.insuranceCompany">
                                   <option value="">ไม่มีประกัน</option>
                                   <option v-for="i in insuranceCompanies" :key="i">{{ i }}</option>
                              </select></label>
                         <label class="md:col-span-6" :class="{ 'invalid-field': submitted && !form.address }">ที่อยู่
                              <em>*</em><textarea v-model.trim="form.address" rows="4" /></label>
                    </div>
                    <div class="actions end "><button type="submit" class="primary" :disabled="submitting">{{ submitting
                         ? 'กำลังบันทึก...' : 'บันทึก' }}</button></div>
                    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
               </form>
          </section>
          <section v-else class="success-card"><span class="success-icon">✓</span>
               <h1>ลงทะเบียนสำเร็จ</h1>
               <p>เลขประจำตัวผู้ป่วย (HN)</p><strong>{{ registeredHn }}</strong>
               <div class="success-actions">
                    <RouterLink class="secondary" to="/staff/patients">กลับไปรายชื่อผู้ป่วย</RouterLink>
                    <RouterLink v-if="registeredPatientId" class="primary"
                         :to="`/staff/patients/${registeredPatientId}`">เปิดข้อมูลผู้ป่วย</RouterLink>
               </div>
          </section>
     </main>
</template>
<style scoped src="../styles/patient-registration.css"></style>
<style src="../styles/therapist.css"></style>
