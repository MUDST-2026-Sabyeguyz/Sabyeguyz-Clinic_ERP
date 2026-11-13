<script setup lang="ts">
import { computed } from 'vue'
import FilterDropdown from './FilterDropdown.vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    min?: string
    disabled?: boolean
    clearable?: boolean
    pastYears?: number
    futureYears?: number
  }>(),
  { min: '', disabled: false, clearable: false, pastYears: 20, futureYears: 30 },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const currentYear = new Date().getFullYear()
const year = computed(
  () => Number(props.modelValue.slice(0, 4)) || Number(props.min.slice(0, 4)) || currentYear,
)
const month = computed(() => props.modelValue.slice(5, 7))
const years = computed(() => {
  const first = props.min
    ? Number(props.min.slice(0, 4))
    : Math.min(currentYear - props.pastYears, year.value)
  const last = Math.max(currentYear + props.futureYears, year.value, first)
  return Array.from({ length: last - first + 1 }, (_, index) => ({
    value: String(first + index),
    label: String(first + index + 543),
  }))
})
const months = computed(() =>
  Array.from({ length: 12 }, (_, index) => {
    const value = String(index + 1).padStart(2, '0')
    return {
      value,
      label: new Intl.DateTimeFormat('th-TH', { month: 'long' }).format(new Date(2026, index, 1)),
    }
  }).filter((item) => !props.min || `${year.value}-${item.value}` >= props.min),
)
function selectYear(value: string) {
  const next = `${value}-${month.value || '01'}`
  emit('update:modelValue', props.min && next < props.min ? props.min : next)
}
function selectMonth(value: string) {
  emit('update:modelValue', value ? `${year.value}-${value}` : '')
}
</script>

<template>
  <div class="month-picker">
    <FilterDropdown
      :model-value="month"
      :options="months"
      label="เลือกเดือน"
      :default-label="clearable ? 'ทุกเดือน' : 'เลือกเดือน'"
      :disabled="disabled"
      :clear-on-reselect="false"
      :hide-default-option="!clearable"
      @update:model-value="selectMonth"
    />
    <FilterDropdown
      :model-value="String(year)"
      :options="years"
      label="เลือกปี"
      default-label="เลือกปี"
      :disabled="disabled"
      :clear-on-reselect="false"
      hide-default-option
      @update:model-value="selectYear"
    />
  </div>
</template>

<style scoped>
.month-picker {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 8px;
}
</style>
