<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const route = useRoute()
const reason = ref('')
</script>

<template>
  <div class="commission-manager-page"><StaffHeader /><main class="commission-manager-main reopen-main"><header><h1>เปิดรอบค่าตอบแทนอีกครั้ง</h1><p>สำหรับ Manager · งวดที่ปิดแล้วและยังไม่ Finalized</p></header><section class="reopen-card"><div class="reopen-head"><div><h2>ตรวจสอบก่อนเปิดรอบ</h2><p>กันยายน 2569 | พระราม 9 | Closed / Pending Payment</p></div><span>รอบปิด</span></div><p class="notice">ระบบจะกลับเป็น Open เพื่อแก้รายการราคา หรือข้อมูลจากต้นทาง จากนั้นคำนวณค่าตอบแทนใหม่ก่อนปิดรอบอีกครั้ง</p><label>เหตุผลในการเปิดรอบอีกครั้ง *<textarea v-model="reason" placeholder="พบ Visit V001 บันทึกการรักษาซ้ำ"></textarea><small>ระบบเก็บเหตุผล ผู้ดำเนินการ และเวลาในประวัติรอบ</small></label><footer><RouterLink class="commission-secondary" :to="{ name: 'commission-adjustment-closed', params: { id: route.params.id } }">ดูประวัติการเปิดรอบ</RouterLink><RouterLink class="commission-primary" :to="{ name: 'commission-adjustment-reopen-success', params: { id: route.params.id } }">ยืนยันเปิดรอบอีกครั้ง</RouterLink></footer></section></main></div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.reopen-main { padding-top: 34px; }.reopen-main h1 { margin: 0; font-size: clamp(1.7rem, 2.7vw, 2.15rem); }.reopen-main > header > p { margin: 8px 0 26px; color: #63746c; }.reopen-card { width: min(900px, 100%); margin: auto; padding: 24px; border: 1px solid #dce5e0; border-radius: 10px; background: #fff; }.reopen-head { display: flex; justify-content: space-between; gap: 18px; }.reopen-head h2 { margin: 0; font-size: 1.2rem; }.reopen-head p { margin: 8px 0 0; color: #718078; }.reopen-head span { height: fit-content; padding: 6px 12px; border-radius: 999px; background: #e8f1ec; color: #5a6e64; font-size: .8rem; }.notice { margin: 18px 0; padding: 14px; border-radius: 7px; background: #eaf3ef; color: #4d6659; }.reopen-card label { display: grid; gap: 8px; font-weight: 700; }.reopen-card textarea { min-height: 100px; padding: 14px; border: 1px solid #d6e0dc; border-radius: 7px; font: inherit; resize: vertical; }.reopen-card small { color: #718078; font-weight: 400; }.reopen-card footer { display: flex; justify-content: flex-end; gap: 12px; margin-top: 22px; }.reopen-card footer a { display: inline-flex; align-items: center; text-decoration: none; } @media (max-width: 560px) { .reopen-card footer { flex-direction: column-reverse; }.reopen-card footer > * { justify-content: center; } }
</style>
