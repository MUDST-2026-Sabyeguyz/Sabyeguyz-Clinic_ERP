<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'

type Tier = { order: number; min: number; max: number | null; rate: number; active: boolean }
const tiers = reactive<Tier[]>([])
const router = useRouter()
const editingIndex = ref(-1)
const editorOpen = ref(false)
const editorType = ref<'tier' | 'scheme'>('tier')
const error = ref('')
const scheme = reactive({
  id: 0,
  name: '',
  version: '',
  effectiveFrom: '',
  active: true,
  effectiveTo: null as string | null,
})
const form = reactive({ min: 0, max: '' as number | '', rate: 5, active: true })
const schemeForm = reactive({
  name: 'Commission Scheme',
  version: '2',
  effectiveFrom: '2027-01-01',
  effectiveTo: '',
  basis: 'net',
})
const schemeTiers = reactive<Tier[]>([])
const compensationType = ref('course')
const serviceAmount = ref(0)
const loading = ref(false)
const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'long', year: 'numeric' }).format(
    new Date(`${value}T12:00:00`),
  )
const effectivePeriod = computed(() => {
  if (!scheme.effectiveFrom) return 'ยังไม่มีข้อมูลช่วงวันที่'
  if (!scheme.effectiveTo) return `${dateLabel(scheme.effectiveFrom)} เป็นต้นไป`
  const end = new Date(`${scheme.effectiveTo}T12:00:00`)
  end.setDate(end.getDate() - 1)
  return `${dateLabel(scheme.effectiveFrom)}–${end.toLocaleDateString('th-TH', { day: 'numeric', month: 'long', year: 'numeric' })}`
})
function editInSettings(index: number) {
  void router.push({
    path: '/staff/settings',
    query: { section: 'commission', schemeId: scheme.id, tier: tiers[index]?.order },
  })
}
const formStatus = computed({
  get: () => (form.active ? 'active' : 'inactive'),
  set: (value: string) => {
    form.active = value === 'active'
  },
})

