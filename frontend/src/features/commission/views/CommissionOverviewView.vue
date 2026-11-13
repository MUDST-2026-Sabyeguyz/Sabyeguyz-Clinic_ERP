<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import StaffHeader from "@/features/staff/components/StaffHeader.vue";
import FilterDropdown from "@/features/staff/components/FilterDropdown.vue";
import MonthFilterPicker from "@/features/staff/components/MonthFilterPicker.vue";
import CommissionTabs from "./CommissionTabs.vue";
import { commissionApi, staffApi } from "@/api/client";
import { selectedManagerBranch } from "@/features/staff/data/managerBranch";
import { clinicToday } from "@/utils/clinicDate";

type PersonRow = {
  id: string;
  name: string;
  tier: string;
  base: number;
  commission: number;
  treatment: number | null;
  pendingPayment: number | null;
  branch: string;
  status: "pending" | "approved";
};
const rows = ref<PersonRow[]>([]);
const errorMessage = ref("");
const closing = ref(false);
const loading = ref(false);
const closeConfirmOpen = ref(false);
const branchClosed = ref(false);
const lastLoadedAt = ref<Date | null>(null);
const lastLoadedLabel = computed(() =>
  lastLoadedAt.value
    ? new Intl.DateTimeFormat("th-TH", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(lastLoadedAt.value)
    : "ยังไม่โหลดข้อมูล",
);
const route = useRoute();
const search = ref(typeof route.query.search === "string" ? route.query.search : "");
const status = ref(route.query.status === "pending" || route.query.status === "approved" ? route.query.status : "");
const selectedBranchCode = computed(() => selectedManagerBranch()?.code ?? "");
const selectedMonth = String(route.query.month ?? "");
const date = ref(
  /^\d{4}-(0[1-9]|1[0-2])$/.test(selectedMonth)
    ? `${selectedMonth}-01`
    : clinicToday(),
);
const monthFilter = computed({
  get: () => date.value.slice(0, 7),
  set: (month: string) => {
    if (/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) date.value = `${month}-01`;
  },
});
const selectedPeriodLabel = computed(() => {
  const [year, month] = date.value.slice(0, 7).split("-").map(Number);
  const lastDay = new Date(year!, month!, 0).getDate();
  const label = new Intl.DateTimeFormat("th-TH", {
    month: "long",
    year: "numeric",
  }).format(new Date(year!, month! - 1, 1, 12));
  return `วันที่ 1–${lastDay} ${label}`;
});
const canCloseSelectedMonth = computed(() => monthFilter.value < clinicToday().slice(0, 7));
const visible = computed(() =>
  rows.value.filter(
    (row) =>
      row.branch === selectedBranchCode.value &&
      (!search.value ||
        row.name.includes(search.value) ||
        row.id.toLowerCase().includes(search.value.toLowerCase())) &&
      (!status.value || row.status === status.value),
  ),
);
const sales = computed(() => visible.value.reduce((s, r) => s + r.base, 0));
const pool = computed(() =>
  visible.value.reduce((s, r) => s + r.commission, 0),
);
const treatment = computed(() =>
  visible.value.some((row) => row.treatment === null)
    ? null
    : visible.value.reduce((sum, row) => sum + (row.treatment ?? 0), 0),
);
const money = (value: number | null) =>
  value === null ? "—" : `฿${value.toLocaleString("th-TH")}`;
let requestVersion = 0;
async function loadOverview() {
  // โหลดรายงาน ปิดยอด งวดจ่าย และบุคลากรตามเดือน/สาขาที่เลือก
  const version = ++requestVersion;
  loading.value = true;
  errorMessage.value = "";
  rows.value = [];
  try {
    const period = date.value.slice(0, 7);
    const [year, month] = period.split("-").map(Number);
    const lastDay = String(new Date(year!, month!, 0).getDate()).padStart(
      2,
      "0",
    );
    const branchCode = selectedBranchCode.value;
    if (!branchCode) throw new Error("กรุณาเลือกสาขาก่อนดูภาพรวมค่าตอบแทน");
    const [data, closings, payments, staff, branchClosing] = await Promise.all([
      commissionApi.report(`${period}-01`, `${period}-${lastDay}`),
      commissionApi.closings(period, branchCode),
      commissionApi.payments(period, branchCode),
      staffApi.list(),
      commissionApi.branchClosing(period, branchCode),
    ]);
    if (version !== requestVersion) return;
    // รวมแหล่งข้อมูลเป็นแถวเดียวต่อพนักงาน โดยคงยอดที่ยังไม่ทราบเป็น null
    const closingStatus = new Map(
      closings.map((item) => [item.employeeId, item.status]),
    );
    const people = new Map(staff.map((person) => [person.id, person]));
    const payroll = new Map(
      payments.map((payment) => [payment.staffId, payment]),
    );
    const salesByStaff = new Map(
      data
        .filter((row) => row.staffId !== null && row.branchCode === branchCode)
        .map((row) => [row.staffId!, row]),
    );
    const ids = new Set([...salesByStaff.keys(), ...payroll.keys()]);
    const realRows: PersonRow[] = [...ids].map((id) => {
      const sale = salesByStaff.get(id);
      const payment = payroll.get(id);
      const person = people.get(id);
      return {
        id: String(id),
        name: person?.fullName ?? sale?.staffName ?? payment!.staffName,
        tier: sale ? `อัตรา ${Math.round(sale.rate * 100)}%` : "—",
        base: Number(sale?.base ?? 0),
        commission: Number(sale?.commission ?? 0),
        treatment:
          payment?.singleVisitFee != null && payment.substituteFee != null
            ? Number(payment.singleVisitFee) + Number(payment.substituteFee)
            : null,
        pendingPayment: payment?.status === "PAID" ? 0 : (payment?.net ?? null),
        branch: person?.branchCode ?? sale?.branchCode ?? "",
        status: ["CLOSED", "FINALIZED"].includes(closingStatus.get(id) ?? "")
          ? "approved"
          : "pending",
      };
    });
    rows.value = realRows;
    branchClosed.value = branchClosing.status === 'CLOSED';
    lastLoadedAt.value = new Date();
  } catch (error) {
    if (version !== requestVersion) return;
    errorMessage.value =
      error instanceof Error ? error.message : "โหลดข้อมูลค่าตอบแทนไม่สำเร็จ";
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}
onMounted(loadOverview);
watch([date, selectedBranchCode], loadOverview);
async function closePeriod() {
  if (closing.value || branchClosed.value || !selectedBranchCode.value) return;
  closing.value = true;
  errorMessage.value = '';
  try {
    await commissionApi.closeMonth(monthFilter.value, selectedBranchCode.value);
    closeConfirmOpen.value = false;
    await loadOverview();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ปิดรอบไม่สำเร็จ';
  } finally {
    closing.value = false;
  }
}
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ภาพรวมค่าตอบแทน</h1>
        <p>
          ดูยอดคอมมิชชัน ค่ามือ และยอดรอจ่าย แยกตามพนักงานและรอบที่เลือก
        </p>
        <div class="overview-period"><span>รอบคำนวณ</span><b>{{ selectedPeriodLabel }}</b></div>
        <button class="commission-primary overview-close-action" type="button" :disabled="loading || closing || branchClosed || !canCloseSelectedMonth || !!errorMessage" @click="closeConfirmOpen = true">{{ branchClosed ? 'ปิดรอบแล้ว' : 'ปิดรอบ' }}</button>
        <small v-if="!canCloseSelectedMonth && !branchClosed" class="overview-close-hint">ปิดรอบได้เมื่อเดือนสิ้นสุดแล้ว</small>
      </header>
      <CommissionTabs active="overview" />
      <p v-if="errorMessage" class="state-card error" role="alert">
        {{ errorMessage }}
      </p>
      <section class="commission-metrics">
        <article>
          <span>ยอดขายสุทธิ</span><b>{{ money(sales) }}</b>
        </article>
        <article>
          <span>ค่ามือรวม</span><b>{{ money(treatment) }}</b>
        </article>
        <article class="highlight">
          <span>ค่าคอมมิชชันรวม</span><b>{{ money(pool) }}</b>
        </article>
        <article class="creation-total">
          <span>ค่าคอมมิชชันที่จะสร้าง</span><b>{{ money(pool) }}</b>
        </article>
      </section>
      <div v-if="closeConfirmOpen" class="overview-close-backdrop" @click.self="closeConfirmOpen = false">
        <section class="overview-close-dialog" role="dialog" aria-modal="true" aria-labelledby="overview-close-title">
          <h2 id="overview-close-title">ยืนยันปิดรอบ {{ selectedPeriodLabel }}</h2>
          <p>ระบบจะล็อกอัตราคอมมิชชันของสาขาที่เลือกสำหรับเดือนนี้</p>
          <div><button class="commission-secondary" type="button" :disabled="closing" @click="closeConfirmOpen = false">ยกเลิก</button><button class="commission-primary" type="button" :disabled="closing" @click="closePeriod">{{ closing ? 'กำลังปิดรอบ…' : 'ยืนยันปิดรอบ' }}</button></div>
        </section>
      </div>
      <section class="commission-directory commission-card">
        <header class="overview-filters">
          <label
            >ค้นหานักกายภาพ<input
              v-model="search"
              type="search"
              aria-label="ค้นหาชื่อ"
              placeholder="ค้นหาชื่อ"
          /></label>
          <div class="overview-filter-field">
            <span>รอบคำนวณ</span
            ><MonthFilterPicker
              v-model="monthFilter"
              :past-years="3"
              :future-years="0"
            />
          </div>
          <label
            >สถานะ<FilterDropdown
              v-model="status"
              default-value=""
              default-label="ทุกสถานะ"
              label="สถานะ"
              :options="[
                { value: 'pending', label: 'รอตรวจสอบ' },
                { value: 'approved', label: 'อนุมัติแล้ว' },
              ]"
          /></label>
        </header>
        <div class="directory-results">
          <div>
            <h2>รายชื่อผู้รับค่าตอบแทน</h2>
            <p>ยอดตามรอบและตัวกรองที่เลือก</p>
          </div>
          <span>ทั้งหมด {{ visible.length }} คน</span>
        </div>
        <div class="commission-table-wrap overview-table-scroll">
          <table class="commission-table">
            <thead>
              <tr>
                <th>นักกายภาพ</th>
                <th>อัตราคอมมิชชัน</th>
                <th class="numeric">ฐานคำนวณ</th>
                <th class="numeric">คอมมิชชัน</th>
                <th>ค่ามือ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visible" :key="row.id">
                <td>
                  <b>{{ row.name }}</b>
                </td>
                <td>{{ row.tier }}</td>
                <td class="numeric">{{ money(row.base) }}</td>
                <td class="numeric commission-value">{{ money(row.commission) }}</td>
                <td class="numeric">{{ money(row.treatment) }}</td>
                <td>
                  <RouterLink
                    class="detail-button"
                    :to="{
                      path: `/staff/commission/staff/${row.id}`,
                      query: {
                        month: date.slice(0, 7),
                        ...(search.trim() ? { search: search.trim() } : {}),
                        ...(status ? { status } : {}),
                      },
                    }"
                    >ดูรายละเอียด</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="!visible.length">
                <td colspan="6" class="empty-row">
                  ยังไม่มีข้อมูลค่าตอบแทนสำหรับเงื่อนไขที่เลือก
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer>
          รวม {{ visible.length }} คน · อัปเดตล่าสุด {{ lastLoadedLabel }}
        </footer>
      </section>
    </main>
  </div>
