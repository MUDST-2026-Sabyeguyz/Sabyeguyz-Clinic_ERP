<script setup lang="ts">
import AppointmentCalendarWorkspace from '../components/AppointmentCalendarWorkspace.vue'

const isManager =
  (JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null)?.role ===
  'MANAGER'
</script>

<template>
  <AppointmentCalendarWorkspace :manager="isManager" />
</template>
