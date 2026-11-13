<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { IdCardLookup } from '@/api/client'
import IdCardInsertAnimation from './IdCardInsertAnimation.vue'
import { setPendingCard } from './pendingCard'
import { useIdCardReader } from './useIdCardReader'

const props = defineProps<{ open: boolean; branchCode?: string }>()
const emit = defineEmits<{ (event: 'close'): void }>()

type Phase = 'choose' | 'running' | 'found' | 'failed'
const router = useRouter()
const reader = useIdCardReader()
const phase = ref<Phase>('choose')
const found = ref<NonNullable<IdCardLookup['patient']> | null>(null)

const authUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null
  } catch {
    return null
  }
})
const isManager = computed(() => authUser.value?.role === 'MANAGER')
const choices = computed(() => reader.workstations.value.filter(
  (w) => w.status === 'Active' && (!props.branchCode || w.branchCode === props.branchCode),
))
const loadingReaders = ref(false)
const canRead = computed(() => !loadingReaders.value && choices.value.some(
  (w) => w.id === reader.selectedId.value && w.online,
))
const failureMessage = computed(() => /[\u0E00-\u0E7F]/.test(reader.message.value) ? reader.message.value : '')
let statusTimer: ReturnType<typeof setInterval> | undefined
let refreshing = false

async function refreshReaders() {
  if (refreshing || !props.open || phase.value !== 'choose') return
  refreshing = true
  try {
    await reader.load(props.branchCode || undefined)
  } finally {
    refreshing = false
    loadingReaders.value = false
  }
}

function stopStatusRefresh() {
  clearInterval(statusTimer)
  statusTimer = undefined
}

