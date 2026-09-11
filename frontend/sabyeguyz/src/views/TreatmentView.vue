<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createTreatment, listTreatmentServices, type ServiceOption } from '@/api/treatments'
import { searchPatients } from '@/api/patients'
const patientId = ref(''); const appointmentId = ref(''); const note = ref(''); const serviceId = ref(''); const services = ref<ServiceOption[]>([]); const saved = ref(false); const error = ref('')
const canSave = computed(() => Boolean(patientId.value && serviceId.value && note.value.trim()))
async function submit() { error.value = ''; saved.value = false; if (!canSave.value) { error.value = 'กรุณากรอกข้อมูลที่จำเป็นให้ครบถ้วน'; return }; try { const raw=patientId.value.trim(); let resolvedId=Number(raw); if (raw.length>6) { const result=await searchPatients(raw); const match=result.items.find(p=>p.hn===raw); if (!match) throw new Error(`ไม่พบผู้ป่วย HN ${raw}`); resolvedId=match.id }; await createTreatment({ patientId: resolvedId, appointmentId: appointmentId.value ? Number(appointmentId.value) : undefined, serviceId: Number(serviceId.value), note: note.value }); saved.value = true } catch (cause) { error.value = cause instanceof Error ? cause.message : 'ไม่สามารถบันทึกการรักษาได้' } }
async function loadServices() { try { services.value = await listTreatmentServices(); serviceId.value = String(services.value[0]?.id ?? '') } catch (cause) { error.value = cause instanceof Error ? cause.message : 'โหลดบริการไม่สำเร็จ' } }
onMounted(loadServices)
</script>
<template>
  <section class="patient-page"><header class="patient-page-header"><div><p class="eyebrow">TREATMENT SESSION</p><h1>บันทึกการรักษา</h1><p class="page-subtitle">เตรียมบันทึกรายละเอียดการรักษาของผู้ป่วย</p></div></header>
    <form class="form-card" novalidate @submit.prevent="submit"><h2>ข้อมูลการรักษา</h2><div class="form-grid"><label>Patient ID / HN *<input v-model="patientId" required placeholder="เช่น 1 หรือ HN" /></label><label>Appointment ID<input v-model="appointmentId" type="number" min="1" /></label><label class="wide">บริการที่ได้รับ *<select v-model="serviceId" required><option disabled value="">เลือกบริการ</option><option v-for="item in services" :key="item.id" :value="String(item.id)">{{ item.name }} · {{ item.price }} บาท</option></select></label><label class="wide">รายละเอียดการรักษา *<textarea v-model="note" rows="6" maxlength="2000" required placeholder="บันทึกอาการ การประเมิน และการรักษาที่ดำเนินการ" /></label></div><div class="actions end"><button class="primary" type="submit">บันทึกการรักษา</button></div><p v-if="saved" class="success" role="status">บันทึกข้อมูลการรักษาแล้ว</p><p v-if="error" class="error" role="alert">{{ error }}</p></form>
  </section>
</template>
