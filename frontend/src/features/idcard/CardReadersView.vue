<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import {
  ApiError,
  idCardApi,
  type IdCardWorkstation,
  type IdCardWorkstationCreated,
} from '@/api/client'

const workstations = ref<IdCardWorkstation[]>([])
const name = ref('')
const created = ref<IdCardWorkstationCreated | null>(null)
const message = ref('')
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')

async function load() {
  try {
    workstations.value = await idCardApi.workstations(branchCode.value || undefined)
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'โหลดรายการเครื่องอ่านบัตรไม่สำเร็จ'
  }
}

async function add() {
  message.value = ''
  if (!name.value.trim() || !branchCode.value) {
    message.value = 'กรอกชื่อเครื่อง และเลือกสาขาก่อน'
    return
  }
  try {
    created.value = await idCardApi.createWorkstation(name.value.trim(), branchCode.value)
    name.value = ''
    await load()
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'เพิ่มเครื่องอ่านบัตรไม่สำเร็จ'
  }
}

async function repair(id: number) {
  created.value = await idCardApi.newPairingCode(id)
}

async function revoke(workstation: IdCardWorkstation) {
  if (!window.confirm(`เพิกถอนเครื่อง "${workstation.name}"? โปรแกรมตัวกลางของเครื่องนี้จะใช้งานไม่ได้อีก`)) return
  await idCardApi.revokeWorkstation(workstation.id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="patient-workspace">
    <StaffHeader />
    <main>
      <section class="page-heading">
        <div>
          <h1>เครื่องอ่านบัตรประชาชน</h1>
          <p>เพิ่มเครื่องอ่านบัตรของสาขา แล้วนำรหัสจับคู่ไปใส่ในโปรแกรมตัวกลางที่เครื่องหน้าเคาน์เตอร์</p>
        </div>
      </section>

      <form class="card-reader-add" @submit.prevent="add">
        <label>
          ชื่อเครื่อง
          <input v-model="name" name="name" maxlength="100" placeholder="เช่น เคาน์เตอร์ 1" />
        </label>
        <button type="submit" class="primary">เพิ่มเครื่อง</button>
      </form>
      <p v-if="message" role="alert" class="required-error">{{ message }}</p>

      <section v-if="created" class="pairing-code" role="status">
        <p>รหัสจับคู่ของ "{{ created.name }}" (ใช้ได้ครั้งเดียว ภายใน 10 นาที)</p>
        <strong>{{ created.pairingCode }}</strong>
      </section>

      <table class="card-reader-list">
        <thead>
          <tr>
            <th>ชื่อ</th>
            <th>สาขา</th>
            <th>สถานะ</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in workstations" :key="w.id">
            <td>{{ w.name }}</td>
            <td>{{ w.branchCode }}</td>
            <td>{{ w.status === 'Revoked' ? 'เพิกถอนแล้ว' : w.online ? 'ออนไลน์' : 'ออฟไลน์' }}</td>
            <td>
              <template v-if="w.status === 'Active'">
                <button type="button" class="secondary" @click="repair(w.id)">รหัสจับคู่ใหม่</button>
                <button type="button" class="secondary revoke" @click="revoke(w)">เพิกถอน</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </main>
  </div>
</template>

<style scoped>
.card-reader-add {
  display: flex;
  gap: 12px;
  align-items: end;
  margin: 16px 0;
}
.pairing-code strong {
  font-size: 32px;
  letter-spacing: 6px;
}
.card-reader-list {
  width: 100%;
  border-collapse: collapse;
}
.card-reader-list th,
.card-reader-list td {
  padding: 8px;
  text-align: left;
  border-bottom: 1px solid #dfe7e3;
}
</style>
