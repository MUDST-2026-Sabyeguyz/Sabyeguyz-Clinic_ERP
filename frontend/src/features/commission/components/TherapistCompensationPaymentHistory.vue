<script setup lang="ts">
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'
</script>

<template>
  <section class="therapist-payment-filter">
    <label>รอบเดือน</label><button type="button">เลือกช่วงเดือน <span>⌄</span></button>
  </section>
  <section class="therapist-payment-card">
    <div class="therapist-payment-table" role="table">
      <div class="therapist-payment-head" role="row">
        <span>รอบเดือน</span><span>Commission</span><span>ค่ามือ</span><span>Adjustment</span
        ><span>ยอดสุทธิ</span><span>วันที่จ่าย</span><span>สถานะ</span>
      </div>
      <TherapistCompensationEmptyState />
    </div>
    <p class="therapist-payment-formula">
      ยอดสุทธิ = Commission + ค่ามือ + Adjustment ที่ผ่านการปิดงวดแล้ว
    </p>
    <RouterLink class="therapist-primary-action" :to="{ name: 'therapist-adjustment-detail' }"
      >ดู Adjustment เดือนนี้</RouterLink
    >
  </section>
  <p class="therapist-payment-note">
    อ่านอย่างเดียว · ยอดที่จ่ายแล้วถูกล็อก หากพบข้อมูลไม่ถูกต้อง กรุณาแจ้ง Manager นอกระบบ
  </p>
</template>
