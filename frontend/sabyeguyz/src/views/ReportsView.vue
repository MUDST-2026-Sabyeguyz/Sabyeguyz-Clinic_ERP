<script setup lang="ts">
import { ref } from 'vue'
import { searchAppointments } from '@/api/appointments'
const date = ref(new Date().toISOString().slice(0, 10)), loading = ref(false), error = ref(''), result = ref<{totalItems:number,totalPages:number}|null>(null)
async function load() { loading.value = true; error.value = ''; try { result.value = await searchAppointments(date.value) } catch (e) { error.value = e instanceof Error ? e.message : 'ไม่สามารถโหลดรายงานได้' } finally { loading.value = false } }
load()
</script>
<template><section class="patient-page"><header class="patient-page-header"><div><p class="eyebrow">REPORTS</p><h1>รายงานการดำเนินงาน</h1><p class="page-subtitle">ข้อมูลรายงานที่คำนวณจากรายการนัดหมายจริง</p></div></header><form class="form-card report-filter" @submit.prevent="load"><label>วันที่<input v-model="date" type="date" required></label><button class="primary" :disabled="loading">{{ loading ? 'กำลังโหลด…' : 'ดูรายงาน' }}</button></form><p v-if="error" class="error" role="alert">{{ error }}</p><div v-if="result" class="dashboard-grid"><article class="metric-card"><span>นัดหมายในวันที่เลือก</span><strong>{{ result.totalItems }}</strong></article><article class="metric-card"><span>จำนวนหน้า</span><strong>{{ result.totalPages }}</strong></article></div><section class="profile-card report-note"><h2>ขอบเขตข้อมูล</h2><p>รายงานรายได้ ยอด Course โอนสิทธิ์ ยอดขายผู้ใช้ และค่าคอมมิชชันจะเปิดเมื่อ transactional schema และกติกาจาก PM ได้รับการยืนยัน</p></section></section></template>
