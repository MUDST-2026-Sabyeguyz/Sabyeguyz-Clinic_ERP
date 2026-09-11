<template>
  <RouterView v-if="$route.meta.public" />
  <MainLayout v-else><RouterView /></MainLayout>
</template>
<script setup lang="ts">
import MainLayout from './layouts/MainLayout.vue'
</script>
