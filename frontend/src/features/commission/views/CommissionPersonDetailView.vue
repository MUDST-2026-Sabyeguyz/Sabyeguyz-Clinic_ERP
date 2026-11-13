<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import StaffHeader from "@/features/staff/components/StaffHeader.vue";
import { commissionApi, staffApi } from "@/api/client";
import { selectedManagerBranch } from "@/features/staff/data/managerBranch";
import { clinicToday } from "@/utils/clinicDate";
type TreatmentRow = Awaited<
  ReturnType<typeof commissionApi.treatmentReport>
>[number];
const route = useRoute();
const staffId = computed(() => {
  const n = Number(String(route.params.staffId ?? "").replace(/^PT-0*/i, ""));
  return Number.isFinite(n) ? n : 0;
});
const month = computed(() =>
  String(route.query.month || clinicToday().slice(0, 7)).slice(0, 7),
);
const selectedBranchCode = computed(() => selectedManagerBranch()?.code ?? "");
const overviewQuery = computed(() => ({
  month: month.value,
  ...(typeof route.query.search === "string" && route.query.search ? { search: route.query.search } : {}),
  ...(route.query.status === "pending" || route.query.status === "approved" ? { status: route.query.status } : {}),
}));
const from = computed(() => `${month.value}-01`);
const to = computed(() => {
  const [y, m] = month.value.split("-").map(Number);
  return `${month.value}-${String(new Date(y!, m!, 0).getDate()).padStart(2, "0")}`;
});
const loading = ref(true),
  errorMessage = ref(""),
  name = ref(""),
  branchName = ref("");
const base = ref(0),
  rate = ref(0),
  commission = ref(0),
  saleCount = ref(0);
type Tier = {
  minimum: number;
  maximum: number | null;
  rate: number;
  order: number;
};
const tiers = ref<Tier[]>([]);
const employeeCode = ref("");
const tierNotice = ref("");
const currentTier = computed(() =>
  tiers.value.find(
    (t) =>
      base.value >= Number(t.minimum) &&
      (t.maximum === null || base.value <= Number(t.maximum)),
  ),
);
const nextTier = computed(() =>
  tiers.value.find((t) => Number(t.minimum) > base.value),
);
const tierProgress = computed(() =>
  nextTier.value
    ? Math.min(100, (base.value / Number(nextTier.value.minimum)) * 100)
    : 100,
);
const treatments = ref<TreatmentRow[]>([]),
  closingId = ref<number | null>(null),
  approved = ref(false),
  finalized = ref(false);
const validationMessage = ref(""),
  saving = ref(false);
const reopenReason = ref("");
const treatmentTotal = computed(() =>
  treatments.value.reduce(
    (sum, item) => sum + Number(item.treatmentFee || 0),
    0,
  ),
);
const netTotal = computed(() => commission.value + treatmentTotal.value);
const money = (value: number) =>
  `฿${value.toLocaleString("th-TH", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat("th-TH", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${value}T00:00:00`));
