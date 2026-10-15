<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import TherapistCompensationPaymentHistory from '../components/TherapistCompensationPaymentHistory.vue'
</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main"><TherapistCompensationPaymentHistory /></main>
  </div>
</template>
<style src="../styles/therapist-compensation-workspace.css"></style>
