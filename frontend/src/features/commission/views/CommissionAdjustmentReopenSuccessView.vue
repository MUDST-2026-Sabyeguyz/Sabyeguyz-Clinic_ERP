<script setup lang="ts">
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main reopen-success-main">
      <section class="success-card">
        <div class="success-icon">✓</div>
        <h1>เปิดรอบกันยายน 2569 อีกครั้งสำเร็จ</h1>
        <p class="success-subtitle">สถานะ: Open · สาขาพระราม 9</p>

        <div class="summary-box">
          <div><span>สถานะ</span><strong>เปิดรอบแล้ว</strong><span>สาขา</span><strong>พระราม 9</strong></div>
          <div><span>รอบ</span><strong>กันยายน 2569</strong><span>เหตุผล</span><strong>พบ Visit V001 บันทึกการรักษาซ้ำ</strong></div>
        </div>

        <RouterLink class="commission-primary history-link" :to="{ name: 'commission-adjustment-reopen-history', params: { id: route.params.id } }">ดูประวัติการเปิดรอบ</RouterLink>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.reopen-success-main { display: grid; min-height: calc(100vh - 94px); place-items: center; padding-block: 48px; }
.success-card { width: min(700px, 100%); padding: 22px; border: 1px solid #dce5e0; border-radius: 10px; background: #fff; text-align: center; }
.success-icon { display: grid; width: 44px; height: 44px; margin: 0 auto 16px; place-items: center; border-radius: 50%; background: #d8fae4; color: #148c72; font-size: 1.55rem; font-weight: 800; }
.success-card h1 { margin: 0; color: #263c35; font-size: 1.45rem; }.success-subtitle { margin: 9px 0 18px; color: #75837d; }
.summary-box { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; padding: 16px 20px; border-radius: 7px; background: #edf1ef; text-align: left; }.summary-box div { display: grid; gap: 5px; }.summary-box span { color: #7b8982; font-size: .8rem; }.summary-box strong { color: #344a42; font-size: .92rem; }.history-link { display: flex; justify-content: center; width: 100%; margin-top: 18px; text-decoration: none; }
@media (max-width: 560px) { .summary-box { grid-template-columns: 1fr; gap: 14px; } }
</style>
