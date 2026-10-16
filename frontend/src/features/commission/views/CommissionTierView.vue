<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { clinicToday } from '@/utils/clinicDate'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'

type Tier = { order: number; min: number; max: number | null; rate: number }
const tiers = reactive<Tier[]>([
  { order: 1, min: 0, max: 59999.99, rate: 5 },
  { order: 2, min: 60000, max: 69999.99, rate: 6 },
  { order: 3, min: 70000, max: 79999.99, rate: 7 },
  { order: 4, min: 80000, max: 89999.99, rate: 8 },
  { order: 5, min: 90000, max: null, rate: 10 },
])
const editingIndex = ref(-1)
const editorOpen = ref(false)
const editorType = ref<'tier' | 'scheme'>('tier')
const error = ref('')
const scheme = reactive({
  id: 0,
  name: 'Commission Scheme V1',
  version: '1',
  effectiveFrom: '2026-01-01',
  effectiveTo: null as string | null,
})
const form = reactive({ min: 0, max: '' as number | '', rate: 5 })
// New tiers are a new cycle's rules: they start on the first day of the month after the current one.
function nextCycleStart() {
  const [year, month] = clinicToday().split('-').map(Number)
  return new Date(Date.UTC(year!, month!, 1)).toISOString().slice(0, 10)
}
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'long', year: 'numeric' }).format(
    new Date(`${value}T12:00:00`),
  )
