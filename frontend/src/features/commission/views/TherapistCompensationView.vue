<script setup lang="ts">
import { computed, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'

type CompensationType = 'ทั้งหมด' | 'Commission' | 'รักษาแทน'
type CompensationStatus = 'ทุกสถานะ' | 'รอตรวจสอบ' | 'จ่ายแล้ว'
type CompensationRow = {
  id: number
  date: string
  service: string
  type: Exclude<CompensationType, 'ทั้งหมด'>
  base: number | null
  compensation: number
  status: Exclude<CompensationStatus, 'ทุกสถานะ'>
}

const rows = ref<CompensationRow[]>([])
const activeTab = ref('ภาพรวม')
const selectedPeriod = ref(new Date().toISOString().slice(0, 7))
const selectedHistoryPeriod = ref('')
const typeFilter = ref<CompensationType>('ทั้งหมด')
const statusFilter = ref<CompensationStatus>('ทุกสถานะ')
const latestPeriodStatus = ref<Exclude<CompensationStatus, 'ทุกสถานะ'>>('รอตรวจสอบ')
const tabs = ['ภาพรวม', 'ค่าคอมมิชชัน', 'ค่ารักษาแทน', 'ประวัติการจ่าย']
const periodOptions: Array<{ value: string; label: string }> = []
const historyPeriodOptions: Array<{ value: string; label: string }> = []
const pageHeading = computed(() => {
  if (activeTab.value === 'ค่าคอมมิชชัน')
    return ['รายละเอียดค่าคอมมิชชัน', 'ตรวจสอบที่มาของฐานคำนวณและอัตราที่ถูกล็อกในรอบเดือน']
  if (activeTab.value === 'ค่ารักษาแทน')
    return ['รายละเอียดค่ารักษาแทน', 'แยกเจ้าของเคสออกจากนักกายภาพผู้ให้บริการจริง']
  if (activeTab.value === 'ประวัติการจ่าย')
    return ['ประวัติการจ่าย', 'ตรวจสอบยอดค่าคอมมิชชันและค่ารักษาแทนที่จ่ายในแต่ละรอบ']
  return ['ค่าตอบแทนของฉัน', 'ดูค่าคอมมิชชัน Tier และค่ารักษาแทนของคุณในแต่ละรอบ']
})
const commissionDetails: string[][] = []
const treatmentDetails: string[][] = []
const paymentHistory: Array<{
  month: string
  commission: number
  treatment: number
  net: number
  paidAt: string
}> = []
const visibleRows = computed(() =>
  rows.value.filter(
    (row) =>
      (typeFilter.value === 'ทั้งหมด' || row.type === typeFilter.value) &&
      (statusFilter.value === 'ทุกสถานะ' || row.status === statusFilter.value),
  ),
)
const displayedTotal = computed(() =>
  visibleRows.value.reduce((sum, row) => sum + row.compensation, 0),
)
const commissionTotal = computed(() =>
  rows.value
    .filter((row) => row.type === 'Commission')
    .reduce((sum, row) => sum + row.compensation, 0),
)
const treatmentTotal = computed(() =>
  rows.value
    .filter((row) => row.type === 'รักษาแทน')
    .reduce((sum, row) => sum + row.compensation, 0),
)
const commissionBase = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.base || 0), 0),
)
const displayedStatus = computed(() =>
  statusFilter.value === 'ทุกสถานะ' ? latestPeriodStatus.value : statusFilter.value,
)
const money = (value: number) => `฿${value.toLocaleString('th-TH')}`
</script>

