<template><section class="patient-page"><header class="patient-page-header"><div><p class="eyebrow">BACK OFFICE</p><h1>Branch Management</h1><p class="page-subtitle">จัดการข้อมูลสาขา</p></div><button @click="reset">เพิ่มสาขา</button></header>
  <form class="profile-card" @submit.prevent="save"><h2>{{ editing ? 'แก้ไขสาขา' : 'สร้างสาขา' }}</h2><div class="profile-grid"><label>Code<input v-model="form.code" maxlength="2" required /></label><label>Numeric Code<input v-model="form.numericCode" maxlength="2" pattern="[0-9]{2}" required /></label><label class="full-width">ชื่อสาขา<input v-model="form.name" required /></label></div><p v-if="errorMessage" class="error">{{ errorMessage }}</p><button type="submit">บันทึก</button><button v-if="editing" type="button" class="secondary" @click="reset">ยกเลิก</button></form>
  <div v-if="loading" class="state-card">กำลังโหลด...</div><div v-else class="patient-results"><article v-for="branch in branches" :key="branch.id" class="patient-result-card"><div><span class="patient-hn">{{ branch.code }} · {{ branch.numericCode }}</span><h2>{{ branch.name }}</h2></div><div><span class="status-badge" :class="{ inactive: !branch.active }">{{ branch.active ? 'Active' : 'Inactive' }}</span><div class="actions"><button class="secondary" @click="edit(branch)">แก้ไข</button><button v-if="branch.active" class="secondary" @click="deactivate(branch)">ปิดใช้งาน</button></div></div></article></div></section></template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createBranch, deactivateBranch, listBranches, updateBranch, type Branch, type BranchPayload } from '../api/branches'
const branches = ref<Branch[]>([]), loading = ref(true), editing = ref<number | null>(null), errorMessage = ref('')
const empty = (): BranchPayload => ({ code: '', numericCode: '', name: '' }); const form = ref(empty())
async function load() { loading.value = true; try { branches.value = await listBranches() } catch (e) { errorMessage.value = e instanceof Error ? e.message : 'โหลดข้อมูลไม่สำเร็จ' } finally { loading.value = false } }
function reset() { editing.value = null; form.value = empty(); errorMessage.value = '' }
function edit(branch: Branch) { editing.value = branch.id; form.value = { code: branch.code, numericCode: branch.numericCode, name: branch.name }; errorMessage.value = '' }
async function save() { errorMessage.value = ''; try { if (editing.value) await updateBranch(editing.value, form.value); else await createBranch(form.value); reset(); await load() } catch (e) { errorMessage.value = e instanceof Error ? e.message : 'บันทึกไม่สำเร็จ' } }
async function deactivate(branch: Branch) { try { await deactivateBranch(branch.id); await load() } catch (e) { errorMessage.value = e instanceof Error ? e.message : 'ปิดใช้งานไม่สำเร็จ' } }
onMounted(load)
</script>
