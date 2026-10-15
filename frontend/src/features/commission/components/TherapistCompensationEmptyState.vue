<script setup lang="ts">
import { EMPTY_COMPENSATION_COPY } from '../types/therapistCompensation'
</script>

<template>
  <div class="therapist-empty-state" role="status">
    <strong>{{ EMPTY_COMPENSATION_COPY }}</strong>
    <p>ข้อมูลจะแสดงเมื่อระบบเชื่อมต่อข้อมูลค่าตอบแทนรายบุคคลแล้ว</p>
  </div>
</template>
