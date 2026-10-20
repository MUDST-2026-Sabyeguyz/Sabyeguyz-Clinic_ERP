<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

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
    hideChevron?: boolean
  }>(),
  { defaultValue: '', disabled: false, clearOnReselect: true, hideDefaultOption: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const open = ref(false)
const menuStyle = ref<Record<string, string>>({})
const selectedLabel = computed(
  () =>
    props.options.find((option) => option.value === props.modelValue)?.label ?? props.defaultLabel,
)
function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) void positionMenu()
}
function select(value: string) {
  emit(
    'update:modelValue',
    props.clearOnReselect && value === props.modelValue ? props.defaultValue : value,
  )
  open.value = false
}
async function positionMenu() {
  await nextTick()
  const triggerElement = trigger.value
  const menuElement = menu.value
  if (!triggerElement || !menuElement) return

  const rect = triggerElement.getBoundingClientRect()
  const menuHeight = menuElement.offsetHeight
  const gap = 5
  const spaceBelow = window.innerHeight - rect.bottom - gap
  const spaceAbove = rect.top - gap
  // Prefer opening downward so the first row never pushes the menu beyond the top edge.
  // Flip upward only when the menu cannot fit below and there is more room above.
  const openAbove = spaceBelow < menuHeight && spaceAbove > spaceBelow
  const left = Math.min(
    Math.max(8, rect.left),
    Math.max(8, window.innerWidth - menuElement.scrollWidth - 8),
  )
  menuStyle.value = {
    left: `${left}px`,
    minWidth: `${Math.max(rect.width, menuElement.scrollWidth)}px`,
    top: openAbove ? 'auto' : `${rect.bottom + gap}px`,
    bottom: openAbove ? `${window.innerHeight - rect.top + gap}px` : 'auto',
  }
}
function closeOnOutsideClick(event: MouseEvent) {
  const target = event.target as Node
  if (!root.value?.contains(target) && !menu.value?.contains(target)) open.value = false
}
function closeOnScroll(event: Event) {
  if (event.target instanceof Node && menu.value?.contains(event.target)) return
  if (open.value) open.value = false
}
function closeOnResize() {
  if (open.value) open.value = false
}
onMounted(() => {
  document.addEventListener('click', closeOnOutsideClick)
  window.addEventListener('scroll', closeOnScroll, true)
  window.addEventListener('resize', closeOnResize)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutsideClick)
  window.removeEventListener('scroll', closeOnScroll, true)
  window.removeEventListener('resize', closeOnResize)
})
</script>

<template>
  <div ref="root" class="filter-dropdown" :class="{ open, disabled }">
    <button
      ref="trigger"
      class="filter-dropdown__button"
      type="button"
      :aria-label="label"
      :aria-expanded="open"
      :disabled="disabled"
      @click="toggle"
    >
      <span>{{ selectedLabel }}</span
      ><span v-if="!hideChevron" class="filter-dropdown__chevron" aria-hidden="true"></span>
    </button>
    <Teleport :to="root?.closest('[role=dialog]') || 'body'">
      <div
        v-if="open"
        ref="menu"
        class="filter-dropdown__menu"
        :style="menuStyle"
        role="listbox"
        :aria-label="label"
      >
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
    </Teleport>
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
  position: fixed;
  z-index: 1000;
  width: max-content;
  max-width: calc(100vw - 16px);
  max-height: min(50vh, 360px);
  overflow-y: auto;
  overscroll-behavior: contain;
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
</style>