const monthLabel = computed(() =>
  new Intl.DateTimeFormat("th-TH", { month: "long", year: "numeric" }).format(
    new Date(`${month.value}-01T00:00:00`),
  ),
);
async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    // อ่านข้อมูลรายงาน ปิดยอด และงวดจ่ายของพนักงานในเดือน/สาขาเดียวกัน
    if (!selectedBranchCode.value) throw new Error("กรุณาเลือกสาขาก่อนดูรายละเอียดค่าตอบแทน");
    const [report, treatmentReport, closings, payments] = await Promise.all([
      commissionApi.report(from.value, to.value),
      commissionApi.treatmentReport(from.value, to.value),
      commissionApi.closings(month.value, selectedBranchCode.value),
      commissionApi.payments(month.value, selectedBranchCode.value),
    ]);
    const person = report.find(
      (item) => item.staffId === staffId.value && item.branchCode === selectedBranchCode.value,
    );
    if (!person) {
      errorMessage.value = "ไม่พบข้อมูลค่าตอบแทนของบุคลากรรายนี้";
      return;
    }
    name.value = person.staffName;
    branchName.value = person.branchName;
    base.value = Number(person.base);
    rate.value = Number(person.rate) * 100;
    commission.value = Number(person.commission);
    saleCount.value = Number(person.saleCount);
    treatments.value = treatmentReport.filter(
      (item) =>
        item.treatingStaffId === staffId.value &&
        item.branchCode === selectedBranchCode.value &&
        item.ownerStaffId !== item.treatingStaffId,
    );
    const closing = closings.find((item) => item.employeeId === staffId.value);
    const payment = payments.find((item) => item.staffId === staffId.value);
    // สถานะ PAID ถือว่าจ่ายครบ ส่วนยอดที่ API ไม่ส่งมาจะไม่แทนด้วยศูนย์
    closingId.value = closing?.id ?? null;
    finalized.value = closing?.status === "FINALIZED" || payment?.status === "PAID";
    approved.value = closing?.status === "CLOSED" || finalized.value;
    const staff = await staffApi.get(staffId.value).catch(() => null);
    employeeCode.value = staff?.employeeCode ?? String(staffId.value);
    tiers.value = [];
    tierNotice.value = "";
    try {
      const schemes = (await commissionApi.schemes()) as Array<{
        id: number;
        version: number;
        active: boolean;
        effectiveFrom: string;
        effectiveTo: string | null;
      }>;
      const scheme = schemes.find((s) =>
        closing
          ? String(s.version) === String(closing.version)
          : s.active &&
            s.effectiveFrom <= to.value &&
            (!s.effectiveTo || s.effectiveTo > to.value),
      );
      if (scheme)
        tiers.value = ((await commissionApi.tiers(scheme.id)) as Tier[]).sort(
          (a, b) => a.order - b.order,
        );
      if (!tiers.value.length)
        tierNotice.value = "ยังไม่มีข้อมูลช่วง Tier ของรอบนี้";
    } catch {
      tierNotice.value = "โหลดข้อมูลช่วง Tier ไม่สำเร็จ";
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "โหลดข้อมูลค่าตอบแทนไม่สำเร็จ";
  } finally {
    loading.value = false;
  }
}
async function reopen() {
  const reason = reopenReason.value.trim();
  if (closingId.value === null || finalized.value || !reason || reason.length > 255 || saving.value) return;
  saving.value = true;
  validationMessage.value = "";
  try {
    await commissionApi.reopenClosing(closingId.value, reason);
    reopenReason.value = "";
    await load();
  } catch (error) {
    validationMessage.value = error instanceof Error ? error.message : "เปิดรอบรายบุคคลไม่สำเร็จ";
  } finally {
    saving.value = false;
  }
}
onMounted(load);
watch([staffId, month, selectedBranchCode], load);
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <RouterLink class="detail-back" :to="{ path: '/staff/commission', query: overviewQuery }">← กลับไปภาพรวมค่าตอบแทน</RouterLink>
      <header class="detail-head">
        <div>
          <h1>รายละเอียดค่าตอบแทนรายบุคคล</h1>
          <p v-if="name">
            {{ name }} · {{ employeeCode }} · {{ branchName }} · งวด{{
              monthLabel
            }}
          </p>
        </div>
        <span
          v-if="!loading && !errorMessage"
          class="commission-status"
          :class="{ ready: approved }"
          >{{ approved ? "อนุมัติแล้ว" : "รอตรวจสอบ" }}</span
        >
      </header>
      <p v-if="loading" class="state-card">กำลังโหลดข้อมูลจริง…</p>
      <p v-else-if="errorMessage" class="state-card error">
        {{ errorMessage }}
      </p>
      <template v-else>
        <section class="commission-metrics">
          <article>
            <span>ฐานคำนวณ</span><b>{{ money(base) }}</b
            ><small>หลังส่วนลดรายรายการ · {{ saleCount }} รายการขาย</small>
          </article>
          <article class="highlight">
            <span>ค่าคอมมิชชัน</span><b>{{ money(commission) }}</b
            ><small
              >{{ currentTier ? `Tier ${currentTier.order} · ` : ""
              }}{{ rate.toLocaleString("th-TH") }}%</small
            >
          </article>
          <article>
            <span>ค่ามือ</span><b>{{ money(treatmentTotal) }}</b
            ><small>{{ treatments.length }} รายการ</small>
          </article>
          <article class="warning">
            <span>ยอดสุทธิ</span><b>{{ money(netTotal) }}</b
            ><small>{{ approved ? "อนุมัติแล้ว" : "รออนุมัติ" }}</small>
          </article>
        </section>
        <section class="detail-columns">
          <article class="commission-card tier-detail">
            <h2>Tier Commission</h2>
            <div class="tier-box">
              <b
                >{{ currentTier ? `Tier ${currentTier.order}` : "อัตราตามรอบ"
                }}<em>{{ rate.toLocaleString("th-TH") }}%</em></b
              >
              <small>ยอดขายเดือนนี้ {{ money(base) }}</small>
              <div
                v-if="currentTier"
                class="tier-progress"
                role="progressbar"
                :aria-valuenow="Math.round(tierProgress)"
                aria-valuemin="0"
                aria-valuemax="100"
                aria-label="ความคืบหน้ายอดขาย"
              >
                <span :style="{ width: `${tierProgress}%` }"></span>
              </div>
              <small v-if="nextTier"
                >อีก {{ money(Number(nextTier.minimum) - base) }} ถึง Tier
                {{ nextTier.order }}</small
              >
              <small v-else-if="currentTier">อยู่ใน Tier สูงสุด</small>
              <small v-if="tierNotice">{{ tierNotice }}</small>
            </div>
            <h3>ที่มาของฐานคำนวณ</h3>
            <dl>
              <div>
                <dt>ยอดบริการก่อนส่วนลด</dt>
                <dd>—</dd>
              </div>
              <div>
                <dt>ส่วนลดรายรายการ</dt>
                <dd>—</dd>
              </div>
              <div class="total">
                <dt>ฐานค่าคอมมิชชัน</dt>
                <dd>{{ money(base) }}</dd>
              </div>
              <div>
                <dt>ส่วนลดท้ายบิล (ไม่หักฐาน)</dt>
                <dd>—</dd>
              </div>
            </dl>
          </article>
          <article class="commission-card treatment-detail">
            <h2>รายละเอียดค่ามือ</h2>
            <p>
              ค่าตอบแทนจากการรักษาที่ให้บริการจริง ·
              {{ treatments.length }} รายการ
            </p>
            <p v-if="!treatments.length" class="empty-treatment">
              ไม่มีรายการรักษาแทนในรอบนี้
            </p>
            <div
              v-for="item in treatments"
              :key="item.id"
              class="treatment-row"
            >
              <span>{{ thaiDate(item.visitDate) }} {{ item.courseCode }}</span
              ><b>{{ money(Number(item.treatmentFee)) }}</b>
            </div>
            <div class="treatment-total">
              <b>รวมค่ามือ</b><b>{{ money(treatmentTotal) }}</b>
            </div>
          </article>
        </section>
        <footer v-if="!approved" class="detail-actions">
          <p>การปิดคอมมิชชันทำเป็นรอบระดับสาขาตามระบบหลัก</p>
          <RouterLink
            class="commission-primary"
            :to="{ path: '/staff/commission/closing', query: { month: month } }"
          >
            ไปตรวจและปิดรอบสาขา
          </RouterLink>
          <p v-if="validationMessage" class="action-error" role="alert">{{ validationMessage }}</p>
        </footer>
        <section v-else class="approved-note">
          <p>✓ ปิดยอดรายบุคคลแล้ว</p>
          <template v-if="finalized">
            <p>งวดนี้จ่ายแล้วและปิดแก้ไขถาวร หากต้องแก้ยอดให้สร้างรายการปรับยอดในงวดถัดไป</p>
          </template>
          <template v-else>
            <label>เหตุผลที่เปิดยอดอีกครั้ง<textarea v-model="reopenReason" maxlength="255" rows="2" /></label>
            <button class="commission-secondary" :disabled="saving || !reopenReason.trim()" @click="reopen">เปิดยอดรายบุคคลอีกครั้ง</button>
          </template>
        </section>
      </template>
    </main>
  </div>
