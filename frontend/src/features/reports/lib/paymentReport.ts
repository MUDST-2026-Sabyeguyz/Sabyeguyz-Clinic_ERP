import type { RevenueReport } from '@/api/client'

export type PaymentReportFilters = {
  from: string
  to: string
  saleType: string
  treatment: string
}

export type PaymentReportRow = {
  category: string
  treatment: string
  date: string
  paid: number
}

const categories: Record<string, string> = {
  COURSE: 'Course Sales',
  COURSE_PURCHASE: 'Course Sales',
  SINGLE_VISIT: 'Single Visit Sales',
  ASSESSMENT: 'Assessment',
}
const treatments: Record<string, string> = {
  COURSE: 'Manual Therapy',
  COURSE_PURCHASE: 'Manual Therapy',
  SINGLE_VISIT: 'Exercise Therapy',
  ASSESSMENT: 'Assessment',
}

/** รายงานรายรับจัดกลุ่มตามวันที่รับชำระ ไม่ใช่วันที่ขายในใบเสร็จ */
export function filterPaymentReportRows(
  rows: RevenueReport['dailyByCategory'],
  filters: PaymentReportFilters,
): PaymentReportRow[] {
  return rows
    .filter((row) => row.date >= filters.from && row.date <= filters.to)
    .map((row) => ({
      category: categories[row.type] ?? row.type,
      treatment: treatments[row.type] ?? '—',
      date: row.date,
      paid: Number(row.amount),
    }))
    .filter(
      (row) =>
        (filters.saleType === 'ทั้งหมด' || filters.saleType === row.category) &&
        (filters.treatment === 'ทั้งหมด' || filters.treatment === row.treatment),
    )
}
