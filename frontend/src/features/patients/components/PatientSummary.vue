<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{
  name: string
  title?: string | null
  hn?: string | null
  phone?: string | null
  branch?: string | null
}>()
const displayName = computed(
  () => [props.title?.trim(), props.name.trim()].filter(Boolean).join(' ') || '-',
)
const initials = computed(
  () =>
    props.name
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.slice(0, 1))
      .join('') || '-',
)
</script>

<template>
  <section class="patient-summary" aria-label="สรุปข้อมูลผู้ป่วย">
    <div class="patient-summary__avatar" aria-hidden="true">{{ initials }}</div>
    <div class="patient-summary__identity">
      <h2 class="patient-summary__name">{{ displayName }}</h2>
      <div class="patient-summary__meta">
        <span>HN {{ hn || '-' }}</span>
        <span class="patient-summary__phone">
          <svg v-if="phone" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.1-8.7A2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 2 .7 2.9a2 2 0 0 1-.5 2.1L8 10a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.5c.9.3 1.9.6 2.9.7a2 2 0 0 1 1.7 2Z"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span><span class="patient-summary__sr-only">โทรศัพท์ </span>{{ phone || '-' }}</span>
        </span>
      </div>
    </div>
    <span v-if="branch" class="patient-summary__branch">{{ branch }}</span>
  </section>
</template>

<style scoped>
.patient-summary {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px 18px;
  position: relative;
  width: 100%;
  min-height: 104px;
  padding: 20px 24px 20px 20px;
  border-left: 5px solid #1f8883;
  border-radius: 12px;
  background: #fff;
}
.patient-summary__avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #e1f0ec;
  color: #176c68;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}
.patient-summary__identity {
  min-width: 0;
}
.patient-summary__name {
  margin: 0;
  color: #25372f;
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.patient-summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 8px;
  color: #667a72;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
}
.patient-summary__phone {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.patient-summary__phone svg {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
}
.patient-summary__branch {
  max-width: 240px;
  padding: 8px 20px;
  border-radius: 17px;
  background: #eef6f0;
  color: #34724c;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  text-align: center;
  overflow-wrap: anywhere;
}
.patient-summary__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
@media (max-width: 600px) {
  .patient-summary {
    grid-template-columns: 48px minmax(0, 1fr);
    padding: 20px 16px;
    gap: 12px 16px;
  }
  .patient-summary__branch {
    grid-column: 2;
    justify-self: start;
    max-width: 100%;
    box-sizing: border-box;
  }
}
</style>
