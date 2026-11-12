<script setup lang="ts">
import { computed } from 'vue'
import type { CoursePurchase } from '@/api/client'
import type { UsageSelection } from '@/features/services/careTransactions'
const props = defineProps<{
  modelValue: UsageSelection[]
  courses: CoursePurchase[]
  branchCode: string
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: UsageSelection[]] }>()
// แสดงเฉพาะคอร์สที่ใช้ได้และอยู่สาขาเดียวกับการรับบริการ
const available = computed(() =>
  props.courses.filter(
    (course) => course.usable && course.remaining > 0 && course.branchCode === props.branchCode,
  ),
)
function add() {
  // เพิ่มคอร์สที่ยังไม่ถูกเลือก เพื่อไม่ให้ส่ง usage ซ้ำในฟอร์ม
  const course = available.value.find(
    (item) => !props.modelValue.some((row) => row.coursePurchaseId === item.id),
  )
  if (course)
    emit('update:modelValue', [...props.modelValue, { coursePurchaseId: course.id, quantity: 1 }])
}
function change(index: number, field: 'coursePurchaseId' | 'quantity', event: Event) {
  emit(
    'update:modelValue',
    props.modelValue.map((row, i) =>
      i === index ? { ...row, [field]: Number((event.target as HTMLInputElement).value) } : row,
    ),
  )
}
</script>
<template>
  <fieldset class="usage-picker" :disabled="disabled">
    <legend>สิทธิ์คอร์สเดิมที่ใช้ในครั้งนี้</legend>
    <p v-if="!available.length">ไม่มีสิทธิ์คอร์สที่พร้อมใช้ในสาขานี้</p>
    <div v-for="(row, index) in modelValue" :key="index" class="usage-row">
      <label
        >คอร์ส<select
          :value="row.coursePurchaseId"
          @change="change(index, 'coursePurchaseId', $event)"
        >
          <option v-for="course in available" :key="course.id" :value="course.id">
            {{ course.course.name }} · คงเหลือ {{ course.remaining }} ครั้ง
          </option>
        </select></label
      >
      <label
        >จำนวนครั้ง<input
          type="number"
          min="1"
          :max="courses.find((c) => c.id === row.coursePurchaseId)?.remaining"
          step="1"
          :value="row.quantity"
          @input="change(index, 'quantity', $event)"
      /></label>
      <button
        type="button"
        @click="
          emit(
            'update:modelValue',
            modelValue.filter((_, i) => i !== index),
          )
        "
      >
        นำออก
      </button>
    </div>
    <button
      type="button"
      :disabled="!available.some((c) => !modelValue.some((row) => row.coursePurchaseId === c.id))"
      @click="add"
    >
      เพิ่มคอร์สที่ใช้สิทธิ์
    </button>
    <p>ใช้หลายสิทธิ์ร่วมใน Visit เดียว สิทธิ์เดิมไม่ถูกคิดเงินซ้ำ</p>
  </fieldset>
</template>
<style scoped>
.usage-picker {
  border: 1px solid #dce8e3;
  border-radius: 10px;
  padding: 16px;
  margin: 18px 0;
}
legend {
  font-weight: 600;
}
.usage-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px auto;
  align-items: end;
  gap: 12px;
  margin: 12px 0;
}
label {
  display: grid;
  gap: 6px;
  min-width: 0;
}
input,
select,
button {
  min-height: 44px;
  max-width: 100%;
  box-sizing: border-box;
  font: inherit;
  padding: 8px;
  border: 1px solid #9bb7ac;
  border-radius: 6px;
  background: white;
  color: #173b3b;
}
p {
  font-size: 14px;
  line-height: 1.5;
}
@media (max-width: 520px) {
  .usage-row {
    grid-template-columns: minmax(0, 1fr) 90px;
  }
  .usage-row button {
    grid-column: 1 / -1;
  }
}
</style>