function openSchemeEditor() {
  compensationType.value = 'course'
  serviceAmount.value = 0
  editorType.value = 'scheme'
  error.value = ''
  Object.assign(schemeForm, {
    name: scheme.name,
    version: String(Number(scheme.version || 0) + 1),
    effectiveFrom: scheme.effectiveTo || `${new Date().getFullYear() + 1}-01-01`,
    effectiveTo: '',
  })
  schemeTiers.splice(0, schemeTiers.length, ...tiers.map((tier) => ({ ...tier })))
  editorOpen.value = true
}
async function saveSchemeVersion() {
  error.value = ''
  if (!schemeForm.name.trim() || !schemeForm.version.trim() || !schemeForm.effectiveFrom) {
    error.value = 'กรุณากรอกชื่อ Version และวันที่เริ่มมีผล'
    return
  }
  try {
    if (scheme.effectiveTo && schemeForm.effectiveFrom < scheme.effectiveTo) {
      error.value = 'วันที่เริ่มมีผลต้องไม่ทับ Version เดิม'
      return
    }
    if (schemeForm.effectiveTo && schemeForm.effectiveTo < schemeForm.effectiveFrom) {
      error.value = 'วันที่สิ้นสุดต้องไม่ก่อนวันที่เริ่มมีผล'
      return
    }
    if (compensationType.value === 'service') {
      if (!Number.isFinite(serviceAmount.value) || serviceAmount.value <= 0) {
        error.value = 'กรุณาระบุค่าตอบแทนต่อครั้งมากกว่า 0'
        return
      }
      error.value = 'ระบบยังไม่รองรับการบันทึกบริการรายครั้งบนเซิร์ฟเวอร์'
      return
    }
    const activeTiers = schemeTiers.filter((t) => t.active).sort((a, b) => a.min - b.min)
    const invalidTier = activeTiers.some(
      (tier, index) =>
        tier.min < 0 ||
        tier.rate <= 0 ||
        (tier.max !== null && tier.min > tier.max) ||
        (index > 0 &&
          (activeTiers[index - 1]!.max === null ||
            Math.abs(tier.min - activeTiers[index - 1]!.max! - 0.01) > 0.001)),
    )
    if (
      !activeTiers.length ||
      activeTiers[0]!.min !== 0 ||
      activeTiers.some((t) => t.rate > 100) ||
      invalidTier
    ) {
      error.value = 'ตรวจช่วงยอดขายและอัตราของ Tier ให้ต่อเนื่องกัน'
      return
    }
    // The API stores an exclusive end date; the date picker shows the last active day.
    const endDate = schemeForm.effectiveTo ? new Date(`${schemeForm.effectiveTo}T12:00:00`) : null
    if (endDate) endDate.setDate(endDate.getDate() + 1)
    const effectiveTo = endDate
      ? `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}-${String(endDate.getDate()).padStart(2, '0')}`
      : null
    const created = (await commissionApi.createScheme({
      name: schemeForm.name,
      version: schemeForm.version,
      effectiveFrom: schemeForm.effectiveFrom,
      effectiveTo,
      tiers: activeTiers.map((t, index) => ({
        minimum: t.min,
        maximum: t.max,
        rate: t.rate / 100,
        order: index + 1,
      })),
    })) as {
      id: number
      name: string
      version: string
      effectiveFrom: string
      effectiveTo: string | null
    }
    Object.assign(scheme, created)
    tiers.splice(
      0,
      tiers.length,
      ...activeTiers.map((tier, index) => ({ ...tier, order: index + 1 })),
    )
    editorOpen.value = false
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'เพิ่ม Scheme Version ไม่สำเร็จ'
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
  try {
    await commissionApi.updateScheme(scheme.id, {
      name: scheme.name,
      version: scheme.version,
      effectiveFrom: scheme.effectiveFrom,
      effectiveTo: scheme.effectiveTo,
      tiers: tiers
        .filter((t) => t.active)
        .sort((a, b) => a.min - b.min)
        .map((t, index) => ({
          minimum: t.min,
          maximum: t.max,
          rate: t.rate / 100,
          order: index + 1,
        })),
    })
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
      if (backendTiers.length) {
        tiers.splice(
          0,
          tiers.length,
          ...backendTiers.map((tier) => ({
            order: tier.order,
            min: tier.minimum,
            max: tier.maximum,
            rate: tier.rate * 100,
            active: true,
          })),
        )
      }
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดข้อมูล Tier ไม่สำเร็จ'
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
            <h2 id="tier-editor-title">{{ scheme.name || 'Commission Scheme' }}</h2>
            <p class="muted-copy">
              มีผล {{ effectivePeriod }} · สถานะ {{ scheme.active ? 'ใช้งานอยู่' : 'ปิดใช้งาน' }}
            </p>
          </div>
        </header>
        <p v-if="error" class="validation-error">{{ error }}</p>
        <p v-if="!loading && !tiers.length && !error" class="muted-copy">
          ยังไม่มี Scheme และ Tier
        </p>
        <div class="commission-table-wrap">
          <table class="commission-table">
            <thead>
              <tr>
                <th>Tier</th>
                <th>ยอดขายขั้นต่ำ</th>
                <th>ยอดขายสูงสุด</th>
                <th>อัตรา</th>
                <th>สถานะ</th>
                <th><span class="sr-only">การจัดการ</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="6">กำลังโหลดข้อมูล Tier...</td>
              </tr>
              <tr v-else-if="!tiers.length">
                <td colspan="6">ยังไม่มีข้อมูล Tier</td>
              </tr>
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
                  <span class="tier-status" :class="{ void: !tier.active }">{{
                    tier.active ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td>
                  <button class="commission-secondary" @click="editInSettings(index)">แก้ไข</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <aside class="tier-note">
          <b>ระบบตรวจสอบก่อนบันทึก</b
          ><span
            >ห้ามช่วงซ้อนทับ · ห้ามมีช่องว่างระหว่าง Tier · อัตราต้องเป็นค่าบวก ·
            วันเริ่มมีผลต้องไม่ทับ Version เดิม</span
          >
        </aside>
        <footer class="scheme-actions">
          <button class="commission-primary" :disabled="loading" @click="openSchemeEditor">
            + เพิ่ม Scheme Version
          </button>
        </footer>
      </section>
    </main>
    <Teleport to="body">
      <div v-if="editorOpen" class="tier-drawer-backdrop" @click.self="editorOpen = false">
        <aside
          class="tier-drawer"
          :class="{ 'scheme-modal': editorType === 'scheme' }"
          role="dialog"
          aria-modal="true"
          aria-labelledby="tier-editor-title"
          @keydown.esc="editorOpen = false"
        >
          <header>
            <div>
              <h2 id="tier-editor-title">
                {{
                  editorType === 'scheme' ? 'เพิ่มค่าคอมมิชชัน' : `แก้ไข Tier ${editingIndex + 1}`
                }}
              </h2>
              <p>
                {{
                  editorType === 'scheme'
                    ? 'สร้าง Scheme Version และกำหนดช่วงยอดขายสำหรับการคำนวณ'
                    : 'ปรับช่วงยอดขายและอัตราคอมมิชชัน'
                }}
              </p>
            </div>
            <button class="drawer-close" @click="editorOpen = false">×</button>
          </header>
          <div v-if="editorType === 'scheme'" class="drawer-form scheme-modal-form">
            <div class="scheme-details">
              <label>ชื่อ Scheme *<input v-model="schemeForm.name" /></label>
              <label
                >วันที่เริ่มมีผล *<ThaiDatePicker
                  v-model="schemeForm.effectiveFrom"
                  numeric-month
                  show-calendar-icon
                  hide-caret
              /></label>
              <label
                >วันที่สิ้นสุด<ThaiDatePicker
                  v-model="schemeForm.effectiveTo"
                  placeholder="ไม่กำหนด"
                  numeric-month
                  show-calendar-icon
                  hide-caret
              /></label>
            </div>
            <section class="scheme-compensation">
              <b>ประเภทค่าตอบแทน</b>
              <div class="compensation-options">
                <label
                  ><input
                    v-model="compensationType"
                    type="radio"
                    name="scheme-compensation"
                    value="course"
                  />Course Commission</label
                >
                <label
                  ><input
                    v-model="compensationType"
                    type="radio"
                    name="scheme-compensation"
                    value="service"
                  />บริการรายครั้ง</label
                >
              </div>
              <div v-if="compensationType === 'course'" class="scheme-rate-details">
                <label
                  >ฐานคำนวณ<FilterDropdown
                    v-model="schemeForm.basis"
                    label="ฐานคำนวณ"
                    default-value="net"
                    default-label="ยอดขายหลังส่วนลดรายการ"
                    hide-default-option
                    :clear-on-reselect="false"
                    :options="[{ value: 'net', label: 'ยอดขายหลังส่วนลดรายการ' }]"
                /></label>
                <label>รูปแบบอัตรา<input value="เปอร์เซ็นต์ (%)" readonly /></label>
                <small>คิดจากยอดขายรายเดือนของคอร์ส</small>
              </div>
            </section>
            <section v-if="compensationType === 'service'" class="scheme-rate-details">
              <label
                >ค่าตอบแทนต่อครั้ง (บาท) *<input
                  v-model.number="serviceAmount"
                  type="number"
                  min="0.01"
                  step="0.01"
              /></label>
              <label>รูปแบบอัตรา<input value="บาท / ครั้ง" readonly /></label>
              <small>คิดตามจำนวนครั้งที่ให้บริการ</small>
            </section>
            <section v-if="compensationType === 'course'" class="scheme-tier-editor">
              <header>
                <b>กำหนด Tier</b
                ><small>ช่วงยอดขายต้องต่อเนื่อง ไม่ซ้อนทับ และ Tier สูงสุดสามารถไม่จำกัดได้</small>
              </header>
              <div class="scheme-tier-head">
                <span>ยอดขายขั้นต่ำ (บาท)</span><span>ยอดขายสูงสุด (บาท)</span
                ><span>ค่าคอมมิชชัน</span><span>สถานะ</span><span></span>
              </div>
              <article v-for="(tier, index) in schemeTiers" :key="tier.order">
                <label
                  ><span class="mobile-field-label">ยอดขายขั้นต่ำ (บาท)</span
                  ><input
                    v-model.number="tier.min"
                    type="number"
                    min="0"
                    :aria-label="`ยอดขายขั้นต่ำ Tier ${index + 1}`"
                /></label>
                <label
                  ><span class="mobile-field-label">ยอดขายสูงสุด (บาท)</span
                  ><input
                    :value="tier.max ?? ''"
                    type="number"
                    min="0"
                    placeholder="ไม่จำกัด"
                    :aria-label="`ยอดขายสูงสุด Tier ${index + 1}`"
                    @input="
                      tier.max =
                        ($event.target as HTMLInputElement).value === ''
                          ? null
                          : Number(($event.target as HTMLInputElement).value)
                    "
                /></label>
                <label class="scheme-percent"
                  ><span class="mobile-field-label">ค่าคอมมิชชัน (%)</span
                  ><input
                    v-model.number="tier.rate"
                    type="number"
                    min="0.01"
                    max="100"
                    step="0.01"
                    :aria-label="`อัตรา Tier ${index + 1}`"
                  /><span>%</span></label
                >
                <button class="scheme-tier-status" @click="tier.active = !tier.active">
                  {{ tier.active ? 'ใช้งาน' : 'ปิดใช้งาน' }}
                </button>
                <button
                  class="scheme-remove-tier"
                  :aria-label="`ลบ Tier ${index + 1}`"
                  @click="schemeTiers.splice(index, 1)"
                >
                  ×
                </button>
              </article>
              <button
                class="scheme-add-tier"
                @click="
                  schemeTiers.push({
                    order: Math.max(0, ...schemeTiers.map((t) => t.order)) + 1,
                    min:
                      (schemeTiers[schemeTiers.length - 1]?.max ??
                        schemeTiers[schemeTiers.length - 1]?.min ??
                        0) + 0.01,
                    max: null,
                    rate: 1,
                    active: true,
                  })
                "
              >
                + เพิ่ม Tier
              </button>
            </section>
            <aside v-if="compensationType === 'course'" class="scheme-calculation-rules">
              <b>กติกาการคำนวณ</b>
              <p>
                ส่วนลดรายการลดฐานค่าคอมมิชชัน
                ส่วนลดท้ายบิลลดเฉพาะยอดลูกค้าชำระและไม่ลดฐานค่าคอมมิชชัน
              </p>
            </aside>
          </div>
          <div v-else class="drawer-form">
            <label>ยอดขายขั้นต่ำ<input v-model.number="form.min" type="number" min="0" /></label
            ><label
              >ยอดขายสูงสุด<input
                v-model.number="form.max"
                type="number"
                min="0"
                placeholder="ไม่จำกัด" /></label
            ><label
              >อัตราคอมมิชชัน (%)<input
                v-model.number="form.rate"
                type="number"
                min="0"
                step="0.01" /></label
            ><label
              >สถานะ<FilterDropdown
                v-model="formStatus"
                label="สถานะ"
                default-value="active"
                default-label="ใช้งาน"
                hide-default-option
                :clear-on-reselect="false"
                :options="[
                  { value: 'active', label: 'ใช้งาน' },
                  { value: 'inactive', label: 'ปิดใช้งาน' },
                ]"
            /></label>
          </div>
          <p v-if="error" class="drawer-error">{{ error }}</p>
          <footer>
            <button class="commission-secondary" @click="editorOpen = false">ยกเลิก</button
            ><button
              class="commission-primary"
              @click="editorType === 'scheme' ? saveSchemeVersion() : saveTier()"
            >
              {{ editorType === 'scheme' ? 'บันทึก Scheme' : 'บันทึก' }}
            </button>
          </footer>
        </aside>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
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
.scheme-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
.commission-table tbody tr:nth-child(even) {
  background: #f8f4ee;
}
.commission-table .tier-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 120px;
  min-height: 34px;
  padding: 5px 10px;
  box-sizing: border-box;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  background: #ddf3e6;
  color: #17663a;
  border-radius: 17px;
}
.commission-table .tier-status.void {
  background: #f2e4e1;
  color: #a13b32;
}
.commission-table td:nth-child(4) {
  color: #176c68;
  font-size: 18px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
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
  grid-template-columns: 64px minmax(120px, 1fr) minmax(120px, 1fr) minmax(90px, 0.65fr);
  gap: 12px;
  padding: 14px;
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
}
.scheme-modal {
  top: 24px;
  left: 50%;
  right: auto;
  transform: translateX(-50%);
  width: min(1278px, calc(100% - 48px));
  height: auto;
  max-height: calc(100dvh - 48px);
  padding: 0;
  border: 1px solid #d7e0dd;
  border-radius: 16px;
  animation: none;
  color: #243832;
}
.scheme-modal > header {
  margin: 0;
  padding: 28px 32px;
  background: #eaf7f3;
  align-items: center;
}
.scheme-modal > header > div {
  position: relative;
  padding-left: 74px;
}
.scheme-modal > header > div::before {
  content: '฿';
  position: absolute;
  left: 0;
  top: 0;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: white;
  color: #218b85;
  font-size: 22px;
  font-weight: 600;
}
.scheme-modal h2 {
  font-size: 25px;
}
.scheme-modal header p {
  font-size: 13px;
  color: #718783;
}
.scheme-modal-form {
  padding: 28px 40px 40px;
  gap: 24px;
}
.scheme-details {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 28px;
}
.scheme-modal label {
  color: #243832;
  font-size: 13px;
  font-weight: 600;
  min-width: 0;
}
.scheme-modal input {
  width: 100%;
  min-width: 0;
  height: 52px;
  background: white;
  border-color: #d7e0dd;
  border-radius: 8px;
  font-weight: 400;
}
.scheme-modal :deep(.date-trigger) {
  min-height: 52px;
  background: white;
}
.scheme-compensation {
  padding: 20px;
  border: 1px solid #d7e0dd;
  border-radius: 10px;
  background: #f8fafc;
}
.compensation-options {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin: 22px 0;
}
.compensation-options label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}
.compensation-options input {
  width: 18px;
  height: 18px;
  min-height: 18px;
  padding: 0;
  accent-color: #218b85;
}
.compensation-options .unavailable-option {
  color: #718783;
}
.scheme-rate-details {
  display: grid;
  grid-template-columns: 2fr 1fr 1.3fr;
  gap: 28px;
  align-items: end;
}
.scheme-rate-details input {
  height: 40px;
  min-height: 40px;
}
.scheme-rate-details small {
  color: #718783;
  font-size: 12px;
  padding-bottom: 12px;
}
.scheme-modal .scheme-tier-editor {
  border: 0;
  padding: 0;
  margin: 0;
  gap: 14px;
}
.scheme-modal .scheme-tier-editor > header {
  gap: 8px;
  margin-bottom: 12px;
}
.scheme-modal .scheme-tier-editor > header b {
  font-size: 17px;
}
.scheme-modal .scheme-tier-editor > header small {
  font-size: 12px;
  color: #718783;
}
.scheme-modal .scheme-tier-editor article,
.scheme-tier-head {
  display: grid;
  grid-template-columns: minmax(0, 270fr) minmax(0, 270fr) minmax(0, 190fr) 110px 44px;
  gap: 30px;
  align-items: center;
}
.scheme-modal .scheme-tier-editor article {
  padding: 0;
  border: 0;
  background: white;
}
.scheme-tier-head {
  color: #718783;
  font-size: 12px;
}
.mobile-field-label {
  display: none;
}
.scheme-modal .scheme-tier-editor article input {
  height: 52px;
  padding: 12px 14px;
}
.scheme-percent {
  position: relative;
}
.scheme-percent input {
  color: #218b85;
  font-weight: 600;
  padding-right: 35px !important;
}
.scheme-percent > span:last-child {
  position: absolute;
  right: 18px;
  bottom: 16px;
  color: #218b85;
}
.scheme-tier-status {
  border: 0;
  height: 36px;
  border-radius: 18px;
  background: #eaf7f3;
  color: #218b85;
  font-size: 12px;
  font-weight: 600;
}
.scheme-remove-tier {
  height: 36px;
  border: 1px solid #d7e0dd;
  border-radius: 8px;
  background: #f4f5f5;
  color: #d64545;
  font-size: 22px;
}
.scheme-add-tier {
  justify-self: start;
  height: 46px;
  padding: 0 18px;
  border: 1px solid #d7e0dd;
  border-radius: 8px;
  background: white;
  color: #218b85;
  font-weight: 600;
  margin-top: 4px;
}
.scheme-calculation-rules {
  padding: 16px 20px;
  border-radius: 10px;
  background: #edf4ff;
  font-size: 12px;
}
.scheme-calculation-rules b {
  color: #285ead;
  font-size: 13px;
}
.scheme-calculation-rules p {
  margin: 8px 0 0;
}
.scheme-modal > footer {
  display: flex;
  justify-content: flex-end;
  padding: 26px 40px;
  margin: 0;
  border-top: 1px solid #d7e0dd;
  gap: 15px;
}
.scheme-modal > footer button {
  min-width: 130px;
  min-height: 46px;
  border-radius: 8px;
}
.scheme-modal > footer .commission-primary {
  background: #218b85;
  color: white;
}
.scheme-modal .drawer-error {
  margin: 0 40px 20px;
}
@media (max-width: 800px) {
  .scheme-modal {
    width: calc(100% - 24px);
    top: 12px;
    max-height: calc(100dvh - 24px);
  }
  .scheme-modal-form {
    padding: 24px;
  }
  .scheme-details {
    grid-template-columns: 1fr 1fr;
    gap: 18px;
  }
  .scheme-details > label:first-child {
    grid-column: 1/-1;
  }
  .scheme-rate-details {
    grid-template-columns: 1fr 1fr;
    gap: 18px;
  }
  .scheme-rate-details small {
    grid-column: 1/-1;
    padding: 0;
  }
  .scheme-modal .scheme-tier-editor article,
  .scheme-tier-head {
    gap: 10px;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 0.7fr) 80px 36px;
  }
}
@media (max-width: 520px) {
  .scheme-modal > header {
    padding: 20px;
  }
  .scheme-modal h2 {
    font-size: 20px;
  }
  .scheme-modal > header > div {
    padding-left: 0;
  }
  .scheme-modal > header > div::before {
    display: none;
  }
  .scheme-modal-form {
    padding: 20px;
  }
  .scheme-details,
  .scheme-rate-details {
    grid-template-columns: 1fr;
  }
  .scheme-tier-head {
    display: none;
  }
  .scheme-modal .scheme-tier-editor article {
    grid-template-columns: 1fr 1fr;
    padding: 14px;
    border: 1px solid #d7e0dd;
    border-radius: 8px;
  }
  .mobile-field-label {
    display: inline;
  }
  .scheme-percent > span:last-child {
    bottom: 16px;
  }
  .scheme-remove-tier {
    grid-column: 2;
    justify-self: end;
    width: 44px;
  }
  .scheme-modal > footer {
    padding: 20px;
    flex-wrap: wrap;
  }
}
</style>
