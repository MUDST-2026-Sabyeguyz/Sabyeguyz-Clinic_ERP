<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import TherapistCompensationSourceDetail from '../components/TherapistCompensationSourceDetail.vue'
</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main therapist-detail-page">
      <TherapistCompensationSourceDetail kind="sale" />
    </main>
  </div>
</template>
<style src="../styles/therapist-compensation-workspace.css"></style>