<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="compensation-main">
      <header class="compensation-heading">
        <div>
          <h1>{{ pageHeading[0] }}</h1>
          <p>{{ pageHeading[1] }}</p>
        </div>
        <div v-if="activeTab === 'ภาพรวม'" class="latest-status">
          <span>สถานะยอดล่าสุด</span
          ><b :class="{ paid: displayedStatus === 'จ่ายแล้ว' }">{{ displayedStatus }}</b>
        </div>
        <div v-else-if="activeTab !== 'ประวัติการจ่าย'" class="latest-status">
          <b class="waiting-payment">รอจ่าย</b>
        </div>
      </header>

      <nav class="compensation-tabs" aria-label="เมนูค่าตอบแทน">
        <button
          v-for="tab in tabs"
          :key="tab"
          type="button"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </nav>

      <template v-if="activeTab === 'ภาพรวม'">
        <section class="compensation-summary" aria-label="สรุปค่าตอบแทน">
          <article class="commission-total">
            <span>ค่าคอมมิชชันสะสม</span><b>{{ money(commissionTotal) }}</b>
          </article>
          <article>
            <span>ค่ารักษาแทน</span><b>{{ money(treatmentTotal) }}</b
            ><small>{{ rows.filter((row) => row.type === 'รักษาแทน').length }} รายการ</small>
          </article>
          <article class="net-total">
            <span>ยอดสุทธิรอบนี้</span><b>{{ money(commissionTotal + treatmentTotal) }}</b
            ><small>สถานะ{{ displayedStatus }}</small>
          </article>
        </section>

        <section class="compensation-filters">
          <label
            ><span>รอบคำนวณ</span
            ><FilterDropdown
              v-model="selectedPeriod"
              label="เลือกรอบคำนวณ"
              default-value=""
              default-label="เลือกรอบคำนวณ"
              :clear-on-reselect="false"
              :options="periodOptions"
          /></label>
          <label
            ><span>ประเภทรายการ</span
            ><FilterDropdown
              v-model="typeFilter"
              label="ประเภทรายการ"
              default-value="ทั้งหมด"
              default-label="ทั้งหมด"
              :options="[
                { value: 'Commission', label: 'Commission' },
                { value: 'รักษาแทน', label: 'รักษาแทน' },
              ]"
          /></label>
          <label
            ><span>สถานะ</span
            ><FilterDropdown
              v-model="statusFilter"
              label="สถานะ"
              default-value="ทุกสถานะ"
              default-label="ทุกสถานะ"
              :options="[
                { value: 'รอตรวจสอบ', label: 'รอตรวจสอบ' },
                { value: 'จ่ายแล้ว', label: 'จ่ายแล้ว' },
              ]"
          /></label>
        </section>

        <section class="compensation-content">
          <article class="tier-panel">
            <h2>Tier ของฉัน</h2>
            <div class="tier-card">
              <header><b>—</b><strong>—</strong></header>
              <p>ยอดฐานเดือนนี้ {{ money(commissionBase) }}</p>
              <div class="tier-track"><span></span></div>
              <small>ข้อมูล Tier จะแสดงเมื่อระบบได้รับข้อมูลค่าตอบแทน</small>
            </div>
          </article>

          <div class="compensation-table-wrap">
            <table>
              <colgroup>
                <col class="date-column" />
                <col class="service-column" />
                <col class="type-column" />
                <col class="base-column" />
                <col class="amount-column" />
                <col class="status-column" />
              </colgroup>
              <thead>
                <tr>
                  <th>วันที่</th>
                  <th>บริการ</th>
                  <th>ประเภท</th>
                  <th>ฐาน</th>
                  <th>ค่าตอบแทน</th>
                  <th class="status-cell">สถานะ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in visibleRows" :key="row.id">
                  <td data-label="วันที่">
                    <b>{{ row.date }}</b>
                  </td>
                  <td data-label="บริการ">{{ row.service }}</td>
                  <td data-label="ประเภท">{{ row.type }}</td>
                  <td data-label="ฐาน">{{ row.base === null ? '—' : money(row.base) }}</td>
                  <td data-label="ค่าตอบแทน">{{ money(row.compensation) }}</td>
                  <td class="status-cell" data-label="สถานะ">
                    <span class="row-status" :class="{ paid: row.status === 'จ่ายแล้ว' }">{{
                      row.status
                    }}</span>
                  </td>
                </tr>
                <tr v-if="!visibleRows.length">
                  <td colspan="6" class="empty-row">ไม่พบรายการตามตัวกรอง</td>
                </tr>
              </tbody>
              <tfoot>
                <tr>
                  <td colspan="4">รวมค่าตอบแทนที่แสดง</td>
                  <td>{{ money(displayedTotal) }}</td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>
      </template>

      <template v-else-if="activeTab === 'ค่าคอมมิชชัน' || activeTab === 'ค่ารักษาแทน'">
        <section class="compensation-detail-card">
          <h2>
            {{ activeTab === 'ค่าคอมมิชชัน' ? 'รายละเอียดค่าคอมมิชชัน' : 'รายละเอียดค่ารักษาแทน' }}
          </h2>
          <p>ข้อมูลการคำนวณแบบอ่านอย่างเดียว</p>
          <dl>
            <div
              v-for="detail in activeTab === 'ค่าคอมมิชชัน' ? commissionDetails : treatmentDetails"
              :key="detail[0]"
            >
              <dt>{{ detail[0] }}</dt>
              <dd>{{ detail[1] }}</dd>
              <small>{{ detail[2] }}</small>
            </div>
          </dl>
        </section>
        <button class="back-overview" type="button" @click="activeTab = 'ภาพรวม'">
          ←&nbsp;&nbsp; กลับภาพรวม
        </button>
      </template>

      <template v-else>
        <section class="payment-filters">
          <label
            ><span>รอบเดือน</span
            ><FilterDropdown
              v-model="selectedHistoryPeriod"
              label="เลือกช่วงประวัติ"
              default-value=""
              default-label="เลือกช่วงประวัติ"
              :clear-on-reselect="false"
              :options="historyPeriodOptions"
          /></label>
        </section>
        <section class="payment-history-table">
          <header>
            <span>รอบเดือน</span><span>Commission</span><span>ค่ารักษาแทน</span><span>รวมสุทธิ</span
            ><span>วันที่จ่าย</span><span>สถานะ</span>
          </header>
          <article v-for="item in paymentHistory" :key="item.month">
            <div>
              <small>รอบเดือน</small><b>{{ item.month }}</b>
            </div>
            <div>
              <small>Commission</small><b>{{ money(item.commission) }}</b>
            </div>
            <div>
              <small>ค่ารักษาแทน</small><b>{{ money(item.treatment) }}</b>
            </div>
            <div>
              <small>รวมสุทธิ</small><b>{{ money(item.net) }}</b>
            </div>
            <div>
              <small>วันที่จ่าย</small><b>{{ item.paidAt }}</b>
            </div>
            <span class="paid-badge">จ่ายแล้ว</span>
          </article>
        </section>
        <p class="payment-note">
          ยอดในประวัติเป็นยอดที่ปิดรอบและจ่ายแล้ว ไม่สามารถแก้ไขย้อนหลังได้
        </p>
      </template>
    </main>
  </div>
</template>

<style scoped src="../styles/therapist-compensation.css"></style>
