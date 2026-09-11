<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listBranches } from '@/api/branches'
import { listCourses } from '@/api/courses'
import { searchAppointments } from '@/api/appointments'
import { searchPatients } from '@/api/patients'
const loading = ref(true), error = ref('')
const counts = ref({ patients: 0, appointments: 0, branches: 0, courses: 0 })
async function load() { loading.value = true; error.value = ''; try { const today = new Date().toISOString().slice(0, 10); const [patients, appointments, branches, courses] = await Promise.all([searchPatients('', 0, 1), searchAppointments(today), listBranches(), listCourses()]); counts.value = { patients: patients.totalItems, appointments: appointments.totalItems, branches: branches.length, courses: courses.length } } catch (e) { error.value = e instanceof Error ? e.message : 'ไม่สามารถโหลด Dashboard ได้' } finally { loading.value = false } }
onMounted(load)
</script>
<template><section class="patient-page"><header class="patient-page-header"><div><p class="eyebrow">OVERVIEW</p><h1>Dashboard</h1><p class="page-subtitle">ภาพรวมข้อมูลจริงจากระบบ Clinic ERP</p></div><button class="secondary" :disabled="loading" @click="load">รีเฟรช</button></header><p v-if="error" class="error" role="alert">{{ error }}</p><div v-if="loading" class="state-card" role="status">กำลังโหลดข้อมูล...</div><div v-else class="dashboard-grid"><article v-for="card in [{label:'ผู้ป่วย',value:counts.patients},{label:'นัดหมายวันนี้',value:counts.appointments},{label:'สาขาที่ใช้งาน',value:counts.branches},{label:'Course ที่ใช้งาน',value:counts.courses}]" :key="card.label" class="metric-card"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></article></div><section v-if="!loading" class="profile-card dashboard-note"><h2>ทางลัด</h2><div class="actions"><RouterLink class="secondary" to="/patients">ค้นหาผู้ป่วย</RouterLink><RouterLink class="secondary" to="/appointments">จัดการนัดหมาย</RouterLink><RouterLink class="secondary" to="/treatment">บันทึกการรักษา</RouterLink></div></section></section></template>
