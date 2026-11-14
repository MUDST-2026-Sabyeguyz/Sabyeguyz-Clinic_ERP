<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import StaffHeader from "@/features/staff/components/StaffHeader.vue";
import CommissionTabs from "./CommissionTabs.vue";
import {
  commissionApi,
  staffApi,
  type ClosingHistoryMonth,
  type StaffPayment,
} from "@/api/client";
import { selectedManagerBranch } from "@/features/staff/data/managerBranch";
import { clinicToday } from "@/utils/clinicDate";
import { isPaymentDateAllowed } from "../lib/paymentBatch";

type PersonRow = {
  id: string;
  name: string;
  tier: string;
  base: number;
  commission: number;
  treatment: number | null;
  adjustment: number | null;
  net: number | null;
  pendingPayment: number | null;
  branch: string;
  status: "pending" | "approved" | "paid";
  paymentId: number | null;
  paymentStatus: StaffPayment["status"] | null;
  closingId: number | null;
  closingClosed: boolean;
};
type DialogState =
  | { type: "close" }
  | { type: "reopenAll" }
  | { type: "pay"; row: PersonRow }
  | { type: "reopen"; row: PersonRow }
  | null;

const rows = ref<PersonRow[]>([]);
const history = ref<ClosingHistoryMonth[]>([]);
const expandedMonth = ref<string | null>(null);
const errorMessage = ref("");
const closing = ref(false);
const closeStep = ref("");
const loading = ref(false);
const busy = ref(false);
const dialog = ref<DialogState>(null);
const dialogError = ref("");
const reason = ref("");
const paidDate = ref(clinicToday());
const reference = ref("");
const branchClosed = ref(false);
const route = useRoute();
const search = ref(typeof route.query.search === "string" ? route.query.search : "");
const statusQuery = route.query.status;
const status = ref(
  statusQuery === "pending" || statusQuery === "approved" || statusQuery === "paid"
    ? statusQuery
    : "",
);
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
const branchRows = computed(() => rows.value.filter((row) => row.branch === selectedBranchCode.value));
const visible = computed(() =>
  branchRows.value.filter(
    (row) =>
      (!search.value ||
        row.name.includes(search.value) ||
        row.id.toLowerCase().includes(search.value.toLowerCase())) &&
      (!status.value || row.status === status.value),
  ),
);
// เลื่อนเดือนด้วยปุ่ม ‹ › (ไม่เกินเดือนปัจจุบัน ย้อนหลังได้ 3 ปี)
// สถานะไม่มีตัวเลือกบนหน้าจอ แต่ยังรับ ?status= จาก URL ที่ส่งกลับมาจากหน้ารายละเอียด
const statusLabels: Record<string, string> = {
  pending: "ยังไม่ปิดรอบ",
  approved: "ปิดรอบแล้ว รอจ่าย",
  paid: "จ่ายแล้ว",
};
const monthName = computed(() => {
  const [year, month] = monthFilter.value.split("-").map(Number);
  return new Intl.DateTimeFormat("th-TH", { month: "long" }).format(
    new Date(year!, month! - 1, 1, 12),
  );
});
const yearOptions = computed(() => {
  const currentYear = Number(clinicToday().slice(0, 4));
  return Array.from({ length: 4 }, (_, index) => currentYear - index);
});
const selectedYear = computed({
  get: () => Number(monthFilter.value.slice(0, 4)),
  set: (year: number) => {
    const month = monthFilter.value.slice(5, 7);
    const next = `${year}-${month}`;
    // ไม่ให้เลือกเดือนในอนาคต: ถ้าเกินเดือนปัจจุบันให้ถอยกลับมาเดือนปัจจุบัน
    monthFilter.value = next > currentMonthValue.value ? currentMonthValue.value : next;
  },
});
const currentMonthValue = computed(() => clinicToday().slice(0, 7));
const earliestMonthValue = computed(() => `${Number(currentMonthValue.value.slice(0, 4)) - 3}-01`);
const canGoPrev = computed(() => monthFilter.value > earliestMonthValue.value);
const canGoNext = computed(() => monthFilter.value < currentMonthValue.value);
function shiftMonth(offset: number) {
  const [year, month] = monthFilter.value.split("-").map(Number);
  const next = new Date(year!, month! - 1 + offset, 1, 12);
  monthFilter.value = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
}
const hasFilters = computed(() => !!search.value.trim() || !!status.value);
const statusLabelOf = (value: string) => statusLabels[value] ?? "";
function clearFilters() {
  search.value = "";
  status.value = "";
}
// แบ่งหน้าเมื่อมีมากกว่า 20 รายการ (รายชื่อนักกายภาพ และประวัติการปิดรอบ)
const PAGE_SIZE = 20;
const listPage = ref(1);
const historyPage = ref(1);
const listTotalPages = computed(() => Math.max(1, Math.ceil(visible.value.length / PAGE_SIZE)));
const historyTotalPages = computed(() => Math.max(1, Math.ceil(history.value.length / PAGE_SIZE)));
const pagedVisible = computed(() =>
  visible.value.slice((listPage.value - 1) * PAGE_SIZE, listPage.value * PAGE_SIZE),
);
const pagedHistory = computed(() =>
  history.value.slice((historyPage.value - 1) * PAGE_SIZE, historyPage.value * PAGE_SIZE),
);
watch([search, status, monthFilter, selectedBranchCode], () => {
  listPage.value = 1;
});
watch(listTotalPages, (total) => {
  if (listPage.value > total) listPage.value = total;
});
watch(historyTotalPages, (total) => {
  if (historyPage.value > total) historyPage.value = total;
});
const sales = computed(() => visible.value.reduce((s, r) => s + r.base, 0));
const pool = computed(() =>
  visible.value.reduce((s, r) => s + r.commission, 0),
);
const treatment = computed(() =>
  visible.value.some((row) => row.treatment === null)
    ? null
    : visible.value.reduce((sum, row) => sum + (row.treatment ?? 0), 0),
);
const awaitingPayment = computed(() =>
  visible.value.some((row) => row.paymentStatus !== "PAID" && row.pendingPayment === null)
    ? null
    : visible.value.reduce((sum, row) => sum + (row.pendingPayment ?? 0), 0),
);
// Reopen is closed for the whole branch month as soon as one person is Paid
const anyPaid = computed(() => branchRows.value.some((row) => row.paymentStatus === "PAID"));
const closedCount = computed(() => branchRows.value.filter((row) => row.paymentStatus === "CLOSED").length);
const remainingToClose = computed(
  () =>
    branchRows.value.filter((row) => row.paymentStatus === null || row.paymentStatus === "OPEN" || !row.closingClosed && row.paymentStatus !== "PAID").length,
);
const needsClose = computed(() => !branchClosed.value || remainingToClose.value > 0);
const closeButtonLabel = computed(() =>
  closedCount.value + (anyPaid.value ? 1 : 0) > 0 && remainingToClose.value > 0
    ? `ปิดรอบที่เหลือ (${remainingToClose.value} คน)`
    : "ปิดรอบ",
);
const branchState = computed(() => {
  if (branchClosed.value && remainingToClose.value === 0) return { cls: "closed", label: "Closed" };
  if (branchRows.value.some((row) => row.closingClosed || row.paymentStatus === "PAID"))
    return { cls: "open", label: "บางส่วนเปิดอยู่" };
  return { cls: "open", label: "ยังไม่ปิดรอบ" };
});
// แถบความคืบหน้า 3 ขั้น: เปิดรอบ → ตรวจสอบ (ปิดแล้วบางส่วน) → ปิดรอบ (ปิดครบทุกคน)
const stages = computed(() => {
  const labels = ["เปิดรอบ", "ตรวจสอบ", "ปิดรอบ"];
  const partial = branchRows.value.some((row) => row.closingClosed || row.paymentStatus === "PAID");
  const current = !needsClose.value ? 2 : partial ? 1 : 0;
  return labels.map((label, index) => ({
    label,
    // ขั้นสุดท้ายที่ถึงแล้วถือว่าเสร็จ ไม่ต้องค้างเป็นขั้นปัจจุบัน
    cls: index < current || current === 2 ? "done" : index === current ? "cur" : "",
  }));
});
const money = (value: number | null) =>
  value === null ? "—" : `฿${value.toLocaleString("th-TH")}`;
