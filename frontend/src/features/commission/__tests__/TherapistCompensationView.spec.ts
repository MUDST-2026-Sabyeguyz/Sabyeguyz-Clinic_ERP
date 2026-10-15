import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  resolve(process.cwd(), 'src/features/commission/views/TherapistCompensationView.vue'),
  'utf-8',
)
const routerSource = readFileSync(resolve(process.cwd(), 'src/router/index.ts'), 'utf-8')
const tabsSource = readFileSync(
  resolve(process.cwd(), 'src/features/commission/components/TherapistCompensationTabs.vue'),
  'utf-8',
)
const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/features/commission/styles/therapist-compensation-workspace.css'),
  'utf-8',
)
const detailSource = readFileSync(
  resolve(process.cwd(), 'src/features/commission/components/TherapistCompensationDetail.vue'),
  'utf-8',
)
const paymentSource = readFileSync(
  resolve(
    process.cwd(),
    'src/features/commission/components/TherapistCompensationPaymentHistory.vue',
  ),
  'utf-8',
)
const sourceDetailSource = readFileSync(
  resolve(
    process.cwd(),
    'src/features/commission/components/TherapistCompensationSourceDetail.vue',
  ),
  'utf-8',
)
const detailViewSources = [
  'TherapistCommissionDetailView.vue',
  'TherapistTreatmentFeeDetailView.vue',
  'TherapistAdjustmentDetailView.vue',
  'TherapistTreatmentSourceView.vue',
].map((file) =>
  readFileSync(resolve(process.cwd(), `src/features/commission/views/${file}`), 'utf-8'),
)

describe('Therapist compensation workspace', () => {
  it('keeps the live compensation overview and registers route-driven detail tabs', () => {
    expect(viewSource).toContain('commissionApi.myPayments()')
    expect(viewSource).not.toContain('EMPTY_COMPENSATION_COPY')
    expect(tabsSource).toContain("name: 'therapist-compensation'")
    expect(tabsSource).toContain('query: { ...route.query, tab: tab.key }')
  })

  it('registers Therapist-only detail routes', () => {
    expect(routerSource).toContain("name: 'therapist-commission-detail'")
    expect(routerSource).toContain("name: 'therapist-treatment-fee-detail'")
    expect(routerSource).toContain("name: 'therapist-payment-history'")
    expect(routerSource).toContain("roles: ['therapist']")
  })

  it('uses a visible 2 by 2 tab grid on narrow screens rather than horizontal tab scrolling', () => {
    expect(stylesheet).toContain('grid-template-columns: repeat(2, minmax(0, 1fr))')
    expect(stylesheet).toMatch(
      /\.therapist-compensation-tabs\s*\{\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);\s*overflow: visible;/,
    )
  })

  it('loads workspace CSS globally on the new detail routes', () => {
    detailViewSources.forEach((source) => {
      expect(source).toContain('<style src="../styles/therapist-compensation-workspace.css"></style>')
    })
  })

  it('renders the Figma commission calculation rows and source-sale action', () => {
    expect(detailSource).toContain('ยอดบริการก่อนส่วนลด')
    expect(detailSource).toContain('ส่วนลดระดับรายการ')
    expect(detailSource).toContain('ฐานค่าคอมมิชชัน')
    expect(detailSource).toContain('Tier และอัตราที่ล็อก')
    expect(detailSource).toContain('ค่าคอมมิชชันสุทธิ')
    expect(detailSource).toContain('ดูรายการขายต้นทาง')
  })

  it('uses the Figma treatment roles and payment-history columns', () => {
    expect(detailSource).toContain('เจ้าของเคส')
    expect(detailSource).toContain('ผู้ให้บริการจริง')
    expect(detailSource).toContain('วิธีคำนวณ')
    expect(detailSource).toContain('อัตราค่ามือตามกฎ')
    expect(detailSource).toContain('ค่ามือสุทธิที่ได้รับ')
    expect(paymentSource).toContain('Adjustment')
    expect(paymentSource).toContain('ยอดสุทธิ')
    expect(paymentSource).toContain('วันที่จ่าย')
  })

  it('renders the Figma source-sales breakdown and a return to commission detail', () => {
    expect(sourceDetailSource).toContain('รายละเอียดรายการขายต้นทาง')
    expect(sourceDetailSource).toContain('เลข HN')
    expect(sourceDetailSource).toContain('ยอดก่อนส่วนลด')
    expect(sourceDetailSource).toContain('ส่วนลดรายการ')
    expect(sourceDetailSource).toContain('ยอดหลังส่วนลดรายการ')
    expect(sourceDetailSource).toContain('ฐาน Commission')
    expect(sourceDetailSource).toContain('กลับรายละเอียดค่าคอมมิชชัน')
  })

  it('keeps return actions within the content instead of adding a duplicate link above it', () => {
    detailViewSources.forEach((source) => {
      expect(source).not.toContain('← กลับ')
    })
  })

  it('does not add an extra treatment-detail return action to the treatment source page', () => {
    expect(sourceDetailSource).not.toContain('กลับรายละเอียดค่ามือ')
  })
})
