<script setup lang="ts">
withDefaults(
  defineProps<{
    name:
      | 'branch'
      | 'users'
      | 'services'
      | 'payments'
      | 'rooms'
      | 'commission'
      | 'tiers'
      | 'master'
      | 'calendar'
      | 'settings'
  }>(),
  {},
)
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'branch'">
      <path d="M4 21h16" />
      <path d="M6 21V5l6-3 6 3v16" />
      <path d="M9 9h.01M15 9h.01M9 13h.01M15 13h.01" />
    </template>
    <template v-else-if="name === 'users'">
      <circle cx="9" cy="8" r="3" />
      <path d="M3 21v-2a6 6 0 0 1 12 0v2M16 4.5a3 3 0 0 1 0 5.8M21 21v-2a6 6 0 0 0-3.8-5.6" />
    </template>
    <template v-else-if="name === 'services'">
      <path d="M12 3v18M3 12h18" />
      <circle cx="12" cy="12" r="7" />
    </template>
    <template v-else-if="name === 'payments'">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3 10h18M7 15h3" />
    </template>
    <template v-else-if="name === 'rooms'">
      <path d="M5 21V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v17" />
      <path d="M3 21h18M10 12h.01" />
    </template>
    <template v-else-if="name === 'commission'">
      <circle cx="9" cy="12" r="5" />
      <path
        d="M9 9v6M7.5 10.5h2.1a1.4 1.4 0 0 1 0 2.8H8.4a1.4 1.4 0 0 0 0 2.8h2.1M15 7.5A5 5 0 0 1 15 16.5"
      />
    </template>
    <template v-else-if="name === 'tiers'">
      <path d="M4 19V5M4 19h16M8 15l3-3 3 2 5-6" />
      <path d="M16 8h3v3" />
    </template>
    <template v-else-if="name === 'master'">
      <ellipse cx="12" cy="5" rx="7" ry="3" />
      <path d="M5 5v7c0 1.7 3.1 3 7 3s7-1.3 7-3V5M5 12v7c0 1.7 3.1 3 7 3s7-1.3 7-3v-7" />
    </template>
    <template v-else-if="name === 'calendar'">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4M17 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="3" />
      <path
        d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.12 2.12-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.04 1.56V20.3h-3v-.08A1.7 1.7 0 0 0 10.66 18.7a1.7 1.7 0 0 0-1.88.34l-.06.06L6.6 16.98l.06-.06A1.7 1.7 0 0 0 7 15.04a1.7 1.7 0 0 0-1.56-1.04h-.08v-3h.08A1.7 1.7 0 0 0 7 9.96a1.7 1.7 0 0 0-.34-1.88L6.6 8.02 8.72 5.9l.06.06a1.7 1.7 0 0 0 1.88.34 1.7 1.7 0 0 0 1.04-1.56v-.08h3v.08a1.7 1.7 0 0 0 1.04 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06 2.12 2.12-.06.06A1.7 1.7 0 0 0 19.4 9.96 1.7 1.7 0 0 0 20.96 11h.08v3h-.08A1.7 1.7 0 0 0 19.4 15Z"
      />
    </template>
  </svg>
</template>

<style scoped>
svg {
  width: 100%;
  height: 100%;
}
</style>
