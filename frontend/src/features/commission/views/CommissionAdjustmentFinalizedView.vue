<script setup lang="ts">
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const route = useRoute()
</script>

<template>
  <div class="commission-manager-page"><StaffHeader /><main class="commission-manager-main finalized-main"><RouterLink class="back-link" :to="{ name: 'commission-adjustment-select-schedule', params: { id: route.params.id } }">← กลับรายการ Adjustment</RouterLink><header><h1>งวดปลายทาง Finalized — แก้ไขไม่ได้</h1><p>คงสถานะและ Tier ของงวดเดิม</p></header><section class="finalized-card"><b>เลือกงวดถัดไปที่ยังดำเนินการได้</b><p>ไม่อนุญาตให้แก้ไข Adjustment หลังงวด Paid / Finalized และไม่คำนวณ Tier ของงวดใหม่</p></section><footer><RouterLink class="commission-secondary" :to="{ name: 'commission-adjustment-select-schedule', params: { id: route.params.id } }">เลือกงวด</RouterLink></footer></main></div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.finalized-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 25px; color: #13766f; font-weight: 700; text-decoration: none; }.finalized-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.finalized-main header p { margin: 6px 0 20px; color: #63746c; font-size: .9rem; }.finalized-card { padding: 18px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.finalized-card p { margin: 10px 0 0; color: #52635b; }.finalized-main footer { display: flex; justify-content: flex-end; margin-top: 14px; }.finalized-main footer a { display: inline-flex; align-items: center; text-decoration: none; }
</style>
