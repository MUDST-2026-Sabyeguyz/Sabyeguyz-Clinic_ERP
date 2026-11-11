<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { vModalFocus } from '@/features/appointments/modalFocus'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import {
  ApiError,
  idCardApi,
  type IdCardWorkstation,
  type IdCardWorkstationCreated,
} from '@/api/client'
import { agentLocal, type LocalAgentStatus } from './agentLocal'

const workstations = ref<IdCardWorkstation[]>([])
defineProps<{ embedded?: boolean }>()
const name = ref('')
const created = ref<IdCardWorkstationCreated | null>(null)
const pairingOpen = ref(false)
const pairingSeconds = ref(0)
const pairingTime = computed(() => `${Math.floor(pairingSeconds.value / 60)}:${String(pairingSeconds.value % 60).padStart(2, '0')}`)
let pairingTimer: ReturnType<typeof setInterval> | undefined
// The agent installed on this PC, if any. The manager may be at another PC, so pairing it is always an explicit click.
const localAgent = ref<LocalAgentStatus | null>(null)
const localPairState = ref<'idle' | 'working' | 'done'>('idle')
const localPairMessage = ref('')
let pairSequence = 0
let reloadTimer: ReturnType<typeof setTimeout> | undefined
async function refreshLocalAgent() {
  localAgent.value = (await agentLocal.status()) ?? null
}
async function pairLocal() {
  const item = created.value
  if (!item || localPairState.value === 'working') return
  const mine = ++pairSequence
  localPairState.value = 'working'
  localPairMessage.value = ''
  const result = await agentLocal.pair(item.pairingCode)
  if (mine !== pairSequence) return
  if (!result.ok) {
    localPairState.value = 'idle'
    localPairMessage.value = result.message
    return
  }
  localAgent.value = result.status
  localPairState.value = 'done'
  clearTimeout(reloadTimer)
  // the agent reports in as soon as it starts; give it a moment, then show the reader online
  reloadTimer = setTimeout(() => void load(), 1500)
}
void refreshLocalAgent()
function closePairing() {
  pairingOpen.value = false
  clearInterval(pairingTimer)
  pairingTimer = undefined
}
function showPairing(item: IdCardWorkstationCreated) {
  closePairing()
  void refreshLocalAgent()
  created.value = item
  const expires = Date.parse(item.pairingExpiresAt)
  const deadline = Math.min(Date.now() + 600000, Number.isFinite(expires) ? expires : Date.now() + 600000)
  const update = () => {
    pairingSeconds.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    if (!pairingSeconds.value) closePairing()
  }
  pairingOpen.value = true
  update()
  if (pairingOpen.value) pairingTimer = setInterval(update, 1000)
}
onBeforeUnmount(() => {
  closePairing()
  clearTimeout(reloadTimer)
})
const message = ref('')
const loadError = ref('')
const loading = ref(false)
const saving = ref(false)
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')
let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  const branch = branchCode.value
  loading.value = true
  loadError.value = ''
  try {
    const rows = await idCardApi.workstations(branch || undefined)
    if (sequence !== loadSequence) return
    const added = created.value
    if (added && added.branchCode === branch && !rows.some((row) => row.id === added.id)) {
      rows.push({ id: added.id, name: added.name, branchCode: added.branchCode, online: false, status: 'Active' })
    }
    workstations.value = rows.filter((row) => row.status === 'Active')
  } catch (error) {
    if (sequence === loadSequence) loadError.value = error instanceof ApiError ? error.message : 'โหลดรายการเครื่องอ่านบัตรไม่สำเร็จ'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function add() {
  if (saving.value) return
  message.value = ''
  if (!name.value.trim() || !branchCode.value) {
    message.value = 'กรอกชื่อเครื่อง และเลือกสาขาก่อน'
    return
  }
  saving.value = true
  const branch = branchCode.value
  try {
    const added = await idCardApi.createWorkstation(name.value.trim(), branch)
    if (branchCode.value !== branch) return
    showPairing(added)
    workstations.value = [...workstations.value.filter((row) => row.id !== added.id), {
      id: added.id, name: added.name, branchCode: added.branchCode, online: false, status: 'Active',
    }]
    name.value = ''
    await load()
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'เพิ่มเครื่องอ่านบัตรไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}

async function repair(id: number) {
  try {
    showPairing(await idCardApi.newPairingCode(id))
  } catch (error) {
    message.value = error instanceof ApiError ? error.message : 'สร้างรหัสจับคู่ไม่สำเร็จ'
  }
}

const cancelTarget = ref<IdCardWorkstation | null>(null)
const cancelling = ref(false)
const cancelError = ref('')
function revoke(workstation: IdCardWorkstation) {
  cancelError.value = ''
  cancelTarget.value = workstation
}
function closeCancel() {
  if (!cancelling.value) cancelTarget.value = null
}
async function confirmCancel() {
  if (!cancelTarget.value || cancelling.value) return
  const workstation = cancelTarget.value
  cancelling.value = true
  cancelError.value = ''
  try {
    await idCardApi.revokeWorkstation(workstation.id)
    workstations.value = workstations.value.filter((row) => row.id !== workstation.id)
    if (created.value?.id === workstation.id) created.value = null
    closePairing()
    cancelTarget.value = null
    await load()
  } catch (error) {
    cancelError.value = error instanceof ApiError ? error.message : 'ยกเลิกเครื่องไม่สำเร็จ กรุณาลองอีกครั้ง'
  } finally {
    cancelling.value = false
  }
}

watch(branchCode, () => {
  cancelTarget.value = null
  closePairing()
  workstations.value = []
  created.value = null
  message.value = ''
  void load()
}, { immediate: true })
</script>

<template>
  <div :class="embedded ? 'card-reader-panel' : 'card-reader-page'">
    <StaffHeader v-if="!embedded" />
    <div class="card-reader-content">
      <section class="card-reader-heading">
        <div>
          <h1>เครื่องอ่านบัตรประชาชน</h1>
          <p>เพิ่มเครื่องอ่านบัตรของสาขา แล้วนำรหัสจับคู่ไปใส่ในโปรแกรมตัวกลางที่เครื่องหน้าเคาน์เตอร์</p>
          <p class="branch-context">สาขา: {{ selectedManagerBranch()?.name || 'ยังไม่ได้เลือกสาขา' }}</p>
          <p v-if="localAgent" class="local-agent-note">
            {{ localAgent.paired
              ? `พบโปรแกรมตัวกลางบนเครื่องนี้ จับคู่กับ “${localAgent.workstationName ?? 'เครื่องอ่านบัตร'}” แล้ว`
              : 'พบโปรแกรมตัวกลางบนเครื่องนี้ ยังไม่ได้จับคู่ เพิ่มเครื่องแล้วกดจับคู่ได้ทันที' }}
          </p>
        </div>
      </section>

      <form class="card-reader-add" @submit.prevent="add">
        <label>
          ชื่อเครื่องใหม่
          <input v-model="name" name="name" maxlength="100" placeholder="เช่น เคาน์เตอร์ 1" autocomplete="off" />
        </label>
        <button type="submit" class="primary" :disabled="saving">{{ saving ? 'กำลังเพิ่ม…' : '+ เพิ่มเครื่อง' }}</button>
      </form>
      <p v-if="message" role="alert" class="required-error">{{ message }}</p>
      <p v-if="loadError" role="alert" class="required-error">{{ loadError }} <button type="button" @click="load">ลองโหลดรายการอีกครั้ง</button></p>

      <Teleport to="body">
        <div v-if="cancelTarget" class="pairing-backdrop" @click.self="closeCancel">
          <section class="pairing-dialog cancel-reader-dialog" v-modal-focus="{ close: closeCancel, busy: cancelling }" role="alertdialog" aria-modal="true" aria-labelledby="cancel-reader-title" aria-describedby="cancel-reader-description">
            <h2 id="cancel-reader-title">ยืนยันยกเลิกเครื่องอ่านบัตร?</h2>
            <p id="cancel-reader-description">เครื่อง “{{ cancelTarget.name }}” จะไม่สามารถใช้อ่านบัตรผ่านระบบได้</p>
            <p v-if="cancelError" class="cancel-reader-error" role="alert">{{ cancelError }}</p>
            <div class="cancel-reader-actions">
              <button type="button" :disabled="cancelling" @click="closeCancel">ยกเลิก</button>
              <button type="button" class="confirm-cancel-reader" :disabled="cancelling" @click="confirmCancel">{{ cancelling ? 'กำลังยกเลิก…' : 'ตกลง' }}</button>
            </div>
          </section>
        </div>
        <div v-if="pairingOpen && created" class="pairing-backdrop" @click.self="closePairing">
          <section class="pairing-dialog" v-modal-focus="{ close: closePairing, busy: false }" role="dialog" aria-modal="true" aria-labelledby="pairing-title">
            <header><h2 id="pairing-title">รหัสจับคู่</h2><button type="button" aria-label="ปิดรหัสจับคู่" @click="closePairing">×</button></header>
            <p>{{ created.name }}</p>
            <p>รหัสใช้ได้ครั้งเดียว · หมดอายุใน <b>{{ pairingTime }}</b> นาที</p>
            <div class="pairing-code"><strong>{{ created.pairingCode }}</strong></div>
            <div v-if="localAgent" class="pairing-local">
              <p v-if="localPairState === 'done'" role="status" class="pairing-local-ok">จับคู่โปรแกรมบนเครื่องนี้สำเร็จ</p>
              <template v-else>
                <p>
                  {{ localAgent.paired
                    ? `โปรแกรมตัวกลางบนเครื่องนี้จับคู่กับ “${localAgent.workstationName ?? 'เครื่องอ่านบัตร'}” อยู่ การจับคู่ใหม่จะแทนที่`
                    : 'พบโปรแกรมตัวกลางบนเครื่องนี้ ยังไม่ได้จับคู่' }}
                </p>
                <button type="button" class="primary pair-local" :disabled="localPairState === 'working'" @click="pairLocal">
                  {{ localPairState === 'working' ? 'กำลังจับคู่…' : 'จับคู่โปรแกรมบนเครื่องนี้' }}
                </button>
                <p class="pairing-local-hint">กดเฉพาะเมื่อเครื่องนี้คือเครื่องหน้าเคาน์เตอร์ของ “{{ created.name }}”</p>
                <p v-if="localPairMessage" role="alert" class="required-error">{{ localPairMessage }}</p>
              </template>
            </div>
          </section>
        </div>
      </Teleport>

      <div class="card-reader-table-wrap"><table class="card-reader-list">
        <thead>
          <tr>
            <th>ชื่อ</th>
            <th>สถานะ</th>
            <th>การจัดการ</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in workstations" :key="w.id">
            <td>{{ w.name }}</td>
            <td><span class="reader-status" :class="w.online ? 'online' : 'offline'">{{ w.online ? 'ออนไลน์' : 'ออฟไลน์' }}</span></td>
            <td>
              <div v-if="w.status === 'Active'" class="reader-actions">
                <button type="button" class="secondary" @click="repair(w.id)">รหัสจับคู่ใหม่</button>
                <button type="button" class="secondary revoke" @click="revoke(w)">ยกเลิก</button>
              </div>
            </td>
          </tr>
          <tr v-if="workstations.length === 0"><td colspan="3" class="reader-empty">{{ loading ? 'กำลังโหลดรายการ…' : loadError ? 'ไม่สามารถโหลดรายการได้' : 'ยังไม่มีเครื่องอ่านบัตรในสาขานี้' }}</td></tr>
        </tbody>
      </table></div>
    </div>
  </div>
</template>

<style scoped>
.pairing-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 20px; background: rgb(24 49 44 / 40%); }
.pairing-dialog { box-sizing: border-box; width: min(460px, 100%); padding: 28px; border-radius: 18px; background: #fff; color: #25372e; box-shadow: 0 20px 60px rgb(0 0 0 / 20%); }
.pairing-dialog header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.pairing-dialog h2 { margin: 0; font-size: 22px; line-height: 1.4; color: #145c59; }
.pairing-dialog header button { width: 38px; height: 38px; border: 1px solid #bdd4d0; border-radius: 9px; background: #fff; color: #187d78; font-size: 24px; cursor: pointer; }
.pairing-dialog p { color: #68776f; line-height: 1.6; margin: 14px 0 0; text-align: center; overflow-wrap: anywhere; }
.pairing-dialog:focus { outline: none; }
.pairing-dialog .pairing-code { margin: 16px 0 0; padding: 14px 18px; border: 0; text-align: center; }
.pairing-dialog .pairing-code strong { font-size: clamp(24px, 6vw, 32px); letter-spacing: 5px; }
.local-agent-note { margin: 6px 0 0; color: #187d78; }
.pairing-local { margin-top: 18px; padding-top: 14px; border-top: 1px solid #e6eae5; text-align: center; }
.pairing-local .pair-local { margin-top: 12px; min-height: 44px; padding: 10px 20px; border: 1px solid #187d78; border-radius: 9px; background: #187d78; color: #fff; font: inherit; font-weight: 600; cursor: pointer; }
.pairing-local .pair-local:disabled { opacity: 0.5; cursor: not-allowed; }
.pairing-dialog .pairing-local-hint { font-size: 14px; margin-top: 8px; }
.pairing-dialog .pairing-local-ok { color: #187d78; font-weight: 600; }
.cancel-reader-dialog { text-align: center; }
.cancel-reader-dialog h2 { color: #25372e; }
.cancel-reader-actions { display: flex; justify-content: center; gap: 12px; margin-top: 26px; }
.cancel-reader-actions button { min-width: 120px; min-height: 44px; padding: 10px 20px; border: 1px solid #a2c7c1; border-radius: 9px; background: #fff; color: #187d78; font: inherit; font-weight: 600; cursor: pointer; }
.cancel-reader-actions .confirm-cancel-reader { border-color: #187d78; background: #187d78; color: #fff; }
.cancel-reader-actions button:disabled { opacity: 0.5; cursor: not-allowed; }
.cancel-reader-dialog .cancel-reader-error { color: #b42318; }
.cancel-reader-actions button:focus-visible { outline: 2px solid #187d78; outline-offset: 3px; }
.card-reader-panel {
  min-width: 0;
  align-self: start;
  overflow: hidden;
  border: 1px solid #e6eae5;
  border-radius: 24px;
  background: #fff;
  color: #25372e;
  box-shadow: 0 16px 35px rgb(37 55 46 / 5%);
}
.card-reader-page {
  min-height: 100vh;
  background: var(--ui-workspace-background, #f8f4ed);
}
.card-reader-page > .card-reader-content {
  width: min(1420px, calc(100% - 64px));
  margin: 40px auto;
  border-radius: 24px;
  background: #fff;
  overflow: hidden;
}
.card-reader-heading {
  padding: 28px 32px;
  border-bottom: 1px solid #e7ede9;
  background: linear-gradient(120deg, #fff, #f3faf7);
}
.card-reader-heading h1 {
  margin: 0;
  color: #25372e;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.4;
}
.card-reader-heading p {
  margin: 10px 0 0;
  color: #68776f;
  line-height: 1.6;
  font-size: 14px;
}
.card-reader-heading .branch-context {
  color: #187d78;
  font-weight: 600;
}
.card-reader-add label {
  display: grid;
  flex: 1;
  max-width: 420px;
  gap: 8px;
  min-width: 0;
  color: #41594f;
  font-size: 14px;
  font-weight: 600;
}
.card-reader-add input {
  width: 100%;
  min-height: 46px;
  padding: 10px 14px;
  box-sizing: border-box;
  border: 1px solid #bfd3d0;
  border-radius: 10px;
  background: #f8faf9;
  color: #25372e;
  font: inherit;
}
.card-reader-content button {
  min-height: 42px;
  padding: 9px 16px;
  border: 1px solid #b9d1ce;
  border-radius: 10px;
  background: #fff;
  color: #187d78;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.card-reader-content button.primary {
  min-height: 46px;
  border-color: transparent;
  background: var(--ui-primary-gradient, linear-gradient(120deg, #1b9289, #147771));
  color: #fff;
  box-shadow: var(--ui-primary-shadow, 0 6px 14px rgb(24 125 120 / 16%));
}
.card-reader-content :is(input, button):focus-visible {
  outline: 2px solid #187d78;
  outline-offset: 3px;
}
.card-reader-content .required-error {
  margin: 16px 32px;
  color: #a03932;
}
.pairing-code {
  margin: 24px 32px;
  padding: 20px;
  border: 1px solid #d1e8e1;
  border-radius: 12px;
  background: #eaf6f1;
  color: #145c59;
}
.pairing-code p {
  margin: 0 0 12px;
  line-height: 1.6;
}
.card-reader-table-wrap { overflow-x: auto; }
.card-reader-list thead {
  background: #f2f8f5;
  color: #4a6961;
}
.card-reader-list tbody { background: #fff; }
.card-reader-list td:last-child {
  min-width: 235px;
}
.reader-actions { display: flex; align-items: center; justify-content: center; gap: 8px; }
.reader-status {
  display: inline-block;
  white-space: nowrap;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  background: #f0f2f1;
  color: #68776f;
}
.reader-status.online { color: #187d78; background: #def3e8; }
.reader-status.offline { color: #946c22; background: #fff3d4; }
.card-reader-list td.reader-empty { padding: 48px 20px; text-align: center; color: #68776f; }
.card-reader-add {
  display: flex;
  gap: 12px;
  align-items: end;
  margin: 0;
  padding: 24px 32px;
  border-bottom: 1px solid #e7ede9;
}
.pairing-code strong {
  font-size: 32px;
  letter-spacing: 6px;
}
.card-reader-list {
  width: 100%;
  table-layout: fixed;
  min-width: 640px;
  border-collapse: collapse;
}
.card-reader-list th:nth-child(1) { width: 40%; }
.card-reader-list th:nth-child(2) { width: 22%; }
.card-reader-list th:nth-child(3) { width: 38%; }
.card-reader-list th { background: #f4f8f6; color: #51645c; font-size: 0.78rem; font-weight: 800; }
.card-reader-list td { color: #33443a; font-size: 0.9rem; vertical-align: middle; overflow-wrap: anywhere; }
.card-reader-list tbody tr:nth-child(even) { background: #fafcfb; }
.card-reader-list tbody tr:hover { background: #f1f9f6; }
.card-reader-list td button {
  min-width: 90px;
  min-height: 38px;
  padding: 8px 18px;
  border: 1px solid var(--ui-primary, #1e807c);
  border-radius: 999px;
  background: var(--ui-primary, #1e807c);
  color: #fff;
  font-weight: 600;
  box-shadow: none;
}
.card-reader-list th,
.card-reader-list td {
  padding: 17px 22px;
  text-align: left;
  border-bottom: 1px solid #dfe7e3;
}
.card-reader-list th:last-child,
.card-reader-list td:last-child { text-align: center; }
.card-reader-list td button.revoke {
  border-color: #c44747;
  background: #c44747;
  color: #fff;
}
@media (max-width: 640px) {
  .card-reader-heading, .card-reader-add { padding: 20px; }
  .card-reader-add { flex-direction: column; align-items: stretch; }
  .pairing-code { margin: 20px; }
  .card-reader-list { min-width: 640px; }
}
</style>
