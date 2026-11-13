<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AnimatedGradient from '@/components/AnimatedGradient.vue'

const route = useRoute()
// Staff workspace pages get the animated gradient; login and branch selection keep their own look.
const showGradient = computed(
  () => route.path.startsWith('/staff/') && route.path !== '/staff/select-branch',
)
</script>

<template>
  <AnimatedGradient v-if="showGradient" />
  <RouterView />
</template>