</template>
<style scoped>
@import "../styles/commission-manager.css";
.commission-manager-main {
  width: min(1360px, calc(100% - 64px));
  padding-top: 34px;
}
.commission-title {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  margin-bottom: 22px;
}
.overview-close-action {
  grid-column: 2;
  grid-row: 1 / span 3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: start;
  text-decoration: none;
}
.overview-close-hint {
  grid-column: 2;
  grid-row: 4;
  justify-self: end;
  margin-top: 6px;
  color: #687d7a;
}
.overview-close-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgb(0 0 0 / 45%); }
.overview-close-dialog { width: min(440px, 100%); padding: 24px; border-radius: 14px; background: #fff; }
.overview-close-dialog h2 { margin: 0 0 8px; }
.overview-close-dialog p { margin: 0 0 20px; }
.overview-close-dialog > div { display: flex; justify-content: flex-end; gap: 8px; }
.commission-title h1 {
  color: #153f3c;
  letter-spacing: -0.025em;
}
.commission-title p {
  max-width: 680px;
  line-height: 1.6;
}
.overview-period {
  display: inline-flex;
  grid-column: 1;
  justify-self: start;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding: 8px 12px;
  border: 1px solid #d5e6df;
  border-radius: 9px;
  background: #edf6f2;
  color: #45675d;
  font-size: 0.9rem;
}
.overview-period b {
  color: #145c59;
  font-weight: 700;
}
.commission-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 20px 0 22px;
}
.commission-metrics article {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  align-items: baseline;
  column-gap: 16px;
  min-height: 104px;
  padding: 18px 22px;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(31 67 58 / 4%);
}
.commission-metrics article span {
  grid-column: 1;
  grid-row: 1;
  font-size: 0.94rem;
  font-weight: 600;
}
.commission-metrics article b {
  grid-column: 2;
  grid-row: 1;
  margin: 0;
  font-size: 1.65rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.commission-metrics article.highlight {
  background: #fff;
}
.commission-metrics article.creation-total {
  border-color: #b9ded4;
  background: #edf7f3;
}
.commission-metrics article.highlight b {
  color: #16875b;
  font-size: 1.85rem;
}
.commission-directory {
  border-radius: 14px;
  box-shadow: 0 3px 14px rgb(31 67 58 / 5%);
}
.overview-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1.5fr) minmax(190px, 0.8fr) minmax(
      190px,
      0.8fr
    );
  gap: 20px;
  padding: 18px 22px;
  border-bottom: 1px solid #e7ede9;
  background: #f8fbf9;
}
.overview-filters label,
.overview-filter-field {
  display: grid;
  gap: 7px;
  color: #25372f;
  font-size: 0.86rem;
  font-weight: 700;
}
.overview-filters input {
  height: 44px;
  padding: 0 14px;
  border: 1px solid #b9c8c2;
  border-radius: 8px;
  font: inherit;
  background: #fff;
  color: #25372f;
}
.overview-filters input:focus-visible {
  outline: 3px solid rgb(30 139 134 / 18%);
  border-color: #1e8b86;
}
.overview-filters :deep(.date-trigger),
.overview-filters :deep(.filter-dropdown__button) {
  min-height: 44px;
  border-color: #b9c8c2;
}
.detail-button {
  display: inline-flex;
  justify-content: center;
  min-width: 116px;
  padding: 9px 14px;
  border: 1px solid #8fb5ae;
  border-radius: 8px;
  background: #fff;
  color: var(--color-primary-strong);
  font-weight: 700;
  text-decoration: none;
}
.detail-button:hover {
  border-color: #145c59;
  background: #edf5f1;
}
.commission-directory {
  padding: 0;
  overflow: hidden;
}
.commission-directory .directory-results {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 24px;
  min-height: 60px;
  padding: 16px 24px;
  box-sizing: border-box;
}
.directory-results h2 {
  margin: 0;
  color: #25372e;
  font-size: 18px;
}
.directory-results p {
  margin: 4px 0 0;
  color: #667a72;
  font-size: 13px;
}
.directory-results > span {
  color: #145c59;
  font-size: 14px;
  font-weight: 700;
}
.commission-directory .commission-table .numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.commission-directory .commission-table .commission-value {
  color: #087b68;
  font-weight: 700;
}
.commission-directory .commission-table-wrap {
  overflow-x: auto;
}
.commission-directory .commission-table {
  width: 100%;
  min-width: 880px;
  table-layout: fixed;
  border-collapse: collapse;
}
.commission-directory .commission-table th {
  padding: 15px 20px;
  border: 0;
  color: #34594f;
  background: #edf5f1;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
}
.commission-directory .commission-table td {
  padding: 17px 20px;
  border-bottom: 1px solid #e7ede9;
  color: #25372e;
  font-size: 15px;
  vertical-align: middle;
}
.commission-directory .commission-table tbody tr:hover td {
  background: #f3f9f6;
}
.commission-directory .commission-table :is(th, td):last-child {
  text-align: right;
}
.commission-directory .commission-table :is(th, td):nth-child(1) {
  width: 23%;
}
.commission-directory .commission-table :is(th, td):nth-child(2) {
  width: 15%;
}
.commission-directory .commission-table :is(th, td):nth-child(3) {
  width: 16%;
}
.commission-directory .commission-table :is(th, td):nth-child(4) {
  width: 16%;
}
.commission-directory .commission-table :is(th, td):nth-child(5) {
  width: 14%;
}
.commission-directory .commission-table :is(th, td):nth-child(6) {
  width: 16%;
}
.approval-status {
  display: inline-flex;
  justify-content: center;
  min-width: 92px;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 0.86rem;
  font-weight: 700;
}
.approval-status.pending {
  background: #fff1c7;
  color: #8a6514;
}
.approval-status.approved {
  background: #def3e7;
  color: #167452;
}
.approval-status.returned {
  background: #ffe2dc;
  color: #b43a2f;
}
.commission-table-wrap footer {
  padding: 20px;
  background: #f0f5ed;
  color: #667a72;
}
.commission-directory > footer {
  padding: 18px 24px;
  color: #667a72;
  background: #f0f5ed;
}
.overview-table-scroll {
  scrollbar-width: thin;
  scrollbar-color: #1e8b86 #e8f1ef;
}
.overview-table-scroll::-webkit-scrollbar {
  height: 8px;
}
.overview-table-scroll::-webkit-scrollbar-track {
  background: #e8f1ef;
}
.overview-table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #1e8b86;
}
.overview-table-scroll::-webkit-scrollbar-thumb:hover {
  background: #176f6b;
}
.empty-row {
  color: #718985 !important;
  text-align: center !important;
}
@media (max-width: 900px) {
  .commission-manager-main {
    width: min(100% - 40px, 760px);
  }
  .overview-filters {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 560px) {
  .commission-manager-main {
    width: calc(100% - 28px);
    padding-top: 24px;
  }
  .commission-title {
    grid-template-columns: 1fr;
  }
  .overview-close-action {
    grid-column: 1;
    grid-row: auto;
    margin-top: 16px;
  }
  .overview-close-hint {
    grid-column: 1;
    grid-row: auto;
    justify-self: start;
  }
  .overview-filters {
    grid-template-columns: 1fr;
  }
  .commission-directory .directory-results {
    padding: 16px;
  }
  .commission-directory .commission-table :is(th, td):last-child {
    position: sticky;
    right: 0;
    border-left: 1px solid #e7ede9;
    background: #fff;
  }
}
</style>
