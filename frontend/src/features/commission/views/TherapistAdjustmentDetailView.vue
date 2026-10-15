<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import TherapistCompensationEmptyState from '../components/TherapistCompensationEmptyState.vue'
</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main therapist-detail-page">
      <section class="therapist-adjustment-card">
        <h1>Adjustment งวดตุลาคม</h1>
        <TherapistCompensationEmptyState />
        <RouterLink
          class="therapist-primary-action"
          :to="{ name: 'therapist-compensation', query: { tab: 'payments' } }"
        >
          กลับประวัติการจ่าย
        </RouterLink>
      </section>
    </main>
  </div>
</template>
<style src="../styles/therapist-compensation-workspace.css"></style>
