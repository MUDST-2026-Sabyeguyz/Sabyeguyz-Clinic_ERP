<script setup lang="ts">
import { computed, ref } from 'vue'
const patientId = ref(''); const appointmentId = ref(''); const note = ref(''); const service = ref(''); const saved = ref(false); const error = ref('')
const canSave = computed(() => Boolean(patientId.value && appointmentId.value && service.value && note.value.trim()))
function submit() { error.value = ''; if (!canSave.value) { error.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบถ้วน'; return }; saved.value = true }
</script>
<template>
  <section class="patient-page"><header class="patient-page-header"><div><p class="eyebrow">TREATMENT SESSION</p><h1>บันทึกการรักษา</h1><p class="page-subtitle">เตรียมบันทึกรายละเอียดการรักษาของผู้ป่วย</p></div></header>
    <form class="form-card" novalidate @submit.prevent="submit"><h2>ข้อมูลการรักษา</h2><div class="form-grid"><label>Patient ID *<input v-model="patientId" type="number" min="1" required /></label><label>Appointment ID *<input v-model="appointmentId" type="number" min="1" required /></label><label class="wide">บริการที่ได้รับ *<select v-model="service" required><option disabled value="">เลือกบริการ</option><option>กายภาพบำบัดทั่วไป</option><option>ประเมินอาการ</option><option>ติดตามผลการรักษา</option></select></label><label class="wide">รายละเอียดการรักษา *<textarea v-model="note" rows="6" maxlength="2000" required placeholder="บันทึกอาการ การประเมิน และการรักษาที่ดำเนินการ" /></label></div><div class="actions end"><button class="primary" type="submit">บันทึกการรักษา</button></div><p v-if="saved" class="success" role="status">บันทึกข้อมูลแบบร่างแล้ว รอ Backend Treatment API เพื่อส่งข้อมูลจริง</p><p v-if="error" class="error" role="alert">{{ error }}</p></form>
  </section>
</template>