// The Manager picks only the month a version starts; it begins on the 1st. Its end is the system's: the day before the
// next version starts, and none for the latest one.
const monthOf = (date: string) => date.slice(0, 7)
const schemeForm = reactive({
  name: 'Commission Scheme',
  version: '2',
  fromMonth: monthOf(nextCycleStart()),
})
// rows of the new Scheme Version: the uid keeps a row's inputs when rows before it are added or removed
type EditorTier = Tier & { uid: number }
const schemeTiers = reactive<EditorTier[]>([])
let nextUid = 1
const renumber = () => schemeTiers.forEach((tier, index) => (tier.order = index + 1))
function addTier() {
  const last = schemeTiers[schemeTiers.length - 1]
  // the new tier continues the ranges: an unbounded last tier is closed one satang below the new one
  let min = 0
  if (last) {
    if (last.max === null) {
      min = Math.round((last.min + 10000) * 100) / 100
      last.max = Math.round((min - 0.01) * 100) / 100
    } else min = Math.round((last.max + 0.01) * 100) / 100
  }
  schemeTiers.push({ uid: nextUid++, order: 0, min, max: null, rate: last?.rate ?? 5 })
  renumber()
}
function removeTier(index: number) {
  if (schemeTiers.length <= 1) return
  schemeTiers.splice(index, 1)
  // the top tier has no ceiling: whichever tier is last now covers every higher sale
  const last = schemeTiers[schemeTiers.length - 1]
  if (last) last.max = null
  renumber()
}
const loading = ref(false)
const schemeLocked = computed(() => new Date(`${scheme.effectiveFrom}T00:00:00`) <= new Date())
// A version that has not started yet is edited in place: saving it replaces its tiers and dates, no new version is made.
const editingVersion = computed(() => scheme.id > 0 && !schemeLocked.value)
function openEditor(index = -1) {
  if (schemeLocked.value) return
  editorType.value = 'tier'
  editingIndex.value = index
  error.value = ''
  const tier = tiers[index]
  Object.assign(
    form,
    tier
      ? { min: tier.min, max: tier.max ?? '', rate: tier.rate }
      : { min: 0, max: '', rate: 5 },
  )
  editorOpen.value = true
}
function openSchemeEditor() {
  editorType.value = 'scheme'
  error.value = ''
  Object.assign(
    schemeForm,
    editingVersion.value
      ? {
          name: scheme.name,
          version: String(scheme.version),
          fromMonth: monthOf(scheme.effectiveFrom),
        }
      : {
          name: scheme.name,
          version: String(Number(scheme.version || 0) + 1),
          fromMonth: monthOf(nextCycleStart()),
        },
  )
  schemeTiers.splice(0, schemeTiers.length, ...tiers.map((tier) => ({ ...tier, uid: nextUid++ })))
  editorOpen.value = true
}
async function saveSchemeVersion() {
  error.value = ''
  if (!schemeForm.name.trim() || !schemeForm.version.trim() || !schemeForm.fromMonth) {
    error.value = 'กรุณากรอกชื่อ Version และเดือนที่เริ่มมีผล'
    return
  }
  try {
    const ordered: Tier[] = schemeTiers
      .slice()
      .sort((a, b) => a.min - b.min)
      .map(({ order, min, max, rate }) => ({ order, min, max, rate }))
    const invalidTier = ordered.some(
      (tier, index) =>
        tier.min < 0 ||
        tier.rate <= 0 ||
        (tier.max !== null && tier.min > tier.max) ||
        // two-decimal ranges: the next tier starts exactly one satang above the previous maximum
        (index > 0 &&
          ordered[index - 1]!.max !== null &&
          Math.round((tier.min - ordered[index - 1]!.max!) * 100) !== 1),
    )
    if (invalidTier) {
      error.value = 'ตรวจช่วงยอดขายและอัตราของ Tier ให้ต่อเนื่องกัน'
      return
    }
    const body = {
      name: schemeForm.name,
      version: schemeForm.version,
      effectiveFrom: `${schemeForm.fromMonth}-01`,
      tiers: ordered.map((t, index) => ({
        minimum: t.min,
        maximum: t.max,
        rate: t.rate / 100,
        order: index + 1,
      })),
    }
    const created = (editingVersion.value
      ? await commissionApi.updateScheme(scheme.id, body)
      : await commissionApi.createScheme(body)) as {
      id: number
      name: string
      version: string
      effectiveFrom: string
      effectiveTo: string | null
    }
    tiers.splice(0, tiers.length, ...ordered.map((tier) => ({ ...tier })))
    Object.assign(scheme, created)
    editorOpen.value = false
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'บันทึกกฎคอมมิชชันไม่สำเร็จ'
  }
}
async function saveTier() {
  error.value = ''
  const maximum = form.max === '' ? null : Number(form.max)
  if (form.min < 0 || form.rate < 0 || (maximum !== null && form.min > maximum)) {
    error.value = 'ตรวจสอบยอดขายขั้นต่ำ ยอดขายสูงสุด และอัตราคอมมิชชันอีกครั้ง'
    return
  }
  const overlaps = tiers.some((tier, index) => {
    if (index === editingIndex.value) return false
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
  }
  const nextTiers = tiers.map((tier) => ({ ...tier }))
  if (editingIndex.value >= 0) nextTiers[editingIndex.value] = record
  else nextTiers.push(record)
  try {
    // only a version that has not started has editable tiers, so this changes that version itself
    const created = (await commissionApi.updateScheme(scheme.id, {
      name: scheme.name,
      effectiveFrom: scheme.effectiveFrom,
      tiers: nextTiers
        .slice()
        .sort((a, b) => a.min - b.min)
        .map((t, index) => ({
          minimum: t.min,
          maximum: t.max,
          rate: t.rate / 100,
          order: index + 1,
        })),
    })) as { id: number; version: string | number; effectiveFrom: string }
    Object.assign(scheme, {
      id: created.id,
      version: String(created.version),
      effectiveFrom: created.effectiveFrom,
      effectiveTo: scheme.effectiveTo,
    })
    tiers.splice(0, tiers.length, ...nextTiers)
    editorOpen.value = false
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'บันทึก Tier ไม่สำเร็จ'
  }
}
onMounted(async () => {
  loading.value = true
  try {
    const schemes = (await commissionApi.schemes()) as Array<{
      id: number
      name: string
      version: string
      effectiveFrom: string
      effectiveTo: string | null
    }>
    const current = schemes[0]
    if (current) {
      Object.assign(scheme, current)
      const backendTiers = (await commissionApi.tiers(current.id)) as Array<{
        minimum: number
        maximum: number | null
        rate: number
        order: number
      }>
      {
        tiers.splice(
          0,
          tiers.length,
          ...backendTiers.map((tier) => ({
            order: tier.order,
            min: tier.minimum,
            max: tier.maximum,
            rate: Math.round(tier.rate * 10000) / 100,
          })),
        )
      }
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดเกณฑ์คอมมิชชันไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <section class="commission-title">
        <h1>ตั้งค่า Commission Tier</h1>
        <p>กำหนดช่วงยอดขาย อัตราค่าคอมมิชชัน และวันที่เริ่มมีผล</p>
      </section>
      <CommissionTabs active="tiers" />
      <section class="commission-card commission-page-panel">
        <header>
          <div>
            <h2>{{ scheme.name }}</h2>
            <p class="muted-copy">
              มีผลตั้งแต่ {{ thaiDate(scheme.effectiveFrom) }}{{
                scheme.effectiveTo ? ` ถึง ${thaiDate(scheme.effectiveTo)}` : ' เป็นต้นไป'
              }}
            </p>
          </div>
          <button class="commission-primary" @click="openSchemeEditor">
            {{ editingVersion ? `แก้ไขเวอร์ชันที่รอเริ่มใช้ V${scheme.version}` : 'แก้กฎคอมมิชชัน' }}
          </button>
        </header>
        <aside class="scheme-cycle-notice" role="note">
          <b>การแก้กฎคอมมิชชันบันทึกเป็นเวอร์ชันใหม่</b>
          <span
            >ต้องปิดยอดคอมมิชชันของรอบปัจจุบันก่อน แล้วเวอร์ชันใหม่จะเริ่มใช้ตั้งแต่ต้นรอบถัดไป (วันที่ 1
            ของเดือน) ส่วนคอร์สที่ปิดยอดไปแล้วยังใช้เกณฑ์เดิม มีเวอร์ชันที่รอเริ่มใช้ได้ครั้งละหนึ่งเวอร์ชัน</span
          >
        </aside>
        <aside v-if="editingVersion" class="scheme-cycle-notice" role="note">
          <b>เวอร์ชันนี้ยังไม่เริ่มใช้</b>
          <span>แก้ไข Tier และเดือนที่เริ่มมีผลได้เลย บันทึกแล้วไม่สร้างเวอร์ชันใหม่</span>
        </aside>
        <aside v-if="schemeLocked" class="scheme-lock-notice">
          <b>Version นี้เริ่มมีผลแล้ว</b
          ><span
            >ไม่สามารถแก้ไข Tier ของเวอร์ชันนี้โดยตรงได้ หากต้องการปรับเกณฑ์ ให้กด “แก้กฎคอมมิชชัน”</span
          >
        </aside>
        <p v-if="error" class="validation-error">{{ error }}</p>
        <div class="commission-table-wrap">
          <table class="commission-table">
            <thead>
              <tr>
                <th>ลำดับ</th>
                <th>ยอดขายขั้นต่ำ</th>
                <th>ยอดขายสูงสุด</th>
                <th>อัตราคอมมิชชัน</th>
                <th v-if="!schemeLocked"></th>
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
                <td v-if="!schemeLocked">
                  <button class="commission-secondary" @click="openEditor(index)">แก้ไข</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <aside class="tier-note">
          <b>ระบบตรวจสอบก่อนบันทึก</b
          ><span
            >ห้ามช่วงยอดขั้นต่ำ–สูงสุดซ้อนกันระหว่าง Tier · อัตราต้องเป็นค่าบวก ·
            วันเริ่มมีผลต้องไม่ทับ Version เดิม</span
          >
        </aside>
      </section>
    </main>
    <Teleport to="body">
      <div v-if="editorOpen" class="tier-drawer-backdrop" @click.self="editorOpen = false">
        <aside class="tier-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>
                {{
                  editorType === 'scheme'
                    ? editingVersion
                      ? `แก้ไขเวอร์ชันที่รอเริ่มใช้ V${scheme.version}`
                      : 'แก้กฎคอมมิชชัน'
                    : `แก้ไข Tier ${editingIndex + 1}`
                }}
              </h2>
              <p>
                {{
                  editorType === 'scheme'
                    ? editingVersion
                      ? 'แก้ไขเกณฑ์ที่ยังไม่เริ่มใช้ บันทึกแล้วไม่สร้างเวอร์ชันใหม่'
                      : 'โหลดกฎปัจจุบันมาให้แก้ บันทึกเป็นเวอร์ชันใหม่ที่เริ่มรอบเดือนถัดไป'
                    : 'ปรับช่วงยอดขายและอัตราคอมมิชชัน'
                }}
              </p>
            </div>
            <button class="drawer-close" @click="editorOpen = false">×</button>
          </header>
          <div v-if="editorType === 'scheme'" class="drawer-form">
            <label>ชื่อ Scheme<input v-model="schemeForm.name" /></label
            ><label>เวอร์ชัน (ระบบกำหนด)<input :value="schemeForm.version" disabled /></label
            ><label
              >เดือนที่เริ่มมีผล (เริ่มวันที่ 1 · วันสิ้นสุดระบบกำหนดให้)<input
                v-model="schemeForm.fromMonth"
                type="month"
                :min="monthOf(nextCycleStart())"
            /></label>
            <section class="scheme-tier-editor">
              <header>
                <b>{{ editingVersion ? `Tier ของเวอร์ชัน V${scheme.version}` : 'Tier ของเวอร์ชันใหม่' }}</b
                ><small>{{ editingVersion ? 'แก้ไขแล้วบันทึกทับเวอร์ชันนี้' : 'โหลดจากกฎปัจจุบัน' }}</small>
              </header>
              <article v-for="(tier, index) in schemeTiers" :key="tier.uid">
                <b>Tier {{ tier.order }}</b
                ><label
                  >ขั้นต่ำ<input
                    v-model.number="tier.min"
                    type="number"
                    min="0"
                    step="0.01" /></label
                ><label
                  >สูงสุด<input
                    :value="tier.max ?? ''"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="ไม่จำกัด"
                    @input="
                      tier.max =
                        ($event.target as HTMLInputElement).value === ''
                          ? null
                          : Number(($event.target as HTMLInputElement).value)
                    " /></label
                ><label
                  >อัตรา (%)<input v-model.number="tier.rate" type="number" min="0.01" step="0.01"
                /></label>
                <button
                  type="button"
                  class="tier-remove"
                  :disabled="schemeTiers.length <= 1"
                  :aria-label="`ลบ Tier ${tier.order}`"
                  @click="removeTier(index)"
                >
                  ลบ
                </button>
              </article>
              <button type="button" class="commission-secondary tier-add" @click="addTier">
                + เพิ่ม Tier
              </button>
            </section>
          </div>
          <div v-else class="drawer-form">
            <label
              >ยอดขายขั้นต่ำ<input
                v-model.number="form.min"
                type="number"
                min="0"
                step="0.01" /></label
            ><label
              >ยอดขายสูงสุด<input
                v-model.number="form.max"
                type="number"
                min="0"
                step="0.01"
                placeholder="ไม่จำกัด" /></label
            ><label
              >อัตราคอมมิชชัน (%)<input
                v-model.number="form.rate"
                type="number"
                min="0"
                step="0.01" /></label
            >
          </div>
          <p v-if="error" class="drawer-error">{{ error }}</p>
          <footer>
            <button class="commission-secondary" @click="editorOpen = false">ยกเลิก</button
            ><button
              class="commission-primary"
              @click="editorType === 'scheme' ? saveSchemeVersion() : saveTier()"
            >
              บันทึก
            </button>
          </footer>
        </aside>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.scheme-cycle-notice {
  display: grid;
  gap: 4px;
  margin: 12px 0;
  padding: 10px 14px;
  border: 1px solid #cfe3e1;
  border-radius: 10px;
  background: #f3f9f8;
  color: #25524e;
}
@import '../../staff/styles/staff.css';
@import '../styles/commission-workspace.css';
@import '../styles/commission-manager.css';
.commission-page-panel {
  padding: 0 24px 34px;
}
.commission-page-panel > header {
  padding-left: 0;
  padding-right: 0;
}
.tier-note {
  display: grid;
  gap: 8px;
  margin-top: 24px;
  padding: 18px;
  border-radius: 10px;
  background: #e6f0ff;
  color: #24558c;
}
.tier-note span {
  font-size: 0.9rem;
}
.tier-drawer-backdrop {
  position: fixed;
  z-index: 9999;
  inset: 0;
  background: #26342d80;
}
.tier-drawer {
  position: absolute;
  top: 0;
  right: 0;
  width: min(470px, 100%);
  height: 100dvh;
  box-sizing: border-box;
  padding: 30px;
  background: #fff;
  box-shadow: -16px 0 40px #0003;
  overflow-y: auto;
  animation: slide-in 0.22s ease-out;
}
.tier-drawer > header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 28px;
}
.tier-drawer h2 {
  margin: 0;
  color: #25372e;
  font-size: 1.65rem;
}
.tier-drawer header p {
  margin: 7px 0 0;
  color: #667a72;
}
.drawer-close {
  border: 0;
  background: none;
  color: #667a72;
  font-size: 2rem;
  cursor: pointer;
}
.drawer-form {
  display: grid;
  gap: 18px;
}
.drawer-form label {
  display: grid;
  gap: 8px;
  color: #52635b;
}
.drawer-form input,
.drawer-form select {
  min-height: 48px;
  box-sizing: border-box;
  padding: 10px 13px;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  background: #f7faf9;
  font: inherit;
}
.tier-drawer > footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 28px;
}
.tier-drawer > footer button {
  justify-content: center;
}
.drawer-error {
  padding: 12px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
@keyframes slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
@media (max-width: 520px) {
  .tier-drawer {
    padding: 22px;
  }
  .tier-drawer > footer {
    grid-template-columns: 1fr;
  }
}
.scheme-tier-editor {
  display: grid;
  gap: 12px;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #dfe7e3;
}
.scheme-tier-editor > header {
  display: grid;
  gap: 4px;
}
.scheme-tier-editor > header small {
  color: #718078;
}
.scheme-tier-editor article {
  display: grid;
  grid-template-columns: 58px 1fr 1fr 90px;
  gap: 8px;
  align-items: end;
  padding: 12px;
  border: 1px solid #dfe7e3;
  border-radius: 10px;
  background: #fbfcfb;
}
.scheme-tier-editor article > b {
  align-self: center;
}
.scheme-tier-editor article label {
  font-size: 0.78rem;
}
.scheme-tier-editor article input {
  min-height: 42px;
  padding: 8px;
}
@media (max-width: 520px) {
  .scheme-tier-editor article {
    grid-template-columns: 1fr 1fr;
  }
  .scheme-tier-editor article > b {
    grid-column: 1/-1;
  }
}
.tier-drawer {
  width: min(720px, 100%);
  overflow-x: hidden;
}
.scheme-lock-notice {
  display: grid;
  gap: 5px;
  margin-bottom: 20px;
  padding: 14px 16px;
  border: 1px solid #f1d28a;
  border-radius: 10px;
  background: #fff5d9;
  color: #765514;
}
.scheme-lock-notice span {
  font-size: 0.9rem;
}
.commission-table button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  background: #eef1ef;
  color: #718078;
}
.scheme-tier-editor article {
  grid-template-columns: 64px minmax(120px, 1fr) minmax(120px, 1fr) minmax(90px, 0.65fr) auto;
  gap: 12px;
  padding: 14px;
}
.tier-remove {
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid #e3b3ad;
  border-radius: 10px;
  background: #fff;
  color: #b42318;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.tier-remove:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.tier-add {
  justify-self: start;
}
.scheme-tier-editor article label {
  min-width: 0;
  max-width: 100%;
}
.scheme-tier-editor article input {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
@media (max-width: 600px) {
  .tier-drawer {
    width: 100%;
    padding: 22px;
  }
  .scheme-tier-editor article {
    grid-template-columns: 1fr 1fr;
  }
  .scheme-tier-editor article > b {
    grid-column: 1/-1;
  }
  .tier-remove {
    grid-column: 1/-1;
  }
}
</style>