</template>
<style scoped>
@import "../styles/commission-manager.css";
.detail-back {
  display: inline-flex;
  align-items: center;
  margin: 0 0 20px;
  color: #155f5c;
  font-weight: 600;
  text-decoration: none;
  text-underline-offset: 4px;
  transition: color 160ms ease, transform 160ms ease;
}
.detail-back:hover { color: #0d4745; text-decoration: underline; transform: translateX(-3px); }
.detail-back:focus-visible { outline: 3px solid #16877f; outline-offset: 4px; border-radius: 3px; }
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}
.detail-head h1 {
  margin: 0;
  font-size: clamp(2rem, 3vw, 2.15rem);
  line-height: 1.3;
}
.detail-head p {
  margin: 4px 0 0;
  color: #63706b;
}
.detail-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.detail-columns article {
  min-height: 620px;
  box-sizing: border-box;
  padding: 28px;
  border-color: #dbded6;
}
.detail-columns h2 {
  margin: 0 0 16px;
  color: #1f2e29;
  font-size: 1.38rem;
}
.tier-box {
  width: 100%;
  box-sizing: border-box;
  display: grid;
  gap: 12px;
  padding: 20px;
  border: 1px solid var(--color-border-soft);
  border-radius: 10px;
}
.tier-box b {
  display: flex;
  justify-content: space-between;
}
.tier-box em {
  color: #1e8b86;
  font-style: normal;
}
.tier-progress {
  height: 8px;
  border-radius: 99px;
  background: #e2ebe6;
  overflow: hidden;
}
.tier-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
}
.breakdown-note {
  font-size: 12px;
  color: #667a72;
}
.tier-detail h3 {
  margin: 16px 0 8px;
  font-size: 18px;
}
.tier-detail dd {
  margin: 0;
  text-align: right;
}
.tier-detail dl {
  display: grid;
  gap: 4px;
}
.tier-detail dl div {
  display: flex;
  justify-content: space-between;
  min-height: 40px;
  box-sizing: border-box;
  align-items: center;
  padding: 0 12px;
}
.tier-detail .total {
  border-radius: 8px;
  background: #eaf5f1;
  font-weight: 700;
}
.treatment-detail > p {
  margin: -6px 0 6px;
  color: #63706b;
}
.treatment-row,
.treatment-total {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  box-sizing: border-box;
  align-items: center;
  padding: 0 12px;
}
.treatment-total {
  min-height: 50px;
  margin-top: 10px;
  border-radius: 8px;
  background: #eaf5f1;
}
.net-note {
  min-height: 48px;
  margin: 0;
  color: #13635e;
  font-size: 1rem;
  font-weight: 500;
}
.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 20px 0 24px;
}
.detail-actions button {
  min-width: 200px;
  min-height: 48px;
  justify-content: center;
}
.detail-actions .commission-secondary {
  width: 220px;
  border: 0;
  background: #56665d33;
}
.detail-actions button:disabled {
  cursor: not-allowed;
  opacity: 1;
}
.approved-note {
  margin-top: 16px !important;
  padding: 12px;
  border-radius: 8px;
  background: #e7f4ef;
  color: var(--color-primary) !important;
  font-weight: 700;
}
.state-card {
  padding: 24px;
  border-radius: 12px;
  background: #fff;
}
.state-card.error {
  background: #fff1ef;
  color: #b42318;
}
.empty-treatment {
  padding: 16px;
  border-radius: 8px;
  background: #f4f6f5;
}
.action-backdrop {
  position: fixed;
  z-index: 9999;
  inset: 0;
  display: grid;
  width: 100vw;
  height: 100dvh;
  box-sizing: border-box;
  place-items: center;
  padding: 20px;
  background: #26342d80;
  overflow-y: auto;
}
.action-modal {
  width: min(460px, 100%);
  box-sizing: border-box;
  padding: 28px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 50px #0003;
}
.action-modal h2 {
  margin: 0;
  color: var(--color-ink);
  text-align: center;
}
.action-modal > p {
  color: #667a72;
  text-align: center;
}
.approval-summary { margin: 20px 0 0; padding: 4px 16px; border-radius: 12px; background: #eef6f3; }
.approval-summary div { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 12px 0; }
.approval-summary div + div { border-top: 1px solid #d6e6e0; }
.approval-summary dt { color: #526e66; }
.approval-summary dd { margin: 0; color: #243a33; font-weight: 700; text-align: right; }
.approval-summary .approval-total dd { color: #116a64; font-size: 1.25rem; font-variant-numeric: tabular-nums; }
.action-modal > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 20px;
}
.action-error {
  padding: 9px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318 !important;
}
.action-modal button {
  justify-content: center;
  min-height: 46px;
}
.action-modal button:focus-visible { outline: 3px solid #16877f; outline-offset: 3px; }
.action-modal .commission-primary {
  border: 0;
}
@media (max-width: 800px) {
  .detail-columns {
    grid-template-columns: 1fr;
  }
  .detail-head {
    flex-direction: column;
  }
  .detail-head h1 {
    font-size: 2rem;
  }
}
@media (max-width: 480px) {
  .detail-actions,
  .action-modal > div {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
  .detail-actions button {
    width: 100%;
  }
}
</style>
