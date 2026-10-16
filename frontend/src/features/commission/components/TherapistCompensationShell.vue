<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import type { TherapistCompensationTab } from '../types/therapistCompensation'
import TherapistCompensationTabs from './TherapistCompensationTabs.vue'

withDefaults(
  defineProps<{
    title: string
    subtitle: string
    activeTab: TherapistCompensationTab
    tabs?: boolean
    error?: string
    loading?: boolean
  }>(),
  { tabs: true },
)
</script>

<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main therapist-detail-page">
      <header v-if="title" class="therapist-compensation-heading">
        <div>
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <slot name="aside" />
      </header>
      <TherapistCompensationTabs v-if="tabs" :active-tab="activeTab" />
      <p v-if="error" role="alert" class="therapist-error">{{ error }}</p>
      <p v-else-if="loading" role="status">กำลังโหลดค่าตอบแทน...</p>
      <slot v-else />
    </main>
  </div>
</template>

<style src="../styles/therapist-compensation-workspace.css"></style>
