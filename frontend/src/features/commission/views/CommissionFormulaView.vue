<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type CommissionFormula } from '@/api/client'

const router = useRouter()
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

// กลับไปหน้าก่อนหน้า ถ้าเปิดหน้านี้ตรงๆ ให้กลับหน้าค่าตอบแทน
const goBack = () => {
  if (window.history.state?.back) router.back()
  else void router.push('/staff/compensation')
}

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
      <button type="button" class="app-back-link formula-back" @click="goBack">← กลับ</button>
      <header class="formula-title">
        <h1>สูตรคำนวณค่าตอบแทน</h1>
        <p>
          อธิบายว่าค่าคอมมิชชันคอร์สและค่ามือคิดอย่างไร วิธีคิดเป็นของระบบ
          ส่วนช่วงยอดขายและอัตราในแต่ละ Tier ผู้จัดการเป็นคนกำหนด
        </p>
      </header>

      <p v-if="errorMessage" role="alert" class="formula-error">
        {{ errorMessage }}
      </p>
      <p v-else-if="loading">กำลังโหลดสูตรคำนวณ...</p>

      <template v-else-if="formula">
        <section class="formula-card" aria-labelledby="formula-owner">
          <h2 id="formula-owner">1. ค่าคอมมิชชันคอร์ส (สำหรับเจ้าของคอร์ส)</h2>
          <ul>
            <li>
              เจ้าของคอร์สคือนักกายภาพที่ขายคอร์สนั้น ค่าคอมมิชชันของคอร์สเป็นของเจ้าของคอร์สคนเดียว
            </li>
            <li>
              อัตราดูจาก
              <b>ยอดขายคอร์สสุทธิรวมของเจ้าของคอร์สในเดือนที่ขาย</b>
              (หักเงินที่คืนลูกค้าแล้วออก) ถ้ายอดอยู่ในช่วง Tier ไหน ก็ใช้อัตราของ Tier นั้น
            </li>
            <li>
              <b>อัตราจะถูกล็อกเมื่อปิดยอดเดือนที่ขาย</b> ก่อนปิดยอดจะแสดงว่า “รอปิดยอด”
              ไม่ได้นับเป็น 0 บาท
            </li>
            <li>
              ค่าคอมมิชชันรวมของคอร์ส = ราคาคอร์สสุทธิ × อัตรา แล้วแบ่งให้แต่ละครั้งที่มารักษาเท่าๆ
              กัน เศษสตางค์จะไปรวมในครั้งสุดท้าย
            </li>
          </ul>

          <template v-if="formula.scheme">
            <h3>
              Tier ที่ใช้ตอนนี้ ({{ formula.scheme.name }} เวอร์ชัน
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
          <p v-else class="formula-empty">วันนี้ยังไม่มี Tier ที่ใช้งาน</p>

          <aside class="formula-example" aria-label="ตัวอย่างการคำนวณ">
            <b>ตัวอย่าง (สมมติอัตรา 7%)</b>
            <span
              >คอร์ส ฿10,000 รักษา 13 ครั้ง → ค่าคอมมิชชันรวม = 10,000 × 7% = ฿700.00 → ได้ครั้งละ
              ฿53.85 สำหรับ 12 ครั้งแรก และ ฿53.80 ในครั้งสุดท้าย (รวมแล้ว ฿700.00)</span
            >
          </aside>
        </section>

        <section class="formula-card" aria-labelledby="formula-substitute">
          <h2 id="formula-substitute">2. ค่ามือ (รักษาแทนและรักษารายครั้ง)</h2>
          <ul>
            <li>
              ถ้าคนที่รักษาไม่ใช่เจ้าของคอร์ส คนที่รักษาแทนจะได้ค่ามือเต็มตามกฎด้านล่าง
              ส่วนเจ้าของคอร์สจะไม่ได้ค่าคอมมิชชันของครั้งนั้น
            </li>
            <li>
              การรักษาแบบรายครั้ง (ไม่ได้ซื้อเป็นคอร์ส) คนที่รักษาได้ค่ามือตามกฎเดียวกัน
              โดยใช้กฎของวันที่รักษา
            </li>
            <li>ค่ามือไม่เกี่ยวกับ Tier และไม่ต้องรอปิดยอดประจำเดือน</li>
          </ul>
          <p v-if="feeText" class="formula-fee">
            กฎค่ามือที่ใช้ตอนนี้: <b>{{ feeText }}</b> (ตั้งแต่
            {{ thaiDate(formula.treatmentFee!.effectiveFrom) }})
          </p>
          <p v-else class="formula-empty">วันนี้ยังไม่มีกฎค่ามือที่ใช้งาน</p>
        </section>

        <section class="formula-card" aria-labelledby="formula-payout">
          <h2 id="formula-payout">3. การจ่าย</h2>
          <ul>
            <li>ยอดรวมของแต่ละเดือน = ค่าคอมมิชชันคอร์ส + ค่ามือ ± รายการปรับค่าตอบแทน</li>
            <li>
              ต้องปิดงวดก่อน แล้วผู้จัดการจึงยืนยันการจ่ายได้ (โดยบันทึกวันที่จ่ายและเลขอ้างอิง)
            </li>
            <li>
              เมื่อจ่ายแล้วจะแก้ย้อนหลังไม่ได้ ถ้าต้องแก้ ให้ทำรายการปรับในงวดถัดไป
              ยอดที่หักจะไม่เกินค่าตอบแทนที่ได้ในงวดนั้น ส่วนที่เหลือจะยกไปหักงวดต่อไป
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
.formula-back {
  justify-self: start;
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
