<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export type FilterOption = { value: string; label: string }
const props = withDefaults(
  defineProps<{
    modelValue: string
    options: FilterOption[]
    defaultValue?: string
    defaultLabel: string
    label: string
    disabled?: boolean
    clearOnReselect?: boolean
    hideDefaultOption?: boolean
  }>(),
  { defaultValue: '', disabled: false, clearOnReselect: true, hideDefaultOption: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement | null>(null)
const open = ref(false)
const selectedLabel = computed(
  () =>
    props.options.find((option) => option.value === props.modelValue)?.label ?? props.defaultLabel,
)
function toggle() {
  if (!props.disabled) open.value = !open.value
}
function select(value: string) {
  emit(
    'update:modelValue',
    props.clearOnReselect && value === props.modelValue ? props.defaultValue : value,
  )
  open.value = false
}
function closeOnOutsideClick(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', closeOnOutsideClick))
onBeforeUnmount(() => document.removeEventListener('click', closeOnOutsideClick))
</script>

<template>
  <div ref="root" class="filter-dropdown" :class="{ open, disabled }">
    <button
      class="filter-dropdown__button"
      type="button"
      :aria-label="label"
      :aria-expanded="open"
      :disabled="disabled"
      @click="toggle"
    >
      <span>{{ selectedLabel }}</span
      ><span class="filter-dropdown__chevron" aria-hidden="true"></span>
    </button>
    <div v-if="open" class="filter-dropdown__menu" role="listbox" :aria-label="label">
      <button
        v-if="!hideDefaultOption"
        class="filter-dropdown__option"
        type="button"
        role="option"
        :aria-selected="modelValue === defaultValue"
        @click="select(defaultValue)"
      >
        <span class="filter-dropdown__check" aria-hidden="true">{{
          modelValue === defaultValue ? '✓' : ''
        }}</span
        ><span>{{ defaultLabel }}</span>
      </button>
      <button
        v-for="option in options"
        :key="option.value"
        class="filter-dropdown__option"
        type="button"
        role="option"
        :aria-selected="modelValue === option.value"
        @click="select(option.value)"
      >
        <span class="filter-dropdown__check" aria-hidden="true">{{
          modelValue === option.value ? '✓' : ''
        }}</span
        ><span>{{ option.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.filter-dropdown {
  position: relative;
  width: 100%;
  min-width: 0;
  color: #153738;
  font: inherit;
}
.filter-dropdown__button {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 46px;
  padding: 0 20px 0 16px;
  border: 1px solid #b7cecc;
  border-radius: 10px;
  background: #fff;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.filter-dropdown.open .filter-dropdown__button {
  border-color: #1e8883;
  box-shadow: 0 0 0 1px #1e8883;
}
.filter-dropdown__chevron {
  width: 8px;
  height: 8px;
  margin-left: 14px;
  border-right: 2px solid #16827d;
  border-bottom: 2px solid #16827d;
  transform: translateY(-2px) rotate(45deg);
  transition: transform 0.16s ease;
}
.filter-dropdown.open .filter-dropdown__chevron {
  transform: translateY(2px) rotate(225deg);
}
.filter-dropdown__menu {
  position: absolute;
  z-index: 40;
  top: calc(100% + 5px);
  left: 0;
  width: 100%;
  min-width: max-content;
  overflow: hidden;
  padding: 6px;
  border: 1px solid #d8e2e1;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 24px rgb(20 55 56 / 16%);
}
.filter-dropdown__option {
  display: grid;
  grid-template-columns: 20px 1fr;
  align-items: center;
  width: 100%;
  min-height: 40px;
  padding: 7px 12px 7px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}
.filter-dropdown__option:hover,
.filter-dropdown__option[aria-selected='true'] {
  background: #e8f1f0;
}
.filter-dropdown__check {
  color: #16827d;
  font-weight: 700;
}
.filter-dropdown.disabled {
  opacity: 0.55;
}
.filter-dropdown__button:disabled {
  cursor: not-allowed;
}
@media (max-width: 720px) {
  .filter-dropdown__menu {
    min-width: 100%;
  }
}
</style>
