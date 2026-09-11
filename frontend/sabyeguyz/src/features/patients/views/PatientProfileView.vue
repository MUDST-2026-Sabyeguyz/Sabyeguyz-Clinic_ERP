<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patientApi, type PatientProfile } from '@/api/client'

const route = useRoute()
const patient = ref<PatientProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const editing = ref(false)
const saved = ref('')
async function save() {
  if (!patient.value) return
  try {
    patient.value = await patientApi.update(patient.value.id, patient.value)
    editing.value = false; saved.value = 'บันทึกข้อมูลแล้ว'; errorMessage.value = ''
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ' }
}
onMounted(async () => {
  try { patient.value = await patientApi.get(Number(route.params.id)) }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลผู้ป่วยไม่สำเร็จ' }
  finally { loading.value = false }
})
</script>

<template>
  <div class="patient-workspace patient-profile-page">
    <StaffHeader />
    <main>
      <RouterLink class="back-link" to="/staff/patients">← กลับไปค้นหาผู้ป่วย</RouterLink>
      <p v-if="loading">กำลังโหลดข้อมูลผู้ป่วย...</p>
      <p v-else-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <template v-if="patient">
      <section class="profile-heading">
        <div class="avatar">{{ patient.fullName.slice(0, 1) }}</div>
        <div>
          <small>PATIENT PROFILE</small>
          <h1>{{ patient.title }}{{ patient.fullName }}</h1>
          <p>HN {{ patient.hn }}</p>
        </div>
        <button class="primary" @click="editing = !editing">{{ editing ? 'ยกเลิก' : 'แก้ไขข้อมูล' }}</button>
      </section>
      <section v-if="editing" class="profile-sheet">
        <h2>แก้ไขข้อมูลผู้ป่วย</h2>
        <div class="details four-columns">
          <label>คำนำหน้า<input v-model="patient.title" /></label><label>ชื่อ–นามสกุล<input v-model="patient.fullName" required /></label>
          <label>วันเกิด<input v-model="patient.birthDate" type="date" required /></label><label>เพศ<input v-model="patient.gender" /></label>
          <label>โทรศัพท์<input v-model="patient.phone" required /></label><label>ประเภทเอกสาร<input v-model="patient.identityDocumentType" required /></label>
          <label>เลขเอกสาร<input v-model="patient.identityNumber" required /></label><label>ผู้ติดต่อฉุกเฉิน<input v-model="patient.emergencyContactName" required /></label>
          <label>ความสัมพันธ์<input v-model="patient.emergencyContactRelationship" required /></label><label>โทรฉุกเฉิน<input v-model="patient.emergencyContactPhone" required /></label>
          <label class="span-all">ที่อยู่<input v-model="patient.address" required /></label><label class="span-all">โรคประจำตัว<input v-model="patient.chronicDisease" /></label>
          <label><input v-model="patient.drugAllergy" type="checkbox" /> แพ้ยา</label><label><input v-model="patient.foodAllergy" type="checkbox" /> แพ้อาหาร</label>
          <label><input v-model="patient.privacyConsent" type="checkbox" /> ยินยอมข้อมูลส่วนบุคคล</label><label><input v-model="patient.active" type="checkbox" /> ใช้งาน</label>
        </div>
        <button class="primary" @click="save">บันทึก</button>
      </section>
      <section class="profile-sheet">
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
            <dd>{{ patient.birthDate }}</dd>
          </div>
          <div>
            <dt>กรุ๊ปเลือด</dt>
            <dd>{{ patient.bloodGroup }}</dd>
          </div>
          <div>
            <dt>เลขเอกสาร</dt>
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
      <p class="edit-success">{{ saved || 'ข้อมูลนี้อ่านจาก backend โดยตรง' }}</p>
      </template>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
