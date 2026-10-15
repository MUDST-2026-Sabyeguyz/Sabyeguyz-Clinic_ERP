<script setup lang="ts">
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'
</script>

<template>
  <section class="therapist-overview-grid">
    <article class="therapist-summary-card">
      <p>สรุปค่าตอบแทน</p>
      <dl>
        <div>
          <dt>Course Commission</dt>
          <dd>—</dd>
        </div>
        <div>
          <dt>ค่ามือ</dt>
          <dd>—</dd>
        </div>
        <div>
          <dt>Adjustment</dt>
          <dd>—</dd>
        </div>
        <div class="net">
          <dt>Net Payout</dt>
          <dd>—</dd>
        </div>
      </dl>
    </article>
    <article class="therapist-principle-card">
      <h2>หลักการคำนวณ</h2>
      <div>
        <b>Course Commission</b>
        <p>คำนวณจากยอดขายคอร์สตาม Tier ที่กำหนด</p>
      </div>
      <div>
        <b>ค่ามือ</b>
        <p>คำนวณจากรายการรักษาที่บันทึกโดยผู้ให้บริการ</p>
      </div>
    </article>
  </section>
  <section class="therapist-table-card">
    <h2>รายละเอียดรอบปัจจุบัน</h2>
    <TherapistCompensationEmptyState />
  </section>
</template>
