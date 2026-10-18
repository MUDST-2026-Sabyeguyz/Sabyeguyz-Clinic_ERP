<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { auditApi, type AuditEntry } from '@/api/client'

// The audit trail (Data Dictionary Audit_log): who changed what and when, with the value before and after.
const PAGE_SIZE = 20
const tableLabels: Record<string, string> = {
  commission_scheme: 'Scheme ค่าคอมมิชชัน',
  treatment_fee_rule: 'กฎค่ามือ',
  monthly_commission_closing: 'ปิดยอดรายเดือน',
  staff_monthly_payment: 'งวดจ่ายพนักงาน',
  staff_pay_adjustment: 'ใบปรับยอด',
  adjustment_application: 'การนำใบปรับยอดไปใช้',
  course: 'คอร์ส',
  course_transfer: 'โอนสิทธิ์คอร์ส',
  course_extension_request: 'คำขอต่ออายุคอร์ส',
  course_template: 'แม่แบบคอร์ส',
  refund_transaction: 'คืนเงิน',
  appointment: 'นัดหมาย',
  app_user: 'ผู้ใช้งาน',
  receipt: 'ใบเสร็จ',
  sales_transaction: 'รายการขาย',
}
const actionLabels: Record<string, string> = { INSERT: 'เพิ่ม', UPDATE: 'แก้ไข', DELETE: 'ลบ' }
const fieldLabels: Record<string, string> = {
  reason: 'เหตุผล',
  reopen_reason: 'เหตุผลที่เปิดรอบ',
  source: 'ที่มา',
  summary: 'สรุป',
  effective_from: 'เริ่มมีผล',
  effective_to: 'สิ้นสุด',
  scheme_name: 'ชื่อ Scheme',
  scheme_version: 'เวอร์ชัน',
  rule_name: 'ชื่อกฎ',
  fee_type: 'วิธีคำนวณ',
  fee_value: 'ค่ามือ',
  enabled: 'เปิดใช้งาน',
  closing_status: 'สถานะการปิดยอด',
  closing_month: 'เดือน',
  staff_id: 'พนักงาน (รหัส)',
  monthly_course_sales: 'ยอดขายคอร์สรายเดือน',
  calculated_commission_rate: 'อัตราที่คำนวณได้',
  locked_commission_rate: 'อัตราที่ล็อก',
  course_status: 'สถานะคอร์ส',
  transfer_status: 'สถานะการโอน',
  appointment_status: 'สถานะนัด',
  appointment_date: 'วันที่นัด',
  appointment_time: 'เวลานัด',
  tiers: 'Tier',
}

const filter = reactive({ tableName: '', action: '', recordId: '', from: '', to: '' })
const tables = ref<string[]>([])
const entries = ref<AuditEntry[]>([])
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
const loading = ref(false)
const error = ref('')
const openId = ref<number | null>(null)

const tableLabel = (name: string) => tableLabels[name] ?? name
const fieldLabel = (key: string) => fieldLabels[key] ?? key
const when = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    timeZone: 'Asia/Bangkok',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))

function parse(value: string | null): Record<string, unknown> | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : { value: parsed }
  } catch {
    return { summary: value }
  }
}
/** A tier row of a Scheme is shown as one line: "Tier 1: 0 – 59999.99 → 5%". */
function tierLine(tier: Record<string, unknown>) {
  const maximum =
    tier.maximum_monthly_sales === null ? 'ไม่จำกัด' : String(tier.maximum_monthly_sales)
  const rate = Math.round(Number(tier.commission_rate) * 10000) / 100
  return `Tier ${String(tier.tier_order)}: ${String(tier.minimum_monthly_sales)} – ${maximum} → ${rate}%`
}
const isTierList = (value: unknown): value is Array<Record<string, unknown>> =>
  Array.isArray(value) &&
  value.length > 0 &&
  value.every((item) => item && typeof item === 'object' && 'tier_order' in item)
/** The lines of one value cell. */
function show(value: unknown): string[] {
  if (value === null || value === undefined) return ['—']
  if (isTierList(value)) return value.map(tierLine)
  if (typeof value === 'object') return [JSON.stringify(value)]
  if (typeof value === 'boolean') return [value ? 'ใช่' : 'ไม่ใช่']
  return [String(value)]
}

/** Who made the change: the user, or for an automatic event the source it names. */
function actorOf(entry: AuditEntry) {
  if (entry.changedBy !== null) return entry.changedByName ?? `ผู้ใช้ #${entry.changedBy}`
  const source = parse(entry.newValue)?.source
  return `ระบบอัตโนมัติ${source ? ` (${String(source)})` : ''}`
}

