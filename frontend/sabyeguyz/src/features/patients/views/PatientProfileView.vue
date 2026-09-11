<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { findPatient, patients, savePatientProfile } from '../data/mockPatients'
import { formatThaiDate } from '../data/patientRegistration'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

const route = useRoute()
const patient = computed(() => findPatient(String(route.params.hn)) ?? patients[0]!)
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
const draft = reactive({ ...patient.value, ...splitFullName(patient.value.fullName), birthDateInput: toDateInput(patient.value.birthDate) })
const isPassportDocument = computed(() => draft.identityDocumentType === 'หนังสือเดินทาง')
const digitsOnly = (value: string) => value.replace(/\D/g, '')
function assignDraft(profile = patient.value) {
  Object.assign(draft, profile, splitFullName(profile.fullName), { birthDateInput: toDateInput(profile.birthDate) })
}
function openEdit() {
  assignDraft()
  editing.value = true
}
function saveEdit() {
  const { firstName, lastName, birthDateInput, ...profile } = draft
  profile.fullName = `${firstName} ${lastName}`.trim()
  profile.birthDate = formatThaiDate(birthDateInput)
  Object.assign(patient.value, profile)
  savePatientProfile({ ...patient.value })
  editing.value = false
  saved.value = true
  window.setTimeout(() => { saved.value = false }, 3000)
}
</script>

<template>
  <div class="patient-workspace patient-profile-page">
    <StaffHeader />
    <main>
      <RouterLink class="back-link" to="/staff/patients">← กลับไปค้นหาผู้ป่วย</RouterLink>
      <section class="profile-heading">
        <div class="avatar">{{ patient.fullName.slice(0, 1) }}</div>
        <div>
          <small>PATIENT PROFILE</small>
          <h1>{{ patient.title }}{{ patient.fullName }}</h1>
          <p>HN {{ patient.hn }}</p>
        </div>
        <button class="primary" @click="openEdit">แก้ไขข้อมูล</button>
      </section>

      <form v-if="editing" class="patient-edit" @submit.prevent="saveEdit">
        <header>
          <h2>แก้ไขข้อมูลผู้ป่วย</h2>
        </header>
        <div class="edit-fields">
          <h3>ข้อมูลส่วนตัว</h3>
          <label>คำนำหน้า<select v-model="draft.title"><option>นาย</option><option>นาง</option><option>นางสาว</option><option>ไม่ระบุ</option></select></label>
          <label>อีเมล <em>*</em><input v-model="draft.email" type="text" inputmode="email" required /></label>
          <label>ชื่อ <em>*</em><input v-model="draft.firstName" required /></label>
          <label>นามสกุล <em>*</em><input v-model="draft.lastName" required /></label>
          <label>ชื่อเล่น <em>*</em><input v-model="draft.nickname" required /></label>
          <label>เพศ<select v-model="draft.gender"><option>ชาย</option><option>หญิง</option><option>ไม่ระบุ</option></select></label>
          <label>วันเกิด <em>*</em><ThaiDatePicker v-model="draft.birthDateInput" /></label>
          <label>เบอร์โทรศัพท์ <em>*</em><input v-model="draft.phone" type="tel" required /></label>
          <label>กรุ๊ปเลือด<select v-model="draft.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
          <label>สัญชาติ<select v-model="draft.nationality"><option>ไทย</option><option>ต่างชาติ</option></select></label>
          <label>ประเภทเอกสารยืนยันตัวตน <em>*</em><select v-model="draft.identityDocumentType" @change="draft.identityNumber = ''"><option>บัตรประจำตัวประชาชน</option><option>หนังสือเดินทาง</option></select></label>
          <label>{{ isPassportDocument ? 'เลขหนังสือเดินทาง' : 'เลขบัตรประชาชน' }} <em>*</em><input v-model.trim="draft.identityNumber" inputmode="numeric" :maxlength="isPassportDocument ? 20 : 13" :placeholder="isPassportDocument ? 'กรอกเลขหนังสือเดินทางเป็นตัวเลข' : 'กรอกเลขบัตรประชาชน 13 หลัก'" required @input="draft.identityNumber = digitsOnly(draft.identityNumber)" /></label>
          <label>กลุ่มลูกค้า<select v-model="draft.customerType"><option>ลูกค้าทั่วไป</option><option>ชาวต่างชาติ</option></select></label>
          <label>ช่องทางแนะนำ<select v-model="draft.customerSource"><option>ลูกค้าแนะนำ</option><option>Facebook</option><option>หน้าสาขา</option></select></label>
          <label>บริษัทประกัน<select v-model="draft.insuranceCompany"><option>ไม่มีประกัน</option><option>AIA</option></select></label>
          <label class="wide">ที่อยู่ <em>*</em><textarea v-model="draft.address" rows="3" required></textarea></label>

        </div>
        <footer>
          <button type="button" class="outline" @click="editing = false">ยกเลิก</button
          ><button class="primary">บันทึกข้อมูล</button>
        </footer>
      </form>
      <section v-else class="profile-sheet">
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
