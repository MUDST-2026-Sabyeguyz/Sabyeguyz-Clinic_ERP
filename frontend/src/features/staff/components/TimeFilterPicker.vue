<script setup lang="ts">
import { computed } from 'vue'
import FilterDropdown from './FilterDropdown.vue'
const props = defineProps<{ modelValue: string; label: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const part = (count: number) =>
  Array.from({ length: count }, (_, index) => {
    const value = String(index).padStart(2, '0')
    return { value, label: value }
  })
const hours = part(24)
const minutes = part(60)
const hour = computed(() => props.modelValue?.slice(0, 2) || '')
const minute = computed(() => props.modelValue?.slice(3, 5) || '')
</script>
<template>
  <div class="time-picker">
    <FilterDropdown
      :model-value="hour"
      :options="hours"
      :label="`${label} ชั่วโมง`"
      default-label="ชั่วโมง"
      :clear-on-reselect="false"
      hide-default-option
      @update:model-value="emit('update:modelValue', `${$event}:${minute || '00'}`)"
    />
    <span>:</span>
    <FilterDropdown
      :model-value="minute"
      :options="minutes"
      :label="`${label} นาที`"
      default-label="นาที"
      :clear-on-reselect="false"
      hide-default-option
      @update:model-value="emit('update:modelValue', `${hour || '00'}:${$event}`)"
    />
    <button
      v-if="modelValue"
      type="button"
      class="clear-time"
      :aria-label="`ล้าง${label}`"
      @click="emit('update:modelValue', '')"
    >
      ล้าง
    </button>
  </div>
</template>
<style scoped>
.time-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}
.clear-time {
  border: 0;
  padding: 4px;
  background: transparent;
  color: #16827d;
  cursor: pointer;
  font: inherit;
}
.time-picker.invalid :deep(.filter-dropdown__button) {
  border-color: #c44;
}
</style>
