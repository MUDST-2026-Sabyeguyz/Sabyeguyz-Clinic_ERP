<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type CommissionFormula } from '@/api/client'

const formula = ref<CommissionFormula | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const baht = (value: number) =>
  `฿${Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const percent = (rate: number) => `${Math.round(rate * 10000) / 100}%`
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(`${value}T12:00:00`))
const feeText = computed(() => {
  const fee = formula.value?.treatmentFee
  if (!fee) return null
  return fee.feeType === 'Fixed'
    ? `${baht(fee.feeValue)} ต่อครั้งที่รักษา`
    : `${Math.round(fee.feeValue * 100) / 100}% ของยอดที่เกี่ยวข้องกับรายการนั้น`
})

onMounted(async () => {
  try {
    formula.value = await commissionApi.formula()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดสูตรคำนวณไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="formula-page">
    <StaffHeader />
    <main class="formula-main">
      <header class="formula-title">
        <h1>สูตรคำนวณค่าตอบแทน</h1>
        <p>
          วิธีคำนวณค่าคอมมิชชันคอร์สและค่ามือที่ใช้อยู่ตอนนี้ วิธีคำนวณกำหนดโดยระบบ
          ส่วนช่วงยอดขายและอัตราของ Tier Manager เป็นผู้กำหนด
        </p>
      </header>

      <p v-if="errorMessage" role="alert" class="formula-error">
        {{ errorMessage }}
      </p>
      <p v-else-if="loading">กำลังโหลดสูตรคำนวณ...</p>

      <template v-else-if="formula">
        <section class="formula-card" aria-labelledby="formula-owner">
          <h2 id="formula-owner">1. ค่าคอมมิชชันของคอร์ส (เจ้าของคอร์ส)</h2>
          <ul>
            <li>
              เจ้าของคอร์สคือนักกายภาพผู้ขายคอร์ส ค่าคอมมิชชันของคอร์สเป็นของเจ้าของคอร์สเท่านั้น
            </li>
            <li>
              อัตราดูจาก
              <b>ยอดขายคอร์สสุทธิรวมของเจ้าของคอร์สในเดือนที่ขาย</b>
              (หักคืนเงินที่คืนแล้วออก) ตกอยู่ในช่วง Tier ไหนใช้อัตราของ Tier นั้น
            </li>
            <li>
              <b>อัตราถูกล็อกเมื่อปิดยอดเดือนที่ขาย</b> ก่อนปิดยอดจะแสดงเป็น “รอคำนวณ” ไม่นับเป็น 0
            </li>
            <li>
              ค่าคอมมิชชันรวมของคอร์ส (pool) = ราคาคอร์สสุทธิ × อัตรา แบ่งให้แต่ละครั้งที่ใช้เท่าๆ
              กัน เศษสตางค์ไปรวมที่ครั้งสุดท้าย
            </li>
          </ul>

          <template v-if="formula.scheme">
            <h3>
              Tier ที่ใช้อยู่ ({{ formula.scheme.name }} เวอร์ชัน
              {{ formula.scheme.version }} ตั้งแต่ {{ thaiDate(formula.scheme.effectiveFrom) }})
            </h3>
            <table class="formula-table">
              <thead>
                <tr>
                  <th>Tier</th>
                  <th>ยอดขายสุทธิรายเดือน</th>
                  <th>อัตราคอมมิชชัน</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="tier in formula.tiers" :key="tier.id">
                  <td>Tier {{ tier.order }}</td>
                  <td>
                    {{ baht(tier.minimum) }} –
                    {{ tier.maximum === null ? 'ไม่จำกัด' : baht(tier.maximum) }}
                  </td>
                  <td>
                    <b>{{ percent(tier.rate) }}</b>
                  </td>
                </tr>
              </tbody>
            </table>
          </template>
          <p v-else class="formula-empty">ยังไม่มี Tier ที่ใช้อยู่ในวันนี้</p>

          <aside class="formula-example" aria-label="ตัวอย่างการคำนวณ">
            <b>ตัวอย่างประกอบ (สมมติว่าอัตรา 7%)</b>
            <span
              >คอร์ส ฿10,000 ใช้ 13 ครั้ง → pool = 10,000 × 7% = ฿700.00 → ครั้งละ ฿53.85 สำหรับ 12
              ครั้งแรก และ ฿53.80 ครั้งสุดท้าย (รวม ฿700.00)</span
            >
          </aside>
        </section>

        <section class="formula-card" aria-labelledby="formula-substitute">
          <h2 id="formula-substitute">2. ค่ามือรักษาแทน และค่ามือรายครั้ง</h2>
          <ul>
            <li>
              ถ้าคนรักษาไม่ใช่เจ้าของคอร์ส ผู้รักษาแทนได้ค่ามือเต็มตามกฎด้านล่าง
              และเจ้าของคอร์สไม่ได้ค่าคอมมิชชันของครั้งนั้น
            </li>
            <li>การรักษาแบบรายครั้ง (Single Visit) ผู้รักษาได้ค่ามือตามกฎเดียวกัน ณ วันที่รักษา</li>
            <li>ค่ามือไม่ขึ้นกับ Tier และไม่ต้องรอปิดยอดเดือน</li>
          </ul>
          <p v-if="feeText" class="formula-fee">
            กฎค่ามือที่ใช้อยู่: <b>{{ feeText }}</b> (ตั้งแต่
            {{ thaiDate(formula.treatmentFee!.effectiveFrom) }})
          </p>
          <p v-else class="formula-empty">ยังไม่มีกฎค่ามือที่ใช้อยู่ในวันนี้</p>
        </section>

        <section class="formula-card" aria-labelledby="formula-payout">
          <h2 id="formula-payout">3. การจ่าย</h2>
          <ul>
            <li>
              ยอดรวมของแต่ละเดือน = ค่าคอมมิชชันคอร์ส + ค่ามือรายครั้ง + ค่ามือรักษาแทน ± ใบปรับยอด
            </li>
            <li>
              งวดจ่ายต้องปิดงวดก่อน แล้ว Manager จึงยืนยันจ่าย (บันทึกวันที่จ่ายและเลขที่อ้างอิง)
            </li>
            <li>
              เมื่อจ่ายแล้วแก้ย้อนหลังไม่ได้ หากต้องแก้ ให้ทำใบปรับยอดในงวดถัดไป
              การหักไม่เกินค่าตอบแทนที่ได้ในงวด ส่วนที่เหลือยกไปงวดถัดไป
            </li>
          </ul>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.formula-page {
  min-height: 100vh;
  background: #faf6f0;
  color: #25372f;
}
.formula-main {
  width: min(960px, calc(100% - 32px));
  margin: 0 auto;
  padding: 24px 0 48px;
  display: grid;
  gap: 16px;
}
.formula-title h1 {
  margin: 0 0 6px;
}
.formula-title p {
  margin: 0;
  color: #52706d;
}
.formula-card {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border: 1px solid #dde6e1;
  border-radius: 12px;
  background: #fff;
}
.formula-card h2,
.formula-card h3 {
  margin: 0;
}
.formula-card h3 {
  font-size: 1rem;
}
.formula-card ul {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 6px;
}
.formula-table {
  width: 100%;
  border-collapse: collapse;
}
.formula-table th,
.formula-table td {
  padding: 9px 10px;
  border-bottom: 1px solid #e6eeea;
  text-align: left;
}
.formula-example {
  display: grid;
  gap: 4px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f3f9f8;
}
.formula-fee {
  margin: 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f3f9f8;
}
.formula-empty {
  margin: 0;
  color: #52706d;
}
.formula-error {
  padding: 11px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
</style>
