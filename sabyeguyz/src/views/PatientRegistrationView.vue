<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import logoUrl from '@/assets/sabyeguyz-logo.png'
import { registerPatient, type PatientRegistrationPayload } from '@/api/patients'

const step = ref(1)
const submitting = ref(false)
const errorMessage = ref('')
const registeredHn = ref('')
const confirmPassword = ref('')
const form = reactive<PatientRegistrationPayload>({
  branchCode: 'RP', username: '', email: '', password: '', title: 'นาย', fullName: '',
  birthDate: '', gender: 'ชาย', phone: '', identityDocumentType: 'NATIONAL_ID',
  identityNumber: '', address: '', drugAllergy: false, foodAllergy: false,
  chronicDisease: '', emergencyContactName: '', emergencyContactRelationship: '',
  emergencyContactPhone: '', privacyConsent: false,
})
const steps = ['บัญชีผู้ใช้', 'ข้อมูลส่วนบุคคล', 'ประวัติและผู้ติดต่อฉุกเฉิน']
const canContinue = computed(() => {
  if (step.value === 1) return Boolean(form.username && form.email && form.password.length >= 8 && form.password === confirmPassword.value)
  if (step.value === 2) return Boolean(form.fullName && form.birthDate && form.phone && form.identityNumber && form.address)
  return Boolean(form.emergencyContactName && form.emergencyContactRelationship && form.emergencyContactPhone && form.privacyConsent)
})

function next() {
  errorMessage.value = ''
  if (!canContinue.value) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบถ้วน'
    return
  }
  step.value += 1
}

