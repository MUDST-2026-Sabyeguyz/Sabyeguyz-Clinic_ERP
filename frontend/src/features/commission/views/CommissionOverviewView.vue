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
const pendingPayment = computed(() =>
  visible.value.some((row) => row.pendingPayment === null)
    ? null
    : visible.value.reduce((sum, row) => sum + (row.pendingPayment ?? 0), 0),
);
const money = (value: number | null) =>
  value === null ? "—" : `฿${value.toLocaleString("th-TH")}`;
let requestVersion = 0;
async function loadOverview() {
  // โหลดรายงาน ปิดยอด งวดจ่าย และบุคลากรตามเดือน/สาขาที่เลือก
  const version = ++requestVersion;
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
    const [data, closings, payments, staff] = await Promise.all([
      commissionApi.report(`${period}-01`, `${period}-${lastDay}`),
      commissionApi.closings(period, branchCode),
      commissionApi.payments(period, branchCode),
      staffApi.list(),
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
  } catch (error) {
    if (version !== requestVersion) return;
    errorMessage.value =
      error instanceof Error ? error.message : "โหลดข้อมูลค่าตอบแทนไม่สำเร็จ";
  }
}
onMounted(loadOverview);
watch([date, selectedBranchCode], loadOverview);
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ภาพรวมค่าตอบแทน</h1>
        <p>
          ตรวจยอด Commission และค่ามือจากรายการต้นทาง ก่อนปิดรอบและจ่ายค่าตอบแทน
        </p>
      </header>
      <CommissionTabs active="overview" />
      <p v-if="errorMessage" class="state-card error" role="alert">
        {{ errorMessage }}
      </p>
      <section class="commission-metrics">
        <article>
          <span>ยอดขายสุทธิ</span><b>{{ money(sales) }}</b
          ><small>ฐานหลังหักส่วนลดรายรายการ</small>
        </article>
        <article class="highlight">
          <span>Commission Pool</span><b>{{ money(pool) }}</b
          ><small>รวมค่าคอมมิชชันเดือนนี้</small>
        </article>
        <article>
          <span>ค่ามือ</span><b>{{ money(treatment) }}</b
          ><small>{{
            treatment === null
              ? "ยังไม่มีข้อมูลสรุปค่ามือ"
              : "รายครั้งและรักษาแทน · ตามสาขาประจำ"
          }}</small>
        </article>
        <article class="warning">
          <span>ยอดรอจ่าย</span><b>{{ money(pendingPayment) }}</b
          ><small>รอตรวจสอบ {{ visible.length }} คน</small>
        </article>
      </section>
      <section class="commission-directory commission-card">
        <header class="overview-filters">
          <label
            >ค้นหานักกายภาพ<input
              v-model="search"
              placeholder="ค้นหาชื่อหรือรหัสพนักงาน"
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
            <p>ยอดคำนวณของรอบที่เลือก</p>
          </div>
          <span>ทั้งหมด {{ visible.length }} คน</span>
        </div>
        <div class="commission-table-wrap overview-table-scroll">
          <table class="commission-table">
            <thead>
              <tr>
                <th>นักกายภาพ</th>
                <th>Tier / อัตรา</th>
                <th>ฐานคำนวณ</th>
                <th>Commission</th>
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
                <td>{{ money(row.base) }}</td>
                <td>{{ money(row.commission) }}</td>
                <td>{{ money(row.treatment) }}</td>
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
          รวม {{ visible.length }} คน · อัปเดตล่าสุด 28 ก.ย. 2569 16:05
        </footer>
      </section>
    </main>
  </div>
</template>
<style scoped>
@import "../styles/commission-manager.css";
.overview-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1.5fr) minmax(190px, 0.8fr) minmax(
      190px,
      0.8fr
    );
  gap: 20px;
  padding: 20px 24px;
  border-bottom: 1px solid #e7ede9;
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
  color: #145c59;
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
  padding: 14px 20px;
  border: 0;
  color: #34594f;
  background: #f2f7f4;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
}
.commission-directory .commission-table td {
  padding: 16px 20px;
  border-bottom: 1px solid #e7ede9;
  color: #25372e;
  font-size: 15px;
  vertical-align: middle;
}
.commission-directory .commission-table tbody tr:hover td {
  background: #f7faf8;
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
  .overview-filters {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 560px) {
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
