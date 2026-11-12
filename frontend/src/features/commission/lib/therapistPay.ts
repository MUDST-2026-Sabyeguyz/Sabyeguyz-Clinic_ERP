import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  commissionApi,
  type StaffPayment,
  type TherapistAdjustment,
  type TherapistLine,
  type TherapistLines,
} from '@/api/client'

export const baht = (value: number | null | undefined) =>
  `฿${Number(value ?? 0).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** A deduction is shown with a minus sign in front of the baht sign: −฿140.00 */
export const signedBaht = (value: number | null | undefined) => {
  const amount = Number(value ?? 0)
  return amount < 0 ? `−${baht(Math.abs(amount))}` : baht(amount === 0 ? 0 : amount)
}

export const percent = (rate: number | null | undefined) =>
  `${Math.round(Number(rate ?? 0) * 10000) / 100}%`

export const monthLabel = (month: string) => {
  const [year, number] = month.split('-').map(Number)
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(year!, number! - 1, 1),
  )
}

export const shortDate = (value: string | null | undefined) =>
  value
    ? new Intl.DateTimeFormat('th-TH', { day: '2-digit', month: 'short' }).format(
        new Date(`${value}T12:00:00`),
      )
    : '—'

export const longDate = (value: string | null | undefined) =>
  value
    ? new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'long', year: 'numeric' }).format(
        new Date(`${value}T12:00:00`),
      )
    : '—'

export const statusLabel = (status: StaffPayment['status'] | null | undefined) =>
  status === 'PAID'
    ? 'จ่ายแล้ว · Paid'
    : status === 'CLOSED'
      ? 'ปิดงวด · Pending Payment'
      : 'อยู่ระหว่างคำนวณ'

/** The commission of a row and the fee of a substitute row come from the same ledger row id, so the key says which. */
export const lineKey = (line: TherapistLine) =>
  `${line.kind === 'COMMISSION' ? 'c' : line.singleVisit ? 'v' : 's'}${line.id}`

export const isCommission = (line: TherapistLine) => line.kind === 'COMMISSION'

const feeRuleText = (line: TherapistLine) =>
  line.feeType === 'Percentage'
    ? `${Math.round(Number(line.feeValue ?? 0) * 100) / 100}% ของ ${baht(line.feeBase)}`
    : 'จำนวนเงินคงที่'

/** How the amount of a row came about, as one short formula for the table. */
export const calcBase = (line: TherapistLine) => {
  if (line.reversal) return 'ยกเลิกรายการ'
  if (isCommission(line)) {
    if (line.rate === null) return 'รอปิดยอดเดือนที่ขาย'
    return `${baht(line.pool)} ÷ ${line.totalVisits ?? 1}`
  }
  return line.feeType === 'Percentage'
    ? `${baht(line.feeBase)} × ${Math.round(Number(line.feeValue ?? 0) * 100) / 100}%`
    : `คงที่ ${baht(line.feeValue)}`
}

export { feeRuleText }

export const currentMonth = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(new Date()).slice(0, 7)

/**
 * The signed-in therapist's pay: their monthly payments, and the lines of the month in the address (?month=), else the latest
 * month that has a payment, else this month.
 */
export function useTherapistPay() {
  const route = useRoute()
  const payments = ref<StaffPayment[]>([])
  const adjustments = ref<TherapistAdjustment[]>([])
  const data = ref<TherapistLines | null>(null)
  const loading = ref(true)
  const errorMessage = ref('')

  const month = computed(
    () =>
      (typeof route.query.month === 'string' ? route.query.month : '') ||
      payments.value[0]?.month ||
      currentMonth(),
  )
  const months = computed(() => {
    const list = payments.value.map((payment) => payment.month)
    return list.includes(month.value) ? list : [month.value, ...list]
  })
  const payment = computed(() => payments.value.find((row) => row.month === month.value) ?? null)
  const lines = computed(() => data.value?.lines ?? [])

  async function loadLines() {
    try {
      data.value = await commissionApi.myLines(month.value)
    } catch (caught) {
      errorMessage.value = caught instanceof Error ? caught.message : 'โหลดค่าตอบแทนไม่สำเร็จ'
    }
  }

  onMounted(async () => {
    try {
      const [paid, changes] = await Promise.all([
        commissionApi.myPayments(),
        commissionApi.myAdjustments(),
      ])
      payments.value = paid
      adjustments.value = changes
      await loadLines()
    } catch (caught) {
      errorMessage.value = caught instanceof Error ? caught.message : 'โหลดค่าตอบแทนไม่สำเร็จ'
    } finally {
      loading.value = false
    }
  })
  watch(month, () => {
    if (!loading.value) void loadLines()
  })

  return { payments, adjustments, data, lines, month, months, payment, loading, errorMessage }
}

export const lineStatusText = (status: TherapistLine['paymentStatus']) =>
  status === 'PAID'
    ? 'จ่ายแล้ว (Paid)'
    : status === 'CLOSED'
      ? 'รอจ่าย (Pending Payment)'
      : 'อยู่ระหว่างคำนวณ'

/** One line of the signed-in therapist's month, found by the key in the address. */
export function useTherapistLine(key: () => string) {
  const pay = useTherapistPay()
  const line = computed(() => pay.lines.value.find((row) => lineKey(row) === key()) ?? null)
  return { ...pay, line }
}
