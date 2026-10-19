<script setup lang="ts">
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main history-main">
      <header><h1>ประวัติการเปิดรอบ</h1><p>กันยายน 2569</p></header>
      <section class="history-card">
        <div class="history-table">
          <div class="table-header"><span>วันเวลา</span><span>ผู้ดำเนินการ</span><span>สถานะเดิม → หลัง</span><span>เหตุผล</span></div>
          <div class="table-row"><span>15 ต.ค. 2569<br>09:30</span><span>กัญญา<br>ผู้จัดการ Manager</span><span>Closed / Pending Payment<br>→ Open</span><span>พบ Visit V001<br>บันทึกการรักษาซ้ำ</span></div>
        </div>
        <p class="audit-note">ระบบบันทึกการเปลี่ยนสถานะรอบเพื่อความถูกต้อง</p>
        <RouterLink class="commission-secondary" :to="{ name: 'commission-adjustments' }">กลับหน้ารอบค่าตอบแทน</RouterLink>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.history-main { padding-top: 36px; }.history-main header h1 { margin: 0; font-size: clamp(1.5rem, 2.5vw, 2rem); }.history-main header p { margin: 9px 0 28px; color: #6f7d76; }.history-card { padding: 16px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.history-table { overflow-x: auto; }.table-header, .table-row { display: grid; min-width: 690px; grid-template-columns: 1.1fr 1.9fr 2.3fr 2.5fr; gap: 20px; }.table-header { padding: 14px 12px; background: #edf4f0; color: #52635b; font-weight: 700; font-size: .86rem; }.table-row { padding: 19px 12px; color: #364b43; line-height: 1.55; }.audit-note { margin: 2px 0 16px; color: #738078; font-size: .84rem; }.history-card a { display: inline-flex; text-decoration: none; }
</style>
