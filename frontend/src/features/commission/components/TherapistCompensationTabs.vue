<script setup lang="ts">
import { useRoute } from 'vue-router'
import {
  THERAPIST_COMPENSATION_TABS,
  type TherapistCompensationTab,
} from '../types/therapistCompensation'

defineProps<{ activeTab: TherapistCompensationTab }>()
const route = useRoute()
</script>

<template>
  <nav class="therapist-compensation-tabs" aria-label="เมนูค่าตอบแทน">
    <RouterLink
      v-for="tab in THERAPIST_COMPENSATION_TABS"
      :key="tab.key"
      :class="{ active: activeTab === tab.key }"
      :to="{
        name: 'therapist-compensation',
        query: { ...route.query, tab: tab.key },
      }"
    >
      {{ tab.label }}
    </RouterLink>
  </nav>
</template>
