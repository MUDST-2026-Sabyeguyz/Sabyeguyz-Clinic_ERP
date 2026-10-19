<script setup lang="ts">
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const route = useRoute()
</script>

<template>
  <div class="commission-manager-page"><StaffHeader /><main class="commission-manager-main closed-main"><RouterLink class="back-link" :to="{ name: 'commission-adjustment-select-schedule', params: { id: route.params.id } }">← กลับรายการ Adjustment</RouterLink><header><h1>งวดปลายทาง Closed</h1><p>ยังไม่สามารถเพิ่มรายการในงวดนี้ได้</p></header><section class="closed-card"><b>ต้อง Reopen และคำนวณใหม่ก่อน</b></section><footer><RouterLink class="commission-secondary" :to="{ name: 'commission-adjustment-select-schedule', params: { id: route.params.id } }">เลือกงวดอื่น</RouterLink><RouterLink class="commission-primary" :to="{ name: 'commission-adjustment-reopen', params: { id: route.params.id } }">Reopen</RouterLink></footer></main></div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.closed-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 25px; color: #13766f; font-weight: 700; text-decoration: none; }.closed-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.closed-main header p { margin: 6px 0 20px; color: #63746c; font-size: .9rem; }.closed-card { padding: 18px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.closed-main footer { display: flex; justify-content: flex-end; gap: 12px; margin-top: 14px; }.closed-main footer a { display: inline-flex; align-items: center; text-decoration: none; } @media (max-width: 560px) { .closed-main footer { flex-direction: column-reverse; }.closed-main footer > * { justify-content: center; } }
</style>
