import type {
  CoursePurchase,
  CourseUseRequest,
  PaymentMethod,
  PaymentStatusOption,
  SaleCreateRequest,
} from '@/api/client'

export type UsageSelection = { coursePurchaseId: number; quantity: number; note?: string }
type UsageContext = {
  appointmentId?: number
  patientId: number
  treatingStaffId?: number
  branchCode: string
}
export const allowedPaymentMethod = (method: PaymentMethod) =>
  method.active !== false && ['CreditCard', 'PromptPay', 'BankTransfer'].includes(method.code)
const positiveId = (id: number | undefined) => Number.isSafeInteger(id) && Number(id) > 0
export function buildCourseUsages(
  context: UsageContext,
  selections: UsageSelection[],
  courses: CoursePurchase[],
): CourseUseRequest[] {
  // ตรวจข้อมูลผู้รับบริการ สาขา ผู้รักษา และยอดคงเหลือก่อนสร้าง usage
  if (
    !positiveId(context.appointmentId) ||
    !positiveId(context.patientId) ||
    !positiveId(context.treatingStaffId) ||
    !context.branchCode
  )
    throw new Error('กรุณาระบุนัด ผู้รับบริการ สาขา และผู้รักษาให้ครบ')
  if (!selections.length) throw new Error('กรุณาเลือกคอร์สที่ใช้สิทธิ์')
  const totals = new Map<number, number>()
  for (const row of selections) {
    if (!Number.isSafeInteger(row.quantity) || row.quantity < 1 || row.quantity > 99)
      throw new Error('จำนวนสิทธิ์ต้องเป็นจำนวนเต็ม 1–99 ครั้ง')
    totals.set(row.coursePurchaseId, (totals.get(row.coursePurchaseId) ?? 0) + row.quantity)
  }
  for (const [id, quantity] of totals) {
    const course = courses.find((item) => item.id === id)
    if (
      !course ||
      !course.usable ||
      course.branchCode !== context.branchCode ||
      !Number.isFinite(course.remaining) ||
      course.remaining < quantity
    )
      throw new Error('คอร์สไม่พร้อมใช้ อยู่ต่างสาขา หรือมีสิทธิ์ไม่เพียงพอ')
  }
  return selections.flatMap((row) =>
    Array.from({ length: row.quantity }, () => ({
      appointmentId: context.appointmentId!,
      patientId: context.patientId,
      treatingStaffId: context.treatingStaffId!,
      coursePurchaseId: row.coursePurchaseId,
      ...(row.note?.trim() ? { note: row.note.trim() } : {}),
    })),
  )
}

