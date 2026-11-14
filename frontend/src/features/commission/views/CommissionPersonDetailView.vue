<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, staffApi, type ManagerAdjustment } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'
type TreatmentRow = Awaited<ReturnType<typeof commissionApi.treatmentReport>>[number]
type SingleFeeRow = Awaited<ReturnType<typeof commissionApi.singleVisitFees>>[number]
type SaleRow = Awaited<ReturnType<typeof commissionApi.report>>[number]['sales'][number]
type ListTab = 'all' | 'sales' | 'fees' | 'adjustments'
const activeList = ref<ListTab>('all')
function showList(list: ListTab) {
  activeList.value = list
  requestAnimationFrame(() => {
    document
      .getElementById('commission-source-list')
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
const sales = ref<SaleRow[]>([])
const adjustments = ref<ManagerAdjustment[]>([])
const adjustmentRows = computed(() =>
  adjustments.value.flatMap((item) => {
    const applications = item.applications.filter(
      (application) => application.month === month.value && application.status !== 'Cancelled',
    )
    if (applications.length)
      return applications.map((application) => ({
        key: `${item.id}-${application.id}`,
        adjustmentId: item.id,
        amount: Number(application.amount),
        reason: item.reason,
        approverName: item.approverName || 'ยังไม่อนุมัติ',
        appliedMonth: application.month || month.value,
      }))
    return item.appliedMonth === month.value
      ? [
          {
            key: String(item.id),
            adjustmentId: item.id,
            amount: Number(item.amount),
            reason: item.reason,
            approverName: item.approverName || 'ยังไม่อนุมัติ',
            appliedMonth: item.appliedMonth,
          },
        ]
      : []
  }),
)
const route = useRoute()
const staffId = computed(() => {
  const n = Number(String(route.params.staffId ?? '').replace(/^PT-0*/i, ''))
  return Number.isFinite(n) ? n : 0
})
const month = computed(() => String(route.query.month || clinicToday().slice(0, 7)).slice(0, 7))
const selectedBranchCode = computed(() => selectedManagerBranch()?.code ?? '')
const overviewQuery = computed(() => ({
  month: month.value,
  ...(typeof route.query.search === 'string' && route.query.search
    ? { search: route.query.search }
    : {}),
  ...(route.query.status === 'pending' || route.query.status === 'approved'
    ? { status: route.query.status }
    : {}),
}))
const from = computed(() => `${month.value}-01`)
const to = computed(() => {
  const [y, m] = month.value.split('-').map(Number)
  return `${month.value}-${String(new Date(y!, m!, 0).getDate()).padStart(2, '0')}`
})
const loading = ref(true),
  errorMessage = ref(''),
  name = ref(''),
  branchName = ref('')
const base = ref(0),
  rate = ref(0),
  commission = ref(0),
  saleCount = ref(0)
type Tier = {
  minimum: number
  maximum: number | null
  rate: number
  order: number
}
const tiers = ref<Tier[]>([])
const employeeCode = ref('')
const tierNotice = ref('')
const currentTier = computed(() =>
  tiers.value.find(
    (t) =>
      base.value >= Number(t.minimum) && (t.maximum === null || base.value <= Number(t.maximum)),
  ),
)
const treatments = ref<TreatmentRow[]>([]),
  closingId = ref<number | null>(null),
  approved = ref(false),
  finalized = ref(false)
const singleFees = ref<SingleFeeRow[]>([])
type EditTarget = { path: string; query?: Record<string, string> }
// ปุ่ม "แก้ไข" พาไปหน้าต้นทางของรายการ (ยกเลิกรายการเดิมแล้วบันทึกใหม่)
const saleEdit = (sale: SaleRow): EditTarget => ({
  path: '/staff/transactions',
  query: { tab: 'sales', search: sale.receiptNo },
})
const adjustmentEdit = (adjustmentId: number): EditTarget => ({
  path: `/staff/commission/adjustments/${adjustmentId}`,
})
const feeRows = computed(() => [
  ...treatments.value.map((item) => ({
    ...item,
    rowKey: `course-${item.id}`,
    edit: { path: '/staff/transactions', query: { usageId: String(item.id) } } as EditTarget | null,
    alwaysEditable: false,
  })),
  ...singleFees.value.map((item) => ({
    ...item,
    rowKey: `single-${item.id}`,
    // ค่ามือรายครั้งแก้ได้โดยยกเลิก (Void) ใบเสร็จของการขายนั้น ถ้าไม่มีใบเสร็จ (มาจากสิทธิ์ล่วงหน้า) จะไม่มีปุ่ม
    edit: (item.receiptNo
      ? { path: '/staff/transactions', query: { tab: 'sales', search: item.receiptNo } }
      : null) as EditTarget | null,
    alwaysEditable: false,
  })),
])
// รวมรายการขายคอร์ส ค่ามือ และรายการปรับยอดไว้ตารางเดียว (ขายคอร์ส/ค่ามือเรียงวันที่ใหม่สุดก่อน ปรับยอดไว้ท้าย)
const allRows = computed(() => {
  const dated = [
    ...sales.value.map((sale) => ({
      key: `sale-${sale.id}`,
      type: 'ขายคอร์ส',
      date: sale.saleDate,
      detail: sale.courseName,
      party: sale.patientName,
      amount: Number(sale.commissionableAmount),
      edit: saleEdit(sale) as EditTarget | null,
      alwaysEditable: false,
    })),
    ...feeRows.value.map((item) => ({
      key: item.rowKey,
      type: 'ค่ามือ',
      date: item.visitDate,
      detail: item.serviceName,
      party: item.patientName,
      amount: Number(item.treatmentFee),
      edit: item.edit,
      alwaysEditable: false,
    })),
  ].sort((a, b) => b.date.localeCompare(a.date))
  const adjusted = adjustmentRows.value.map((item) => ({
    key: `adjustment-${item.key}`,
    type: 'ปรับยอด',
    date: '',
    detail: item.reason,
    party: item.approverName,
    amount: item.amount,
    edit: adjustmentEdit(item.adjustmentId) as EditTarget | null,
    alwaysEditable: true,
  }))
  return [...dated, ...adjusted]
})
// แก้ได้เฉพาะตอนยังไม่ปิด (หรือ Reopen แล้ว) ส่วนรายการปรับยอดอยู่ในงวดถัดไปจึงแก้ได้เสมอ
const canEdit = (row: { alwaysEditable?: boolean }) =>
  !!row.alwaysEditable || (!approved.value && !finalized.value)
const editBlockedReason = computed(() =>
  finalized.value
    ? 'งวดนี้ปิดถาวรแล้ว ให้สร้างรายการปรับค่าตอบแทนในงวดถัดไป'
    : 'เปิดรอบอีกครั้งก่อนจึงจะแก้ไขได้',
)
const validationMessage = ref(''),
  saving = ref(false)
const reopenReason = ref('')
const treatmentTotal = computed(() =>
  feeRows.value.reduce((sum, item) => sum + Number(item.treatmentFee || 0), 0),
)
const adjustmentTotal = computed(() =>
  adjustmentRows.value.reduce((sum, item) => sum + item.amount, 0),
)
const netTotal = computed(() => commission.value + treatmentTotal.value)
const money = (value: number) =>
  `฿${value.toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
const monthLabel = computed(() =>
  new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(`${month.value}-01T00:00:00`),
  ),
)
async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    // อ่านข้อมูลรายงาน ปิดยอด และงวดจ่ายของพนักงานในเดือน/สาขาเดียวกัน
    if (!selectedBranchCode.value) throw new Error('กรุณาเลือกสาขาก่อนดูรายละเอียดค่าตอบแทน')
    const [report, treatmentReport, closings, payments, allAdjustments, singleVisitFees] =
      await Promise.all([
        commissionApi.report(from.value, to.value),
        commissionApi.treatmentReport(from.value, to.value),
        commissionApi.closings(month.value, selectedBranchCode.value),
        commissionApi.payments(month.value, selectedBranchCode.value),
        commissionApi.adjustments(),
        commissionApi.singleVisitFees(
          staffId.value,
          selectedBranchCode.value,
          from.value,
          to.value,
        ),
      ])
    const person = report.find(
      (item) => item.staffId === staffId.value && item.branchCode === selectedBranchCode.value,
    )
    const payment = payments.find((item) => item.staffId === staffId.value)
    const personTreatments = treatmentReport.filter(
      (item) =>
        item.treatingStaffId === staffId.value &&
        item.branchCode === selectedBranchCode.value &&
        item.ownerStaffId !== item.treatingStaffId,
    )
    const personAdjustments = allAdjustments.filter(
      (item) =>
        item.staffId === staffId.value &&
        item.branchCode === selectedBranchCode.value &&
        (item.appliedMonth === month.value ||
          item.applications.some((application) => application.month === month.value)),
    )
    if (
      !person &&
      !payment &&
      !personTreatments.length &&
      !singleVisitFees.length &&
      !personAdjustments.length
    ) {
      errorMessage.value = 'ไม่พบข้อมูลค่าตอบแทนของบุคลากรรายนี้'
      return
    }
    name.value = person?.staffName ?? payment?.staffName ?? ''
    branchName.value = person?.branchName ?? selectedManagerBranch()?.name ?? ''
    base.value = Number(person?.base ?? 0)
    rate.value = Number(person?.rate ?? 0) * 100
    commission.value = Number(person?.commission ?? 0)
    saleCount.value = Number(person?.saleCount ?? 0)
    sales.value = person?.sales ?? []
    adjustments.value = personAdjustments
    treatments.value = personTreatments
    singleFees.value = singleVisitFees
    const closing = closings.find((item) => item.employeeId === staffId.value)
    // สถานะ PAID ถือว่าจ่ายครบ ส่วนยอดที่ API ไม่ส่งมาจะไม่แทนด้วยศูนย์
    closingId.value = closing?.id ?? null
    finalized.value = closing?.status === 'FINALIZED' || payment?.status === 'PAID'
    approved.value = closing?.status === 'CLOSED' || finalized.value
    const staff = await staffApi.get(staffId.value).catch(() => null)
    if (!name.value) name.value = staff?.fullName ?? String(staffId.value)
    employeeCode.value = staff?.employeeCode ?? String(staffId.value)
    tiers.value = []
    tierNotice.value = ''
    try {
      const schemes = (await commissionApi.schemes()) as Array<{
        id: number
        version: number
        active: boolean
        effectiveFrom: string
        effectiveTo: string | null
      }>
      const scheme = schemes.find((s) =>
        closing
          ? String(s.version) === String(closing.version)
          : s.active && s.effectiveFrom <= to.value && (!s.effectiveTo || s.effectiveTo > to.value),
      )
      if (scheme)
        tiers.value = ((await commissionApi.tiers(scheme.id)) as Tier[]).sort(
          (a, b) => a.order - b.order,
        )
      if (!tiers.value.length) tierNotice.value = 'ยังไม่มีข้อมูลช่วง Tier ของรอบนี้'
    } catch {
      tierNotice.value = 'โหลดข้อมูลช่วง Tier ไม่สำเร็จ'
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลค่าตอบแทนไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
async function reopen() {
  const reason = reopenReason.value.trim()
  if (closingId.value === null || finalized.value || !reason || reason.length > 255 || saving.value)
    return
  saving.value = true
  validationMessage.value = ''
  try {
    await commissionApi.reopenClosing(closingId.value, reason)
    reopenReason.value = ''
    await load()
  } catch (error) {
    validationMessage.value = error instanceof Error ? error.message : 'เปิดรอบรายบุคคลไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
onMounted(load)
watch([staffId, month, selectedBranchCode], load)
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <RouterLink class="detail-back app-back-link" :to="{ path: '/staff/commission', query: overviewQuery }"
        >← กลับไปภาพรวมค่าตอบแทน</RouterLink
      >
      <header class="detail-head">
        <div>
          <h1>รายละเอียดค่าตอบแทนรายบุคคล</h1>
          <p v-if="name">
            {{ name }} · {{ employeeCode }} · {{ branchName }} · งวด{{ monthLabel }}
          </p>
        </div>
        <span
          v-if="!loading && !errorMessage"
          class="commission-status"
          :class="{ ready: approved }"
          >{{ approved ? 'อนุมัติแล้ว' : 'รอตรวจสอบ' }}</span
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
            ><small>{{ saleCount }} รายการขาย</small>
          </article>
          <button
            type="button"
            class="metric-card-button"
            aria-label="ดูรายการขายคอร์ส"
            @click="showList('sales')"
          >
            <span>ค่าคอมมิชชัน</span><b>{{ money(commission) }}</b
            ><small
              >{{ currentTier ? `Tier ${currentTier.order} · ` : ''
              }}{{ rate.toLocaleString('th-TH') }}%</small
            >
          </button>
          <button
            type="button"
            class="metric-card-button"
            aria-label="ดูรายการค่ามือ"
            @click="showList('fees')"
          >
            <span>ค่ามือ</span><b>{{ money(treatmentTotal) }}</b
            ><small>{{ feeRows.length }} รายการ</small>
          </button>
          <button
            type="button"
            class="metric-card-button"
            aria-label="ดูรายการปรับยอด"
            @click="showList('adjustments')"
          >
            <span>รายการปรับยอด</span><b>{{ money(adjustmentTotal) }}</b
            ><small>{{ adjustmentRows.length }} รายการ</small>
          </button>
          <article class="commission-metric">
            <span>ยอดสุทธิ</span><b>{{ money(netTotal) }}</b
            ><small>{{ approved ? 'อนุมัติแล้ว' : 'รออนุมัติ' }}</small>
          </article>
        </section>
        <section id="commission-source-list" class="commission-card source-list">
          <div class="source-tabs" role="tablist" aria-label="รายการค่าตอบแทน">
            <button
              v-for="tab in [
                { id: 'all', label: 'รายการทั้งหมด', count: allRows.length },
                { id: 'sales', label: 'รายการขายคอร์ส', count: sales.length },
                { id: 'fees', label: 'รายการค่ามือ', count: feeRows.length },
                { id: 'adjustments', label: 'รายการปรับยอด', count: adjustmentRows.length },
              ] as const"
              :key="tab.id"
              type="button"
              role="tab"
              :aria-selected="activeList === tab.id"
              :class="{ selected: activeList === tab.id }"
              @click="activeList = tab.id"
            >
              {{ tab.label }} {{ tab.count }}
            </button>
          </div>
          <div v-if="activeList === 'all'" role="tabpanel">
            <h2>รายการทั้งหมด</h2>
            <p v-if="!allRows.length" class="source-empty">ไม่มีรายการในรอบนี้</p>
            <div v-else class="source-scroll">
              <table data-test="all-rows">
                <thead>
                  <tr>
                    <th>ประเภท</th>
                    <th>วันที่</th>
                    <th>รายละเอียด</th>
                    <th>ผู้ป่วย / ผู้อนุมัติ</th>
                    <th>จำนวนเงิน</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in allRows" :key="item.key">
                    <td>{{ item.type }}</td>
                    <td>{{ item.date ? thaiDate(item.date) : '—' }}</td>
                    <td>{{ item.detail }}</td>
                    <td>{{ item.party }}</td>
                    <td>{{ money(item.amount) }}</td>
                    <td class="edit-cell">
                      <template v-if="item.edit">
                        <RouterLink
                          v-if="canEdit(item)"
                          class="edit-link"
                          :to="item.edit"
                          data-test="edit-link"
                          >แก้ไข</RouterLink
                        >
                        <span v-else class="edit-link disabled" :title="editBlockedReason" data-test="edit-disabled">แก้ไข</span>
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else-if="activeList === 'sales'" role="tabpanel">
            <h2>รายการขายคอร์ส</h2>
            <p v-if="!sales.length" class="source-empty">ไม่มีรายการขายคอร์สในรอบนี้</p>
            <div v-else class="source-scroll">
              <table>
                <thead>
                  <tr>
                    <th>วันที่</th>
                    <th>ใบเสร็จ</th>
                    <th>ผู้ป่วย</th>
                    <th>คอร์ส</th>
                    <th>ราคาก่อนลด</th>
                    <th>ส่วนลด</th>
                    <th>ยอดสุทธิ</th>
                    <th>ผู้ขาย</th>
                    <th>คืนเงิน</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="sale in sales" :key="sale.id">
                    <td>{{ thaiDate(sale.saleDate) }}</td>
                    <td>{{ sale.receiptNo }}</td>
                    <td>{{ sale.patientName }}</td>
                    <td>{{ sale.courseName }}</td>
                    <td>{{ money(Number(sale.gross)) }}</td>
                    <td>{{ money(Number(sale.lineDiscount)) }}</td>
                    <td>{{ money(Number(sale.commissionableAmount)) }}</td>
                    <td>{{ sale.sellerName }}</td>
                    <td>{{ money(Number(sale.completedRefund)) }}</td>
                    <td class="edit-cell">
                      <RouterLink
                        v-if="canEdit({})"
                        class="edit-link"
                        :to="saleEdit(sale)"
                        data-test="edit-link"
                        >แก้ไข</RouterLink
                      >
                      <span v-else class="edit-link disabled" :title="editBlockedReason" data-test="edit-disabled">แก้ไข</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else-if="activeList === 'fees'" role="tabpanel">
            <h2>รายการค่ามือ</h2>
            <p v-if="!feeRows.length" class="source-empty">ไม่มีรายการค่ามือในรอบนี้</p>
            <div v-else class="source-scroll">
              <table>
                <thead>
                  <tr>
                    <th>วันที่รักษา</th>
                    <th>ผู้ป่วย</th>
                    <th>บริการ</th>
                    <th>ผู้รักษาจริง</th>
                    <th>กฎที่ใช้</th>
                    <th>ค่ามือ</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in feeRows" :key="item.rowKey">
                    <td>{{ thaiDate(item.visitDate) }}</td>
                    <td>{{ item.patientName }}</td>
                    <td>{{ item.serviceName }}</td>
                    <td>{{ item.treatingStaffName }}</td>
                    <td>{{ item.feeRuleName || '—' }}</td>
                    <td>{{ money(Number(item.treatmentFee)) }}</td>
                    <td class="edit-cell">
                      <template v-if="item.edit">
                        <RouterLink
                          v-if="canEdit(item)"
                          class="edit-link"
                          :to="item.edit"
                          data-test="edit-link"
                          >แก้ไข</RouterLink
                        >
                        <span v-else class="edit-link disabled" :title="editBlockedReason" data-test="edit-disabled">แก้ไข</span>
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else role="tabpanel">
            <h2>รายการปรับยอด</h2>
            <p v-if="!adjustmentRows.length" class="source-empty">ไม่มีรายการปรับยอดในรอบนี้</p>
            <div v-else class="source-scroll">
              <table>
                <thead>
                  <tr>
                    <th>ยอดปรับ</th>
                    <th>เหตุผล</th>
                    <th>ผู้อนุมัติ</th>
                    <th>งวดที่นำไปใช้</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in adjustmentRows" :key="item.key">
                    <td>{{ money(item.amount) }}</td>
                    <td>{{ item.reason }}</td>
                    <td>{{ item.approverName }}</td>
                    <td>{{ item.appliedMonth }}</td>
                    <td class="edit-cell">
                      <RouterLink
                        class="edit-link"
                        :to="adjustmentEdit(item.adjustmentId)"
                        data-test="edit-link"
                        >แก้ไข</RouterLink
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
        <footer v-if="!approved && validationMessage" class="detail-actions">
          <p v-if="validationMessage" class="action-error" role="alert">{{ validationMessage }}</p>
        </footer>
        <!-- งวดที่จ่ายแล้ว (Finalized) ไม่แสดงกล่องนี้ ปุ่ม "แก้ไข" ที่เทาอยู่บอกเหตุผลใน tooltip -->
        <section v-else-if="approved && !finalized" class="approved-note">
          <p>✓ ปิดยอดรายบุคคลแล้ว</p>
          <label
            >เหตุผลที่เปิดรอบอีกครั้ง<textarea v-model="reopenReason" maxlength="255" rows="2" />
          </label>
          <button
            class="commission-secondary"
            :disabled="saving || !reopenReason.trim()"
            @click="reopen"
          >
            เปิดรอบอีกครั้ง
          </button>
        </section>
      </template>
    </main>
  </div>
</template>
<style scoped>
@import '../styles/commission-manager.css';
.source-list {
  margin-top: 24px;
  padding: 24px;
  scroll-margin-top: 24px;
}
.commission-metrics {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.commission-metrics {
  gap: 14px;
  margin: 20px 0 22px;
}
/* การ์ดสรุปทั้ง 5 ใบเป็นกรอบสีขาวแบบเดียวกัน (ใบที่กดได้ต่างกันแค่ hover) */
.commission-metrics article,
.metric-card-button {
  display: block;
  width: 100%;
  min-height: 132px;
  padding: 21px 24px;
  box-sizing: border-box;
  border: 1px solid #d9e1dc;
  border-radius: 14px;
  background: #fff;
  color: inherit;
  box-shadow: 0 2px 8px rgb(31 67 58 / 4%);
}
.metric-card-button {
  text-align: left;
  font: inherit;
  cursor: pointer;
}
.metric-card-button b {
  display: block;
  margin: 10px 0 5px;
  font-size: 1.6rem;
}
.metric-card-button:hover {
  border-color: #16877f;
  background: #f3faf7;
}
.metric-card-button:focus-visible {
  outline: 3px solid #16877f;
  outline-offset: 3px;
}
@media (max-width: 1100px) {
  .commission-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 520px) {
  .commission-metrics {
    grid-template-columns: 1fr;
  }
}
.edit-cell {
  width: 1%;
  text-align: right;
  white-space: nowrap;
}
.edit-link {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid #8fb5ae;
  border-radius: 8px;
  background: #fff;
  color: #145c59;
  font-size: 0.86rem;
  font-weight: 700;
  text-decoration: none;
}
a.edit-link:hover {
  background: #f3faf7;
  border-color: #16877f;
}
.edit-link.disabled {
  border-color: #d3dcd8;
  background: #f1f4f2;
  color: #9aa7a1;
  cursor: not-allowed;
}
.source-list h2 {
  margin: 20px 0 14px;
  font-size: 1.3rem;
}
.source-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.source-tabs button {
  padding: 10px 16px;
  border: 1px solid #bbd4ce;
  border-radius: 8px;
  background: #fff;
  color: #145c59;
  font: inherit;
  cursor: pointer;
}
.source-tabs button.selected {
  background: #167d78;
  color: #fff;
}
.source-scroll {
  overflow-x: auto;
}
.source-list table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.source-list th,
.source-list td {
  padding: 12px;
  border-bottom: 1px solid #dce8e2;
  text-align: left;
  vertical-align: top;
}
.source-list th {
  background: #f0f7f3;
  white-space: nowrap;
}
.source-list td:nth-last-child(-n + 5) {
  white-space: nowrap;
}
.source-empty {
  padding: 18px;
  background: #f4f7f5;
  border-radius: 8px;
}
.detail-back {
  margin: 0 0 20px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 8px;
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
  display: block;
}
.detail-columns article {
  min-height: auto;
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
  border: 1px solid #d9e1dc;
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
  background: #1e807c;
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
  color: #1e807c !important;
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
  color: #25372e;
  text-align: center;
}
.action-modal > p {
  color: #667a72;
  text-align: center;
}
.approval-summary {
  margin: 20px 0 0;
  padding: 4px 16px;
  border-radius: 12px;
  background: #eef6f3;
}
.approval-summary div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 0;
}
.approval-summary div + div {
  border-top: 1px solid #d6e6e0;
}
.approval-summary dt {
  color: #526e66;
}
.approval-summary dd {
  margin: 0;
  color: #243a33;
  font-weight: 700;
  text-align: right;
}
.approval-summary .approval-total dd {
  color: #116a64;
  font-size: 1.25rem;
  font-variant-numeric: tabular-nums;
}
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
.action-modal button:focus-visible {
  outline: 3px solid #16877f;
  outline-offset: 3px;
}
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
