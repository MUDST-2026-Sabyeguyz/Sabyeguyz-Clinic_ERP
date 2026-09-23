<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi } from '@/api/client'

type Tier = { order: number; min: number; max: number | null; rate: number; active: boolean }
const tiers = reactive<Tier[]>([
  { order: 1, min: 0, max: 59999, rate: 5, active: true },
  { order: 2, min: 60000, max: 69999, rate: 6, active: true },
  { order: 3, min: 70000, max: 79999, rate: 7, active: true },
  { order: 4, min: 80000, max: 89999, rate: 8, active: true },
  { order: 5, min: 90000, max: null, rate: 10, active: true },
])
const editingIndex = ref(-1)
const editorOpen = ref(false)
const error = ref('')
const scheme = reactive({ id: 0, name: 'Commission Scheme V1', version: '1', effectiveFrom: '2026-01-01', effectiveTo: null as string|null })
const form = reactive({ min: 0, max: '' as number | '', rate: 5, active: true })
const loading = ref(false)

function openEditor(index = -1) {
  editingIndex.value = index
  error.value = ''
  const tier = tiers[index]
  Object.assign(
    form,
    tier
      ? { min: tier.min, max: tier.max ?? '', rate: tier.rate, active: tier.active }
      : { min: 0, max: '', rate: 5, active: true },
  )
  editorOpen.value = true
}
async function saveTier() {
  error.value = ''
  const maximum = form.max === '' ? null : Number(form.max)
  if (form.min < 0 || form.rate < 0 || (maximum !== null && form.min > maximum)) {
    error.value = 'ตรวจสอบยอดขายขั้นต่ำ ยอดขายสูงสุด และอัตราคอมมิชชันอีกครั้ง'
    return
  }
  const overlaps = tiers.some((tier, index) => {
    if (index === editingIndex.value || !tier.active || !form.active) return false
    const aMax = maximum ?? Number.POSITIVE_INFINITY
    const bMax = tier.max ?? Number.POSITIVE_INFINITY
    return form.min <= bMax && tier.min <= aMax
  })
  if (overlaps) {
    error.value = 'บันทึกไม่ได้ เนื่องจากช่วงยอดขายซ้อนกับ Tier ที่มีอยู่'
    return
  }
  const record: Tier = {
    order: editingIndex.value >= 0 ? tiers[editingIndex.value]!.order : tiers.length + 1,
    min: Number(form.min),
    max: maximum,
    rate: Number(form.rate),
    active: form.active,
  }
  if (editingIndex.value >= 0) tiers[editingIndex.value] = record
  else tiers.push(record)
  try { await commissionApi.updateScheme(scheme.id,{name:scheme.name,version:scheme.version,effectiveFrom:scheme.effectiveFrom,effectiveTo:scheme.effectiveTo,tiers:tiers.filter(t=>t.active).sort((a,b)=>a.min-b.min).map((t,index)=>({minimum:t.min,maximum:t.max,rate:t.rate/100,order:index+1}))}); editorOpen.value = false }
  catch(caught){error.value=caught instanceof Error?caught.message:'บันทึก Tier ไม่สำเร็จ'}
}
onMounted(async () => {
  loading.value = true
  try {
    const schemes = await commissionApi.schemes() as Array<{ id: number; name: string; version:string; effectiveFrom: string; effectiveTo:string|null }>
    const current = schemes[0]
    if (current) {
      Object.assign(scheme,current)
      const backendTiers = await commissionApi.tiers(current.id) as Array<{ minimum: number; maximum: number | null; rate: number; order: number }>
      tiers.splice(0, tiers.length, ...backendTiers.map((tier) => ({ order: tier.order, min: tier.minimum, max: tier.maximum, rate: tier.rate * 100, active: true })))
    }
  } catch (caught) { error.value = caught instanceof Error ? caught.message : 'โหลดเกณฑ์คอมมิชชันไม่สำเร็จ' }
  finally { loading.value = false }
})
</script>

<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>COMMISSION SETTINGS</small>
        <h1>ตั้งค่า Commission Tier</h1>
        <p>กำหนดช่วงยอดขายคอร์สและอัตราคอมมิชชัน โดยไม่กระทบรอบที่ปิดแล้ว</p>
      </section>
      <nav class="commission-nav">
        <RouterLink to="/staff/commission">ภาพรวม</RouterLink>
        <RouterLink to="/staff/commission/tiers">Commission Tier</RouterLink>
        <RouterLink to="/staff/commission/closing">ปิดรอบรายเดือน</RouterLink>
      </nav>
      <section class="staff-panel commission-page-panel">
        <header>
          <div>
            <small>SCHEME VERSION</small>
            <h2>{{ scheme.name }}</h2>
            <p class="muted-copy">เริ่มใช้ {{ scheme.effectiveFrom }}</p>
          </div>
          <button class="primary" @click="openEditor()">+ เพิ่ม Tier</button>
        </header>
        <div v-if="editorOpen" class="commission-form">
          <label>ยอดขายขั้นต่ำ<input v-model.number="form.min" type="number" min="0" /></label>
          <label
            >ยอดขายสูงสุด<input
              v-model.number="form.max"
              type="number"
              min="0"
              placeholder="ไม่จำกัด"
          /></label>
          <label
            >อัตราคอมมิชชัน (%)<input v-model.number="form.rate" type="number" min="0" step="0.01"
          /></label>
          <label
            >สถานะ<select v-model="form.active">
              <option :value="true">ใช้งาน</option>
              <option :value="false">ปิดใช้งาน</option>
            </select></label
          >
          <div class="form-actions form-wide">
            <button class="secondary" @click="editorOpen = false">ยกเลิก</button
            ><button class="primary" @click="saveTier">บันทึก Tier</button>
          </div>
        </div>
        <p v-if="error" class="validation-error">{{ error }}</p>
        <table class="staff-table">
          <thead>
            <tr>
              <th>ลำดับ</th>
              <th>ยอดขายขั้นต่ำ</th>
              <th>ยอดขายสูงสุด</th>
              <th>อัตราคอมมิชชัน</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(tier, index) in tiers" :key="tier.order">
              <td>
                <b>Tier {{ tier.order }}</b>
              </td>
              <td>฿{{ tier.min.toLocaleString() }}</td>
              <td>{{ tier.max === null ? 'ไม่จำกัด' : `฿${tier.max.toLocaleString()}` }}</td>
              <td>
                <b>{{ tier.rate }}%</b>
              </td>
              <td>
                <span class="status" :class="{ void: !tier.active }">{{
                  tier.active ? 'ใช้งาน' : 'ปิดใช้งาน'
                }}</span>
              </td>
              <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-workspace.css';
</style>