export type SaleLineInput = {
  type: 'SERVICE' | 'COURSE'
  referenceId: number
  quantity: number
  price: number
  discountType?: string
  discountValue?: number
}
export type SaleInput = UsageContext & {
  salespersonStaffId: number
  paymentLabelCode: string
  paymentMethodCode: string
  labels: PaymentStatusOption[]
  methods: PaymentMethod[]
  items: SaleLineInput[]
  billDiscountType?: string
  billDiscountValue?: number
  note?: string
  usageSelections?: UsageSelection[]
  courses?: CoursePurchase[]
}
const cents = (amount: number) => Math.round((amount + Number.EPSILON) * 100)
function discount(base: number, type?: string, value = 0) {
  if (!Number.isFinite(value) || value < 0 || cents(value) / 100 !== value)
    throw new Error('ส่วนลดต้องไม่ติดลบและไม่เกินสองตำแหน่งทศนิยม')
  if (!type || type === 'NONE') {
    if (value) throw new Error('กรุณาเลือกประเภทส่วนลด')
    return 0
  }
  if (!['FIXED', 'PERCENT'].includes(type) || (type === 'PERCENT' && value > 100))
    throw new Error('ประเภทหรืออัตราส่วนลดไม่ถูกต้อง')
  const off = type === 'PERCENT' ? Math.round((base * value) / 100) : cents(value)
  if (off > base) throw new Error('ส่วนลดต้องไม่เกินราคา')
  return off
}
export function buildSaleRequest(input: SaleInput): SaleCreateRequest {
  if (!positiveId(input.patientId) || !input.branchCode || !positiveId(input.salespersonStaffId))
    throw new Error('กรุณาระบุผู้ป่วย สาขา และผู้ขาย')
  const label = input.labels.find((item) => item.enabled && item.code === input.paymentLabelCode)
  if (!label) throw new Error('กรุณาเลือกป้ายการชำระเงินที่เปิดใช้งาน')
  if (
    !input.methods.some(
      (item) => item.code === input.paymentMethodCode && allowedPaymentMethod(item),
    )
  )
    throw new Error('กรุณาเลือกช่องทางชำระเงินที่อนุญาต')
  if (!input.items.length)
    throw new Error('ต้องมีรายการขาย หากใช้คอร์สอย่างเดียวให้ไปหน้าใช้สิทธิ์')
  const hasService = input.items.some((item) => item.type === 'SERVICE')
  if (hasService && (!positiveId(input.appointmentId) || !positiveId(input.treatingStaffId)))
    throw new Error('บริการรายครั้งต้องระบุนัดและผู้รักษาจริง')
  let subtotal = 0,
    lineDiscount = 0
  const items: SaleCreateRequest['items'] = []
  for (const row of input.items) {
    if (
      !['SERVICE', 'COURSE'].includes(row.type) ||
      !positiveId(row.referenceId) ||
      !Number.isSafeInteger(row.quantity) ||
      row.quantity < 1 ||
      row.quantity > 99 ||
      !Number.isFinite(row.price) ||
      row.price < 0
    )
      throw new Error('รายการหรือจำนวนขายไม่ถูกต้อง')
    // API สร้างคอร์สแยกหนึ่งรายการต่อจำนวนที่ซื้อ
    const count = row.type === 'COURSE' ? row.quantity : 1
    const quantity = row.type === 'COURSE' ? 1 : row.quantity
    const base = cents(row.price) * quantity
    const off = discount(base, row.discountType, row.discountValue)
    subtotal += (base - off) * count
    lineDiscount += off * count
    for (let i = 0; i < count; i++)
      items.push({
        type: row.type,
        referenceId: row.referenceId,
        quantity,
        ...(row.discountType && row.discountType !== 'NONE'
          ? {
              discountType: row.discountType as 'PERCENT' | 'FIXED',
              discountValue: row.discountValue ?? 0,
            }
          : {}),
      })
  }
  const billOff = discount(subtotal, input.billDiscountType, input.billDiscountValue)
  if (lineDiscount > 0 && billOff > 0) throw new Error('ใช้ส่วนลดรายการร่วมกับส่วนลดทั้งบิลไม่ได้')
  if (subtotal - billOff <= 0) throw new Error('ยอดสุทธิต้องมากกว่าศูนย์')
  let usages: SaleCreateRequest['courseUsages']
  if (input.usageSelections?.length) {
    if (!hasService) throw new Error('บริการผสมต้องมีบริการรายครั้งร่วมด้วย')
    usages = buildCourseUsages(input, input.usageSelections, input.courses ?? []).map(
      ({ coursePurchaseId, note }) => ({ coursePurchaseId, ...(note ? { note } : {}) }),
    )
  }
  // ส่งยอดรับชำระหนึ่งรายการตามยอดสุทธิ และแนบ usages ในคำขอเดียวกัน
  return {
    patientId: input.patientId,
    branchCode: input.branchCode,
    salespersonStaffId: input.salespersonStaffId,
    ...(hasService
      ? { appointmentId: input.appointmentId, treatingStaffId: input.treatingStaffId }
      : {}),
    paymentLabelCode: label.code,
    note: input.note,
    items,
    ...(input.billDiscountType && input.billDiscountType !== 'NONE'
      ? {
          billDiscountType: input.billDiscountType as 'FIXED' | 'PERCENT',
          billDiscountValue: input.billDiscountValue ?? 0,
        }
      : {}),
    ...(usages ? { courseUsages: usages } : {}),
    payments: [{ paymentMethodCode: input.paymentMethodCode, amount: (subtotal - billOff) / 100 }],
  }
}
