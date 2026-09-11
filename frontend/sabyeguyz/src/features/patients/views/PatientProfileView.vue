<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { findPatient, patients, savePatientProfile } from '../data/mockPatients'

const route = useRoute()
const patient = computed(() => findPatient(String(route.params.hn)) ?? patients[0]!)
const editing = ref(false)
const saved = ref(false)
const draft = reactive({ ...patient.value })
function openEdit() {
  Object.assign(draft, patient.value)
  editing.value = true
}
function saveEdit() {
  Object.assign(patient.value, draft)
  savePatientProfile({ ...patient.value })
  editing.value = false
  saved.value = true
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
          <button type="button" aria-label="ปิดแบบฟอร์ม" @click="editing = false">×</button>
        </header>
        <div class="edit-fields">
          <h3>ข้อมูลส่วนตัว</h3>
          <label>คำนำหน้า<select v-model="draft.title"><option>นาย</option><option>นาง</option><option>นางสาว</option><option>ไม่ระบุ</option></select></label>
          <label>ชื่อ–นามสกุล ภาษาไทย <em>*</em><input v-model="draft.fullName" required /></label>
          <label>ชื่อเล่น<input v-model="draft.nickname" /></label>
          <label>เพศ<select v-model="draft.gender"><option>ชาย</option><option>หญิง</option><option>ไม่ระบุ</option></select></label>
          <label>วันเกิด<input v-model="draft.birthDate" /></label>
          <label>เบอร์โทรศัพท์ <em>*</em><input v-model="draft.phone" type="tel" required /></label>
          <label>กรุ๊ปเลือด<select v-model="draft.bloodGroup"><option>ไม่ทราบ</option><option>A</option><option>B</option><option>AB</option><option>O</option></select></label>
          <label>สัญชาติ<select v-model="draft.nationality"><option>ไทย</option><option>ต่างชาติ</option></select></label>
          <label>เลขบัตรประชาชน/หนังสือเดินทาง <em>*</em><input v-model="draft.identityNumber" required /></label>
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
      <p v-if="saved && !editing" class="edit-success">บันทึกข้อมูลผู้ป่วยแล้ว</p>
    </main>
  </div>
</template>

<style scoped src="../styles/patients.css"></style>
<style src="../styles/therapist.css"></style>