watch(
  () => [props.open, props.branchCode] as const,
  ([open]) => {
    stopStatusRefresh()
    if (open) {
      phase.value = 'choose'
      found.value = null
      loadingReaders.value = true
      void refreshReaders()
      statusTimer = setInterval(() => void refreshReaders(), 5000)
    } else {
      reader.cancel()
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  stopStatusRefresh()
  reader.cancel()
})

// While a card is awaited or being read the dialog cannot be dismissed; the wait ends by itself.
function close() {
  if (phase.value === 'running') return
  emit('close')
}

function manual() {
  emit('close')
  router.push('/staff/patients/new')
}

async function readCard() {
  if (!canRead.value) return
  phase.value = 'running'
  const view = await reader.read()
  if (!view) {
    phase.value = reader.state.value === 'idle' ? 'choose' : 'failed'
    return
  }
  if (view.lookup?.exists && view.lookup.patient) {
    found.value = view.lookup.patient
    phase.value = 'found'
    return
  }
  if (view.card) {
    setPendingCard(view.card)
    emit('close')
    router.push('/staff/patients/new')
    return
  }
  phase.value = 'failed'
}

function openDetails() {
  if (!found.value) return
  const id = found.value.id
  emit('close')
  router.push(`/staff/patients/${id}`)
}

function chooseReader(event: Event) {
  const id = Number((event.target as HTMLSelectElement).value)
  if (id) reader.select(id)
}
</script>

<template>
  <div v-if="open" class="register-dialog-backdrop" @click.self="close" @keydown.esc="close">
    <section
      class="register-dialog"
      :class="{ 'read-failed': phase === 'failed' }"
      role="dialog"
      aria-modal="true"
      aria-labelledby="register-dialog-title"
      tabindex="-1"
    >
      <template v-if="phase === 'choose'">
        <header>
          <h2 id="register-dialog-title">ลงทะเบียนผู้ป่วยใหม่</h2>
          <button type="button" class="dialog-close" aria-label="ปิด" @click="close">×</button>
        </header>
        <p>เลือกวิธีกรอกข้อมูลผู้ป่วย</p>
        <div class="methods">
          <button type="button" class="method method-manual" @click="manual">
            <strong>กรอกข้อมูลเอง</strong>
            <span>พิมพ์ข้อมูลผู้ป่วยในฟอร์ม</span>
          </button>
          <button type="button" class="method method-card" :disabled="!canRead" @click="readCard">
            <strong>อ่านข้อมูลจากบัตรประชาชน</strong>
            <span>เสียบบัตรที่เครื่องอ่านบัตร</span>
          </button>
        </div>
        <select
          v-if="choices.length > 1"
          :value="reader.selectedId.value ?? ''"
          aria-label="เลือกเครื่องอ่านบัตร"
          @change="chooseReader"
        >
          <option value="" disabled>เลือกเครื่องอ่านบัตร</option>
          <option v-for="w in choices" :key="w.id" :value="w.id" :disabled="!w.online">
            {{ w.name }}{{ w.online ? '' : ' (ออฟไลน์)' }}
          </option>
        </select>
        <small v-if="choices.length === 0" class="id-card-hint">
          <span>สาขานี้ยังไม่มีเครื่องอ่านบัตร</span>
          <RouterLink
            v-if="isManager"
            class="id-card-settings-link"
            to="/staff/settings/card-readers"
            @click="emit('close')"
          >
            จัดการเครื่องอ่านบัตร
          </RouterLink>
        </small>
        <small v-else-if="!choices.some((w) => w.online)" class="id-card-hint">
          เครื่องอ่านบัตรยังไม่เชื่อมต่อ
        </small>
      </template>

      <template v-else-if="phase === 'running'">
        <div class="running" aria-live="polite">
          <template v-if="reader.state.value === 'reading'">
            <div class="spinner" aria-hidden="true"></div>
            <h2 id="register-dialog-title">ระบบกำลังอ่านข้อมูล</h2>
            <p>กรุณาอย่าดึงบัตรออกจนกว่าจะอ่านเสร็จ</p>
          </template>
          <template v-else>
            <IdCardInsertAnimation />
            <h2 id="register-dialog-title">กรุณาเสียบบัตรประชาชน</h2>
          </template>
        </div>
      </template>

      <template v-else-if="phase === 'found' && found">
        <h2 id="register-dialog-title">พบผู้ป่วยในระบบแล้ว</h2>
        <p class="found-patient">
          <strong>{{ found.hn }}</strong>
          <span>{{ found.fullName }}</span>
        </p>
        <div class="actions">
          <button type="button" class="found-details" @click="openDetails">ดูรายละเอียด</button>
          <button type="button" class="secondary" @click="close">ปิด</button>
        </div>
      </template>

      <template v-else>
        <span class="read-failed-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18" /></svg>
        </span>
        <h2 id="register-dialog-title">อ่านบัตรไม่สำเร็จ</h2>
        <p v-if="failureMessage" role="alert">{{ failureMessage }}</p>
        <div class="actions">
          <button type="button" class="secondary method-manual-after-failure" @click="manual">
            กรอกข้อมูลเอง
          </button>
          <button type="button" class="primary retry" @click="readCard">ลองอีกครั้ง</button>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.register-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  background: rgb(15 21 20 / 0.45);
}
.register-dialog {
  box-sizing: border-box;
  width: min(520px, calc(100vw - 32px));
  padding: 24px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 50px rgb(0 0 0 / 0.25);
  text-align: center;
}
.register-dialog.read-failed {
  width: min(440px, calc(100vw - 32px));
  padding: 32px;
  border: 1px solid #e1eae6;
  border-radius: 20px;
  color: #25372e;
}
.read-failed-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 18px;
  border-radius: 50%;
  background: #fdecec;
  color: #c44747;
}
.read-failed-icon svg { width: 28px; height: 28px; }
.read-failed h2 {
  margin: 0;
  color: #25372e;
  font-size: 23px;
  font-weight: 700;
  line-height: 1.4;
}
.read-failed p {
  margin: 12px 0 26px;
  color: #68776f;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.register-dialog.read-failed .actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 24px 0 0;
  padding: 0;
  border: 0;
  align-items: stretch;
}
.register-dialog.read-failed .actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
  height: 48px;
  min-width: 0;
  min-height: 48px;
  margin: 0;
  padding: 10px 14px;
  border: 1px solid #98c2bd;
  border-radius: 9px;
  font: inherit;
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
  cursor: pointer;
}
.register-dialog.read-failed .actions .secondary {
  background: #fff;
  color: #187d78;
  box-shadow: none;
}
.register-dialog.read-failed .actions .primary {
  border-color: #187d78;
  background: var(--ui-primary-gradient, #187d78);
  color: #fff;
  box-shadow: 0 4px 10px rgb(24 125 120 / 15%);
}
.register-dialog.read-failed button:focus-visible {
  outline: 2px solid #187d78;
  outline-offset: 3px;
}
@media (max-width: 420px) {
  .register-dialog.read-failed { padding: 24px 20px; }
  .register-dialog.read-failed .actions { grid-template-columns: 1fr; }
}
.register-dialog header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dialog-close {
  border: 0;
  background: transparent;
  font-size: 24px;
  cursor: pointer;
}
.methods {
  display: grid;
  gap: 12px;
  margin: 16px 0;
}
.method {
  display: grid;
  gap: 4px;
  padding: 16px;
  border: 1px solid #cfe0da;
  border-radius: 12px;
  background: #f6fbf9;
  font: inherit;
  cursor: pointer;
}
.register-dialog .method-card:disabled {
  border-color: #e3e8e5;
  background: #f3f5f4;
  color: #8a9690;
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
}
.running {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 12px 0;
}
.spinner {
  width: 56px;
  height: 56px;
  border: 6px solid #cfe9dc;
  border-top-color: #1f9d55;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 3s;
  }
}
.found-patient {
  display: grid;
  gap: 4px;
  margin: 12px 0 20px;
  font-size: 18px;
}
.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.found-details {
  padding: 10px 22px;
  border: 0;
  border-radius: 8px;
  background: #1f9d55;
  color: #fff;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.id-card-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: center;
  gap: 4px 8px;
  color: #6b7d76;
  line-height: 1.6;
}
.id-card-settings-link {
  color: var(--ui-primary, #187d78);
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.id-card-settings-link:focus-visible {
  outline: 2px solid var(--ui-primary, #187d78);
  outline-offset: 3px;
}
</style>