async function submit() {
  if (!canContinue.value) return next()
  submitting.value = true
  errorMessage.value = ''
  try {
    registeredHn.value = (await registerPatient(form)).hn
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'เกิดข้อผิดพลาด'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="registration-page">
    <header class="clinic-header"><img :src="logoUrl" alt="Sabyeguyz" /></header>
    <section v-if="!registeredHn" class="registration-shell">
      <div class="intro">
        <h1>ลงทะเบียนผู้ป่วยใหม่</h1>
        <p>กรอกข้อมูลให้ครบถ้วนเพื่อสร้างบัญชีและเลขประจำตัวผู้ป่วย (HN)</p>
      </div>
      <ol class="stepper">
        <li v-for="(label, index) in steps" :key="label" :class="{ active: index + 1 <= step }">
          <span>{{ index + 1 }}</span><small>{{ label }}</small>
        </li>
      </ol>
      <form class="form-card" @submit.prevent="submit">
        <template v-if="step === 1">
          <h2>สร้างบัญชีผู้ใช้</h2>
          <div class="form-grid">
            <label>ชื่อผู้ใช้ *<input v-model.trim="form.username" autocomplete="username" required /></label>
            <label>อีเมล *<input v-model.trim="form.email" type="email" autocomplete="email" required /></label>
            <label>รหัสผ่าน *<input v-model="form.password" type="password" minlength="8" autocomplete="new-password" required /></label>
            <label>ยืนยันรหัสผ่าน *<input v-model="confirmPassword" type="password" autocomplete="new-password" required /></label>
          </div>
          <div class="actions end"><button type="button" class="primary" @click="next">ถัดไป</button></div>
        </template>
        <template v-else-if="step === 2">
          <h2>ข้อมูลส่วนบุคคลและการติดต่อ</h2>
          <div class="form-grid">
            <label>คำนำหน้า<select v-model="form.title"><option>นาย</option><option>นาง</option><option>นางสาว</option><option>ไม่ระบุ</option></select></label>
            <label>ชื่อ-นามสกุล *<input v-model.trim="form.fullName" required /></label>
            <label>ชื่อผู้ใช้<input :value="form.username" disabled /></label>
            <label>วัน/เดือน/ปีเกิด *<input v-model="form.birthDate" type="date" required /></label>
            <label>เพศ<select v-model="form.gender"><option>ชาย</option><option>หญิง</option><option>ไม่ระบุ</option></select></label>
            <label>เบอร์โทรศัพท์ *<input v-model.trim="form.phone" type="tel" required /></label>
            <label>เอกสารยืนยันตัวตน *<select v-model="form.identityDocumentType"><option value="NATIONAL_ID">บัตรประจำตัวประชาชน</option><option value="PASSPORT">หนังสือเดินทาง</option></select></label>
            <label>เลขบัตรประจำตัวประชาชน *<input v-model.trim="form.identityNumber" required /></label>
            <label class="wide">ที่อยู่ *<textarea v-model.trim="form.address" rows="3" required /></label>
          </div>
          <div class="actions"><button type="button" class="secondary" @click="step--">ย้อนกลับ</button><button type="button" class="primary" @click="next">ถัดไป</button></div>
        </template>
        <template v-else>
          <h2>ประวัติและผู้ติดต่อฉุกเฉิน</h2>
          <fieldset><legend>ประวัติการแพ้</legend><label class="check"><input v-model="form.drugAllergy" type="checkbox" /> แพ้ยา</label><label class="check"><input v-model="form.foodAllergy" type="checkbox" /> แพ้อาหาร</label></fieldset>
          <div class="form-grid">
            <label class="wide">โรคประจำตัว<textarea v-model.trim="form.chronicDisease" rows="2" /></label>
            <label>ชื่อผู้ติดต่อกรณีฉุกเฉิน *<input v-model.trim="form.emergencyContactName" required /></label>
            <label>ความสัมพันธ์ *<input v-model.trim="form.emergencyContactRelationship" required /></label>
            <label>เบอร์โทร *<input v-model.trim="form.emergencyContactPhone" type="tel" required /></label>
          </div>
          <label class="consent"><input v-model="form.privacyConsent" type="checkbox" required /> ยินยอมให้คลินิกจัดเก็บข้อมูลเพื่อการรักษาตามนโยบายความเป็นส่วนตัว</label>
          <div class="actions"><button type="button" class="secondary" @click="step--">ย้อนกลับ</button><button type="submit" class="primary" :disabled="submitting">{{ submitting ? 'กำลังบันทึก...' : 'ยืนยันการลงทะเบียน' }}</button></div>
        </template>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </form>
    </section>
    <section v-else class="success-card">
      <span class="success-icon">✓</span><h1>ลงทะเบียนสำเร็จ</h1><p>เลขประจำตัวผู้ป่วย (HN)</p><strong>{{ registeredHn }}</strong>
    </section>
  </main>
</template>

<style scoped>
.registration-page { min-height: 100vh; background: var(--clinic-background); }
.clinic-header { height: 108px; display: flex; align-items: center; padding: 4px 28px; background: linear-gradient(90deg, #166361, #1e807c); }
.clinic-header img { width: 228px; height: 98px; object-fit: contain; }
.registration-shell { width: min(920px, calc(100% - 32px)); margin: 72px auto; }
.intro { text-align: center; }.intro h1 { margin: 0; font-size: 36px; }.intro p { margin: 4px 0 46px; color: #7c8581; font-size: 20px; font-weight: 600; }
.stepper { display: grid; grid-template-columns: repeat(3, 1fr); list-style: none; padding: 0; margin: 0 auto 48px; }
.stepper li { position: relative; display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--clinic-muted); }
.stepper li:not(:last-child)::after { content: ''; position: absolute; top: 22px; left: calc(50% + 24px); width: calc(100% - 48px); border-top: 1px solid #a9b4af; }
.stepper span { z-index: 1; display: grid; place-items: center; width: 46px; height: 46px; border: 1px solid #999; border-radius: 50%; background: #e8e8e8; }
.stepper .active { color: var(--clinic-primary); }.stepper .active span { color: white; border-color: var(--clinic-primary); background: var(--clinic-primary); }
.form-card, .success-card { padding: 56px; border: 1px solid var(--clinic-border); border-radius: 15px; background: white; box-shadow: 0 4px 3px rgb(0 0 0 / 9%); }
.form-card h2 { margin: 0 0 30px; font-size: 30px; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
label, legend { font-size: 15px; }.wide { grid-column: 1 / -1; }
input, select, textarea { width: 100%; margin-top: 6px; padding: 13px 16px; border: 1px solid #8f8f8f; border-radius: 7px; background: var(--clinic-input); box-shadow: 0 1px 3px rgb(119 119 119 / 25%); }
input:disabled { color: #777; border-color: #cdcdcd; background: #e8e8e8; } textarea { resize: vertical; }
fieldset { display: grid; gap: 8px; margin: 0 0 20px; padding: 0; border: 0; }.check { padding: 11px; border: 1px solid #8f8f8f; border-radius: 7px; background: var(--clinic-input); }.check input, .consent input { width: auto; margin: 0 8px 0 0; box-shadow: none; }
.consent { display: block; margin-top: 22px; }.actions { display: flex; justify-content: space-between; margin-top: 32px; }.actions.end { justify-content: flex-end; }
.primary, .secondary { min-height: 44px; padding: 8px 24px; border-radius: 11px; }.primary { color: white; border: 0; background: var(--clinic-primary); box-shadow: 0 3px 3px rgb(0 0 0 / 14%); }.secondary { color: #2c5662; border: 1px solid #2c5662; background: white; }.primary:disabled { opacity: .6; }
.error { margin: 20px 0 0; color: #b42318; }.success-card { width: min(560px, calc(100% - 32px)); margin: 120px auto; text-align: center; }.success-icon { display: inline-grid; place-items: center; width: 64px; height: 64px; color: white; border-radius: 50%; background: var(--clinic-primary); font-size: 34px; }.success-card strong { display: block; color: var(--clinic-primary); font-size: 32px; }
@media (max-width: 680px) { .registration-shell { margin-top: 36px; }.intro h1 { font-size: 28px; }.intro p { font-size: 16px; }.stepper small { max-width: 90px; text-align: center; }.form-card { padding: 28px 20px; }.form-grid { grid-template-columns: 1fr; }.wide { grid-column: auto; }.clinic-header { height: 84px; }.clinic-header img { width: 180px; height: 76px; } }
</style>
