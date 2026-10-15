<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import TherapistCompensationDetail from '../components/TherapistCompensationDetail.vue'
</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main therapist-detail-page">
      <TherapistCompensationDetail kind="commission" />
    </main>
  </div>
</template>
<style src="../styles/therapist-compensation-workspace.css"></style>