const paymentLabel = (value: PersonRow["paymentStatus"]) =>
  value === "PAID"
    ? "Paid · จ่ายแล้ว"
    : value === "CLOSED"
      ? "Closed · รอจ่าย"
      : value === "OPEN"
        ? "Open · ยังไม่ปิด"
        : "ยังไม่มีงวดจ่าย";
const historyStatusLabel: Record<ClosingHistoryMonth["status"], string> = {
  OPEN: "ยังไม่ปิด",
  CLOSED: "Closed · รอจ่าย",
  PARTIAL: "จ่ายบางส่วน",
  FINALIZED: "Finalized",
};
const monthLabel = (month: string) => {
  const [year, number] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("th-TH", { month: "long", year: "numeric" }).format(
    new Date(year!, number! - 1, 1, 12),
  );
};
const dateTime = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat("th-TH", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(new Date(value))
    : "—";
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
    const [data, closings, payments, staff, branchClosing, historyRows, feePreview] = await Promise.all([
      commissionApi.report(`${period}-01`, `${period}-${lastDay}`),
      commissionApi.closings(period, branchCode),
      commissionApi.payments(period, branchCode),
      staffApi.list(),
      commissionApi.branchClosing(period, branchCode),
      commissionApi.closingHistory(branchCode).catch(() => [] as ClosingHistoryMonth[]),
      // ค่ามือที่เกิดแล้วแต่ยังไม่มีงวดจ่าย (ก่อนปิดรอบ) ถ้าโหลดไม่ได้ก็แสดงเท่าที่มี
      commissionApi.feePreview(period, branchCode).catch(() => [] as Awaited<ReturnType<typeof commissionApi.feePreview>>),
    ]);
    if (version !== requestVersion) return;
    // รวมแหล่งข้อมูลเป็นแถวเดียวต่อพนักงาน โดยคงยอดที่ยังไม่ทราบเป็น null
    const closingByStaff = new Map(
      closings.map((item) => [item.employeeId, item]),
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
    const previewByStaff = new Map(feePreview.map((item) => [item.staffId, item]));
    const ids = new Set([...salesByStaff.keys(), ...payroll.keys(), ...previewByStaff.keys()]);
    const realRows: PersonRow[] = [...ids].map((id) => {
      const sale = salesByStaff.get(id);
      const payment = payroll.get(id);
      const person = people.get(id);
      const closingRow = closingByStaff.get(id);
      const preview = previewByStaff.get(id);
      const closingClosed = ["CLOSED", "FINALIZED"].includes(closingRow?.status ?? "");
      const paymentStatus = payment?.status ?? null;
      return {
        id: String(id),
        name: person?.fullName ?? sale?.staffName ?? payment?.staffName ?? preview!.staffName,
        tier: sale ? `อัตรา ${Math.round(sale.rate * 100)}%` : "—",
        base: Number(sale?.base ?? 0),
        commission: Number(sale?.commission ?? 0),
        // ค่ามือจากงวดจ่าย ถ้ายังไม่ปิดรอบใช้ค่ามือที่เกิดแล้วจาก ledger
        treatment:
          payment?.singleVisitFee != null && payment.substituteFee != null
            ? Number(payment.singleVisitFee) + Number(payment.substituteFee)
            : !payment && preview
              ? Number(preview.singleVisitFee) + Number(preview.substituteFee)
              : null,
        adjustment: payment?.adjustment != null ? Number(payment.adjustment) : null,
        net: payment?.net != null ? Number(payment.net) : null,
        pendingPayment: paymentStatus === "PAID" ? 0 : (payment?.net ?? null),
        // ค่ามือที่ยังไม่มีงวดจ่ายดึงมาตามสาขาที่เลือกอยู่แล้ว จึงถือเป็นสาขานี้
        branch: !sale && !payment && preview ? branchCode : (person?.branchCode ?? sale?.branchCode ?? ""),
        status:
          paymentStatus === "PAID" ? "paid" : closingClosed ? "approved" : "pending",
        paymentId: payment?.id ?? null,
        paymentStatus,
        closingId: closingRow?.id ?? null,
        closingClosed,
      };
    });
    rows.value = realRows;
    history.value = historyRows;
    branchClosed.value = branchClosing.status === 'CLOSED';
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

function openDialog(next: DialogState) {
  dialog.value = next;
  dialogError.value = "";
  reason.value = "";
  reference.value = "";
  paidDate.value = clinicToday();
}
function closeDialog() {
  if (closing.value || busy.value) return;
  dialog.value = null;
}
// ปิดรอบ: ล็อกอัตรา → เตรียมงวดจ่าย → ปิดงวดจ่าย (ยังไม่จ่ายเงิน) ถ้าล้มกลางทางกดซ้ำได้
async function closePeriod() {
  if (closing.value || !selectedBranchCode.value || !needsClose.value) return;
  closing.value = true;
  errorMessage.value = '';
  const month = monthFilter.value;
  const branchCode = selectedBranchCode.value;
  let failure = "";
  try {
    closeStep.value = "ล็อกอัตราคอมมิชชัน";
    await commissionApi.closeMonth(month, branchCode);
    closeStep.value = "เตรียมงวดจ่าย";
    await commissionApi.preparePayments(month, branchCode);
    closeStep.value = "ปิดงวดจ่าย";
    await commissionApi.closePayments(month, branchCode);
  } catch (error) {
    failure = `ปิดรอบไม่สำเร็จที่ขั้น "${closeStep.value}": ${
      error instanceof Error ? error.message : 'เกิดข้อผิดพลาด'
    } กดปิดรอบอีกครั้งเพื่อทำต่อจากขั้นที่ค้าง`;
  } finally {
    dialog.value = null;
    closing.value = false;
    closeStep.value = "";
    // โหลดใหม่เพื่อแสดงสถานะจริง แล้วคงข้อความขั้นที่ล้มไว้ (โหลดจะล้างข้อความเดิม)
    await loadOverview();
    if (failure) errorMessage.value = failure;
  }
}
async function submitPay() {
  const current = dialog.value;
  if (current?.type !== "pay" || busy.value || current.row.paymentId === null) return;
  if (!isPaymentDateAllowed(paidDate.value, clinicToday())) {
    dialogError.value = "วันที่จ่ายต้องไม่เกินวันที่ปัจจุบันของคลินิก";
    return;
  }
  busy.value = true;
  try {
    await commissionApi.pay(current.row.paymentId, paidDate.value, reference.value);
    dialog.value = null;
    await loadOverview();
  } catch (error) {
    dialogError.value = error instanceof Error ? error.message : "บันทึกการจ่ายไม่สำเร็จ";
  } finally {
    busy.value = false;
  }
}
async function submitReopen() {
  const current = dialog.value;
  if ((current?.type !== "reopen" && current?.type !== "reopenAll") || busy.value) return;
  const text = reason.value.trim();
  if (!text) {
    dialogError.value = "กรอกเหตุผลก่อนเปิดรอบอีกครั้ง";
    return;
  }
  if (text.length > 255) {
    dialogError.value = "เหตุผลยาวเกิน 255 ตัวอักษร";
    return;
  }
  busy.value = true;
  try {
    if (current.type === "reopenAll") {
      await commissionApi.reopenAllBranch(monthFilter.value, selectedBranchCode.value, text);
    } else {
      // เปิดงวดจ่ายก่อน แล้วค่อยเปิดการปิดยอด (ลำดับที่ backend กำหนด)
      if (current.row.paymentId !== null && current.row.paymentStatus === "CLOSED")
        await commissionApi.reopenPayment(current.row.paymentId, text);
      if (current.row.closingId !== null && current.row.closingClosed)
        await commissionApi.reopenClosing(current.row.closingId, text);
    }
    dialog.value = null;
    await loadOverview();
  } catch (error) {
    dialogError.value = error instanceof Error ? error.message : "เปิดรอบอีกครั้งไม่สำเร็จ";
    await loadOverview();
  } finally {
    busy.value = false;
  }
}
function toggleMonth(month: string) {
  expandedMonth.value = expandedMonth.value === month ? null : month;
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
      </header>
      <CommissionTabs active="overview" />
      <p v-if="errorMessage" class="state-card error" role="alert">
        {{ errorMessage }}
      </p>
      <section class="round-card commission-card" data-test="round-card" aria-label="รอบคำนวณ">
        <div class="round-period overview-filter-field">
          <span>รอบคำนวณ</span>
          <div class="month-stepper-row">
            <div class="month-stepper" role="group" aria-label="เลือกรอบคำนวณ">
              <button type="button" aria-label="เดือนก่อน" data-test="month-prev" :disabled="!canGoPrev" @click="shiftMonth(-1)">‹</button>
              <strong data-test="month-label">{{ monthName }}</strong>
              <button type="button" aria-label="เดือนถัดไป" data-test="month-next" :disabled="!canGoNext" @click="shiftMonth(1)">›</button>
            </div>
            <select v-model.number="selectedYear" class="year-select" aria-label="ปี" data-test="year-select">
              <option v-for="year in yearOptions" :key="year" :value="year">{{ year + 543 }}</option>
            </select>
            <button v-if="canGoNext" type="button" class="link-button" data-test="month-now" @click="monthFilter = currentMonthValue">เดือนปัจจุบัน</button>
          </div>
          <small class="round-dates">
            {{ selectedPeriodLabel }}
            <span class="state-badge" :class="branchState.cls" data-test="branch-state">{{ branchState.label }}</span>
          </small>
        </div>
        <ol class="stage-row" aria-label="ความคืบหน้าของรอบ" data-test="stage-row">
          <li
            v-for="(stage, index) in stages"
            :key="stage.label"
            :class="stage.cls"
            :aria-current="stage.cls === 'cur' ? 'step' : undefined"
          >
            <span class="stage-dot" aria-hidden="true">{{ stage.cls === 'done' ? '✓' : index + 1 }}</span>
            <span class="stage-label">{{ stage.label }}</span>
          </li>
        </ol>
        <div class="round-actions overview-actions">
          <button
            v-if="needsClose"
            class="commission-primary"
            type="button"
            data-test="close-round"
            :disabled="loading || closing || !canCloseSelectedMonth || !!errorMessage && !rows.length"
            @click="openDialog({ type: 'close' })"
          >{{ closeButtonLabel }}</button>
          <button
            v-else-if="closedCount > 0 && !anyPaid"
            class="commission-secondary"
            type="button"
            data-test="reopen-all"
            :disabled="loading"
            @click="openDialog({ type: 'reopenAll' })"
          >เปิดรอบอีกครั้งทั้งสาขา</button>
        </div>
      </section>
      <section class="commission-metrics">
        <article>
          <span>ยอดขายสุทธิ</span><b>{{ money(sales) }}</b>
        </article>
        <article>
          <span>ค่ามือรวม</span><b>{{ money(treatment) }}</b>
        </article>
        <article>
          <span>ค่าคอมมิชชันรวม</span><b>{{ money(pool) }}</b>
        </article>
        <article>
          <span>ยอดรอจ่าย</span>
          <b v-if="awaitingPayment !== null">{{ money(awaitingPayment) }}</b>
          <b v-else class="metric-placeholder" data-test="awaiting-placeholder">รอการปิดรอบ</b>
        </article>
      </section>
      <div v-if="dialog" class="overview-close-backdrop" @click.self="closeDialog">
        <section class="overview-close-dialog" role="dialog" aria-modal="true" aria-labelledby="overview-dialog-title">
          <template v-if="dialog.type === 'close'">
            <h2 id="overview-dialog-title">ยืนยันปิดรอบ</h2>
            <p class="dialog-period">{{ selectedPeriodLabel }}</p>
            <p v-if="closing" class="dialog-progress">กำลังทำ: {{ closeStep }}…</p>
            <div class="dialog-actions"><button class="commission-secondary" type="button" :disabled="closing" @click="closeDialog">ยกเลิก</button><button class="commission-primary" type="button" data-test="confirm-close" :disabled="closing" @click="closePeriod">{{ closing ? 'กำลังปิดรอบ…' : 'ยืนยันปิดรอบ' }}</button></div>
          </template>
          <template v-else-if="dialog.type === 'pay'">
            <h2 id="overview-dialog-title">ยืนยันจ่าย {{ dialog.row.name }} · {{ money(dialog.row.net) }}</h2>
            <label class="dialog-field">วันที่จ่าย<input v-model="paidDate" type="date" :max="clinicToday()" data-test="paid-date" /></label>
            <label class="dialog-field">เลขอ้างอิง (ไม่บังคับ)<input v-model="reference" type="text" maxlength="100" placeholder="เลขที่โอนเงิน" data-test="pay-reference" /></label>
            <p v-if="!anyPaid" class="dialog-warn" data-test="first-pay-warning">นี่คือการจ่ายคนแรกของรอบ เมื่อยืนยันแล้วทั้งสาขาจะเปิดรอบอีกครั้งไม่ได้อีก ตรวจยอดทุกคนก่อนกดจ่าย</p>
            <p class="dialog-note">ระบบจะ Finalize เดือนขายที่เกี่ยวข้องของคนนี้ด้วย</p>
            <p v-if="dialogError" class="dialog-error" role="alert">{{ dialogError }}</p>
            <div class="dialog-actions"><button class="commission-secondary" type="button" :disabled="busy" @click="closeDialog">ยกเลิก</button><button class="commission-primary" type="button" data-test="confirm-pay" :disabled="busy" @click="submitPay">{{ busy ? 'กำลังบันทึก…' : 'ยืนยันจ่าย' }}</button></div>
          </template>
          <template v-else>
            <h2 id="overview-dialog-title">{{ dialog.type === 'reopenAll' ? `เปิดรอบอีกครั้งทั้งสาขา (${closedCount} คน)` : `เปิดรอบอีกครั้งงวดของ ${dialog.row.name}` }}</h2>
            <p>ปลดล็อกเพื่อแก้ข้อมูลต้นทาง แล้วปิดรอบใหม่ ค่าคอมมิชชันและค่ามือ{{ dialog.type === 'reopenAll' ? 'ของทุกคน' : 'ของคนนี้' }}จะถูกคำนวณใหม่ สถานะสาขากลับเป็น Open จนกว่าจะปิดรอบอีกครั้ง</p>
            <p v-if="dialog.type === 'reopenAll'" class="dialog-warn">Tier ของทุกคนจะถูกหาใหม่ตอนปิดรอบอีกครั้ง และอาจเปลี่ยนได้</p>
            <label class="dialog-field">เหตุผล<input v-model="reason" type="text" maxlength="255" placeholder="เช่น บันทึกผู้รักษาแทนผิดคน" data-test="reopen-reason" /></label>
            <p v-if="dialogError" class="dialog-error" role="alert">{{ dialogError }}</p>
            <div class="dialog-actions"><button class="commission-secondary" type="button" :disabled="busy" @click="closeDialog">ยกเลิก</button><button class="commission-primary" type="button" data-test="confirm-reopen" :disabled="busy" @click="submitReopen">{{ busy ? 'กำลังเปิดรอบอีกครั้ง…' : 'ยืนยันเปิดรอบอีกครั้ง' }}</button></div>
          </template>
        </section>
      </div>
      <section class="commission-directory commission-card">
        <header class="overview-filters">
          <label class="search-field"
            ><span>ค้นหานักกายภาพ</span>
            <span class="search-box">
              <input
                v-model="search"
                type="search"
                aria-label="ค้นหาชื่อ"
                placeholder="ค้นหาชื่อนักกายภาพ"
              />
              <button v-if="search" type="button" class="search-clear" aria-label="ล้างคำค้น" data-test="search-clear" @click="search = ''">✕</button>
            </span>
          </label>
          <div v-if="hasFilters" class="active-filters" data-test="active-filters">
            <span v-if="search.trim()" class="filter-chip">ชื่อ: {{ search.trim() }}<button type="button" aria-label="ลบตัวกรองชื่อ" @click="search = ''">✕</button></span>
            <span v-if="status" class="filter-chip">สถานะ: {{ statusLabelOf(status) }}<button type="button" aria-label="ลบตัวกรองสถานะ" @click="status = ''">✕</button></span>
            <button type="button" class="link-button" data-test="clear-filters" @click="clearFilters">ล้างตัวกรอง</button>
          </div>
        </header>
        <div class="directory-results">
          <div>
            <h2>รายชื่อนักกายภาพ</h2>
          </div>
          <span>ทั้งหมด {{ visible.length }} คน</span>
        </div>
        <div class="commission-table-wrap overview-table-scroll">
          <table class="commission-table overview-table">
            <thead>
              <tr>
                <th>นักกายภาพ</th>
                <th>อัตราคอมมิชชัน</th>
                <th class="numeric">ฐานคำนวณ</th>
                <th class="numeric">คอมมิชชัน</th>
                <th class="numeric">ค่ามือ</th>
                <th class="numeric">รายการปรับค่าตอบแทน</th>
                <th class="numeric">ยอดสุทธิ</th>
                <th>สถานะงวด</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pagedVisible" :key="row.id" :data-payment-status="row.paymentStatus ?? 'NONE'">
                <td>
                  <b>{{ row.name }}</b>
                </td>
                <td>{{ row.tier }}</td>
                <td class="numeric">{{ money(row.base) }}</td>
                <td class="numeric commission-value">{{ money(row.commission) }}</td>
                <td class="numeric">{{ money(row.treatment) }}</td>
                <td class="numeric">{{ row.adjustment ? money(row.adjustment) : '—' }}</td>
                <td class="numeric"><b>{{ money(row.net) }}</b></td>
                <td><span class="state-badge" :class="(row.paymentStatus ?? 'none').toLowerCase()">{{ paymentLabel(row.paymentStatus) }}</span></td>
                <td>
                  <div class="row-actions">
                    <button
                      v-if="row.paymentStatus === 'CLOSED'"
                      class="commission-primary row-action"
                      type="button"
                      data-test="pay-row"
                      @click="openDialog({ type: 'pay', row })"
                    >ยืนยันจ่าย</button>
                    <button
                      v-if="row.paymentStatus === 'CLOSED' && !anyPaid"
                      class="commission-secondary row-action"
                      type="button"
                      data-test="reopen-row"
                      @click="openDialog({ type: 'reopen', row })"
                    >เปิดรอบอีกครั้ง</button>
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
                  </div>
                </td>
              </tr>
              <tr v-if="!visible.length">
                <td colspan="9" class="empty-row">
                  <template v-if="hasFilters">
                    ไม่พบรายการที่ตรงกับตัวกรอง
                    <button type="button" class="link-button" @click="clearFilters">ล้างตัวกรอง</button>
                  </template>
                  <template v-else>
                    ยังไม่มีข้อมูลค่าตอบแทนของ{{ monthName }} ลองเลือกเดือนอื่น
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="visible.length > PAGE_SIZE" class="overview-pagination" aria-label="หน้ารายชื่อนักกายภาพ" data-test="list-pagination">
          <button type="button" :disabled="listPage === 1" data-test="list-prev" @click="listPage--">← ก่อนหน้า</button>
          <span>หน้า {{ listPage }} จาก {{ listTotalPages }}</span>
          <button type="button" :disabled="listPage === listTotalPages" data-test="list-next" @click="listPage++">ถัดไป →</button>
        </nav>
      </section>
      <section class="commission-directory commission-card history-card" data-test="closing-history">
        <div class="directory-results">
          <div>
            <h2>ประวัติการปิดรอบเดือน</h2>
          </div>
        </div>
        <div class="commission-table-wrap overview-table-scroll">
          <table class="commission-table history-table">
            <thead>
              <tr>
                <th>เดือนขาย</th>
                <th>สถานะ</th>
                <th>ปิดโดย</th>
                <th class="numeric">เปิดรอบอีกครั้ง</th>
                <th>จ่ายเมื่อ</th>
                <th class="numeric">ยอดจ่ายรวม</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in pagedHistory" :key="item.month">
                <tr class="history-row" tabindex="0" :aria-expanded="expandedMonth === item.month" @click="toggleMonth(item.month)" @keydown.enter="toggleMonth(item.month)">
                  <td>{{ expandedMonth === item.month ? '▾' : '▸' }} {{ monthLabel(item.month) }}</td>
                  <td><span class="state-badge" :class="item.status === 'FINALIZED' ? 'paid' : item.status === 'OPEN' ? 'open' : 'closed'">{{ historyStatusLabel[item.status] }}</span></td>
                  <td>{{ item.closedBy ?? '—' }}</td>
                  <td class="numeric">{{ item.reopenCount ? `${item.reopenCount} ครั้ง` : '—' }}</td>
                  <td>{{ item.lastPaidDate ?? '—' }}</td>
                  <td class="numeric">{{ item.paidTotal ? money(item.paidTotal) : '—' }}</td>
                </tr>
                <tr v-if="expandedMonth === item.month" class="history-detail">
                  <td colspan="6">
                    <ol class="history-timeline">
                      <li v-for="(event, index) in item.events" :key="index" :class="`kind-${event.kind}`">
                        <b>{{ event.title }}</b>
                        <small>{{ dateTime(event.at) }}<template v-if="event.by"> · {{ event.by }}</template><template v-if="event.detail"> · {{ event.detail }}</template></small>
                        <small v-if="event.reason">เหตุผล: {{ event.reason }}</small>
                      </li>
                      <li v-if="!item.events.length"><small>ยังไม่มีเหตุการณ์</small></li>
                    </ol>
                  </td>
                </tr>
              </template>
              <tr v-if="!history.length">
                <td colspan="6" class="empty-row">ยังไม่มีประวัติการปิดรอบของสาขานี้</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="history.length > PAGE_SIZE" class="overview-pagination" aria-label="หน้าประวัติการปิดรอบ" data-test="history-pagination">
          <button type="button" :disabled="historyPage === 1" data-test="history-prev" @click="historyPage--">← ก่อนหน้า</button>
          <span>หน้า {{ historyPage }} จาก {{ historyTotalPages }}</span>
          <button type="button" :disabled="historyPage === historyTotalPages" data-test="history-next" @click="historyPage++">ถัดไป →</button>
        </nav>
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
  margin-bottom: 22px;
}
.round-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px 40px;
  margin: 20px 0 0;
  padding: 20px 24px;
  border-radius: 14px;
  box-shadow: 0 3px 14px rgb(31 67 58 / 5%);
}
.round-period { align-self: start; }
.round-dates { display: flex; align-items: center; gap: 8px; color: #45675d; font-size: 0.86rem; font-weight: 400; }
.round-dates .state-badge { margin-left: 0; }
.round-card .stage-row { width: 100%; max-width: 520px; justify-self: center; margin: 0; }
.round-actions { align-self: start; padding-top: 24px; }
.round-actions > button { min-width: 140px; min-height: 44px; }
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
  background: #fff;
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
.commission-metrics article b.metric-placeholder {
  color: #7b8a84;
  font-size: 1rem;
  font-weight: 500;
}
.commission-directory {
  border-radius: 14px;
  box-shadow: 0 3px 14px rgb(31 67 58 / 5%);
}
.overview-filters {
  display: grid;
  grid-template-columns: 1fr;
  align-items: end;
  gap: 16px 24px;
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
  width: 100%;
  box-sizing: border-box;
  height: 44px;
  padding: 0 38px 0 14px;
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
.month-stepper-row { display: flex; align-items: center; gap: 12px; }
.month-stepper { display: flex; flex: none; align-items: center; height: 48px; border: 1px solid #b9c8c2; border-radius: 12px; background: #fff; overflow: hidden; }
.year-select {
  flex: none;
  box-sizing: border-box;
  height: 48px;
  width: 150px;
  padding: 0 40px 0 18px;
  border: 1px solid #b9c8c2;
  border-radius: 12px;
  background: #fff url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%2317653f' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") no-repeat right 16px center;
  appearance: none;
  -webkit-appearance: none;
  color: #25372f;
  font: inherit;
  font-size: 1rem;
  cursor: pointer;
}
.year-select:focus-visible { outline: 3px solid rgb(30 139 134 / 18%); border-color: #1e8b86; }
.month-stepper strong { min-width: 96px; padding: 0 4px; text-align: center; font-weight: 700; white-space: nowrap; }
.month-stepper button { flex: none; width: 40px; height: 100%; border: 0; background: transparent; color: #17653f; font-size: 1.6rem; font-weight: 700; line-height: 1; cursor: pointer; }
.month-stepper button:hover:not(:disabled) { background: #eef6f2; }
.month-stepper button:disabled { color: #b4c0bb; cursor: not-allowed; }
.search-box { position: relative; display: block; }
.search-clear { position: absolute; top: 50%; right: 6px; transform: translateY(-50%); width: 28px; height: 28px; border: 0; border-radius: 50%; background: transparent; color: #7b8a84; cursor: pointer; }
.search-clear:hover { background: #eef1ef; }
.active-filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.filter-chip { display: inline-flex; align-items: center; gap: 6px; padding: 3px 6px 3px 12px; border-radius: 999px; background: #eef1ef; color: #25372f; font-size: 0.84rem; font-weight: 400; }
.filter-chip button { width: 18px; height: 18px; padding: 0; border: 0; background: transparent; color: #7b8a84; font-size: 0.72rem; cursor: pointer; }
.link-button { padding: 0 4px; border: 0; background: transparent; color: #1b4f82; font: inherit; font-size: 0.84rem; font-weight: 400; cursor: pointer; }
.link-button:hover { text-decoration: underline; }
.overview-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 22px; border-top: 1px solid #e7ede9; color: #50645a; }
.overview-pagination button { min-height: 40px; padding: 0 14px; border: 1px solid #b7cecc; border-radius: 8px; background: #fff; color: #145c59; font: inherit; font-weight: 700; cursor: pointer; }
.overview-pagination button:disabled { opacity: 0.45; cursor: not-allowed; }
.overview-pagination button:focus-visible { outline: 2px solid #145c59; outline-offset: 3px; }
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
    grid-template-columns: 1fr;
  }
  .round-card {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .round-card .stage-row { max-width: none; }
  .round-actions { padding-top: 0; align-items: stretch; }
}
@media (max-width: 560px) {
  .commission-manager-main {
    width: calc(100% - 28px);
    padding-top: 24px;
  }
  .round-card {
    padding: 16px;
  }
  .month-stepper strong {
    min-width: 0;
    flex: 1;
  }
  .month-stepper {
    flex: 1;
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
<style scoped>
.overview-actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }
.commission-primary:disabled, .commission-secondary:disabled { cursor: not-allowed; background: #e4e9e7; border-color: #d3dcd8; color: #8a9893; }
.state-badge { display: inline-block; margin-left: 10px; padding: 2px 10px; border-radius: 999px; font-size: 0.8rem; font-weight: 600; white-space: nowrap; background: #eef1ef; color: #4a5d56; }
.state-badge.open, .state-badge.none { background: #fdf3dc; color: #7a5a10; }
.state-badge.closed { background: #e3effa; color: #1b4f82; }
.state-badge.paid { background: #e2f3e8; color: #17653f; }
.row-actions { display: flex; gap: 6px; justify-content: flex-end; flex-wrap: wrap; align-items: center; }
.row-action { padding: 4px 10px; font-size: 0.82rem; }
.stage-row { display: flex; align-items: flex-start; margin: 0; padding: 0; list-style: none; font-size: 0.84rem; }
.stage-row li { position: relative; flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; color: #7b8a84; }
.stage-row li:not(:first-child)::before { content: ""; position: absolute; top: 13px; right: 50%; width: 100%; height: 3px; border-radius: 2px; background: #d9e2de; }
.stage-row li.done:not(:first-child)::before, .stage-row li.cur:not(:first-child)::before { background: #17653f; }
.stage-dot { position: relative; z-index: 1; width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 1.5px solid #c3cfca; background: #fff; color: #7b8a84; font-weight: 600; font-size: 0.8rem; }
.stage-row li.done { color: #1f2d27; }
.stage-row li.done .stage-dot { background: #17653f; border-color: #17653f; color: #fff; }
.stage-row li.cur { color: #17653f; font-weight: 600; }
.stage-row li.cur .stage-dot { border-color: #17653f; color: #17653f; box-shadow: 0 0 0 4px #e2f3e8; }
.dialog-field { display: grid; gap: 6px; margin: 0 0 14px; font-size: 0.86rem; color: #25372f; }
.dialog-field input { padding: 8px 10px; border: 1px solid #cfdad5; border-radius: 8px; font: inherit; }
.dialog-warn { margin: 0 0 14px; padding: 8px 10px; border-radius: 8px; background: #fdf3dc; color: #7a5a10; font-size: 0.86rem; }
.dialog-note { margin: 0 0 14px; font-size: 0.84rem; color: #5b6e67; }
.dialog-error { margin: 0 0 14px; color: #b3261e; font-size: 0.86rem; }
.dialog-progress { margin: 0 0 14px; color: #1b4f82; font-size: 0.86rem; }
.history-card { margin-top: 20px; }
.history-row { cursor: pointer; }
.history-row:hover td { background: #f6faf8; }
.history-detail td { background: #f8fbf9; }
.history-timeline { margin: 4px 0 8px 14px; padding: 0 0 0 18px; list-style: none; border-left: 2px solid #d3e0da; }
.history-timeline li { position: relative; padding: 4px 0 10px; }
.history-timeline li::before { content: ""; position: absolute; left: -24px; top: 9px; width: 9px; height: 9px; border-radius: 50%; background: #8fa59c; }
.history-timeline li.kind-close::before { background: #2a6fb0; }
.history-timeline li.kind-pay::before, .history-timeline li.kind-finalize::before { background: #1f8a52; }
.history-timeline li.kind-reopen::before { background: #c58a14; }
.history-timeline small { display: block; color: #5b6e67; }
.commission-directory .commission-table.overview-table,
.commission-directory .commission-table.history-table { min-width: 0; table-layout: fixed; }
.commission-directory .commission-table.overview-table th,
.commission-directory .commission-table.history-table th { padding: 12px 8px; font-size: 13px; white-space: normal; line-height: 1.3; }
.commission-directory .commission-table.overview-table td,
.commission-directory .commission-table.history-table td { padding: 12px 8px; font-size: 14px; white-space: normal; overflow-wrap: anywhere; }
.commission-directory .commission-table.overview-table :is(th, td):first-child,
.commission-directory .commission-table.history-table :is(th, td):first-child { padding-left: 16px; }
.commission-directory .commission-table.overview-table :is(th, td):last-child { padding-right: 16px; text-align: right; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(1) { width: 14%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(2) { width: 9%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(3) { width: 10%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(4) { width: 10%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(5) { width: 8%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(6) { width: 9%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(7) { width: 10%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(8) { width: 12%; }
.commission-directory .commission-table.overview-table :is(th, td):nth-child(9) { width: 18%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(1) { width: 22%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(2) { width: 17%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(3) { width: 18%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(4) { width: 10%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(5) { width: 15%; }
.commission-directory .commission-table.history-table :is(th, td):nth-child(6) { width: 18%; text-align: right; }
.commission-directory .commission-table.overview-table .state-badge { margin-left: 0; padding: 2px 8px; font-size: 0.76rem; white-space: normal; text-align: center; }
.commission-directory .commission-table.overview-table .row-actions { flex-direction: column; align-items: stretch; gap: 6px; }
.commission-directory .commission-table.overview-table .row-action,
.commission-directory .commission-table.overview-table .detail-button { min-width: 0; width: 100%; min-height: 0; padding: 6px 8px; font-size: 0.82rem; text-align: center; }
.commission-directory .overview-table-scroll { overflow-x: visible; }
@media (max-width: 900px) {
  .commission-directory .overview-table-scroll { overflow-x: auto; }
  .commission-directory .commission-table.overview-table { min-width: 880px; }
}
.overview-close-dialog { text-align: center; }
.overview-close-dialog h2 { font-size: 1.4rem; line-height: 1.3; }
.overview-close-dialog .dialog-period { margin: 0 0 24px; color: #4a5d56; font-size: 1rem; }
.overview-close-dialog .dialog-field, .overview-close-dialog .dialog-warn, .overview-close-dialog .dialog-note, .overview-close-dialog .dialog-error { text-align: left; }
.overview-close-dialog > div.dialog-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; justify-content: stretch; }
.overview-close-dialog .dialog-actions button { width: 100%; }
</style>
