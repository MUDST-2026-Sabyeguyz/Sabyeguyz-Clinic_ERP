import type { RevenueReport } from '@/api/client'

export type PaymentReportFilters = {
  from: string
  to: string
  saleType: string
}

export type PaymentReportRow = {
  category: string
  date: string
  paid: number
}

/** ชื่อประเภทการขายที่แสดงในตัวเลือกกรองและตาราง (ระบบขายได้แค่ 2 แบบ) */
export const SALE_TYPE_COURSE = 'ขายคอร์ส'
export const SALE_TYPE_SINGLE = 'ขายรายครั้ง'
export const SALE_TYPE_OTHER = 'อื่นๆ'
export const SALE_TYPE_OPTIONS = [
  { value: SALE_TYPE_COURSE, label: SALE_TYPE_COURSE },
  { value: SALE_TYPE_SINGLE, label: SALE_TYPE_SINGLE },
]

/** แปลงค่าประเภทธุรกรรมจาก backend (CoursePurchase, SingleVisit, ...) เป็นชื่อที่แสดง */
export function saleTypeLabel(type: string): string {
  const key = type.replace(/[^a-z]/gi, '').toLowerCase()
  if (key === 'coursepurchase' || key === 'course') return SALE_TYPE_COURSE
  if (key === 'singlevisit') return SALE_TYPE_SINGLE
  return SALE_TYPE_OTHER
}

/** รายงานรายรับจัดกลุ่มตามวันที่รับชำระ ไม่ใช่วันที่ขายในใบเสร็จ */
export function filterPaymentReportRows(
  rows: RevenueReport['dailyByCategory'],
  filters: PaymentReportFilters,
): PaymentReportRow[] {
  return rows
    .filter((row) => row.date >= filters.from && row.date <= filters.to)
    .map((row) => ({
      category: saleTypeLabel(row.type),
      date: row.date,
      paid: Number(row.amount),
    }))
    .filter((row) => filters.saleType === 'ทั้งหมด' || filters.saleType === row.category)
}
