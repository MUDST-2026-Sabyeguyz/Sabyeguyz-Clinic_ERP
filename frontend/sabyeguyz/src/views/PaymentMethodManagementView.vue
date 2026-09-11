<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPaymentMethods, createPaymentMethod, updatePaymentMethod, deactivatePaymentMethod, type PaymentMethod } from '@/api/paymentMethods'
const rows = ref<PaymentMethod[]>([])
const form = ref({ code: '', name: '' })
const editing = ref<number | null>(null)
const busy = ref(false), error = ref(''), success = ref('')
async function load() { rows.value = await listPaymentMethods() }
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { await action() } catch (e) { error.value = e instanceof Error ? e.message : 'ดำเนินการไม่สำเร็จ' } finally { busy.value = false }
}
function reset() { editing.value = null; form.value = { code: '', name: '' } }
function edit(row: PaymentMethod) { editing.value = row.id; form.value = { code: row.code, name: row.name } }
function save() { return run(async () => {
  const body = { code: form.value.code.trim(), name: form.value.name.trim() }
  if (!body.code || !body.name) throw new Error('กรุณากรอกรหัสและชื่อ')
  if (editing.value) await updatePaymentMethod(editing.value, body)
  else await createPaymentMethod(body)
  reset(); success.value = 'บันทึกแล้ว'; await load()
}) }
function deactivate(row: PaymentMethod) { return run(async () => { await deactivatePaymentMethod(row.id); success.value = 'ปิดใช้งานแล้ว ประวัติการชำระเงินยังคงอยู่'; await load() }) }
onMounted(() => run(load))
</script>
<template>
  <section class="patient-page"><header class="patient-page-header"><h1>ช่องทางชำระเงิน</h1></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p><p v-if="success" role="status" class="success">{{ success }}</p>
    <form class="form-card" @submit.prevent="save"><h2>{{ editing ? 'แก้ไข' : 'เพิ่มช่องทางชำระเงิน' }}</h2>
      <fieldset :disabled="busy"><div class="form-grid"><label>รหัส<input v-model="form.code" required /></label><label>ชื่อ<input v-model="form.name" required /></label></div>
      <button type="submit" class="primary">บันทึก</button><button type="button" @click="reset">ยกเลิก</button></fieldset>
    </form>
    <p v-if="busy" role="status">กำลังดำเนินการ...</p><p v-else-if="!rows.length">ไม่มีช่องทางชำระเงินที่ใช้งานอยู่</p>
    <article v-for="row in rows" :key="row.id" class="patient-result-card"><div><h2>{{ row.name }}</h2><p>{{ row.code }}</p></div><div class="actions"><button :disabled="busy" @click="edit(row)">แก้ไข</button><button :disabled="busy" @click="deactivate(row)">ปิดใช้งาน</button></div></article>
  </section>
</template>