/** One row per field of the old and new value; rows whose value changed are marked. */
function diffOf(entry: AuditEntry) {
  const before = parse(entry.oldValue) ?? {}
  const after = parse(entry.newValue) ?? {}
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].map((key) => ({
    key,
    before: before[key],
    after: after[key],
    changed: JSON.stringify(before[key]) !== JSON.stringify(after[key]),
  }))
}

async function load(next = 0) {
  loading.value = true
  error.value = ''
  openId.value = null
  try {
    const result = await auditApi.list(next, PAGE_SIZE, {
      tableName: filter.tableName,
      action: filter.action,
      recordId: filter.recordId.trim(),
      from: filter.from,
      to: filter.to,
    })
    entries.value = result.items
    page.value = result.page
    totalPages.value = result.totalPages
    totalItems.value = result.totalItems
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดประวัติการแก้ไขไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
function clear() {
  Object.assign(filter, { tableName: '', action: '', recordId: '', from: '', to: '' })
  void load(0)
}
onMounted(async () => {
  try {
    tables.value = await auditApi.tables()
  } catch {
    // the filter then lists only the tables this page knows
    tables.value = Object.keys(tableLabels)
  }
  await load(0)
})
</script>

<template>
  <div class="audit-page">
    <StaffHeader />
    <main class="audit-main">
      <RouterLink class="audit-back" to="/staff/settings">← กลับการตั้งค่า</RouterLink>
      <header class="audit-title">
        <h1>ประวัติการแก้ไขระบบ</h1>
        <p>ใครแก้อะไร เมื่อไร พร้อมค่าก่อนและหลังการแก้ไข (ดูได้อย่างเดียว)</p>
      </header>

      <form class="audit-filter" @submit.prevent="load(0)">
        <label
          >ตาราง<FilterDropdown
            v-model="filter.tableName"
            label="ตาราง"
            default-label="ทั้งหมด"
            :options="tables.map((name) => ({ value: name, label: tableLabel(name) }))"
            :clear-on-reselect="false"
        /></label>
        <label
          >การกระทำ<FilterDropdown
            v-model="filter.action"
            label="การกระทำ"
            default-label="ทั้งหมด"
            :options="Object.entries(actionLabels).map(([value, label]) => ({ value, label }))"
            :clear-on-reselect="false"
        /></label>
        <label>รหัสรายการ<input v-model="filter.recordId" placeholder="เช่น 3" /></label>
        <label
          >ตั้งแต่วันที่<ThaiDatePicker
            v-model="filter.from"
            numeric-month
            show-calendar-icon
            hide-caret
        /></label>
        <label
          >ถึงวันที่<ThaiDatePicker v-model="filter.to" numeric-month show-calendar-icon hide-caret
        /></label>
        <div class="audit-buttons">
          <button type="submit" class="audit-primary">ค้นหา</button>
          <button type="button" class="audit-secondary" @click="clear">ล้างตัวกรอง</button>
        </div>
      </form>

      <p v-if="error" class="audit-error" role="alert">{{ error }}</p>
      <p v-else-if="loading">กำลังโหลด...</p>
      <p v-else-if="entries.length === 0" class="audit-empty">ไม่พบประวัติการแก้ไขตามตัวกรองนี้</p>

      <section v-else class="audit-card">
        <p class="audit-count">ทั้งหมด {{ totalItems }} รายการ</p>
        <div class="audit-scroll">
          <table class="audit-table">
            <thead>
              <tr>
                <th>วันเวลา</th>
                <th>ผู้ทำ</th>
                <th>ตาราง</th>
                <th>รหัสรายการ</th>
                <th>การกระทำ</th>
                <th>รายละเอียด</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="entry in entries" :key="entry.id">
                <tr :class="{ picked: openId === entry.id }">
                  <td>{{ when(entry.changedAt) }}</td>
                  <td>{{ actorOf(entry) }}</td>
                  <td>{{ tableLabel(entry.tableName) }}</td>
                  <td>{{ entry.recordId }}</td>
                  <td>
                    <span class="audit-action" :class="entry.action.toLowerCase()">{{
                      actionLabels[entry.action] ?? entry.action
                    }}</span>
                  </td>
                  <td>
                    <button
                      type="button"
                      class="audit-link"
                      :aria-expanded="openId === entry.id"
                      @click="openId = openId === entry.id ? null : entry.id"
                    >
                      {{ openId === entry.id ? 'ซ่อน' : 'ดูรายละเอียด' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="openId === entry.id" class="audit-detail">
                  <td colspan="6">
                    <table class="audit-diff">
                      <thead>
                        <tr>
                          <th>รายการ</th>
                          <th>ก่อน</th>
                          <th>หลัง</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="row in diffOf(entry)"
                          :key="row.key"
                          :class="{ changed: row.changed }"
                        >
                          <td>{{ fieldLabel(row.key) }}</td>
                          <td>
                            <div v-for="(line, index) in show(row.before)" :key="index">
                              {{ line }}
                            </div>
                          </td>
                          <td>
                            <div v-for="(line, index) in show(row.after)" :key="index">
                              {{ line }}
                            </div>
                          </td>
                        </tr>
                        <tr v-if="diffOf(entry).length === 0">
                          <td colspan="3">ไม่มีรายละเอียดค่า</td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <nav v-if="totalPages > 1" class="audit-pager" aria-label="เปลี่ยนหน้า">
          <button
            type="button"
            class="audit-secondary"
            :disabled="page === 0"
            @click="load(page - 1)"
          >
            ก่อนหน้า
          </button>
          <span>หน้า {{ page + 1 }} / {{ totalPages }}</span>
          <button
            type="button"
            class="audit-secondary"
            :disabled="page + 1 >= totalPages"
            @click="load(page + 1)"
          >
            ถัดไป
          </button>
        </nav>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.audit-page {
  min-height: 100vh;
  color: #25372e;
  background: #f8f4ed;
}
.audit-main {
  width: min(1280px, calc(100% - 48px));
  margin: auto;
  padding: 28px 0 80px;
}
.audit-back {
  color: #1e807c;
  font-weight: 600;
  text-decoration: none;
}
.audit-title h1 {
  margin: 18px 0 6px;
  font-size: clamp(1.8rem, 3.4vw, 2.6rem);
}
.audit-title p {
  margin: 0 0 22px;
  color: #68776f;
}
.audit-filter {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 14px;
  align-items: end;
  margin-bottom: 22px;
  padding: 18px;
  border-radius: 16px;
  background: #fff;
}
.audit-filter label {
  display: grid;
  gap: 6px;
  font-size: 0.9rem;
  color: #53635b;
}
.audit-filter select,
.audit-filter input {
  box-sizing: border-box;
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #b7cecc;
  border-radius: 10px;
  background: #f7faf9;
  font: inherit;
}
.audit-buttons {
  display: flex;
  gap: 10px;
}
.audit-primary,
.audit-secondary {
  white-space: nowrap;
  min-height: 42px;
  padding: 0 20px;
  border-radius: 999px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.audit-primary {
  border: 0;
  color: #fff;
  background: #1e807c;
}
.audit-secondary {
  border: 2px solid #1e807c;
  color: #1e807c;
  background: #fff;
}
.audit-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.audit-card {
  padding: 18px;
  border-radius: 16px;
  background: #fff;
}
.audit-count {
  margin: 0 0 12px;
  color: #68776f;
}
.audit-scroll {
  overflow-x: auto;
}
.audit-table,
.audit-diff {
  width: 100%;
  border-collapse: collapse;
}
.audit-table th,
.audit-table td {
  padding: 12px 10px;
  text-align: left;
  vertical-align: top;
}
.audit-table > thead th {
  background: #f1f4f2;
}
.audit-table > tbody > tr + tr > td {
  border-top: 1px solid #e6ece8;
}
.audit-table tr.picked > td {
  background: #e8f4f1;
}
.audit-action {
  display: inline-block;
  min-width: 56px;
  padding: 4px 12px;
  border-radius: 999px;
  text-align: center;
  font-size: 0.85rem;
  font-weight: 600;
}
.audit-action.insert {
  background: #dff3e7;
  color: #16765d;
}
.audit-action.update {
  background: #fdeabf;
  color: #7a4d00;
}
.audit-action.delete {
  background: #f8deda;
  color: #b42318;
}
.audit-link {
  padding: 0;
  border: 0;
  background: none;
  color: #1e807c;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.audit-detail > td {
  background: #f7faf9;
}
.audit-diff {
  table-layout: fixed;
}
.audit-diff th,
.audit-diff td {
  padding: 8px 10px;
  text-align: left;
  word-break: break-word;
}
.audit-diff th:first-child,
.audit-diff td:first-child {
  width: 30%;
  word-break: normal;
}
.audit-diff thead th {
  color: #53635b;
  font-size: 0.85rem;
}
.audit-diff tr.changed td {
  background: #fff7e0;
}
.audit-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
}
.audit-error {
  color: #b42318;
}
.audit-empty {
  color: #68776f;
}
</style>
